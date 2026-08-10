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
  compose.yaml compose.dev.yaml compose.dogfood.yaml compose.e2e.yaml compose.prod.yaml compose.sh \
  environments/dogfood.env.example environments/e2e.env.example \
  scripts/compose_env.py scripts/compose_runtime.py scripts/render_config.py \
  scripts/rendering/gateway.py scripts/rendering/providers.py \
  scripts/nextcloud_reconcile.py scripts/keycloak_migration.py keycloak/Dockerfile.runtime; do
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

require "${ROOT_DIR}/compose.yaml" 'profiles: *identity-profiles'
require "${ROOT_DIR}/compose.yaml" 'profiles: *deployment-profiles'
require "${ROOT_DIR}/compose.yaml" 'profiles: *application-profiles'
require "${ROOT_DIR}/compose.yaml" 'POSTGRES_PASSWORD_FILE: /run/secrets/postgres-admin-password'
require "${ROOT_DIR}/compose.yaml" 'SPRING_CONFIG_IMPORT: configtree:/run/secrets/weave/'
require "${ROOT_DIR}/compose.yaml" \
  'target: /run/secrets/identity-admin/weave-identity-admin-private-jwk.json'
[[ "$(grep -Fc 'source: identity-admin-private-jwk' "${ROOT_DIR}/compose.yaml")" == "1" ]] ||
  fail "identity-admin private JWK must be mounted only by Weave Server"
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.managed: "true"'
require "${ROOT_DIR}/compose.dev.yaml" 'host.docker.internal:host-gateway'
require "${ROOT_DIR}/compose.dogfood.yaml" 'WEAVE_RELEASE_POSTURE: dogfood'
require "${ROOT_DIR}/compose.e2e.yaml" 'WEAVE_CHAT_E2E_PROOF_ENABLED: "true"'
require "${ROOT_DIR}/compose.e2e.yaml" 'context-authorization-memberships.json'
require "${ROOT_DIR}/compose.e2e.yaml" 'WEAVE_RELEASE_POSTURE: dogfood'
require "${ROOT_DIR}/compose.yaml" 'runtime-state-volume-init:'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.operation: volume-initialize'
require "${ROOT_DIR}/compose.yaml" 'mc alias set -- runtime-state'
require "${ROOT_DIR}/compose.yaml" 'agent-runtime-keys-init:'
require "${ROOT_DIR}/compose.prod.yaml" 'WEAVE_RELEASE_POSTURE: stable'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.protocol: s3-compatible'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.data-class: runtime-state-sensitive'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.operation: bucket-initialize'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.operation: key-initialize'
require "${ROOT_DIR}/compose.yaml" 'MINIO_ROOT_USER_FILE: /run/secrets/runtime-state-s3-access-key'
require "${ROOT_DIR}/compose.yaml" 'mc version enable runtime-state/weave-runtime-state'
require "${ROOT_DIR}/scripts/compose_env.py" 'OPERATOR_ENVIRONMENTS = ("dev", "dogfood", "prod", "e2e")'
require "${ROOT_DIR}/scripts/compose_env.py" 'refusing to deploy {environment} from an example environment file'
require "${ROOT_DIR}/scripts/compose_env.py" 'persistent-dogfood'
require "${ROOT_DIR}/scripts/compose_runtime.py" 'refusing unowned existing Docker'
reject "${ROOT_DIR}/scripts/compose_runtime.py" 'WEAVE_ADOPTION_RECEIPT'
[[ ! -e "${ROOT_DIR}/keycloak/identity_ops.py" ]] || fail "general Identity Ops authority remains"
[[ ! -e "${ROOT_DIR}/keycloak/Dockerfile.identity-ops" ]] || fail "Identity Ops image remains"
[[ ! -e "${ROOT_DIR}/scripts/build_identity_ops_image.py" ]] || fail "Identity Ops image builder remains"
reject "${ROOT_DIR}/compose.yaml" 'identity-ops:'
reject "${ROOT_DIR}/compose.yaml" 'keycloak-bootstrap-admin-password'
require "${ROOT_DIR}/compose.yaml" 'keycloak-realm-migration-bootstrap:'
require "${ROOT_DIR}/compose.yaml" 'keycloak-realm-migration-receipt-check:'
require "${ROOT_DIR}/compose.yaml" 'network_mode: none'
require "${ROOT_DIR}/compose.yaml" 'com.massimotter.weave.operation: keycloak-realm-migration-receipt-verify'
require "${ROOT_DIR}/scripts/keycloak_migration.py" 'bootstrapAuthorityNegativeReadbackVerified'
reject "${ROOT_DIR}/scripts/compose_runtime.py" 'kcadm'
reject "${ROOT_DIR}/scripts/compose_runtime.py" '--client-secret:env=WEAVE_IDENTITY'
require "${ROOT_DIR}/scripts/build_keycloak_image.py" 'STOCK_KEYCLOAK_REFERENCE ='
require "${ROOT_DIR}/scripts/build_keycloak_image.py" '"weave.downstream-keycloak-image.v1"'
require "${ROOT_DIR}/keycloak/Dockerfile.runtime" 'com.massimotter.weave.keycloak-patch-sha256'
require "${ROOT_DIR}/keycloak/Dockerfile.runtime" 'kc.sh build --db=postgres --vault=file'
require "${ROOT_DIR}/compose.prod.yaml" 'target: /opt/keycloak/vault/weave_smtp-password'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'ordinary reconciliation refuses an implicit rotation'
require "${ROOT_DIR}/scripts/nextcloud_reconcile.py" 'oidcManagedProjectionDigest'

for profile in dev dogfood e2e prod; do
  require "${REPO_ROOT}/server/src/main/resources/application-${profile}.yml" "on-profile: ${profile}"
  require "${REPO_ROOT}/weave-mcp-server/src/main/resources/application-${profile}.yml" "on-profile: ${profile}"
done
require "${REPO_ROOT}/server/src/main/resources/application-dogfood.yml" 'issuer-uri: https://auth.weave.test/realms/weave'
require "${REPO_ROOT}/weave-mcp-server/src/main/resources/application-dogfood.yml" 'authorization-server: https://auth.weave.test/realms/weave'
reject "${ROOT_DIR}/scripts/render_config.py" '_backend_env'
reject "${ROOT_DIR}/scripts/render_config.py" '_mcp_env'
reject "${ROOT_DIR}/scripts/render_config.py" 'backend/public.env'
reject "${ROOT_DIR}/scripts/render_config.py" 'mcp/public.env'

require "${ROOT_DIR}/scripts/render_config.py" '"requiredScopes": ["files.read", "mcp.tools"]'
require "${ROOT_DIR}/scripts/render_config.py" '"credentialRefTemplate": "credentialref://weave/runtime/{cellRef}/{workloadClientId}/mcp"'

require "${ROOT_DIR}/compose.yaml" \
  'file: ${WEAVE_SECRET_ROOT:-./.generated/dev/secrets}/identity-reference-hmac-key'
require "${ROOT_DIR}/compose.yaml" 'target: identity-reference-hmac-key'
[[ "$(grep -Fc 'source: identity-reference-hmac-key' "${ROOT_DIR}/compose.yaml")" == "1" ]] ||
  fail "identity-reference-hmac-key must be mounted into Weave Server exactly once"
reject "${REPO_ROOT}/server/src/main/resources/application.yml" \
  'spring.security.oauth2.client.registration.weave-identity-admin.client-secret'

require "${REPO_ROOT}/settings.gradle" "include 'weave-application-core',"
require "${REPO_ROOT}/settings.gradle" "'weave-persistence-jpa',"
require "${REPO_ROOT}/settings.gradle" "'weave-runtime-security-adapters',"
require "${REPO_ROOT}/settings.gradle" "'weave-runtime-provider-adapters',"
require "${REPO_ROOT}/settings.gradle" "'infra',"
require "${REPO_ROOT}/settings.gradle" "'server',"
require "${REPO_ROOT}/settings.gradle" "'weave-mcp-server'"
require "${REPO_ROOT}/infra/build.gradle" 'apply from: "$projectDir/gradle/tasks/environment-profiles.gradle"'
require "${REPO_ROOT}/infra/gradle/tasks/environment-profiles.gradle" '"keycloak${profileTitle}MigrationApply"'
require "${REPO_ROOT}/infra/gradle/tasks/environment-profiles.gradle" "'keycloakRuntimeImageBuild'"
require "${REPO_ROOT}/server/build.gradle" 'apply from: "${projectDir}/gradle/tasks/development.gradle"'
require "${REPO_ROOT}/server/gradle/tasks/development.gradle" "'serverDevH2Test'"
require "${REPO_ROOT}/server/gradle/tasks/development.gradle" "'serverPostgresIntegrationTest'"
readonly TEST_STACK_WORKFLOW="${REPO_ROOT}/.github/workflows/test-stack-deploy.yml"
require "${TEST_STACK_WORKFLOW}" 'deployment_mode:'
require "${TEST_STACK_WORKFLOW}" '- fresh-start'
require "${TEST_STACK_WORKFLOW}" 'Create or reuse the exact private backup, restore proof, and Fresh Start plan'
require "${TEST_STACK_WORKFLOW}" 'FreshStartBackupRehearsal.json'
require "${TEST_STACK_WORKFLOW}" 'plan.json'
reject "${TEST_STACK_WORKFLOW}" 'WEAVE_ADOPTION_RECEIPT'
reject "${REPO_ROOT}/build.gradle" 'gradle/tasks/environment-profiles.gradle'

reject "${ROOT_DIR}/compose.yaml" 'WEAVE_CREATE_TEST_USER'
reject "${ROOT_DIR}/scripts/compose_runtime.py" 'WEAVE_TEST_USERS_FILE'
reject "${ROOT_DIR}/compose.yaml" '/var/run/docker.sock'
reject "${ROOT_DIR}/compose.yaml" 'OpenProject'
reject "${ROOT_DIR}/compose.yaml" 'WEAVE_IDENTITY_EVENTS_HMAC_SECRET'
reject "${ROOT_DIR}/compose.yaml" 'weave/weave.identity.invitations.keycloak.client-secret'
reject "${ROOT_DIR}/scripts/init_secrets.py" '"keycloak-weave-identity-admin",'
require "${ROOT_DIR}/scripts/init_secrets.py" \
  '("keycloak-weave-identity-admin-jwk.json", "weave-identity-admin-current")'
reject "${REPO_ROOT}/server/src/main/resources/application.yml" 'WEAVE_IDENTITY_EVENTS_HMAC_SECRET'
reject "${REPO_ROOT}/server/src/main/resources/application-base.yml" 'weave-keycloak:8080'
reject "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/config/IdentityInvitationProperties.java" \
  'weave-keycloak:8080'
reject "${REPO_ROOT}/server/src/main/java/com/massimotter/weave/backend/config/SecurityConfig.java" '/api/internal/keycloak/events'

printf 'V01_INFRA_CONTROL_PLANE_BOOTSTRAP status=passed infrastructure product contract tests passed\n'
