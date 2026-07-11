# AGENTS.md

Kotlin Multiplatform app (Android, iOS) for logging gym
workouts. It is local-first: all data lives in an on-device Room (KMP)
database; there is no backend and no sign-in. The local schema mirrors the
old `moonrose` Rails backend (sibling repo) so a future premium sync feature
can map rows 1:1.

## Module layout

- [sharedLogic](sharedLogic/src) — platform-agnostic Kotlin: domain models
  (`commonMain/.../model/`) and the Room database layer (`dbMain/.../db/`).
  `dbMain` is an intermediate source set shared by android + ios. Platform
  `dbMain` actuals (database file location) live in `androidMain`/`iosMain`.
- [sharedUI](sharedUI/src) — Compose Multiplatform UI shared by Android
  and iOS. Its targets are a subset of `dbMain`'s, so its `commonMain` can
  use the db layer directly (screens consume repositories/Flows, never Room
  types).
- [androidApp](androidApp), [iosApp](iosApp) — thin per-platform entry
  points wiring up `sharedLogic` + `sharedUI`.

## UI architecture

**Policy: every iOS page is native SwiftUI. Every Android page is native
Compose with Material Design.** No screen should render Compose Material
widgets on iOS. Shared Kotlin code's job is domain models, the Room DB layer,
and plain `StateFlow`-based form/state holders in `sharedLogic`'s `dbMain`
source set (see `ProfileFormState.kt`, `ExerciseFormState.kt`) that both a
Compose screen (Android) and a native SwiftUI view (iOS) can bind to
— never shared Compose UI as the iOS rendering path.

This supersedes the original hybrid pattern (JetBrains' "Compose renders
content, SwiftUI owns chrome" —
https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html), which is
**legacy** and being migrated off screen-by-screen, oldest offender first.
Profile was the pilot migration (see "Migrating a screen to native SwiftUI"
below); Plans/Workouts/Exercises are still Compose-hosted on iOS and are next
in line. Do not build *new* iOS screens against the legacy hybrid pattern —
build them native from the start, extracting shared state into `dbMain` as
needed.

### Legacy Compose-hosted screens (not yet migrated)

Until a screen is migrated, it still follows the old hybrid pattern:

- **iOS (26.0+ only, no fallback)**: SwiftUI owns all navigation chrome —
  `TabView` + per-tab `NavigationStack` in `iosApp/iosApp/ContentView.swift`
  get real Liquid Glass styling. Compose renders screen *content* only, hosted
  per-screen via the entry points in `sharedUI/src/iosMain/.../MainViewController.kt`
  (`ProfileSetupViewController`, `MainViewController(route:onNavigate:)`,
  `ScreenViewController(route:onNavigate:onGoBack:)`). Navigation intents flow
  Kotlin → Swift through those callbacks; Swift coordinators push/pop the stack.
  The shared `App()` composable is **not** used on iOS.
- **Android**: pure Compose. `App()` (`sharedUI/.../App.kt`) renders
  profile gate → `NavigationBar` tab scaffold, with a hand-rolled `NavStack`
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

### Migrating a screen to native SwiftUI

Profile was the pilot (see `iosApp/iosApp/ProfileView.swift`,
`ProfileFormView.swift`, `ProfileSetupView.swift`); follow the same pattern
for every other screen:

1. Extract the screen's state into a plain `StateFlow`-based class in
   `sharedLogic/src/dbMain/.../db/` (e.g. `ProfileFormState.kt`,
   `ExerciseFormState.kt`) — a `MutableStateFlow<UiState>` plus action methods
   and a `save(...)`. No Compose types; this is what both the Compose form
   and the native SwiftUI view bind to.
2. SKIE (`co.touchlab.skie`, applied in `sharedUI/build.gradle.kts`) exposes
   that `StateFlow` to Swift as a `SkieSwiftStateFlow`, which conforms to
   `AsyncSequence` — bind with `for await state in formState.uiState { ... }`.
3. Write the SwiftUI view(s) in `iosApp/iosApp/`, using native components
   (`Form`, `List`, `Picker`, `NavigationStack` toolbar items, native
   alerts/action sheets) — no Compose hosting, no `UIViewControllerRepresentable`
   for this screen.
4. Wire it into `ContentView.swift`'s tab/`NavigationStack` coordinators in
   place of the old `TabRootComposeView`/`DetailComposeView` route dispatch
   for that route.
5. The Compose version of the screen (still used by Android) keeps
   binding to the same shared state class — don't fork the state/business
   logic, only the view layer forks per platform.

## Local database

Room KMP (`androidx.room` + bundled SQLite driver, KSP codegen) in
`sharedLogic/src/dbMain/.../db/`:

- **Entities** (`Entities.kt`): `profile` (single row, id = 1: name, age,
  gender, weight, height), `exercises` (reference catalog, seeded on first
  create by `ExerciseSeed.kt`), `plans`, `routines`, `work_sets`,
  `work_set_details`, `workout_sessions`, `workout_session_details`.
  Snake_case table/column names mirror moonrose's schema for future sync
  parity; timestamps are epoch millis. Containment FKs cascade (plan →
  routines), history FKs null out (session → routine).
- **Repositories** (`ProfileRepository`, `PlanRepository`): the only API the
  UI layer uses — Flow-based reads of domain models
  (`commonMain/.../model/Models.kt`), suspend writes. `ProfileStore` is a
  callback bridge for the SwiftUI shell (it can't consume Flows).
- **Database file**: `AppDb.db` lazy singleton; file lives at
  `getDatabasePath("logtrain.db")` (Android, context via `DbContextHolder`,
  set in `MainActivity`) or the app documents dir (iOS).
- **Schema JSON** is exported to `sharedLogic/schemas/` (committed). Bump the
  `@Database` version and write a migration for any schema change.

## Building and running

- Android: `./gradlew :androidApp:assembleDebug`
- iOS: open [iosApp](iosApp) in Xcode and run from there (requires Xcode 26+;
  deployment target is iOS 26.0). Headless:
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' build`

## Tests

- Android: `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest`
- iOS: `./gradlew :sharedUI:iosSimulatorArm64Test :sharedLogic:iosSimulatorArm64Test`

Run the ones relevant to what you touched; there's no single "test everything"
task because platform toolchains (Xcode, Android SDK) aren't all guaranteed
to be present.

## Resetting app state (manual/agent testing)

The tab shell is behind the profile gate; creating a profile through the
first-run form is the fast path. To get back to a fresh install:

- Android: `adb shell pm clear dev.balaji.moonrose.logtrain`
- iOS simulator: delete the app (the database lives in the app's Documents
  directory).
