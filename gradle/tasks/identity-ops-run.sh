#!/usr/bin/env bash

set -euo pipefail

operation=""
image=""
state_root=""
environment_file=""
network=""

fail() {
  printf 'WEAVE_IDENTITY_OPS_RUN_ERROR %s\n' "$*" >&2
  exit 1
}

while (($# > 0)); do
  case "$1" in
    --operation) operation="${2:-}"; shift 2 ;;
    --image) image="${2:-}"; shift 2 ;;
    --state-root) state_root="${2:-}"; shift 2 ;;
    --env-file) environment_file="${2:-}"; shift 2 ;;
    --network) network="${2:-}"; shift 2 ;;
    *) fail "unsupported argument $1" ;;
  esac
done

[[ "${operation}" == "plan" || "${operation}" == "apply" || "${operation}" == "validate" ]] ||
  fail "operation must be plan, apply, or validate"
[[ -n "${image}" && "${image}" != *:latest ]] || fail "an immutable or bounded identity-ops image is required"
[[ "${state_root}" == /* ]] || fail "state root must be absolute"
[[ "${environment_file}" == /* ]] || fail "environment file must be absolute"
[[ -f "${environment_file}" && ! -L "${environment_file}" ]] ||
  fail "private environment file is unavailable"
[[ -d "${state_root}" && ! -L "${state_root}" ]] ||
  fail "state root is unavailable"
[[ -n "${network}" ]] || fail "an exact Docker network is required"

file_mode() {
  local detected
  if detected="$(stat -f '%Lp' "$1" 2>/dev/null)" &&
    [[ "${detected}" =~ ^[0-7]{3,4}$ ]]; then
    printf '%s' "${detected}"
    return
  fi
  detected="$(stat -c '%a' "$1" 2>/dev/null)" ||
    fail "private environment file permissions are unreadable"
  [[ "${detected}" =~ ^[0-7]{3,4}$ ]] ||
    fail "private environment file permissions are invalid"
  printf '%s' "${detected}"
}

mode="$(file_mode "${environment_file}")"
(((8#${mode} & 8#077) == 0)) ||
  fail "private environment file must not grant group or other access"

docker run --rm \
  --name "weave-identity-ops-${operation}" \
  --network "${network}" \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,nodev,size=16m \
  --tmpfs /work:rw,exec,nosuid,nodev,size=128m \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --env-file "${environment_file}" \
  --volume "${state_root}:/state:rw" \
  "${image}" \
  "${operation}"
