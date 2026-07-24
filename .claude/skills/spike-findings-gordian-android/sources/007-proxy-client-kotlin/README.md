---
spike: 007
name: proxy-client-kotlin
type: standard
validates: "Given the live proxy, when Kotlin/OkHttp calls /v1/session-plan with X-Device-ID, then all modes decode and lockout state round-trips"
verdict: VALIDATED
related: [005, 006]
tags: [proxy, okhttp, contract]
---

# Spike 007: proxy-client-kotlin

## What This Validates
Given the live proxy (`gordian-proxy.gordian-app.workers.dev`), when Kotlin/OkHttp/Moshi calls
`/v1/session-plan` and `/v1/verdict` with an `X-Device-ID` UUID header, then all session modes
decode (BINARY / TOO_BIG / SENSITIVE with risk + lockout) and a verdict round-trips from answers.

## Research
Contract mirrored from the shipped iOS client (`ProxyClient.swift`):
- `POST /v1/session-plan` `{scenario}` → `{mode, optionA, optionB, questions[], risk?, lockout?{until, strikes}}`
- `POST /v1/verdict` `{scenario, answers[{question, choice, reflection}]}` → `{decision, sentiment, analysis, probe}`
- TOO_BIG reuses `questions[]` to carry the 2–4 decomposed knots.
- The app's existing deps (okhttp, moshi-kotlin + codegen, retrofit) cover the client with zero additions.

## How to Run
```
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
gradle :app:testDebugUnitTest --tests "com.example.spike.ProxyContractSpike"
```
Runnable lives at `app/src/test/java/com/example/spike/ProxyContractSpike.kt` (JVM unit test,
live network). Uses a fresh random device UUID per run: no meter buildup, no strike buildup.

## What to Expect
4/4 green in ~25 s (gate ~2 s + premium plan calls).

## Investigation Trail
- 2026-07-24: First run 4/4 in 22.7 s against the live proxy:
  - BINARY: `optionA='Spanish' optionB='German'`, 12 questions.
  - TOO_BIG: 3 knots, blocking decision first ("Should I quit my job and sell my house?" /
    "Should I move to Lisbon?" / "Should I end my long-distance relationship?").
  - SENSITIVE: `risk=harm_others`, `lockout={until: 0, strikes: 1}` — first offense on a fresh
    device is a warning with no clock, matching the server ladder.
  - VERDICT: "Take the new job." + concrete probe ("Send your signed offer letter ... today.").

## Results
VALIDATED. The Kotlin stack speaks the proxy contract exactly as iOS does; the entire AI/safety
surface needs zero Android-specific server work. Moshi note: reflection adapter not needed —
`@JsonClass(generateAdapter = true)` + existing KSP codegen handled all models.
