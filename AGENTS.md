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

## UI architecture

Goal: native look-and-feel per platform, following JetBrains' hybrid pattern
(https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html).

- **iOS (26.0+ only, no fallback)**: SwiftUI owns all navigation chrome —
  `TabView` + per-tab `NavigationStack` in `iosApp/iosApp/ContentView.swift`
  get real Liquid Glass styling. Compose renders screen *content* only, hosted
  per-screen via the entry points in `sharedUI/src/iosMain/.../MainViewController.kt`
  (`AuthViewController`, `MainViewController(route:onNavigate:)`,
  `ScreenViewController(route:onNavigate:onGoBack:)`). Navigation intents flow
  Kotlin → Swift through those callbacks; Swift coordinators push/pop the stack.
  The shared `App()` composable is **not** used on iOS.
- **Android/Desktop**: pure Compose. `App()` (`sharedUI/.../App.kt`) renders
  auth gate → `NavigationBar` tab scaffold, with a hand-rolled `NavStack`
  (no navigation library — deliberate; SwiftUI owns the iOS stack, so a shared
  nav library would be dead weight). Android theming is `MaterialExpressiveTheme`
  + Material You dynamic color (`PlatformTheme.android.kt`); predictive back is
  enabled in the manifest.

Adding a shared screen means: define its route in `sharedUI/.../navigation/Routes.kt`
(routes carry `title`/`subtitle` so SwiftUI can draw nav bars without calling
Kotlin), branch it in `navigation/ScreenContent.kt`, and in the screen itself
gate any top bar / back button on `LocalUseNativeNavigation` (true when SwiftUI
draws the chrome) and use `WindowInsets.safeDrawing` padding *inside* scrollables
so content flows behind translucent system bars. A new tab additionally needs a
`Tab` + coordinator in `ContentView.swift` and a `TabSpec` in `App.kt`.

### Don't let Material patterns leak onto iOS

Compose's defaults are Material (Android) by design, so any control that isn't
explicitly branched will render Android-style on iOS too. Before adding a new
screen or interaction, check whether it falls into one of these categories —
each has an established fix, don't reinvent it:

- **Primary/floating actions** (a screen's main "+"/create button): never use
  `FloatingActionButton` when `LocalUseNativeNavigation.current` is true — iOS
  puts this in the native nav bar's trailing `ToolbarItem`, not a floating
  circle. Since SwiftUI owns that toolbar and Compose only owns content, the
  tap has to cross the boundary: add a small listener-object bridge (see
  `PlansCreateRequestNotifier` in
  `sharedUI/.../plans/`) that the Compose screen listens to via
  `DisposableEffect`, and call `.shared.requestCreate()` from a `.toolbar` in
  `ContentView.swift`.
- **Alerts and confirmations** (rename/delete/error dialogs): never render a
  bare Material `AlertDialog` unconditionally — on iOS it should present a
  native `UIAlertController` instead. Use `LocalNativeAlertPresenter`
  (`sharedUI/.../navigation/NativeAlerts.kt`, iOS impl in `NativeAlerts.ios.kt`)
  from the dialog composable: if `LocalUseNativeNavigation.current` and the
  presenter is non-null, call it from a `LaunchedEffect(Unit)` and return
  early; otherwise fall back to the Material `AlertDialog`. See
  `PlanDialogs.kt` for the pattern.
- **Row/contextual actions** (rename/delete on a list row): never use a bare
  `DropdownMenu` unconditionally — on iOS this should be a native action
  sheet (`LocalNativeAlertPresenter.presentActionSheet(...)`) or a gesture
  that reads natively on both platforms. The current convention is
  swipe-to-reveal action buttons via `SwipeRevealRow`
  (`sharedUI/.../screens/SwipeRevealRow.kt`; see `PlanRow`/`RoutineRow` in
  `PlansScreen.kt`).
- **Icons**: `material-icons-core` is a small, frozen set (see version gotcha
  below) — don't reach for an "iOS-shaped" icon (e.g. a horizontal-ellipsis
  glyph) that isn't in that set just to look native; the interaction pattern
  (toolbar button, native alert, action sheet) is what actually matters, the
  glyph is secondary.

When in doubt: if a control's only job is to look right (a spinner, a list
row), it's usually fine shared as-is. If a control is "chrome-adjacent" —
something Android and iOS have genuinely different conventions for (primary
actions, alerts/action sheets, menus, swipe gestures, tab/nav bars) — assume it
needs platform branching from the start rather than discovering the gap later.

Version gotchas: `material3` must stay on an alpha (`libs.versions.toml`) —
Expressive APIs are stripped from stable releases. The material-icons artifacts
are frozen at 1.7.3 and no longer come in transitively; only
`material-icons-core` icons are available.

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
- iOS: open [iosApp](iosApp) in Xcode and run from there (requires Xcode 26+;
  deployment target is iOS 26.0). Headless:
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' build`
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

## Faking a signed-in state (manual/agent testing)

The signed-in UI (tab shell) is behind the auth gate. To reach it without a
real Google OAuth round-trip:

- iOS simulator (token store is plain NSUserDefaults):
  `xcrun simctl spawn <udid> defaults write dev.balaji.moonrose.logtrain.LogTrain "dev.balaji.moonrose.logtrain.auth.token" test-token`
  then (re)launch the app.
- Android (token store is encrypted, so inject via the deep-link flow instead):
  `adb shell am start -a android.intent.action.VIEW -d "logtrain://auth?token=test-token" dev.balaji.moonrose.logtrain`

Backend API calls will 401 with a fake token, but screens that don't hit the
API render fine.
