#!/usr/bin/env sh

set -eu

operation="${1:-plan}"
state_root="${WEAVE_IDENTITY_OPS_STATE_ROOT:-/state}"
configuration_root="/work/weave-identity"
state_file="${state_root}/terraform.tfstate"
plan_file="${state_root}/identity.plan"
receipt_file="${state_root}/apply-receipt.txt"

fail() {
  printf 'WEAVE_IDENTITY_OPS_ERROR %s\n' "$*" >&2
  exit 1
}

case "${operation}" in
  plan | apply | validate) ;;
  *) fail "operation must be plan, apply, or validate" ;;
esac

test -d "${state_root}" || fail "state SecretRef mount is unavailable"
test -w "${state_root}" || fail "state SecretRef mount is not writable by the runtime user"
test ! -L "${state_root}" || fail "state SecretRef mount must not be a symbolic link"

rm -rf "${configuration_root}"
mkdir -m 0700 "${configuration_root}"
cp -R /opt/weave/identity/. "${configuration_root}/"
cat >"${configuration_root}/.tofurc" <<'EOF'
provider_installation {
  filesystem_mirror {
    path    = "/opt/weave/provider-cache"
    include = ["registry.opentofu.org/keycloak/keycloak"]
  }
  direct {
    exclude = ["registry.opentofu.org/keycloak/keycloak"]
  }
}
EOF
export TF_CLI_CONFIG_FILE="${configuration_root}/.tofurc"

cd "${configuration_root}"
tofu init \
  -backend-config="path=${state_file}" \
  -input=false \
  -lockfile=readonly \
  -no-color >/dev/null

case "${operation}" in
  validate)
    tofu validate -no-color
    ;;
  plan)
    tofu plan \
      -input=false \
      -lock=true \
      -lock-timeout=30s \
      -no-color \
      -out="${plan_file}"
    printf 'WEAVE_IDENTITY_OPS_PLAN state=review-required plan=%s\n' "${plan_file}"
    ;;
  apply)
    test -f "${plan_file}" || fail "reviewed identity plan is unavailable"
    tofu apply \
      -input=false \
      -lock=true \
      -lock-timeout=30s \
      -no-color \
      "${plan_file}"
    rm -f "${plan_file}"
    printf 'status=applied\n' >"${receipt_file}"
    chmod 0600 "${receipt_file}"
    printf 'WEAVE_IDENTITY_OPS_APPLY status=applied\n'
    ;;
esac
