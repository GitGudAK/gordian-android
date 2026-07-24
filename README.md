# Gordian (Android)

**Untie the knot.** Gordian is a decision app for people stuck in loops: describe what you're wrestling with, answer rapid-fire gut questions against a sixty-second clock, and get a straight answer — the decision, why, and one small next step.

This repository is the Android version, being modernized to parity with the shipped iOS product:

- Sessions are powered by a server-side proxy (no client API keys)
- Graduated safety system with server-side strikes
- Tangled dilemmas decompose into separate knots
- Free week, then subscription or one-time lifetime unlock (Google Play Billing)

## Layout

- `app/` — Kotlin / Jetpack Compose application
- `.planning/spikes/` — feasibility spikes for the modernization (billing model, toolchain, proxy client)
