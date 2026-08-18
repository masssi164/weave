#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${ROOT_DIR}/lib/runtime-namespace.sh"
NEXTCLOUD_CONTAINER="$(weave_container_name nextcloud)"
BACKEND_CONTAINER="$(weave_container_name backend)"
PROXY_CONTAINER="$(weave_container_name proxy)"

LOG_FILE=""
OUTPUT_FILE=""
TAIL_LINES="${WEAVE_NEXTCLOUD_SECURITY_AUDIT_LINES:-2000}"

fail() { printf 'NEXTCLOUD_SECURITY_AUDIT_ERROR %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: nextcloud-auth-security-audit.sh [--log-file FILE] [--output FILE]

Reads recent Nextcloud JSON security log events without mutating counters or
brute-force protection. Output contains only event counts, timestamps, source
classes, salted source hashes, canonical method/route classes, and aggregate
configured-backend-actor attribution. Raw addresses, actors, messages, URLs,
and provider payloads are never emitted.
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --log-file) LOG_FILE="${2:-}"; shift 2 ;;
      --output) OUTPUT_FILE="${2:-}"; shift 2 ;;
      -h|--help) usage; exit 0 ;;
      *) fail "unknown argument '$1'" ;;
    esac
  done
  if [[ ! "${TAIL_LINES}" =~ ^[0-9]+$ ]] || ((TAIL_LINES < 1 || TAIL_LINES > 100000)); then
    fail "audit line limit must be 1..100000"
  fi
}

container_ip() {
  local container="$1"
  local network="${WEAVE_DOCKER_NETWORK_NAME:-weave_network}"
  docker inspect --format '{{json .NetworkSettings.Networks}}' "${container}" 2>/dev/null |
    python3 -c 'import json,sys
network=json.load(sys.stdin).get(sys.argv[1], {})
print(network.get("IPAddress", ""))
' "${network}" || true
}

read_live_log() {
  docker exec "${NEXTCLOUD_CONTAINER}" sh -c 'if [ -f /var/www/html/data/nextcloud.log ]; then tail -n "$1" /var/www/html/data/nextcloud.log; fi' sh "${TAIL_LINES}"
}

configured_backend_actor() {
  local configured="${WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME:-${WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME:-${WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME:-}}}"
  if [[ -n "${configured}" ]]; then
    printf '%s' "${configured}"
    return
  fi
  # A live support-bundle invocation does not necessarily source bootstrap.env.
  # Read only the actor username from the isolated backend container config and
  # keep it internal to the classifier.
  docker inspect --format '{{json .Config.Env}}' "${BACKEND_CONTAINER}" 2>/dev/null |
    python3 -c 'import json,sys
for value in json.load(sys.stdin):
    if value.startswith("WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME="):
        print(value.split("=", 1)[1], end="")
        break
' 2>/dev/null || true
}

main() {
  parse_args "$@"
  command -v python3 >/dev/null || fail "python3 is required"
  command -v openssl >/dev/null || fail "openssl is required"

  local raw_file salt proxy_ip backend_ip nextcloud_ip backend_actor target
  raw_file="$(mktemp)"
  trap 'rm -f -- "${raw_file:-}"' EXIT
  if [[ -n "${LOG_FILE}" ]]; then
    [[ -f "${LOG_FILE}" ]] || fail "log fixture is unavailable"
    tail -n "${TAIL_LINES}" "${LOG_FILE}" >"${raw_file}"
    proxy_ip="${WEAVE_AUDIT_PROXY_IP:-}"
    backend_ip="${WEAVE_AUDIT_BACKEND_IP:-}"
    nextcloud_ip="${WEAVE_AUDIT_NEXTCLOUD_IP:-}"
  else
    command -v docker >/dev/null || fail "docker is required for live audit"
    read_live_log >"${raw_file}"
    proxy_ip="$(container_ip "${PROXY_CONTAINER}")"
    backend_ip="$(container_ip "${BACKEND_CONTAINER}")"
    nextcloud_ip="$(container_ip "${NEXTCLOUD_CONTAINER}")"
  fi
  salt="${WEAVE_AUDIT_HASH_SALT:-$(openssl rand -hex 16)}"
  if [[ -n "${LOG_FILE}" ]]; then
    backend_actor="${WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME:-${WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME:-${WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME:-}}}"
  else
    backend_actor="$(configured_backend_actor)"
  fi
  target="${OUTPUT_FILE:-/dev/stdout}"
  [[ "${target}" == /dev/stdout ]] || mkdir -p "$(dirname -- "${target}")"

  python3 - "${raw_file}" "${target}" "${salt}" "${proxy_ip}" "${backend_ip}" "${nextcloud_ip}" "${backend_actor}" <<'PY'
import hashlib
import json
import sys
from collections import defaultdict
from pathlib import Path
from urllib.parse import unquote, urlsplit

source, target, salt, proxy_ip, backend_ip, nextcloud_ip, backend_actor = sys.argv[1:]
known = {proxy_ip: "caddy", backend_ip: "backend", nextcloud_ip: "nextcloud"}
known.pop("", None)
summaries = defaultdict(lambda: {"invalidAuthenticationEvents": 0, "throttleEvents": 0, "firstSeen": None, "lastSeen": None})
request_summaries = defaultdict(lambda: {"invalidAuthenticationEvents": 0, "throttleEvents": 0, "backendActorFailureEvents": 0})
events = 0
backend_actor_failures = 0

CANONICAL_METHODS = {"PROPFIND", "REPORT", "GET", "PUT", "DELETE", "POST"}

def request_method_class(item):
    value = item.get("method") or item.get("httpMethod") or item.get("requestMethod")
    if not isinstance(value, str) or not value.strip():
        return "UNKNOWN"
    normalized = value.strip().upper()
    return normalized if normalized in CANONICAL_METHODS else "OTHER"

def request_path(item):
    for key in ("url", "requestUri", "request_uri", "path"):
        value = item.get(key)
        if isinstance(value, str) and value.strip():
            try:
                return unquote(urlsplit(value.strip()).path)
            except ValueError:
                return ""
    return ""

def route_class(path):
    normalized = path.casefold()
    if not normalized:
        return "unknown"
    if "/remote.php/dav/files/" in normalized or normalized.startswith("/dav/files/"):
        return "files_webdav"
    if any(value in normalized for value in (
        "/remote.php/dav/calendars/",
        "/remote.php/dav/principals/",
        "/caldav/",
    )):
        return "calendar_caldav"
    if "/login" in normalized or "/index.php/login" in normalized:
        return "identity_login"
    return "other"

def attributed_to_backend_actor(item, message, path):
    if not backend_actor:
        return False
    expected = backend_actor.casefold()
    for key in ("user", "username", "loginName", "login", "actor"):
        value = item.get(key)
        if isinstance(value, str) and value.strip().casefold() == expected:
            return True
    segments = [segment.casefold() for segment in path.split("/") if segment]
    if expected in segments:
        return True
    return expected in message

def timestamp(item):
    value = item.get("time") or item.get("timestamp")
    return value if isinstance(value, str) and len(value) <= 40 else None

for line in Path(source).read_text(encoding="utf-8", errors="replace").splitlines():
    try:
        item = json.loads(line)
    except Exception:
        continue
    if not isinstance(item, dict):
        continue
    message = str(item.get("message", "")).lower()
    status = item.get("status") or item.get("httpStatus")
    throttled = str(status) == "429" or any(token in message for token in ("brute force", "bruteforce", "throttl", "too many requests"))
    invalid = throttled or any(token in message for token in ("login failed", "authentication failed", "invalid credential", "invalid password"))
    if not invalid:
        continue
    remote = str(item.get("remoteAddr") or item.get("remote_addr") or "")
    source_class = known.get(remote, "external_or_unclassified" if remote else "unknown")
    source_hash = hashlib.sha256((salt + "\0" + remote).encode()).hexdigest()
    key = (source_class, source_hash)
    entry = summaries[key]
    entry["invalidAuthenticationEvents"] += 1
    entry["throttleEvents"] += int(throttled)
    observed = timestamp(item)
    if observed:
        entry["firstSeen"] = min(filter(None, (entry["firstSeen"], observed)), default=observed)
        entry["lastSeen"] = max(filter(None, (entry["lastSeen"], observed)), default=observed)
    method_class = request_method_class(item)
    path = request_path(item)
    canonical_route = route_class(path)
    actor_failure = attributed_to_backend_actor(item, message, path)
    request_entry = request_summaries[(method_class, canonical_route)]
    request_entry["invalidAuthenticationEvents"] += 1
    request_entry["throttleEvents"] += int(throttled)
    request_entry["backendActorFailureEvents"] += int(actor_failure)
    backend_actor_failures += int(actor_failure)
    events += 1

output = {
    "schemaVersion": "weave-nextcloud-auth-security-audit-v1",
    "eventsObserved": events,
    "sources": [
        {"sourceClass": key[0], "sourceSha256": key[1], **value}
        for key, value in sorted(summaries.items())
    ],
    "requestClassifications": [
        {"methodClass": key[0], "routeClass": key[1], **value}
        for key, value in sorted(request_summaries.items())
    ],
    "backendActorAttribution": {
        "configured": bool(backend_actor),
        "failureObserved": backend_actor_failures > 0,
        "failureEvents": backend_actor_failures,
    },
    "trustedProxyRemediation": "verify_exact_caddy_address_and_correct_invalid_credential_source",
    "bruteForceProtectionChanged": False,
    "countersReset": False,
    "rawAddressesIncluded": False,
    "actorIdentifiersIncluded": False,
    "rawProviderPayloadIncluded": False,
    "supportSafe": True,
}
rendered = json.dumps(output, indent=2, sort_keys=True) + "\n"
if target == "/dev/stdout":
    sys.stdout.write(rendered)
else:
    Path(target).write_text(rendered, encoding="utf-8")
PY
}

main "$@"
