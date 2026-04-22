import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

const BASE = "https://zxh4ps7huh.execute-api.ap-south-1.amazonaws.com/prod";
const SHORT_CODE = "ZO2iQj8sJh";

const errorRate = new Rate("errors");
const rateLimitedCount = new Counter("rate_limited");

export function setup() {
  const res = http.post(`${BASE}/auth/login`,
    JSON.stringify({ username: "testuser_final_5", password: "test123" }),
    { headers: { "Content-Type": "application/json" } });
  return { token: res.json("token") };
}

export const options = {
  scenarios: {
    redirect_load: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 5 },
        { duration: "1m", target: 15 },
        { duration: "30s", target: 25 },
        { duration: "1m", target: 25 },
        { duration: "30s", target: 0 },
      ],
      exec: "testRedirect",
    },
    shorten_load: {
      executor: "constant-arrival-rate",
      rate: 2,
      timeUnit: "1s",
      duration: "3m",
      preAllocatedVUs: 5,
      exec: "testShorten",
    },
  },
  thresholds: {
    "http_req_duration{scenario:redirect_load}": ["p(95)<1000"],
    "http_req_duration{scenario:shorten_load}": ["p(95)<2000"],
    "errors": ["rate<0.05"],
  },
};

export function testRedirect() {
  const fakeIp = `${10 + Math.floor(Math.random() * 200)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
  const res = http.get(`${BASE}/${SHORT_CODE}`, {
    redirects: 0,
    headers: { "X-Forwarded-For": fakeIp },
    timeout: "10s",
  });

  const ok = res.status === 301 || res.status === 302;
  const rateLimited = res.status === 429;
  if (rateLimited) {
    rateLimitedCount.add(1);
  }
  errorRate.add(!ok && !rateLimited);

  check(res, {
    "301 redirect or 429 rate-limit": (r) => [301, 302, 429].includes(r.status),
  });
  sleep(0.5);
}

export function testShorten(data) {
  if (!data || !data.token) {
    return;
  }
  const res = http.post(`${BASE}/url/shorten`,
    JSON.stringify({ longUrl: `https://example.com/load-${__VU}-${__ITER}` }),
    {
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${data.token}`,
      },
      timeout: "15s",
    });

  errorRate.add(res.status !== 200);
  check(res, { "shorten 200": (r) => r.status === 200 });
  sleep(1);
}