<div align="center">

<img src="metadata/en-US/images/icon.png" width="120" alt="白い熊 臨電話 icon" />

# 白い熊 臨電話

**A SIP/VoIP phone that looks the way I want it to, and can restore itself from a single file.**

A fork of [Linphone for Android](https://github.com/BelledonneCommunications/linphone-android) with
**major additions**: a black-yellow theme applied through the whole app, a settings page that makes
colours, fonts and shapes live-editable, a full-app Export/Import backup, and a token-gated
automation contract so a sister app can back this one up unattended.

Installs **side-by-side** with stock Linphone (app id `shiroikuma.rindenwa`).

**📥 Latest release: [`6.3.0-alpha+9`](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases)

</div>

---

## 🎨 Black and yellow, everywhere

Upstream resolves every colour in the app through about sixty theme attributes. Rather than repaint
views one by one, this fork overrides all of them in a single theme overlay applied last — so every
fragment, dialog and list row comes out black-on-yellow natively, at inflation time, with no flicker
and nothing missed. Top bars are black with the accent carried by their text and icons; field pills
have yellow borders; semantic colours keep their meaning, so a hang-up button is still red.

---

## 🎛 A UI page that changes the app while you watch

**白い熊 臨電話 UI** — first row in Settings, or a long-press on the drawer's Settings cog or the
top-bar hamburger. Sections are separated by hairlines with text-wide underlined headings and a
36/54/72/90 dp indent ladder, so where you are is obvious at a glance.

Every surface is a slot you can set: colours through an RGBA slider picker with one-click boxes of
your recent choices; fonts imported from your own `.ttf`/`.otf` files and **rendered in their own
glyphs** in the picker; per-slot weight and size; and border and roundness sliders that go all the
way to zero. Everything previews live.

---

## 💾 One file holds the whole app

Export/Import writes a single ZIP into a directory you choose once — the page tells you when the
last backup was written, in red until you've set one. Categories are selectable, sub-options
included: **the accounts and their full configuration** first, then call history and conversations,
SIP and media settings, appearance with its imported fonts, and app settings.

Import merges rather than clears, and it works on a **completely clean install**: the assistant
normally refuses to let you reach the app without an account, which would make restoring impossible,
so the setup screens carry a skip that takes you straight in.

---

## 🤖 Backs itself up unattended

A token-gated broadcast contract (`EXPORT_STATE` / `LIST_CATEGORIES`) lets 白い熊 自由作業盤 trigger
this app's export headlessly as part of backing up every sister app in one run — reporting real
progress counts and replying with the path and size it wrote. Off by default; the switch and its
token live inside the Export/Import section, where backup lives.

---

## Built on Linphone

A fork of [Linphone for Android](https://github.com/BelledonneCommunications/linphone-android)
(app id `shiroikuma.rindenwa`, so it coexists with the official build). Linphone is Belledonne
Communications' open-source, fully SIP-based softphone — the calling, messaging and encryption that
make this app work are entirely theirs, and this fork only changes how it looks and how it is backed
up. Upstream is dual licensed; this fork tracks the
[GNU/GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html) side (see `LICENSE.txt`).

Issues in the underlying app belong
[upstream](https://github.com/BelledonneCommunications/linphone-android); the SIP service and
documentation remain at [linphone.org](https://linphone.org).

## Building

```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-rindenwa.git
cd shiroikuma-rindenwa
cp keystore.properties_sample keystore.properties   # then fill in your signing key
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME="$HOME/android-sdk"
./gradlew buildApk
```

`buildApk` assembles the signed release APK, copies it to `~/tmp/` as
`shiroikuma-rindenwa_<version>_arm64-v8a.apk`, and bumps the build counter. The Linphone SDK is
pulled as a prebuilt AAR from `download.linphone.org`, so no NDK toolchain work is needed. Without a
`keystore.properties` the build still configures — the release APK is simply unsigned.

## Branch model

| Branch | Role |
| --- | --- |
| `master` | Mirrors `upstream/master`, fast-forward only. No fork work lives here. |
| `custom` | All of our work, rebased onto `master` on each upstream sync. **Default branch.** |
