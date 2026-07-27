# Gordian Android — v1 Requirements

Parity is both floor and ceiling: the iOS app is the spec. Derived from PROJECT.md,
research (STACK/FEATURES/ARCHITECTURE/PITFALLS), and spike findings 005–007.

**Conflict resolutions (against iOS semantics, decided 2026-07-24):**
- Trial/device-ID reinstall persistence: match iOS — accept that uninstall wipes the trial anchor
  and device ID (iOS ships this way as a documented non-blocker; server-side hardening via
  device first-seen date stays deferred for both platforms). No backup-rules gymnastics.
- HTTP layer: plain OkHttp + Moshi (spike-proven); Retrofit dropped.
- Billing library: 9.1.0 (v8 patterns carry over per STACK verification).
- Restore-purchases button: included (cheap, reviewer-friendly).

## v1 Requirements

### Foundation
- [ ] **FND-01**: applicationId AND namespace renamed to `dev.gordian.app` repo-wide (grep-zero for `com.aistudio.gordian.ovthnk` and `com.example`) before any Play upload
- [ ] **FND-02**: Firebase/google-services/secrets layers removed; Compose BOM 2026.06.01, Room 2.8.4, billing-ktx 9.1.0, DataStore 1.2.1, WorkManager 2.11.2 in place; targetSdk 36
- [ ] **FND-03**: Edge-to-edge + predictive back work correctly on the dark/gold scaffold at API 36 (no visual regressions, back semantics owned by session state)

### Session
- [ ] **SES-01**: User can enter a dilemma by typing (always) or speech (optional, restart-loop tolerant) on the parity home screen
- [ ] **SES-02**: App calls `POST /v1/session-plan` with a persisted random-UUID `X-Device-ID`; all six modes decode (BINARY / YES_NO / SENSITIVE / TOO_BIG / NOT_A_DECISION / LOCKED)
- [ ] **SES-03**: 60-second session at parity: rapid-fire questions, option buttons labeled with the user's own options, deadline-timestamp timer that survives process death
- [ ] **SES-04**: Verdict from `POST /v1/verdict` rendered in the unified card (decision, WHY, NEXT STEP, disclaimer) at iOS design parity
- [ ] **SES-05**: Any proxy failure resolves to a graceful local fallback — a session never dead-ends
- [ ] **SES-06**: TOO_BIG shows the decomposed knots; user picks one to run as a new dilemma
- [ ] **SES-07**: NOT_A_DECISION bounces with reframe guidance at parity

### Safety
- [ ] **SAF-01**: Refusal screen at parity; US-988 crisis line shown only when risk is `self_harm`; supportive, never punitive
- [ ] **SAF-02**: Lockout screen mirrors the server ladder (strikes, until-timestamp countdown); LOCKED short-circuit respected on session start
- [ ] **SAF-03**: Client renders only server-provided safety state — no client-side classification

### Monetization
- [ ] **MON-01**: 7-day free trial anchored at first launch in DataStore; FREE WEEK pill on home (iOS-parity semantics)
- [ ] **MON-02**: Paywall replaces home when trial ends unpurchased; a session in flight always finishes; Logs/Guides stay accessible
- [ ] **MON-03**: Play Billing 9.1.0: one subscription product (base plans monthly $4.99 / annual $29.99) + lifetime one-time $69.99; purchase flow with acknowledgment in listener AND on-resume sweep; PENDING handled
- [ ] **MON-04**: On-resume `queryPurchasesAsync(SUBS+INAPP)` sweep delivers out-of-app promo-code redemptions (the gift mechanism)
- [ ] **MON-05**: Membership badges (LIFETIME knot glyph / PREMIUM sparkles, gold sheen) at parity; restore-purchases and manage-subscription affordances; redeem-code entry point
- [ ] **MON-06**: Paywall/trial disclosure copy meets Play subscription policy (price, renewal, cancel path)

### Data & Retention
- [ ] **DAT-01**: Completed sessions persist locally (Room: decision/sentiment/analysis/probe, answers, actedOn)
- [ ] **DAT-02**: Logs tab shows history + acted-on stats at parity
- [ ] **DAT-03**: 3-day follow-up notification (WorkManager) asks "did you act on it?", answerable from the notification; POST_NOTIFICATIONS requested contextually after first verdict
- [ ] **DAT-04**: Guides tab at parity

### Copy & Legal
- [ ] **CPY-01**: AI-invisible copy everywhere; every old-app string replaced (no "cognitive analysis engine", no AI mentions)
- [ ] **CPY-02**: Self-reflection disclaimer on verdict card; legal hedging language matches iOS

### Store
- [ ] **STO-01**: Play Console app record for `dev.gordian.app` (Free with IAPs, Google-generated signing key)
- [ ] **STO-02**: Billing products configured in Console (sub `plus` + two base plans, `lifetime`, promo promotions incl. 3-months-free trial length verified eyes-on)
- [ ] **STO-03**: Data safety form consistent with mic/speech + app-generated device ID; `uses-feature android.hardware.microphone required=false`
- [ ] **STO-04**: Closed test started with the earliest non-crashing build (12 testers / 14 days clock runs in parallel); production submission after gate clears
- [ ] **STO-05**: Store listing at parity with iOS metadata (no AI mentions); adaptive icon with monochrome layer + dark splash screen

## v2 (Deferred)

- Server-side trial hardening via device first-seen date (both platforms)
- Crisis resources beyond US 988 (localize before non-US marketing)
- Tablet layouts

## Out of Scope

- Material You / dynamic color — user decision: exactly like iOS
- Widgets, shortcuts, Wear OS — parity is the ceiling
- Freemium/meters — business model locked (paid, trial-then-charged)
- iOS or proxy changes — server frozen from this repo's perspective

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FND-01 | Phase 1 | Pending |
| FND-02 | Phase 1 | Pending |
| FND-03 | Phase 1 | Pending |
| STO-01 | Phase 2 | Pending |
| STO-02 | Phase 2 | Pending |
| STO-04 | Phase 2 | Pending |
| SES-01 | Phase 3 | Pending |
| SES-02 | Phase 3 | Pending |
| SES-03 | Phase 3 | Pending |
| SES-04 | Phase 3 | Pending |
| SES-05 | Phase 3 | Pending |
| CPY-02 | Phase 3 | Pending |
| SES-06 | Phase 4 | Pending |
| SES-07 | Phase 4 | Pending |
| SAF-01 | Phase 4 | Pending |
| SAF-02 | Phase 4 | Pending |
| SAF-03 | Phase 4 | Pending |
| DAT-01 | Phase 5 | Pending |
| DAT-02 | Phase 5 | Pending |
| DAT-03 | Phase 5 | Pending |
| DAT-04 | Phase 5 | Pending |
| MON-01 | Phase 6 | Pending |
| MON-02 | Phase 6 | Pending |
| MON-03 | Phase 6 | Pending |
| MON-04 | Phase 6 | Pending |
| MON-05 | Phase 6 | Pending |
| MON-06 | Phase 6 | Pending |
| CPY-01 | Phase 7 | Pending |
| STO-03 | Phase 7 | Pending |
| STO-05 | Phase 7 | Pending |

**Coverage:** 30/30 v1 requirements mapped. No orphans, no duplicates.
*Mapped by roadmap 2026-07-25.*
