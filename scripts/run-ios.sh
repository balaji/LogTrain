#!/bin/bash
# Build sharedLogic/sharedUI + iosApp and run it in the iOS Simulator.
# Usage: run-ios.sh [language-code]
#   language-code: optional, e.g. "en" or "sv". Overrides the app's language
#   for this launch only (via -AppleLanguages/-AppleLocale), without changing
#   the simulator's system-wide language setting.
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME=/opt/homebrew/opt/openjdk
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer

SIM_NAME="iPhone 17"
APP_ID="dev.balaji.moonrose.logtrain.LogTrain"
LANG_CODE="${1:-}"

# Boot the simulator if nothing is booted yet.
if ! xcrun simctl list devices | grep -q "Booted"; then
    echo "No simulator booted — booting \"$SIM_NAME\"..."
    xcrun simctl boot "$SIM_NAME"
fi

# Open Simulator.app if it isn't running.
if ! pgrep -x "Simulator" > /dev/null; then
    open -a Simulator
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
    LAUNCH_ARGS=(-AppleLanguages "($LANG_CODE)" -AppleLocale "$LOCALE")
    # Relaunch (not just foreground) so the language override actually applies.
    xcrun simctl terminate booted "$APP_ID" 2>/dev/null || true
fi

xcrun simctl launch booted "$APP_ID" "${LAUNCH_ARGS[@]}"
