#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=prepare_bundled_katago.sh
source "$ROOT_DIR/scripts/prepare_bundled_katago.sh"

[[ "$KATAGO_TAG" == "v1.17.1" ]]
[[ "$(expected_asset_sha256 "$WINDOWS_ASSET")" == \
  "3a7538ecb6facefcfe16d649fd695c29e44f8372cb7de8c316eee5779865f379" ]]
[[ "$(expected_asset_sha256 "$WINDOWS_OPENCL_ASSET")" == \
  "68d0a9b11ef7e3c1ddfc5bcd400306ca66c3770dd67a22cb377d3aaaf32e8c66" ]]
[[ "$(expected_asset_sha256 "$WINDOWS_NVIDIA_ASSET")" == \
  "b081832d48b4a553436ad5c54f9c4f4feff39df7b52e68228929e9f8a70988bc" ]]
[[ "$(expected_asset_sha256 "$WINDOWS_NVIDIA50_CUDA_ASSET")" == \
  "476a35c0b43cc937906d4313acaf592a97a30775ec51d37f5401a284ad9fa0f9" ]]
[[ "$(expected_asset_sha256 "$LINUX_ASSET")" == \
  "cca71fff39abd19bd9acfc17750025d4bb0ee6adbad99d7513a2c6401b0a7af3" ]]
[[ "$(expected_asset_sha256 "$LINUX_OPENCL_ASSET")" == \
  "be537295868c0b8ff6985e62e411fff67cbba2dc872343c74896063de1ef51e9" ]]
[[ "$(expected_asset_sha256 "$LINUX_NVIDIA_ASSET")" == \
  "451ae213021cef0d2fcbfae650479532b53361c5ecbdfe1a5a643065bc76edc8" ]]

uname() {
  printf '%s\n' "MINGW64_NT-10.0"
}

if is_macos_host; then
  echo "Windows Git Bash must not be detected as macOS" >&2
  exit 1
fi

skip_output="$(prepare_macos_bundle)"
grep -Fq "Skipping macOS KataGo bundle on non-macOS host" <<<"$skip_output"

uname() {
  printf '%s\n' "Linux"
}

if is_macos_host; then
  echo "Linux must not be detected as macOS" >&2
  exit 1
fi

uname() {
  printf '%s\n' "Darwin"
}

if ! is_macos_host; then
  echo "Darwin must be detected as macOS" >&2
  exit 1
fi

echo "prepare_bundled_katago host gating tests passed"
