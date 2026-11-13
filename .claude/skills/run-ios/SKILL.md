---
name: run-ios
description: Build, install, and launch LogTrain in the iOS Simulator via scripts/run-ios.py. Use when asked to run, launch, or test the iOS app, or to verify a change works in the simulator.
---

# Running LogTrain on iOS

Run from the repo root:

```bash
scripts/run-ios.py
```

This builds `sharedLogic`/`sharedUI` + `iosApp`, installs, and launches on a
booted "iPhone 17" simulator (booting it and opening Simulator.app if
neither is already running). It sets `JAVA_HOME`/`DEVELOPER_DIR` itself —
no need to export them separately.

Pass a language code to launch with that language for this run only (via
`-AppleLanguages`/`-AppleLocale`), without changing the simulator's
system-wide setting — useful for eyeballing localization:

```bash
scripts/run-ios.py sv   # Swedish
scripts/run-ios.py en   # English
```

Pass `--clean` to uninstall the app (if installed) before installing,
wiping its data — useful for testing first-run behavior or fresh-install
database migrations. Combines with a language code in any order:

```bash
scripts/run-ios.py --clean
scripts/run-ios.py --clean sv
```

Pass `--seed [days]` to land a fresh install on a populated app instead of
the profile-setup screen. It implies `--clean` (the hook only fires when no
profile exists, so the script starts from an empty install): on launch, a
debug-only hook (`DevSeed` in sharedLogic, triggered via the
`-seedTestData` launch argument) creates a default profile ("Test User",
metric) and generates that many days of workout history (default 60) using
the same generator as the in-app Developer link. This is the usual way to
start a test session:

```bash
scripts/run-ios.py --seed        # fresh install + 60 days of data
scripts/run-ios.py --seed 120 sv # 120 days, Swedish
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
