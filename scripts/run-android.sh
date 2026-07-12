#!/bin/bash
# Build sharedLogic/sharedUI + androidApp and run it in the Android Emulator.
# Usage: run-android.sh [--clean] [--seed [days]] [language-code]
#   --clean: uninstall the app (if present) before installing, wiping its data.
#   --seed [days]: uninstall first (implies --clean), then on launch create a
#     default profile and generate that many days of test workout history
#     (default 60). Debug-only hook.
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
SEED_DAYS=0
LANG_CODE=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean) CLEAN=1 ;;
        --seed)
            SEED_DAYS=60
            if [[ "${2:-}" =~ ^[0-9]+$ ]]; then
                SEED_DAYS="$2"
                shift
            fi
            ;;
        *) LANG_CODE="$1" ;;
    esac
    shift
done
# Seeding only fires on an install with no profile, so start from one.
if [[ "$SEED_DAYS" -gt 0 ]]; then
    CLEAN=1
fi

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

START_ARGS=()
if [[ "$SEED_DAYS" -gt 0 ]]; then
    echo "Launching with test-data seed: $SEED_DAYS days"
    START_ARGS=(--ei seedTestData "$SEED_DAYS")
fi

# The guarded expansion keeps bash 3.2's set -u happy when the array is empty.
adb shell am start -n "$APP_ID/.MainActivity" ${START_ARGS[@]+"${START_ARGS[@]}"}
