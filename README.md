# Sniplink

A URL shortener with Base62 short codes, Redis-cached redirects, and asynchronous click analytics.

**Live at [sniplink.dedyn.io](https://sniplink.dedyn.io)** — [API docs](https://sniplink.dedyn.io/swagger-ui.html)

## Features

- **Collision-free short codes** — Base62 encoding of the database id, so there is no generate-and-retry loop and no duplicate check.
- **Sub-millisecond redirects** — Redis caches `shortCode → originalUrl` with a 24-hour TTL; Postgres is the fallback and the source of truth.
- **Non-blocking click tracking** — every redirect logs IP, user agent, and referrer on a Java 21 virtual thread, off the request path.
- **Per-IP rate limiting** — Redis `INCR` in a fixed one-minute window, 30 requests/min on the shorten endpoint.
- **Idempotent shortening** — submitting the same URL twice returns the original short link instead of a second one.
- **Interactive API docs** — Swagger UI generated from the controllers and DTOs.

## Tech stack

Java 21 · Spring Boot 3.5.16 · Spring Data JPA · PostgreSQL · Redis · Maven · springdoc-openapi · React 19 · Vite · TypeScript · Tailwind CSS v4

## Quick start

### Prerequisites

- **JDK 21** — Maven itself is not needed; the bundled wrapper (`mvnw`) downloads it.
- **PostgreSQL** running on `localhost:5432`
- **Redis** running on `localhost:6379` (Memurai works as a drop-in on Windows)

### 1. Create the database

```sql
CREATE ROLE sniplink LOGIN PASSWORD 'sniplink';
CREATE DATABASE sniplink OWNER sniplink;
```

Making `sniplink` the database owner is what lets Hibernate create its tables on first boot. Tables and sequences are generated automatically via `ddl-auto: update` — there are no migrations to run.

### 2. Start everything

```powershell
.\dev.cmd
```

That's it. The launcher checks the environment first — JDK 21, PostgreSQL, Redis, the database, free ports, UI dependencies — and if something is wrong it says exactly what to do instead of failing halfway through a boot. Then it opens the API and the UI in their own windows and waits until both answer.

```
  UI    http://localhost:5173
  API   http://localhost:8080
  Docs  http://localhost:8080/swagger-ui.html
```

| Command | Does |
|---|---|
| `.\dev.cmd` | Start the API and UI |
| `.\dev.cmd stop` | Shut down whatever was started |
| `.\dev.cmd status` | Report what's running; changes nothing |

From git-bash, use `./dev.sh` with the same subcommands.

The launcher never touches your database — if the role or database is missing it prints the `CREATE` statements and stops.

> `dev.cmd` is a shim that runs `dev.ps1` with `-ExecutionPolicy Bypass`. That's process-scoped and needs no admin rights, so a default `Restricted` PowerShell profile won't block it and nothing on your machine changes.

<details>
<summary><b>Running without the launcher</b></summary>

Two terminals. The backend needs `JAVA_HOME` pointed at a JDK 21 — on a machine where the `java` on `PATH` is an older release, the build fails without it.

```powershell
# terminal 1
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-21.0.12+8"
cd sniplink-api
.\mvnw.cmd spring-boot:run

# terminal 2
cd sniplink-ui
npm install
npm run dev
```

</details>

UI on <http://localhost:5173>. Vite proxies `/api` to the backend, so no CORS round trip in development.

## API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/shorten` | Shorten a URL. Returns the existing link if already shortened. |
| `GET` | `/{code}` | 302 redirect to the original URL; records the click. |
| `GET` | `/api/v1/analytics/{code}` | Total clicks plus the 20 most recent click events. |
| `DELETE` | `/api/v1/urls/{code}` | Delete the link, its click history, and its cache entry. |

Errors share one shape:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Short URL 'xyz' not found",
  "timestamp": "2026-08-05T14:30:00"
}
```

### Examples

Shorten:

```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/some/long/path"}'
```

```json
{
  "shortUrl": "http://localhost:8080/1",
  "shortCode": "1",
  "originalUrl": "https://example.com/some/long/path",
  "createdAt": "2026-08-05T14:30:00"
}
```

Analytics:

```bash
curl http://localhost:8080/api/v1/analytics/1
```

```json
{
  "shortCode": "1",
  "originalUrl": "https://example.com/some/long/path",
  "totalClicks": 3,
  "createdAt": "2026-08-05T14:30:00",
  "recentClicks": [
    {
      "ipAddress": "127.0.0.1",
      "userAgent": "curl/8.4.0",
      "referrer": null,
      "clickedAt": "2026-08-05T15:02:00"
    }
  ]
}
```

## Design notes

A few implementation choices worth knowing about:

- **`short_code` is nullable in the schema but never null in practice.** The code is derived from the id, so it cannot exist until the row does. Hibernate snapshots entity state when `persist()` queues the insert, so a value assigned afterwards lands in a follow-up `UPDATE` — a `NOT NULL` column would reject the insert outright. Both statements run in one transaction, so no other session observes the intermediate null, and the `UNIQUE` constraint still applies.
- **The redirect path variable is constrained** to `{code:[0-9a-zA-Z]{1,10}}`. A bare `/{code}` at the root outranks static resource handling and would swallow `/swagger-ui.html`.
- **Click logging lives in its own bean** (`ClickLoggingService`). `@Async` works through a proxy, so calling it from a sibling method on the same class would bypass the proxy and quietly run synchronously.
- **The click counter is incremented with an atomic `UPDATE`**, not a read-modify-write, since every increment runs on a separate virtual thread.
- **Only `http` and `https` URLs are accepted.** The stored value goes straight into a `Location` header, so allowing `javascript:` would make every short link a script-injection vector.
- **Analytics can lag by milliseconds.** Click logging is fire-and-forget, so a click issued microseconds ago may not be counted yet.

## Configuration

Defaults live in `sniplink-api/src/main/resources/application.yml` and can be overridden with standard Spring environment variables:

| Property | Default | Purpose |
|---|---|---|
| `app.base-url` | `http://localhost:8080` | Prefix used to build returned short URLs |
| `app.rate-limit.requests-per-minute` | `30` | Shorten-endpoint limit per IP |
| `app.cache.url-ttl-hours` | `24` | Redis TTL for resolved URLs |
| `app.cors.allowed-origins` | `http://localhost:5173` | Allowed browser origins |

## Deploying

Production runs as five containers behind Caddy — API, UI, Postgres, Redis, and the proxy — on a single VPS, with GitHub Actions building the images and rolling them out on every push to `main`. Full VPS setup commands, the secrets to configure, and the operations runbook are in [DEPLOYMENT.md](DEPLOYMENT.md).

## Tests

```bash
cd sniplink-api
./mvnw test
```

Covers the Base62 codec — encoding, decoding, round-trips across magnitudes, and rejection of out-of-alphabet and negative input. These run without Postgres or Redis.

## Screenshots

_Add a screenshot of the frontend and of Swagger UI here._
