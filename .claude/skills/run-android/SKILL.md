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

Pass `--clean` to uninstall the app (if installed) before installing,
wiping its data — useful for testing first-run behavior or fresh-install
database migrations. Combines with a language code in any order:

```bash
scripts/run-android.sh --clean
scripts/run-android.sh --clean sv
```

If there are uncommitted schema changes (anything modified or untracked
under `sharedLogic/schemas/`), pass `--clean` on the **first** run of the
test session: a previous session may have installed a build with an
earlier draft of the in-flux migration, and re-running on top of that
database produces misleading migration failures. Subsequent runs in the
same session can drop `--clean` — upgrading over the now-consistent
install is then exactly what you want to exercise.

To then observe what changed, see the `verify` skill (app DB inspection,
screenshots).
