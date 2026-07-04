# AGENTS.md

Kotlin Multiplatform app (Android, iOS, Desktop, Web) for logging gym
workouts. Its backend is `moonrose` (Rails, sibling repo), which in
production is reachable at https://moonrose.balaji.dev. Locally it runs at
`http://localhost:3000` via `docker compose up -d moonrose` from the moonrose
repo — start that first if you need a live backend.

## Module layout

- [sharedLogic](sharedLogic/src) — platform-agnostic Kotlin: networking (Ktor),
  auth, models. `commonMain` is the bulk of it; `androidMain`/`iosMain`/`jvmMain`/`jsMain`
  hold `expect`/`actual` platform implementations. Compiles to a JS library
  (`sharedLogic`) consumed by `webApp`.
- [sharedUI](sharedUI/src) — Compose Multiplatform UI shared by Android,
  Desktop, and iOS. Not used by `webApp` (that's plain React).
- [androidApp](androidApp), [desktopApp](desktopApp), [iosApp](iosApp) —
  thin per-platform entry points wiring up `sharedLogic` + `sharedUI`.
- [webApp](webApp) — React + TypeScript, consumes the Kotlin/JS build of
  `sharedLogic` as an npm dependency.

`MoonroseConfig.baseUrl` (`sharedLogic/.../auth/LoginController.kt`) is the
single point of configuration for which backend a build talks to — defaults
to `http://localhost:3000`.

## Backend contract

Auth is a browser-based OAuth handoff, not a mobile-native SDK flow:

- `LoginController.startLogin()` opens `{baseUrl}/mobile/login?redirect_uri=...`
  in the system browser. Moonrose does Google OAuth, then redirects back to
  `redirect_uri` with a `token` query param.
- `LoginController.completeLogin(callbackUrl)` extracts that `token` and
  hands it to `AuthTokenStore` (platform-specific: Keychain/EncryptedSharedPreferences/etc.).
- Authenticated API calls send the token as a bearer token against
  `/api/v1/...` (exercises, plans, routines, work_sets, work_set_details,
  workout_sessions, workout_session_details — see moonrose's `config/routes.rb`).

If you change anything about this flow (redirect_uri handling, token param
name, deep link scheme), it must stay in sync with `mobile_auth_controller.rb`
and `sessions_controller.rb` in the moonrose repo — check there first.

## Building and running

- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :desktopApp:run` (or `:desktopApp:hotRun --auto` for hot reload)
- iOS: open [iosApp](iosApp) in Xcode and run from there
- Web:
  ```
  npm run build:shared   # builds sharedLogic's Kotlin/JS library
  npm install
  npm run start
  ```
  (`npm run build:shared` must be re-run after any `sharedLogic` change —
  webApp consumes a built artifact, not live source.)

## Tests

- Android: `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest`
- Desktop: `./gradlew :sharedUI:jvmTest :sharedLogic:jvmTest`
- Web: `./gradlew :sharedLogic:jsTest`
- iOS: `./gradlew :sharedUI:iosSimulatorArm64Test :sharedLogic:iosSimulatorArm64Test`

Run the ones relevant to what you touched; there's no single "test everything"
task because platform toolchains (Xcode, Android SDK) aren't all guaranteed
to be present.
