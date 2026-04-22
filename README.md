# URL Shortner

Production-ready serverless URL shortener built with Java Spring Boot Lambda handlers, React frontend, DynamoDB sharding, Redis caching, SQS analytics, and CloudWatch observability.

## Live Deployment

- Frontend (CloudFront): https://dy4r6as5xlueq.cloudfront.net
- API Gateway (Prod): https://zxh4ps7huh.execute-api.ap-south-1.amazonaws.com/prod

## Core Features

- JWT-based authentication (register/login)
- URL shortening with Base62 short codes
- Consistent-hash sharding across DynamoDB URL shard tables
- Redirect path with Redis cache read-through
- Per-IP redirect rate limiting (Redis INCR window)
- URL expiry support (`expiresAt`) with `410 Gone` handling
- User-scoped delete authorization (`403` on non-owner delete)
- Click analytics pipeline: redirect -> SQS -> analytics Lambda -> DynamoDB
- CloudWatch dashboard for Lambda, DynamoDB, SQS, and Redis metrics

## Architecture

- Frontend: Vite + React (`url-frontend`)
- Backend: Spring Boot Java handlers packaged for AWS Lambda (`url-backend`)
- API: Amazon API Gateway (prod stage)
- Data:
  - DynamoDB shards for URLs (`url-shard-a`, `url-shard-b`, `url-shard-c`)
  - DynamoDB clicks table for analytics
- Cache/Rate-limit: ElastiCache Redis
- Queue: SQS (`url-analytics-queue`)
- Async analytics processor: `url-analytics` Lambda
- Monitoring: CloudWatch dashboard (`url-shortner`)

## Repository Layout

- `url-backend/` Java Lambda handlers, build scripts, infra helper scripts
- `url-frontend/` React app and frontend deployment config
- `load-test.js` k6 load profile used for performance validation
- `cw-dashboard.json` CloudWatch dashboard definition

## API Endpoints (Prod)

- `POST /auth/register`
- `POST /auth/login`
- `POST /url/shorten` (auth required)
- `GET /url/my` (auth required)
- `DELETE /url/{id}` (auth required, owner only)
- `GET /{shortcode}` (public redirect)

## Local Development

### Backend

1. `cd url-backend`
2. Configure env vars used in `application.properties`:
   - `JWT_SECRET`
   - `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`
   - `REDIS_HOST`, `REDIS_PORT`
   - `APP_BASE_URL`
3. Build:
   - `mvn clean package -DskipTests`

### Frontend

1. `cd url-frontend`
2. Install dependencies:
   - `npm install`
3. Run dev server:
   - `npm run dev`

## Performance Snapshot (k6, realistic profile)

Using `load-test.js` with peak 25 redirect VUs + 2 shorten req/s:

- Total requests: 5639
- Error rate: 1.96%
- Redirect p95 latency: 214.23 ms
- Shorten p95 latency: 918.66 ms
- Overall p95 latency: 247.93 ms
- Throughput: ~25.76 req/s

## Observability

CloudWatch dashboard includes widgets for:

- Lambda invocations/errors/throttles/init duration
- Lambda p95 duration
- DynamoDB consumed RCU/WCU
- SQS queue depth and send/delete rates
- ElastiCache current connections, cache hits, cache misses

## Notes

- The repository currently contains Lambda packaging artifacts and dependency layer binaries generated during deployment workflows.
- For cleaner source-only history in future commits, add artifact exclusion rules in `.gitignore` before committing build outputs.
