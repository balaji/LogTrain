---
name: run-ios
description: Build, install, and launch LogTrain in the iOS Simulator via scripts/run-ios.sh. Use when asked to run, launch, or test the iOS app, or to verify a change works in the simulator.
---

# Running LogTrain on iOS

Run from the repo root:

```bash
scripts/run-ios.sh
```

This builds `sharedLogic`/`sharedUI` + `iosApp`, installs, and launches on a
booted "iPhone 17" simulator (booting it and opening Simulator.app if
neither is already running). It sets `JAVA_HOME`/`DEVELOPER_DIR` itself —
no need to export them separately.

Pass a language code to launch with that language for this run only (via
`-AppleLanguages`/`-AppleLocale`), without changing the simulator's
system-wide setting — useful for eyeballing localization:

```bash
scripts/run-ios.sh sv   # Swedish
scripts/run-ios.sh en   # English
```

To then observe what changed, see the `verify` skill (app DB inspection,
screenshots).
