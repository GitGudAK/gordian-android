# Spike Wrap-Up Summary

**Date:** 2026-07-24
**Spikes processed:** 3
**Feature areas:** Play Billing, Build Toolchain, Proxy Client
**Skill output:** `./.claude/skills/spike-findings-gordian-android/`

## Processed Spikes
| # | Name | Type | Verdict | Feature Area |
|---|------|------|---------|--------------|
| 005 | play-billing-model | standard | VALIDATED | Play Billing |
| 006 | android-toolchain-build | standard | VALIDATED | Build Toolchain |
| 007 | proxy-client-kotlin | standard | VALIDATED | Proxy Client |

## Key Findings

- **Billing structure inverts vs iOS:** one subscription product (`plus`) with `monthly`/`annual`
  base plans + a `lifetime` one-time product. Entitlements key on productId+basePlanId. Gifts:
  lifetime via one-time promo codes (500/qtr, ≤1-yr window), 3-months-free via subscription
  promo-code trials (custom memorable codes supported, in-app only, never-subscribed users).
  Promo codes don't carry over quarters. billing-ktx 8.0.0 requires PendingPurchasesParams.
- **Toolchain is fully headless on the Intel Mac:** brew openjdk@21 + standalone Gradle 9.6.1 +
  the leftover SDK built the app first try; API 36 Play-image emulator boots headless in ~30 s.
  No Android Studio, no Intel ceiling (unlike the iOS Xcode 16.4 wall).
- **Proxy contract is platform-neutral in practice:** Kotlin/OkHttp/Moshi decoded every mode
  live (BINARY, TOO_BIG knots-in-questions[], SENSITIVE risk+lockout, verdict) with deps already
  in the app. The entire AI/safety surface needs zero Android-specific server work.
- **Launch-timeline wildcard:** if the Play developer account is personal and created after
  2023-11-13, production access requires a closed test with 12 testers opted in for 14 continuous
  days. Account facts unconfirmed.
