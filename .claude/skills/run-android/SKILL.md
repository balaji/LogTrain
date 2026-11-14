---
name: run-android
description: Build, install, and launch Train Free in the Android Emulator via scripts/run-android.py. Use when asked to run, launch, or test the Android app, or to verify a change works in the emulator.
---

# Running Train Free on Android

Run from the repo root:

```bash
scripts/run-android.py
```

This installs the debug build (`:androidApp:installDebug`) and launches on
the "Medium_Phone" AVD, booting it first if no emulator/device is attached
and waiting for a full boot (not just the ADB link). It sets `JAVA_HOME`
and the Android SDK `PATH` itself — no need to export them separately.

Pass a language code to override the app's per-app locale for this run only
(via `cmd locale set-app-locales`), without changing the emulator's
system-wide language setting — useful for eyeballing localization:

```bash
scripts/run-android.py sv   # Swedish
scripts/run-android.py en   # English
```

Pass `--clean` to uninstall the app (if installed) before installing,
wiping its data — useful for testing first-run behavior or fresh-install
database migrations. Combines with a language code in any order:

```bash
scripts/run-android.py --clean
scripts/run-android.py --clean sv
```

Pass `--seed [days]` to land a fresh install on a populated app instead of
the profile-setup screen. It implies `--clean` (the hook only fires when no
profile exists, so the script starts from an empty install): on launch, a
debug-only hook (`DevSeed` in sharedLogic, triggered via the
`seedTestData` intent extra) creates a default profile ("Test User",
metric) and generates that many days of workout history (default 60) using
the same generator as the in-app Developer link. This is the usual way to
start a test session:

```bash
scripts/run-android.py --seed        # fresh install + 60 days of data
scripts/run-android.py --seed 120 sv # 120 days, Swedish
```

Seeding is asynchronous — give the app a second or two after launch before
screenshotting or querying the DB.

If there are uncommitted schema changes (anything modified or untracked
under `sharedLogic/schemas/`), pass `--clean` on the **first** run of the
test session: a previous session may have installed a build with an
earlier draft of the in-flux migration, and re-running on top of that
database produces misleading migration failures. Subsequent runs in the
same session can drop `--clean` — upgrading over the now-consistent
install is then exactly what you want to exercise.

To then observe what changed, see the `verify` skill (app DB inspection,
screenshots).
