#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KATAGO_TAG="${KATAGO_TAG:-v1.17.0}"
KATAGO_COMMIT="${KATAGO_COMMIT:-2d0538979f73e580e81185ce836089ddcb58659a}"
CACHE_DIR="${CACHE_DIR:-$ROOT_DIR/.cache/katago-source}"
SOURCE_DIR="$CACHE_DIR/source-$KATAGO_COMMIT"
ARCH="$(uname -m)"
if [[ "$ARCH" == "aarch64" ]]; then
  ARCH="arm64"
fi
BUILD_DIR="$CACHE_DIR/build-${KATAGO_TAG#v}-$ARCH-${KATAGO_COMMIT:0:12}"
OUTPUT_BIN="$BUILD_DIR/katago"

require_cmd() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command for macOS KataGo build: $command_name" >&2
    exit 1
  fi
}

is_expected_binary() {
  local binary="$1"
  local output
  if [[ ! -x "$binary" ]]; then
    return 1
  fi
  output="$("$binary" version 2>&1)" || return 1
  grep -Fq "KataGo ${KATAGO_TAG}" <<<"$output" \
    && grep -Fq "Git revision: ${KATAGO_COMMIT}" <<<"$output" \
    && grep -Fq "Using Metal backend" <<<"$output"
}

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "The macOS KataGo builder can only run on macOS." >&2
  exit 1
fi

if is_expected_binary "$OUTPUT_BIN"; then
  echo "$OUTPUT_BIN"
  exit 0
fi

require_cmd git
require_cmd cmake
require_cmd ninja

mkdir -p "$CACHE_DIR"
if [[ ! -d "$SOURCE_DIR/.git" ]]; then
  rm -rf "$SOURCE_DIR"
  git clone --filter=blob:none --no-checkout https://github.com/lightvector/KataGo.git \
    "$SOURCE_DIR" >&2
fi

git -C "$SOURCE_DIR" fetch --depth 1 origin "$KATAGO_COMMIT" >&2
git -C "$SOURCE_DIR" checkout --detach --force "$KATAGO_COMMIT" >&2
actual_commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
if [[ "$actual_commit" != "$KATAGO_COMMIT" ]]; then
  echo "Unexpected KataGo source commit: expected $KATAGO_COMMIT, got $actual_commit" >&2
  exit 1
fi

cmake_options=()
if command -v brew >/dev/null 2>&1; then
  brew_root="$(brew --prefix)"
  prefix_paths=("$brew_root")
  libzip_prefix="$(brew --prefix libzip 2>/dev/null || true)"
  if [[ -n "$libzip_prefix" ]]; then
    libzip_include="$libzip_prefix/include"
    libzip_library="$libzip_prefix/lib/libzip.dylib"
    for required_file in \
      "$libzip_include/zip.h" \
      "$libzip_include/zipconf.h" \
      "$libzip_library"; do
      if [[ ! -e "$required_file" ]]; then
        echo "Homebrew libzip is incomplete; missing $required_file" >&2
        exit 1
      fi
    done
    prefix_paths+=("$libzip_prefix")
    cmake_options+=(
      -DLIBZIP_INCLUDE_DIR_ZIP="$libzip_include"
      -DLIBZIP_INCLUDE_DIR_ZIPCONF="$libzip_include"
      -DLIBZIP_LIBRARY="$libzip_library"
    )
  fi
  prefix_path="$(IFS=';'; echo "${prefix_paths[*]}")"
  cmake_options+=(-DCMAKE_PREFIX_PATH="$prefix_path")
fi

rm -rf "$BUILD_DIR"
cmake \
  -S "$SOURCE_DIR/cpp" \
  -B "$BUILD_DIR" \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_OSX_ARCHITECTURES="$ARCH" \
  -DUSE_BACKEND=METAL \
  -DBUILD_DISTRIBUTED=0 \
  "${cmake_options[@]}" >&2
cmake --build "$BUILD_DIR" --target katago --parallel "$(sysctl -n hw.logicalcpu)" >&2

if ! is_expected_binary "$OUTPUT_BIN"; then
  echo "Built KataGo did not report ${KATAGO_TAG} with the Metal backend." >&2
  "$OUTPUT_BIN" version >&2 || true
  exit 1
fi

echo "$OUTPUT_BIN"
