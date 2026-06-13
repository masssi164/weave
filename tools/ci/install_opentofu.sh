#!/usr/bin/env bash
set -euo pipefail

version="${1:-${OPENTOFU_VERSION:-1.10.6}}"
bin_dir="${OPENTOFU_BIN_DIR:-${RUNNER_TEMP:-/tmp}/weave-opentofu/bin}"
evidence_dir="${WEAVE_ACCEPTANCE_EVIDENCE_DIR:-}"

classify_failure() {
  local message="$1"
  echo "::error title=toolchain_bootstrap::${message}" >&2
  if [ -n "$evidence_dir" ]; then
    mkdir -p "$evidence_dir"
    cat >"$evidence_dir/toolchain-bootstrap.json" <<JSON
{
  "classification": "toolchain_bootstrap",
  "tool": "opentofu",
  "version": "${version}",
  "message": "${message//"/\\"}"
}
JSON
  fi
}

case "$(uname -s)" in
  Darwin) os="darwin" ;;
  Linux) os="linux" ;;
  *) classify_failure "Unsupported OpenTofu runner OS: $(uname -s)"; exit 1 ;;
esac

case "$(uname -m)" in
  arm64|aarch64) arch="arm64" ;;
  x86_64|amd64) arch="amd64" ;;
  *) classify_failure "Unsupported OpenTofu runner architecture: $(uname -m)"; exit 1 ;;
esac

asset="tofu_${version}_${os}_${arch}.zip"
base_url="https://github.com/opentofu/opentofu/releases/download/v${version}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

mkdir -p "$bin_dir"

retry_curl() {
  local url="$1" out="$2"
  curl --fail --location --show-error --silent \
    --connect-timeout 20 \
    --max-time 180 \
    --retry 4 \
    --retry-delay 5 \
    --retry-all-errors \
    --output "$out" \
    "$url"
}

if ! retry_curl "$base_url/$asset" "$tmp_dir/$asset"; then
  classify_failure "Failed to download OpenTofu ${version} asset ${asset} after retries"
  exit 1
fi

if ! unzip -q "$tmp_dir/$asset" -d "$tmp_dir/unpacked"; then
  classify_failure "Downloaded OpenTofu ${version} asset ${asset} was not a valid zip archive"
  exit 1
fi

if [ ! -x "$tmp_dir/unpacked/tofu" ]; then
  classify_failure "OpenTofu ${version} asset ${asset} did not contain an executable tofu binary"
  exit 1
fi

install -m 0755 "$tmp_dir/unpacked/tofu" "$bin_dir/tofu"

if [ -n "${GITHUB_PATH:-}" ]; then
  echo "$bin_dir" >> "$GITHUB_PATH"
fi
if [ -n "${GITHUB_ENV:-}" ]; then
  echo "WEAVE_IAC_BIN=$bin_dir/tofu" >> "$GITHUB_ENV"
fi
"$bin_dir/tofu" version
