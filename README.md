# Seat Reservation at Scale

A JSON API that sells assigned seats for a show without ever double-selling a seat, exceeding a
user's booking limit, or double-charging a retried request — even under thousands of concurrent
buyers stampeding the same show. Built for the Paytm Money backend take-home.

See `WRITEUP.md` for the design rationale (atomic decision, idempotency, holds & expiry,
consistency vs. availability, observability, AI usage).

## Stack

Java 21 + Spring Boot 3 (virtual threads), PostgreSQL + Flyway + Spring Data JPA, JWT auth,
Micrometer/Prometheus metrics, JSON logs. See `docker-compose.yml` for the full local stack.

## Run locally

Requires Docker and Docker Compose. No local Java/Maven install needed.

```bash
git clone https://github.com/Ishita2803/seat-reservation-tha-ptm.git
cd seat-reservation-tha-ptm
docker compose up -d --build
```

Wait for the app to report healthy, then:

```bash
curl http://localhost:8080/healthz
# {"status":"UP"}
```

Postgres is not exposed on the host (`docker-compose.yml` publishes no port for it) — only the
app's port 8080 is reachable from outside the compose network.

## Auth

Tokens are signed JWTs. Mint one with the admin secret (default `dev-admin-secret` locally,
set via the `ADMIN_SECRET` env var in `docker-compose.yml` for anything beyond local dev):

```bash
# Admin token (required for POST /shows)
curl -X POST http://localhost:8080/auth/tokens \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Secret: dev-admin-secret' \
  -d '{"user_id":"admin1","admin":true}'

# Regular user token
curl -X POST http://localhost:8080/auth/tokens \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Secret: dev-admin-secret' \
  -d '{"user_id":"alice"}'
```

Use the returned token as `Authorization: Bearer <token>` on every other endpoint.

## API

| Method | Path | Auth | Notes |
|---|---|---|---|
| `POST` | `/shows` | admin | Creates a show with every seat `available`. |
| `GET` | `/shows/{id}` | any user | Per-seat status + counts; `available + held + confirmed == total_seats` always. |
| `POST` | `/shows/{id}/reserve` | any user | Holds the requested seats for 5 min. All-or-nothing. Requires `Idempotency-Key` (header or body). |
| `POST` | `/reservations/{id}/confirm` | owner only | Held → confirmed. 409 `hold_expired` if the hold expired. |
| `POST` | `/reservations/{id}/cancel` | owner only | Releases a held or confirmed reservation. |
| `GET` | `/healthz` | public | Liveness: process is up. |
| `GET` | `/readyz` | public | Readiness: runs `SELECT 1`; 503 when the DB is down. |
| `GET` | `/metrics` | public | Prometheus exposition format. |

Identity always comes from the JWT, never the request body — a spoofed `user_id` in a request
body is ignored.

Reserve returns `"status": "held"` by design, not `"confirmed"` — confirming is a deliberate
second step. See `WRITEUP.md` for why.

## Burst testing

`./burst.sh <BASE_URL>` runs a k6 load test via Docker (no local k6 install needed):

```bash
# Against the local compose stack (not localhost -- see note below)
./burst.sh http://host.docker.internal:8080

# Against a deployed instance
./burst.sh https://<live-url>
```

It runs five scenarios — a hot-seat storm, a general stampede (~20k requests), a per-user-limit
test, idempotency retries, and a spoofed-identity check — then prints outcome counts and a final
reconciliation against `GET /shows/{id}` and `/metrics` for every scenario.

> Against a local compose stack, use `http://host.docker.internal:8080`, not `localhost` — the
> k6 container's `localhost` is itself, not your machine.

## Metrics & logs

- `/metrics`: `reservations_held_total`, `reservations_confirmed_total`,
  `reservations_declined_total{reason}`, `reservations_idempotent_replay_total`,
  `holds_expired_total`, `seats_available/held/confirmed{show}` gauges.
- Logs are structured JSON (one object per line) with a `requestId` field, propagated from the
  `X-Request-Id` request header (or generated if absent) and echoed back in the response.

## Deploy

Pending — see `WRITEUP.md` → "What's next" for the deployment plan (GCP Compute Engine +
Docker Compose + Caddy + Dozzle). This section will be filled in with the live URL and log
access once deployed.

## Tests

```bash
./mvnw test
```

Integration tests use Testcontainers with real PostgreSQL (never H2), so the locking behaviour
under test matches what runs in production. Requires a working local Docker setup.
