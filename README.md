# ShortX — Distributed URL Shortener

> A production-oriented, horizontally scalable URL-shortening API built with Spring Boot, PostgreSQL, and Redis.

ShortX is an API backend: it has no web or mobile user interface. It creates and manages short links, redirects visitors, secures accounts with JWTs, and records click analytics. The repository includes Docker Compose, tests, database migrations, Prometheus, Grafana, and GitHub Actions CI.

---

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Folder Structure](#folder-structure)
- [Scalability Design](#scalability-design)
- [Getting Started](#getting-started)
- [API Overview](#api-overview)
- [Testing](#testing)
- [Observability](#observability)
- [GitHub and security checklist](#github-and-security-checklist)
- [Known Limitations](#known-limitations)
- [Future Improvements](#future-improvements)

---

## Architecture

```
                              ┌─────────────────┐
                              │   Load Balancer   │
                              └────────┬─────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
             ┌───────────┐      ┌───────────┐      ┌───────────┐
             │ ShortX #1 │      │ ShortX #2 │      │ ShortX #N │   (stateless,
             └─────┬─────┘      └─────┬─────┘      └─────┬─────┘   horizontally scaled)
                    │                  │                  │
        ┌───────────┼──────────────────┼──────────────────┼───────────┐
        ▼           ▼                  ▼                  ▼           ▼
  ┌──────────┐ ┌──────────────────────────────┐   ┌────────────────────────┐
  │  Redis   │ │  PostgreSQL (primary)         │   │  Prometheus + Grafana  │
  │  cache + │ │  + read replicas (future)     │   │  (metrics/dashboards)  │
  │  click   │ └──────────────────────────────┘   └────────────────────────┘
  │  buffer  │
  │  + rate  │
  │  limiter │
  └──────────┘
```

**Request flow — a redirect (the hot path):**
`GET /{code}` → `RedirectController` → `ShortUrlService.resolveForRedirect` → Redis cache-aside lookup (cache hit: skip Postgres entirely) → validate active/expiry/password → **302 response returned immediately** → *asynchronously, off the response path*: `ClickEventRecorder` parses geo/device/browser and inserts a `click_events` row; a Redis `INCR` buffers the click count for later batch reconciliation into Postgres.

**Request flow — creating a link:**
`POST /api/v1/urls` → Bean Validation (`@ValidUrl`, custom alias pattern) → `ShortUrlService.create` → Snowflake ID generated in-process (no DB round-trip, no central sequence) → Base62-encoded into the public short code (or the caller's custom alias is used verbatim) → persisted.

## Tech Stack

| Layer | Choice |
|---|---|
| Language / Runtime | Java 21, Spring Boot 3.4 |
| Persistence | PostgreSQL 16, Spring Data JPA, Flyway migrations |
| Cache / Buffer / Rate limiting | Redis 7 (Lettuce), Bucket4j |
| Security | Spring Security, stateless JWT (jjwt), BCrypt |
| Mapping | MapStruct, Lombok |
| Docs | springdoc-openapi (Swagger UI) |
| Observability | Micrometer → Prometheus → Grafana |
| Analytics | ua-parser (device/browser), MaxMind GeoIP2 (geo) |
| Testing | JUnit 5, Mockito, Testcontainers, JaCoCo |
| CI/CD | GitHub Actions, multi-stage Docker build, GHCR |

## Folder Structure

```
src/main/java/com/shortx/
├── controller/    REST endpoints (ShortUrl, Redirect, Auth, Analytics)
├── service/       Business logic (ShortUrlService, AuthService) + specification/ (dynamic JPA filters)
├── repository/    Spring Data JPA interfaces + projection/ (typed aggregation results)
├── entity/        JPA entities mapping 1:1 to the Flyway-managed schema
├── dto/           request/ and response/ - the API's actual contract, decoupled from entities
├── mapper/        MapStruct entity <-> DTO translation
├── config/        Spring bean wiring (Security, Redis, Async, OpenAPI, Password)
├── exception/     Domain exceptions + GlobalExceptionHandler (uniform error responses)
├── security/      JWT issuance/validation, rate limiting, principal adaptation
├── cache/         Redis cache-aside + the click-count write buffer
├── analytics/     UA/geo parsing, async click recording, analytics aggregation reads
├── scheduler/      Background jobs (click-count reconciliation, expired-URL sweep)
├── util/          Stateless helpers (Snowflake ID gen, Base62, hashing, IP extraction)
└── common/        Cross-cutting constants, enums, Bean Validation extensions

src/main/resources/
├── db/migration/  Flyway SQL migrations (V1-V6) - the source of truth for schema
└── application*.yml  Externalized config, profile-layered (local/docker/test)
```

Every package boundary above was a deliberate call made and explained in the commit history as the project was built — see individual commit messages for the reasoning behind each one (e.g. why `analytics` is separate from `service`, why entities don't map certain collections, why click counting bypasses optimistic locking).

## Scalability Design

- **Snowflake IDs, not DB sequences**: `short_urls` and `click_events` (the two highest-write-volume tables) use 64-bit IDs generated in-process (timestamp + worker-id + sequence bits), not `SERIAL`/`IDENTITY`. No central sequence to coordinate across instances, no UUID-index fragmentation.
- **Base62 encoding**: the public short code is a Base62 encoding of the Snowflake ID — URL-safe, no escaping needed, naturally ~10-11 characters today and growing by roughly one character only every few hundred years.
- **Redis cache-aside** for the redirect hot path, with write-through invalidation on every edit/delete and a TTL as a safety net.
- **Click counting never hits Postgres per-request**: every redirect does a Redis `INCR`; a scheduled job batches the deltas into a single bulk `UPDATE` per URL, deliberately bypassing optimistic locking so viral-link traffic never contends with a settings edit happening in another tab.
- **Optimistic locking (`@Version`)** guards metadata edits (expiry, password, active flag) specifically — a different, much lower-frequency access pattern than click counting, so it's guarded differently on purpose.
- **Purpose-built indexes**, not blanket ones: partial indexes on `is_active` for the expiry sweep and the Top-URLs leaderboard, a lean single composite index on the append-only `click_events` table since every additional index there is pure write-amplification cost.
- **Horizontal scaling**: the app is fully stateless (JWT auth, no session affinity needed); Redis-backed rate limiting and (optionally) Bucket4j buckets are shared across instances, not per-instance.
- **Read replicas / Kafka**: not implemented in this codebase (see [Future Improvements](#future-improvements)) — noted here as the natural next steps once a single Postgres primary or synchronous click-event writes become the bottleneck, not because they were forgotten.

## Getting Started

### Prerequisites
- Docker + Docker Compose
- (For local non-Docker dev) JDK 21, Maven, a local Postgres 16 and Redis 7

### Run everything with Docker Compose

```bash
cp .env.example .env
# edit .env: set JWT_SECRET to a real value, e.g.:
#   openssl rand -base64 48

docker compose up --build
```

`JWT_SECRET` is mandatory. Do not use the fallback value in `application.yml` in any deployed environment. The PostgreSQL and Grafana passwords in `docker-compose.yml` are development defaults only; replace them and avoid exposing ports publicly before deploying.

This starts the app (`:8080`), Postgres (`:5432`), Redis (`:6379`), Prometheus (`:9090`), and Grafana (`:3000`, default login `admin`/`admin` unless overridden via `.env`).

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

### Try it

```bash
# Register (also logs you in)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"password123","fullName":"Your Name"}'

# Create a short URL (swap in the accessToken from the response above)
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"originalUrl":"https://example.com/some/very/long/path"}'

# Follow the redirect
curl -i http://localhost:8080/<shortCode>
```

### Run locally without Docker

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
(Requires Postgres/Redis reachable at the defaults in `application.yml`, or override via env vars.)

## GitHub and security checklist

This project is ready to initialize as a fresh Git repository. Before the first push:

1. Copy `.env.example` to `.env` and generate a real JWT secret with `openssl rand -base64 48`. Keep `.env` private.
2. Confirm that no database dumps, GeoIP databases (`*.mmdb`), certificates, IDE files, build outputs, or local data are staged. The supplied `.gitignore` excludes these.
3. Review `git status --ignored` and then `git diff --cached` before committing. Do not commit real passwords, tokens, private keys, or `.env`.
4. Run `mvn clean verify` and `docker compose up --build` locally. The test suite includes Testcontainers, so Docker must be running for integration tests.
5. Create a repository on GitHub, then add the remote and push only after the review is clean. The included workflow runs tests on pull requests and pushes images to GitHub Container Registry after pushes to `main`.

For deployment, provide secrets through the hosting platform's secret manager or environment variables. Do not rely on the default JWT value, local Postgres credentials, or Grafana's default password.

## API Overview

Full interactive documentation lives at `/swagger-ui.html` once running. Summary:

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Create an account, returns tokens |
| POST | `/api/v1/auth/login` | Public | Returns a new token pair |
| POST | `/api/v1/auth/refresh` | Public | Rotates a refresh token |
| POST | `/api/v1/auth/logout` | Public | Revokes a refresh token |
| POST | `/api/v1/urls` | Optional | Create a short URL (anonymous or owned) |
| GET | `/api/v1/urls/{shortCode}` | Public | Metadata for one short URL |
| GET | `/api/v1/urls` | **Required** | List your own URLs, filtered/paginated |
| GET | `/api/v1/urls/top` | Public | Leaderboard by click count |
| PATCH | `/api/v1/urls/{id}` | **Required** | Update expiry/password/active status (owner/admin) |
| DELETE | `/api/v1/urls/{id}` | **Required** | Delete a URL (owner/admin) |
| GET | `/api/v1/urls/{id}/analytics` | **Required** | Click totals, geo/device/browser breakdowns, daily trend |
| GET | `/{shortCode}` | Public | The actual redirect (302) |

## Testing

```bash
mvn verify
```

Runs unit tests (Mockito, no Spring context — `service`, `security.jwt`, `util` packages) and integration tests (Testcontainers spinning up a real Postgres 16 container — `integration` package), then enforces an 80% JaCoCo line-coverage gate over the codebase's actual logic (entities/DTOs/config wiring are excluded from that gate as boilerplate — see `pom.xml`).

## Observability

- `/actuator/health`, `/actuator/prometheus` — scraped by the bundled Prometheus service every 15s.
- Grafana auto-provisions the Prometheus datasource; drop dashboard JSON into `docker/grafana/provisioning/dashboards/` to have it appear automatically on next startup.
- Structured console logging includes a `traceId`/`userId` MDC pattern (see `application.yml`'s `logging.pattern.console`) for correlating log lines to a specific request.

## Known Limitations

Documented here rather than left for you to discover:

- **GeoIP requires a file this repo doesn't include.** `analytics.GeoIpService` needs `GeoLite2-City.mmdb` (a free MaxMind account is required to download it) at the path set by `shortx.geoip.database-path`. Without it, geo analytics silently disables itself — everything else keeps working.
- **Redis-backed rate limiting's exact Lettuce integration API was not compiler-verified** in the environment this project was built in (no Maven Central access). It has an automatic, logged fallback to per-instance in-memory rate limiting if that initialization fails for any reason — run `mvn compile` as your first step after cloning to confirm, and if it doesn't compile as-is, the fix is scoped entirely to `RateLimitService.init()`.
- **No `mvn compile`/`mvn verify` was run during this project's construction** — this entire codebase was hand-written and reviewed (brace-balance checks, import/naming consistency, and the database layer was validated against a real local Postgres instance) but not compiler-verified, because Maven Central was not reachable from that sandboxed environment. Run `mvn clean verify` yourself as the first step.

## Future Improvements

- Kafka (or another queue) for click-event ingestion instead of `@Async` + a direct DB write, for durability under a burst that exceeds the thread pool's queue capacity
- Postgres read replicas, with analytics/reporting queries routed to a replica
- `click_events` range partitioning by month once volume warrants it (the table and its access patterns were designed with this in mind from the start — see the Database migration's comments)
- A lock-free (CAS-based) variant of `SnowflakeIdGenerator` if profiling ever shows the current `synchronized` implementation under real contention
- Short-URL redirect traffic served from its own subdomain/route, fronted by a CDN, scaled independently from the management API (see `RedirectController`'s Javadoc)
- Multi-role support beyond `ROLE_USER`/`ROLE_ADMIN`, with `@PreAuthorize`-gated admin-only endpoints (the `@EnableMethodSecurity` wiring is already in place)
