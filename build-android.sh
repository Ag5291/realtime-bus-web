#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_SDK_ROOT="$ROOT_DIR/.android-tools/android-sdk"
JAVA_HOME="$ROOT_DIR/.android-tools/jdks/temurin-17"
GRADLE_BIN="$ROOT_DIR/.android-tools/gradle-8.7/bin/gradle"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Missing local JDK 17 at: $JAVA_HOME" >&2
  exit 1
fi

if [[ ! -x "$GRADLE_BIN" ]]; then
  echo "Missing local Gradle at: $GRADLE_BIN" >&2
  exit 1
fi

if [[ ! -d "$ANDROID_SDK_ROOT/platforms/android-34" ]]; then
  echo "Missing Android SDK platform android-34 at: $ANDROID_SDK_ROOT" >&2
  exit 1
fi

export ANDROID_SDK_ROOT
export JAVA_HOME

"$GRADLE_BIN" -p "$ROOT_DIR/android" --no-daemon --console=plain assembleRelease

echo
echo "APK:"
echo "$ROOT_DIR/android/app/build/outputs/apk/release/app-release.apk"
