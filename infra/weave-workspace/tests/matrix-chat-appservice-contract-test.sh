#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Missing Matrix facade contract '$2' in $1"; }
reject() { [[ -f "$1" ]] || fail "Missing inspected contract file: $1"; ! grep -Fq -- "$2" "$1" || fail "Forbidden Matrix contract '$2' remains in $1"; }

readonly PROVIDERS="${ROOT_DIR}/scripts/rendering/providers.py"
readonly GATEWAY="${ROOT_DIR}/scripts/rendering/gateway.py"

require "${PROVIDERS}" 'id: weave-chat-synapse'
require "${PROVIDERS}" 'http://backend:8080/api/internal/chat/matrix/appservice'
require "${PROVIDERS}" 'rate_limited: true'
require "${PROVIDERS}" "regex: '^@_weave_[a-z0-9]{{26,64}}:{host_regex}$'"
require "${PROVIDERS}" 'rooms: []'
require "${ROOT_DIR}/scripts/init_secrets.py" 'Matrix Application Service tokens must be distinct high-entropy SecretRefs'

require "${ROOT_DIR}/compose.yaml" 'install -m 0400'
require "${ROOT_DIR}/compose.yaml" '! cmp -s /appservice/as-token /appservice/hs-token'
require "${ROOT_DIR}/compose.yaml" 'matrix-appservice:/run/weave-chat-appservice:ro'
require "${ROOT_DIR}/compose.yaml" 'profiles: *provider-matrix-profiles'
require "${ROOT_DIR}/compose.yaml" '/backend/configtree:/run/secrets/providers:ro'
require "${GATEWAY}" '@internal path /api/internal/* /actuator/*'
require "${GATEWAY}" 'handle @internal'

require "${REPO_ROOT}/server/app/src/main/resources/application.yml" 'import: classpath:application-base.yml'
require "${REPO_ROOT}/server/domains/chat/src/main/resources/weave/domain/chat-defaults.properties" 'weave.chat.provider=${WEAVE_CHAT_PROVIDER:weave-native}'
require "${REPO_ROOT}/server/domains/chat/src/main/resources/weave/domain/chat-defaults.properties" 'weave.chat.matrix.as-token-file=${WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE:}'
require "${REPO_ROOT}/server/domains/chat/src/main/java/com/massimotter/weave/backend/chat/capability/ChatWorkspaceCapabilityConfiguration.java" 'classpath:weave/domain/chat-defaults.properties'
require "${REPO_ROOT}/server/domains/chat/src/main/java/com/massimotter/weave/backend/chat/config/MatrixApplicationServiceSecurityConfiguration.java" '.securityMatcher("/api/internal/chat/matrix/appservice/**")'
require "${REPO_ROOT}/server/domains/chat/persistence/src/main/java/com/massimotter/weave/backend/chat/persistence/CanonicalChatPersistence.java" 'weave_chat_appservice_transactions'
require "${REPO_ROOT}/server/domains/chat/src/main/java/com/massimotter/weave/backend/chat/persistence/CanonicalChatJpaAuthority.java" 'PROPAGATION_REQUIRES_NEW'
require "${REPO_ROOT}/server/domains/chat/src/main/java/com/massimotter/weave/backend/chat/persistence/CanonicalChatJpaAuthority.java" 'callbacks.saveAndFlush'
require "${REPO_ROOT}/server/domains/chat/src/main/java/com/massimotter/weave/backend/chat/persistence/CanonicalChatJpaAuthority.java" 'callbacks.existsById'
[[ ! -e "${REPO_ROOT}/server/domains/chat/src/main/java/com/massimotter/weave/backend/chat/persistence/ChatCallbackClaimNativeRepository.java" ]] \
  || fail "Database-specific Chat callback claim repository must stay removed"

reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN='
reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN='
reject "${ROOT_DIR}/compose.yaml" 'matrix-appservice-as-token:/run/secrets/matrix/as-token:ro'
reject "${ROOT_DIR}/compose.yaml" 'matrix-appservice-hs-token:/run/secrets/matrix/hs-token:ro'
reject "${PROVIDERS}" 'rate_limited: false'
reject "${PROVIDERS}" 'regex: .*'

printf 'Matrix Chat Application Service contract tests passed\n'
