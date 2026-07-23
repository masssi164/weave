#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Expected $1 to contain: $2"; }
reject() { ! grep -Fq -- "$2" "$1" || fail "Retired contract remains in $1: $2"; }

for file in \
  compose.yaml compose.dev.yaml compose.dogfood.yaml compose.main.yaml compose.sh \
  scripts/compose_env.py scripts/compose_runtime.py scripts/render_config.py \
  scripts/nextcloud_reconcile.py keycloak/supervisor.py keycloak/reconciler.py; do
  [[ -f "${ROOT_DIR}/${file}" ]] || fail "Missing Compose authority file: ${file}"
done

if find "${ROOT_DIR}/01-infrastructure" "${ROOT_DIR}/02-keycloak-setup" -type f -print -quit 2>/dev/null | grep -q .; then
  fail "Executable OpenTofu authority files were not retired"
fi
[[ ! -e "${ROOT_DIR}/docker-compose.yml" ]] || fail "Legacy parallel Compose model was not retired"

require "${ROOT_DIR}/compose.yaml" 'profiles: *core-profiles'
require "${ROOT_DIR}/compose.yaml" 'POSTGRES_PASSWORD_FILE: /run/secrets/postgres-admin-password'
require "${ROOT_DIR}/compose.yaml" 'SPRING_CONFIG_IMPORT: configtree:/run/secrets/weave/'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.managed: "true"'
require "${ROOT_DIR}/compose.dev.yaml" 'host.docker.internal:host-gateway'
require "${ROOT_DIR}/compose.dogfood.yaml" 'WEAVE_RELEASE_POSTURE: dogfood'
require "${ROOT_DIR}/compose.main.yaml" 'WEAVE_RELEASE_POSTURE: main'
require "${ROOT_DIR}/scripts/compose_env.py" 'PROFILES = ("dev", "dogfood", "main")'
require "${ROOT_DIR}/scripts/compose_env.py" 'refusing to deploy {profile} from an example environment file'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'WEAVE_KEYCLOAK_REVIEWED_ENV_FILE'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'persistent reconciliation cannot execute a supervisor from the candidate checkout'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'ordinary reconciliation refuses an implicit rotation'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'oidcManagedProjectionDigest'
require "${ROOT_DIR}/scripts/render_config.py" 'WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE'
require "${ROOT_DIR}/scripts/render_config.py" 'WEAVE_MATRIX_FEDERATION_ENABLED'
require "${REPO_ROOT}/build.gradle" "'serverDevH2Test'"
require "${REPO_ROOT}/build.gradle" "'serverPostgresIntegrationTest'"
require "${REPO_ROOT}/build.gradle" '"keycloak${profileTitle}${operationTitle}"'

reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CREATE_TEST_USER'
reject "${ROOT_DIR}/compose.yaml" 'OpenProject'

printf 'V01_INFRA_CONTROL_PLANE_BOOTSTRAP status=passed infrastructure product contract tests passed\n'
