import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const rateLimited = new Counter('rate_limited');
const successRate = new Rate('success_rate');

export const options = {
  stages: [
    { duration: '10s', target: 10  },
    { duration: '30s', target: 50  },
    { duration: '10s', target: 100 },
    { duration: '10s', target: 0   },
  ],
  thresholds: {
    http_req_duration: ['p(99)<500'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:80';

export default function () {
  const clientId = `user-${Math.floor(Math.random() * 5)}`;
  const res = http.get(`${BASE}/api/v1/ping`, {
    headers: { 'X-User-ID': clientId },
  });

  check(res, {
    'status 200 or 429': r => r.status === 200 || r.status === 429,
    'has rate limit headers': r => !!r.headers['X-Ratelimit-Limit'],
  });

  if (res.status === 429) rateLimited.add(1);
  successRate.add(res.status === 200 ? 1 : 0);
  sleep(0.1);
}
