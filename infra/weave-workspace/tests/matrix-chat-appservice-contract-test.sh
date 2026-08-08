#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Missing Matrix facade contract '$2' in $1"; }
reject() { ! grep -Fq -- "$2" "$1" || fail "Forbidden Matrix contract '$2' remains in $1"; }

require "${ROOT_DIR}/scripts/render_config.py" 'id: weave-chat-synapse'
require "${ROOT_DIR}/scripts/render_config.py" 'http://backend:8080/api/internal/chat/matrix/appservice'
require "${ROOT_DIR}/scripts/render_config.py" 'rate_limited: true'
require "${ROOT_DIR}/scripts/render_config.py" "regex: '^@_weave_[a-z0-9]{{26,64}}:{host_regex}$'"
require "${ROOT_DIR}/scripts/render_config.py" 'rooms: []'
require "${ROOT_DIR}/scripts/init_secrets.py" 'Matrix Application Service tokens must be distinct high-entropy SecretRefs'

require "${ROOT_DIR}/compose.yaml" 'install -m 0400'
require "${ROOT_DIR}/compose.yaml" '! cmp -s /appservice/as-token /appservice/hs-token'
require "${ROOT_DIR}/compose.yaml" 'matrix-appservice:/run/weave-chat-appservice:ro'
require "${ROOT_DIR}/compose.yaml" 'profiles: *provider-matrix-profiles'
require "${ROOT_DIR}/compose.yaml" '/backend/configtree:/run/secrets/providers:ro'
require "${ROOT_DIR}/scripts/render_config.py" '@internal path /api/internal/* /actuator/*'
require "${ROOT_DIR}/scripts/render_config.py" 'respond @internal'

require "${REPO_ROOT}/server/src/main/resources/application.yml" 'import: classpath:application-base.yml'
require "${REPO_ROOT}/server/src/main/resources/application-base.yml" 'provider: ${WEAVE_CHAT_PROVIDER:weave-native}'
require "${REPO_ROOT}/server/src/main/resources/application-base.yml" 'as-token-file: ${WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE:}'
require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/config/MatrixApplicationServiceSecurityConfiguration.java" '.securityMatcher("/api/internal/chat/matrix/appservice/**")'
require "${REPO_ROOT}/weave-persistence-jpa/src/main/java/com/massimotter/weave/backend/chat/store/CanonicalChatPersistence.java" 'weave_chat_appservice_transactions'
require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/chat/store/CanonicalChatJpaAuthority.java" 'PROPAGATION_REQUIRES_NEW'
require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/chat/store/CanonicalChatJpaAuthority.java" 'callbacks.saveAndFlush'
require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/chat/store/CanonicalChatJpaAuthority.java" 'callbacks.existsById'
[[ ! -e "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/chat/store/ChatCallbackClaimNativeRepository.java" ]] \
  || fail "Database-specific Chat callback claim repository must stay removed"

reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN='
reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN='
reject "${ROOT_DIR}/compose.yaml" 'matrix-appservice-as-token:/run/secrets/matrix/as-token:ro'
reject "${ROOT_DIR}/compose.yaml" 'matrix-appservice-hs-token:/run/secrets/matrix/hs-token:ro'
reject "${ROOT_DIR}/scripts/render_config.py" 'rate_limited: false'
reject "${ROOT_DIR}/scripts/render_config.py" 'regex: .*'

printf 'Matrix Chat Application Service contract tests passed\n'
