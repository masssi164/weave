#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_FILE="$ROOT/infra/provider-lab/docker-compose.yml"
PROJECT="weave-provider-lab"
case "${1:-help}" in
  start) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d ;;
  start-zulip) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" --profile zulip up -d ;;
  stop) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" stop ;;
  reset) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" --profile zulip down --volumes --remove-orphans ;;
  ps) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" ps ;;
  synapse-config) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" run --rm matrix-synapse generate ;;
  zulip-init) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" --profile zulip run --rm zulip app:init ;;
  health) python3 "$ROOT/tools/provider_lab_check.py" --health-report ;;
  verify) python3 "$ROOT/tools/provider_lab_check.py" ;;
  *) cat <<EOF
Usage: $0 start|start-zulip|stop|reset|ps|synapse-config|zulip-init|health|verify

start           Start the default Sprint 22 local provider lab services.
start-zulip     Start default services plus the heavy Zulip profile after zulip-init.
stop            Stop containers without deleting lab volumes.
reset           Delete only the local weave-provider-lab containers/volumes, including profiles.
ps              Show compose service state.
synapse-config  Generate local Synapse static config in the synapse-data volume.
zulip-init      Run Zulip's documented app:init bootstrap for the Zulip profile.
health          Emit a support-safe manifest-based health report.
verify          Run CI-safe manifest, fixture, scoreboard, and redaction checks.
EOF
  ;;
esac
