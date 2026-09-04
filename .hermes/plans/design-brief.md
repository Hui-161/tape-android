# Claude Design Brief — Tape (AR Measurement App)

> Paste this entire file into claude-design. It includes the brief, the filters to keep claude-design on-rails, and the artifact expectations.

---

## Project: Tape

**One sentence:** A free, ad-free AR measurement app for Android. Point camera, tap two points, get the distance. That's the entire pitch.

**Category:** Utility / Measurement / AR
**Platform:** Android (v1)
**Inspired by:** Apple iOS Measure app — but free, with no paywalls, no dark patterns, and a clean UX. The wedge is honesty (about accuracy) and zero friction (no ads in measurement flow).
**Existing project:** `/Users/akshaysharma/Downloads/Work/Projects/Tape` (Flutter, fresh, no existing code yet — empty except for README and this brief)
**Reference project (for visual vibe, NOT to clone):** `/Users/akshaysharma/Downloads/Work/Projects/barely` — Barely is a PWA daily-tracker with a deliberately minimal, kind, calm aesthetic. Tape should feel like a sibling — same restraint, same warmth, but more precise because it's a measurement tool.

---

## The deliverable

Produce a **single self-contained HTML design artifact** (one file, embedded CSS, embedded JS if needed). The artifact should be a high-fidelity interactive prototype that exercises every screen and key state of the v1 flow.

### Screens / states to design

The prototype must cover these surfaces. Each is a real state the user will encounter. Don't skip any.

1. **First-launch / permission screen**
   - "Welcome to Tape. Let's measure something."
   - One CTA: "Continue"
   - On continue: triggers camera permission, with a plain explanation of why ("to measure what your camera sees")

2. **Camera permission denied**
   - Honest copy: "Tape needs your camera to measure. You can grant it in Settings."
   - One CTA: "Open Settings" (deep link to app settings on real device)

3. **AR not supported / device unsupported**
   - Honest copy: "Your phone doesn't support AR measurement."
   - Explain briefly why (no depth sensor, motion sensors, or ARCore)
   - Show which features still work: photo mode (coming soon v1.1 — show as locked card)
   - One CTA: "Got it" → returns to a no-AR home screen

4. **AR measurement — empty state**
   - Full-screen camera view (use a placeholder gray/dark gradient for the prototype — no real camera)
   - Subtle grid overlay on detected planes (very subtle, mostly invisible until tracking is locked)
   - Top: minimal app title + close button
   - Bottom: "Move your phone slowly to detect a surface" hint (auto-hides once tracking is locked)
   - Center crosshair / tap reticle (subtle, low-opacity until user taps)

5. **AR measurement — first tap**
   - Same as above, but a small ring/dot is drawn at the tap location
   - Hint updates: "Tap a second point to measure"

6. **AR measurement — second tap**
   - Two dots drawn, a line connects them, distance label appears mid-line (e.g. "1.42 m" or "4'8"")
   - Confidence badge near the measurement: "High accuracy" (green), "Medium" (amber), "Low" (red) — pick the right one based on prototype scenario
   - Bottom action sheet appears:
     - "Save" (with optional label input)
     - "Switch units" (cm / m / in / ft — tap to cycle)
     - "Undo" (remove last point)
     - "Reset" (start over)
     - "Share" (icon)

7. **Saved measurement confirmation**
   - Small toast: "Saved as 'Living room door'"
   - Auto-dismisses in 2 seconds

8. **Measurements list (history)**
   - Title: "Saved"
   - Each row: label + value + unit + relative date ("Today", "Yesterday", "3 days ago")
   - Tap row → opens detail view with the value, the date, and options: rename / delete / share
   - Empty state: "No measurements yet. Tap two points in the camera to start."

9. **Settings (minimal)**
   - Default unit (cm / m / in / ft)
   - Theme (system / light / dark)
   - About (Tape, version, MIT license, link to source)
   - That's it. No analytics opt-in. No notification settings. No account.

10. **Home / launch screen (for unsupported devices or PWA companion)**
    - App title
    - Single CTA: "Start measuring"
    - Below: tiny "Why Tape?" three-bullet explainer (free, no ads, no account)
    - Bottom: tiny install prompt footer (for the PWA companion version) — "Add to home screen" hint

### Variations to produce

For the **AR measurement screen** (state 6 above), produce **3 visual variations** side by side:

- **Variation A — Minimal:** only the measurement line + label + confidence badge. No grid. Clean.
- **Variation B — Ambient grid:** subtle plane grid in background, measurement line on top. More spatial awareness.
- **Variation C — Confidence-forward:** measurement line + label + a large confidence badge (e.g. "±2%") that visually dominates. Trust-forward.

Let the user pick which one feels right for the brand.

---

## Visual / aesthetic direction

- **Type:** Sans-serif, modern, with strong numeric treatment (measurements should look like measurements, not body text — tabular figures, monospace or near-monospace numerals). Lean toward something like Inter, IBM Plex Sans, or Geist. NOT a default system font.
- **Color:** Restrained. One primary accent (suggest a warm orange/amber — Tape = a tool, not a tech product; warm = human). Otherwise: deep neutrals, plenty of dark mode, high-contrast text. Avoid blue/indigo gradients — those scream "generic SaaS."
- **Shape:** Generous spacing. Subtle radius (8-12px on cards, 4-6px on small elements). No oversized pill buttons. No glassmorphism.
- **Motion:** Smooth, restrained. Measurement line draws in over 200ms. Confidence badge fades in. No bouncing, no celebration animations. This is a precision tool, not a game.
- **Iconography:** Use existing icon sets (Phosphor, Lucide, Tabler) — match Barely's vibe. No custom illustrated icons.
- **Dark mode:** Default. Measurement apps are used everywhere including in dim rooms / outdoors — dark UI is the right baseline. Light mode as a setting, not the default.

### Borrow from Barely (the sibling project)

- The same warm, minimal, kind tone
- The same restrained color palette (look at `barely/public` and `barely/src` for actual color values before designing)
- The same gentle, low-density layout
- The same "no guilt, no nag" feel

Do NOT copy Barely's specific components or layout — Tape is a different app. Just match the philosophy.

---

## Filters (hard constraints — do not violate)

1. **No fake dashboards / fake metrics.** This is a measurement tool, not a SaaS product. No charts, no analytics, no "your productivity score."
2. **No emoji in UI.** No 🎉 📏 ✨. Use real iconography or typography.
3. **No tech-gradient backgrounds.** No purple-to-blue. No glassmorphism on hero panels.
4. **No feature-tile grid.** The "Why Tape?" section is 3 bullets, not 3 icon-cards.
5. **No subscription / pricing page.** Free is free. Don't design a paywall.
6. **No account / sign-up screens.** There is no account.
7. **No tutorial walls.** Inline hints only. No "Swipe to learn" walkthrough.
8. **No left-border accent rails on cards.** Use real hierarchy (scale, weight, spacing) instead.
9. **Dark mode is the default. Light mode is the alt.** Not the other way around.
10. **No iOS-specific UI elements.** This is Android. Material 3 influence is fine; iOS HIG is not.
11. **Do not invent features outside the v1 scope.** No room scanner, no person height, no LiDAR. Save those for later.
12. **Tabular numerals on all measurement values.** They should look like a ruler, not like running text.

---

## What's deliberately out of scope

These are NOT in v1 and should NOT appear in the design:

- Room scanning / floor plan mode
- Person height estimation
- Photo + reference object measurement (v1.1)
- Cloud sync / account
- Subscription / premium features
- In-app purchases
- Notification settings
- Analytics opt-in screens

If the design accidentally includes any of these, remove them.

---

## Format expectations

- Single self-contained HTML file, embedded CSS, minimal JS for state toggling
- Mobile viewport (375×812 baseline, but responsive)
- Include a "Tweaks" panel (theme toggle, density toggle, accent color picker) so we can iterate live
- Use real placeholder content (real measurement values like "1.42 m", not "Lorem ipsum")
- Verify in the browser before declaring done — open the file, check console, screenshot the primary viewport

---

## After you finish

- Write the file to `/Users/akshaysharma/Downloads/Work/Projects/Tape/.hermes/plans/design-prototype.html`
- Report back with the file path, what each variation covers, and what you'd recommend as the strongest direction
- Don't ship a generic SaaS layout. The bar is high.