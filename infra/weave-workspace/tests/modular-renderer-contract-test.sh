#!/usr/bin/env bash
# shellcheck shell=bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
GATEWAY="${ROOT}/infra/weave-workspace/scripts/rendering/gateway.py"
PROVIDERS="${ROOT}/infra/weave-workspace/scripts/rendering/providers.py"
RENDERER="${ROOT}/infra/weave-workspace/scripts/render_config.py"
SERVER_DOGFOOD="${ROOT}/server/src/main/resources/application-dogfood.yml"
SERVER_E2E="${ROOT}/server/src/main/resources/application-e2e.yml"

require() {
  local file="$1"
  local fragment="$2"
  grep -Fq -- "$fragment" "$file" || {
    printf 'modular renderer contract missing %s in %s\n' "$fragment" "$file" >&2
    exit 1
  }
}

reject() {
  local file="$1"
  local fragment="$2"
  ! grep -Fq -- "$fragment" "$file" || {
    printf 'modular renderer contract found retired %s in %s\n' "$fragment" "$file" >&2
    exit 1
  }
}

# Gateway owns all public routing and fail-closed internal boundaries.
require "$GATEWAY" '@internal path /api/internal/* /actuator/*'
require "$GATEWAY" 'header_up X-Forwarded-For {http.request.remote.host}'
require "$GATEWAY" 'handle /.well-known/oauth-protected-resource*'

# Provider renderer owns Matrix/MAS/Appservice artifacts.
require "$PROVIDERS" 'id: weave-chat-synapse'
require "$PROVIDERS" '/api/internal/chat/matrix/appservice'

# Spring application configuration is not synthesized by infra anymore.
reject "$RENDERER" '_backend_env'
reject "$RENDERER" '_mcp_env'
reject "$RENDERER" 'WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE'
reject "$RENDERER" 'WEAVE_OIDC_ISSUER_URI'

# Canonical dogfood/e2e app profiles are explicit and provider-neutral by default.
require "$SERVER_DOGFOOD" 'provider: weave-native'
require "$SERVER_E2E" 'provider: weave-native'
require "$SERVER_DOGFOOD" 'issuer-uri: https://auth.weave.test/realms/weave'
require "$SERVER_E2E" 'issuer-uri: https://auth.weave.test/realms/weave'

printf 'modular renderer contract tests passed\n'
