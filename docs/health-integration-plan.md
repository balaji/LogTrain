# Health platform integration plan

Status: **draft / ideas** — not started. Android (Health Connect) first;
Apple Health (HealthKit) to be designed in a later session using the same
shared abstraction.

## Guiding constraints

- Train Free stays **fully offline**. Health Connect and HealthKit are both
  on-device OS stores — no network, no `INTERNET` permission, no sign-in.
- **Integrate with Health Connect, not the Google Health API.** The Google
  Health app (ex-Fitbit) is a separate cloud app that syncs *into* Health
  Connect; the cloud Google Health API (`health.googleapis.com`) needs OAuth +
  internet and is only relevant to a future premium sync backend.
- On-device AI narration over health data is fine — nothing leaves the device.
- Value depends on what else the user has: phone-only users mostly get
  Train Free's own workouts/weight; HR/sleep/HRV need a watch, scale, or other
  app feeding the store.

## Current state

No Health Connect / HealthKit code exists. Relevant local data:
`workout_sessions` (performed_at / completed_at), `workout_session_details`
(targets + actuals, `completed` flag but no per-set timestamp),
`work_sets` / `work_set_details` (routine targets), `body_metrics` (weight
only), `profile` (height/weight).

## Phase 1 — Train Free as a data source (write)

1. **Workouts → `ExerciseSessionRecord`**
   - `EXERCISE_TYPE_STRENGTH_TRAINING`, start = `performed_at`,
     end = `completed_at`, title = routine name.
   - Idempotent: `clientRecordId = "trainfree:session:<id>"`,
     `clientRecordVersion = updated_at`. Edits upsert; local delete →
     delete in HC.
   - Skip `test_data` rows.
2. **`ExerciseSegment` per exercise/set**
   - HC has segment types (bench press, squat, deadlift, curls, …) with a
     repetitions count. Needs an exercise → segment-type mapping, keyed on
     builtin exercises in `ExerciseSeed.kt`.
   - Segments need timestamps → add `completed_at` to
     `workout_session_details` (also unlocks real rest-time analytics).
   - HC segments have no weight field — put a load summary in session notes.
3. **Weight → `WeightRecord`** on `body_metrics` insert; profile height →
   `HeightRecord`.
4. **Cardio** — distance sets → `DistanceRecord` / laps.

## Phase 2 — Enrich Train Free (read)

5. **Smart-scale import** — `WeightRecord`, `BodyFatRecord` into
   `body_metrics` (new `source` + `external_id` columns). Filter out our own
   `dataOrigin` to avoid echo loops.
6. **Heart rate per session** — read `HeartRateRecord` over the session
   window; store avg/max HR + time-in-zones on the session (not raw samples).
   Show on DayDetail.
7. **Calories** — aggregate active calories over the session window from the
   watch; don't estimate ourselves.
8. **Recovery insights** — `SleepSessionRecord`, `RestingHeartRateRecord`,
   `HeartRateVariabilityRmssdRecord` → new insight cards ("short sleep,
   consider a lighter day", sleep vs PRs), narrated by the existing on-device
   AI (`ai_insight_cache`).
9. **Other activities** — exercise sessions from other apps (runs, rides) as
   read-only calendar entries; optionally count toward streaks.

## Phase 3 — Training plans

10. **`PlannedExerciseSessionRecord`** — near-perfect fit for the schema:
    routine → planned session, `work_set` → block, `work_set_detail` → step
    with `WeightTarget` / `RepetitionsTarget` / `DurationTarget` /
    `DistanceTarget` (maps 1:1 to `target_*` columns). Link completed
    sessions via `plannedExerciseSessionId` so watches / Google's apps can
    show "today's planned workout" and adherence. Gate on
    `getFeatureStatus(FEATURE_PLANNED_EXERCISE)`.
11. **Later** — Wear OS companion using Health Services for live HR during
    sets.

## Suggested order

1 → 3 → 6 → 5 → 10 → 8

## Architecture (Apple-ready)

- **`HealthPlatform` interface in `sharedLogic`** with domain-level methods:
  `upsertWorkout`, `deleteWorkout`, `writeWeight`, `readWeights(since)`,
  `heartRateSummary(window)`, `recovery(day)`. Android actual = Health
  Connect; iOS actual = HealthKit (`HKWorkout` +
  `.traditionalStrengthTraining`, bodyMass, heartRate).
- Exercise → platform type mappings live in shared code as enums.
- **Deterministic IDs** work on both platforms: HC `clientRecordId` /
  `clientRecordVersion`; HealthKit `HKMetadataKeySyncIdentifier` /
  `HKMetadataKeySyncVersion` — may avoid needing a sync-ledger table.
- **Incremental sync** — HC Changes API tokens stored in `user_settings`;
  WorkManager for background sync. Permissions:
  `READ_HEALTH_DATA_IN_BACKGROUND`, `READ_HEALTH_DATA_HISTORY` (>30 days).
- **Settings section** — per-type toggles, "Sync now", last synced. Shared
  `StateFlow` state class per AGENTS.md; strings via `strings.xml` +
  `L10n.swift`.
- **Schema changes** (`body_metrics.source/external_id`, detail
  `completed_at`, session HR summary columns) → version bump + migration +
  migration test (`Migration13To14Test` pattern).

## Platform / store requirements

### Android — Health Connect
- Built into Android 14+; Android 9–13 needs the Health Connect app from
  Play. minSdk 26 is fine. Check availability via `getSdkStatus`.
- Manifest: permission-rationale activity
  (`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`) and a
  `VIEW_PERMISSION_USAGE` activity-alias (Android 14+).
- Play Console: Health apps declaration, justify each permission, privacy
  policy URL (required even though offline), no ads/data-selling use.
- Health Connect backup/restore via Google account is a system feature —
  not our concern.

### iOS — HealthKit (later session)
- HealthKit capability/entitlement; `NSHealthShareUsageDescription` and
  `NSHealthUpdateUsageDescription` in Info.plist.
- Per-type authorization sheet; no sign-in. iCloud Health sync is a
  user-level OS setting (end-to-end encrypted).
- App Store: privacy policy required; no advertising use; don't store
  health data in our own iCloud.
- Background updates via `HKObserverQuery` + background delivery.

## Open questions

- Which HC exercise type: `STRENGTH_TRAINING` vs `WEIGHTLIFTING`?
- Segment granularity: one per set vs one per exercise?
- Should imported body-weight rows be editable/deletable in Train Free?
- Do other-app workouts count toward streaks/insights?
- Is the user-facing toggle one master switch or per data type?
