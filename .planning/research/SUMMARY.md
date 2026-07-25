# Project Research Summary

**Project:** Gordian Android
**Domain:** iOS-to-Android parity port of a paid consumer decision app (Kotlin/Compose client, Play Billing, frozen server-side AI proxy) shipping on Google Play
**Researched:** 2026-07-25
**Confidence:** HIGH

## Executive Summary

Gordian Android is a second client of an already-proven product: the shipped iOS app is the spec, the Cloudflare Workers proxy is frozen, and everything hard (session gate, safety strikes, TOO_BIG decomposition, verdicts) already lives server-side. The client's job is UI + billing + local state. The old Android app in the repo is a usable shell — current toolchain (Kotlin 2.2.10, AGP 9.1.1, targetSdk 36), working Compose scaffold, Room, and speech input — but its product layer is a pre-evolution prototype that must be gutted: client-side Gemini calls, API-key entry, clarifying-questions flow, AI-visible copy everywhere, and the wrong applicationId. The work splits cleanly into modernize (bump the 21-month-stale AndroidX layer, delete the entire Firebase/google-services/secrets stack), build the parity spine (proxy client, mode-aware session state machine, verdict card, safety surfaces, trial/paywall/billing), and clear Android-only platform gates iOS never faced (predictive back at targetSdk 36, edge-to-edge enforcement, POST_NOTIFICATIONS, Play data safety form, subscription disclosures).

The recommended architecture is deliberately minimal and matches both the iOS design and the simplicity-first constraint: a single-activity Compose app with **no navigation library** — a sealed-interface `SessionState` rendered by one root `when(state)`, a `SessionViewModel` with SavedStateHandle and a deadline-based (not counter-based) 60s timer, an app-scoped `EntitlementManager` consulted exactly once at session start ("sessions in flight always finish"), and an outcome-typed `SessionRepository` that collapses every proxy error into a typed `Offline` fallback so the ViewModel never sees HTTP. Spike-proven code (proxy client 007, billing patterns 005) promotes nearly verbatim.

The two biggest risks are calendar and identity, not code. Calendar: the Play closed-test gate (12 testers opted in continuously for 14 days) must start early and run concurrently with development — discovered late, it adds 3+ weeks nothing can compress; and new-app submissions require targetSdk 36 from 2026-08-31, right at this project's likely ship window, so target 36 from day one. Identity: the applicationId rename to `dev.gordian.app` becomes permanent at first Play upload (even to a closed track), so the deep rename (applicationId + namespace + package + grep-zero) is the very first coding task, strictly before the early upload the closed-test clock demands. Beyond those: billing acknowledgment (unacknowledged purchases silently auto-refund) and the on-resume `queryPurchasesAsync` sweep (the entire promo-gift delivery mechanism) are non-negotiable invariants, and Android's `SpeechRecognizer` needs a restart-loop design because its aggressive endpointing will otherwise cut users off mid-thought — the highest-variance parity feature.

## Key Findings

### Recommended Stack

Keep the proven toolchain untouched (Kotlin 2.2.10, AGP 9.1.1, KSP 2.3.5 — do not bump KSP past 2.3.6 until Kotlin moves to 2.3.x). Bump the stale AndroidX layer, add exactly three capabilities, and delete the dead Firebase-era build wiring. Full detail in STACK.md.

**Core technologies:**
- Compose BOM **2026.06.01** (from 2024.09.00): the single most important bump — Material3 1.4.0 stable, sufficient for the custom Art Deco theme
- Play Billing **billing-ktx 9.1.0**: promote from debug-only 8.0.0; all spike-005 patterns carry over unchanged; shipping 9.x now avoids the next forced migration (Play deadline: 8+ by 2026-08-31)
- Room **2.8.4**: decision-log persistence (schema v2, one row per session)
- DataStore Preferences **1.2.1** (new): trial anchor + device UUID + small flags — not SharedPreferences
- WorkManager **2.11.2** (new): 3-day follow-up notification — not AlarmManager (exact alarms need Play policy declarations for zero product benefit at 72h granularity)
- OkHttp **4.12.0** (final 4.x; defer 5.x migration) + Moshi 1.15.2 codegen: proxy transport, spike-proven
- Platform `SpeechRecognizer`: zero-dependency speech input, already in the old app

**Delete outright:** firebase-ai, firebase-appcheck, google-services plugin + json, secrets-gradle-plugin (`.env` key wiring — "no client keys" is a hard rule), material-icons-extended, all dead template deps. No navigation library.

### Expected Features

Parity is locked: iOS is both the floor and the ceiling. There are no P2/P3 features — everything below is required for the Play release. Full build/adapt/delete inventory in FEATURES.md.

**Must have (the product):**
- Proxy client (`/v1/session-plan`, `/v1/verdict`) with persisted `X-Device-ID` — blocks everything
- Mode-aware session flow: BINARY / YES_NO / SENSITIVE / TOO_BIG / NOT_A_DECISION / LOCKED; custom option buttons from the plan
- Unified verdict card (decision + WHY + NEXT STEP + disclaimer)
- Safety surfaces: refusal screen (self-harm → supportive US-988 path, never penalized; harm/illegal → warning), lockout screen mirroring the server ladder (3 warnings → 5m/30m/24h)
- TOO_BIG knot decomposition UI
- 7-day device-anchored trial → paywall ($4.99/mo, $29.99/yr via ONE sub product + two base plans; $69.99 lifetime one-time product) → billing plumbing (acknowledge within 3 days, PENDING handling, resume sweep) → badges → redeem-code flow
- Logs with acted-on stats (new session-level Room schema), 3-day follow-up notification, Guides at iOS parity
- Speech input (adapt existing `VoiceRecognizer`), AI-invisible copy sweep (every old string violates the rule — blanket replacement)

**Must have (Android table stakes iOS never faced):**
- Real back navigation + predictive back (targetSdk 36 no longer calls `onBackPressed` — old app has zero back handling)
- Edge-to-edge correctness under API 35+/36 enforcement; IME insets on input screens
- POST_NOTIFICATIONS runtime prompt (contextual, not at cold launch) + one notification channel
- Play data safety form (mic/voice, user-generated content to proxy, device ID), subscription disclosures on the paywall, manage-subscription deep link, restore-purchases affordance
- Adaptive icon + monochrome layer + dark splash; appId rename before ANY upload; closed-test gate (12 testers/14 days)

**Anti-features (deliberate):** Material You/dynamic color, widgets, shortcuts, tablet/Wear, freemium meters, reviving old-app extras (topic presets, clarifying step, per-question logging).

### Architecture Approach

Single-activity Compose with a sealed-interface state machine as the only navigation source — HOME → PREPARING → SESSION → VERDICT is a state machine, not a back stack, and REFUSAL/LOCKOUT/TOO_BIG/PAYWALL are server/entitlement-driven branches. `BackHandler` semantics are product decisions per state (mid-session back = confirm-abort). Manual DI in `GordianApp` (no Hilt — ~6 singletons, 3 ViewModels). Feature-first packaging for the session; layered for shared services. Full detail in ARCHITECTURE.md.

**Major components:**
1. `SessionViewModel` — sealed `SessionState` machine, SavedStateHandle process-death snapshot, deadline-timestamp 60s timer (never a decrementing counter)
2. `SessionRepository` — maps every proxy response and error to typed `PlanOutcome`/`VerdictOutcome`; owns all offline-fallback content; ViewModel never sees HTTP
3. `ProxyClient` — spike-007 OkHttp+Moshi code promoted verbatim; `X-Device-ID` interceptor; 60s call timeout
4. `EntitlementManager` — app-scoped singleton combining billing purchases + trial anchor into `StateFlow<Entitlement>`; read once at `startSession()`, never reactively inside the session
5. `BillingClientWrapper` — connect, SUBS+INAPP `queryPurchasesAsync` on every resume AND in listener, acknowledge in both paths
6. `DecisionLogRepository` (Room v2, per-session rows, DAO aggregates for acted-on stats) + `TrialStore`/`DeviceIdentityStore` (DataStore) + `SpeechCoordinator` (UI-adjacent restart-loop wrapper)

### Critical Pitfalls

Top 5 of 10 (full list with recovery strategies in PITFALLS.md):

1. **Unacknowledged purchases auto-refund in 3 days** (3 minutes for license testers — that's the canary). Acknowledge in BOTH the listener and the resume sweep; grant only on PURCHASED, never PENDING.
2. **Out-of-app promo redemptions never processed** — gift codes redeem in the Play Store app while Gordian is closed; listener-only billing means the gift "didn't work" and then auto-refunds. The on-resume SUBS+INAPP sweep is a product feature, not hygiene.
3. **12-testers/14-days closed-test gate treated as a checkbox** — the single largest schedule risk. Confirm account status now; upload the earliest non-crashing build; recruit 13–15 testers; run the clock concurrently with development. The early upload also unblocks billing-product creation.
4. **applicationId rename done shallowly or too late** — permanent at first upload; rename applicationId AND namespace AND Kotlin packages, grep-zero the old string, before the early upload pitfall 3 demands. Note the debug `applicationIdSuffix` breaks billing testing.
5. **SpeechRecognizer endpointing kills mid-thought pauses** — 1–2s of silence triggers timeout; silence-length extras are widely ignored; AOSP emulator images have no recognizer at all. Design a debounced accumulate-and-restart loop; keep typed input first-class; test on a Play-image emulator + one physical device.

Also load-bearing: edge-to-edge/insets as scaffold-level foundation (retrofitting per-screen is the expensive path), predictive back verified on an Android 16 image, data safety form filled from the final merged manifest, and irreversible Play Console choices (Free app, Google-generated signing key, correct ID) made from a checklist.

## Implications for Roadmap

Based on research, suggested phase structure (8 phases; ARCHITECTURE.md's build order merged with PITFALLS.md's phase mapping):

### Phase 1: Foundation — rename, modernize, delete
**Rationale:** The rename touches every file (do it before code multiplies the diff) and must precede any Play upload; the dependency bumps and Firebase deletion de-risk everything downstream.
**Delivers:** applicationId + namespace + packages → `dev.gordian.app` (grep-zero exit criterion); targetSdk 36 confirmed; Compose BOM 2026.06.01 + Room/DataStore/WorkManager/Billing dependency delta; Firebase/google-services/secrets-plugin deleted; `GordianApp` composition root; `DeviceIdentityStore` + trial-anchor slot in DataStore; theme carry-over; manifest permission audit.
**Addresses:** appId rename, manifest hygiene (FEATURES Part 2).
**Avoids:** Pitfall 5 (shallow/late rename), target-API deadline, Pitfall 3 groundwork (clean manifest for the data safety form).

### Phase 2: Play Console setup + first closed-test upload (early, runs in parallel)
**Rationale:** Starts the 14-day clock immediately and unblocks billing-product creation; purely operator + packaging work, so it overlaps later phases.
**Delivers:** Operator confirms account age/type; app created as Free, ID `dev.gordian.app`, Google-generated signing key; earliest non-crashing AAB on the closed track; 13–15 testers recruited and opted in; clock start date recorded; billing products (`plus` with `monthly`/`annual` base plans; `lifetime` one-time) created after upload.
**Avoids:** Pitfalls 4 and 10 (the two calendar/permanence traps).

### Phase 3: Proxy layer
**Rationale:** Pure JVM-testable on the headless Intel-Mac toolchain; proves the contract in this codebase before any UI depends on it.
**Delivers:** `ProxyClient` + Moshi models promoted from spike 007; `X-Device-ID` interceptor; `SessionRepository` with full `PlanOutcome`/`VerdictOutcome` mapping and offline-fallback content; unit tests (fresh UUIDs in tests only — never rotate the production UUID).
**Uses:** OkHttp 4.12.0, Moshi codegen, DataStore device identity from Phase 1.

### Phase 4: Session state machine + core screens
**Rationale:** The product core; needs only the repository. Insets and back semantics are foundation here, not polish.
**Delivers:** `SessionState` sealed interface; `SessionViewModel` (SavedStateHandle snapshot, deadline timer); HOME → PREPARING → SESSION → VERDICT screens with `AnimatedContent`; `BackHandler` semantics per state (mid-session confirm-abort, verified on an Android 16 image); scaffold-level edge-to-edge/inset strategy + `imePadding`; screenshot sign-off gate (gesture/3-button/cutout/IME matrix, 2.0x font scale).
**Avoids:** Pitfalls 6 (edge-to-edge) and 7 (predictive back); anti-patterns 1–3 (nav-library routes, counter timer, god ViewModel).

### Phase 5: Safety + TOO_BIG surfaces
**Rationale:** Pure additions to the sealed class; the repository outcomes from Phase 3 already drive them.
**Delivers:** Refusal screen (988 copy, mirrored from server — never invented), Lockout screen (ladder countdown from `lockout.until`), TOO_BIG knots UI, NOT_A_DECISION surface.

### Phase 6: Persistence, Logs, and the follow-up loop
**Rationale:** Needs the verdict shape finalized in Phases 4–5; independent of billing.
**Delivers:** Room v2 per-session entity + DAO aggregate flows; log write at verdict (offline-flagged); Logs tab with acted-on stats; WorkManager 3-day follow-up notification + contextual POST_NOTIFICATIONS prompt + channel; `exportSchema = true` committed before release.

### Phase 7: Monetization
**Rationale:** Last of the core because it's the only phase blocked on Play Console products and signed-in Play-image testing (unblocked by Phase 2); the app is fully exercisable end-to-end after Phase 5 with entitlement stubbed, keeping Play off the UI-sign-off critical path.
**Delivers:** `TrialStore` activation; `BillingClientWrapper` (acknowledge in listener AND sweep; PENDING handling; SUBS+INAPP on every resume); `EntitlementManager`; one-shot gate in `startSession()`; paywall with Play-compliant disclosures (price, period, auto-renew, app-managed free week adjacent to the buy button); manage-subscription deep link; restore affordance; badges; redeem flow.
**Avoids:** Pitfalls 1, 2, 9; anti-pattern 6 (two-product iOS port). Success criteria: license-tester purchase survives 10+ min and renews; kill-app promo redemption entitles on next open; uninstall/reinstall restores entitlement.

### Phase 8: Speech polish, copy sweep, and ship
**Rationale:** Speech is the highest-variance parity feature and deserves its own device-testing loop after the typed flow works; the copy sweep must be a last pass over every finished screen; publishing artifacts depend on the final manifest and permission set.
**Delivers:** `SpeechCoordinator` restart-loop formalization (debounced accumulate-and-restart, `isRecognitionAvailable()` guard, typed path first-class), tested on Play-image emulator + physical device; Guides content at parity; full AI-invisible copy sweep (checklist, screen-by-screen); icon/monochrome/splash; data safety form from `aapt dump permissions` + privacy policy cross-check; listing assets from the Android build; paywall-copy compliance pass; production-access application once the 14-day clock (running since Phase 2) is satisfied.
**Avoids:** Pitfalls 3 and 8.

### Phase Ordering Rationale

- **Rename before upload, upload before billing:** Pitfall 5 → Phase 1; Pitfall 4's clock and the billing-product prerequisite → Phase 2 immediately after, overlapping everything else.
- **Dependency chain from FEATURES.md:** device ID → proxy → session modes → verdict → logs/notification; billing → paywall → badges/redeem. Navigation/back-stack and insets before screens (retrofitting is a rewrite).
- **Play-Console independence:** entitlement stubbed to "entitled" through Phase 5 keeps UI sign-off (the project's screenshot-approval rule) unblocked by console setup.
- **Cross-cutting gates last:** the copy sweep and data safety form are only meaningful against finished screens and a final manifest.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 7 (Monetization):** real Play Console product/promo setup vs spike assumptions — promo-code promotion UI noted as unconfirmed in spike 005; one-time-product codes are auto-generated only (500/quarter); real-cadence renewal behavior masked by accelerated test clocks.
- **Phase 8 (Speech):** SpeechRecognizer endpointing is device/OEM/Google-app-version variant by nature; restart-loop tuning needs an empirical device loop, not docs.
- **Phase 1/7 (Device-identity persistence decision):** must be decided before trial code lands — see Gaps below; changing it post-launch resets everyone.

Phases with standard patterns (skip research-phase):
- **Phase 3 (Proxy):** contract frozen and 4/4 live-verified; promotion of proven spike code.
- **Phase 4–6 (State machine, safety surfaces, Room/WorkManager):** official, well-documented Jetpack patterns; architecture doc already specifies the exact shapes.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified against official release pages 2026-07-25; toolchain spike-proven in-repo. One MEDIUM: KSP 2.3.7+ / Kotlin 2.3 coupling read from release notes, untested — but the recommendation is to hold KSP anyway |
| Features | HIGH | Old-app delta read directly from source; parity set from PROJECT.md + live-verified spikes. Play policy specifics MEDIUM-HIGH (official docs, phrasing interpretations) |
| Architecture | HIGH | Official Compose/ViewModel/SavedStateHandle guidance; proxy + billing facts spike-proven; Nav3/retain status web-verified |
| Pitfalls | HIGH | Play policy/billing facts from current official docs; SpeechRecognizer behavior MEDIUM (inherently device-variant — flagged for phase research) |

**Overall confidence:** HIGH

### Gaps to Address

- **Device-ID persistence across reinstall — the documents disagree and it must be resolved before trial/billing phases.** FEATURES.md flags that a plain UUID dies on uninstall (trial-reset + strike-evasion loophole iOS's keychain doesn't have) and suggests Auto Backup rules or SSAID; STACK.md says leave `allowBackup` on so the DataStore trial anchor survives reinstall; ARCHITECTURE.md (Anti-Pattern 4) says accept the uninstall-wipes-DataStore limit and "do not add backup-based persistence without checking iOS behavior." Resolution path: restate the iOS device-anchoring semantics in the trial-phase plan and pick one deliberately. PITFALLS.md additionally warns against trusting the client clock alone for the 7-day window.
- **Billing library version drift across documents.** STACK.md recommends 9.1.0; ARCHITECTURE.md and PITFALLS.md were written against the spike's 8.0.0. Not a real conflict — STACK.md verifies every v8 pattern carries over — but phase plans should reference 9.1.0 (+ `enableAutoServiceReconnection()`) and not copy "8.0.0" from the architecture doc.
- **Retrofit keep-vs-drop.** STACK.md keeps Retrofit 2.12.0; ARCHITECTURE.md says plain OkHttp only for the two endpoints. Either works; recommend following ARCHITECTURE.md (drop Retrofit) since the spike proved OkHttp suffices — decide in the Phase 3 plan.
- **iOS timer background behavior.** The deadline-based timer keeps counting while backgrounded; if iOS pauses instead, a two-line variation is needed — confirm against the iOS spec in the Phase 4 plan.
- **Restore-purchases button.** FEATURES.md wants a visible button (reviewer/user expectation); PITFALLS.md says not strictly required if auto-restore works. Cheap to add — include it, decide placement in the Phase 7 plan.
- **Play account status unconfirmed.** Plan assumes the 12/14 gate applies; operator confirmation in Phase 2 is the first checklist item.

## Sources

### Primary (HIGH confidence)
- Spike findings, live-verified 2026-07-24: `.claude/skills/spike-findings-gordian-android/` — 005 (billing structure, promo mapping, resume sweep), 006 (toolchain), 007 (proxy contract, 4/4 live)
- Old-app source read directly 2026-07-25: `MainActivity.kt`, `MainViewModel.kt`, `GeminiService.kt`, `DecisionDatabase.kt`, manifest, `build.gradle.kts`
- Official Android/Play docs fetched 2026-07-25: Compose BOM mapping; Play Billing release notes + v9 migration; androidx releases (Room 2.8.4, DataStore 1.2.1, WorkManager 2.11.2, lifecycle 2.11.0, activity 1.13.0); Android 16 behavior changes (edge-to-edge, predictive back); target-API requirements; closed-testing requirements; data safety; subscriptions policy; Compose state/ViewModel/SavedStateHandle guidance; Navigation 3 stable announcement

### Secondary (MEDIUM confidence)
- KSP GitHub releases — 2.3.7+ Kotlin-2.3 target coupling (verify on first build if bumping)
- material-icons frozen at 1.7.8 — release notes + community confirmation
- RevenueCat/Adapty billing-testing guides — accelerated test clocks, error codes
- Community reports on SpeechRecognizer endpointing/timeouts — inherently device-variant; flagged for phase research
- Process-death/`retain{}` community write-ups — consistent with official docs

---
*Research completed: 2026-07-25*
*Ready for roadmap: yes*
