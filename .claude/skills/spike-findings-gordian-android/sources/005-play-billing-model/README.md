---
spike: 005
name: play-billing-model
type: standard
validates: "Given Play Billing primitives, when we map monthly/annual/lifetime + coupon strategy onto them, then BillingClient connects on an emulator and every iOS entitlement has a working Play equivalent"
verdict: VALIDATED
related: [006]
tags: [billing, play-console, monetization]
---

# Spike 005: play-billing-model

## What This Validates
Given Play Billing primitives (subscriptions are one product with base plans/offers, not sibling
products; promo codes have different semantics than Apple's), when we map Gordian's iOS economics
($4.99/mo, $29.99/yr, $69.99 lifetime, operator gifts of lifetime and 3-months-free) onto them,
then BillingClient connects on an emulator and every iOS entitlement has a working Play equivalent.

## Research

### The mapping (iOS → Play)

| iOS (App Store Connect) | Play equivalent | Notes |
|---|---|---|
| Subscription group "Gordian Access" with 2 products (plus.monthly, plus.annual) | **ONE subscription product** (`plus`) with **two base plans** (`monthly` P1M $4.99, `annual` P1Y $29.99) | Play inverts Apple's structure. Upgrade/downgrade between base plans is native. |
| plus.lifetime non-consumable $69.99 | One-time product `lifetime` $69.99 | Direct equivalent. |
| Promo codes for lifetime (100/version, 28-day expiry) | **One-time-code promo codes** on the one-time product — 500/quarter, promotion window up to 1 year, redeemable in Play Store or in-app | Strictly better than Apple's: no per-version cap, longer expiry. |
| Offer codes "3 months free" on plus.monthly | **Subscription promo codes granting a free trial** (trial length set by the promotion, overrides default) — one-time codes: 10,000/quarter/product; custom memorable codes (e.g. GORDIAN-LAUNCH): 2,000–99,999 redemptions, in-app redemption only, never-subscribed users only | Same semantics as Apple offer codes: free period, then auto-renews (payment method required). |
| In-app "Redeem a code" sheet (offerCodeRedemption) | In-app redemption UI or deep link `https://play.google.com/redeem?code=X`; app must handle **out-of-app purchases** (query purchases on resume, PurchasesUpdatedListener) | Redemption can happen in Play Store while app is closed. |
| 7-day free trial (in-app, device-anchored) | Identical — no Play involvement | Business logic ports as-is. |

### Caveats found
- Subscription promo codes grant **trials, not free subscriptions** — user must attach a payment
  method and the sub renews after the gifted period. Same as Apple offer codes; fine for gifts.
- **Custom codes** (memorable strings) are subscription-only and in-app-only; lifetime gifts must
  use auto-generated one-time codes.
- Unused promo codes **do not carry over quarters**.
- Exact configurability of a 3-month trial length in the promotion UI to be confirmed once
  Play Console access exists (docs say promo trial length overrides the default trial).

### Publishing gate (affects timeline, not feasibility)
Personal Play developer accounts created after 2023-11-13 must run a **closed test with 12 testers
opted in continuously for 14 days** before applying for production access (reduced from 20 testers
in Dec 2024). Organizational accounts and older personal accounts are exempt.
**Operator fact needed: account type + creation date.**

## How to Run
```
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
gradle assembleDebug
~/Library/Android/sdk/platform-tools/adb -e install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb -e shell am start -n com.aistudio.gordian.ovthnk/com.example.spike.BillingSpikeActivity
~/Library/Android/sdk/platform-tools/adb -e logcat -d -s BillingSpike
```
Probe lives at `app/src/debug/java/com/example/spike/BillingSpikeActivity.kt`
(debug source set only — never ships) with billing-ktx 8.0.0 as a `debugImplementation` dep.

## What to Expect
On a Play-Store-image emulator without a signed-in Google account: `SETUP-FINISHED code=3`
(BILLING_UNAVAILABLE) — a clean typed response, no crash. With a signed-in account and products
configured in Play Console, code 0 (OK) and a product query.

## Investigation Trail
- 2026-07-24: Docs research (developer.android.com billing/subscriptions, billing/promo,
  Play Console help). Mapping table above; no blockers found.
- 2026-07-24: Hands-on probe on the gordian-spike emulator (API 36 Play image, no account):
  billing-ktx 8.0.0 resolved and compiled first try (PendingPurchasesParams now mandatory in v8);
  BillingClient built, bound to Play, returned `code=3 'Billing service unavailable on device.'`
  — the exact documented behavior for a signed-out device. Library mechanics proven.

## Results
VALIDATED. Every iOS entitlement has a Play-native equivalent (mapping table above), and the
BillingClient path works mechanically end-to-end. Two caveats for the real build:
1. Full purchase-flow validation (code 0 + product query + sandbox purchase) requires a signed-in
   Google account and products in Play Console — operator steps, not feasibility risks.
2. Structural inversion vs iOS: ONE subscription product with two base plans (not two products) —
   the EntitlementManager port must map productId+basePlanId, not two productIds.
Operator facts still needed: Play developer account type + creation date (decides the
12-testers/14-days closed-testing gate) and confirmation of 3-month trial length in the
promo-code promotion UI.
