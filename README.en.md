# Mio

[简体中文](README.md) | [English](README.en.md)

Mio is an Android host application for Minecraft assistance and input mapping. It is built around a **floating-window UI + DSL plugin system + input injection + WebSocket game integration**.

> This project is intended for learning, research, and personal use only. Please follow Minecraft server rules and local laws. Do not use it to gain unfair advantages or for any illegal purpose.

## Offline Build

This repository ships an **Offline Build** with the following behavior:

- Skips server verification and version checks at startup; the home screen opens without a network connection.
- Login, registration, password recovery, forum, and membership pages are bypassed — the app is ready to use immediately.
- The user agreement is bundled locally, so the "I Agree" button works offline.
- **Network features are still available**: the WebSocket service, plugin downloads, and online APIs work normally once connected.

### Download & Install

Download the APK from [Releases](https://github.com/YUNDATAFw/Mio-Keymapper/releases/latest):

| File | Target devices |
|---|---|
| `mio-1.2.9-universal.apk` | Universal package; choose this if unsure |
| `mio-1.2.9-arm64-v8a.apk` | Most modern Android phones |
| `mio-1.2.9-armeabi-v7a.apk` | Older 32-bit devices |

Requirement: Android 8.0 (API 26) or newer.

> The offline build is signed with a debug key. If another build of Mio with a different signature is already installed, uninstall it first.

## Features

- **Physical keys / external keyboard triggers**: bind external keyboard or mouse buttons to plugin shortcuts, with press, release, long-press, and toggle trigger modes.
- **Floating panels**: shortcut, WebSocket toggle, and MCID lookup panels that can be dragged, resized, and minimized.
- **DSL plugin system**: install / uninstall / enable / disable plugins, plugin configuration pages, event callbacks, and permission declarations.
- **Kore plugin engine**: powered by the `runtime` / `runtime-core` / `utils` AARs, compatible with the legacy DSL plugin interface.
- **WebSocket integration**: built-in WebSocket service for Minecraft Bedrock, with event subscriptions and the `Ws.*` host bridge.
- **Input injection**: multiple channels including IME, Shizuku, and virtual gamepad.
- **Offline MCID data**: blocks, items, enchantments, effects, particles, biomes, entities, and fog data are loaded asynchronously from `res/raw/*.json`.
- **Material 3 theming**: dark mode, dynamic color, and a gradient drawer header.

## Tech Stack

| Item | Version / Notes |
|---|---|
| Language | Java 17 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 26 |
| Gradle | 8.14.6 |
| Android Gradle Plugin | 8.10.1 |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |
| UI | ViewBinding / DataBinding, Navigation, Material 3 |
| Networking | OkHttp |
| Images / Markdown | Glide, Markwon |
| Plugin runtime | Kore `runtime-release.aar`, `runtime-core.jar`, `utils-release.aar` |
| Plugin compiler | `app/libs/dsl-cli.jar` |

## Project Layout

```
Mio/
├─ app/
│  ├─ libs/                     # Local AAR / JAR (Kore runtime, pandaLibrary, dsl-cli)
│  └─ src/main/
│     ├─ cpp/                   # Native injection / integrity / UI C++
│     ├─ java/io/takamiya/mio/
│     │  ├─ app/                # Application initialization
│     │  ├─ dsl/                # Plugin lifecycle and host bridge registration
│     │  ├─ floating/           # Floating window service and panels
│     │  ├─ mcid/               # Offline MCID data repository
│     │  ├─ navigation/         # Home / plugins / config / settings fragments
│     │  ├─ ws/                 # WebSocket service and feature center
│     │  └─ utils/              # Shared utilities
│     └─ res/raw/               # Offline MCID JSON data
├─ docs/                        # DSL plugin development docs (start at docs/README.md)
├─ gradle/                      # Gradle wrapper and version catalog
└─ settings.gradle.kts
```

## Building

### Requirements

- JDK 17
- Android SDK 36
- Android NDK `29.0.14206865`
- CMake `3.22.1`

### Steps

1. Create `local.properties` in the project root and point it to your SDK:

   ```properties
   sdk.dir=D:\\Android\\Sdk
   ```

2. Make sure the following files exist under `app/libs/`:

   ```
   runtime-release.aar
   runtime-core.jar
   utils-release.aar
   pandaLibrary-release.aar
   dsl-cli.jar
   ```

3. Build the offline (debug) APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

4. Output:

   ```
   app/build/outputs/apk/debug/mio-1.2.9-universal.apk
   app/build/outputs/apk/debug/mio-1.2.9-arm64-v8a.apk
   app/build/outputs/apk/debug/mio-1.2.9-armeabi-v7a.apk
   ```

> `gradle/wrapper/gradle-wrapper.properties` uses the Tencent Cloud Gradle mirror by default for users in mainland China. If you can reach the official source directly, change it to:
>
> ```properties
> distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.6-bin.zip
> ```

## Plugin Development Docs

Plugin authors should start with [`docs/README.md`](docs/README.md), which covers:

- DSL syntax and quick start
- Host bridge / module API reference
- Event system and physical-key trigger model
- Input injection and key codes
- WebSocket service and game integration
- Configuration pages and HostData

## Debug Switches

The offline build keeps the following switches enabled. **Restore them before publishing a regular online build**:

| Location | Switch | Current value | Description |
|---|---|---|---|
| `app/src/main/cpp/integrity-lib.cpp` | `kIntegrityEnforcementEnabled` | `false` | Disables native integrity checks, anti-debugging, and monitor kills |
| `app/src/main/java/io/takamiya/mio/MainActivity.java` | `SKIP_STARTUP_NETWORK_CHECK` | `true` | Skips server verification and version checks |
| `app/src/main/java/io/takamiya/mio/LoginActivity.java` | — | Redirects to home | Login page disabled in offline mode |
| `app/src/main/java/io/takamiya/mio/UserAgreementActivity.java` | — | Local fallback text | Agreement can be accepted offline |

## Known Limitations

- Not fully validated on all devices and OS versions, especially Kore plugin installation, WebSocket connections, physical-key triggers, and injection channels.
- Some online features depend on server APIs and are unavailable offline.
- The AAR / JAR files under `app/libs/` are local binary dependencies. The repository does not guarantee their origin or license; verify distribution rights before publishing.
- `app/release/` and the `.idea/` IDE configuration are listed in `.gitignore` and are no longer distributed with the repository.

## Security & Privacy

- The signing key `key/key.jks` and `local.properties` are listed in `.gitignore` and are never committed.
- Local AAR / APK files may still contain server addresses, keys, or proprietary logic. **Review them before making the repository public.**
- This project does not collect or upload user data. Network requests are only made to endpoints you configure.

## License

This project is licensed under the [Apache License 2.0](LICENSE).

```
Copyright 2026 YUNDATAFw

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

Third-party dependencies and the local AAR / JAR files under `app/libs/` remain under their respective licenses. This project does not alter or represent those licenses.

## Acknowledgements

- Kore plugin runtime
- pandaLibrary
- All open-source dependency authors
