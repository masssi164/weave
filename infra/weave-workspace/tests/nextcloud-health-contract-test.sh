#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Missing Nextcloud provider boundary '$2' in $1"; }
reject() { ! grep -Fq -- "$2" "$1" || fail "Forbidden Nextcloud boundary '$2' remains in $1"; }

require "${ROOT_DIR}/compose.yaml" 'POSTGRES_PASSWORD_FILE: /run/secrets/nextcloud-db-password'
require "${ROOT_DIR}/compose.yaml" 'FORWARDEDFORHEADERS: HTTP_X_FORWARDED_FOR'
require "${ROOT_DIR}/compose.yaml" '"127.0.0.1:${WEAVE_NEXTCLOUD_HOST_PORT:-48083}:80"'
require "${ROOT_DIR}/scripts/render_config.py" 'header_up X-Forwarded-For {{http.request.remote.host}}'
require "${ROOT_DIR}/scripts/render_config.py" 'header_up X-Forwarded-Host {{host}}'
require "${ROOT_DIR}/scripts/render_config.py" 'header_up X-Forwarded-Proto {{scheme}}'

require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'existing Nextcloud actor credential differs from the selected SecretRef'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'weave-workspace'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'weave-team-engineering'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'weave-channel-engineering-general'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'weave-contacts'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'credentialMutationCount'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'oidcManagedProjectionDigest'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'containsSecretValues'

require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/service/files/NextcloudFilesAdapter.java" 'implements FilesProviderPort'
require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/service/calendar/CalDavCalendarAdapter.java" 'implements CalendarProviderPort'
require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/service/FilesFacadeService.java" 'FilesProviderPort'
require "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/service/CalendarFacadeService.java" 'CalendarProviderPort'
require "${REPO_ROOT}/server/src/main/resources/application.yml" '/weave-workspace/'

reject "${ROOT_DIR}/compose.yaml" 'TRUSTED_PROXIES='
reject "${ROOT_DIR}/compose.yaml" 'WEAVE_NEXTCLOUD_BACKEND_ACTOR_TOKEN='
reject "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'password:reset'

printf 'Nextcloud provider readiness contract tests passed\n'
