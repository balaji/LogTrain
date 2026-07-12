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

Pass `--clean` to uninstall the app (if installed) before installing,
wiping its data — useful for testing first-run behavior or fresh-install
database migrations. Combines with a language code in any order:

```bash
scripts/run-ios.sh --clean
scripts/run-ios.sh --clean sv
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
