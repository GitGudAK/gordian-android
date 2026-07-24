# Spike Manifest

## Idea
Ship Gordian on Google Play. The Android app (repo root) is the original pre-iOS-evolution build:
client-side Gemini, no proxy, no safety system, no monetization, old verdict UX. The shipped iOS
product moved everything hard server-side (proxy: session gate, safety strikes, TOO_BIG, verdicts),
so the Android work is: modernize the app to product parity, solve Play-specific monetization,
and clear Play Console's publishing gates. Spike numbering continues from the iOS series (001–004,
in the gordian repo) — knowledge carries over; artifacts do not.

## Requirements
Design decisions locked in by the user. Non-negotiable for the real build.

- Android version lives in this standalone private repo — no iOS/proxy crossover artifacts.
- AI-always via the shared proxy (`https://gordian-proxy.gordian-app.workers.dev`); no client keys.
- Paid app economics identical to iOS: 7-day free trial, $4.99/mo, $29.99/yr, $69.99 lifetime.
- Operator coupon capability required: lifetime gifts and 3-month gifts (mechanism may differ from
  iOS offer/promo codes — spike 005 decides the Play-native mapping).
- No AI mentions in user-facing copy.

## Spikes

| # | Name | Type | Validates | Verdict | Tags |
|---|------|------|-----------|---------|------|
| 005 | play-billing-model | standard | Given Play Billing primitives (one subscription product + base plans/offers; promo-code limits), when we map monthly/annual/lifetime + coupon strategy onto them, then BillingClient connects on an emulator and every iOS entitlement has a working Play equivalent | VALIDATED (mapping complete; BillingClient binds, clean code=3 signed-out; full flow needs Play Console + signed-in account) | [billing, play-console, monetization] |
| 006 | android-toolchain-build | standard | Given this Intel Mac (no Android Studio installed), when we install the toolchain and run the Gradle build, then the existing app compiles and boots on an emulator | VALIDATED (assembleDebug first try; boots on API 36 Play-image emulator; toolchain was 90% present) | [toolchain, gradle, emulator] |
| 007 | proxy-client-kotlin | standard | Given the live proxy, when Kotlin/OkHttp calls /session-plan with X-Device-ID, then all modes decode (BINARY/YES_NO/SENSITIVE/TOO_BIG/LOCKED) and lockout state round-trips | VALIDATED (4/4 live tests: BINARY, TOO_BIG knots, SENSITIVE risk+lockout, verdict) | [proxy, okhttp, contract] |
