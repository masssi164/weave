#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_FILE="$ROOT/infra/provider-lab/docker-compose.yml"
PROJECT="weave-provider-lab"
case "${1:-help}" in
  start) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d ;;
  stop) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" stop ;;
  reset) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down --volumes --remove-orphans ;;
  ps) docker compose -p "$PROJECT" -f "$COMPOSE_FILE" ps ;;
  health) python3 "$ROOT/tools/provider_lab_check.py" --health-report ;;
  verify) python3 "$ROOT/tools/provider_lab_check.py" ;;
  *) cat <<EOF
Usage: $0 start|stop|reset|ps|health|verify

start  Start the Sprint 22 local provider lab with Docker Compose.
stop   Stop containers without deleting lab volumes.
reset  Delete only the local weave-provider-lab containers/volumes.
ps     Show compose service state.
health Emit a support-safe manifest-based health report.
verify Run CI-safe manifest, fixture, scoreboard, and redaction checks.
EOF
  ;;
esac
