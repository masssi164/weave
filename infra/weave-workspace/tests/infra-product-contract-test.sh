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
  compose.yaml compose.dev.yaml compose.test.yaml compose.prod.yaml compose.sh \
  scripts/compose_env.py scripts/compose_runtime.py scripts/render_config.py \
  scripts/nextcloud_reconcile.py keycloak/identity_ops.py keycloak/Dockerfile.identity-ops; do
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
require "${ROOT_DIR}/compose.test.yaml" 'WEAVE_RELEASE_POSTURE: test'
require "${ROOT_DIR}/compose.prod.yaml" 'WEAVE_RELEASE_POSTURE: prod'
require "${ROOT_DIR}/scripts/compose_env.py" 'PROFILES = ("dev", "test", "prod")'
require "${ROOT_DIR}/scripts/compose_env.py" 'refusing to deploy {profile} from an example environment file'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'prod rejects WEAVE_TEST_USERS_FILE before Identity Ops mutation'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'persistent-adoption'
require "${ROOT_DIR}/keycloak/identity_ops.py" '/opt/keycloak/bin/kcadm.sh'
require "${ROOT_DIR}/keycloak/Dockerfile.identity-ops" 'ARG WEAVE_KEYCLOAK_BASE=quay.io/keycloak/keycloak@sha256:'
require "${ROOT_DIR}/keycloak/Dockerfile.identity-ops" 'ARG WEAVE_UBI9_BASE=registry.access.redhat.com/ubi9@sha256:'
require "${ROOT_DIR}/keycloak/Dockerfile.identity-ops" 'FROM ${WEAVE_KEYCLOAK_BASE}'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'ordinary reconciliation refuses an implicit rotation'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'oidcManagedProjectionDigest'
require "${ROOT_DIR}/scripts/render_config.py" 'WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE'
require "${ROOT_DIR}/scripts/render_config.py" 'WEAVE_MATRIX_FEDERATION_ENABLED'
require "${REPO_ROOT}/gradle/tasks/environment-profiles.gradle" "'serverDevH2Test'"
require "${REPO_ROOT}/gradle/tasks/environment-profiles.gradle" "'serverPostgresIntegrationTest'"
require "${REPO_ROOT}/gradle/tasks/environment-profiles.gradle" '"identity${profileTitle}${operationTitle}"'

reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CREATE_TEST_USER'
reject "${ROOT_DIR}/compose.yaml" '/var/run/docker.sock'
reject "${ROOT_DIR}/compose.yaml" 'OpenProject'

printf 'V01_INFRA_CONTROL_PLANE_BOOTSTRAP status=passed infrastructure product contract tests passed\n'
