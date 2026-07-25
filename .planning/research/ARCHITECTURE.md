# Architecture Research

**Domain:** Android client (Compose) for a paid consumer decision app — parity port of a shipped iOS app against a frozen Cloudflare Workers proxy
**Researched:** 2026-07-25
**Confidence:** HIGH (Compose/ViewModel/SavedStateHandle patterns from official docs; proxy + billing facts spike-proven 2026-07-24; Nav3/retain status web-verified against official Android sources)

## Core Recommendation (one paragraph)

Single-activity Compose app with **no navigation library for the session flow**. The faithful Compose equivalent of the iOS root view switching on an `@Observable` session view model is a sealed-interface `SessionState` rendered by one root `when(state)` composable inside `AnimatedContent`. Session state lives in a **`SessionViewModel` (ViewModel + StateFlow + SavedStateHandle)** — not Compose `retain{}` or plain state holders — because SavedStateHandle is the only mechanism that survives process death for an in-flight 60s session. The countdown is a **persisted deadline timestamp, not a decrementing counter**. Entitlement is an **app-scoped `EntitlementManager` singleton** consulted exactly once per session (at `startSession()`); a session in flight never re-checks it. The spike-proven OkHttp+Moshi client sits behind a `SessionRepository` that translates every proxy error into a typed `Offline` outcome with a local fallback, so the ViewModel never sees HTTP.

## Standard Architecture

### System Overview

```
┌────────────────────────────────────────────────────────────────────┐
│  UI LAYER — single Activity, Compose                               │
│  MainActivity → GordianRoot(when(tab)) → SessionRoot(when(state))  │
│  ┌──────┐ ┌─────────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────────┐  │
│  │ HOME │ │PREPARING│ │SESSION│ │VERDICT│ │PAYWALL│ │REFUSAL /  │  │
│  │      │ │         │ │ (60s) │ │       │ │       │ │LOCKOUT /  │  │
│  └──────┘ └─────────┘ └───────┘ └───────┘ └───────┘ │TOO_BIG    │  │
│      Logs tab │ Guides tab (enum tab state, no nav) └───────────┘  │
├────────────────────────────────────────────────────────────────────┤
│  STATE LAYER                                                       │
│  ┌───────────────────────────┐  ┌──────────────┐ ┌──────────────┐  │
│  │ SessionViewModel          │  │ LogsViewModel│ │GuidesViewModel│ │
│  │ StateFlow<SessionState>   │  │ (Room flows) │ │  (static)    │  │
│  │ + SavedStateHandle        │  └──────┬───────┘ └──────────────┘  │
│  │ + deadline timer          │         │                           │
│  └──────┬──────────┬─────────┘         │                           │
├─────────┼──────────┼───────────────────┼───────────────────────────┤
│  DOMAIN │/ DATA LAYER (app-scoped singletons, manual DI)           │
│  ┌──────▼─────┐ ┌──▼──────────────┐ ┌──▼──────────────┐            │
│  │ Session    │ │ Entitlement     │ │ DecisionLog     │            │
│  │ Repository │ │ Manager         │ │ Repository      │            │
│  │ (plan/     │ │ StateFlow<      │ │ (Room v2 +      │            │
│  │  verdict + │ │  Entitlement>   │ │  acted-on stats)│            │
│  │  fallback) │ └──┬───────────┬──┘ └──────┬──────────┘            │
│  └──────┬─────┘    │           │           │                       │
│  ┌──────▼─────┐ ┌──▼────────┐ ┌▼─────────┐ ┌▼─────────┐            │
│  │ProxyClient │ │ Billing   │ │TrialStore│ │  Room    │            │
│  │OkHttp+Moshi│ │ClientWrap │ │(DataStore│ │ database │            │
│  │X-Device-ID │ │(ktx 8.0.0)│ │ + device │ └──────────┘            │
│  └──────┬─────┘ └──┬────────┘ │  UUID)   │                         │
├─────────┼──────────┼──────────┴──────────┴─────────────────────────┤
│  EXTERNAL          │              ┌────────────────────┐           │
│  Cloudflare Workers proxy         │ SpeechCoordinator  │→ UI layer │
│  Play Billing / Play Store        │ (SpeechRecognizer) │           │
└────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| `MainActivity` | Theme + `setContent { GordianRoot() }`. Nothing else. | `ComponentActivity`, edge-to-edge |
| `SessionState` | The sealed state machine: `Home`, `Preparing`, `Session`, `Verdict`, `Refusal`, `Lockout`, `TooBig`, `Paywall` | Sealed interface, each variant carries its screen's data |
| `SessionViewModel` | Owns the state machine, timer, answer accumulation, entitlement gate at start, verdict fetch, log write | `ViewModel(savedStateHandle)`, exposes `StateFlow<SessionState>` + `StateFlow<Int>` countdown |
| `SessionRepository` | plan/verdict calls; maps every proxy response (incl. errors) to typed domain outcomes; owns offline fallback content | Plain class wrapping `ProxyClient` |
| `ProxyClient` | Raw HTTP only: two POSTs, Moshi codegen models, `X-Device-ID` header, `callTimeout(60s)` | Promote spike code (`ProxyContractSpike.kt`) verbatim |
| `EntitlementManager` | Single source of truth for access: purchase OR in-trial. Combines billing purchases + trial anchor into one `StateFlow<Entitlement>` | App-scoped singleton in `GordianApp` |
| `BillingClientWrapper` | billing-ktx 8.0.0 lifecycle: connect, `queryPurchasesAsync` (SUBS + INAPP) on every resume AND in listener, launch purchase flow | Wraps `BillingClient`, emits purchase set |
| `TrialStore` | Device-anchored 7-day trial: first-launch timestamp in DataStore, next to persisted device UUID | Jetpack DataStore (Preferences) |
| `DecisionLogRepository` | Insert verdict logs, acted-on updates, stats aggregates | Room v2 (schema below) |
| `DeviceIdentityStore` | One persisted `UUID.randomUUID()` for `X-Device-ID` — strikes/lockouts are keyed to it | DataStore; created once, never regenerated |
| `SpeechCoordinator` | Wraps `SpeechRecognizer` (evolve existing `VoiceRecognizer`); RMS levels + transcripts as callbacks/flows | UI-layer object (needs Activity context + permission), results funneled into ViewModel intents |
| `LogsViewModel` / `GuidesViewModel` | Logs list + acted-on stats; static guides content | Thin ViewModels over Room flows / static data |

## Key Question Answers

### 1. Sealed-class state machine vs navigation-compose — use the state machine

**The faithful Compose equivalent of the iOS "one root view switching on session state" is exactly that: `when(sessionState)` on a sealed interface.** Do not use navigation-compose routes and do not adopt Navigation 3 (stable 1.0.0 since 2025-11-19) for the session flow.

Rationale:
- HOME → PREPARING → SESSION → VERDICT is a **state machine, not a back stack**. There is no legitimate "back" from SESSION to PREPARING, or from VERDICT to SESSION. REFUSAL/LOCKOUT/TOO_BIG/PAYWALL are server- or entitlement-driven branches, not pushed destinations. A nav library's core abstraction (a user-navigable stack) is actively wrong here — you'd spend effort disabling its behavior.
- Back gesture semantics must be product semantics: `BackHandler` in SESSION means "abort session" (with the same confirm/abandon behavior as iOS), in VERDICT it means "done, go home", in PAYWALL it means "dismiss to home". One `BackHandler(enabled = state !is Home) { viewModel.onBack() }` at the root, decided by the ViewModel.
- Screen transitions: wrap the `when` in `AnimatedContent(targetState = state::class)` for cross-fades matching iOS.
- The top-level tabs (Focus / Logs / Guides) also stay as plain enum state — the old app already does this and it matches iOS tab behavior. **No navigation dependency at all.** If a genuinely stacked flow ever appears (it hasn't on iOS), Navigation 3 is the modern choice — its `NavDisplay` observes a developer-owned list of sealed keys, so migrating a sealed-state app to it later is cheap.

```kotlin
sealed interface SessionState {
    data object Home : SessionState
    data object Preparing : SessionState                       // gate + plan call in flight
    data class Session(val plan: SessionPlan, val questionIndex: Int,
                       val answers: List<Answer>) : SessionState
    data class Verdict(val verdict: VerdictData, val loading: Boolean) : SessionState
    data class Refusal(val risk: Risk) : SessionState          // self_harm → 988 copy
    data class Lockout(val until: Long, val strikes: Int) : SessionState
    data class TooBig(val knots: List<String>) : SessionState
    data object Paywall : SessionState
}
```

### 2. ViewModel + StateFlow (with SavedStateHandle) — not Compose state holders, not `retain{}`

Mid-2026 Compose offers three tiers: `remember`/plain state holders (survive recomposition), the new `retain{}` API in Compose 1.10 (survives configuration change, **not process death**), and ViewModel + SavedStateHandle (survives both). An in-flight 60-second session with money-adjacent semantics (the user paid for this session experience) must survive process death → **ViewModel + SavedStateHandle is the only correct tier.** Plain state holders remain appropriate for purely visual, throwaway state (text field drafts, mic RMS animation) hoisted inside individual screens.

**Process-death snapshot** (SavedStateHandle, all Parcelable/primitive):
- `scenario: String`, `plan` (mode, options, questions — serialize as JSON string or Parcelable), `answers` accumulated so far, `phase` marker, and `deadlineAtMillis`.
- On ViewModel init, if a snapshot exists: rebuild `SessionState` from it. If `deadlineAtMillis` has passed while dead → jump straight to the verdict fetch with the answers collected so far (mirrors "session in flight always finishes").

**The 60s countdown: persist a deadline, never a counter.** The old `MainViewModel` decrements an `Int` with `delay(1000)` — this drifts, pauses wrongly, and cannot be restored. Correct pattern:

```kotlin
// start: savedStateHandle["deadline"] = System.currentTimeMillis() + 60_000
private fun runTimer() {
    timerJob = viewModelScope.launch {
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
            _countdown.value = (remaining / 1000).toInt()
            if (remaining == 0L) { finishSessionAndFetchVerdict(); break }
            delay(250)  // sub-second tick keeps the arc smooth and self-correcting
        }
    }
}
```
Wall clock (`System.currentTimeMillis()`) rather than `elapsedRealtime()`: it stays meaningful across process death *and* reboot, and a 60s window doesn't care about clock-set edge cases. Consequence to confirm against iOS parity: the clock keeps counting while backgrounded (deadline-based timers do); if iOS pauses on background instead, store remaining-at-pause in the snapshot and rebuild the deadline on resume — a two-line variation of the same pattern.

### 3. Entitlement: app-scoped `EntitlementManager`, gate checked once at session start

Mirror the iOS EntitlementManager as an **application-scoped singleton** created in `GordianApp` (composition root), never per-screen — billing connections and trial state are app concerns, and PAYWALL can be reached from anywhere.

```kotlin
sealed interface Entitlement {
    data object Unknown : Entitlement                    // billing query in flight; fail-open per iOS rule if needed
    data class Lifetime(val fromGift: Boolean) : Entitlement
    data class Subscriber(val basePlanId: String) : Entitlement   // "monthly" | "annual"
    data class Trial(val expiresAtMillis: Long) : Entitlement
    data object Expired : Entitlement
}
```

- **Inputs:** (a) `BillingClientWrapper` — `queryPurchasesAsync` for SUBS and INAPP on every `onResume` *and* in the `PurchasesUpdatedListener` (promo codes redeem out-of-app; this replaces the iOS `Transaction.currentEntitlements` sweep); entitlement key is `productId` + `basePlanId`. (b) `TrialStore` — trial anchor timestamp written on first launch into DataStore alongside the device UUID (device-anchored, Play not involved).
- **Combine:** `combine(purchasesFlow, trialAnchorFlow) { ... }` → `StateFlow<Entitlement>`. Access = `Lifetime || Subscriber || Trial-not-expired`.
- **Gating rule (critical parity):** `SessionViewModel.startSession()` reads `entitlementManager.state.value` exactly once. Not entitled → `SessionState.Paywall`. Entitled → `Preparing` and the plan call fires. **From that moment the session state machine never consults entitlement again** — trial expiry or subscription lapse mid-session cannot interrupt SESSION or VERDICT. This is a one-line discipline that's easy to violate with reactive plumbing; do not `combine` entitlement into the session state flow.
- PAYWALL is a `SessionState` variant (matches the iOS state machine in the milestone context), rendered by the same root `when`. The paywall screen talks to `EntitlementManager.launchPurchase(activity, product)`; a successful purchase flips the flow and the paywall screen observes it to dismiss back to `Home`.

### 4. Room schema v2 for decision logs + acted-on stats

Replace the v1 entity (per-question logs shaped around the old client-side-Gemini flow) with a **one-row-per-session** entity shaped by the proxy verdict contract:

```kotlin
@Entity(tableName = "decision_logs")
data class DecisionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val scenario: String,
    val mode: String,            // BINARY | YES_NO | TOO_BIG (decomposed sessions log too)
    val optionA: String,
    val optionB: String,
    val decision: String,        // verdict fields, verbatim from proxy
    val sentiment: String,
    val analysis: String,
    val probe: String,
    val answersJson: String,     // Moshi-serialized List<Answer(question, choice, reflection)>
    val offline: Boolean,        // verdict came from local fallback
    val actedOn: Boolean?,       // null = not yet reported; true/false = user's answer
    val actedOnAt: Long?
)
```

- **Acted-on stats are DAO aggregates**, not client-side list math: `SELECT COUNT(*) FROM decision_logs WHERE actedOn = 1` (acted), `WHERE actedOn = 0` (didn't), `WHERE actedOn IS NULL` (unreported); follow-through rate computed in the ViewModel from those three flows. Room `Flow<Int>` queries keep the Logs tab live.
- `answersJson` as a serialized column (not a child table): answers are never queried individually, only displayed with their session — a relation table is complexity with no query to justify it.
- **Migration:** none needed — v1 has no shipped users and already sets `fallbackToDestructiveMigration()`. Bump to version 2, keep destructive fallback through development, then flip `exportSchema = true` and commit the schema JSON **before** the Play release; every schema change after publish needs a real `Migration`.

### 5. Proxy client integration + offline fallback

Two layers, sharply separated:

- **`ProxyClient`** — promote the spike code as-is: singleton OkHttp (`callTimeout(60s)`), Moshi+KSP codegen models (`ProxySessionPlan`, `ProxyVerdict`, `ProxyLockout`, error envelope), an interceptor adding `X-Device-ID` from `DeviceIdentityStore`. No Retrofit — two endpoints (spike guidance). Knows nothing about the app.
- **`SessionRepository`** — the only caller of `ProxyClient`. Translates transport into domain:

```kotlin
sealed interface PlanOutcome {
    data class Plan(val plan: SessionPlan) : PlanOutcome            // BINARY / YES_NO
    data class TooBig(val knots: List<String>) : PlanOutcome        // knots ride in questions[]
    data class Refusal(val risk: Risk, val lockout: LockoutInfo?) : PlanOutcome  // SENSITIVE
    data class Locked(val until: Long, val strikes: Int) : PlanOutcome           // LOCKED
    data class NotADecision(val message: String) : PlanOutcome
    data object Offline : PlanOutcome    // ANY error envelope, non-2xx, IOException, timeout
}
```

- **Fallback rule (from spike):** every proxy error code — `rate_limited`, `spend_cap`, `upstream_error`, network failure — collapses to `Offline`. The repository owns the fallback content (generic question bank for plans; canned verdict for `/v1/verdict` failures) so there is exactly one place where offline behavior lives; the ViewModel just renders what it gets and marks the log row `offline = true`. Loading copy describes *reflection*, never generation (no-AI-mentions rule).
- **Data-flow guarantee:** the ViewModel never sees HTTP status codes, JSON, or exceptions — only `PlanOutcome`/`VerdictOutcome`. This makes the state machine trivially unit-testable with a fake repository on the headless Intel-Mac toolchain (no emulator needed for logic tests).

## Recommended Project Structure

```
app/src/main/java/dev/gordian/app/          # applicationId rename lands here
├── GordianApp.kt            # Application; manual composition root (all singletons)
├── MainActivity.kt          # theme + setContent { GordianRoot() } only
├── ui/
│   ├── theme/               # Art Deco gold/dark — carries over from old app
│   ├── components/          # timer ring, verdict card, badges, wave bars
│   ├── session/             # HomeScreen, PreparingScreen, SessionScreen, VerdictScreen,
│   │                        #   RefusalScreen, LockoutScreen, TooBigScreen, PaywallScreen
│   ├── logs/                # LogsScreen + acted-on stats header
│   └── guides/              # GuidesScreen
├── session/
│   ├── SessionState.kt      # sealed interface (the state machine)
│   ├── SessionViewModel.kt  # StateFlow + SavedStateHandle + deadline timer
│   └── SessionRepository.kt # PlanOutcome/VerdictOutcome mapping + offline fallback
├── network/
│   ├── ProxyClient.kt       # OkHttp + interceptor (from spike 007)
│   └── ProxyModels.kt       # Moshi codegen DTOs (from spike 007)
├── entitlement/
│   ├── EntitlementManager.kt
│   ├── BillingClientWrapper.kt   # billing-ktx 8.0.0 (from spike 005)
│   └── TrialStore.kt             # DataStore trial anchor
├── data/
│   ├── DecisionDatabase.kt / DecisionDao.kt / DecisionLogEntity.kt
│   ├── DecisionLogRepository.kt
│   └── DeviceIdentityStore.kt    # DataStore persisted UUID
└── speech/
    └── SpeechCoordinator.kt      # evolved VoiceRecognizer
```

### Structure Rationale

- **Feature-first for the session (the product), layer-ish for shared infrastructure.** The session package holds state + logic together because the state machine IS the app; network/entitlement/data are genuinely shared services.
- **Manual DI in `GordianApp`, no Hilt.** Two real ViewModels and ~six singletons; a `GordianApp.container` with lazy properties plus ViewModel factories is fewer moving parts than a DI framework and matches the simplicity-first constraint. Adopt Hilt only if the object graph triples.
- **`speech/` is UI-adjacent, not a data source.** `SpeechRecognizer` needs Activity context and mic permission; keep it instantiated in composition (like today) but reduced to a thin coordinator whose results become ViewModel intents (`submitAnswer(choice, reflection)`), never direct state mutation.

## Data Flow

### Session request flow (happy path)

```
User taps "start" on HOME
    ↓
SessionViewModel.startSession(scenario)
    ↓ entitlement gate (ONCE): EntitlementManager.state.value
    ├─ not entitled → SessionState.Paywall (stop)
    ↓ entitled
SessionState.Preparing  →  SessionRepository.plan(scenario)
    ↓                          ↓ ProxyClient POST /v1/session-plan (X-Device-ID)
    ↓                      PlanOutcome
    ├─ Plan       → SessionState.Session + deadline written to SavedStateHandle, timer starts
    ├─ TooBig     → SessionState.TooBig(knots)
    ├─ Refusal    → SessionState.Refusal(risk, lockout)   // self_harm → 988, no penalty
    ├─ Locked     → SessionState.Lockout(until, strikes)  // server ladder copy
    └─ Offline    → SessionState.Session with local fallback plan
    ↓ (answers accumulate via submitAnswer intents; speech/text feed the same intent)
Deadline reached OR questions exhausted
    ↓
SessionState.Verdict(loading) → SessionRepository.verdict(scenario, answers)
    ↓ success or Offline-canned verdict
SessionState.Verdict(data) + DecisionLogRepository.insert(row)   // always logs, offline flagged
```

### State management (unidirectional)

```
Compose screens ──user intents──→ SessionViewModel ──calls──→ repositories/managers
      ↑                                   │
      └───── collectAsStateWithLifecycle ─┘   (StateFlow<SessionState>, StateFlow<Int> countdown)

EntitlementManager (independent loop):
  Play Billing listener + onResume queries ┐
  TrialStore DataStore flow ───────────────┴→ combine → StateFlow<Entitlement>
       consumed by: HOME (badge), PAYWALL (dismiss-on-purchase), startSession() gate (one-shot read)
```

### Key data flows

1. **Session flow:** UI intent → ViewModel → repository → typed outcome → new `SessionState` → recomposition. One direction, one owner.
2. **Entitlement flow:** billing events + trial anchor → `EntitlementManager` flow → observed by home/paywall; **read (not observed) by the session gate** so in-flight sessions can't be interrupted.
3. **Logs flow:** verdict completion writes Room row → `Flow<List<Entity>>` + aggregate count flows → Logs tab live-updates; acted-on taps write back through `DecisionLogRepository`.
4. **Speech flow:** `SpeechCoordinator` callbacks (transcript, RMS) → ViewModel intents → state. RMS animation may shortcut as local Compose state (pure visual, discardable).

## Suggested Build Order

Dependencies drive this; each phase is testable on the headless toolchain before the next starts.

| # | Phase | Contents | Depends on | Why this position |
|---|-------|----------|------------|-------------------|
| 1 | Foundation | Package/applicationId rename to `dev.gordian.app` (must precede any Play upload — permanent after publish); `GordianApp` composition root; DataStore module (device UUID + trial-anchor slot); theme carry-over | — | Everything imports these; the rename touches every file so do it before new code multiplies the diff |
| 2 | Proxy layer | `ProxyClient` + Moshi models promoted from spike 007; `DeviceIdentityStore` wired to interceptor; `SessionRepository` with full `PlanOutcome` mapping + offline fallback content; unit tests against live proxy (spike pattern, fresh UUIDs in tests only) | 1 | Pure JVM-testable; proves the contract in this codebase before any UI depends on it |
| 3 | Session state machine | `SessionState` sealed interface; `SessionViewModel` (SavedStateHandle snapshot + deadline timer); HOME → PREPARING → SESSION → VERDICT screens; `BackHandler` semantics; existing `VoiceRecognizer` carried as-is | 2 | The product core; needs only repository fakes + real repository. Screenshot/sign-off gate applies |
| 4 | Safety + TOO_BIG surfaces | `Refusal` (988 copy), `Lockout` (ladder copy), `TooBig` (knot decomposition UI) branches | 3 | Pure additions to the sealed class; server already drives them via outcomes from phase 2 |
| 5 | Persistence + Logs | Room v2 entity/DAO; log write at verdict; Logs tab with acted-on stats; `exportSchema = true` before release | 3 | Needs the verdict shape finalized in 3–4; independent of billing |
| 6 | Monetization | `TrialStore` activation; `BillingClientWrapper`; `EntitlementManager`; PAYWALL state + gate wired into `startSession()`; membership badges; redeem flow | 1, 3 | Last of the core because it's the only phase blocked on Play Console setup (products, signed-in device); trial logic alone could land earlier if sequencing demands |
| 7 | Polish + ship | `SpeechCoordinator` formalization; Guides tab; full AI-invisible copy sweep; Play listing, data safety form, closed test | all | Copy sweep must be last-pass over every screen |

Note on phase 6 ordering: the app is fully exercisable end-to-end after phase 4 with entitlement stubbed to "entitled" — this keeps the Play-Console dependency off the critical path for UI sign-off.

## Architectural Patterns

### Pattern 1: Sealed state machine as the single navigation source

**What:** One `StateFlow<SessionState>`; root composable renders `when(state)` inside `AnimatedContent`; `BackHandler` delegates to `viewModel.onBack()`.
**When to use:** Flows where transitions are product rules, not user browsing — exactly this app.
**Trade-offs:** No deep links / no free back stack (neither exists on iOS, so nothing is lost); transitions are exhaustively testable as pure functions; adding a state is a compiler-enforced checklist across the `when`.

### Pattern 2: Deadline-based timer with SavedStateHandle snapshot

**What:** Persist `deadlineAtMillis`; a coroutine recomputes remaining every 250ms; restore-from-snapshot re-derives everything, including "deadline already passed → fetch verdict".
**When to use:** Any countdown that must survive rotation, backgrounding, and process death.
**Trade-offs:** Marginally more code than `delay(1000)` decrements; eliminates drift and the entire class of "timer forgot where it was" bugs the old app has.

### Pattern 3: Outcome-typed repository boundary

**What:** Repositories return exhaustive sealed outcomes (`PlanOutcome`); transport errors are a domain case (`Offline`), not exceptions.
**When to use:** Whenever the server encodes product logic in response shapes (mode = LOCKED/SENSITIVE/TOO_BIG here).
**Trade-offs:** A mapping layer to maintain — but it is the parity contract made explicit, and it makes the ViewModel test suite proxy-independent.

### Pattern 4: One-shot gate read vs. reactive entitlement

**What:** Screens *observe* `Entitlement` (badges, paywall dismissal); the session gate *reads it once* at start.
**When to use:** "Sessions in flight always finish" semantics — any paid product where mid-flow revocation is worse UX than a slightly generous boundary.
**Trade-offs:** A user whose trial expires mid-session finishes free — exactly the iOS behavior, by design.

## Anti-Patterns

### Anti-Pattern 1: Navigation library routes for session phases
**What people do:** Model HOME/SESSION/VERDICT as nav destinations because "Compose apps use Navigation."
**Why it's wrong:** System back then pops VERDICT→SESSION→PREPARING, resurrecting dead states; you fight the library to forbid its core feature.
**Do this instead:** Sealed state machine (Pattern 1). Adopt Navigation 3 only if a real stacked flow appears later.

### Anti-Pattern 2: Counter-decrement timer (the old app's bug)
**What people do:** `while (count > 0) { delay(1000); count-- }` in a ViewModel.
**Why it's wrong:** Drifts, double-runs after re-entry, and evaporates on process death — an in-flight paid session is lost.
**Do this instead:** Deadline timestamp in SavedStateHandle (Pattern 2).

### Anti-Pattern 3: God ViewModel (the old `MainViewModel`)
**What people do:** One ViewModel owning tabs, session, API calls, prompt text, speech state, and Room writes (879 lines today).
**Why it's wrong:** Untestable, unrestorable, and the copy/prompt content embedded in it violates the AI-invisible rule anyway — it must all be replaced, not evolved.
**Do this instead:** `SessionViewModel` + `LogsViewModel` over repositories; treat the old file as a reference for screen inventory only.

### Anti-Pattern 4: Fresh device UUID per install/run
**What people do:** Regenerate the `X-Device-ID` (fine in spike tests, fatal in production).
**Why it's wrong:** Safety strikes and lockouts are keyed to the UUID server-side; regenerating launders lockouts and breaks the reinstall-proof safety design.
**Do this instead:** One `DeviceIdentityStore` UUID, written once to DataStore, never rotated. (Accepted limit: uninstall wipes DataStore — same as iOS device-anchoring semantics; do not add backup-based persistence without checking iOS behavior.)

### Anti-Pattern 5: Reactive entitlement inside the session flow
**What people do:** `combine(entitlementFlow, sessionFlow)` so state "stays consistent."
**Why it's wrong:** Trial expiry at second 40 of a session would yank the user to PAYWALL — violates "sessions in flight always finish."
**Do this instead:** One-shot gate read at `startSession()` (Pattern 4).

### Anti-Pattern 6: Porting iOS billing structure literally
**What people do:** Two subscription products (monthly, annual) mirroring App Store Connect.
**Why it's wrong:** Play-native is ONE product (`plus`) with two base plans; two products break upgrade/downgrade flows (spike 005).
**Do this instead:** Entitlement key = `productId` + `basePlanId`; query SUBS + INAPP on every resume for out-of-app promo redemptions.

## Scaling Considerations

Client app against a frozen shared proxy — server scaling is out of scope. The relevant "scale" axes:

| Axis | Concern | Approach |
|------|---------|----------|
| Data volume | Decision log grows unbounded locally | Room + Flow paging is a non-issue below ~10k rows; no pagination needed at launch |
| Feature growth | More states/screens | Sealed class scales linearly; if stacked flows appear, Navigation 3 migration is straightforward (sealed keys map 1:1) |
| Object graph | More singletons/ViewModels | Manual DI holds to ~15 objects; introduce Hilt only past that |

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Gordian proxy (Cloudflare Workers) | Plain OkHttp POSTs, Moshi codegen, `X-Device-ID` header, 60s call timeout | Contract frozen; 4/4 live-verified 2026-07-24. Every error → offline fallback. Body ≤16KB, answers ≤20 (server-enforced) |
| Play Billing | billing-ktx 8.0.0; `PendingPurchasesParams` with `enableOneTimeProducts()` (mandatory in v8); query SUBS+INAPP on resume + listener | Full flow testing needs Play Console products + signed-in Play-image emulator; `code=3` when signed out is normal |
| Android SpeechRecognizer | Existing `VoiceRecognizer` wrapper, evolved to `SpeechCoordinator` | Activity-context bound; permission flow already implemented in old app |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| UI ↔ SessionViewModel | Intents down (function calls), `StateFlow` up | UI never mutates state; local Compose state only for throwaway visuals |
| SessionViewModel ↔ SessionRepository | suspend calls returning sealed outcomes | No HTTP types cross this line |
| SessionViewModel ↔ EntitlementManager | One-shot `.value` read at gate; no subscription | The "sessions always finish" boundary |
| Paywall/Home UI ↔ EntitlementManager | Observe `StateFlow<Entitlement>` | Purchase launch needs Activity reference passed at call time |
| Verdict completion ↔ DecisionLogRepository | Fire-and-forget insert from ViewModel scope | Always log, `offline` flag distinguishes fallback verdicts |
| SpeechCoordinator ↔ ViewModel | Callbacks → intents | Coordinator holds no session state |

## Sources

- Spike findings (HIGH — live-verified in this repo 2026-07-24): `.claude/skills/spike-findings-gordian-android/references/proxy-client.md`, `references/play-billing.md`
- Existing code surveyed: `app/src/main/java/com/example/MainViewModel.kt`, `MainActivity.kt`, `data/DecisionDatabase.kt`
- [Android Developers Blog — Jetpack Navigation 3 is stable](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html) (HIGH — official; stable 1.0.0, 2025-11-19)
- [navigation3 releases — Android Developers](https://developer.android.com/jetpack/androidx/releases/navigation3) (HIGH)
- [State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state), [Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting), [State lifespans in Compose](https://developer.android.com/develop/ui/compose/state-lifespans) (HIGH — official guidance on ViewModel vs state holders vs `retain`)
- [ViewModel overview — Android Developers](https://developer.android.com/topic/libraries/architecture/viewmodel) (HIGH — SavedStateHandle/process-death)
- [Process Death and SavedStateHandle — Atomic Spin](https://spin.atomicobject.com/android-process-death/), [Compose retain vs ViewModel — Android Engineering Notes](https://www.davideagostini.com/android/2026-02-24-compose-retain-vs-viewmodel) (MEDIUM — community, consistent with official docs)

---
*Architecture research for: Gordian Android client (iOS parity port)*
*Researched: 2026-07-25*
