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
- [sharedUI](sharedUI/src) — Compose UI, used only on Android (screens
  consume repositories/Flows, never Room types directly). Its iOS target
  exists because its Gradle module is the umbrella that packages the iOS
  framework (`baseName = "SharedUI"`, see `sharedUI/build.gradle.kts`) — iOS
  `import SharedUI` pulls in `sharedLogic`'s state/repositories via that
  framework, not any Compose UI.
- [androidApp](androidApp), [iosApp](iosApp) — thin per-platform entry
  points wiring up `sharedLogic` + `sharedUI`.

## UI architecture

Every iOS page is native SwiftUI. Every Android page is native Compose with
Material Design. Shared Kotlin code's job is domain models, the Room DB
layer, and plain `StateFlow`-based form/state holders in `sharedLogic`'s
`dbMain` source set (see `ProfileFormState.kt`, `ExerciseFormState.kt`) that
both a Compose screen (Android) and a native SwiftUI view (iOS) bind to.
Compose UI (`sharedUI`) is Android-only: `App()` (`sharedUI/.../App.kt`)
renders the profile gate and `NavigationBar` tab scaffold with a hand-rolled
`NavStack`. iOS's `ContentView.swift` builds its own `TabView` +
per-tab `NavigationStack` and binds directly to `sharedLogic` state —
it never hosts Compose.

Building a new screen:

1. Extract its state into a plain `StateFlow`-based class in
   `sharedLogic/src/dbMain/.../db/` (a `MutableStateFlow<UiState>` plus
   action methods and a `save(...)`) — no Compose types; this is what both
   the Compose screen and the native SwiftUI view bind to.
2. SKIE (`co.touchlab.skie`, applied in `sharedUI/build.gradle.kts`) exposes
   that `StateFlow` to Swift as a `SkieSwiftStateFlow`, which conforms to
   `AsyncSequence` — bind with `for await state in formState.uiState { ... }`.
3. Write the SwiftUI view(s) in `iosApp/iosApp/` using native components
   (`Form`, `List`, `Picker`, `NavigationStack` toolbar items, native
   alerts/action sheets) and wire them into `ContentView.swift`'s
   tab/`NavigationStack` coordinators.
4. Write the Compose screen in `sharedUI/.../screens/` binding to the same
   shared state class for Android — only the view layer forks per platform.

Version gotchas: `material3` must stay on an alpha (`libs.versions.toml`) —
Expressive APIs are stripped from stable releases. The material-icons
artifacts are frozen at 1.7.3 and no longer come in transitively; only
`material-icons-core` icons are available.

## Local database

Room KMP (`androidx.room` + bundled SQLite driver, KSP codegen) in
`sharedLogic/src/dbMain/.../db/`:

- **Entities** (`Entities.kt`): `profile` (single row, id = 1), `user_settings`
  (key/value, e.g. week start, appearance, weight unit), `exercises`
  (reference catalog, seeded on first create by `ExerciseSeed.kt`),
  `programs`, `routines` (FK'd to a program), `work_sets`, `work_set_details`,
  `workout_sessions`, `workout_session_details`, `body_metrics`, and
  `ai_insight_cache` (cached LLM narration for insight cards). Snake_case
  table/column names mirror moonrose's schema for future sync parity;
  timestamps are epoch millis. Containment FKs cascade (program → routines),
  history FKs null out (session → routine).
- **Repositories**: one per feature area (`ProfileRepository`,
  `ProgramRepository`, `ExerciseRepository`, `CalendarRepository`,
  `WorkoutSessionRepository`, `BodyMetricsRepository`,
  `UserSettingsRepository`, `InsightsRepository`, `TestDataRepository`) —
  the only API the UI layer uses: Flow-based reads of domain models
  (`commonMain/.../model/Models.kt`), suspend writes. `ProfileStore` is a
  callback bridge for the SwiftUI shell (it can't consume Flows).
- **Database file**: `AppDb.db` lazy singleton; file lives at
  `getDatabasePath("trainfree.db")` (Android, context via `DbContextHolder`,
  set in `MainActivity`) or the app documents dir (iOS).
- **Schema JSON** is exported to `sharedLogic/schemas/` (committed). Bump the
  `@Database` version and write a migration for any schema change — then
  verify it: `createDatabase()` uses `fallbackToDestructiveMigration(dropAllTables
  = true)`, so a wrong migration silently wipes data instead of crashing.
  Reconstruct a version-N database from the committed schema JSON, open it
  with `addMigrations(...)` and **no fallback**, and query it — Room
  validates the migrated schema and throws on any mismatch (see
  `Migration13To14Test.kt` in `sharedLogic/src/iosTest/.../db/` for the
  pattern; run with `:sharedLogic:iosSimulatorArm64Test` — there's no
  `jvmTest` source set). Mutate the migration once and re-run to confirm the
  test can actually fail; with the fallback off, a broken migration can still
  leave empty tables that vacuously pass assertions.

## Localization

`sharedUI` strings go through Compose Multiplatform resources
(`sharedUI/src/commonMain/composeResources/values/strings.xml`); a new
language is a new `values-<lang>/strings.xml`. `sharedLogic` never emits
user-facing English — it returns keys/enums/structured values (e.g.
`InsightCard.headlineKey`, `ProfileNameError`), which `LogicLabels.kt` maps to
Compose strings and `iosApp/iosApp/L10n.swift` maps to
`String(localized:defaultValue:)` constants under the same keys. Follow
`.claude/skills/localized-strings/SKILL.md` for the full checklist when
adding UI text.

Gotcha: Compose Multiplatform resources don't process Android-style escape
sequences — `\'`/`\"` render as a literal backslash+quote in strings.xml. Use
plain `'` and `"`.

## Building and running

- Android: `./gradlew :androidApp:assembleDebug`
- iOS: open [iosApp](iosApp) in Xcode and run from there (requires Xcode 26+;
  deployment target is iOS 26.0). Headless:
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' build`

If Gradle or xcodebuild's "Compile Kotlin Framework" phase fails with an
opaque JDK/toolchain error, `JAVA_HOME` likely isn't pointed at a real JDK —
export it explicitly before invoking `./gradlew`/`xcodebuild`. If
`xcode-select` points at CommandLineTools rather than a full Xcode install,
also set `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.

## Tests

- Android: `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest`
- iOS: `./gradlew :sharedUI:iosSimulatorArm64Test :sharedLogic:iosSimulatorArm64Test`

Run the ones relevant to what you touched; there's no single "test everything"
task because platform toolchains (Xcode, Android SDK) aren't all guaranteed
to be present.

## Resetting app state (manual/agent testing)

The tab shell is behind the profile gate; creating a profile through the
first-run form is the fast path. To get back to a fresh install:

- Android: `adb shell pm clear dev.balaji.moonrose.trainfree`
- iOS simulator: delete the app (the database lives in the app's Documents
  directory).
