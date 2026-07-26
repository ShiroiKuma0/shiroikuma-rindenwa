---
name: build-apk
description: Build the signed release APK of shiroikuma-rindenwa (the "白い熊 臨電話" SIP/VoIP phone — a fork of Linphone for Android) with the `buildApk` Gradle task, then deliver it automatically via the global /after-build skill (adb push if the phone is reachable, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the 臨電話 release APK and deliver it

> **Never ask whether to build — just build.** When this skill applies (白い熊 asked to build, or
> you've made changes ready to test), run the build immediately. Do **not** ask "shall I build?".
> There is **no** transfer question either: after a successful build, deliver the APK automatically
> via the global **`/after-build`** skill — no prompts at all.

> **Never run `adb install` (or `pm install`).** You may `adb push` (that's what `/after-build`
> does); **白い熊 installs the APK themselves** from the phone's file manager. The push destination
> is always `/sdcard/tmp/`.

> **Never `git commit` or `git push` on your own.** Building does not include committing. After
> building, 白い熊 tests the build. **Only when they explicitly say "Push"** do you `git commit` and
> `git push origin custom`. Their **"Push"** means *commit-and-push-to-the-fork* — unrelated to the
> `adb push` file copy.

## Build environment (this machine)

- The default `java` is **JDK 11**, which cannot run modern Gradle. Always export JDK 21.
- The Android SDK is **not** on a default env var; export `ANDROID_HOME` explicitly.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/shiroikuma/android-sdk
```

The Linphone SDK itself is **not** built locally — it resolves as a prebuilt AAR
(`org.linphone:linphone-sdk-android`) from `https://download.linphone.org/maven_repository`, so the
build needs network but no NDK toolchain work. `LinphoneSdkBuildDir` in `gradle.properties` stays
empty (it only matters when pointing at a locally built SDK).

## Steps

1. **Note the output filename / version.** Read the counter from `gradle.properties` and upstream's
   numbers from `app/build.gradle.kts`:
   ```bash
   grep BUILD_NUMBER gradle.properties
   grep -E 'versionCode|versionName' app/build.gradle.kts | head -2
   ```
   - The APK will be `shiroikuma-rindenwa_<upstream versionName>+<BUILD_NUMBER>_arm64-v8a.apk`,
     using the `BUILD_NUMBER` value **before** the build (`buildApk` bumps it afterward).
   - versionCode for that build = `<upstream versionCode> * 1000 + BUILD_NUMBER`
     (e.g. 602003 → `602003001`). The multiplier is **1000**, not 10000 — see the
     `upstream-new-version` skill for why (Android's 2,100,000,000 ceiling).

2. **Build** (release, signed) — from the repo root:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
   ./gradlew buildApk --console=plain < /dev/null
   ```
   - `buildApk` runs `assembleRelease`, copies the signed APK to `~/tmp/<apk name>`, and
     auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>` (cyan) — use those to confirm the exact
     filename/code; confirm `BUILD SUCCESSFUL`.
   - Configuration prints `shiroikuma fork version: <name> (versionCode <n>)` and
     `Signing config release is using keystore [...]` — if the keystore line says the keystore
     doesn't exist, the APK will be unsigned and won't install. Fix before delivering.
   - The release build runs R8 (`isMinifyEnabled`) and `ktlintFormat` on `preBuild`; a cold build
     downloads the Linphone SDK AAR and can take a while. A cold build that may exceed the
     foreground timeout can be run with `run_in_background`.
   - **Fast dev iteration:** `./gradlew :app:assembleDebug` — no R8, much faster. It installs
     side-by-side with the release build only if you give it an `applicationIdSuffix`
     (`useDifferentPackageNameForDebugBuild` in `app/build.gradle.kts`, `false` by default), so by
     default it *replaces* the release install. The shippable build is always `buildApk`.

3. **At the end of every build, deliver the APK via `/after-build`** — no exceptions, no asking. As
   soon as `BUILD SUCCESSFUL` appears and the signed APK is in `~/tmp/`, invoke the global
   **`/after-build`** skill; it picks adb-push (phone reachable) or scp-to-skhw on its own and
   announces what landed.

4. **What `/after-build` does** (for reference — you don't run these by hand): `/adb-check` lists
   devices UNSANDBOXED; if the phone is reachable, `/adb-push` copies the newest `~/tmp/*.apk` to
   `/sdcard/tmp/`; otherwise `/scp` copies it to `skhw:~/tmp/`. It never runs `adb install`.
   Per the global adb rule, wireless adb is disconnected at the end of the delivery batch.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from the **gitignored**
`keystore.properties` at the repo root (`keystore.properties_sample` documents the keys). This fork
uses its own keystore `~/.android-keystores/shiroikuma-rindenwa.jks` (alias `rindenwa`); the
store/key password is recorded in `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`, with a
backup of the `.jks` in the `android-keystores/` directory next to it. Losing both loses the signing
identity — updates could no longer install over an existing app.

Upstream tracks `keystore.properties` with empty values and loads it unconditionally; our fork
untracks it and tolerates its absence (the release APK is then simply unsigned).

## Versioning (how the numbers are formed)

- Upstream's `versionCode` / `versionName` literals in `app/build.gradle.kts` are left exactly as
  upstream writes them; the fork block just below `defaultConfig` reads them back and derives ours.
- `BUILD_NUMBER` in `gradle.properties` is **our** increment, bumped on every `buildApk`, reset to
  `1` on each new upstream version (see the `upstream-new-version` skill).
- Fork `versionName = "<upstream>+<BUILD_NUMBER>"`;
  `versionCode = <upstream versionCode> * 1000 + BUILD_NUMBER`.
- Single-ABI **arm64-v8a** build (upstream ships armeabi-v7a too) — matches the APK filename.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
