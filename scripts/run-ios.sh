#!/bin/bash
# Build sharedLogic/sharedUI + iosApp and run it in the iOS Simulator.
# Usage: run-ios.sh [--clean] [--seed [days]] [language-code]
#   --clean: uninstall the app (if present) before installing, wiping its data.
#   --seed [days]: uninstall first (implies --clean), then on launch create a
#     default profile and generate that many days of test workout history
#     (default 60). Debug-only hook.
#   language-code: optional, e.g. "en" or "sv". Overrides the app's language
#   for this launch only (via -AppleLanguages/-AppleLocale), without changing
#   the simulator's system-wide language setting.
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME=/opt/homebrew/opt/openjdk
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer

SIM_NAME="iPhone 17"
APP_ID="dev.balaji.moonrose.logtrain.LogTrain"
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

# Boot the simulator if nothing is booted yet.
if ! xcrun simctl list devices | grep -q "Booted"; then
    echo "No simulator booted — booting \"$SIM_NAME\"..."
    xcrun simctl boot "$SIM_NAME"
fi

# Open Simulator.app if it isn't running.
if ! pgrep -x "Simulator" > /dev/null; then
    open -a Simulator
fi

if [[ "$CLEAN" = "1" ]]; then
    if xcrun simctl get_app_container booted "$APP_ID" > /dev/null 2>&1; then
        echo "Uninstalling $APP_ID..."
        xcrun simctl uninstall booted "$APP_ID"
    else
        echo "$APP_ID not installed — nothing to uninstall."
    fi
fi

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
    -sdk iphonesimulator -derivedDataPath iosApp/build \
    CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO

xcrun simctl install booted iosApp/build/Build/Products/Debug-iphonesimulator/LogTrain.app

LAUNCH_ARGS=()
if [[ -n "$LANG_CODE" ]]; then
    case "$LANG_CODE" in
        en) LOCALE="en_US" ;;
        sv) LOCALE="sv_SE" ;;
        *) LOCALE="$LANG_CODE" ;;
    esac
    echo "Launching with language override: $LANG_CODE ($LOCALE)"
    LAUNCH_ARGS+=(-AppleLanguages "($LANG_CODE)" -AppleLocale "$LOCALE")
fi
if [[ "$SEED_DAYS" -gt 0 ]]; then
    echo "Launching with test-data seed: $SEED_DAYS days"
    LAUNCH_ARGS+=(-seedTestData "$SEED_DAYS")
fi
if [[ ${#LAUNCH_ARGS[@]} -gt 0 ]]; then
    # Relaunch (not just foreground) so the launch arguments actually apply.
    xcrun simctl terminate booted "$APP_ID" 2>/dev/null || true
fi

# The guarded expansion keeps bash 3.2's set -u happy when the array is empty.
xcrun simctl launch booted "$APP_ID" ${LAUNCH_ARGS[@]+"${LAUNCH_ARGS[@]}"}
