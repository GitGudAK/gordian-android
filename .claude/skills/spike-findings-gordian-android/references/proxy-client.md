# Proxy Client (Kotlin ↔ Gordian Workers proxy)

## Requirements

- AI-always via the shared proxy `https://gordian-proxy.gordian-app.workers.dev`. No client keys.
- Device identity: app-generated random UUID, persisted, sent as `X-Device-ID` on every call.
- No AI mentions in user-facing copy (loading states describe reflection, not generation).

## How to Build It

Contract (identical to shipped iOS `ProxyClient.swift` — server needs ZERO Android changes):

- `POST /v1/session-plan` `{"scenario": "..."}` →
  `{mode, optionA, optionB, questions[], risk?, lockout?{until, strikes}}`
  - `mode`: `BINARY` | `YES_NO` | `SENSITIVE` | `TOO_BIG` | `NOT_A_DECISION` | `LOCKED`
  - TOO_BIG carries the 2–4 decomposed knots IN `questions[]` (optionA/B empty)
  - SENSITIVE: `risk` = `self_harm` (supportive path, no penalty) | `harm_others` | `illegal`;
    `lockout.until` epoch-ms (0 = warning only), `lockout.strikes` count
- `POST /v1/verdict` `{"scenario": "...", "answers": [{question, choice, reflection}]}` →
  `{decision, sentiment, analysis, probe}`
- Errors arrive as `{error: {code, message}}` with non-2xx status — treat every code
  ("rate_limited", "spend_cap", "upstream_error", …) as "use the offline fallback".

Models (Moshi + KSP codegen, deps already in the app — no additions):

```kotlin
@JsonClass(generateAdapter = true)
data class ProxyLockout(val until: Double, val strikes: Int)

@JsonClass(generateAdapter = true)
data class ProxySessionPlan(
    val mode: String, val optionA: String, val optionB: String,
    val questions: List<String>, val risk: String?, val lockout: ProxyLockout?,
)

@JsonClass(generateAdapter = true)
data class ProxyVerdict(
    val decision: String, val sentiment: String, val analysis: String, val probe: String,
)
```

OkHttp with `callTimeout(60s)` (gate ~2 s; premium plan calls 5–10 s — always show a loading
state). Full working client: `sources/007-proxy-client-kotlin/ProxyContractSpike.kt`; rerun via
`gradle :app:testDebugUnitTest --tests "com.example.spike.ProxyContractSpike"`.

## What to Avoid

- Don't use a fixed device UUID in tests — fresh `UUID.randomUUID()` per run avoids the
  per-device meter and safety-strike accumulation. (The REAL app must do the opposite:
  one persisted UUID, because strikes/lockouts are keyed to it — that's the reinstall-proof
  safety design.)
- Don't add Retrofit service interfaces for two endpoints — plain OkHttp calls are sufficient
  (spike pattern), though Retrofit is available if the app grows.
- Don't special-case TOO_BIG payload shape — it's the same ProxySessionPlan, knots in questions[].

## Constraints

- Live-verified 2026-07-24: 4/4 (BINARY custom options + 12 questions; TOO_BIG 3 knots ordered
  blocking-first; SENSITIVE harm_others strikes=1 until=0 on fresh device; verdict round-trip).
- Server safety ladder (mirror in UI copy): 3 warnings, then 5 min / 30 min / 24 h locks;
  self_harm never penalized, gets crisis line (US 988).
- Scenario limits enforced server-side (body ≤ 16 KB; answers ≤ 20).

## Origin

Synthesized from spike: 007
Source files: sources/007-proxy-client-kotlin/
