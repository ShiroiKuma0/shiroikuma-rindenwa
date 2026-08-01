# shiroikuma-rindenwa

**白い熊 臨電話** — a fork of [Linphone for Android](https://github.com/BelledonneCommunications/linphone-android)
(GPL-3.0), the SIP/VoIP softphone. Package `shiroikuma.rindenwa`, label **"白い熊 臨電話"**,
installable side-by-side with stock Linphone.

## Branch & remote model (same as the sister forks)

- `origin` = `git@github.com:ShiroiKuma0/shiroikuma-rindenwa.git` (ssh) — our fork.
- `upstream` = `https://github.com/BelledonneCommunications/linphone-android.git` (https, fetch only).
- **`master`** mirrors `upstream/master` (currently the `6.3.0-alpha` line). Fast-forward only —
  no fork work ever lives here.
- **`custom`** carries all our work, rebased onto `master` on each upstream sync. **All development
  happens on `custom`**, and it is the GitHub default branch.
- **Do not rename the `org.linphone` code namespace** — only the installed `applicationId` differs
  (`shiroikuma.rindenwa`). Renaming would make every rebase a mass-conflict.

## Skills (`.claude/skills/`)

- **`build-apk`** — build the signed release APK via the `buildApk` Gradle task, then deliver it
  automatically via the global `/after-build` skill (adb push to `/sdcard/tmp/` if the phone is
  reachable, else scp to skhw) — **no transfer prompt**, never pause to ask how to transfer.
- **`upstream-new-version`** — check upstream for new commits; **⛔ before any rebase, present a
  proceed-gated descriptive table of the new upstream version's features and wait for 白い熊's
  explicit go-ahead**; then fast-forward `master`, rebase `custom`, reset `BUILD_NUMBER`, build the
  new `+1`.
- **`publish-version`** — publish the latest tested APK as a GitHub release: tag `<version>` (no `v`
  prefix), attach the APK, refresh README + `CHANGELOG-shiroikuma.md`, keep the default branch on
  `custom`. Pin `gh` with `-R ShiroiKuma0/shiroikuma-rindenwa` (the `upstream` remote otherwise wins).

## Build, versioning, signing

- **Build env (this machine):** default `java` is JDK 11 (can't run modern Gradle). Always:
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk`.
- **Build:** `./gradlew buildApk` (release, signed; copies the APK to `~/tmp` and bumps
  `BUILD_NUMBER`). Fast dev iteration: `./gradlew :app:assembleDebug` (no R8; note it shares the
  applicationId with release unless `useDifferentPackageNameForDebugBuild` is turned on).
- **Linphone SDK:** resolved as a **prebuilt AAR** (`org.linphone:linphone-sdk-android`, version
  `5.6.+`) from `https://download.linphone.org/maven_repository` — we never build the native SDK
  locally, so `LinphoneSdkBuildDir` stays empty. Builds need network but no NDK work.
- **Versioning:** upstream's `versionCode`/`versionName` literals in `app/build.gradle.kts` are left
  **byte-identical to upstream** and read back by the fork block just below `defaultConfig`, so an
  upstream bump flows through with no hand-editing and no conflict on those lines.
  `BUILD_NUMBER` (`gradle.properties`) is our increment — bumped every build, reset to 1 on each new
  upstream version. Fork `versionName = "<upstream>.g<upstream base sha>+<BUILD_NUMBER>"`,
  `versionCode = <upstream versionCode> * 1000 + BUILD_NUMBER` (602003 → `602003001`).
  **The multiplier is 1000, not the sister forks' 10000** — Linphone's upstream code is already
  ~602003, and ×10000 would overflow Android's 2,100,000,000 versionCode ceiling. Never "restore"
  ×10000 while resolving a rebase conflict.
- **Upstream tracking: `git`** — `custom` is rebased onto every upstream commit, and upstream's
  `6.3.0-alpha` literal stands still for months, so the versionName pins the **upstream base sha**:
  `git merge-base HEAD master`, first 8 chars, as `.g<sha>` before the `+N`
  (e.g. `6.3.0-alpha.g6441c21e+24`). It changes only on an upstream sync, so two builds under the
  same sha are built on the same upstream base. See the global **`git-versioning`** skill.
  Never use our own HEAD sha, and never `master`'s tip. The sha never touches `versionCode`.
- **APK filename:** `shiroikuma-rindenwa_<versionName>_arm64-v8a.apk`, single-ABI arm64-v8a build
  (upstream ships armeabi-v7a as well; we drop it).
- **Signing:** release signed from the **gitignored** `keystore.properties`
  (`keystore.properties_sample` documents the keys) → `~/.android-keystores/shiroikuma-rindenwa.jks`
  (alias `rindenwa`). Password recorded in `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`
  (jks backup in `android-keystores/` next to it). Losing both loses the signing identity.
  Upstream tracks `keystore.properties` with empty values and loads it unconditionally; we untrack
  it and tolerate its absence (release APK then simply unsigned).
- **Delivery:** APK to `~/tmp`, then `/after-build` (adb push to `/sdcard/tmp/` or scp to skhw);
  **白い熊 installs from the on-device file manager** (never `adb install`).

## Working rules (override harness defaults where noted)

- **No `Co-Authored-By: Claude` / "Generated with Claude" trailer** in commits or PR bodies — end
  the message at the last line of the body. (Overrides the harness default; global rule in
  `~/.claude/CLAUDE.md`.)
- **Never commit or push until 白い熊 says "Push".** Treat the working tree as scratch between
  "Push" commands; multiple uncommitted fixes can stack. "Push" = `git commit` + `git push origin
  custom` (and `master` after an upstream sync). 白い熊 tests each build on-device first.
- **After every successful build, deliver the APK automatically via `/after-build`** — never ask how
  to transfer it, never pause.
- **Commit subjects:** plain descriptive summary, no prefix.
- Upstream owns `CHANGELOG.md` — fork changelog notes go to `CHANGELOG-shiroikuma.md` only.
- `git push` / `gh` / `scp` need `~/.ssh` and `~/.config/gh`, which the command sandbox blocks — run
  those with `dangerouslyDisableSandbox: true`.

## Fork identity (the standing customization layer)

| What | Value | Where |
| --- | --- | --- |
| App id | `shiroikuma.rindenwa` | `app/build.gradle.kts` → `val packageName` |
| Namespace | `org.linphone` (**never rename**) | `app/build.gradle.kts` → `android.namespace` |
| Label | `白い熊 臨電話` | `<!ENTITY appName>` in `app/src/main/res/values/strings.xml` |
| Icon | black-yellow traced handset (yellow `#FFFF00` line-art on black) | `mipmap-*/ic_launcher*`, `mipmap-anydpi/` |
| Version logic | `shiroikumaBuild` + the `* 1000` fork block + `buildApk` task | `app/build.gradle.kts` |
| Signing | `keystore.properties` (gitignored) → `~/.android-keystores/shiroikuma-rindenwa.jks` | `app/build.gradle.kts` |
| Single ABI | `abiFilters += listOf("arm64-v8a")` | `app/build.gradle.kts` |

Because `applicationId` (`shiroikuma.rindenwa`) differs from `namespace` (`org.linphone`), the
`packageName` val also drives the **FileProvider authority**, the **AppAuth redirect scheme** and the
**OpenID callback scheme** — that is deliberate, and it is what makes the fork installable alongside
stock Linphone. Watch for the sister-fork trap: leading-dot class names in *runtime* layout attrs
(`android:name=".Foo"`, `app:layoutManager=".Bar"`) resolve via the applicationId, not the namespace,
so they would `ClassNotFound` here — fully-qualify them to `org.linphone.…` if lint flags them.

## Repo layout (upstream Linphone)

- `app/src/main/java/org/linphone/` — Kotlin sources: `core/` (the LinphoneCore service, call/chat
  managers), `ui/` (MVVM fragments + data-binding, navigation graphs), `telecom/`
  (ConnectionService integration), `contacts/`, `notifications/`.
- `app/src/main/res/` — data-binding layouts, many translated locales (the label therefore lives in
  the **DOCTYPE entity** `&appName;`, not in a per-locale string).
- Firebase Cloud Messaging + Crashlytics activate only when `app/google-services.json` exists — it
  does not here, so both are **disabled** in our builds.
- `ktlintFormat` runs on `preBuild`; the release build runs R8 with `proguard-rules.pro`.
- minSdk 28, targetSdk/compileSdk 37, JDK 21.

## Current status

**Phase 0 — repo bootstrap (2026-07-26).** Fork created from `BelledonneCommunications/linphone-android`;
`master` mirrors upstream, `custom` created with the identity layer: app id `shiroikuma.rindenwa`,
label `白い熊 臨電話`, fork versioning (`+N` / `×1000`), single-ABI arm64 build, house APK naming,
own keystore + gitignored `keystore.properties`, and the three skills above. Icon and full
de-branding follow next, then the first build.
