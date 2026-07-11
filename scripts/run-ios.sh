#!/bin/bash
# Build sharedLogic/sharedUI + iosApp and run it in the iOS Simulator.
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME=/opt/homebrew/opt/openjdk
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer

SIM_NAME="iPhone 17"
APP_ID="dev.balaji.moonrose.logtrain.LogTrain"

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
xcrun simctl launch booted "$APP_ID"
