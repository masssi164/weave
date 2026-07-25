#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TENANT_DOMAIN="${WEAVE_TENANT_DOMAIN:-weave.test}"
PUBLIC_SCHEME="${WEAVE_PUBLIC_SCHEME:-https}"
PROXY_PORT="${WEAVE_PROXY_HTTPS_HOST_PORT:-443}"
MAILPIT_HOST="mail.${TENANT_DOMAIN}"
PORT_SUFFIX=""
if [[ "${PUBLIC_SCHEME}" != "https" || "${PROXY_PORT}" != "443" ]]; then
  PORT_SUFFIX=":${PROXY_PORT}"
fi
MAILPIT_URL="${WEAVE_MAILPIT_URL:-${PUBLIC_SCHEME}://${MAILPIT_HOST}${PORT_SUFFIX}}"
CA_FILE="${WEAVE_CADDY_TLS_CA_FILE:-${WEAVE_TLS_ROOT:-${ROOT_DIR}/.generated/dogfood/tls}/ca.pem}"

fail() {
  printf 'iPhone Mailpit smoke failed: %s\n' "$*" >&2
  exit 1
}

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v openssl >/dev/null 2>&1 || fail "openssl is required"
[[ -r "${CA_FILE}" ]] || fail "local CA is not readable; run install.sh first"

resolved="$(python3 - "${MAILPIT_HOST}" <<'PY'
import socket
import sys

try:
    addresses = sorted({item[4][0] for item in socket.getaddrinfo(sys.argv[1], 443)})
except socket.gaierror:
    addresses = []
print(" ".join(addresses))
PY
)"
[[ -n "${resolved}" ]] || fail "${MAILPIT_HOST} does not resolve; configure the same private DNS on the iPhone Wi-Fi network"

openssl verify -CAfile "${CA_FILE}" "${CA_FILE}" >/dev/null || fail "local CA is not self-verifiable"
status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --cacert "${CA_FILE}" "${MAILPIT_URL}")"
[[ "${status}" == "200" ]] || fail "${MAILPIT_URL} returned HTTP ${status}; the client address must be inside WEAVE_MAILPIT_ALLOWED_CIDRS"

printf 'Mailpit iPhone prerequisite smoke passed.\n'
printf 'Safari URL: %s\n' "${MAILPIT_URL}"
printf 'Private DNS addresses: %s\n' "${resolved}"
printf 'Manual device gate: open the URL in Safari after enabling full trust for the Weave local CA.\n'
