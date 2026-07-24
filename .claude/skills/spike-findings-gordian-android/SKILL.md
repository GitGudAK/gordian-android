---
name: spike-findings-gordian-android
description: Implementation blueprint from spike experiments. Requirements, proven patterns, and verified knowledge for building gordian-android. Auto-loaded during implementation work.
---

<context>
## Project: gordian-android

Ship Gordian on Google Play. The Android app (repo root) is the original pre-iOS-evolution build:
client-side Gemini, no proxy, no safety system, no monetization, old AI-visible copy. The shipped
iOS product moved everything hard server-side (proxy: session gate, safety strikes, TOO_BIG,
verdicts), so the Android work is: modernize the app to product parity, solve Play-specific
monetization, and clear Play Console's publishing gates.

Spike sessions wrapped: 2026-07-24
</context>

<requirements>
## Requirements

- Android version lives in this standalone private repo (GitGudAK/gordian-android) — no iOS/proxy
  crossover artifacts.
- AI-always via the shared proxy (`https://gordian-proxy.gordian-app.workers.dev`); no client keys.
- Paid app economics identical to iOS: 7-day free trial (in-app, device-anchored), $4.99/mo,
  $29.99/yr, $69.99 lifetime. No freemium, no meters.
- Operator coupon capability: lifetime gifts (one-time promo codes) and 3-month gifts
  (subscription promo-code trials) — mapping decided by spike 005.
- No AI mentions in user-facing copy.
- For UI changes: emulator screenshot + user approval BEFORE committing.
</requirements>

<findings_index>
## Feature Areas

| Area | Reference | Key Finding |
|------|-----------|-------------|
| Play Billing | references/play-billing.md | ONE sub product + two base plans (not two products); gifts map via promo codes; billing-ktx 8.0.0 mechanics proven |
| Build Toolchain | references/build-toolchain.md | Fully headless on the Intel Mac: brew JDK 21 + Gradle 9.6.1 + existing SDK; emulator recipe; no Android Studio |
| Proxy Client | references/proxy-client.md | Kotlin/OkHttp/Moshi speaks the iOS proxy contract verbatim — 4/4 live; zero server changes needed |

## Source Files

Original spike source files are preserved in `sources/` for complete reference.
</findings_index>

<metadata>
## Processed Spikes

- 005-play-billing-model
- 006-android-toolchain-build
- 007-proxy-client-kotlin
</metadata>
