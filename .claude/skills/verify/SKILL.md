---
name: verify
description: How to run LogTrain and observe changes at runtime (desktop Compose app, iOS simulator, seeded data)
---

# Verifying LogTrain changes at runtime

If JDK is not found, check if it is installed via homebrew; export `JAVA_HOME=$BREW_HOME/opt/openjdk` for every Gradle call.

## Desktop (Compose, fastest shared-UI surface)

```bash
JAVA_HOME=$BREW_HOME/opt/openjdk ./gradlew :desktopApp:run
```

- Opens a macOS window (main class `dev.balaji.moonrose.logtrain.MainKt`). Runs until the window is closed; launch it in the background.
- Its Room DB lives at `~/.logtrain/logtrain.db` (JVM `databaseBuilder` uses `user.home`). That is the developer's own data — inspect with sqlite3, don't reset it. Note: an old DB may predate `ExerciseSeedCallback` and have an empty `exercises` table, which leaves most screens empty.
- The Profile screen has a developer test-data generator (`TestDataRepository.generate(days)`) and a matching delete; it's the sanctioned way to get workout history for the Progress screen.
- `screencapture -x <file>.png` works from this environment for capturing the window.

## iOS simulator

See the `ios-build-validation` memory / previous sessions: build with
`JAVA_HOME=... DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild` (JAVA_HOME on the command line or the Kotlin framework phase fails), install/launch with `xcrun simctl install/launch booted dev.balaji.moonrose.logtrain.LogTrain`. The iPhone 17 simulator (iOS 26.5) usually already has the app with rich generated test data.

- App DB: `$(xcrun simctl get_app_container booted dev.balaji.moonrose.logtrain.LogTrain data)/Documents/logtrain.db` — query it with sqlite3 to cross-check numbers the UI shows against ground truth.
- Screenshot: `xcrun simctl io booted screenshot <file>.png`. simctl cannot tap UI; the app opens on the Exercises tab.
- Check the installed bundle's mtime (`stat -f "%Sm" <container>/LogTrain`) before trusting what's on screen — it only reflects code as of its last build.
