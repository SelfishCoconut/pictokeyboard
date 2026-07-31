---
name: run
description: Build, install and drive the Android app on a device or emulator so a change can be seen working — launching it, reading logs, and capturing screenshots. Use for "run the app", "install it", "show me it working", "take a screenshot".
---

# Run the app on a device

The goal is to see the change actually working, not to see a build succeed. A green `assembleDebug` proves compilation; it proves nothing about behavior.

## 1. Find a target

```sh
adb devices -l
```

- No device: start an emulator with `emulator -list-avds` then `emulator -avd <name> -no-snapshot-load &`, and wait for it with `adb wait-for-device shell getprop sys.boot_completed` returning `1`.
- Several devices: every subsequent `adb` command needs `-s <serial>`. Ask which one rather than guessing.

## 2. Build and install

```sh
./gradlew installDebug
```

Prefer `installDebug` over `assembleDebug` + manual `adb install`: it picks the right ABI and handles reinstall-over-different-signature. If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall first (`adb uninstall <applicationId>`) — and say so, because it wipes app data.

Read `applicationId` from `app/build.gradle.kts`; do not assume it matches the namespace.

## 3. Launch

Ordinary app:

```sh
adb shell monkey -p <applicationId> -c android.intent.category.LAUNCHER 1
```

or, when you know the entry point, `adb shell am start -n <applicationId>/.ui.MainActivity`.

### Components with no launcher entry

Input methods, accessibility services, wallpapers, tiles and widgets are not launched — they are *enabled* in system settings and then exercised from another app. Enable the component, then drive it in a real host:

```sh
# Input method
adb shell ime list -a                       # find the exact id
adb shell ime enable <applicationId>/.<ServiceClass>
adb shell ime set    <applicationId>/.<ServiceClass>

# Accessibility service
adb shell settings put secure enabled_accessibility_services <applicationId>/.<ServiceClass>

# Open a host app to exercise it against
adb shell am start -a android.intent.action.VIEW -d "https://example.com"
```

**Record the previous value before you change any of these, and restore it afterwards.** Leaving someone's device on a half-finished test input method or accessibility service is a real disruption — for an IME it can leave them unable to type.

## 4. Observe

Logs, filtered to the app and cleared first so the output belongs to this run:

```sh
adb logcat -c
adb logcat --pid=$(adb shell pidof -s <applicationId>) *:W
```

For a service running inside another app's flow (IME, accessibility service), `pidof` the *service's own* package, not the host app's.

Screenshots:

```sh
adb exec-out screencap -p > /tmp/screen.png
```

Read the PNG back to actually look at it. A screenshot nobody views is not verification. When the component draws over another app, screenshot the *host* app with your UI raised — that is the only view showing the real composition.

## 5. Report honestly

Say what you observed, not what should have happened. If the app crashed, paste the stack trace from logcat. If you could not reach the screen the change affects, say that rather than implying it works. "Builds and installs" is not "works".

## Teardown

Stop the emulator you started, restore the IME you changed, and leave the device in the state you found it.
