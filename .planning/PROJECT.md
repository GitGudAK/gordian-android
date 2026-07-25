# Gordian Android

## What This Is

The Android version of Gordian — the shipped iOS decision app (iOS 1.0 in App Store review,
2026-07). Gordian is a 60-second decision-paralysis-bypass tool: describe a dilemma, answer
rapid-fire gut questions against a sixty-second clock, get a straight answer (decision, why,
one next step). This project modernizes the original Android app (repo root, pre-evolution)
to **exact parity with the iOS product** and ships it on Google Play.

**Parity means:** same screens, same Art Deco gold/dark design language, same product rules,
same server proxy, same economics. Android is a second client of an already-proven product,
not a reinterpretation.

## Core Value

A user who has seen Gordian on a friend's iPhone installs it on Android and gets the
identical product: AI-always sessions through the proxy, the same verdict experience,
the same paid deal. Nothing less, nothing visibly "Android-flavored."

## Requirements

### Validated (existing code / shipped elsewhere)

- ✓ Old Android app builds and boots (spike 006) — Compose scaffold, Room DB, speech input exist
- ✓ Proxy contract speaks Kotlin verbatim (spike 007) — zero server changes needed
- ✓ Play Billing maps the full iOS entitlement set (spike 005) — one sub product + base plans + lifetime + promo-code gifts
- ✓ Product rules proven on iOS: AI-always, graduated safety, TOO_BIG knots, trial-then-paid

### Active

- [ ] Session flow at iOS parity: home → plan (all modes) → 60s session → verdict card (WHY / NEXT STEP / disclaimer)
- [ ] Safety surfaces: refusal screen (988 line for self-harm), lockout screen with server ladder copy
- [ ] TOO_BIG knot decomposition UI
- [ ] Monetization: 7-day device-anchored trial, paywall, Play Billing (plus: monthly+annual base plans; lifetime one-time), membership badges, redeem flow
- [ ] Logs (history + acted-on stats) and Guides tabs at parity
- [ ] AI-invisible copy everywhere (old app's copy must be fully replaced)
- [ ] applicationId renamed to `dev.gordian.app` (matches iOS bundle ID; permanent after publish)
- [ ] Play Store listing + data safety form + closed-test path to production

### Out of Scope

- Any iOS or proxy changes — server is shared and frozen from this repo's perspective
- Material You / dynamic color reinterpretation — explicit user decision: exactly like iOS
- Tablets, Wear OS, widgets — phone-first like iOS (iPhone-only)
- Freemium/meters — Gordian is paid; free week then charged (business model locked)

## Context

- Standalone private repo (GitGudAK/gordian-android); NO iOS/proxy crossover artifacts.
- Everything hard lives server-side already: `https://gordian-proxy.gordian-app.workers.dev`
  (session gate, safety strikes, TOO_BIG, verdicts). Client is UI + billing + local state.
- Dev environment: Intel Mac, fully headless (brew JDK 21, Gradle 9.6.1, SDK at
  ~/Library/Android/sdk, emulator recipe in spike findings). No Android Studio.
- Spike findings skill auto-loads: `.claude/skills/spike-findings-gordian-android/`.
- iOS reference: the iOS app's screens/copy are the spec. Where a plan needs exact copy or
  layout values, they are re-stated in that plan (no reading the iOS repo from here).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Exact iOS parity (design + product) | User decision 2026-07-24: "It should be exactly like the iOS app" | — Pending |
| applicationId → `dev.gordian.app` | Matches iOS bundle; last chance to shed AI-Studio ID before Play publish makes it permanent | — Pending |
| Standalone repo, no crossover | User rule at repo creation | ✓ Held since init |
| One sub product + two base plans (not two products) | Play-native structure (spike 005) | — Pending |
| Compose stays (no rewrite of stack) | Old app is already Kotlin/Compose on current AGP/Kotlin; modernize in place | — Pending |

## Constraints

- **No AI mentions** in any user-facing copy (product rule from iOS).
- **Pricing locked:** $4.99/mo, $29.99/yr, $69.99 lifetime; 7-day in-app trial; no meters.
- **Safety ladder is server truth:** 3 warnings then 5min/30min/24h; self-harm always
  supportive (US 988), never penalized. Client mirrors, never invents.
- **Play publishing gate (assumed):** plan as if the 12-testers/14-days closed test applies;
  operator confirms account age in Play Console.
- **UI approval rule:** emulator screenshot + user sign-off BEFORE committing UI changes.

---
*Last updated: 2026-07-24 after initialization*

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition:**
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone:**
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state
