#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=prepare_bundled_katago.sh
source "$ROOT_DIR/scripts/prepare_bundled_katago.sh"

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
