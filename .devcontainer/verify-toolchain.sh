#!/usr/bin/env bash
set -euo pipefail

java_version="$(java -version 2>&1 | head -n 1)"
go_version="$(go version)"
docker_version="$(docker --version)"
compose_version="$(docker compose version)"
postgres_version="$(psql --version)"
python_version="$(python3 --version)"

case "$java_version" in
  *'21.'*) ;;
  *) echo "Expected Java 21, got: $java_version" >&2; exit 1 ;;
esac

case "$go_version" in
  *'go1.26.'*) ;;
  *) echo "Expected supported Go 1.26.x for runner/go.mod, got: $go_version" >&2; exit 1 ;;
esac

python3 - <<'PY'
import yaml  # noqa: F401
PY

printf '%s\n' \
  "$java_version" \
  "$go_version" \
  "$docker_version" \
  "$compose_version" \
  "$postgres_version" \
  "$python_version"

./gradlew --version >/dev/null
