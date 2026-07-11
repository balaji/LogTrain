#!/bin/bash
# Build sharedLogic/sharedUI + androidApp and run it in the Android Emulator.
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME=/opt/homebrew/opt/openjdk
ANDROID_SDK="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$PATH"

AVD_NAME="Medium_Phone"
APP_ID="dev.balaji.moonrose.logtrain"

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

./gradlew :androidApp:installDebug

adb shell am start -n "$APP_ID/.MainActivity"
