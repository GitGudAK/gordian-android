# Play Billing (subscriptions, lifetime, gifts)

## Requirements

- Economics identical to iOS: 7-day free trial (in-app, device-anchored — Play is NOT involved),
  $4.99/mo, $29.99/yr, $69.99 lifetime. No freemium, no meters.
- Operator gift capability: lifetime gifts and 3-months-free gifts.
- Access = purchase OR in-trial, mirroring the iOS EntitlementManager.

## How to Build It

**Product structure (inverted vs iOS — do not port the two-product model literally):**

| Thing | Play Console setup |
|---|---|
| Subscription | ONE product, id `plus`, with TWO base plans: `monthly` (P1M, $4.99, auto-renewing) and `annual` (P1Y, $29.99, auto-renewing) |
| Lifetime | One-time product `lifetime`, $69.99 |
| 3-months-free gift | Promo-code promotion on the subscription granting a free trial (length set by the promotion; overrides default trial). Custom memorable codes (e.g. GORDIAN-LAUNCH) supported: in-app redemption only, never-subscribed users only, 2,000–99,999 redemption cap. One-time codes: 10,000/quarter/product, redeemable in Play Store or in-app |
| Lifetime gift | One-time promo codes on the one-time product: 500/quarter across all one-time products, promotion window up to 1 year |

**Client wiring (billing-ktx 8.0.0):**

```kotlin
implementation("com.android.billingclient:billing-ktx:8.0.0")

val billing = BillingClient.newBuilder(context)
    .setListener { result, purchases -> /* PurchasesUpdatedListener */ }
    .enablePendingPurchases(               // MANDATORY in v8, and one-time
        PendingPurchasesParams.newBuilder() // products must be opted in:
            .enableOneTimeProducts().build()
    )
    .build()
```

- Entitlement key is `productId` + `basePlanId` (from the purchase's product details), not two
  product ids.
- Query subs and INAPP purchases on every app resume AND in the listener: promo codes can be
  redeemed in the Play Store while the app is closed (out-of-app purchase). This replaces the
  iOS `Transaction.currentEntitlements` sweep.
- Proven probe: `sources/005-play-billing-model/BillingSpikeActivity.kt` (debug source set;
  launch via adb, observe `adb logcat -s BillingSpike`).

## What to Avoid

- Don't create two subscription products to mirror ASC — upgrade/downgrade between base plans of
  one product is the native Play path.
- Don't expect promo-code subs to be permanently free: they grant a trial; a payment method is
  required and the sub renews after (same semantics as Apple offer codes).
- Don't test billing on a signed-out emulator and read `code=3` (BILLING_UNAVAILABLE) as breakage —
  it's the documented signed-out response. Sign into a Google account on the Play-image emulator.
- Custom (memorable) codes cannot gift the lifetime product — one-time products get auto-generated
  codes only.

## Constraints

- Promo codes do NOT carry over quarters (use-or-lose per quarter).
- Publishing gate: personal Play developer accounts created after 2023-11-13 need a closed test
  with 12 testers opted in continuously for 14 days before production access (organizational and
  older personal accounts exempt). Operator account facts unconfirmed as of 2026-07-24.
- 3-month trial length in the promotion UI still needs eyes-on confirmation once Play Console
  access exists.
- Full purchase-flow validation requires products in Play Console + signed-in account; the spike
  proved library mechanics only.

## Origin

Synthesized from spike: 005
Source files: sources/005-play-billing-model/
