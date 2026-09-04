# Tape — Pre-Execution Summary

**Date:** 2026-08-19
**Status:** Decisions locked. Design brief ready. Awaiting design before code.

---

## What we decided

| Decision | Choice | Rationale |
|---|---|---|
| App name | **Tape** | India-friendly, verb+noun, low Play Store saturation at top, physical-object association |
| Target user | **India-first, Android-first** | Largest Android market; gap is real (no native Measure app) |
| MVP scope | **AR point-to-point only** | One job, done well. No room scan, no height, no photo ref. |
| Build path | **AR first, fallback later** | Your call. Photo + ref object + screen ruler queued for v1.1+. |
| Build team | **Solo (you + Marc)** | No collaborators needed |
| Tech stack | **Flutter** (your call — see note below) | |
| Monetization | **None in v1.** Free forever. | Win by being the opposite of the category |
| Open source | **MIT license** | Free to use, fork, learn from |

---

## Important corrections from Marc (please read before we proceed)

### 1. PWA can't do AR on Android

You asked about building Tape as a PWA (like Barely) to bypass the app stores. **This works for the photo + reference object fallback mode**, but it does NOT work for AR measurement on Android. Here's why:

- ARCore is a native Android SDK. There is no WebAR implementation that gives you plane detection + depth on Android Chrome.
- WebXR exists but Android support is patchy (Samsung Internet = nothing, Chrome Android = inconsistent).
- iOS Safari has the best WebXR support, ironically — so a PWA could be iOS-friendly, but your primary market (Android) would be the worst-supported.

**Recommended path:** ship AR as a Flutter Android app first. **Then** add a PWA companion later for the photo + reference fallback mode (which works in any browser). The PWA can also be the install-prompt destination — "for the full AR experience, install the native app, otherwise try the web version."

So we're not abandoning the Barely-style philosophy — we're doing it in two phases. Native first, PWA second.

### 2. Flutter is risky for AR-heavy apps

Flutter has AR plugins (`ar_flutter_plugin`, `arcore_flutter_plugin`), but they're community-maintained, lag ARCore releases, and have known accuracy/stability issues vs native Kotlin.

For a measurement app where **accuracy IS the product**, betting on third-party AR plugins is a real risk.

**You have three options:**

| Option | Pro | Con |
|---|---|---|
| **A. Native Kotlin Android (recommended)** | Direct ARCore access, best accuracy, no plugin risk | No iOS without rewriting later |
| **B. Flutter (your original choice)** | Cross-platform story, easier UI, you're familiar | AR plugin risk, accuracy ceiling |
| **C. Native first, Flutter later** | Best of both — AR v1 in Kotlin, rewrite to Flutter for v2 if cross-platform matters | Two rewrites |

**My strong recommendation: Option A or C.** Option B is what you asked for, but the AR plugin risk is real and I want you to make the call with full info.

We can revisit once the design prototype is in. You can still pick Flutter — just knowing the tradeoff.

### 3. Deployment answer

For the future PWA / landing page / backend:

- **Vercel** ← my pick. Free tier, best DX, instant deploys from git, perfect for static + serverless.
- Cloudflare Pages — same DX, slightly faster globally. Also good.
- Netlify — fine but Vercel wins for React/Next.js.
- Hetzner / Hostinger — overkill. Don't pay for capacity you don't need.

**Final pick: Vercel.** Save the dollars.

---

## What's ready right now

1. **README.md** — `/Users/akshaysharma/Downloads/Work/Projects/Tape/README.md`
   - Why this exists, what it does, design principles, roadmap, use cases
2. **Discovery document** — `/Users/akshaysharma/Downloads/Work/Projects/Tape/.hermes/plans/discovery.md`
   - Full Discovery research (Q1-Q4) with data and verdict
3. **Design brief for Claude Design** — `/Users/akshaysharma/Downloads/Work/Projects/Tape/.hermes/plans/design-brief.md`
   - Paste this into claude-design to get the design prototype
4. **This summary** — `/Users/akshaysharma/Downloads/Work/Projects/Tape/.hermes/plans/pre-execution.md`

---

## Next steps

1. **You:** decide on Option A / B / C for tech stack (Kotlin / Flutter / Kotlin then Flutter)
2. **You:** hand the design brief to claude-design, get the HTML prototype back
3. **Marc:** review the design with you, lock the visual direction
4. **Marc:** write the execution plan based on the locked design + tech stack decision
5. **Marc:** scaffold the project, build the first vertical slice (camera + AR + first measurement)
6. **Marc:** ship v1

---

## Open questions you might want to answer

- Package name for Play Store? (`com.yourname.tape` or something more brand-y?)
- App icon — do you have one in mind or want me to suggest one?
- Any specific colors you want to lock (or trust me to pick)?