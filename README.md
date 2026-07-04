# LogTrain

A gym workout logger that gets out of your way. Log your sets between sets —
pick the exercise, punch in weight and reps, done — and let the app keep the
long-term record: what you lifted, when, and how it's trending.

LogTrain is the client side of a two-repo project: it talks to
[moonrose](https://github.com/balaji/moonrose), a Rails backend that
owns the data model (exercises, plans, routines, workout sessions and their
sets) and handles Google sign-in. One account, every device.

## Where it runs

- **iPhone** and **Android** — the primary experience: logging at the gym.
- **Desktop** — reviewing history and planning routines with a keyboard.
- **Web** — the same, from any browser.

## Product principles

1. **Logging speed beats features.** Anything on the log-a-set path must work
   one-handed, in seconds, mid-workout. Features that slow that path down
   don't ship.
2. **Native feel on every platform.** An iPhone user should get a real iOS
   app — Liquid Glass chrome, system navigation transitions, swipe-back — and
   an Android user a real Material You app that follows their wallpaper and
   dark mode. Sharing code (Kotlin Multiplatform) is an implementation
   strategy, not an excuse for a lowest-common-denominator UI.
3. **Your data outlives the app.** Everything lives behind a plain JSON API
   in moonrose; nothing is trapped in a proprietary store.

## Status

Early. Sign-in works end to end, and the native app shells are in place
(iOS 26 Liquid Glass tab navigation, Material 3 Expressive on Android).
The actual workout screens are placeholders — plans, routines, session
logging, and history are next.

## Contributing / hacking

Architecture, build commands, tests, and conventions live in
[AGENTS.md](AGENTS.md) — it's written for coding agents but it's the
engineering README too. The backend contract is documented there as well.
