---
name: localized-strings
description: User-facing text must go through translation constants, never hardcoded English. Read before writing or editing any UI code (Compose screens, iOS SwiftUI views, alerts, buttons, titles, labels, placeholders, empty states, error messages).
---

# Localized strings

Every string a user can see or hear (titles, buttons, tabs, alerts, overlays, menus, placeholders, empty states, error messages, accessibility labels) must come from a translation constant, not an inline English literal. This keeps regionalization a translations-only task.

## Where strings live, per module

- **sharedUI (Compose)** — add the string to
  `sharedUI/src/commonMain/composeResources/values/strings.xml` and render with
  `stringResource(Res.string.<key>)`. Parameterized text uses `%1$s`-style
  placeholders; plurals use `<plurals>`. Do NOT escape quotes as `\'`/`\"` —
  Compose Multiplatform renders the backslash literally; use plain quotes.
- **iOS shell (iosApp)** — add a constant to `iosApp/iosApp/L10n.swift` using
  `String(localized: "<key>", defaultValue: "…")` (a `static func` for
  parameterized text) and reference `L10n.<member>` from views. Keys mirror the
  Compose resource names in strings.xml so the two translation tables stay
  aligned; iOS-only keys sit at the end of their section, commented.
- **sharedLogic** — never emits display text. It produces language-neutral
  keys, enums, ids, and pre-formatted numeric args (see InsightCard,
  SampleProgram, ProfileNameError). Each shell maps those to localized text:
  Compose in `sharedUI/.../screens/LogicLabels.kt`, iOS in `L10n.swift`.
  When adding a new key to sharedLogic, add the mapping to BOTH shells.

## Exercise catalog

- Exercise **name** and **description** are catalog/user data — shown verbatim,
  deliberately not localized.
- **bodyPart / equipment / exerciseType** are lowercase language-neutral DB
  values ("chest", "smith machine", "reps_duration"). Display them only through
  `exerciseCatalogLabel(...)` (LogicLabels.kt on Compose, L10n.swift on iOS) —
  never `capitalize`/`firstCapitalized` on the raw value.

## Not localized (deliberately)

- The brand name "Train Free" (`app_name` / `L10n.appName`).
- Language-neutral symbols/data: "—", numbers, units passed in as args,
  SF Symbol names, ids/keys, DB values used as values rather than display text.
- `Route.title` defaults in Routes.kt are never-displayed fallbacks (tab labels
  and screen titles resolve through resources or carry user data).

## Checklist when adding UI text

1. Add the key to strings.xml (and use the same key name in L10n.swift if the
   iOS shell shows the same text).
2. No English literal in the composable/view — only resource/constant lookups.
3. If the text originates in sharedLogic, emit a key there and map it in
   LogicLabels.kt + L10n.swift instead of emitting English.
