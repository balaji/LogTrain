---
name: verify
description: How to run LogTrain and observe changes at runtime (iOS simulator, seeded data)
---

# Verifying LogTrain changes at runtime

If JDK is not found, check if it is installed via homebrew; export `JAVA_HOME=$BREW_HOME/opt/openjdk` for every Gradle call.

## iOS simulator

See the `ios-build-validation` memory / previous sessions: build with
`JAVA_HOME=... DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild` (JAVA_HOME on the command line or the Kotlin framework phase fails), install/launch with `xcrun simctl install/launch booted dev.balaji.moonrose.logtrain.LogTrain`. The iPhone 17 simulator (iOS 26.5) usually already has the app with rich generated test data.

- App DB: `$(xcrun simctl get_app_container booted dev.balaji.moonrose.logtrain.LogTrain data)/Documents/logtrain.db` — query it with sqlite3 to cross-check numbers the UI shows against ground truth.
- Screenshot: `xcrun simctl io booted screenshot <file>.png`. simctl cannot tap UI; the app opens on the Exercises tab.
- Check the installed bundle's mtime (`stat -f "%Sm" <container>/LogTrain`) before trusting what's on screen — it only reflects code as of its last build.
