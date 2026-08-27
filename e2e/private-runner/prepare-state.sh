#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
state_root="${WEAVE_E2E_STATE_ROOT:-$root/.state}"
secret_root="$state_root/secrets"
pki_root="$state_root/pki"

umask 077
mkdir -p "$secret_root" "$pki_root"

write_secret() {
  local path="$1"
  if [[ ! -s "$path" ]]; then
    openssl rand -hex 32 >"$path"
  fi
  chmod 0600 "$path"
}

for secret in \
  weave-db.password \
  keycloak-db.password \
  nextcloud-db.password \
  nextcloud-admin.password \
  runner-enrollment.secret \
  internal-api.token \
  tuwunel-registration.token; do
  write_secret "$secret_root/$secret"
done

generate_ca() {
  local prefix="$1"
  local common_name="$2"
  local key="$pki_root/$prefix-key.pem"
  local certificate="$pki_root/$prefix.pem"
  if [[ ! -s "$key" || ! -s "$certificate" ]]; then
    rm -f "$key" "$certificate"
    openssl genpkey \
      -algorithm EC \
      -pkeyopt ec_paramgen_curve:P-256 \
      -out "$key"
    openssl req \
      -x509 \
      -new \
      -sha256 \
      -key "$key" \
      -days 7 \
      -subj "/CN=$common_name" \
      -out "$certificate"
  fi
  chmod 0600 "$key" "$certificate"
}

generate_ca engine-ca "Weave E2E Engine CA"
generate_ca runner-ca "Weave E2E Runner CA"

engine_key="$pki_root/engine-key.pem"
engine_csr="$pki_root/engine.csr"
engine_certificate="$pki_root/engine-cert.pem"
engine_extensions="$pki_root/engine.ext"

if [[ ! -s "$engine_key" || ! -s "$engine_certificate" ]]; then
  rm -f "$engine_key" "$engine_csr" "$engine_certificate" "$engine_extensions"
  openssl genpkey \
    -algorithm EC \
    -pkeyopt ec_paramgen_curve:P-256 \
    -out "$engine_key"
  openssl req \
    -new \
    -sha256 \
    -key "$engine_key" \
    -subj "/CN=weave-server" \
    -out "$engine_csr"
  cat >"$engine_extensions" <<'EOF'
subjectAltName=DNS:weave-server,DNS:localhost,IP:127.0.0.1
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyAgreement
EOF
  openssl x509 \
    -req \
    -sha256 \
    -in "$engine_csr" \
    -CA "$pki_root/engine-ca.pem" \
    -CAkey "$pki_root/engine-ca-key.pem" \
    -CAcreateserial \
    -days 7 \
    -extfile "$engine_extensions" \
    -out "$engine_certificate"
fi

chmod 0600 "$engine_key" "$engine_certificate" "$pki_root/engine-ca.pem"
rm -f "$engine_csr" "$engine_extensions"

printf 'Prepared disposable private Runner state at %s\n' "$state_root"
