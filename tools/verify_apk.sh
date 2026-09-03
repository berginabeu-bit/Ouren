#!/bin/sh
set -eu
APK="${1:?APK path required}"
EXPECTED="${2:?expected applicationId required}"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
APKANALYZER=$(find "${ANDROID_HOME:-$ANDROID_SDK_ROOT}/cmdline-tools" -type f -path '*/bin/apkanalyzer' 2>/dev/null | head -n1 || true)
if [ -z "$APKANALYZER" ]; then
  AAPT=$(find "${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools" -type f -name aapt2 2>/dev/null | sort -V | tail -n1 || true)
  [ -n "$AAPT" ] || { echo "No apkanalyzer/aapt2 available" >&2; exit 1; }
  OUT=$($AAPT dump packagename "$APK")
else
  OUT=$($APKANALYZER manifest application-id "$APK")
fi
[ "$OUT" = "$EXPECTED" ] || { echo "Unexpected applicationId: $OUT" >&2; exit 1; }
echo "APK verified: $EXPECTED"
