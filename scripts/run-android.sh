#!/bin/bash
# Build sharedLogic/sharedUI + androidApp and run it in the Android Emulator.
# Usage: run-android.sh [--clean] [language-code]
#   --clean: uninstall the app (if present) before installing, wiping its data.
#   language-code: optional, e.g. "en" or "sv". Overrides the app's per-app
#   language via `cmd locale set-app-locales`, without changing the
#   emulator's system-wide language setting.
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME=/opt/homebrew/opt/openjdk
ANDROID_SDK="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$PATH"

AVD_NAME="Medium_Phone"
APP_ID="dev.balaji.moonrose.logtrain"
CLEAN=0
LANG_CODE=""
for arg in "$@"; do
    case "$arg" in
        --clean) CLEAN=1 ;;
        *) LANG_CODE="$arg" ;;
    esac
done

# Boot an emulator if no device/emulator is currently attached.
if ! adb devices | grep -q "device$"; then
    echo "No emulator running — booting \"$AVD_NAME\"..."
    nohup emulator -avd "$AVD_NAME" > /tmp/logtrain-emulator.log 2>&1 &
    adb wait-for-device
    # Wait for full boot, not just the ADB link.
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 2
    done
fi

if [[ "$CLEAN" = "1" ]]; then
    if adb shell pm list packages | grep -q "^package:$APP_ID$"; then
        echo "Uninstalling $APP_ID..."
        adb uninstall "$APP_ID"
    else
        echo "$APP_ID not installed — nothing to uninstall."
    fi
fi

./gradlew :androidApp:installDebug

adb shell am force-stop "$APP_ID"

if [[ -n "$LANG_CODE" ]]; then
    case "$LANG_CODE" in
        en) LOCALE="en-US" ;;
        sv) LOCALE="sv-SE" ;;
        *) LOCALE="$LANG_CODE" ;;
    esac
    echo "Launching with language override: $LANG_CODE ($LOCALE)"
    adb shell cmd locale set-app-locales "$APP_ID" --user current --locales "$LOCALE"
fi

adb shell am start -n "$APP_ID/.MainActivity"
