# Deploying Sniplink

Production runs as four containers on a NAT'd VPS. GitHub Actions builds the images, pushes them to GitHub Container Registry, and SSHes in to restart the stack. The VPS never compiles anything.

## Architecture

The VPS has **no public IPv4 of its own**. It is an LXC container with a private address (`10.10.10.91/24`) behind AIC Cloud's NAT; the public IP `37.187.159.43` is shared with other tenants, and AIC's own Caddy owns ports 80 and 443 on it. So this stack cannot terminate TLS, and there is no reverse proxy of our own.

Instead, AIC's panel maps the hostname to a port on the VPS, and their edge handles the certificate:

```
   client
     │  https://sniplink.dedyn.io
     ▼
  AIC Cloud shared proxy      TLS terminated here (auto-SSL, their cert)
     │  http, Host: sniplink.dedyn.io
     ▼
  10.10.10.91:3000            published by the `ui` container
     │
  ┌──▼────────────────┐
  │ ui  (nginx)       │       serves the SPA *and* routes:
  │                   │         /api/*, /swagger-ui*, /v3/api-docs*
  │                   │         ^/[0-9a-zA-Z]{1,10}$   (short codes)
  └──┬────────────────┘
  ┌──▼──────────┐
  │ api :8080   │             not published
  └──┬───────┬──┘
┌────▼───┐ ┌─▼──────┐
│postgres│ │ redis  │         internal network only, named volumes
└────────┘ └────────┘
```

`ui` is the app's router, not just a file server — see [sniplink-ui/nginx.conf](sniplink-ui/nginx.conf). Short links live at the web root, so any root path of pure letters and digits goes to the API and everything else falls through to the SPA. The Base62 alphabet contains no dots or slashes, so `/assets/index-abc123.js` can never be mistaken for a short code.

DNS is deSEC: zone `sniplink.dedyn.io`, apex `A` → `37.187.159.43`.

---

## Current state

Already provisioned on the VPS, so this section is history rather than instructions:

- Ubuntu 24.04 LXC, 2 vCPU, 4 GB RAM, 40 GB disk
- Docker Engine 29.7.1 + Compose v5.4.0 from Docker's apt repo
- User `deploy` (groups: `sudo`, `docker`), SSH key-only
- `/opt/sniplink` owned by `deploy`, containing `.env` (mode 600, password generated on the server)
- Logged into `ghcr.io` as `deploy`
- Unattended security upgrades enabled

GitHub secrets: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_SSH_KNOWN_HOSTS`. Variable: `VPS_PORT=20086`.

### Two deliberate omissions

**No swap was added.** This is an LXC container; `swapon` is not permitted inside one. The 512 MB shown in `free` is provided by the host cgroup. 4 GB of RAM is enough, and the API container is capped at 1 GB so the JVM cannot crowd out Postgres.

**No `ufw`.** It would be theatre here. The NAT gateway already blocks everything except the single forwarded SSH port, and Docker's published ports bypass ufw's INPUT chain anyway. See *Hardening follow-up* below for the one exposure that is real.

---

## How a deploy works

Push to `main` (or **Actions → Deploy → Run workflow**):

1. **build** — both images built in parallel, pushed to GHCR as `sha-<short>` and `latest`.
2. **deploy** — rsyncs `deploy/` to `/opt/sniplink/` *excluding `.env`*, then runs `deploy.sh sha-<short>`.
3. `deploy.sh` writes the tag into `.env`, pulls, restarts, and waits for the API to report healthy. **If it does not come up within 150 s it restores the previous tag** — a bad build leaves the running site untouched.

Health means `/actuator/health/readiness`, whose group includes `db`. Spring's default readiness group is only `readinessState`, which reports UP with the database down and would make this gate meaningless. Redis is deliberately *excluded*: the cache and rate limiter both fail open, so an outage there should degrade the app, not restart-loop a container that is still serving correctly.

---

## Operations

All from `/opt/sniplink` as `deploy`.

```bash
docker compose ps                     # what is running and how healthy
docker compose logs -f api            # follow one service
docker compose logs --tail=200 ui     # nginx: routing and client IPs
docker compose restart api
docker compose up -d                  # apply an edited .env
docker stats --no-stream
```

Database shell:

```bash
docker compose exec postgres psql -U sniplink -d sniplink
```

**Roll back.** Any previously built commit is still in GHCR:

```bash
./deploy.sh sha-1a2b3c4
```

Same health gate and automatic rollback as a forward deploy.

**Change the port.** Edit `APP_PORT` in `.env`, `docker compose up -d`, and update the mapping in the AIC panel to match. Both must agree.

**Change the domain.** Update the deSEC A record, the AIC domain mapping, and `APP_BASE_URL` in `.env`, then `docker compose up -d`. `APP_BASE_URL` is both the prefix for every short URL returned and the sole allowed CORS origin.

---

## Backups

`backup.sh` writes a gzipped `pg_dump` to `/opt/sniplink/backups` and prunes past seven days.

```bash
mkdir -p /opt/sniplink/backups
crontab -e
```

```cron
15 3 * * * /opt/sniplink/backup.sh >> /opt/sniplink/backups/backup.log 2>&1
```

Restore:

```bash
gunzip -c backups/sniplink-20260806-031500.sql.gz | \
  docker compose exec -T postgres psql -U sniplink -d sniplink
```

Backups on the same VPS do not survive losing the VPS. Pull them off periodically:

```bash
rsync -az -e 'ssh -p 20086' deploy@37.187.159.43:/opt/sniplink/backups/ ./sniplink-backups/
```

---

## Hardening follow-up

The VPS shares a private subnet (`10.10.10.0/24`) with other AIC tenants, and port 3000 is published on all interfaces because AIC's proxy reaches it over that network. A neighbour on the same subnet could therefore hit the app directly, bypassing the TLS edge.

Once AIC's proxy source address is known — read it from `docker compose logs ui` after real traffic — restrict it in the `DOCKER-USER` chain (ufw cannot do this; Docker bypasses it):

```bash
iptables -I DOCKER-USER -p tcp --dport 3000 ! -s <aic-proxy-ip> -j DROP
```

Persist with `iptables-persistent`. Severity is low — the app is public anyway — but it also means unmetered access that skips the edge.

---

## Troubleshooting

**502 / 503 from the domain.** AIC's proxy cannot reach `10.10.10.91:3000`. Check `docker compose ps` (is `ui` up?) and that `APP_PORT` matches the panel mapping.

**`api` stuck `starting` or `unhealthy`.** `docker compose logs api`. Almost always the datasource: `POSTGRES_PASSWORD` in `.env` no longer matches what the `pgdata` volume was initialised with. Postgres reads those variables *only* when creating the data directory, so editing the password later changes nothing — use `ALTER USER` in psql instead, or destroy the volume and the data with it.

**Short links 404 but the UI loads.** The nginx short-code regex isn't matching. It is quoted in `nginx.conf` because unquoted `{1,10}` braces collide with nginx block syntax.

**Wrong client IPs in analytics, or rate limiting keying everyone together.** The `set_real_ip_from` range in `nginx.conf` must contain AIC's proxy address. Check what `$remote_addr` actually is in the `ui` access log.

**Deploy fails pulling from GHCR.** Re-run `docker login ghcr.io` as `deploy` with a PAT scoped `read:packages`.

**Deploy fails at SSH.** If the VPS was rebuilt its host key changed; regenerate `VPS_SSH_KNOWN_HOSTS` with `ssh-keyscan -H -p 20086 37.187.159.43`.

---

## Local development

Unchanged — see [README.md](README.md). `dev.cmd` runs the API and UI directly against a native Postgres and Redis; none of the above is needed to work on the code.
