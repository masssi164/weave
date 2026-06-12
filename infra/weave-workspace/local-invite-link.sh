#!/usr/bin/env bash
# Generate deterministic no-secret local dogfood invite links for Weave.
# shellcheck shell=bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: local-invite-link.sh [--json] [--run-id ID] [--handoff-ref REF] [--org ORG] [--workspace WORKSPACE] [--profile PROFILE] [--base-url URL]

Defaults emit the Massimo local dogfood/home invite using DNS-first https://weave.test:44443.
No tokens, passwords, or secrets are generated or embedded.
USAGE
}

json=false
org="massimo-dogfood"
workspace="home"
profile="local-lan-dogfood"
run_id="s32-massimo-dogfood"
handoff_ref="handoff-s32-massimo-dogfood-home"
base_url="${WEAVE_PUBLIC_BASE_URL:-https://weave.test:44443}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --json) json=true; shift ;;
    --run-id) run_id="${2:?Missing value for --run-id}"; shift 2 ;;
    --handoff-ref) handoff_ref="${2:?Missing value for --handoff-ref}"; shift 2 ;;
    --org) org="${2:?Missing value for --org}"; shift 2 ;;
    --workspace) workspace="${2:?Missing value for --workspace}"; shift 2 ;;
    --profile) profile="${2:?Missing value for --profile}"; shift 2 ;;
    --base-url) base_url="${2:?Missing value for --base-url}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

python3 - "$json" "$base_url" "$handoff_ref" "$org" "$workspace" "$profile" "$run_id" <<'PY'
import json
import re
import sys
from urllib.parse import urlencode, urlparse

as_json = sys.argv[1] == "true"
base_url, handoff_ref, org, workspace, profile, run_id = sys.argv[2:]
parsed = urlparse(base_url)
errors = []
if parsed.scheme != "https":
    errors.append("base URL must use https")
if parsed.hostname != "weave.test":
    errors.append("base URL must be DNS-first weave.test")
if parsed.port != 44443:
    errors.append("base URL must include local dogfood port 44443")
if parsed.username or parsed.password or parsed.query or parsed.fragment:
    errors.append("base URL must not contain credentials, query, or fragment")
for label, value in {
    "handoff_ref": handoff_ref,
    "org": org,
    "workspace": workspace,
    "profile": profile,
    "run_id": run_id,
}.items():
    if not re.fullmatch(r"[A-Za-z0-9._:-]+", value):
        errors.append(f"{label} contains unsupported characters")
for value in (base_url, handoff_ref, org, workspace, profile, run_id):
    lowered = value.lower()
    if any(secret_word in lowered for secret_word in ("password", "token", "secret", "access_token", "refresh_token", "id_token", "client_secret")):
        errors.append("invite inputs must not contain credential-like fields")
if errors:
    print("local-invite-link: " + "; ".join(errors), file=sys.stderr)
    sys.exit(1)

origin = f"{parsed.scheme}://{parsed.hostname}:{parsed.port}"
query = urlencode({
    "handoff_ref": handoff_ref,
    "org": org,
    "workspace": workspace,
    "profile": profile,
    "run_id": run_id,
})
invite_link = f"{origin}/join?{query}"
platform_config_url = f"{origin}/api/platform/config"
result = {
    "inviteLink": invite_link,
    "qrPayload": invite_link,
    "platformConfigUrl": platform_config_url,
    "productBaseUrl": origin + "/",
    "org": org,
    "workspace": workspace,
    "profile": profile,
    "runId": run_id,
    "handoffRef": handoff_ref,
    "secretPolicy": "no tokens, passwords, or secrets embedded; local test password remains only in .generated/bootstrap.env",
}
if as_json:
    print(json.dumps(result, separators=(",", ":"), sort_keys=True))
else:
    print(invite_link)
PY
