# Build Toolchain (headless, Intel Mac, no Android Studio)

## Requirements

- Everything must run on the Intel Mac without Android Studio (none installed; not needed).
- Screenshot-verified iteration: build → install on emulator → screencap for user approval
  BEFORE committing UI changes.

## How to Build It

```bash
# JDK — the /usr/bin/java stub has no runtime; always export first:
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home

# Build (standalone Gradle 9.6.1 at /usr/local/bin/gradle — no wrapper in repo):
gradle assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk

# Emulator (AVD gordian-spike = system-images;android-36;google_apis_playstore;x86_64, pixel_6):
~/Library/Android/sdk/emulator/emulator -avd gordian-spike \
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
# wait: poll `adb -e shell getprop sys.boot_completed` for "1" (~30-60 s cold)

ADB=~/Library/Android/sdk/platform-tools/adb
$ADB -e install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -e shell am start -n com.aistudio.gordian.ovthnk/com.example.MainActivity
$ADB -e exec-out screencap -p > shot.png     # screenshot for approval loop
$ADB -e emu kill                             # shut down when done
```

One-time machine setup already done (2026-07-24): sdkmanager installed `emulator` +
`system-images;android-36;google_apis_playstore;x86_64`; AVD `gordian-spike` created;
`local.properties` (sdk.dir) and `debug.keystore` regenerated (both gitignored — regenerate with
`keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey`
if lost).

## What to Avoid

- Don't install Android Studio to "fix" anything — the headless stack covers build, install,
  screenshot, logcat.
- Don't add a gradle wrapper reflexively; system Gradle 9.6.1 is compatible with AGP 9.1.1.
  (Add a wrapper deliberately if CI enters the picture.)
- Don't run `adb` from two processes simultaneously on first daemon start — there's a one-time
  smartsocket race; just retry.
- Don't ship the debug source set: spike/probe activities live in `app/src/debug/` precisely so
  release builds exclude them.

## Constraints

- Versions proven together: AGP 9.1.1, Kotlin 2.2.10, Gradle 9.6.1, JDK 21 (brew openjdk@21),
  compileSdk/targetSdk 36, build-tools 36.x.
- Intel x86_64 system images only on this machine; swiftshader GPU for headless stability.
- `googleServices.missing.passthrough=true` keeps the build green without google-services.json.

## Origin

Synthesized from spike: 006
Source files: sources/006-android-toolchain-build/ (incl. boot-home.png evidence)
