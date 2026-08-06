#!/usr/bin/env bash
# Entry point for the Sniplink dev launcher from git-bash.
#
# Deliberately a thin wrapper: all the logic lives in dev.ps1 so there is only
# one implementation to keep correct. -ExecutionPolicy Bypass is process-scoped
# and needs no admin rights.
#
#   ./dev.sh          start
#   ./dev.sh stop     shut down
#   ./dev.sh status   report only

set -euo pipefail

action="${1:-start}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v powershell >/dev/null 2>&1; then
  echo "powershell not found on PATH - this launcher is Windows-only." >&2
  exit 1
fi

exec powershell -NoProfile -ExecutionPolicy Bypass \
  -File "$(cygpath -w "$here/dev.ps1")" -Action "$action"
