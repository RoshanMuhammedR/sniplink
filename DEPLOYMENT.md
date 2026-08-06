# Deploying Sniplink

Production runs as five containers on a single VPS. GitHub Actions builds the images, pushes them to GitHub Container Registry, and SSHes in to restart the stack. **The VPS never compiles anything** — it only pulls images, which keeps a small box viable.

```
              :80 / :443
                  │
            ┌─────▼─────┐   Caddy — automatic TLS, renews itself
            │   caddy   │
            └─────┬─────┘
       ┌──────────┴───────────┐
  /api/*, /swagger-ui*,       │  everything else
  /v3/api-docs*,              │
  ^/[0-9a-zA-Z]+$             │
       ┌──────▼──────┐  ┌─────▼─────┐
       │  api :8080  │  │  ui :80   │  nginx serving the built SPA
       └──┬───────┬──┘  └───────────┘
   ┌──────▼──┐ ┌──▼──────┐
   │ postgres│ │  redis  │  internal network only, named volumes
   └─────────┘ └─────────┘
```

Only Caddy publishes ports. Postgres, Redis, the API, and the UI are reachable on the internal Docker network and nowhere else — there is no exposed database port to attack.

Short links live at the web root, so the edge proxy sends any root path made purely of letters and digits to the API and everything else to the SPA. Because the Base62 alphabet contains no dots or slashes, `/assets/index-abc123.js` and `/favicon.svg` can never be mistaken for a short code.

---

## 1. What you need first

| | |
|---|---|
| VPS | AIC Cloud, Ubuntu 24.04, 2 GB RAM recommended (1 GB works with swap) |
| Domain | A DNS **A record** pointing at the VPS IP |
| GitHub | This repo pushed to GitHub; Actions enabled |

Point DNS **before** starting the stack. Let's Encrypt validates over port 80 against the real hostname, so Caddy cannot get a certificate until the name resolves.

```bash
dig +short sniplink.example.com     # must print your VPS IP
```

---

## 2. Bootstrap the VPS

SSH in as root the first time.

**Create a non-root user** — the deploy workflow logs in as this user, not root.

```bash
adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh && chmod 700 /home/deploy/.ssh
```

Add your own public key so you keep interactive access (the CI key comes later):

```bash
echo "ssh-ed25519 AAAA... you@laptop" >> /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```

**Harden SSH.** Open a second terminal and confirm `ssh deploy@<ip>` works *before* running this — locking yourself out of a fresh VPS means rebuilding it.

```bash
sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/'            /etc/ssh/sshd_config
sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh
```

**Firewall** — only SSH and the web ports.

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

**Automatic security updates:**

```bash
apt-get update && apt-get install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades
```

**Swap** — cheap insurance so the JVM and Postgres never get OOM-killed together on a small box. Skip if you have 4 GB or more.

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

---

## 3. Install Docker

From Docker's official repository — Ubuntu's packaged `docker.io` is older and ships no Compose v2 plugin.

```bash
apt-get update
apt-get install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

usermod -aG docker deploy      # lets the deploy user run docker without sudo
```

Log out and back in as `deploy`, then check:

```bash
docker compose version
```

---

## 4. Give GitHub Actions an SSH key

Generate a **dedicated** keypair for CI — separate from your personal key, so it can be revoked on its own. Run this **on your laptop**, not the VPS:

```bash
ssh-keygen -t ed25519 -f ./sniplink_deploy_key -N "" -C "github-actions"
```

Install the public half on the VPS:

```bash
ssh-copy-id -i ./sniplink_deploy_key.pub deploy@<vps-ip>
```

Capture the host's fingerprint for the `VPS_SSH_KNOWN_HOSTS` secret — this pins the server identity so the workflow cannot be redirected to an impostor host:

```bash
ssh-keyscan -H <vps-ip>
```

---

## 5. Let the VPS pull from GHCR

The images are private by default. Create a **classic** GitHub PAT with only the `read:packages` scope, then on the VPS:

```bash
echo "<your-pat>" | docker login ghcr.io -u <your-github-username> --password-stdin
```

Credentials land in `~/.docker/config.json` and persist across reboots.

> Alternative: make both packages public on GitHub (**Packages → sniplink-api → Package settings → Change visibility**) and skip the login entirely. Public packages are world-readable — fine for build artifacts of an open-source app, not for anything with embedded secrets. These images contain none, so either choice is defensible.

---

## 6. Create the stack directory

As `deploy` on the VPS:

```bash
sudo mkdir -p /opt/sniplink
sudo chown deploy:deploy /opt/sniplink
```

The workflow rsyncs `deploy/` here on every run. Seed `.env` by hand once — CI never writes or overwrites it:

```bash
cd /opt/sniplink
# paste the contents of deploy/.env.example, or scp it up
nano .env
chmod 600 .env
```

Generate a real database password:

```bash
openssl rand -base64 32
```

Fill in at minimum:

```ini
DOMAIN=sniplink.example.com
ACME_EMAIL=you@example.com
APP_BASE_URL=https://sniplink.example.com
GHCR_OWNER=your-github-username
IMAGE_TAG=latest
POSTGRES_DB=sniplink
POSTGRES_USER=sniplink
POSTGRES_PASSWORD=<the generated password>
```

`APP_BASE_URL` must match `DOMAIN` exactly, including `https://` and **no trailing slash** — it is both the prefix for every short URL the API returns and the sole allowed CORS origin.

There is no schema to install. Postgres creates the database owned by `POSTGRES_USER`, and Hibernate's `ddl-auto: update` builds the tables on first boot, exactly as it does locally.

---

## 7. Add the GitHub secrets

**Settings → Secrets and variables → Actions.**

| Secret | Value |
|---|---|
| `VPS_HOST` | VPS IP or hostname |
| `VPS_USER` | `deploy` |
| `VPS_SSH_KEY` | Full contents of `sniplink_deploy_key` (the private half, including the BEGIN/END lines) |
| `VPS_SSH_KNOWN_HOSTS` | Output of `ssh-keyscan -H <vps-ip>`. Optional — omitted, the workflow trusts on first use instead |

Under the **Variables** tab, only if your SSH port is not 22:

| Variable | Value |
|---|---|
| `VPS_PORT` | e.g. `2222` |

No application secret goes into GitHub. The database password, domain, and ACME email live only in `/opt/sniplink/.env`.

---

## 8. First deploy

Push to `main`, or run **Actions → Deploy → Run workflow**.

The run does three things: builds both images in parallel and pushes them to GHCR tagged `sha-<short>` and `latest`; rsyncs `deploy/` to the VPS excluding `.env`; then runs `deploy.sh`, which pulls, restarts, and waits for the API to report healthy. If the API does not come up within 150 seconds it restores the previous tag and fails the run — a broken build leaves the running site untouched.

Watch the first one from the VPS:

```bash
cd /opt/sniplink && docker compose logs -f
```

Certificate issuance takes a few seconds on first boot. `caddy` logging `certificate obtained successfully` means TLS is live.

### Check it worked

```bash
docker compose ps        # five services, api/postgres/redis healthy
```

```bash
curl -X POST https://sniplink.example.com/api/v1/shorten \
  -H 'Content-Type: application/json' -d '{"url":"https://example.com/x"}'

curl -i  https://sniplink.example.com/1          # 302 to example.com
curl     https://sniplink.example.com/api/v1/analytics/1
curl -I  https://sniplink.example.com/           # the SPA, not the API
curl -I  https://sniplink.example.com/actuator/health   # 404 — not routed publicly
```

Open the domain in a browser: the UI loads over a valid certificate, and `/swagger-ui.html` serves the API docs.

---

## 9. Everyday operations

All commands run from `/opt/sniplink`.

```bash
docker compose ps                     # what is running and how healthy
docker compose logs -f api            # follow one service
docker compose logs --tail=200 caddy  # TLS and routing problems
docker compose restart api            # restart one service
docker compose up -d                  # apply an edited .env or Caddyfile
docker stats --no-stream              # memory and CPU per container
```

Database shell:

```bash
docker compose exec postgres psql -U sniplink -d sniplink
```

**Roll back to an earlier build.** Any previously built commit is still in GHCR:

```bash
./deploy.sh sha-1a2b3c4
```

Same health gate and same automatic rollback as a forward deploy.

**Change the domain.** Edit `DOMAIN` and `APP_BASE_URL` in `.env`, then `docker compose up -d`. Caddy requests a certificate for the new name on restart. Update DNS first.

---

## 10. Backups

`backup.sh` writes a gzipped `pg_dump` to `/opt/sniplink/backups` and prunes anything older than seven days. Schedule it:

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

Copies stored only on the same VPS do not survive losing the VPS — pull them somewhere else periodically:

```bash
rsync -az deploy@<vps-ip>:/opt/sniplink/backups/ ./sniplink-backups/
```

Worth knowing: the `caddy_data` volume holds your TLS certificates. Deleting it forces a full re-issue and consumes Let's Encrypt rate limit, so leave it alone during cleanups.

---

## 11. Troubleshooting

**No certificate / browser warning.** Confirm `dig +short <domain>` returns the VPS IP, that ports 80 and 443 are open in `ufw` *and* in any AIC Cloud panel-level firewall, and that `DOMAIN` in `.env` is a bare hostname with no scheme. Then `docker compose logs caddy`.

**`api` stuck in `starting` or `unhealthy`.** `docker compose logs api`. Almost always the datasource: `POSTGRES_PASSWORD` in `.env` no longer matches what the `pgdata` volume was initialised with. Postgres only reads those variables when creating the data directory, so changing the password later has no effect on the existing database — change it with `ALTER USER` in psql instead, or destroy the volume (which destroys the data).

**502 from Caddy.** The upstream is down; `docker compose ps` shows which. Caddy retries automatically once it recovers.

**Redis down but the site still works.** Expected. Redis is deliberately excluded from the API's readiness group: the cache falls back to Postgres and the rate limiter fails open, so an outage degrades performance rather than breaking the app, and the container stays healthy instead of restart-looping. Watch for `Could not cache short code` warnings in `docker compose logs api`.

**Deploy fails at the pull step.** GHCR auth expired or was never set up — redo the `docker login ghcr.io` in step 5. A PAT with only `read:packages` is enough.

**Deploy fails at the SSH step.** If you pinned `VPS_SSH_KNOWN_HOSTS` and later rebuilt the VPS, its host key changed; re-run `ssh-keyscan` and update the secret.

**Disk filling up.** `docker system df`, then `docker image prune -a -f`. `deploy.sh` already prunes dangling images after each successful deploy.

---

## Local development

Unchanged — see [README.md](README.md). `dev.cmd` still runs the API and UI directly against a native Postgres and Redis; none of the above is needed to work on the code.

To exercise the full containerised stack on a machine with Docker, copy `deploy/.env.example` to `deploy/.env`, set `DOMAIN=:80` and `APP_BASE_URL=http://localhost` so Caddy serves plain HTTP, build the images locally, and `docker compose up -d`.
