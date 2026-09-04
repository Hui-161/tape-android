# Tape — Execution Brief

**Date:** 2026-08-22
**Status:** v1 scope locked, v2 parked, ready for design prototype handoff

---

## TL;DR

Build a free, ad-free, no-account AR point-to-point measurement app for Android (phone + tablet, portrait + landscape). Replicate the iOS Measure app's core gesture — tap two points, get a distance — without the dark patterns, paywalls, or guesswork. v1 ships ONLY point-to-point measurement. Everything else (level tool, 3D object detection, image upload, room scan) is **out of scope for v1**.

---

## v1 scope (locked)

### What ships in v1.0

| # | Feature | Why | Notes |
|---|---|---|---|
| 1 | AR point-to-point distance | The core gesture | Two taps on detected planes. Confidence badge per measurement. |
| 2 | Plane detection (floor + wall) | Required for ARCore hit-testing | Visual indicator when tracking is locked |
| 3 | Unit switching (cm / m / in / ft) | Global audience | Global toggle (viewfinder + saved list agree) |
| 4 | Save measurements locally | Privacy-first, no account | Optional label. No cloud in v1. |
| 5 | Measurement list (Saved) | Review past measurements | Rename, delete, share as text/image |
| 6 | Settings (minimal) | Default unit, theme (dark/light/system), about | No analytics opt-in, no notifications, no account |
| 7 | Honest accuracy indicator | The wedge — we win by being trustworthy | Derived from tracking quality + distance |
| 8 | Phone + Tablet, Portrait + Landscape | You said tablet has no specific use case but should work opportunistically | Two-pane on tablet landscape |

### What does NOT ship in v1 (explicit anti-scope)

| Anti-feature | Reason | Target |
|---|---|---|
| 3D object detection (auto-measure) | Needs ML pipeline (ML Kit / MediaPipe), quality tradeoff on non-ToF phones, broader complexity | v2.0 |
| Level / spirit bubble tool | Civil engineers use it but it's a separate workflow | v1.x later |
| Room scan / floor plans | High complexity, different UX | v1.2 |
| Photo + reference object mode | Non-AR fallback, but per your direction AR-first | v1.1 (revisit if v1 ships and demand shows) |
| Image upload (instead of camera) | You asked — works for photos of past objects but ARCore can't reconstruct from a single image without depth data | v1.1 (use photo + reference object, NOT AR-on-still-image) |
| Cloud sync / accounts | Privacy-first — local only | v2.0 (optional, E2E encrypted if at all) |
| Subscription / IAP | Free forever | never |
| Ads in measurement flow | Trust wedge | single banner on home screen max, v1.x |
| iOS port | Native Kotlin Android only | v2.0 |

---

## User flows (v1)

### Flow 1 — First measurement (primary)

```
Open Tape
  → Welcome screen ("Let's measure something")
  → Tap "Continue"
  → Camera permission request (plain explanation)
  → Camera opens, plane detection starts
  → "Move phone to detect surface" hint
  → Plane locked → status badge changes to "ready"
  → Tap point A → dot drawn, hint "Tap second point"
  → Tap point B → line + distance + confidence badge
  → Action sheet: Save | Units | Undo | Reset | Share
  → Done. Continue measuring OR exit.
```

### Flow 2 — Save a measurement

```
After Flow 1 with a measurement visible
  → Tap "Save"
  → Optional: type a label (or skip → saves as untitled + relative date)
  → Toast: "Saved"
  → Measurement persists to local Room DB
```

### Flow 3 — View saved measurements

```
Open Tape
  → Tap "Saved" (or pull from drawer)
  → List view: [label | value | relative date]
  → Tap row → detail view (rename, delete, share)
  → Empty state: "No measurements yet. Tap two points in the camera."
```

### Flow 4 — Switch units

```
Anywhere in the app where a measurement is shown
  → Tap the unit chip (m / cm / in / ft)
  → OR go to Settings → Default unit → changes everywhere globally
```

### Flow 5 — Unsupported device

```
Open Tape on a non-ARCore device
  → Welcome screen → Continue
  → "Your phone doesn't support AR measurement" honest screen
  → Show what's coming in v1.1 (photo + reference object mode) as locked card
  → "Got it" → home screen with no camera
```

---

## Design prototype (received 2026-08-22)

Claude Design delivered the prototype. Two files:
- `Tape Prototype` — interactive HTML prototype, all 10 screens, states navigable, dark/light theme toggle, density switcher, confidence display mode, grid/notes toggles, phone/tablet switcher
- `Tape AR Variations` — side-by-side comparison of 3 AR measurement treatments (Minimal / Ambient Grid / Confidence-Forward)

**Recommended default (for v1): Variation 1c — Confidence-Forward**
- Tolerance (±%) is the headline, value secondary
- ±2% HIGH badge directly addresses the "measurement apps lie" pain from competitor reviews
- Plain-language explanation: "Two locked planes, 1.4 m apart. Expect ±3 cm on this reading."
- Honest wedge, hardest to fake

**Other variations to keep in mind:**
- 1a Minimal — cleanest but hides accuracy (cuts against honesty wedge). Don't use as default.
- 1b Ambient Grid — good context, useful for the "I'm lost in space" feeling. Consider as opt-in via the `showGrid` toggle already in the prototype.

**Design decisions captured in the prototype** (these are now binding unless we explicitly revise):
- Dark baseline, light as a setting
- IBM Plex Sans (prose) + IBM Plex Mono (measurements, tabular figures)
- Warm amber primary on warm neutrals
- Radius 8-12px, motion under 220ms
- No emoji, no glassmorphism, no gradients
- Density: comfortable (we picked this)

---

## Technical context

| | Choice | Why |
|---|---|---|
| Language | Kotlin 2.0+ | Direct ARCore access, no plugin risk |
| UI | Jetpack Compose | Modern, less XML, state-friendly |
| AR | ARCore | Native, best Android AR accuracy |
| Min SDK | 24 (Android 7.0) | ~97% device coverage |
| Target SDK | 35 (Android 15) | Play Store requirement for 2026 |
| DB | Room | Local-only by privacy policy |
| DI | Hilt | Standard |
| Async | Coroutines + Flow | Idiomatic Kotlin |
| Testing | JUnit5 + MockK + Compose UI Test | Standard |
| Build | Gradle (Kotlin DSL) | Modern, type-safe |

**Accuracy expectations (honest):**
- Flagship w/ ToF (Pixel 6+, S22+, OnePlus 9+): ±1-2%
- Mid-range (M-series, Nord, Note 13): ±3-5%
- Sub-₹10K: degraded, often unusable

Tape will display the confidence badge derived from tracking quality + distance so users know when to trust the number and when to grab a physical tape.

---

## Privacy & data

- All measurements stored on-device in Room DB
- No analytics, no crash reporting, no account
- Camera frames processed on-device only, never uploaded
- Settings → About links to LICENSE (MIT) and source code

---

## Deployment

- **v1.0:** Play Store only (paid Google Play developer account, $25 one-time)
- **Future:** companion PWA (photo + reference object mode) on Vercel (free tier)

---

## Out of scope for THIS document

- v1.1 (photo + reference object)
- v1.2 (room scan / floor plans)
- v2.0 (3D object detection, iOS port, optional E2E cloud sync)

Those are tracked separately. v2.0 3D detection notes (for when we get there):
- Google ML Kit Object Detection (free, on-device, ~20 categories — furniture, electronics, food, fashion, home goods, places, plants)
- MediaPipe Object Detection as alternative
- Depth API: real depth maps from ToF sensor (flagship only), pose-aware heuristics as fallback for non-ToF
- Bounding-box-to-physical-dimension math is the hard part — needs depth + perspective correction
- **Disable auto-detect entirely on non-ToF devices** per tier (you'll be doing live testing on tables/chairs at home — that's the validation)

---

## Open questions (resolved)

1. ~~Package name?~~ → `com.tape.measure`
2. ~~App icon direction?~~ → Yellow-amber tape-measure motif on dark surface (iterate before Play Store submission)
3. ~~Confidence thresholds?~~ → <70% amber "lower accuracy", <40% red "unreliable" tag
4. ~~Default unit on first launch?~~ → Locale-driven (en_US / en_LR / en_MM → imperial, else metric), Settings override

---

## Visual direction (locked 2026-08-22)

| Element | Spec |
|---|---|
| **Color theme** | Yellow + amber gradient (`#FFC857` → `#E89B3C` primary) on warm near-black (`#0F0E0C` background, `#1F1C18` / `#2B2722` surfaces). Matches tape-measure mental model. |
| **Typography** | IBM Plex Sans for prose, IBM Plex Mono (or Berkeley Mono / JetBrains Mono) for measurements — segmented, monospaced, industrial-numeral feel like a real tape measure's printed numbers. NOT Geist Mono (too clean). |
| **Shape** | Radius 8–12px cards, 4–6px small elements. No pill buttons. No glassmorphism. |
| **Motion** | Restrained. Measurement line 200ms draw-in. Confidence fade-in. No bounce, no celebration. |
| **Iconography** | Phosphor / Lucide / Tabler — match Barely. No custom illustrations. |
| **Dark mode** | Default. Light is a setting. |

---

## Decision log (so far)

| Date | Decision | Rationale |
|---|---|---|
| 2026-08-19 | Name = "Tape" | India-friendly, verb+noun, low Play Store top saturation |
| 2026-08-19 | Tech = native Kotlin (not Flutter) | AR plugin accuracy risk; you chose Option A after I flagged it |
| 2026-08-19 | Free forever, no monetization in v1 | Win by being the opposite of the category |
| 2026-08-22 | Default AR variation = 1c Confidence-Forward | Honest wedge, hardest to fake |
| 2026-08-22 | v1 = point-to-point only | You confirmed: replicate iOS Measure core gesture |
| 2026-08-22 | v2.0 = 3D object detection (ML Kit) | Auto-detect gated behind a "Scan" button, not always-on |
| 2026-08-22 | Disable 3D detect on non-ToF phones | Per your acceptance of my tier split |
| 2026-08-22 | Image upload = photo + reference object (v1.1), not AR-on-still-image | ARCore can't reconstruct 3D from single photo without depth |
| 2026-08-22 | Package = `com.tape.measure` | Public, Play Store-ready |
| 2026-08-22 | Color = yellow-amber gradient on warm near-black | Matches tape-measure mental model |
| 2026-08-22 | Numerals = IBM Plex Mono / industrial monospace | Segmented feel, like real measuring tools |
| 2026-08-22 | Default unit = locale-driven (en_US/LR/MM → imperial, else metric) | Settings override available |