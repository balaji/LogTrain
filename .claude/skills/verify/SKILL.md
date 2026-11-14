---
name: verify
description: How to run Train Free and observe changes at runtime (iOS simulator, Android emulator, seeded data)
---

# Verifying Train Free changes at runtime

## iOS simulator

Use the `run-ios` skill to build, install, and launch (`scripts/run-ios.py`).
The iPhone 17 simulator (iOS 26.5) usually already has the app installed
with rich generated test data.

- App DB: `$(xcrun simctl get_app_container booted dev.balaji.moonrose.trainfree.trainfree data)/Documents/trainfree.db` — query it with sqlite3 to cross-check numbers the UI shows against ground truth.
- Screenshot: `xcrun simctl io booted screenshot <file>.png`. simctl cannot tap UI; the app opens on the Exercises tab.
- Check the installed bundle's mtime (`stat -f "%Sm" <container>/trainfree`) before trusting what's on screen — it only reflects code as of its last build.

## Android emulator

Use the `run-android` skill to build, install, and launch
(`scripts/run-android.py`) on the "Medium_Phone" AVD.

- App DB: pull it with `adb exec-out run-as dev.balaji.moonrose.trainfree cat databases/trainfree.db > /tmp/trainfree.db` (app must be debuggable), then query with sqlite3. Pull `trainfree.db-wal` and `trainfree.db-shm` alongside it into the same directory — the app runs in WAL mode, so recent (or on a fresh install, all) writes live in the WAL and the main file alone can be missing whole tables.
- Screenshot: `adb exec-out screencap -p > <file>.png`. adb cannot tap UI reliably from this harness; treat it the same as the simulator — inspect state via DB/screenshot rather than driving it.
