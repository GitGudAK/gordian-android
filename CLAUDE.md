# Gordian Android

60-second decision-paralysis-bypass app — Android version (Kotlin/Jetpack Compose, `app/`).
This repo is intentionally standalone: no iOS code, no proxy code, no shared artifacts.
The iOS product (shipped) and the Cloudflare Workers proxy live in the separate `gordian` repo.

## Ground rules

- The AI proxy is consumed over HTTPS only: `https://gordian-proxy.gordian-app.workers.dev`.
  No API keys in this repo, ever. Device identity is an app-generated random UUID sent as `X-Device-ID`.
- Never mention AI in user-facing copy. The product is a self-reflection exercise.
- Business model: free 7-day trial (device-anchored), then paid — monthly $4.99 / annual $29.99 /
  lifetime $69.99 via Google Play Billing. No freemium, no meters.
- Safety: server classifies risk (none / self_harm / harm_others / illegal). Self-harm refusals are
  supportive (crisis line, no penalty); harm/illegal refusals count strikes server-side
  (3 warnings, then 5 min / 30 min / 24 h locks).
- For UI changes: build and screenshot on the emulator, get user approval BEFORE committing.

## State

- `.planning/spikes/` — modernization spikes (numbering continues from the iOS spike series at 005).
