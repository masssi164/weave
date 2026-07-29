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

if find "${ROOT_DIR}/01-infrastructure" "${ROOT_DIR}/02-keycloak-setup" \
  -type f \( -name '*.tf' -o -name '*.tofu' -o -name '.terraform.lock.hcl' \) \
  -print -quit 2>/dev/null | grep -q .; then
  fail "Executable OpenTofu authority files were not retired"
fi
[[ ! -e "${ROOT_DIR}/docker-compose.yml" ]] || fail "Legacy parallel Compose model was not retired"
if find "${REPO_ROOT}/infra/keycloak-event-listener" -type f -print -quit 2>/dev/null | grep -q .; then
  fail "Custom Keycloak provider/theme source was not retired"
fi
[[ ! -e "${ROOT_DIR}/keycloak/Dockerfile.sanitizer" ]] ||
  fail "Privileged Keycloak sanitizer authority was not retired"

require "${ROOT_DIR}/compose.yaml" 'profiles: *core-profiles'
require "${ROOT_DIR}/compose.yaml" 'POSTGRES_PASSWORD_FILE: /run/secrets/postgres-admin-password'
require "${ROOT_DIR}/compose.yaml" 'SPRING_CONFIG_IMPORT: configtree:/run/secrets/weave/'
require "${ROOT_DIR}/compose.yaml" \
  'target: weave/spring.security.oauth2.client.registration.weave-identity-admin.client-secret'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.managed: "true"'
require "${ROOT_DIR}/compose.dev.yaml" 'host.docker.internal:host-gateway'
require "${ROOT_DIR}/compose.test.yaml" 'WEAVE_RELEASE_POSTURE: test'
require "${ROOT_DIR}/compose.test.yaml" 'runtime-state-init:'
require "${ROOT_DIR}/compose.yaml" 'agent-runtime-keys-init:'
require "${ROOT_DIR}/compose.prod.yaml" 'WEAVE_RELEASE_POSTURE: prod'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.protocol: s3-compatible'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.data-class: runtime-state-sensitive'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.operation: bucket-initialize'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.operation: key-initialize'
require "${ROOT_DIR}/compose.yaml" 'MINIO_ROOT_USER_FILE: /run/secrets/runtime-state-s3-access-key'
require "${ROOT_DIR}/compose.yaml" 'mc version enable runtime-state/weave-runtime-state'
require "${ROOT_DIR}/scripts/compose_env.py" 'PROFILES = ("dev", "test", "prod")'
require "${ROOT_DIR}/scripts/compose_env.py" 'refusing to deploy {profile} from an example environment file'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'persistent-adoption'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'WEAVE_ADOPTION_RECEIPT'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'resource inventory is incomplete or ambiguous'
require "${ROOT_DIR}/keycloak/identity_ops.py" '/opt/keycloak/bin/kcadm.sh'
require "${ROOT_DIR}/keycloak/Dockerfile.identity-ops" 'ARG WEAVE_KEYCLOAK_BASE=quay.io/keycloak/keycloak@sha256:'
require "${ROOT_DIR}/keycloak/Dockerfile.identity-ops" 'ARG WEAVE_UBI9_BASE=registry.access.redhat.com/ubi9@sha256:'
require "${ROOT_DIR}/keycloak/Dockerfile.identity-ops" 'FROM ${WEAVE_KEYCLOAK_BASE}'
require "${ROOT_DIR}/scripts/build_keycloak_image.py" 'STOCK_KEYCLOAK_REFERENCE ='
require "${ROOT_DIR}/scripts/build_keycloak_image.py" '"weave.downstream-keycloak-image.v1"'
require "${ROOT_DIR}/keycloak/Dockerfile.runtime" 'com.massimotter.weave.keycloak-patch-sha256'
require "${ROOT_DIR}/scripts/build_identity_ops_image.py" 'com.massimotter.weave.component=keycloak-identity-ops'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'ordinary reconciliation refuses an implicit rotation'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'oidcManagedProjectionDigest'
require "${ROOT_DIR}/scripts/render_config.py" 'WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE'
require "${ROOT_DIR}/scripts/render_config.py" 'WEAVE_MATRIX_FEDERATION_ENABLED'
require "${ROOT_DIR}/scripts/render_config.py" '"WEAVE_IDENTITY_KEYCLOAK_TOKEN_URI"'
require "${ROOT_DIR}/scripts/render_config.py" \
  '"spring.security.oauth2.client.registration.weave-identity-admin.client-secret": "keycloak-weave-identity-admin"'
require "${ROOT_DIR}/scripts/render_config.py" \
  '"WEAVE_IDENTITY_REFERENCE_HMAC_SECRET_FILE"'
require "${ROOT_DIR}/scripts/render_config.py" \
  '"WEAVE_AGENT_RUNTIME_DEFAULT_CLIENT_SCOPES": "weaver-runtime-workload"'
require "${ROOT_DIR}/scripts/render_config.py" \
  '"WEAVE_AGENT_RUNTIME_OPTIONAL_CLIENT_SCOPES":'
require "${ROOT_DIR}/scripts/render_config.py" \
  '"requiredScopes": ["files.read", "mcp.tools"]'
require "${ROOT_DIR}/compose.yaml" \
  'file: ${WEAVE_SECRET_ROOT:-./.generated/dev/secrets}/identity-reference-hmac-key'
require "${ROOT_DIR}/compose.yaml" \
  'target: identity-reference-hmac-key'
[[ "$(grep -Fc 'source: identity-reference-hmac-key' "${ROOT_DIR}/compose.yaml")" == "1" ]] ||
  fail "identity-reference-hmac-key must be mounted into Weave Server exactly once"
require "${REPO_ROOT}/settings.gradle" "include 'weave-application-core',"
require "${REPO_ROOT}/settings.gradle" "'weave-persistence-jpa',"
require "${REPO_ROOT}/settings.gradle" "'weave-runtime-security-adapters',"
require "${REPO_ROOT}/settings.gradle" "'weave-runtime-provider-adapters',"
require "${REPO_ROOT}/settings.gradle" "'infra',"
require "${REPO_ROOT}/settings.gradle" "'server',"
require "${REPO_ROOT}/settings.gradle" "'weave-mcp-server'"
require "${REPO_ROOT}/infra/build.gradle" 'apply from: "$projectDir/gradle/tasks/environment-profiles.gradle"'
require "${REPO_ROOT}/infra/gradle/tasks/environment-profiles.gradle" '"identity${profileTitle}${operationTitle}"'
require "${REPO_ROOT}/infra/gradle/tasks/environment-profiles.gradle" "'identityOpsImageBuild'"
require "${REPO_ROOT}/infra/gradle/tasks/environment-profiles.gradle" "'keycloakRuntimeImageBuild'"
require "${REPO_ROOT}/server/build.gradle" 'apply from: "${projectDir}/gradle/tasks/development.gradle"'
require "${REPO_ROOT}/server/gradle/tasks/development.gradle" "'serverDevH2Test'"
require "${REPO_ROOT}/server/gradle/tasks/development.gradle" "'serverPostgresIntegrationTest'"
require "${REPO_ROOT}/.github/workflows/test-stack-deploy.yml" 'WEAVE_TEST_BACKUP_ROOT'
require "${REPO_ROOT}/.github/workflows/test-stack-deploy.yml" './compose.sh test adoption-check'
require "${REPO_ROOT}/.github/workflows/test-stack-deploy.yml" './adoption-rehearsal.sh test'
require "${REPO_ROOT}/.github/workflows/test-stack-deploy.yml" 'WEAVE_ADOPTION_RECEIPT'
reject "${REPO_ROOT}/build.gradle" 'gradle/tasks/environment-profiles.gradle'

reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CREATE_TEST_USER'
reject "${ROOT_DIR}/scripts/compose_runtime.py" 'WEAVE_TEST_USERS_FILE'
reject "${ROOT_DIR}/keycloak/identity_ops.py" 'WEAVE_TEST_USERS_FILE'
reject "${ROOT_DIR}/keycloak/identity_ops.py" 'kcadm.call("set-password"'
reject "${ROOT_DIR}/compose.yaml" '/var/run/docker.sock'
reject "${ROOT_DIR}/compose.yaml" 'OpenProject'
reject "${ROOT_DIR}/compose.yaml" 'WEAVE_IDENTITY_EVENTS_HMAC_SECRET'
reject "${ROOT_DIR}/compose.yaml" 'weave/weave.identity.invitations.keycloak.client-secret'
reject "${ROOT_DIR}/scripts/render_config.py" \
  '"weave.identity.invitations.keycloak.client-secret": "keycloak-weave-identity-admin"'
reject "${REPO_ROOT}/server/src/main/resources/application.yml" 'WEAVE_IDENTITY_EVENTS_HMAC_SECRET'
reject "${REPO_ROOT}/server/src/main/resources/application-base.yml" 'weave-keycloak:8080'
reject "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/config/IdentityInvitationProperties.java" \
  'weave-keycloak:8080'
reject "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/config/SecurityConfig.java" '/api/internal/keycloak/events'

printf 'V01_INFRA_CONTROL_PLANE_BOOTSTRAP status=passed infrastructure product contract tests passed\n'
