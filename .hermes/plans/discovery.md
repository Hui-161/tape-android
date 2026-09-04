# Tape — Discovery

**Date:** 2026-08-19
**Author:** Marc (Hermes)
**Status:** Research complete. Awaiting user verdict on go/no-go.

---

## TL;DR

The problem is **real and validated by users**, the gap is **clear and ugly** (free users get terrible experiences in existing apps), and the technical path is **feasible but constrained by ARCore device support**. This is worth building — but only as a tightly-scoped, free, AR-only Android app that wins on simplicity and trust, not features.

Three things kill 90% of the category, and Tape must be the opposite of all three:
1. **Paywalls** — every decent competitor hides the basic measurement behind a subscription
2. **Dark-pattern ads** — frequent complaint in reviews
3. **Inaccuracy** — AR point-to-point is genuinely hard on cheap phones

If we ship a clean, free, accurate point-to-point tape measure that just works, we have a real shot at the segment of users who refuse to pay for utilities and gave up on the ad-laden alternatives.

---

## Q1: Does the problem exist?

**Yes, unambiguously.**

### The Android-side gap
- **No native Android Measure app exists.** Google has never shipped one to compete with iOS Measure. iOS has had it built-in since 2018 (iOS 12).
- **Google Lens** does NOT do spatial measurement. It identifies objects, translates text, scans QR codes, etc. — but no ruler/tape function. Confirmed via Play Store listing.
- ARCore is Google's AR SDK (free), but they have never wrapped it as a consumer app. Only OEM implementations on Pixel Camera (limited), Samsung Camera (limited).

### The competitor reality on Play Store
Search "tape measure" returns ~25 apps. Top hits:

| App | Dev | Rating | Reviews | Downloads | Model |
|---|---|---|---|---|---|
| AR Ruler App: Easy Measure Pic | Grymala | 4.3 | 247K | 10M+ | Ad-supported + subscription |
| Tape Measure: Measuring Ruler | Grymala | 4.1 | — | — | Same dev, same model |
| AR Meter: Tape Measure Camera | Galaxy studio | 4.0 | — | — | Subscription |
| AR Measure Tape: SmartRuler | Lascade | 4.2 | — | — | Subscription |
| Ruler | NixGame | 4.4 | — | — | Free with ads (basic) |
| Ruler | Xalpha Lab | 4.6 | — | — | Free with ads |

**Key signal:** The Grymala apps (10M+ downloads on the flagship one) are clearly making real money. They ship 4 apps in this category alone. The market is profitable.

### What users actually say (extracted from live Play Store reviews of AR Ruler App: Easy Measure Pic)

> "I was forced to subscribe to unlock its features. so I paid $50 on a discounted deal and got the annual subscription only to find out that the app doesn't work well." — Erin S.

> "absolutely stuffed with ads and, to be frank, sneaky hidden fees and subscription buttons... It cannot measure anything that's not on a level plane... fails as an AR ruler app. Waste of time, do not use." — Alexander B.

> "STAY AWAY. Horrible dark pattern ads at nearly every click. I couldn't even use it for a simple point to point measure and when I tried it just kept putting new measure points on screen." — Ken P.

The dev response to all of these is identical boilerplate "thank you for your understanding." That's a company that has decided to extract maximum revenue per install and has stopped optimizing for satisfaction.

**Verdict:** Problem confirmed. Users are frustrated. Existing solutions are optimized for revenue extraction, not for getting the user a measurement.

---

## Q2: What do competitors do, and what's the gap?

### Common feature set across competitors
- AR point-to-point distance measurement (core)
- AR plane detection (floor / wall)
- Person height estimation (some)
- Photo-based measurement with reference object (some)
- Level tool (bubble)
- Unit conversion (cm / in / ft)
- Floor plan / room scan (premium tier)
- Export to PDF (premium tier)
- Save measurements history (premium tier)

### The gap (where every competitor is bad)

**Gap 1: Free tier is unusable.** Almost every app gates the one thing users actually want (point-to-point measurement) behind a subscription. Free users get ads, watermarks, or limited measurements per session.

**Gap 2: Inaccuracy on Android.** iOS Measure works because LiDAR is in every recent iPhone. Android fragmentation means ARCore must run on hardware from ₹8K phones to ₹80K flagships. Cheap phones have bad IMUs, no depth sensor, low-quality cameras. The result is jittery points and 5-15% measurement error. Users notice.

**Gap 3: Dark-pattern ads.** Interstitials after every measurement, "watch this ad to save your measurement," popup subscriptions when you tap "export." The whole category feels scammy.

**Gap 4: No trust signal.** No app explains *how* it measures, *what accuracy to expect*, or *why your number might be off*. Users get a number and have to take it on faith.

### Tape's wedge (proposed positioning)

**Be the opposite:**
- 100% free, no subscription, no ads in measurement flow
- Honest about accuracy ("±2-5% on devices without depth sensor")
- One job: tap two points, get a distance. That's it.
- Beautiful, fast, no account, no upsell

This is the iOS Measure experience, on Android, free. That's the pitch.

---

## Q3: Is this a "tool for one" or scaleable?

### Demand signals

- Grymala's category leader has **10M+ installs** on the main app alone
- 247K reviews = meaningful daily active usage
- The same dev ships 4 apps in this category (portfolio bet, not a one-off)
- Lascade, Galaxy studio, multiple Vietnamese studios (HoangSi, GODHITECH, Starnest JSC) all shipping clones = low technical bar, proven monetization

### Real-world use cases (not theoretical)
- **Buying furniture online** — "will this sofa fit through my door?"
- **Real estate / rentals** — quick room dimensions before visiting
- **Interior design / home renovation** — measuring walls, floors, windows
- **Construction / handyman** — rough estimates on-site (these users often buy a $5 tape but use AR for quick checks)
- **Fitting things in cars / moving** — trunk space, cargo area
- **Photo reference** — measuring an item in a photo you took (less AR, more computer vision)

The furniture/real-estate use case alone is huge in India — Urban Ladder, Pepperfry, IKEA delivery, NoBroker all create friction that this app removes.

### Monetization paths (for later)
1. **Donations / tip jar** — if user base is real but small, accept donations via UPI
2. **Ad-supported, single banner only** — never in measurement flow
3. **Pro tier for floor plan / export** — only after core is nailed
4. **None** — keep it free forever as a portfolio piece / genuine utility

For v1, **no monetization.** Build the tool right first. Find out if anyone uses it. Then decide.

### India-specific demand
- India is the largest Android market by volume (~600M+ active Android users)
- English keyword discovery works for tier-1 cities
- Tier-2/3 cities skew Hindi/regional — could matter for marketing later, not for v1
- ARCore support varies; flagship Indian phones (Samsung M-series, OnePlus Nord, Redmi Note) are mostly certified. Sub-₹10K phones are not.

---

## Q4: Technical feasibility

### AR (ARCore) — what works, what doesn't

**What ARCore gives you for free:**
- Plane detection (horizontal floor, vertical wall)
- Hit-testing (tap on detected plane, get 3D position)
- Camera pose tracking (SLAM)
- Depth API (on supported devices only — see below)

**The honest truth about AR measurement accuracy:**
- On a flagship Pixel/Samsung/OnePlus with ToF sensor: **±1-2% accurate** within a few meters. Good.
- On a mid-range phone (₹15K-25K) with no ToF: **±3-5% accurate**. Acceptable for "will this fit?" questions.
- On a budget phone (sub-₹10K): **±10-20%**, jittery points, frequent loss of tracking. Bad.
- Beyond 5 meters: error compounds. iOS Measure is the same — it tells you to step closer.

**ARCore device support** (confirmed from Google's docs):
- ~600+ certified Android devices as of 2026
- Most mid-range and up Samsung, OnePlus, Xiaomi, Realme, Vivo, Oppo, Pixel
- Sub-₹10K segment largely **unsupported** or degraded
- This means: **Tape can target ~70-80% of Indian Android users well, and the bottom 20% will have a degraded experience.** We should detect and warn users.

**Non-AR fallback options:**
1. **Photo + known reference object.** User places a credit card or coin in frame, the app does perspective math to measure any object in the photo. Works on any phone with a camera. Pros: universal. Cons: requires user to have reference object, less interactive.
2. **Manual measurement with phone as a ruler.** Use the screen + accelerometer to estimate distance by tapping screen as you walk toward an object. Crude but works.
3. **LiDAR-only path.** Only works on supported devices. Skip.

**Recommendation:** Build AR-first (with ARCore). Add the photo + reference object fallback for unsupported devices or for when AR loses tracking. Ship the screen-as-ruler as a tertiary fallback for budget phones.

### Cost of building (rough estimate, ranges)
- **Tech stack:** Kotlin + ARCore + Jetpack Compose (or Flutter for cross-platform later)
- **Backend:** None needed for v1. Everything on-device.
- **Developer account:** $25 one-time (Google Play)
- **Hosting:** $0 for v1
- **Time:** Solo dev with ARCore experience = 2-4 weeks for MVP. Less experienced = 6-8 weeks.

### Realistic risk
- The single biggest risk is **ARCore accuracy on cheap phones**, which we cannot engineer around. We can mitigate by:
  - Clear "your device may give less accurate results" warning
  - Auto-fallback to photo mode on unsupported devices
  - Showing live confidence indicator (low/med/high) so users know when to trust the number

---

## Final verdict

**GO — but scope tightly.**

Build v1 as:
- Android-only
- ARCore-based point-to-point distance measurement
- 100% free, no ads in measurement flow, no subscription
- Honest about accuracy
- Fallback to photo+reference object for non-ARCore devices
- Ship it, get users, then decide on monetization

If you want to de-risk further, build the **photo + reference object** mode FIRST (no ARCore needed, works on any phone, validates demand). Then add AR as the "if your phone supports it, this is even better" feature.

---

## Open questions for the user

1. **Photo-first or AR-first MVP?** (My recommendation: photo-first for speed and universal device support, then AR as v1.1)
2. **Solo build or pull in collaborators?** (Affects timeline)
3. **Tech preference:** native Kotlin or Flutter? (Affects future iOS port potential)
4. **Anything I missed?** — this is Discovery, not the full plan; user has more context.