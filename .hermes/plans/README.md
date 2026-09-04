# .hermes/plans/ — public planning artifacts

This directory contains the planning, research, and design documents for Tape.

It is **public by intent**. We ship our thinking alongside our code because:

1. **The problem is worth documenting.** Android has no built-in Measure app; users get squeezed by paid tools and ad-poisoned free ones. We want anyone considering building (or funding) a competitor to see the gap clearly.
2. **Decisions deserve rationale.** Why native Kotlin instead of Flutter? Why AR-first instead of photo-first? Why no subscriptions? The answers are in `discovery.md` and `pre-execution.md`.
3. **Open source includes the why, not just the code.** If someone forks Tape and wants to take it in a different direction, the thinking behind the original direction is right here.

## What's in here

| File | What it is |
|---|---|
| `discovery.md` | Research on the problem space: who else solves this, how, what's broken, what's missing, who pays. Q1–Q4 framework. |
| `pre-execution.md` | Locked decisions before code: tech stack, scope, risks, deployment, open questions. |
| `design-brief.md` | The brief for claude-design (or any designer) to produce the v1 visual prototype. Includes screens, filters, anti-slop rules. |
| `execution.md` | Locked v1 scope, user flows, technical context, decision log. The build references this. |

The HTML prototype files live in [`../docs/`](../docs/), not here. `.hermes/` is for planning markdown only.

## What's NOT in here

- Private notes, scratch work, session transcripts
- Things that haven't been thought through yet — those stay in private scratch until they earn a place here

## How to use this

- Reading for the first time? Start with `discovery.md`, then `pre-execution.md`, then `design-brief.md`.
- Want to fork Tape and go a different direction? Read `pre-execution.md` to see what we considered and rejected — you might find a constraint we missed.
- Want to contribute? Read `design-brief.md` for the visual direction, then check the GitHub Issues for what's open.

---

This directory is part of the [MIT-licensed](../LICENSE) Tape project. Free to read, fork, link to, and learn from.