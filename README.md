<img src="metadata/en-US/images/icon.png" height="120" align="right" alt="白い熊 臨電話">

# 白い熊 臨電話

A personal fork of [Linphone for Android](https://github.com/BelledonneCommunications/linphone-android)
— an open source, fully SIP-based softphone for voice/video calls and instant messaging.

Package `shiroikuma.rindenwa`, so it installs **side-by-side** with stock Linphone.

**📥 Latest release: [`6.3.0-alpha+2`](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases)

## What this fork changes

- **Own identity** — app id `shiroikuma.rindenwa`, label 白い熊 臨電話, its own signing key. The
  FileProvider authority, AppAuth redirect scheme and OpenID callback scheme follow the app id, so
  nothing collides with an upstream install.
- **Black-yellow icon** — the Linphone mark re-traced as yellow `#FFFF00` line-art on black, across
  the adaptive icon, the legacy launcher icons, the splash screen, the notification icon, the
  welcome logo and the store graphics. The splash background is black to match.
- **De-branded** — the app name is 白い熊 臨電話 in every locale, and the About screen links to this
  repository. The in-app update check is disabled: updates come from this repo's releases, not
  upstream's server.
- **Fork versioning** — `<upstream version>+<build>`, with `versionCode = <upstream> × 1000 + build`,
  so every rebuild is monotonic and upgrades install cleanly over one another.
- **arm64-v8a only** — a single-ABI APK, roughly half the size of upstream's.

Everything else is upstream Linphone, rebased regularly.

## Branch model

| Branch | Role |
| --- | --- |
| `master` | Mirrors `upstream/master`, fast-forward only. No fork work lives here. |
| `custom` | All of our work, rebased onto `master` on each upstream sync. **Default branch.** |

## Build

```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-rindenwa.git
cd shiroikuma-rindenwa
cp keystore.properties_sample keystore.properties   # then fill in your signing key
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME="$HOME/android-sdk"
./gradlew buildApk
```

`buildApk` assembles the signed release APK, copies it to `~/tmp/` as
`shiroikuma-rindenwa_<version>_arm64-v8a.apk`, and bumps the build counter. The Linphone SDK is
pulled as a prebuilt AAR from `download.linphone.org`, so no NDK toolchain work is needed.

Without a `keystore.properties` the build still configures — the release APK is simply unsigned.

## License

Copyright © Belledonne Communications, and contributors.

Upstream Linphone is dual licensed; this fork tracks the
[GNU/GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html) side (see `LICENSE.txt`). The proprietary
licensing option is upstream's to offer — contact
[Belledonne Communications](https://linphone.org/contact) about it, not this fork.

## Upstream

Issues in the underlying app belong upstream:
[BelledonneCommunications/linphone-android](https://github.com/BelledonneCommunications/linphone-android).
Upstream documentation and the SIP service remain at [linphone.org](https://linphone.org).
