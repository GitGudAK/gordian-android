# Stack Research

**Domain:** Android client — iOS-to-Android port of a paid consumer decision app (Kotlin/Compose, Play Billing, server-side AI proxy)
**Researched:** 2026-07-25
**Confidence:** HIGH (all versions verified against official release pages this week)

## Executive Position

The repo's toolchain (AGP 9.1.1, Kotlin 2.2.10, compileSdk 36.1, KSP 2.3.5) is already
current and spike-proven — **do not touch it this milestone**. The work is: (1) bump the
stale AndroidX layer (Compose BOM is from Sept **2024**; everything else follows), (2) add
exactly three capabilities (Billing, DataStore, WorkManager), and (3) **delete the entire
Firebase/Google-services layer**, which is dead weight from the pre-proxy era.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.2.10 (keep) | Language | Already building headlessly (spike 006). Compose compiler ships inside the Kotlin plugin since 2.0 — no separate compiler version to manage. |
| AGP | 9.1.1 (keep) | Build | Current major; already proven with Gradle 9.6.1 + JDK 21 on the Intel Mac. No reason to move during a parity milestone. |
| Compose BOM | **2026.06.01** (bump from 2024.09.00) | UI platform | Latest stable BOM (Compose UI 1.11.4). The current 2024.09.00 BOM is 21 months stale against Kotlin 2.2.10 — the single most important bump in this milestone. Verified at the official BOM mapping page. |
| Material3 | **1.4.0** (via BOM, stable) | Components/theming | BOM 2026.06.01 resolves material3 to 1.4.0 stable. Sufficient for the Art Deco custom theme — the app overrides colors/typography anyway, so no need for 1.5.0-alpha. |
| Play Billing (billing-ktx) | **9.1.0** (bump from 8.0.0 debug-only, promote to `implementation`) | Subscriptions + lifetime | v8 was the breaking release; v9 (2026-05-19) is a small delta — every pattern spike 005 proved on 8.0.0 carries over unchanged (`PendingPurchasesParams.enableOneTimeProducts()` still mandatory). Google's deadline: all updates must be on 8+ by 2026-08-31. Shipping 9.1.0 now avoids the *next* forced migration and picks up `enableAutoServiceReconnection()`. |
| Room | **2.8.4** (bump from 2.7.0) | Decision-log persistence | Latest stable (2025-11-19). Drop-in from 2.7.0; minSdk 23 requirement is under our minSdk 24. Prepared-statement caching and Flow deadlock fixes since 2.8.x. |
| DataStore Preferences | **1.2.1** (add — currently commented out at 1.1.7) | Trial anchor + small flags | Latest stable (2026-03-11). See "Trial anchoring" below for why over SharedPreferences. |
| WorkManager (work-runtime-ktx) | **2.11.2** (add) | 3-day follow-up notification | Latest stable (2026-03-25). See "Notification scheduling" below for why over AlarmManager. |

### Supporting Libraries (bumps to existing deps)

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| androidx.activity:activity-compose | **1.13.0** (from 1.10.1) | Host activity, edge-to-edge | Latest stable (2026-03-11); edge-to-edge now re-applies on config change. |
| androidx.lifecycle (runtime/viewmodel-compose) | **2.11.0** (from 2.8.7) | ViewModels, lifecycle-aware collection | Latest stable (2026-06-17). |
| OkHttp + logging-interceptor | **4.12.0** (from 4.10.0) | Proxy transport | 4.12.0 is the final 4.x. Deliberately NOT moving to OkHttp 5.x mid-parity — the proxy client is spike-proven (007, 4/4 live) on the 4.x line; zero-risk patch bump only. |
| Retrofit + converter-moshi | 2.12.0 (keep) | Proxy API surface | Current and spike-proven. |
| Moshi + moshi-kotlin-codegen (KSP) | 1.15.2 (keep) | JSON codegen | Final Moshi line; codegen avoids kotlin-reflect. |
| KSP plugin | **2.3.5 (keep — do not bump past 2.3.6)** | Room/Moshi codegen | KSP now versions independently of Kotlin. 2.3.5 is proven with Kotlin 2.2.10 in this repo. KSP **2.3.7+ bumped its Kotlin target language version to 2.3** — hold KSP until Kotlin itself moves. |
| material-icons-core | 1.7.8 (pin explicitly if kept) | Stock icons | The material-icons artifacts are **frozen at 1.7.8** and no longer published for new Compose versions — after the BOM bump they need an explicit version. Prefer dropping them: parity design uses custom Art Deco assets. |

### No-Dependency Capabilities (platform APIs)

| Capability | API | Why no library |
|------------|-----|----------------|
| Speech-to-text | `android.speech.SpeechRecognizer` + `RecognizerIntent` | Already present in the old app; zero-dependency, on-device on modern devices. `RECORD_AUDIO` runtime permission. `createOnDeviceSpeechRecognizer()` (API 31+) can force on-device where available; use plain `createSpeechRecognizer()` + `isRecognitionAvailable()` guard for minSdk 24. Whisper-style on-device models (~40–150 MB) and cloud STT SDKs are absurd overkill for dictating a dilemma sentence. |
| Follow-up notification | `NotificationManager` + `POST_NOTIFICATIONS` runtime permission (API 33+) | Platform notifications; scheduled by WorkManager (below). One notification channel is enough. |

## Key Integration Patterns

### Play Billing 9.1.0 — one sub product, two base plans, one one-time product

Structure (from spike 005, unchanged in v9):
- Sub product `plus` with base plans `monthly` (P1M, $4.99) and `annual` (P1Y, $29.99); one-time product `lifetime` ($69.99).
- **Entitlement key is `productId` + `basePlanId`**, not two product IDs. Do not port the iOS two-product model literally.

Client wiring:

```kotlin
val billing = BillingClient.newBuilder(context)
    .setListener(purchasesUpdatedListener)
    .enablePendingPurchases(                       // still MANDATORY in v9
        PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts().build())      // required for `lifetime`
    .enableAutoServiceReconnection()               // v8+; replaces manual retry loops
    .build()
```

Entitlement checking (mirrors iOS `Transaction.currentEntitlements` sweep):
- `queryPurchasesAsync(SUBS)` **and** `queryPurchasesAsync(INAPP)` on **every app resume** (ON_RESUME lifecycle observer), plus handle `PurchasesUpdatedListener`.
- The resume-time sweep is what catches **out-of-app promo-code redemption** — codes redeemed in the Play Store app while Gordian is closed arrive as out-of-app purchases with no listener callback. This is the entire promo-gift delivery mechanism (lifetime gifts = auto-generated one-time-product codes; 3-month gifts = custom sub promotion codes), so the resume sweep is not optional hygiene, it is a product feature.
- **Acknowledge every purchase within 3 days** (`acknowledgePurchase` for subs, and for the lifetime non-consumable) or Play auto-refunds. Only acknowledge `PurchaseState.PURCHASED` — pending purchases must not be granted or acknowledged.
- Access = valid purchase OR in-device-trial (trial is app-local, Play is not involved).

v9 behavioral notes (small but silent): blocked Play Store now returns `BILLING_UNAVAILABLE` instead of `ERROR`; signed-out emulator still returns `code=3` — not breakage (spike 005).

### Trial anchoring — DataStore Preferences, not SharedPreferences

- **DataStore 1.2.1** for the trial-start timestamp and small flags: coroutine-native (no main-thread I/O / ANR class of bugs), transactional, and the app is already 100% coroutines. SharedPreferences is not deprecated but is the legacy path Google explicitly steers away from; there is no reason to introduce it fresh in 2026.
- Room stays for structured decision logs (history + acted-on stats) — DataStore is wrong for lists/queries.
- Anchor design: store `trialStartEpochMs` on first launch; entitlement check = `now < trialStart + 7d` OR billing entitlement. Note: Android Auto Backup will restore DataStore files on reinstall — for trial anchoring this is a *feature* (reinstall does not reset the trial). Leave `allowBackup` on; do not exclude the trial file in `dataExtractionRules`.

### Notification scheduling — WorkManager, not AlarmManager

- `OneTimeWorkRequestBuilder<FollowUpWorker>().setInitialDelay(3, TimeUnit.DAYS)` with a unique work name per decision (`REPLACE` policy) — survives reboot and app death, no permissions beyond `POST_NOTIFICATIONS`.
- AlarmManager exact alarms are wrong here: `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` require Play Console declarations and policy justification reserved for alarm/calendar-class apps, and a "3-day follow-up" has zero exactness requirement — WorkManager's batching window (minutes of drift on a 72-hour delay) is invisible to the user.
- Cancel via `WorkManager.cancelUniqueWork(name)` when the user marks the decision acted-on early.

## Installation (net dependency delta)

```kotlin
// libs.versions.toml — bump
composeBom = "2026.06.01"
roomRuntime = "2.8.4"            // + roomKtx, roomCompiler
lifecycleRuntimeKtx = "2.11.0"   // + viewmodelCompose, runtimeCompose
activityCompose = "1.13.0"
okhttp = "4.12.0"                // + loggingInterceptor
datastorePreferences = "1.2.1"
workRuntime = "2.11.2"           // new entry
billingKtx = "9.1.0"             // new entry

// app/build.gradle.kts — add
implementation(libs.androidx.datastore.preferences)   // uncomment + bump
implementation(libs.androidx.work.runtime.ktx)        // new
implementation(libs.billing.ktx)                      // promote from debugImplementation 8.0.0
```

## What NOT to Use (removals for the parity build)

| Avoid / Remove | Why | Use Instead |
|-------|-----|-------------|
| **firebase-ai** + **firebase-bom** | Dead weight from the pre-proxy client-side-Gemini era. AI is entirely server-side at the Workers proxy; no client keys exist by requirement. | Existing OkHttp/Retrofit proxy client (spike 007, proven verbatim). |
| **firebase-appcheck-recaptcha** | Only existed to attest client-side Gemini calls. The proxy has its own gate; App Check attests nothing anymore. | Nothing. |
| **google-services Gradle plugin** (+ `googleServices {}` block, the `MissingGoogleServicesStrategy` import, and any `google-services.json`) | Exists solely to configure Firebase. With Firebase gone it's build-time complexity and a config file referencing the old AI-Studio identity — actively dangerous across the `dev.gordian.app` applicationId rename. | Delete plugin, block, import, and json. |
| **secrets-gradle-plugin** (`.env` wiring) | Existed to inject the client Gemini API key. "No client keys" is a hard requirement — its continued presence invites regression. | Delete; proxy URL is a plain `buildConfigField`/constant. |
| **material-icons-extended** | Frozen at 1.7.8 (no longer in new BOMs) and adds thousands of unused vector icons to a custom-designed app. | Custom Art Deco vector assets; pin `material-icons-core:1.7.8` only if any stock icon survives the reskin. |
| **AlarmManager exact alarms** | Play policy declaration burden for zero product benefit at 72-hour granularity. | WorkManager 2.11.2. |
| **SharedPreferences** (fresh usage) | Synchronous main-thread I/O foot-gun; legacy API. | DataStore Preferences 1.2.1. |
| **navigation-compose / Navigation 3** | Currently commented out; a ~6-screen parity app with iOS-spec custom transitions is simpler with state-driven navigation the old app already uses. Adding a nav framework mid-parity buys ceremony, not parity. | Existing screen-state approach. |
| **OkHttp 5.x migration (this milestone)** | Proxy client is spike-proven on 4.x; 5.x is a package/API migration with no feature the client needs. | OkHttp 4.12.0 (final 4.x patch). |
| Dead toml entries: camera-*, coil, accompanist-permissions, play-services-location, credentials/googleid, firebase-auth/firestore | Template residue never used by the product. | Prune the version catalog while renaming the applicationId. |

## Version Compatibility

| Package | Compatible With | Notes |
|-----------|-----------------|-------|
| Compose BOM 2026.06.01 | Kotlin 2.2.10 | Compose compiler ships with the Kotlin plugin since 2.0 — BOM and Kotlin version are decoupled; any current BOM works with 2.2.10. |
| KSP 2.3.5 | Kotlin 2.2.10 | Proven in-repo. **Do not bump KSP to 2.3.7+** until Kotlin moves to 2.3.x (KSP 2.3.7 raised its Kotlin target language version to 2.3). |
| Room 2.8.4 / WorkManager 2.11.2 | minSdk 24 | Both raised their floor to API 23 — under our minSdk 24, no conflict. |
| Billing 9.1.0 | targetSdk 36 | v9 targets SDK 35+; our targetSdk 36 satisfies it. Play deadline: 8+ mandatory for updates 2026-08-31. |
| material-icons 1.7.8 | BOM 2026.06.01 | Frozen artifact — must carry an explicit version after the BOM bump or resolution fails/downgrades silently. |

## Confidence Assessment

| Recommendation | Confidence | Basis |
|----------------|-----------|-------|
| Compose BOM 2026.06.01 / material3 1.4.0 | HIGH | Official BOM mapping page, fetched 2026-07-25 |
| Billing 9.1.0 + v8-proven patterns | HIGH | Official release notes (9.1.0, 2026-06-18) + official v9 migration guide + spike 005 |
| Room 2.8.4, DataStore 1.2.1, WorkManager 2.11.2, lifecycle 2.11.0, activity 1.13.0 | HIGH | Official androidx release pages, fetched 2026-07-25 |
| KSP hold at 2.3.5 (2.3.7+ needs Kotlin 2.3) | MEDIUM | KSP GitHub releases; the "2.3.7 requires Kotlin 2.3" reading is from release-note phrasing, not tested — verify on the first build if bumping |
| SpeechRecognizer over alternatives | HIGH | Stable platform API, already working in old app (spike 006) |
| WorkManager over AlarmManager | HIGH | Play exact-alarm policy is documented; product needs no exactness |
| Firebase/google-services/secrets removal | HIGH | Requirements ("no client keys", server-side AI) + direct read of build files |
| OkHttp stay on 4.12.0 | HIGH | 4.12.0 is the documented final 4.x; migration deferral is a judgment call aligned with parity scope |

## Sources

- https://developer.android.com/develop/ui/compose/bom/bom-mapping — BOM 2026.06.01 → UI 1.11.4 / material3 1.4.0 (HIGH)
- https://developer.android.com/google/play/billing/release-notes — 9.1.0 (2026-06-18), v8/v9 change log, 2026-08-31 deadline (HIGH)
- https://developer.android.com/google/play/billing/migrate-gpblv9 — v9 migration scope (HIGH)
- https://developer.android.com/jetpack/androidx/releases/room — 2.8.4 (HIGH)
- https://developer.android.com/jetpack/androidx/releases/datastore — 1.2.1 (HIGH)
- https://developer.android.com/jetpack/androidx/releases/work — 2.11.2 (HIGH)
- https://developer.android.com/jetpack/androidx/releases/lifecycle — 2.11.0 (HIGH)
- https://developer.android.com/jetpack/androidx/releases/activity — 1.13.0 (HIGH)
- https://github.com/google/ksp/releases — independent 2.3.x versioning; 2.3.7 Kotlin-target bump (MEDIUM)
- Compose material-icons frozen at 1.7.8 — androidx compose-material release notes + Maven/community confirmation (MEDIUM)
- Spike findings: `.claude/skills/spike-findings-gordian-android/references/play-billing.md` (billing structure, promo-code mapping, resume-sweep pattern) (HIGH, empirically proven)

---
*Stack research for: Gordian Android parity client*
*Researched: 2026-07-25*
