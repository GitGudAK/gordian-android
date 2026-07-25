# Feature Research

**Domain:** iOS-to-Android parity port of a paid consumer decision app (Gordian)
**Researched:** 2026-07-25
**Confidence:** HIGH (old-app state: read from source; parity set: PROJECT.md + spike findings) / MEDIUM (Play policy specifics: web-verified against official docs)

**Framing:** iOS IS the spec (locked user decision). This document does not invent features. Part 1 enumerates the parity delta against the old Android app's actual source (read 2026-07-25: `MainActivity.kt` 2,046 lines, `MainViewModel.kt`, `GeminiService.kt`, `DecisionDatabase.kt`, manifest, `build.gradle.kts`). Part 2 enumerates Android-platform table stakes the iOS app never faced.

---

## Part 1: Parity Delta — Build vs Adapt vs Delete

### What the old app actually is (audit summary)

A pre-evolution prototype: 3-tab Compose app (FOCUS / INSIGHTS / CALIBRATE), client-side Gemini calls with user-entered API key, a client-invented "clarifying questions" step, hardcoded YES/NO answers, per-question Room logging, AI-visible copy everywhere ("cognitive analysis engine", "AI Analysis"), zero monetization, zero safety system, zero navigation back-stack, applicationId `com.aistudio.gordian.ovthnk`, targetSdk 36 / minSdk 24, Firebase AI + App Check + secrets-plugin build wiring.

### BUILD (missing entirely — net-new for Android)

| Feature | iOS Source of Truth | Complexity | Notes / Dependencies |
|---------|--------------------|------------|----------------------|
| Proxy client (`/v1/session-plan`, `/v1/verdict`) | Spike 007, verbatim contract | LOW | Pattern proven live 4/4. OkHttp + Moshi, `callTimeout(60s)`. Blocks everything below. |
| Persisted device identity (`X-Device-ID`) | iOS keychain-anchored UUID | LOW-MEDIUM | One UUID for life of install. Android decision needed: plain UUID dies on reinstall (trial-reset + strike-evasion loophole iOS doesn't have). Closest keychain analog: back the UUID with Auto Backup rules and/or derive from SSAID (`ANDROID_ID`, per-app-signing-key, survives reinstall). Flag for phase research. |
| Mode-aware session plan handling (BINARY / YES_NO / SENSITIVE / TOO_BIG / NOT_A_DECISION / LOCKED) | Proxy contract + iOS screens | MEDIUM | Old app has one linear flow; the mode switch is the new spine of the session feature. |
| Custom option buttons (optionA/optionB from plan) | BINARY mode | LOW | Old app hardcodes YES/NO buttons. |
| Refusal screen (SENSITIVE: `self_harm` supportive path with US 988; `harm_others`/`illegal` warning) | iOS refusal screen | MEDIUM | Copy is safety-critical; mirror server, never invent. `self_harm` never penalized. |
| Lockout screen (server ladder: 3 warnings → 5m/30m/24h, countdown from `lockout.until`) | iOS lockout screen | MEDIUM | Client mirrors `lockout.until` epoch-ms; LOCKED mode on plan response. |
| TOO_BIG knot decomposition UI (2–4 knots delivered in `questions[]`) | iOS knots screen | MEDIUM | Same `ProxySessionPlan` shape — no special payload casing (spike 007). |
| NOT_A_DECISION handling | iOS | LOW | Distinct response surface, no session. |
| Unified verdict card: decision + WHY + NEXT STEP + disclaimer | iOS verdict card | MEDIUM | Maps to `{decision, sentiment, analysis, probe}`. Replaces old sentiment-pill/"CALM–FREE"/probe layout entirely. |
| 7-day device-anchored trial gate | iOS trial | MEDIUM | Depends on device identity decision above; no meters, trial-then-paywall only. |
| Paywall (monthly $4.99 / annual $29.99 / lifetime $69.99) | iOS paywall | HIGH | Play Billing 8.0.0; ONE sub product + two base plans + separate lifetime in-app product (spike 005). Must meet Play subscription-disclosure rules (Part 2). |
| Play Billing plumbing (connect, query, purchase, acknowledge, entitlement state) | — (Android-specific mechanics) | HIGH | Acknowledge within 3 days or refund; handle PENDING purchases (lifetime via cash/pending payment methods); `queryPurchasesAsync` on launch = silent restore. |
| Membership badges | iOS badges | LOW | Pure UI over entitlement state; depends on billing. |
| Redeem-code flow | iOS redeem; spike 005 mapping | MEDIUM | Play promo codes: lifetime gifts = one-time-product promo codes; 3-month gifts = subscription promo-code trials. In-app entry hands off to Play redeem surface. |
| Logs with acted-on stats | iOS Logs tab | MEDIUM | Requires new session-level schema with `actedOn` field (old Room schema is per-question and wrong shape — see ADAPT). |
| 3-day follow-up notification ("did you act on it?") | iOS local notification | MEDIUM | WorkManager (inexact is fine, no exact-alarm permission needed) + POST_NOTIFICATIONS runtime prompt (Part 2). Writes back into acted-on stats. |
| Guides content at iOS parity | iOS Guides tab | LOW | Old tab has 4 hardcoded guides with non-iOS copy; container adapts, content replaced with iOS set verbatim. |
| Offline fallback per iOS behavior | iOS error path | MEDIUM | Every proxy error code ⇒ "use the offline fallback" (spike 007). Old app HAS local fallback questions/verdicts but with AI-flavored copy and wrong shape — concept survives, content and structure replaced to match iOS. |
| AI-invisible copy, full replacement | iOS copy | MEDIUM | Every string in the old app violates this ("cognitive analysis engine", "AI Analysis", "DECODING YOUR INNER INTUITION", API-key note). Loading states describe reflection, not generation. Treat as a sweep with checklist, not incidental. |

### ADAPT (exists in old app, reshape to iOS spec)

| Surface (old app) | Keep | Change | Complexity |
|-------------------|------|--------|------------|
| 60s session screen (timer canvas, question card, answer capture, `RapidFireAnswer` accumulation) | Timer mechanics, answer-list accumulation, Compose structure | Visuals/copy to iOS spec; options from plan (not YES/NO); answers ≤ 20 (server limit); remove pause-on-tap if iOS lacks it; remove "REFLECT" as a choice type if iOS models reflection differently | MEDIUM |
| Home scenario entry (text field + speech button) | Input + mic pattern, permission launcher | iOS layout/copy; scenario ≤ 16 KB server limit; remove preset `SimulationTopic` chips (Career Shift etc.) | LOW-MEDIUM |
| Speech input (`VoiceRecognizer.kt`, `SpeechRecognizer`-based, RMS waveform) | Whole component — genuinely reusable | Re-skin waveform to iOS look; confirm where iOS allows speech (scenario entry vs mid-session reflections) | LOW |
| Logs tab (`InsightsTabScreen`: stats card + chronology + delete) | Tab concept, Room + Flow + repository pattern | Schema redesign: per-question `DecisionLog` → per-session log with verdict fields + `actedOn`. Destructive migration acceptable (v1, no shipped users; DB already `fallbackToDestructiveMigration`). Stats become acted-on stats, not yes/no split | MEDIUM |
| Guides tab (`CalibrateTabScreen` guide cards + reader view) | Card list + expanded reader structure | Rename tab per iOS; content replaced; REMOVE embedded API-key section and "Purge Log History" unless iOS has equivalent | LOW |
| Theme (`Color.kt` gold/dark, `Theme.kt`, `Type.kt`) | Gold-on-dark Art Deco direction already matches | Align exact tokens (colors, type scale) to iOS values when plans restate them | LOW |
| Bottom navigation (3 tabs) | Tab bar pattern | Tab set/labels/icons per iOS (FOCUS/INSIGHTS/CALIBRATE → iOS names, e.g., Logs/Guides) | LOW |
| Edge-to-edge (`enableEdgeToEdge()` + `WindowInsets.safeDrawing` already present) | Yes | Verify under API 35/36 enforcement; add `imePadding` on input screens | LOW |
| Manifest + Gradle | Module layout, Compose/Room/Moshi/OkHttp deps, headless toolchain | applicationId → `dev.gordian.app` (before ANY Play upload — permanent after); add POST_NOTIFICATIONS; `billing-ktx` from `debugImplementation` → `implementation`; mic `uses-feature required=false` | LOW |

### DELETE (exists in old app, not in the iOS product)

| Surface | Why Delete | Notes |
|---------|-----------|-------|
| `GeminiService.kt` + `RetrofitClient` + all Gemini prompt-building in `MainViewModel` | Replaced by proxy; client keys forbidden | Retrofit dep can stay or go; spike says plain OkHttp suffices |
| API-key entry UI + SharedPreferences key storage + secrets plugin `.env` wiring | No client keys, period | Also delete `BuildConfig.GEMINI_API_KEY` |
| Client-side clarifying-questions step (`FocusScreenState.CLARIFYING`, `generateClarifyingQuestions`, skip flow) | Not in iOS flow: home → plan → session. Server owns question generation | Largest single behavioral divergence in the old app |
| Preset `SimulationTopic` catalog (Career Shift, Meeting Loop, etc.) + `generateAiQuestionsForTopic` | iOS has no topic presets | |
| Per-question `logDecision` path, "REFLECT" choice pill, sentiment enum (CONFIDENT/FEARFUL/…), local sentiment keyword heuristics | Superseded by unified verdict + session-level logs | Server's `sentiment` field is different and comes from `/v1/verdict` |
| Old verdict layout ("THE GORDIAN NODE UNTIED", AFFECTIVE RESPONSE / SENSE OF RELEASE pills, `CalmingUpliftingAnimation`) | iOS verdict card is the spec | Animation dies unless iOS has an equivalent moment |
| Firebase: `google-services` plugin, `firebase-ai`, `firebase-appcheck-recaptcha`, Firebase BoM | No Firebase in the product; proxy handles everything | Also drop `googleServices { missingGoogleServicesStrategy }` |
| Timer pause-on-tap, question-dot carousel looping (`% activeQuestions.size` wraparound) | Verify against iOS; wraparound answering the same questions repeatedly is a prototype artifact | Server caps answers at 20 anyway |
| All existing user-facing strings | AI-visible copy violates product rule | Blanket rule — nothing survives review |
| Default/AI-Studio launcher assets, `img_gordian_knot_*` placeholder framing | Replaced by real Gordian adaptive icon set | See Part 2 |

---

## Part 2: Android Table Stakes iOS Never Faced

These are not features users asked for — they are Play-acceptance and platform-correctness requirements.

| Feature | Why Required | Complexity | Notes |
|---------|--------------|------------|-------|
| Real back navigation + predictive back | targetSdk 36: system **no longer calls `onBackPressed`/dispatches KEYCODE_BACK**; predictive back is on by default. Old app has ZERO back handling — a state-machine `FocusScreenState` with no back stack, so system back exits the app from any inner screen | MEDIUM | Model navigation so back works everywhere (verdict → home? session → confirm-abort?). Compose `BackHandler`/Navigation-Compose support predictive back on current versions. iOS never faced this: no system back gesture |
| Edge-to-edge under API 35+/36 enforcement | Enforced targeting 35+; opt-out removed targeting 36 | LOW | Already mostly done (`enableEdgeToEdge` + `safeDrawing`); verify cutouts, 3-button vs gesture nav, and IME insets on input screens per-screen during UI approval screenshots |
| POST_NOTIFICATIONS runtime prompt (API 33+) | 3-day follow-up notification silently never fires without it | LOW-MEDIUM | Request contextually (at verdict save / first log), never at cold launch; handle denial gracefully (acted-on stats still function, prompt affordance in Logs). Use WorkManager — no exact-alarm permission |
| Notification channel + sane defaults | Required for any notification since API 26 | LOW | One channel ("Follow-ups"); user-visible name must be AI-invisible too |
| Play Data Safety form (speech/mic/device-ID implications) | Publishing gate; wrong answers = rejection or takedown | LOW (but must be exact) | Declare: **voice/audio** (mic → `SpeechRecognizer`; on many devices audio is processed by Google's recognizer service), **user-generated content** (scenario text → proxy, encrypted in transit), **device or other IDs** (`X-Device-ID`). Add `<uses-feature android:name="android.hardware.microphone" android:required="false"/>` so mic-less devices aren't filtered |
| Subscription-app disclosure on the paywall | Play policy: explicit price, billing period, auto-renewal terms, and how to cancel, *inside the app before purchase* | LOW-MEDIUM | Bake into paywall layout — iOS paywall copy may need Android-specific footer lines. Verified against Play policy docs 2026-07-25 |
| Manage-subscription link | Play expects an easy in-app path to manage/cancel | LOW | Deep link `https://play.google.com/store/account/subscriptions?sku={sku}&package=dev.gordian.app` from settings/membership surface |
| Restore-purchases affordance | Play auto-restores entitlements via `queryPurchasesAsync` per Google account (unlike Apple's explicit restore), but users and reviewers expect a visible "Restore purchases" button; silent-only restore generates support mail | LOW | Button = re-run `queryPurchasesAsync` + re-evaluate entitlement. Cheap insurance |
| Purchase acknowledgment + PENDING handling | Unacknowledged purchases auto-refund after ~3 days; lifetime one-time product can arrive PENDING (cash payment methods) | MEDIUM | Part of billing plumbing (Part 1 BUILD), listed here because it's an Android-only failure mode with revenue consequences |
| Adaptive launcher icon + monochrome layer + splash | Android 12+ system splash shows the icon; Android 13+ themed-icon mode renders a broken tile without a monochrome layer | LOW | Set splash background to the app's dark color to kill the white flash. Monochrome layer is NOT Material-You reinterpretation of the app — it's icon hygiene |
| Device-ID persistence vs uninstall (trial/strike integrity) | iOS keychain survives reinstall; a plain Android UUID does not → free-trial reset and safety-strike evasion by reinstalling | MEDIUM (decision + small code) | Options: include UUID in Auto Backup rules (partial coverage), or anchor to SSAID (`ANDROID_ID` — per-app-signing-key, survives reinstall on same device). Flag for phase research; affects trial AND the reinstall-proof safety design |
| Play closed-test gate (12 testers / 14 days) | Personal dev accounts must run a closed test before production | — (process) | Plan the milestone tail around it; operator confirms account age in Play Console |

---

### Anti-Features (deliberately NOT building)

| Feature | Why It Comes Up | Why Problematic Here | Alternative |
|---------|-----------------|----------------------|-------------|
| Material You / dynamic color | Default Android expectation | Explicit user decision: exactly like iOS; dynamic color would repaint the Art Deco gold/dark identity per-device | Fixed brand palette; keep only the monochrome icon layer (icon hygiene, not theming) |
| Home-screen widgets / Quick Settings tile | "Android apps have widgets" | iOS app has none; a decision session is a deliberate 60s ritual, not a glanceable surface; adds Play-listing and maintenance surface | Nothing. Notification already covers re-engagement |
| App shortcuts (long-press launcher) / Assistant App Actions | Cheap-looking win | Another divergence from iOS spec with zero parity value; shortcut labels create new copy surfaces to police for AI-invisibility | Skip for v1; revisit only if iOS adds equivalents |
| Tablet / foldable / landscape layouts | Play consoles nag about large screens | iOS is iPhone-only; parity means phone-first portrait | Lock portrait like iOS (or verify iOS behavior); accept large-screen compatibility mode |
| Wear OS companion | Ecosystem checkbox | Out of scope per PROJECT.md | — |
| Freemium meters / limited free sessions | Standard Android monetization instinct | Business model locked: paid, 7-day trial, no meters | Trial-then-paywall only |
| Reviving old-app extras (topic presets, clarifying step, per-question logging, sentiment analytics bar) | Code already exists, feels free | Every one is a divergence from the iOS spec; "already written" is not a reason | Delete per Part 1 |
| Real client-side generation fallback (old Gemini fallback path) | Old app did it | No client keys, no AI mentions; server is the only brain | Static offline-fallback content matching iOS behavior |

---

## Feature Dependencies

```
Persisted device ID ──required-by──> Proxy client (X-Device-ID header)
                    ──required-by──> 7-day trial gate
                    ──required-by──> Safety strikes/lockout integrity (reinstall-proof)

Proxy client ──required-by──> Session flow (all modes)
             ──required-by──> Verdict card
             ──required-by──> Refusal / Lockout screens (SENSITIVE / LOCKED)
             ──required-by──> TOO_BIG knots UI

Navigation rework (back stack) ──required-by──> every multi-screen flow
                               ──required-by──> predictive back correctness (targetSdk 36)

Play Billing plumbing ──required-by──> Paywall
Paywall ──required-by──> Trial-expiry gate
Billing entitlement state ──required-by──> Membership badges
                          ──required-by──> Redeem-code flow (entitlement refresh after redeem)

Session-level log schema ──required-by──> Acted-on stats
                         ──required-by──> 3-day follow-up notification (writes actedOn back)
POST_NOTIFICATIONS prompt ──required-by──> 3-day follow-up notification

applicationId rename (dev.gordian.app) ──must-precede──> first Play upload (permanent)
Data safety form ──depends-on──> final permission set (mic, notifications) + proxy data flows

Verdict card ──enhances──> Logs (log entries reuse verdict fields)
AI-invisible copy sweep ──gates──> every user-facing surface (cross-cutting, last-mile check)
```

### Dependency Notes

- **Device ID before everything network:** every proxy call carries `X-Device-ID`; strikes, lockouts, and trial are keyed to it server-side. The persistence strategy (backup rules / SSAID) must be decided before the trial phase, not after — changing it post-launch resets everyone.
- **Navigation before screens:** retrofitting a back stack after building screens on the old state-machine pattern is a rewrite; do it first.
- **Billing before badges/redeem:** badges and redeem are thin UI over entitlement state; worthless until the billing source of truth exists.
- **Log schema before notification:** the follow-up notification's whole purpose is writing `actedOn`; schema must land first.
- **appId rename precedes upload:** Play makes it permanent; it is currently still `com.aistudio.gordian.ovthnk` in `build.gradle.kts`.

## MVP Definition

Parity is locked, so "MVP" here means *ship order within the milestone*, not scope cuts. Everything in Part 1 BUILD/ADAPT and Part 2 is required for the Play release.

### Launch With (v1 — all required)

- [ ] Session flow, all six modes, verdict card, refusal + lockout, TOO_BIG — the product
- [ ] Trial + paywall + billing + badges + redeem — the deal
- [ ] Logs with acted-on stats, Guides, 3-day notification — the retention loop
- [ ] Speech input (adapted), AI-invisible copy sweep — the polish gates
- [ ] Part 2 platform items: back navigation, insets verification, notification permission, data safety form, paywall disclosures, manage-sub link, restore affordance, icon/splash, appId rename

### Add After Validation (v1.x)

- [ ] Nothing planned — parity is the ceiling as well as the floor. Only track iOS if iOS moves.

### Future Consideration (v2+)

- [ ] Any Android-native surface (widgets, shortcuts) — only if the product decision changes on both platforms.

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Proxy client + device ID | HIGH (nothing works without it) | LOW | P1 |
| Navigation/back-stack rework | HIGH (app is unshippable on targetSdk 36 without it) | MEDIUM | P1 |
| Session flow (modes) + verdict card | HIGH | HIGH | P1 |
| Safety surfaces (refusal, lockout) | HIGH (product rule + liability) | MEDIUM | P1 |
| TOO_BIG knots UI | HIGH | MEDIUM | P1 |
| Billing + paywall + trial | HIGH (revenue) | HIGH | P1 |
| Badges + redeem flow | MEDIUM | LOW-MEDIUM | P1 (thin, after billing) |
| Logs + acted-on stats | MEDIUM-HIGH | MEDIUM | P1 |
| 3-day notification + permission flow | MEDIUM | MEDIUM | P1 |
| Guides content | MEDIUM | LOW | P1 |
| Copy sweep (AI-invisible) | HIGH (product rule) | MEDIUM | P1 (cross-cutting gate) |
| Data safety form + listing + closed test | HIGH (publishing gate) | LOW-MEDIUM | P1 (milestone tail) |
| Icon/splash/monochrome layer | MEDIUM | LOW | P1 |

**Priority key:** P1 = must have for launch (parity is locked; there are no P2/P3 features in this milestone).

## Competitor Feature Analysis

Not applicable — the "competitor" is the shipped iOS app, and the strategy is verbatim parity. Where iOS screen details are needed (exact copy, layout values), they will be re-stated in each phase plan per PROJECT.md (no reading the iOS repo from this repo).

## Sources

- Old app source (HIGH confidence — read directly 2026-07-25): `app/src/main/java/com/example/{MainActivity,MainViewModel}.kt`, `api/GeminiService.kt`, `data/DecisionDatabase.kt`, `voice/VoiceRecognizer.kt` (listed), `AndroidManifest.xml`, `app/build.gradle.kts`
- iOS feature set + product rules (HIGH): `.planning/PROJECT.md`; spike skill `.claude/skills/spike-findings-gordian-android/` (005 billing, 006 toolchain, 007 proxy contract — live-verified 2026-07-24)
- Predictive back at targetSdk 36 (MEDIUM-HIGH, web-verified): [Android Developers — predictive back gesture](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture) (Android 16 no longer calls `onBackPressed`/KEYCODE_BACK for apps targeting 36+)
- Play subscription policy (MEDIUM-HIGH, web-verified): [Play Console Help — subscriptions](https://support.google.com/googleplay/android-developer/answer/140504), [Play Billing — subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions), [Play Billing — manage purchases](https://developer.android.com/google/play/billing/manage-purchases) (explicit terms disclosure, easy cancel access, acknowledgment window, restore behavior)

---
*Feature research for: Gordian Android parity port*
*Researched: 2026-07-25*
