# Spike Conventions

Patterns and stack choices established across spike sessions. New spikes follow these unless the
question requires otherwise. (Android repo — supersedes the iOS-era Swift conventions.)

## Stack
- **No Android Studio.** Everything runs headless: brew `openjdk@21` for the JDK
  (`export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home` — the
  `/usr/bin/java` stub finds no runtime without it), standalone Gradle 9.6.1, SDK at
  `~/Library/Android/sdk` (build-tools 36.x, platforms android-36, cmdline-tools, platform-tools).
- Project: AGP 9.1.1, Kotlin 2.2.10, compileSdk/targetSdk 36, Compose. No gradle wrapper in-repo;
  system Gradle works.
- HTTP/JSON: **OkHttp + Moshi with KSP codegen** (`@JsonClass(generateAdapter = true)`) — already
  app deps; zero additions needed for the proxy client.

## Structure
- One directory per spike: `.planning/spikes/NNN-name/` with `README.md` (frontmatter, Research,
  Investigation Trail, Results) and captured evidence (screenshots).
- Runnable spike code lives where Gradle can build it, referenced from the spike README:
  JVM contract tests in `app/src/test/java/com/example/spike/`, device probes in the
  **debug source set** `app/src/debug/java/com/example/spike/` (+ debug AndroidManifest) so they
  can never ship in a release build.
- Numbering continues the iOS series (001–004 live in the gordian repo).

## Patterns
- **Emulator recipe (Intel Mac):** AVD `gordian-spike` = `system-images;android-36;google_apis_playstore;x86_64`,
  pixel_6 profile; boot headless `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`;
  poll `adb -e shell getprop sys.boot_completed`; screenshot via `adb -e exec-out screencap -p`.
- **Live proxy tests use a fresh random `X-Device-ID` UUID per run** — avoids the per-device meter
  and safety-strike accumulation.
- Debug builds need a local `debug.keystore` (gitignored): regenerate with keytool
  (storepass/keypass `android`, alias `androiddebugkey`) — the build config expects it at repo root.
- `local.properties` (gitignored) carries `sdk.dir`.

## Tools & Libraries
- billing-ktx 8.0.0 (PendingPurchasesParams mandatory in v8) — debugImplementation until the real
  billing phase.
- Avoid: gradle wrapper regeneration churn (system Gradle 9.6.1 is compatible), Robolectric for
  network spikes (plain JVM tests are faster and sufficient).
