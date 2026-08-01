#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "create_macos_drag_dmg.sh only supports macOS." >&2
  exit 1
fi

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "Usage: $0 <volume-name> <source-folder-containing-app> <output.dmg> [architecture-label]" >&2
  exit 1
fi

VOLUME_NAME="$1"
SOURCE_FOLDER="$2"
OUTPUT_DMG="$3"
ARCHITECTURE_LABEL="${4:-macOS}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKGROUND_TEMPLATE="$ROOT_DIR/packaging/macos/dmg-background.svg"

if [[ ! -d "$SOURCE_FOLDER" ]]; then
  echo "Source folder not found: $SOURCE_FOLDER" >&2
  exit 1
fi

if ! command -v hdiutil >/dev/null 2>&1; then
  echo "hdiutil not found." >&2
  exit 1
fi
for required_command in diskutil osascript python3 sips strings tiffutil; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "$required_command not found; polished macOS DMG layout cannot be created." >&2
    exit 1
  fi
done
if [[ ! -f "$BACKGROUND_TEMPLATE" ]]; then
  echo "DMG background template not found: $BACKGROUND_TEMPLATE" >&2
  exit 1
fi

APP_BUNDLES=()
while IFS= read -r -d '' app_bundle; do
  APP_BUNDLES+=("$app_bundle")
done < <(find "$SOURCE_FOLDER" -maxdepth 1 -type d -name '*.app' -print0)
if [[ "${#APP_BUNDLES[@]}" -ne 1 ]]; then
  echo "Expected exactly one .app bundle in $SOURCE_FOLDER; found ${#APP_BUNDLES[@]}." >&2
  exit 1
fi

APP_NAME="$(basename "${APP_BUNDLES[0]}")"
OUTPUT_DIR="$(dirname "$OUTPUT_DMG")"
mkdir -p "$OUTPUT_DIR"

WORK_DIR="$(mktemp -d -t lizzieyzy-dmg-layout.XXXXXX)"
BUILD_VOLUME_NAME="LizzieYzy Layout $$-$RANDOM"
STAGING_DIR="$WORK_DIR/staging"
RW_DMG="$WORK_DIR/layout-rw.dmg"
MOUNT_POINT="$WORK_DIR/mount"
BACKGROUND_SVG="$WORK_DIR/install-background.svg"
BACKGROUND_1X="$WORK_DIR/install-background.png"
BACKGROUND_2X="$WORK_DIR/install-background@2x.png"
MOUNTED=0

detach_mount() {
  local attempt
  if [[ "$MOUNTED" -ne 1 ]]; then
    return 0
  fi
  for attempt in 1 2 3 4 5; do
    if hdiutil detach "$MOUNT_POINT" -quiet >/dev/null 2>&1; then
      MOUNTED=0
      return 0
    fi
    sleep "$attempt"
  done
  hdiutil detach "$MOUNT_POINT" -force -quiet >/dev/null 2>&1 || true
  MOUNTED=0
}

cleanup() {
  detach_mount
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

create_writable_dmg() {
  local attempt
  for attempt in 1 2 3; do
    rm -f "$RW_DMG"
    echo "Creating writable DMG (attempt $attempt/3)..." >&2
    if hdiutil create \
      -volname "$BUILD_VOLUME_NAME" \
      -fs HFS+ \
      -srcfolder "$STAGING_DIR" \
      -format UDRW \
      -ov \
      "$RW_DMG"; then
      return 0
    fi
    echo "Writable DMG creation attempt $attempt/3 failed; retrying..." >&2
    sleep "$attempt"
  done
  echo "Unable to create the writable DMG after 3 attempts." >&2
  exit 1
}

mkdir -p "$STAGING_DIR" "$MOUNT_POINT"
ditto "$SOURCE_FOLDER" "$STAGING_DIR"
rm -f "$STAGING_DIR/.DS_Store"
rm -rf "$STAGING_DIR/Applications"
ln -s /Applications "$STAGING_DIR/Applications"
mkdir -p "$STAGING_DIR/.background"

python3 - "$BACKGROUND_TEMPLATE" "$BACKGROUND_SVG" "$ARCHITECTURE_LABEL" <<'PY'
from pathlib import Path
import sys

template_path = Path(sys.argv[1])
output_path = Path(sys.argv[2])
architecture_label = sys.argv[3]
template = template_path.read_text(encoding="utf-8")
if "__ARCH_LABEL__" not in template:
    raise SystemExit(f"DMG background template has no architecture token: {template_path}")
output_path.write_text(
    template.replace("__ARCH_LABEL__", architecture_label),
    encoding="utf-8",
)
PY

sips -s format png "$BACKGROUND_SVG" --out "$BACKGROUND_2X" >/dev/null
sips -z 500 800 "$BACKGROUND_2X" --out "$BACKGROUND_1X" >/dev/null

BACKGROUND_1X_SIZE="$(sips -g pixelWidth -g pixelHeight "$BACKGROUND_1X" 2>/dev/null)"
BACKGROUND_2X_SIZE="$(sips -g pixelWidth -g pixelHeight "$BACKGROUND_2X" 2>/dev/null)"
if ! grep -q "pixelWidth: 800" <<<"$BACKGROUND_1X_SIZE" \
  || ! grep -q "pixelHeight: 500" <<<"$BACKGROUND_1X_SIZE"; then
  echo "Unexpected 1x DMG background dimensions." >&2
  echo "$BACKGROUND_1X_SIZE" >&2
  exit 1
fi
if ! grep -q "pixelWidth: 1600" <<<"$BACKGROUND_2X_SIZE" \
  || ! grep -q "pixelHeight: 1000" <<<"$BACKGROUND_2X_SIZE"; then
  echo "Unexpected 2x DMG background dimensions." >&2
  echo "$BACKGROUND_2X_SIZE" >&2
  exit 1
fi

tiffutil -cathidpicheck "$BACKGROUND_1X" "$BACKGROUND_2X" \
  -out "$STAGING_DIR/.background/install-background.tiff" >/dev/null
python3 - "$STAGING_DIR/.background/layout.json" "$ARCHITECTURE_LABEL" <<'PY'
from pathlib import Path
import json
import sys

output_path = Path(sys.argv[1])
architecture_label = sys.argv[2]
output_path.write_text(
    json.dumps(
        {
            "schemaVersion": 1,
            "architecture": architecture_label,
            "background": "install-background.tiff",
            "window": {"width": 800, "height": 500},
            "appPosition": {"x": 185, "y": 300},
            "applicationsPosition": {"x": 615, "y": 300},
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)
PY

create_writable_dmg

hdiutil attach "$RW_DMG" \
  -mountpoint "$MOUNT_POINT" \
  -readwrite \
  -noverify \
  -noautoopen \
  -quiet
MOUNTED=1

LAYOUT_CREATED=0
for attempt in 1 2 3; do
  if osascript >/dev/null <<OSA
tell application "Finder"
  set dmgRoot to POSIX file "$MOUNT_POINT" as alias
  set backgroundImage to POSIX file "$MOUNT_POINT/.background/install-background.tiff" as alias
  open dmgRoot
  delay 2
  set dmgWindow to container window of dmgRoot
  set current view of dmgWindow to icon view
  try
    set toolbar visible of dmgWindow to false
  end try
  try
    set statusbar visible of dmgWindow to false
  end try
  set the bounds of dmgWindow to {160, 100, 960, 620}
  set viewOptions to the icon view options of dmgWindow
  set arrangement of viewOptions to not arranged
  set icon size of viewOptions to 128
  set text size of viewOptions to 14
  set background picture of viewOptions to backgroundImage
  set position of item "$APP_NAME" of dmgRoot to {185, 300}
  set position of item "Applications" of dmgRoot to {615, 300}
  update dmgRoot without registering applications
  set toolbar visible of dmgWindow to false
  set statusbar visible of dmgWindow to false
  delay 2
  close dmgWindow
end tell
OSA
  then
    sync
    sleep 1
    DS_STORE_STRINGS="$(strings -a "$MOUNT_POINT/.DS_Store" 2>/dev/null || true)"
    if grep -q "install-background.tiff" <<<"$DS_STORE_STRINGS"; then
      LAYOUT_CREATED=1
      break
    fi
  fi
  echo "Finder layout attempt $attempt/3 did not persist the branded background; retrying..." >&2
  sleep "$attempt"
done

if [[ "$LAYOUT_CREATED" -ne 1 ]]; then
  echo "Finder layout customization failed; refusing to publish a plain DMG." >&2
  exit 1
fi
if [[ ! -s "$MOUNT_POINT/.DS_Store" ]]; then
  echo "Finder did not persist DMG layout metadata." >&2
  exit 1
fi
if [[ ! -s "$MOUNT_POINT/.background/install-background.tiff" ]]; then
  echo "DMG background is missing after Finder layout generation." >&2
  exit 1
fi

diskutil rename "$MOUNT_POINT" "$VOLUME_NAME" >/dev/null
sync
detach_mount

TMP_OUTPUT="$WORK_DIR/$(basename "$OUTPUT_DMG")"
rm -f "$OUTPUT_DMG" "$TMP_OUTPUT"
hdiutil convert "$RW_DMG" \
  -quiet \
  -format UDZO \
  -imagekey zlib-level=9 \
  -o "$TMP_OUTPUT"
mv "$TMP_OUTPUT" "$OUTPUT_DMG"

echo "Created drag-install DMG: $OUTPUT_DMG"
