# Distributed Rate Limiter

Production-grade, multi-node rate limiter — **Java 21 + Spring Boot 3 + Gradle + Redis + AWS**.

> Portfolio project demonstrating distributed systems design, atomic concurrency via Lua, and cloud infrastructure as code.

---

## Quick start (local)

```bash
git clone https://github.com/YOUR_USERNAME/distributed-rate-limiter
cd distributed-rate-limiter

# Start full stack: Redis Sentinel + 3 API nodes + Nginx + Prometheus + Grafana
docker compose up --build -d

# Smoke test
curl -i http://localhost/api/v1/ping

# Trigger rate limit (run 200 times fast)
for i in $(seq 1 200); do curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-User-ID: test-user" http://localhost/api/v1/ping; done

# Load test (requires k6)
k6 run k6/load-test.js
```

**Dashboard:** `cd rate-limiter-dashboard && npm install && npm run dev` → http://localhost:3000

---

## Architecture

```
Client
  │
  ▼
Nginx (round-robin load balancer)
  ├── API Node 1 :8081  ─┐
  ├── API Node 2 :8082  ──►  Redis Primary ──► Redis Replica
  └── API Node 3 :8083  ─┘        │
                              Redis Sentinel
                          (auto-failover ~5s)
```

State lives entirely in Redis — all nodes share the same counters.

---

## Build with Gradle

```bash
# Build all modules
./gradlew build

# Run API locally (requires Redis on localhost:6379)
./gradlew :rate-limiter-api:bootRun

# Run tests only
./gradlew test

# Build fat JAR
./gradlew :rate-limiter-api:bootJar
# → rate-limiter-api/build/libs/rate-limiter-api.jar

# See all tasks
./gradlew tasks
```

---

## Algorithms

| Algorithm | Memory | Burst | Boundary-safe |
|---|---|---|---|
| Sliding window *(default)* | O(1) | No | Yes |
| Token bucket | O(1) | Yes | Yes |
| Fixed window | O(1) | No | No |

Switch via `rate-limiter.default-algorithm` in `application.yml`.

---

## HTTP headers

| Header | Present on | Description |
|---|---|---|
| `X-RateLimit-Limit` | All | Max requests per window |
| `X-RateLimit-Remaining` | All | Remaining in current window |
| `X-RateLimit-Reset` | All | Unix epoch second of next reset |
| `Retry-After` | 429 only | Seconds until retry allowed |

---

## Deploy to AWS

```bash
cd infra/terraform
terraform init
terraform apply \
  -var="key_pair_name=my-key" \
  -var="ecr_image_url=ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/rate-limiter:latest"
```

Push to `main` → GitHub Actions runs tests → builds & pushes Docker image to ECR → rolling deployment via ASG instance refresh.

---

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Build tool | Gradle 8.7 (Kotlin DSL) |
| State store | Redis 7.2 + Lua scripts (atomic) |
| Metrics | Micrometer → Prometheus → Grafana |
| Infra | Terraform — VPC, EC2 ASG, ElastiCache, ALB |
| CI/CD | GitHub Actions → ECR → rolling ASG update |
| Load test | k6 |
| Dashboard | React 18 + TypeScript + Recharts + Tailwind |
