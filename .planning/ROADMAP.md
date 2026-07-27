# Roadmap: Gordian Android

## Overview

Modernize the pre-evolution Android shell into an exact-parity second client of the shipped iOS product, and ship it on Google Play. The journey: lock the permanent identity and toolchain first (rename to `dev.gordian.app`, gut Firebase, target SDK 36, inset-aware scaffold), immediately start the Play Console 14-day closed-test clock with the earliest non-crashing build (calendar anchor — runs in parallel with all feature work), then build the product spine — proxy client, session state machine, verdict card — followed by the server-driven safety/TOO_BIG surfaces, local persistence with Logs/Guides, and monetization (the only Play-Console-blocked phase, deliberately late with entitlement stubbed until then). Finish with the AI-invisible copy sweep, data safety form, and store listing, submitting to production once the closed-test gate clears.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation & Modernization** - Rename to dev.gordian.app, gut Firebase, bump the stack, inset-aware API 36 scaffold
- [ ] **Phase 2: Play Console & Closed-Test Clock** - Console record, first upload, 12 testers opted in, billing products created (runs in parallel)
- [ ] **Phase 3: Proxy & Session Core** - Proxy client + session state machine: home → plan → 60s session → verdict card
- [ ] **Phase 4: Safety & Special Modes** - Refusal, lockout, TOO_BIG knots, NOT_A_DECISION — all server-driven
- [ ] **Phase 5: Logs, Guides & Follow-up** - Room persistence, Logs tab with acted-on stats, 3-day notification, Guides tab
- [ ] **Phase 6: Monetization** - Trial, paywall, Play Billing, badges, redeem/restore/manage flows
- [ ] **Phase 7: Copy Sweep & Ship** - AI-invisible copy pass, data safety form, listing, production submission

## Phase Details

### Phase 1: Foundation & Modernization
**Goal**: The old app is reborn as `dev.gordian.app` on the current toolchain — clean identity, clean dependencies, and an edge-to-edge dark/gold scaffold that behaves correctly at API 36
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: FND-01, FND-02, FND-03
**Success Criteria** (what must be TRUE):
  1. App builds and boots on an API 36 emulator under `dev.gordian.app`; repo-wide grep for `com.aistudio.gordian.ovthnk` and `com.example` returns zero hits
  2. Firebase/google-services/secrets-plugin wiring is gone and a release AAB assembles with Compose BOM 2026.06.01, Room 2.8.4, billing-ktx 9.1.0, DataStore 1.2.1, WorkManager 2.11.2, targetSdk 36
  3. The dark/gold scaffold renders edge-to-edge with visible (light) status-bar icons and unobscured content on gesture-nav, 3-button-nav, and cutout profiles; keyboard never covers an input field
  4. Back gesture participates in predictive back on an Android 16 image (no legacy onBackPressed interception anywhere)
**Plans**: TBD
**UI hint**: yes

### Phase 2: Play Console & Closed-Test Clock
**Goal**: The 14-day/12-tester closed-test clock is running and billing products exist in Play Console — the two calendar/permanence traps are defused early while feature work continues in parallel
**Mode:** mvp
**Depends on**: Phase 1 (rename must land before any upload — applicationId is permanent)
**Requirements**: STO-01, STO-02, STO-04
**Success Criteria** (what must be TRUE):
  1. Play Console app record exists via deliberate checklist: ID `dev.gordian.app`, Free with in-app products, Google-generated signing key, operator-confirmed account status
  2. The earliest non-crashing AAB is live on the closed track with 13+ testers opted in, and the clock start date is recorded in STATE.md
  3. Billing products are configured: subscription `plus` with monthly $4.99 / annual $29.99 base plans, `lifetime` one-time $69.99, and promo promotions (3-months-free trial length verified eyes-on)
**Plans**: TBD

### Phase 3: Proxy & Session Core
**Goal**: The core product works at iOS parity — a user types (or speaks) a dilemma, runs the 60-second rapid-fire session, and gets the unified verdict card, with the proxy contract proven in this codebase and every failure resolving gracefully
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: SES-01, SES-02, SES-03, SES-04, SES-05, CPY-02
**Success Criteria** (what must be TRUE):
  1. User can enter a dilemma by typing (always) or speech (restart-loop tolerant of thinking pauses on a Play-image emulator; typed path stays first-class) on the parity home screen
  2. All six plan modes decode from `POST /v1/session-plan` with a persisted random-UUID `X-Device-ID`, and BINARY/YES_NO run the full 60s rapid-fire flow with the user's own option labels and a deadline-timestamp timer that survives process death
  3. The verdict from `POST /v1/verdict` renders in the unified card — decision, WHY, NEXT STEP, self-reflection disclaimer — at iOS design parity
  4. With the proxy unreachable or erroring, a session still completes via local fallback plan and canned verdict — a session never dead-ends
  5. User has approved emulator screenshots of home/session/verdict (gesture/3-button/cutout, IME open) before commit
**Plans**: TBD
**UI hint**: yes

### Phase 4: Safety & Special Modes
**Goal**: Every server-driven branch of the session state machine has its parity surface — refusal, lockout, TOO_BIG decomposition, and NOT_A_DECISION — rendered strictly from server state
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: SES-06, SES-07, SAF-01, SAF-02, SAF-03
**Success Criteria** (what must be TRUE):
  1. A self-harm-flagged dilemma shows the supportive refusal screen with the US 988 line (988 only for `self_harm` risk), never punitive framing
  2. A LOCKED response shows the lockout screen mirroring the server ladder (strikes, countdown to until-timestamp), and session start short-circuits while locked
  3. A TOO_BIG plan shows the decomposed knots and picking one runs it as a new dilemma session
  4. NOT_A_DECISION bounces with reframe guidance at parity, and all safety/lockout state is rendered exclusively from server payloads (no client-side classification)
  5. User has approved emulator screenshots of refusal/lockout/TOO_BIG/NOT_A_DECISION screens before commit
**Plans**: TBD
**UI hint**: yes

### Phase 5: Logs, Guides & Follow-up
**Goal**: Decisions have a life after the verdict — sessions persist locally, the Logs tab shows history and acted-on stats, a 3-day follow-up notification closes the loop, and Guides is at parity
**Mode:** mvp
**Depends on**: Phase 3 (verdict shape finalized; independent of billing)
**Requirements**: DAT-01, DAT-02, DAT-03, DAT-04
**Success Criteria** (what must be TRUE):
  1. Completed sessions (including offline-fallback ones, flagged) persist in Room and survive kill-and-relaunch
  2. Logs tab shows session history and acted-on stats at parity, live-updating when a session completes or an acted-on answer is recorded
  3. Three days after a verdict, a notification asks "did you act on it?" and is answerable from the notification itself; POST_NOTIFICATIONS is requested contextually after the first verdict, never at cold launch
  4. Guides tab renders at iOS parity
  5. User has approved emulator screenshots of Logs and Guides before commit
**Plans**: TBD
**UI hint**: yes

### Phase 6: Monetization
**Goal**: The paid deal works end-to-end — 7-day device-anchored trial into paywall into Play Billing purchase, with gift codes, restore, badges, and policy-compliant disclosures
**Mode:** mvp
**Depends on**: Phase 2 (Console products), Phase 3 (session gate to wire into); entitlement stubbed "entitled" until this phase
**Requirements**: MON-01, MON-02, MON-03, MON-04, MON-05, MON-06
**Success Criteria** (what must be TRUE):
  1. Fresh install shows the FREE WEEK pill anchored at first launch; when the trial ends unpurchased the paywall replaces home, a session in flight always finishes, and Logs/Guides stay accessible
  2. A license-tester purchase (monthly, annual, or lifetime) survives 10+ minutes and renews — acknowledged in BOTH the purchase listener and the on-resume sweep; PENDING shows pending UI and grants nothing
  3. A promo code redeemed in the Play Store app while Gordian is killed grants entitlement on next open, and uninstall/reinstall restores a purchase without repurchase
  4. Membership badges (LIFETIME knot glyph / PREMIUM sparkles, gold sheen) render at parity, with restore-purchases, manage-subscription deep link, and redeem-code entry points reachable in-app
  5. Paywall discloses price, period, auto-renewal, and the app-managed free week adjacent to the buy button (Play subscriptions policy), and user has approved paywall/badge emulator screenshots before commit
**Plans**: TBD
**UI hint**: yes

### Phase 7: Copy Sweep & Ship
**Goal**: Every screen speaks the product's voice with zero AI mentions, the store artifacts are honest and at parity, and the app ships to production once the closed-test gate clears
**Mode:** mvp
**Depends on**: All previous phases (copy sweep and data safety form only meaningful against finished screens and final manifest)
**Requirements**: CPY-01, STO-03, STO-05
**Success Criteria** (what must be TRUE):
  1. Screen-by-screen copy audit passes: zero AI mentions and zero old-app copy remnants anywhere in the app
  2. Data safety form is filled from `aapt dump permissions` on the final release AAB, consistent with mic/speech + app-generated device ID + dilemma text to proxy; manifest declares `uses-feature android.hardware.microphone required=false`; privacy policy cross-checks
  3. Store listing is at parity with iOS metadata (no AI mentions), with adaptive icon (monochrome layer) and dark splash screen verified on-device, all listing assets captured from the Android build
  4. Production submission is made once the 14-day closed-test gate (running since Phase 2) is satisfied
  5. User has approved final icon/splash and any copy-touched screens via emulator screenshots before commit
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7. Phase 2 is operator + packaging work that runs concurrently with Phases 3-6 (its 14-day clock is the schedule anchor).

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Modernization | 0/TBD | Not started | - |
| 2. Play Console & Closed-Test Clock | 0/TBD | Not started | - |
| 3. Proxy & Session Core | 0/TBD | Not started | - |
| 4. Safety & Special Modes | 0/TBD | Not started | - |
| 5. Logs, Guides & Follow-up | 0/TBD | Not started | - |
| 6. Monetization | 0/TBD | Not started | - |
| 7. Copy Sweep & Ship | 0/TBD | Not started | - |

---
*Coverage: 30/30 v1 requirements mapped (FND 3, SES 7, SAF 3, MON 6, DAT 4, CPY 2, STO 5). No orphans, no duplicates.*
*Created: 2026-07-25 from REQUIREMENTS.md + research (ARCHITECTURE.md build order, PITFALLS.md phase mapping). Granularity: coarse.*
