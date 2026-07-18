#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: persistent_dogfood_resource_guard.sh capture|verify BASELINE_FILE" >&2
  exit 64
}

[[ $# -eq 2 ]] || usage
mode="$1"
baseline_file="$2"

case "$mode" in
  capture|verify) ;;
  *) usage ;;
esac

[[ -n "$baseline_file" && "$baseline_file" != "/" ]] || {
  echo "persistent-dogfood-resource-guard: invalid baseline path" >&2
  exit 64
}

collect_resources() {
  {
    docker container ls --all --format 'container {{.Names}} {{.ID}}'
    docker volume ls --format 'volume {{.Name}} {{.Name}}'
    docker network ls --format 'network {{.Name}} {{.ID}}'
  } | awk '
    $2 ~ /^weave[-_]/ && $2 !~ /^weave[-_]e2e[-_]/ { print }
  ' | LC_ALL=C sort
}

if [[ "$mode" == "capture" ]]; then
  mkdir -p "$(dirname "$baseline_file")"
  umask 077
  collect_resources >"$baseline_file"
  echo "PERSISTENT_DOGFOOD_RESOURCE_GUARD status=captured count=$(wc -l <"$baseline_file" | tr -d ' ') supportSafe=true"
  exit 0
fi

[[ -f "$baseline_file" && ! -L "$baseline_file" ]] || {
  echo "persistent-dogfood-resource-guard: baseline is missing or unsafe" >&2
  exit 1
}

current_file="$(mktemp "${TMPDIR:-/tmp}/weave-persistent-dogfood.XXXXXX")"
trap 'rm -f -- "$current_file"' EXIT
collect_resources >"$current_file"

if ! cmp -s "$baseline_file" "$current_file"; then
  echo "persistent-dogfood-resource-guard: persistent resource identity changed during isolated E2E" >&2
  exit 1
fi

echo "PERSISTENT_DOGFOOD_RESOURCE_GUARD status=preserved count=$(wc -l <"$current_file" | tr -d ' ') supportSafe=true"
