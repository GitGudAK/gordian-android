---
spike: 006
name: android-toolchain-build
type: standard
validates: "Given this Intel Mac (no Android Studio), when we install the toolchain and run the Gradle build, then the existing app compiles and boots on an emulator"
verdict: VALIDATED
related: [005, 007]
tags: [toolchain, gradle, emulator]
---

# Spike 006: android-toolchain-build

## What This Validates
Given this Intel Mac (no Android Studio installed, unknown SDK state), when we install/repair the
toolchain and run the Gradle build, then the pre-modernization app compiles and boots on an emulator.

## Research
Machine state discovered:
- Android SDK already at `~/Library/Android/sdk` (build-tools 36.1.0, platforms android-36/36.1,
  cmdline-tools, platform-tools) — leftover from the original Android build. No Android Studio.
- `/usr/bin/java` is the macOS stub; the real JDK is brew's `openjdk@21` keg (21.0.11), unlinked.
- Standalone Gradle 9.6.1 at `/usr/local/bin/gradle`; **no gradle wrapper in the repo**.
- Project is current: AGP 9.1.1, Kotlin 2.2.10, compileSdk 36 — all mutually compatible with
  Gradle 9.6.1 + JDK 21.

## How to Run
```
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
gradle assembleDebug
~/Library/Android/sdk/emulator/emulator -avd gordian-spike -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
~/Library/Android/sdk/platform-tools/adb -e install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb -e shell am start -n com.aistudio.gordian.ovthnk/com.example.MainActivity
```

## What to Expect
`app-debug.apk` (~23 MB); emulator boots in well under a minute; app renders the (old) home screen.

## Investigation Trail
- 2026-07-24: First `assembleDebug` succeeded end-to-end — Gradle auto-installed missing
  build-tools 36.0.0 and accepted licenses itself. Only manual fixes needed:
  `debug.keystore` regenerated via keytool (gitignored in the old repo, never copied),
  `local.properties` written with sdk.dir, stale template test `GreetingScreenshotTest.kt`
  deleted (referenced a long-removed `Greeting` composable).
- Emulator: installed `emulator` + `system-images;android-36;google_apis_playstore;x86_64`
  via sdkmanager (~1.5 GB); AVD `gordian-spike` (pixel_6 profile); headless boot with
  swiftshader completed in ~20 s of polling. adb had a one-time daemon startup race
  (two parallel starts); resolved itself.
- App installed and launched; `boot-home.png` captured.

## Results
VALIDATED. Compile and boot both proven; screenshot at `boot-home.png`.
Surprises: (1) toolchain was 90% present already — no Android Studio needed at all;
(2) the app's own copy is the strongest argument for the modernization: the old home screen
says "cognitive analysis engine" and "hyper-personalized bypass questions", violating the
product's AI-invisible rule, and the old three-tab shell (FOCUS/INSIGHTS/GUIDES) predates
the iOS UX overhaul entirely.
No Intel-Mac ceiling here (unlike the Xcode 16.4 wall): current AGP/Gradle/JDK all run fine.
