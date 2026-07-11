---
name: run-android
description: Build, install, and launch LogTrain in the Android Emulator via scripts/run-android.sh. Use when asked to run, launch, or test the Android app, or to verify a change works in the emulator.
---

# Running LogTrain on Android

Run from the repo root:

```bash
scripts/run-android.sh
```

This installs the debug build (`:androidApp:installDebug`) and launches on
the "Medium_Phone" AVD, booting it first if no emulator/device is attached
and waiting for a full boot (not just the ADB link). It sets `JAVA_HOME`
and the Android SDK `PATH` itself — no need to export them separately.

Pass a language code to override the app's per-app locale for this run only
(via `cmd locale set-app-locales`), without changing the emulator's
system-wide language setting — useful for eyeballing localization:

```bash
scripts/run-android.sh sv   # Swedish
scripts/run-android.sh en   # English
```

To then observe what changed, see the `verify` skill (app DB inspection,
screenshots).
