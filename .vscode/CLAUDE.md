# Tape — Claude Code Project Context

## What this is

Tape is a free, ad-free AR measurement app for Android. Point your camera at two points,
tap them, get the distance. Free forever — no subscription, no premium tier, no ads.

- Package: `com.tape.measure`
- Min SDK: 24 (Android 7.0+)
- Stack: Kotlin 2.0.21 · Jetpack Compose 2024.09.03 · ARCore · Hilt · KSP · Kotlin Serialization
- License: MIT

## Project layout

```
app/src/main/java/com/tape/measure/
  ui/
    screens/welcome/  WelcomeScreen.kt
    theme/            Color.kt, Theme.kt, Type.kt
    MainActivity.kt
    TapeApplication.kt
  ...                  (camera, AR session, distance calc — coming next)
```

## Visual identity (golden rule)

**Token colors MUST mirror the runtime palette** in `app/src/main/java/com/tape/measure/ui/theme/Color.kt`.
When suggesting colors, names, or visual changes, derive from there — do not invent a new palette.
The IDE theme at `.vscode/themes/tape-warm-amber.json` is derived from the same Color.kt so
editor and app feel like the same product.

Primary palette anchors:
- Accent (warm amber): `#E89B3C`
- Editor / app bg (warm near-black): `#0F0E0C`
- Foreground (warm bone): `#F4ECDF`
- Cursor / highlight (warm gold): `#FFC857`

## Design principles

1. One job, done well. Measurement. That's it.
2. Free forever. No subscription, no premium tier, no upsells.
3. Honest about accuracy. Show confidence. Explain why a measurement might be off.
4. Respect the user's time. Tap two points. Get the distance. No tutorial walls.
5. Respect the user's data. No account. No analytics on what you measure. Local-first.

## Architecture

- **MVVM + unidirectional data flow.** Compose for UI, ViewModels expose `StateFlow<UiState>`.
- **Hilt for DI.** `@HiltAndroidApp` on TapeApplication; `@HiltViewModel` on VMs.
- **ARCore for camera + plane detection.** Check support before rendering camera; show honest
  "unsupported" screen if device lacks ARCore.
- **Kotlin Serialization for persistence** (TBD — local Room or simple file). No analytics SDK.

## Build commands

```
./gradlew assembleDebug                # build APK
./gradlew installDebug                 # build + install on connected device
./gradlew test                         # unit tests
./gradlew lint                         # Android lint
```

ARCore-capable device or emulator required. No tests for plane detection without a real
device — mock the ARCore APIs in unit tests.

## Working agreements

- **Match repo conventions.** 4-space Kotlin indent; Compose `Modifier` first in the param
  list; small composables; prefer `derivedStateOf` over nested recomposition.
- **Don't add a paywall, ads SDK, or analytics SDK.** It crosses the design principles
  (Free forever / No analytics). Push back, don't comply.
- **Permission UX matters.** `Manifest.permission.CAMERA` is the only dangerous permission
  needed — flow it through the welcome screen with a clear "why."
- **Min SDK 24 floor.** Don't pull in libraries that require higher.
- **When changing visual tokens in `Color.kt`**, update `.vscode/themes/tape-warm-amber.json`
  in the same commit — editor and app must stay in sync.

## Where to find more

- `README.md` — full release plan, v1.0 → v2.0 roadmap with timeline
- `.hermes/plans/` — discovery, design brief, execution plan
- `docs/` — design notes and mermaid diagrams
