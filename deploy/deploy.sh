#!/usr/bin/env bash
#
# Roll the stack onto a new image tag, gated on the API reporting healthy.
# Run on the VPS by the deploy workflow:  ./deploy.sh sha-1a2b3c4
#
# On failure the previous tag is restored, so a bad build leaves the site up.

set -euo pipefail

STACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$STACK_DIR"

HEALTH_TIMEOUT=150 # seconds; the API image allows a 60s start period

NEW_TAG="${1:-}"
if [ -z "$NEW_TAG" ]; then
	echo "usage: $(basename "$0") <image-tag>" >&2
	exit 2
fi

if [ ! -f .env ]; then
	echo "error: no .env in $STACK_DIR — copy .env.example to .env and fill it in" >&2
	exit 2
fi

# Remember what is running now so a failed rollout can be undone.
PREV_TAG="$(sed -n -E 's/^IMAGE_TAG=(.*)$/\1/p' .env | tail -1)"
PREV_TAG="${PREV_TAG:-latest}"

set_tag() {
	if grep -qE '^IMAGE_TAG=' .env; then
		sed -i -E "s|^IMAGE_TAG=.*|IMAGE_TAG=$1|" .env
	else
		printf 'IMAGE_TAG=%s\n' "$1" >>.env
	fi
}

# Poll the api container's Docker health status, which reads
# /actuator/health/readiness — so "healthy" means Postgres is reachable, not
# merely that the JVM started. Redis is excluded from the readiness group on
# purpose; the app fails open without it.
wait_for_api() {
	local waited=0 cid status
	while [ "$waited" -lt "$HEALTH_TIMEOUT" ]; do
		cid="$(docker compose ps -q api || true)"
		if [ -n "$cid" ]; then
			status="$(docker inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo starting)"
			case "$status" in
			healthy) return 0 ;;
			esac
		fi
		sleep 5
		waited=$((waited + 5))
		printf '  ... waiting for api to become healthy (%ss/%ss)\n' "$waited" "$HEALTH_TIMEOUT"
	done
	return 1
}

echo "==> deploying $NEW_TAG (current: $PREV_TAG)"
set_tag "$NEW_TAG"

echo "==> pulling images"
docker compose pull

echo "==> starting"
docker compose up -d --remove-orphans

if wait_for_api; then
	echo "==> healthy on $NEW_TAG"
	# Keep old tags from filling the disk on a small VPS.
	docker image prune -f >/dev/null || true
	docker compose ps
	exit 0
fi

echo "!!! api did not become healthy within ${HEALTH_TIMEOUT}s — rolling back to $PREV_TAG" >&2
docker compose logs --tail=80 api >&2 || true

set_tag "$PREV_TAG"
docker compose up -d --remove-orphans >&2 || true

echo "!!! rolled back to $PREV_TAG" >&2
exit 1
