# LogTrain

A gym workout logger that gets out of your way. Log your sets between sets —
pick the exercise, punch in weight and reps, done — and let the app keep the
long-term record: what you lifted, when, and how it's trending.

LogTrain is local-first: all data (profile, exercises, plans, routines,
workout sessions and their sets) lives in an on-device Room database. No
account, no sign-in — you create a profile on first launch and start logging.
Syncing local data to a backend (to view progress on the web) is planned as a
future premium feature.

## Where it runs

- **iPhone** and **Android** — the primary experience: logging at the gym.
- **Desktop** — reviewing history and planning routines with a keyboard.
- **Web** — coming soon; local data isn't available in the browser yet, so
  the web app currently shows a notice.

## Product principles

1. **Logging speed beats features.** Anything on the log-a-set path must work
   one-handed, in seconds, mid-workout. Features that slow that path down
   don't ship.
2. **Native feel on every platform.** An iPhone user should get a real iOS
   app — Liquid Glass chrome, system navigation transitions, swipe-back — and
   an Android user a real Material You app that follows their wallpaper and
   dark mode. Sharing code (Kotlin Multiplatform) is an implementation
   strategy, not an excuse for a lowest-common-denominator UI.
3. **Your data outlives the app.** Everything lives in a plain SQLite
   database on your device; nothing is trapped in a proprietary store or
   locked behind someone else's server.

## Status

Early. Profile creation and plans/routines work end to end against the local
database, and the native app shells are in place (iOS 26 Liquid Glass tab
navigation, Material 3 Expressive on Android). Session logging and history
are next.

## Contributing / hacking

Architecture, build commands, tests, and conventions live in
[AGENTS.md](AGENTS.md) — it's written for coding agents but it's the
engineering README too. The local data model is documented there as well.
