#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
VERSION=$(sed -n 's#^distributionUrl=.*gradle-\([^/]*\)-bin\.zip.*#\1#p' "$PROPS" | head -n1)
: "${VERSION:?Gradle version missing from $PROPS}"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/focused-mind-bootstrapped/$VERSION"
GRADLE_BIN="$CACHE/gradle-$VERSION/bin/gradle"
if [ -x "$GRADLE_BIN" ]; then
  exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle -p "$APP_HOME" "$@"
fi
command -v curl >/dev/null 2>&1 || { echo "Gradle $VERSION is not installed and curl is unavailable." >&2; exit 1; }
mkdir -p "$CACHE"
TMP="$CACHE/gradle-$VERSION-bin.zip"
printf '%s\n' "Bootstrapping Gradle $VERSION..." >&2
curl -fL --retry 3 --connect-timeout 20 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$TMP"
command -v unzip >/dev/null 2>&1 || { echo "unzip is required to bootstrap Gradle." >&2; exit 1; }
unzip -q -o "$TMP" -d "$CACHE"
exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
