# Pitfalls Research

**Domain:** iOS-to-Android parity port of a paid consumer app (subscription + lifetime IAP) shipping on Google Play
**Researched:** 2026-07-25
**Confidence:** HIGH (Play policy/billing facts verified against current official docs and community guides; Compose/speech behavior MEDIUM-HIGH from official behavior-change docs + community reports)

Context anchors: Gordian Android targets exact iOS parity (dark/gold Art Deco design), uses billing-ktx 8.0.0 (spike 005), renames applicationId to `dev.gordian.app`, ships through a likely 12-testers/14-days closed-test gate, and the proxy is frozen (no server changes possible from this repo).

## Critical Pitfalls

### Pitfall 1: Unacknowledged purchases silently auto-refund

**What goes wrong:**
A purchase completes, the user gets access, everything looks done — then Google auto-refunds and revokes it because the app never called `acknowledgePurchase()` within 3 days. For subscriptions, renewals also won't fire until the original purchase is acknowledged, so an unacknowledged sub looks alive in testing and dies in production.

**Why it happens:**
The happy-path demo works without acknowledgment. Developers wire `PurchasesUpdatedListener` → grant entitlement → ship, and the refund arrives days later when nobody is watching. There's no error at purchase time. iOS StoreKit 2 has `Transaction.finish()` but Apple doesn't auto-refund on failure to finish, so an iOS-first mental model hides this.

**How to avoid:**
- Acknowledge in BOTH paths: the `PurchasesUpdatedListener` AND the on-resume `queryPurchasesAsync` sweep. The sweep is the safety net — check `purchase.isAcknowledged` and `purchaseState == PURCHASED` (never acknowledge PENDING), acknowledge anything unacknowledged.
- Only grant entitlement on `PURCHASED` state; `PENDING` (cash/slow payment methods, opted-in via `enablePendingPurchases` which is mandatory in v8) must show "pending" UI, not access.
- Since the proxy is frozen, acknowledgment is client-side only (`BillingClient.acknowledgePurchase`) — that's fine, but it means the app itself is the only thing that can acknowledge, so the resume sweep is non-negotiable.

**Warning signs:**
License tester purchases refund after **3 minutes** if unacknowledged (compressed from 3 days) and Google emails a cancellation notice — this is your canary. If a test purchase disappears minutes later, acknowledgment is broken. Also: test subscription renewals not firing = original purchase not acknowledged.

**Phase to address:**
Monetization/billing phase. Make "test purchase survives 10+ minutes and renews (accelerated ~5-min cycles, up to 6 renewals)" an explicit success criterion.

---

### Pitfall 2: Out-of-app promo-code redemptions never get processed

**What goes wrong:**
Gordian's operator gift system (lifetime one-time codes, 3-month sub trials) depends on promo codes, and one-time codes are redeemable **in the Play Store app while Gordian is closed**. If the app only handles purchases via `PurchasesUpdatedListener`, a gifted user opens the app and sees the paywall — the gift "didn't work." Worse: that out-of-app purchase is unacknowledged, so if they bounce off the paywall and don't return, it auto-refunds in 3 days and the gift evaporates.

**Why it happens:**
The listener-only pattern covers in-app purchase flows. Out-of-app redemption (and family sharing, resubscribe-from-Play-subscriptions-center) arrive with no callback. iOS's `Transaction.currentEntitlements` sweep habit maps to `queryPurchasesAsync`, but it's easy to run it only at cold start, not on every resume.

**How to avoid:**
- `queryPurchasesAsync` for BOTH `SUBS` and `INAPP` on every `onResume` (spike 005 already prescribes this — keep it in the plan as a requirement, not a nice-to-have).
- Acknowledge in the sweep (see Pitfall 1).
- Test the actual flow: redeem a one-time code in the Play Store app with Gordian killed, then open Gordian and verify entitlement + acknowledgment.

**Warning signs:**
Gift recipient reports "code worked in Play Store but app still shows paywall." Any entitlement logic that lives only inside the purchase-flow callback.

**Phase to address:**
Monetization/billing phase (sweep + acknowledge). Gift-code validation belongs in the pre-launch/publishing phase once real promo codes exist in Play Console.

---

### Pitfall 3: Data safety form contradicts what the app observably does → rejection loop

**What goes wrong:**
Play rejects the release (or later pulls the update) for an "invalid data safety form" because the declared practices don't match detected behavior. Gordian's specific exposure: `RECORD_AUDIO` permission + SpeechRecognizer, and dilemma text sent to the proxy. A form that says "no data collected" while the manifest requests the mic and the app ships user-typed dilemmas off-device is a mismatch Play's automated + human review flags. Each rejection cycle costs days and each resubmission during the closed-test window can reset your launch timeline.

**Why it happens:**
"Collected" in Play's definition means *transmitted off-device* (ephemeral processing still must be declared, just marked as ephemeral). Developers reason "we don't store anything, so nothing is collected" — wrong by Play's definition. Also the old pre-evolution app's manifest may carry permissions the new app no longer needs, widening the mismatch surface.

**How to avoid:**
- Audit the final merged manifest (`aapt dump permissions` on the release AAB) before filling the form; strip permissions the parity app doesn't use.
- Declare honestly: voice/sound (mic input — note: if audio only goes to the OS SpeechRecognizer service and only *text* leaves the app, declare the text; document the reasoning), "Other user-generated content" (dilemma text sent to proxy), mark ephemeral/not-stored where true, encrypted in transit.
- Privacy policy URL is mandatory and must mention mic/audio use and the proxy processing — reviewers cross-check it against the form.
- No AI mentions in user-facing copy is a product rule, but the privacy policy is a legal document — it can and should accurately describe automated processing without marketing "AI" language.

**Warning signs:**
Manifest contains permissions not covered by form answers; privacy policy silent on microphone; form says "no data shared/collected" for a networked app.

**Phase to address:**
Play Console/publishing phase for the form itself, but the manifest permission audit belongs in the rename/modernization phase so the form is filled against a clean manifest.

---

### Pitfall 4: The 12-testers/14-days gate treated as a checkbox instead of a schedule anchor

**What goes wrong:**
Personal Play accounts created after 2023-11-13 cannot touch production until a closed test has had **12 testers opted in continuously for the last 14 days** at the moment you apply for production access. Teams finish the app, then discover launch is 3+ weeks away: recruit 12 real humans, get them all opted in, wait 14 unbroken days (testers dropping out can reset the clock's "continuous" requirement), then pass a production-access questionnaire, then normal review. This is the single largest schedule risk in the whole milestone.

**Why it happens:**
The gate is invisible until you look for the "Apply for production" button. It's a calendar constraint, not an engineering one, so it doesn't show up in code-centric planning. PROJECT.md already flags account status as unconfirmed — unconfirmed means plan for the worst case.

**How to avoid:**
- Operator confirms account type/age in Play Console **now** (Phase 1 checklist item, not launch-week).
- If the gate applies: start the closed test with the *earliest usable build* — it does not need to be the final app; it needs to not crash. Recruit 12+ testers (13–15 for dropout margin) immediately and keep the test running while development continues. New builds pushed to the same track don't reset the clock; testers opting out can.
- Sequencing bonus: uploading a build with the billing permission to any track is also the prerequisite for creating/testing IAP products, so an early closed-test upload unblocks billing work too.

**Warning signs:**
No closed test running by the time monetization work starts. Fewer than 12 opted-in (not just invited) testers in the track. "Apply for production access" button absent or greyed.

**Phase to address:**
Play Console setup must be one of the FIRST phases (account verification + app creation + first closed-test upload), even though listing polish comes last. The 14-day clock should run concurrently with feature phases.

---

### Pitfall 5: applicationId rename done shallowly — or too late

**What goes wrong:**
Two failure modes. (a) Shallow rename: changing `applicationId` in `build.gradle.kts` but leaving the old AI-Studio-era package in `namespace`, manifest component names, `${applicationId}` placeholders (FileProvider authorities), Kotlin package declarations/imports, ProGuard rules, or deep-link hosts — producing an app that builds but has mismatched R-class/BuildConfig packages, broken providers, or a Play upload under the wrong ID. (b) Too late: `applicationId` becomes **permanent forever** on first Play upload (even to a closed track; even deleting the app doesn't free the ID). Uploading one early closed-test build under the old ID locks the wrong identity permanently.

**Why it happens:**
AGP splits `namespace` (compile-time: R class, BuildConfig, relative `.MainActivity` resolution) from `applicationId` (device/store identity). Changing one and not the other compiles fine and even runs — the breakage is subtle (provider authority collisions, wrong package in Play). And the permanence rule surprises everyone coming from iOS, where bundle IDs are also permanent but the App Store Connect flow makes it more visible.

**How to avoid:**
- Rename is Phase-1 work, completed and verified BEFORE the first Play upload (which Pitfall 4 wants early — so rename is the very first coding task).
- Rename both `applicationId` AND `namespace` (decide deliberately if they diverge; simplest is both `dev.gordian.app`), then grep the entire repo for the old package string — manifests, Kotlin packages, `google-services`-style config files, provider authorities, ProGuard.
- Watch `applicationIdSuffix ".debug"` on the debug build type: billing and license testing only work for the exact applicationId uploaded to Play, so billing testing must use a release-signed (or suffix-free) build. Either drop the suffix or accept that billing tests require the release variant.
- Old-app local data (Room DB, prefs) lives under the old package's data dir — a fresh ID means fresh data. That's fine here (no migration needed), but uninstall old-ID installs from test devices to avoid confusion.

**Warning signs:**
Old package string still greps anywhere in the repo; `adb shell pm list packages` shows two Gordians on a test device; Play Console shows the app registered under anything other than `dev.gordian.app`.

**Phase to address:**
Foundation/modernization phase (first phase), with "grep for old package returns zero hits + emulator boot under new ID" as the exit criterion.

---

### Pitfall 6: Edge-to-edge enforcement wrecks the bespoke dark/gold layout

**What goes wrong:**
Targeting API 35 forces edge-to-edge (opt-out attribute exists but is deprecated and **dead at API 36** — and new apps must target 36 by 2026-08-31, which is right around this project's likely production date). Content draws under the status bar and gesture/3-button nav bar. For a bespoke dark app: clock/battery icons vanish into the dark background if icon appearance isn't set, the 60-second timer or verdict card sits under the camera cutout, buttons hide behind the nav bar, and the keyboard covers the dilemma text field because nobody wired IME insets. The old pre-evolution app almost certainly predates this enforcement, so "it built fine before" is misleading.

**Why it happens:**
Insets became app responsibility exactly at the API level this project must target. iOS parity thinking makes it worse: SwiftUI safe areas are automatic; Compose insets are explicit (`Scaffold` handles some, custom full-bleed layouts handle nothing).

**How to avoid:**
- Target API 36 from day one (don't do 35 now and re-migrate in August — the deadline is 2026-08-31 for new apps, extensions to Nov 1 possible but not a plan).
- Establish the inset strategy in the design-system/scaffold phase, not per-screen: `enableEdgeToEdge()`, dark background behind system bars, `isAppearanceLightStatusBars = false` (light icons on dark), `WindowInsets.safeDrawing` padding at the screen-scaffold level, `imePadding()` on the dilemma input.
- Screenshot-verify (already a project rule) on: gesture nav, 3-button nav, and a cutout device profile — the emulator recipe should include at least one cutout AVD.

**Warning signs:**
Status bar icons invisible in screenshots; content clipped at top of screenshot; keyboard covering the input field; any screen that looks right only on the one emulator profile used daily.

**Phase to address:**
Core UI scaffold/design-system phase — insets are foundation, retrofitting them per-screen later is the expensive path.

---

### Pitfall 7: Predictive back (default-on at target 36) fights the 60-second session

**What goes wrong:**
At target API 36 on Android 16 devices, predictive back animations are on by default, `onBackPressed` isn't called, and `KEYCODE_BACK` isn't dispatched. A mid-session back gesture starts a peek-behind animation and can pop the user out of a running 60-second timed session — losing session state and, product-wise, burning a session the server gate already counted. Any legacy back-interception in the old app silently stops working.

**Why it happens:**
The behavior change is invisible until you run on an Android 16 device with target 36. iOS has no system back gesture with this semantics, so the parity spec has nothing to say about it — it's an Android-only design decision that must be made deliberately.

**How to avoid:**
- Use Compose `BackHandler`/`PredictiveBackHandler` (these participate correctly in predictive back) — never `onBackPressed` overrides.
- Decide back semantics per screen in the session-flow phase: during an active session, back should probably show a "leave session?" confirm (BackHandler intercepts); on verdict/home, default back is fine.
- Do NOT ship `android:enableOnBackInvokedCallback="false"` as a "fix" — it's a temporary opt-out crutch that just defers the migration.

**Warning signs:**
Back gesture during a session returns to home with no confirm; back does nothing at all on some screen (classic sign of legacy interception being ignored); QA only ever tested on Android 14/15 images.

**Phase to address:**
Session-flow phase (the timed session is where back semantics carry product weight); verify on an Android 16 emulator image.

---

### Pitfall 8: SpeechRecognizer is not SFSpeechRecognizer — endpointing kills mid-thought pauses

**What goes wrong:**
iOS SFSpeechRecognizer streams continuous partial results for up to ~1 minute and tolerates thinking pauses. Android's `SpeechRecognizer` aggressively endpoints: a 1–2 second pause triggers `onEndOfSpeech`/`ERROR_SPEECH_TIMEOUT` or `ERROR_NO_MATCH`, the silence-length extras (`EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`) are widely ignored by the actual recognizer implementations, rapid restarts hit `ERROR_RECOGNIZER_BUSY`, and behavior varies by OEM and Google-app version. For Gordian — a user describing a dilemma under a 60-second clock, pausing to think — a naive 1:1 port produces a mic that keeps cutting off exactly when the user hesitates. Also: AOSP emulator images have no recognition service at all, so speech "doesn't work" headlessly for reasons that have nothing to do with your code.

**Why it happens:**
The API surface looks equivalent (start listening, partial results, final result), so parity porting copies the iOS interaction model onto a service with fundamentally different endpointing behavior and no contract for silence tolerance.

**How to avoid:**
- Design for restart: on `onEndOfSpeech`/timeout/NO_MATCH during an active input window, accumulate the partial transcript and immediately restart listening (destroy + recreate the recognizer on errors; debounce restarts to dodge RECOGNIZER_BUSY). The UI shows one continuous dictation; the implementation is a restart loop.
- Guard with `SpeechRecognizer.isRecognitionAvailable()` and always keep the typed-text path first-class — speech is an accelerator, not the only input (this also de-risks the data safety story).
- Test on a Play-image emulator (Google app present) and at least one physical device; expect the OS listening beep and decide whether to accept it (muting the stream is a hack with side effects).
- Consider API 33+ on-device recognition (`createOnDeviceSpeechRecognizer`) for latency/privacy, but treat it as an optimization — availability varies.

**Warning signs:**
Transcription stops after the first sentence in manual testing; different behavior on emulator vs phone; `ERROR_NO_MATCH` spam in logcat; speech feature "works" only when the tester talks fast without pausing.

**Phase to address:**
Dedicated speech-input phase (or explicit sub-plan within session flow) — this is the highest-variance parity feature and should be flagged for its own device testing loop, after the session flow works with typed input.

---

### Pitfall 9: Trial/paywall copy and subscription plumbing that Play reviewers reject

**What goes wrong:**
Play's subscriptions policy rejects (or later flags) apps that: don't clearly disclose that a trial converts to an auto-renewing charge and the exact price/period *before* purchase; make the post-trial price hard to read; provide no in-app path to manage/cancel the subscription; or fail to restore entitlements on reinstall. Gordian's shape adds a twist: the 7-day trial is **app-managed and device-anchored (Play is not involved)**, so the Play purchase itself has no Play-side free trial — the paywall copy must not imply Play will handle a trial, and the trial→paid handoff must be unmistakably disclosed. Separately: Gordian must be listed as a **free app with in-app purchases**, never as a Play "paid app" (paid-app listing has no trial mechanism and would contradict the entire model).

**Why it happens:**
The iOS paywall copy was written for App Store conventions. Ported verbatim, it can violate Play's specific disclosure phrasing expectations, and the Android-specific requirements (deep link to Play's subscription center, resubscribe handling) have no iOS equivalent to port from.

**How to avoid:**
- Paywall states, adjacent to the purchase button: price, period, auto-renewal, and that the free week is provided by the app before any charge. Annual plan shows full $29.99/yr price (not only a "$2.50/mo" style breakdown).
- Add a "Manage subscription" affordance (settings or membership screen) deep-linking to `https://play.google.com/store/account/subscriptions?sku={productId}&package=dev.gordian.app`.
- "Restore" on Android = the `queryPurchasesAsync` sweep on launch/resume (Pitfall 2). No dedicated button is strictly required if entitlement restores automatically on reinstall — but verify the reinstall path explicitly: uninstall → reinstall → entitlement back without repurchase.
- Device-anchored trial: define the reinstall/reset behavior deliberately (server anchor already exists from iOS); a trial that resets on reinstall is a policy-adjacent and revenue problem.

**Warning signs:**
Paywall screenshot where the renewal price is smaller/dimmer than "free"; no subscription-management link anywhere in the app; reinstall shows the paywall to a paid user until they buy again.

**Phase to address:**
Monetization phase for plumbing (restore sweep, manage link); pre-launch/publishing phase for a dedicated paywall-copy compliance pass against the subscriptions policy checklist.

---

### Pitfall 10: Irreversible Play Console choices made casually during setup

**What goes wrong:**
Several Play Console decisions at app-creation time are permanent: the applicationId (Pitfall 5), the app's free/paid classification (a free app can never become paid — irrelevant here but the reverse trap: accidentally creating it as paid is unfixable without a new listing), and Play App Signing enrollment. For a new app, Play App Signing is mandatory with AABs; the real choice is Google-generated signing key vs. uploading your own — per-app permanent. If the old AI-Studio-era project has a debug/ad-hoc keystore, do NOT promote it to the production signing key.

**Why it happens:**
App creation feels like filling a form; nothing warns that three of the fields are forever. Coming from iOS where certificates rotate, the "signing key choice is permanent" framing doesn't register.

**How to avoid:**
- Let Google generate the app signing key (recommended path for a fresh app; enables key security + upload-key reset via support if the upload key is ever lost).
- Create the app as **Free** with in-app products.
- Do the creation step deliberately with a checklist: ID = `dev.gordian.app`, free, Google-generated signing key, correct developer account.
- Keep the upload keystore backed up outside the repo (it's resettable via support, but a reset costs days).

**Warning signs:**
App created in Play Console before the rename landed in code; any keystore file committed to the repo; "paid app" toggle set during creation.

**Phase to address:**
Play Console setup phase (early, per Pitfall 4) — with the rename (Phase 1) as a hard prerequisite.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| `enableOnBackInvokedCallback=false` to silence predictive back | Session flow "works" today | Must migrate anyway; animations broken on Android 16; flagged in review of behavior changes | Never for ship; OK for one debug build while diagnosing |
| Target API 35 now, "migrate to 36 later" | Slightly fewer behavior changes today | Full re-test in August 2026 exactly at launch crunch; new-app submissions require 36 from 2026-08-31 | Never — target 36 from the start |
| Grant entitlement in listener only, skip resume sweep | Less billing code | Gift codes broken, out-of-app purchases lost, auto-refunds (Pitfalls 1–2) | Never |
| Port old app's copy/screens and "fix wording later" | Faster first build | AI-visible copy is a hard product rule; every screen needs a copy audit anyway; risk of shipping a violation | OK for throwaway scaffolding builds never uploaded to Play |
| Keep old package name for local dev, rename "before upload" | Zero rename work now | One accidental early upload makes the wrong ID permanent; rename touches everything the longer you wait | Never — rename first |
| Speech via single start/stop call, no restart loop | Simple implementation | Mic cuts off on thinking pauses; the flagship input feels broken vs iOS | Only if speech is demoted to experimental behind the text path |
| Test billing only with license testers, never a real closed-track user | Free, fast | Accelerated test clocks (5-min renewals, 3-min ack window) mask real-world timing bugs; trial/renewal UX unverified at real cadence | Acceptable for MVP if the resume-sweep + acknowledge invariants are unit-tested; verify real cadence post-launch |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Play Billing (testing) | Reading `code=3 BILLING_UNAVAILABLE` on a signed-out emulator as breakage | Sign into a Google account on a Play-image emulator (spike 005 finding) |
| Play Billing (testing) | Testing purchases before any AAB with the billing permission is on a Play track → products invisible / `ITEM_UNAVAILABLE` | Upload first build to closed testing early; create products after; test with the same applicationId + version signed appropriately |
| Play Billing (testing) | Debug-suffix applicationId during billing tests | Billing works only for the exact uploaded applicationId; use the suffix-free/release variant for billing testing |
| Play Billing (products) | Two subscription products (porting the iOS two-product model) | ONE `plus` product, `monthly`/`annual` base plans; entitlement key = productId + basePlanId (spike 005) |
| Play Billing (v8) | Forgetting `enablePendingPurchases(PendingPurchasesParams…enableOneTimeProducts())` | Mandatory in v8; lifetime is a one-time product and must be opted in |
| Promo codes | Planning custom memorable codes for the lifetime gift | One-time products get auto-generated codes only (500/quarter cap, use-or-lose); memorable codes are subscription-only |
| Proxy | Adding server-side purchase verification / receipt endpoints | Server is frozen; entitlement is client-derived from Play Billing state only — accept the tamper risk (see Security) |
| SpeechRecognizer | Testing on AOSP emulator image | No recognition service there; use Play-image emulator + one physical device |
| Play Console | Filling data safety form from memory of what the app does | Fill from the final merged manifest + actual network behavior; cross-check privacy policy |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Recreating `BillingClient`/reconnecting per screen | `SERVICE_DISCONNECTED` errors, slow paywall | One app-scoped client, reconnect-with-backoff on disconnect | Immediately under normal navigation |
| Speech restart loop without debounce | `ERROR_RECOGNIZER_BUSY` cascades, battery drain, hot mic indicator flicker | Small delay + state machine around restarts; stop the loop when session input window closes | First real user who pauses twice |
| 60s timer driving whole-screen recomposition every frame | Janky countdown, dropped frames on mid-range devices | Isolate ticking state (frame-based `withFrameNanos` or 1Hz state) to the timer composable | Any low-end Android device (far wider device spread than iPhone) |
| Loading full session history into memory for Logs tab | Fine at 10 sessions, slow at hundreds | Room paging / limit queries from the start (Room already in stack) | Power users after a few months |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Client-only entitlement with no obfuscation of the check | Patched APKs unlock paid access (proxy is frozen, so no server verification possible this milestone) | Accept the residual risk consciously; keep entitlement logic non-trivial to stub, R8 enabled on release; the real gate (AI sessions) is server-side anyway — server session gate limits blast radius |
| Trusting client clock for the 7-day device-anchored trial | Clock rollback = infinite trial | Anchor trial server-side (iOS pattern presumably does; mirror it), never client date math alone |
| Logging dilemma text / transcripts in logcat or crash reports | Sensitive user content leaks; contradicts data safety form | Strip content from logs in release builds; audit before publishing |
| Committing upload keystore or Play service credentials to the repo | Key compromise; repo is private but standalone-repo rule exists for a reason | Keystore outside repo; document its location for the operator |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Fonts render subtly differently → the "1:1 port" reads as a knockoff | Gold/dark Art Deco identity depends on typography; Android default vertical metrics, letter-spacing, and font fallback differ from iOS | Bundle the exact typefaces (check license for Android embedding); Compose `includeFontPadding` is false by default on current Compose (good — verify), set explicit `lineHeight`/`letterSpacing` from the iOS spec values rather than eyeballing |
| Fixed layouts break at Android font scale 1.5–2.0 (non-linear scaling API 34+) | Timer/verdict card text clips or overlaps for accessibility users; also a review-quality signal | Use sp for text, test at 1.5x and 2.0x font scale in the screenshot-verification loop |
| Dark/gold colors look different on-device | sRGB assets rendered on wide-gamut/OLED screens shift the gold; screenshots on emulator ≠ device | Verify the hero gold on at least one physical OLED device early; define colors once in the design system |
| System back vs in-app back inconsistency | Android users expect gesture back everywhere; iOS parity spec has no answer | Decide back behavior per screen deliberately (Pitfall 7); never leave a screen where back does nothing |
| Play listing screenshots showing iOS UI or status bars | Listing rejection risk + user distrust | Capture listing assets from the Android build only |
| Speech beep + mic indicator surprises | Android plays an audible cue and shows a green mic dot; users mid-dilemma may be startled | Design the listening state UI around it; don't fight the OS indicator |

## "Looks Done But Isn't" Checklist

- [ ] **Purchase flow:** Often missing acknowledgment — verify a license-tester purchase survives >10 minutes and renews (unacknowledged = refund in 3 min for testers)
- [ ] **Gift codes:** Often missing out-of-app path — verify redeem-in-Play-Store-with-app-killed → open app → entitled + acknowledged
- [ ] **Reinstall restore:** Often missing — uninstall/reinstall as a purchased user shows entitlement without repurchase
- [ ] **Pending purchases:** Often missing — PENDING state shows "pending" UI, grants nothing, and completes correctly (test with slow-card test instrument)
- [ ] **Edge-to-edge:** Often missing nav-bar/cutout cases — screenshots on gesture nav, 3-button nav, cutout AVD, with keyboard open
- [ ] **Predictive back:** Often untested — back gesture mid-session on an Android 16 image shows confirm, not silent exit
- [ ] **Speech fallback:** Often missing — flow fully completable by typing when `isRecognitionAvailable()` is false
- [ ] **Copy audit:** Often incomplete — zero AI mentions AND zero old-app copy remnants, checked screen-by-screen against iOS spec
- [ ] **Manifest hygiene:** Often stale — no leftover permissions/components from the pre-evolution app before the data safety form is filled
- [ ] **Subscription management:** Often missing — deep link to Play subscription center reachable from within the app
- [ ] **Trial disclosure:** Often vague — paywall states price, period, auto-renew, and app-managed free week adjacent to the buy button
- [ ] **Font scale:** Often untested — key screens legible at 2.0x font scale

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Purchases auto-refunded (missing ack) | MEDIUM | Fix ack in listener + sweep; affected users must repurchase (or operator issues promo codes as goodwill); no way to un-refund |
| Wrong applicationId uploaded to Play | HIGH | ID is permanent; only recovery is a NEW app listing under the correct ID (new closed test, new 14-day clock if gated) — prevention is everything |
| Data safety rejection | LOW-MEDIUM | Correct form in App Content → Data safety, align privacy policy, resubmit; days of review latency per cycle |
| 14-day clock discovered late | HIGH (calendar) | Nothing compresses it; recruit testers immediately, upload any stable build, run clock in parallel with remaining dev |
| Closed-test tester dropout below 12 | MEDIUM (calendar) | Over-recruit to 15; monitor opted-in count weekly; re-recruit fast — "continuous" is judged at application time |
| Predictive back / edge-to-edge issues found at review | LOW | Code fixes are contained if the scaffold is inset-aware; a new build through the existing track |
| SpeechRecognizer unusable on some devices | LOW | Text path is first-class; ship speech as progressive enhancement, iterate |
| App-signing key choice regret | MEDIUM | Upload key: resettable via Play support (days). Google-managed signing key: key upgrade possible once for new installs only — avoid needing it |

## Pitfall-to-Phase Mapping

Suggested prevention phases (roadmap should order accordingly):

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| applicationId shallow/late rename (5) | Phase 1: Foundation & rename | `grep -r` old package = 0 hits; app boots under `dev.gordian.app`; target SDK 36 set |
| Irreversible Console choices (10) + 14-day gate (4) | Phase 2 (early, parallel): Play Console setup + first closed-test upload | App exists as Free, ID `dev.gordian.app`, Google-generated key; 12+ opted-in testers; clock start date recorded |
| Edge-to-edge/insets (6) | Phase 3: Design system & app scaffold | Screenshot matrix: gesture/3-button/cutout/IME, status-bar icons visible |
| Font/typography parity (UX) | Phase 3: Design system | Side-by-side screenshot vs iOS reference approved by user; 2.0x font scale pass |
| Predictive back (7) | Phase 4: Session flow | Back mid-session on Android 16 image → confirm dialog |
| SpeechRecognizer endpointing (8) | Phase 5: Speech input (own phase/sub-plan, after typed flow works) | Dictation with 3-second pauses completes on Play-image emulator + one physical device |
| Ack auto-refund (1), out-of-app redemption (2) | Phase 6: Monetization | License-tester purchase survives + renews; kill-app promo redemption entitles on next open |
| Trial disclosure / manage-sub / restore (9) | Phase 6 (plumbing) + Phase 7 (compliance pass) | Reinstall-restore test; paywall copy reviewed against Play subscriptions policy |
| Data safety mismatch (3) | Phase 7: Listing & publishing (manifest audit inherited from Phase 1) | Form answers derived from `aapt dump permissions` + network audit; privacy policy cross-checked |
| Target API deadline | Phase 1 (target 36) | `targetSdk = 36` in Gradle; no reliance on the Nov 2026 extension |

Phases needing deeper research flags: **Speech input** (device-variance, restart-loop tuning — highest unknowns) and **Monetization** (real Play Console product setup vs spike assumptions, promo-code promotion UI eyes-on confirmation noted open in spike 005).

## Sources

- Play Billing acknowledgment/3-day refund: [Integrate the Google Play Billing Library](https://developer.android.com/google/play/billing/integrate) (HIGH)
- Billing testing, license testers, 3-min ack refund, 5-min renewals, 6-renewal cap: [Test your Play Billing integration](https://developer.android.com/google/play/billing/test), [RevenueCat Android subscription testing guide](https://www.revenuecat.com/blog/engineering/the-ultimate-guide-to-android-subscription-testing), [Adapty billing error codes](https://adapty.io/blog/google-play-billing-library-in-app-purchase-error-codes/) (HIGH)
- 12-testers/14-days gate (personal accounts post 2023-11-13; reduced from 20 in Dec 2024): [Play Console Help — App testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465), [Play community guide](https://support.google.com/googleplay/android-developer/community-guide/255621488/everything-about-the-12-testers-requirement) (HIGH)
- Target API: 35 required now; 36 for new apps from 2026-08-31, extension to 2026-11-01: [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878), [developer.android.com target-sdk](https://developer.android.com/google/play/requirements/target-sdk) (HIGH)
- Edge-to-edge enforcement (35) and opt-out removal + predictive back default-on (36): [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) (HIGH)
- Data safety form rejections/mismatches: [Play Console Help — Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [webtonative invalid-form write-up](https://www.webtonative.com/blog/fixing-invalid-data-safety-form-android-rejection) (MEDIUM-HIGH)
- Subscription policy: restore required, cancel/manage paths, trial disclosure violations: [Manage subscriptions and one-time purchases](https://developer.android.com/google/play/billing/manage-purchases), [Play Console Help — Understanding subscriptions](https://support.google.com/googleplay/android-developer/answer/12154973), [Subscriptions policy help](https://support.google.com/googleplay/android-developer/answer/9900533) (HIGH)
- SpeechRecognizer endpointing/timeout/NO_MATCH behavior: community reports + API docs ([codingtechroom timeout thread](https://codingtechroom.com/question/prevent-speech-recognition-timeout-android), RecognitionListener samples) (MEDIUM — behavior is device/version-variant by nature; flagged for phase research)
- Project-specific facts (billing v8 mechanics, promo-code caps, signed-out emulator code=3, one-product/two-base-plan model): spike 005 findings, `.claude/skills/spike-findings-gordian-android/references/play-billing.md` (HIGH — locally proven)

---
*Pitfalls research for: iOS-to-Android parity port with Play subscription launch (Gordian Android)*
*Researched: 2026-07-25*
