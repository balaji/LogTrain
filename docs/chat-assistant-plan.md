# On-device AI chat assistant plan

Status: **Phase 1 in progress** (`ChatToolbox` + `ExerciseMatcher` in `sharedLogic`).

## Guiding constraints

- **Strictly offline / on-device.** Train Free has no backend, no sign-in, and no `INTERNET` permission. All inference runs locally via Apple Foundation Models (iOS 26+) and Gemini Nano via ML Kit GenAI Prompt (`com.google.mlkit:genai-prompt`, Android).
- **Read-only Q&A + catalog coaching in v1.** The assistant answers questions over the user's logged workouts, PRs, progression, routines, body metrics, and profile, plus exercise form/substitution guidance grounded in the app's exercise catalog. No write actions (creating routines, logging sets/weight) and no active-session recall in v1.
- **Deterministic Kotlin math.** Every statistic, 1RM estimate, volume sum, streak, BMI, date label, and unit formatting is computed in Kotlin (`InsightsRepository`, `InsightMath`, `ChatToolbox`). The language model only selects tools/context and phrases responses using the numbers returned to it.
- **Ephemeral in-memory sessions in v1.** Conversation history lives in `ChatState` while the app is open, with a "New Chat" reset action. No Room schema migration (`AppDatabase` stays at v15).
- **Shared logic, native UI.** Following `AGENTS.md`, tool execution, matching, and `StateFlow`-backed `ChatState` live in `sharedLogic` (`commonMain`/`dbMain`); SwiftUI (`iosApp`) and Compose (`sharedUI`) render native chat surfaces.

## Current state

Existing on-device LLM infrastructure (built for Progress tab card narration):

- `InsightGenerationProvider` (`sharedLogic/commonMain`) — callback-based one-shot text generation interface.
- `FoundationModelInsightProvider.swift` (`iosApp`) — iOS 26 `FoundationModels` (`SystemLanguageModel.default`, `LanguageModelSession`).
- `GeminiNanoInsightProvider.kt` (`sharedLogic/androidMain`) — Android ML Kit `genai-prompt:1.0.0-beta5` (`Generation.getClient()`, probing `Feature.SYSTEM_PROMPT`).
- `InsightsRepository` / `InsightMath` (`sharedLogic`) — deterministic analytics already shaped for tool reuse (`weeklyVolume`, `personalRecords`, `exerciseProgression`, `trainingFrequency`, `trainedExerciseNames`).

## Target architecture

```
SwiftUI Chat Sheet (iosApp)          Compose Chat Sheet/Screen (sharedUI)
           \                                       /
            +--------> StateFlow<ChatUiState> <---+
                                |
                       ChatState (sharedLogic/dbMain)
                        - Conversation history & sliding window
                        - Starter prompt generation
                        - Availability & cancellation state
                                |
           +--------------------+--------------------+
           |                                         |
iOS: FoundationModelChatDriver        Android: GeminiNanoChatDriver
- LanguageModelSession + streamResponse - Feature.FUNCTION_CALLING probe
- Swift Tool structs calling ChatToolbox - dynamicTools loop OR
                                           ChatContextRouter pre-fetch fallback
                                         - generateContentStream
           \                                         /
            +--------> ChatToolbox (sharedLogic/dbMain)
                        - ExerciseMatcher (commonMain)
                        - Unit-aware, token-bounded read queries
```

### Handling platform asymmetry (iOS vs. Android)

- **iOS (`FoundationModels`)**: `LanguageModelSession` natively drives multi-turn tool calling via Swift `Tool` conformances (`@Generable` argument structs) and supports token streaming (`streamResponse`). Swift `Tool` structs are thin adapters delegating directly to `ChatToolbox` and returning pre-formatted text.
- **Android (`com.google.mlkit:genai-prompt`)**: While `genai-prompt:1.0.0-beta5` exposes `Feature.FUNCTION_CALLING` and `dynamicTools`, availability of optional `Feature` flags varies across AICore / Gemini Nano device builds.
  - When `Feature.FUNCTION_CALLING` is in `client.getAvailableFeatures()`, `GeminiNanoChatDriver` runs a bounded tool-calling loop (`maxToolRounds = 4`) via `ChatToolbox`.
  - When `Feature.FUNCTION_CALLING` is unavailable on an otherwise eligible device, `ChatContextRouter` in `sharedLogic` deterministically matches exercise/routine entities and question intents (PRs, progression, volume, recent workouts, routines, body metrics, exercise catalog) and pre-fetches the relevant `ChatToolbox` snippets into a grounded `GenerateContentRequest`.

### Context window management (~4,096 tokens)

Both Apple's on-device model and Gemini Nano share roughly a 4K-token ceiling across instructions, tool schemas, conversation transcript, tool outputs, and model output:

1. **Compact tool surface (7 tools)**: Prefer a small set of parameterized tools over many fine-grained ones to minimize per-turn schema overhead.
2. **Pre-formatted, bounded tool outputs**: `ChatToolbox` returns terse line-oriented text with explicit unit labels (`kg`/`lb`, `km`/`mi`, `hh:mm`) rather than raw entity dumps, capping list sizes (`limit`) and omitting lengthy exercise descriptions unless requested (`includeDescription = true`).
3. **Sliding transcript window**: `ChatUiState.messages` keeps the full UI transcript, while the model receives only the recent turns (stripping stale tool payloads from older turns). On `.exceededContextWindowSize` (iOS) or `countTokens` approaching `getTokenLimit()` (Android), the driver resets/compacts the underlying session with a short rolling summary.

## Tool surface (`ChatToolbox`)

All tools return `ToolOutput(summary: String, sources: List<ChatSourceRef>)`, pairing model-readable text with structured entity references (`exerciseId`, `epochDay`) so the UI can render clickable verification chips.

1. `getTrainingSummary(weeks = 8)` — weekly volume trend, workout frequency/streaks, and 1-year/recent body-part distribution (`InsightsRepository`).
2. `getPersonalRecords(exerciseQuery?, limit = 5)` — best estimated 1RM / weight PRs for strength lifts, and best duration/distance marks when filtered to non-strength exercises (`ExerciseType`).
3. `getExerciseProgression(exerciseQuery, weeks = 12)` — weekly best 1RM & volume (or duration/distance) plus the last few sessions' working sets.
4. `getRecentWorkouts(limit = 5, routineQuery?, days?)` — completed sessions with date, duration, routine name, and grouped working sets (warm-ups excluded).
5. `getProgramsAndRoutines(routineQuery?)` — programs, routines, target sets/reps/weights, and last-performed dates so the assistant can answer *"what's next in my split?"*.
6. `searchExercises(query?, bodyPart?, equipment?, includeDescription = false, limit = 8)` — fuzzy search across `ExerciseRepository` (including `otherBodyParts` and `equipment`) for form guidance and equipment-matched substitutions.
7. `getBodyMetricsAndProfile(days = 90)` — body-weight trend (`BodyMetricsRepository`), profile stats, and BMI computed with the user's configured `BmiFormula` and `BmiClassification`.

### Fuzzy entity resolution (`ExerciseMatcher`)

Users write *"bench"*, *"OHP"*, *"squat"*, or *"RDL"* rather than `"Barbell Bench Press"`, `"Barbell Overhead Press"`, `"Barbell Back Squat"`, or `"Romanian Deadlift"`. `ExerciseMatcher` resolves queries using:
- Exact case-insensitive match, then normalized/punctuation-stripped match.
- Common gym abbreviation expansion (`bp`, `ohp`, `rdl`, `sldl`, `dl`, `bb`, `db`, `kb`, `ez`).
- Body-part group aliases (`legs` -> `thighs`, `hamstrings`, `glutes`, `calves`; `arms` -> `biceps`, `triceps`, `forearms`).
- Token overlap and prefix scoring, breaking ties using the user's actual trained set counts (`ObserveCompletedSetCountsByExercise`).

## Guardrails & localization

- **Safety**: System instructions reuse `InsightCardNarrator`'s guardrails — use only tool-provided numbers, no invented stats, no medical/injury advice, and neutral factual phrasing on body weight/BMI (no diet prescriptions).
- **Language**: System instructions include the target language from `currentLanguageTag()` (`"en"`, `"sv"`). All UI strings, empty/unavailable states, disclaimers, and starter prompt chips go through `strings.xml` + `L10n.swift` / `Localizable.xcstrings`.

## UI & navigation

- **No 5th tab**: Entry points live where users already inspect data, hidden when `availability == "device_not_eligible"` and showing a status prompt for `apple_intelligence_disabled` / `model_downloading`:
  - **Progress tab**: Top-bar "Ask Coach" action opening the chat sheet with data-aware starter chips.
  - **Exercise Detail & Day Detail**: Contextual *"Ask about this exercise"* / *"Ask about this workout"* action pre-seeding the chat sheet with `exerciseId` or `epochDay` context.
- **Verification pills**: Assistant messages display subtle source chips (`Bench Press`, `Nov 10 workout`) linking to `ExerciseDetailRoute` / `DayDetailRoute`.

## Phased rollout

1. **Phase 1 — `ChatToolbox` + `ExerciseMatcher` (`sharedLogic`)**: Read-only DAO queries, `otherBodyParts` mapping on `Exercise`, fuzzy matching, unit-aware `ToolOutput` builders, and unit tests. *(Current)*
2. **Phase 2 — `ChatState` + `ChatGenerationProvider` + `ChatContextRouter` (`sharedLogic`)**: `StateFlow<ChatUiState>`, streaming/cancellation lifecycle, sliding window compaction, intent-preload fallback, and fake-driver state tests.
3. **Phase 3 — Platform drivers**: `FoundationModelChatDriver` (iOS) + `GeminiNanoChatDriver` (Android).
4. **Phase 4 — Native UIs**: SwiftUI chat sheet (`iosApp`) + Compose chat sheet/screen (`sharedUI`), localized strings, and Progress / ExerciseDetail / DayDetail entry points.
