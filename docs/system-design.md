# System Design — Distributed Rate Limiter

> Every decision in this document has a reason.
> This is what you explain in a system design interview when the interviewer asks "why".

---

## Problem statement

Design a rate limiter that:

1. Allows at most **N requests per time window** per client
2. Works correctly with **multiple API server instances** (distributed)
3. Adds **< 5ms** overhead per request (P99)
4. Survives a Redis primary failure with **< 10s downtime**
5. Is **observable**: real-time visibility into allowed/denied rates per client

---

## High-level design

```
Client
  │
  ▼
[Nginx — round-robin]
  ├── API Node 1
  ├── API Node 2   ──► Redis Primary ──► Redis Replica
  └── API Node 3         │
                    Redis Sentinel
                  (automatic failover)
```

**Key insight:** rate-limit state must live _outside_ the API nodes.
If each node had its own counter, a client could hit all three nodes
and exceed the limit by 3×. Centralizing in Redis means all nodes
see the same counter — always consistent.

---

## Why Redis?

| Requirement | How Redis satisfies it |
|---|---|
| Sub-ms latency | In-memory store, P99 < 1ms in same AZ |
| Atomic operations | Lua scripts run as a single transaction |
| Auto-expiry | Native TTL — no cleanup job needed |
| High availability | Replication + Sentinel failover |
| Horizontal scale | Cluster mode for sharding across primaries |

Alternatives considered:
- **In-process counter**: fast but breaks with multiple nodes
- **Relational DB**: too slow (5–20ms per check), not designed for this
- **Memcached**: lacks Lua scripting, no replication

---

## Algorithm decision: sliding window counter

### Fixed window — eliminated

```
Window 1:  00:00 → 00:59 — 100 requests  ← limit hit
Window 2:  01:00 → 01:59 — 100 requests  ← new window, counter reset
```

A client can fire 100 at `00:59` and 100 at `01:00` — 200 requests in 2 seconds
against a "100 req/min" limit. This is the **boundary spike problem** and it's a
real attack vector. Eliminated.

### Sliding window log — eliminated

Stores every request timestamp; counts those within the last `window_ms`.
Perfectly accurate but **O(requests)** memory per client.
At 1M clients × 100 req/min → 100M entries. Unacceptable. Eliminated.

### Sliding window counter — chosen ✓

Uses two counters (current slot + previous slot) and approximates
with a weighted average:

```
elapsed   = now_ms % window_ms
weight    = 1 − (elapsed / window_ms)
estimated = floor(prev_count × weight) + curr_count
```

**Error bound:** at most `limit / window` extra requests — for 100 req/60s
that's < 2 requests. Bounded and acceptable.

**Memory:** O(1) per client — always exactly 2 Redis keys.

---

## Atomicity: why Lua scripts

The naive approach requires two Redis calls:

```
// Node A reads 99                // Node B reads 99 (same time)
GET counter → 99                 GET counter → 99
if 99 < 100: INCR counter        if 99 < 100: INCR counter
// Both succeed — counter is 101, both requests pass
```

This is a classic **TOCTOU (time-of-check-time-of-use) race condition**.
With multiple API nodes, this happens constantly under load.

**Solution:** run the entire check-and-increment as a Lua script.
Redis executes Lua atomically — no other commands can interleave during execution.

```lua
local estimated = math.floor(prev * weight) + curr
if estimated >= limit then return {0, ...} end  -- deny, no write
redis.call('INCR', KEYS[1])                     -- allow + increment atomically
return {1, ...}
```

This is the same approach used by **Stripe, Cloudflare, and AWS API Gateway**
in their rate limiters.

---

## High availability

### Redis Sentinel

Sentinel monitors the primary with 1s heartbeats.
After `down-after-milliseconds` (5s) of silence it:

1. Marks the primary as `S_DOWN` (subjectively down)
2. After quorum confirmation → `O_DOWN` (objectively down)
3. Elects a new primary from replicas
4. Notifies all clients of the new primary endpoint

**Recovery time:** 5–10 seconds. Lettuce (Spring's Redis client)
reconnects automatically — no code change needed.

### What if Redis is completely unavailable?

Two strategies — choose by business context:

**Fail open (this project's default):** let all requests through.
A few minutes of unlimited traffic is better than a complete outage.
Appropriate for most web APIs.

```java
try {
    return rateLimiter.check(clientKey);
} catch (RedisException e) {
    log.error("Redis unavailable — failing open");
    return RateLimitResult.builder().allowed(true)...build();
}
```

**Fail closed:** deny all requests. Use for security-critical endpoints
(auth, payment) where allowing unlimited traffic is worse than downtime.

---

## Client identification

Priority order (first non-blank wins):

1. `X-User-ID` — authenticated user ID (most precise)
2. `X-API-Key` — API consumer key
3. `X-Forwarded-For` — first IP in the chain (behind reverse proxy)
4. `getRemoteAddr()` — direct TCP connection IP

**Why this order:** authenticated identity is more precise than network identity.
Without it, a logged-in user behind a shared office IP would be throttled
by their colleagues' requests.

**X-Forwarded-For gotcha:** always take the _first_ IP (leftmost), not the last.
The last IP is added by your own proxy and can be spoofed if you take
a client-supplied value.

---

## Scalability analysis

### Memory

Per client: 2 Redis keys × ~60 bytes = 120 bytes.
At 1M active clients: ~120MB on Redis.
`cache.t3.micro` (512MB RAM) handles ~4M active clients.

### Throughput

Each check = 1 Lua script execution ≈ 0.3ms round-trip (same AZ).
With Lettuce pool (size 20), each API node handles ~66k checks/sec.
Three nodes → **~200k checks/sec** — far beyond what `t3.small` can generate in web traffic.

### Scaling beyond this

- **Redis Cluster**: shard keys across 3+ primaries. Key design is deterministic
  (clientKey → same shard every time), so no cross-shard coordination needed.
- **More API nodes**: stateless — just add nodes to the ASG. No configuration change.
- **More Redis replicas**: read from replicas for non-write operations (health checks, dashboards).

### Multi-region

Current design: single Redis cluster in one region.

For global rate limiting:

| Strategy | Accuracy | Latency | Complexity |
|---|---|---|---|
| Per-region counters (limit / num_regions) | Approximate | Local | Low |
| Central Redis + cross-region writes | Exact | +50–150ms | Low |
| CRDTs (e.g. Redisson) | Eventual | Local | High |

For most products, **per-region approximate** is the right answer.
Over-allowing by `limit/N` is acceptable; adding 150ms to every API request is not.

---

## Observability

Metrics exposed via Micrometer → Prometheus → Grafana:

| Metric | Labels | Type | Use |
|---|---|---|---|
| `rate_limiter.requests` | `algorithm`, `result` | Counter | Allowed vs denied over time |
| `rate_limiter.blocked_requests` | — | Counter | 429 responses sent |
| `rate_limiter.check_duration` | — | Timer | Redis latency P99 |

**Key dashboards:**
- Request rate (allowed/denied per second) — spot traffic spikes
- Block rate by client — identify abusers
- Redis P99 latency — detect performance degradation
- Active keys in Redis — measure unique client growth

---

## Trade-offs summary

| Decision | What's here | Production upgrade |
|---|---|---|
| HA strategy | Redis Sentinel | Redis Cluster (sharding) |
| Limit config | Static YAML | Dynamic DB with hot-reload |
| Single window | One window/client | Tiered: 10 req/s AND 1000 req/h |
| Observability | Micrometer + Prometheus | OpenTelemetry collector |
| Circuit breaker | None (fail open) | Resilience4j with fallback |
| Auth | Header-based | JWT claim extraction |

---

## Interview answers

**"How do you prevent race conditions?"**
> Lua scripts. The entire read-check-increment runs as one atomic operation in Redis.
> No interleaving between nodes is possible because Redis is single-threaded for Lua execution.

**"What happens if Redis goes down?"**
> Sentinel detects the failure within 5s and promotes a replica to primary.
> Lettuce reconnects automatically. During the window we fail open to avoid a full outage.

**"How would you scale to 100M users?"**
> Redis Cluster with 6 shards (3 primaries × 2 replicas each). Keys hash
> deterministically to the same shard — no cross-shard coordination.
> API nodes are stateless and scale horizontally behind the ALB.

**"Why not use Kong / AWS API Gateway?"**
> In production I would evaluate them seriously. I built this to demonstrate
> that I understand the problem at the mechanism level — atomic counters,
> Lua for TOCTOU prevention, Sentinel for HA. That understanding makes me
> a better engineer regardless of which tool I eventually use.

**"How would you add per-endpoint limits?"**
> The key already encodes the endpoint: `sw:<method>:<path>:<clientKey>:<slot>`.
> Load the per-route config from `RateLimiterProperties.rules`, resolve the rule
> by matching the request path, and pass that rule's limit/window to the script.
> The Lua script is unchanged — only the parameters differ.
