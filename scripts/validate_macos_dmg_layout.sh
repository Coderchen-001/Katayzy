#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "validate_macos_dmg_layout.sh only supports macOS." >&2
  exit 1
fi

DMG_PATH="${1:-}"
if [[ -z "$DMG_PATH" || ! -f "$DMG_PATH" ]]; then
  echo "Usage: $0 <path-to.dmg> [expected-architecture-label]" >&2
  exit 1
fi
EXPECTED_ARCHITECTURE_LABEL="${2:-}"
if [[ -z "$EXPECTED_ARCHITECTURE_LABEL" ]]; then
  case "$(basename "$DMG_PATH")" in
    *mac-apple-silicon*)
      EXPECTED_ARCHITECTURE_LABEL="Apple Silicon"
      ;;
    *mac-intel*)
      EXPECTED_ARCHITECTURE_LABEL="Intel"
      ;;
  esac
fi

WORK_DIR="$(mktemp -d -t lizzieyzy-dmg-validate.XXXXXX)"
MOUNT_POINT="$WORK_DIR/mount"
ATTACH_PLIST="$WORK_DIR/attach.plist"
DISK_INFO_PLIST="$WORK_DIR/disk-info.plist"
MOUNTED_TARGET=""

detach_image() {
  local target="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    if hdiutil detach "$target" -quiet >/dev/null 2>&1; then
      return 0
    fi
    sleep "$attempt"
  done
  hdiutil detach "$target" -force -quiet >/dev/null 2>&1 || true
}

cleanup() {
  if [[ -n "$MOUNTED_TARGET" ]]; then
    detach_image "$MOUNTED_TARGET"
  fi
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$MOUNT_POINT"
hdiutil attach "$DMG_PATH" \
  -mountpoint "$MOUNT_POINT" \
  -readonly \
  -noverify \
  -noautoopen \
  -nobrowse \
  -plist >"$ATTACH_PLIST"

MOUNTED_TARGET="$(
  python3 - "$ATTACH_PLIST" <<'PY'
import plistlib
import sys

with open(sys.argv[1], "rb") as f:
    data = plistlib.load(f)
for entity in data.get("system-entities", []):
    if entity.get("mount-point"):
        print(entity.get("dev-entry", ""))
        break
PY
)"
if [[ -z "$MOUNTED_TARGET" ]]; then
  MOUNTED_TARGET="$MOUNT_POINT"
fi
diskutil info -plist "$MOUNT_POINT" >"$DISK_INFO_PLIST"
MOUNTED_VOLUME_NAME="$(
  python3 - "$DISK_INFO_PLIST" <<'PY'
import plistlib
import sys

with open(sys.argv[1], "rb") as f:
    data = plistlib.load(f)
print(data.get("VolumeName", ""))
PY
)"

APP_COUNT="$(find "$MOUNT_POINT" -maxdepth 1 -type d -name '*.app' -print | wc -l | tr -d ' ')"
if [[ "$APP_COUNT" -ne 1 ]]; then
  echo "Expected exactly one .app at DMG root; found $APP_COUNT." >&2
  find "$MOUNT_POINT" -maxdepth 1 -print >&2
  exit 1
fi

if [[ ! -L "$MOUNT_POINT/Applications" ]]; then
  echo "DMG is missing the /Applications drag target symlink." >&2
  find "$MOUNT_POINT" -maxdepth 1 -print >&2
  exit 1
fi

if [[ "$(readlink "$MOUNT_POINT/Applications")" != "/Applications" ]]; then
  echo "Applications drag target does not point to /Applications." >&2
  exit 1
fi

if [[ ! -f "$MOUNT_POINT/.DS_Store" ]]; then
  echo "DMG is missing .DS_Store Finder layout metadata." >&2
  find "$MOUNT_POINT" -maxdepth 1 -print >&2
  exit 1
fi
if [[ ! -s "$MOUNT_POINT/.DS_Store" ]]; then
  echo "DMG Finder layout metadata is empty." >&2
  exit 1
fi
DS_STORE_STRINGS="$(strings -a "$MOUNT_POINT/.DS_Store")"
if ! grep -q "install-background.tiff" <<<"$DS_STORE_STRINGS"; then
  echo "DMG Finder layout does not reference the branded background." >&2
  exit 1
fi

BACKGROUND_DIR="$MOUNT_POINT/.background"
BACKGROUND_TIFF="$BACKGROUND_DIR/install-background.tiff"
LAYOUT_METADATA="$BACKGROUND_DIR/layout.json"
if [[ ! -d "$BACKGROUND_DIR" ]]; then
  echo "DMG is missing the hidden background resource directory." >&2
  exit 1
fi
if [[ ! -s "$BACKGROUND_TIFF" ]]; then
  echo "DMG is missing the branded Retina background." >&2
  exit 1
fi
if [[ ! -s "$LAYOUT_METADATA" ]]; then
  echo "DMG is missing install layout metadata." >&2
  exit 1
fi

TIFF_INFO="$(tiffutil -info "$BACKGROUND_TIFF")"
TIFF_IMAGE_COUNT="$(grep -c '^Directory at ' <<<"$TIFF_INFO")"
if [[ "$TIFF_IMAGE_COUNT" -ne 2 ]]; then
  echo "DMG background must contain exactly 1x and 2x images; found $TIFF_IMAGE_COUNT." >&2
  exit 1
fi
if ! grep -q "Image Width: 800 Image Length: 500" <<<"$TIFF_INFO"; then
  echo "DMG background is missing the 800x500 representation." >&2
  exit 1
fi
if ! grep -q "Image Width: 1600 Image Length: 1000" <<<"$TIFF_INFO"; then
  echo "DMG background is missing the 1600x1000 Retina representation." >&2
  exit 1
fi

python3 - \
  "$LAYOUT_METADATA" \
  "$EXPECTED_ARCHITECTURE_LABEL" \
  "$MOUNTED_VOLUME_NAME" <<'PY'
from pathlib import Path
import json
import sys

metadata_path = Path(sys.argv[1])
expected_architecture = sys.argv[2]
volume_name = sys.argv[3]
metadata = json.loads(metadata_path.read_text(encoding="utf-8"))

if metadata.get("schemaVersion") != 1:
    raise SystemExit("Unexpected DMG layout metadata schema.")
if metadata.get("background") != "install-background.tiff":
    raise SystemExit("Unexpected DMG background metadata.")
if metadata.get("window") != {"width": 800, "height": 500}:
    raise SystemExit("Unexpected DMG install window dimensions.")
if metadata.get("appPosition") != {"x": 185, "y": 300}:
    raise SystemExit("Unexpected app icon position.")
if metadata.get("applicationsPosition") != {"x": 615, "y": 300}:
    raise SystemExit("Unexpected Applications icon position.")

architecture = metadata.get("architecture", "")
if expected_architecture and architecture != expected_architecture:
    raise SystemExit(
        f"DMG architecture label mismatch: expected {expected_architecture!r}, "
        f"found {architecture!r}."
    )
if expected_architecture and expected_architecture not in volume_name:
    raise SystemExit(
        f"DMG volume name does not identify {expected_architecture}: {volume_name!r}."
    )
PY

APP_PATH="$(find "$MOUNT_POINT" -maxdepth 1 -type d -name '*.app' -print -quit)"
KATAGO_BUNDLES=()
while IFS= read -r -d '' katago_path; do
  KATAGO_BUNDLES+=("$(dirname "$katago_path")")
done < <(
  find "$APP_PATH/Contents/app/engines/katago" \
    -mindepth 2 -maxdepth 2 -type f -name katago -print0 2>/dev/null
)

if [[ "${#KATAGO_BUNDLES[@]}" -gt 1 ]]; then
  echo "Expected at most one native macOS KataGo bundle; found ${#KATAGO_BUNDLES[@]}." >&2
  exit 1
fi
if [[ "${#KATAGO_BUNDLES[@]}" -eq 1 ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  python3 "$SCRIPT_DIR/macos_katago_bundle.py" audit \
    --bundle "${KATAGO_BUNDLES[0]}"
fi

echo "Validated macOS drag-install DMG layout: $(basename "$DMG_PATH")"
