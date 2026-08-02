<div align="center">

<img src="metadata/en-US/images/icon.png" width="120" alt="白い熊 臨電話 icon" />

# 白い熊 臨電話

**A SIP/VoIP phone that looks the way I want it to, and can restore itself from a single file.**

A fork of [Linphone for Android](https://github.com/BelledonneCommunications/linphone-android) with
**major additions**: a call history written in Japanese with imperial-era day headlines, an address
book with foldable gojūon letter headings, a Favorites tab of draggable tiles, a black-yellow theme
applied through the whole app, a card-folder tab navigation with an account strip you can drag into
order, a settings page that makes colours, fonts and shapes live-editable, settings groups boxed in
yellow that remember what you folded, a full-app Export/Import backup, and a token-gated automation
contract so a sister app can back this one up unattended.

Installs **side-by-side** with stock Linphone (app id `shiroikuma.rindenwa`).

**📥 Latest release: [`6.3.0-alpha.2026-07-30.g5c0ed6a3+016`](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases)

</div>

---

## 🎨 Black and yellow, everywhere

Upstream resolves every colour in the app through about sixty theme attributes. Rather than repaint
views one by one, this fork overrides all of them in a single theme overlay applied last — so every
fragment, dialog and list row comes out black-on-yellow natively, at inflation time, with no flicker
and nothing missed. Top bars are black with the accent carried by their text and icons; field pills
have yellow borders; semantic colours keep their meaning, so a hang-up button is still red.

---

## 🗂 The app is a card folder

Both bars of the main screens are one folder seen from two sides. A continuous bold line runs the
full width of the window — over the detail pane too, not just the list — and where the tab in the
foreground is, it curls outward, runs down both sides and around the far corners, open on the panel
side because that tab *is* the panel you are looking at. Every other tab is the same box, thinner
and half transparent, filed behind that line. Switching interpolates, so the line dives around the
tab you are moving to while the one you left closes up.

At the bottom that folder is the navigation: Contacts, Favorites, Calls, Conversations, each inside
its own tab. Which screen you are on is told by the folder, so the screen no longer has to name
itself in the title bar.

---

## 👥 Every account, one tab each

The top of the screen is a strip of account tabs. The account in use shows its picture at twice
upstream's size and its display name; the rest are their pictures, filed behind the line. Tap one to
switch the default account without opening the drawer, and the strip scrolls sideways when you have
more accounts than fit.

**Long-press a tab and it buzzes, lifts and follows your finger** — drag sideways to reorder the
accounts, or up and down on the same accounts in the drawer menu. Both lists share one stored order,
keyed by identity address, so they always read the same way round and it survives restarts.

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

## 🟨 Settings that stay where you left them

Every foldable group in Settings — and in the account, profile and advanced pages — sits in its own
`#FFFF00` round-corner box, header and content framed as one with a single divider between them. The
chevron follows the platform's tree convention: **down when the group is open, right when it is
folded**, so it says what the group is rather than what a tap will do.

Groups open unfolded, because upstream folds all of them shut and reaching any one setting costs a
tap first. And a group you *do* fold stays folded — upstream keeps that state in a view model scoped
to the navigation graph, so folding was only ever a way to lose a group until the next cold start.

---

## 💬 Conversations, off unless you want them

The Conversations tab is SIP instant messaging, not SMS, and with an ordinary SIP provider it can
only ever open a basic chat room whose messages are plain `MESSAGE` requests that most proxies
refuse to relay — group chat and encryption need a conference factory that only Flexisip servers
run. So the tab is hidden by default here instead of shown, and there is a switch under
Settings → User interface to bring it back. Turning it on rebuilds the bottom navigation bar
immediately rather than waiting for a restart.

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

## 📞 A call log that says which number rang

Stock shows a name and a time. With several numbers saved for one contact that leaves out the part
that matters — which of them the call came in on. Each record now carries the number and the
address-book field it is filed under ("Home", "Work mobile"), grouped for reading as
`+420-601-524-009` and `+1-808-500-5515`, beside an avatar sized to span the three lines. A country
code the fork does not group is shown exactly as it arrived rather than guessed at. The number
line's font and both spacings — between records, and between the lines inside one — are live
controls on the UI page like everything else.

---

## 🗓 The call history in Japanese

The day is written once, as an underlined headline standing above the calls made on it —
`令和八年七月三十日（木曜日）` in imperial era, or the Common Era spelled the same way, with today
and yesterday named instead of dated. Each record then carries only its own time, as a
Sino-Japanese clock reading: `午後三時三十六分`, where a whole hour drops its minutes, `:30` becomes
`半`, and noon and midnight get their own words, `正午` and `正子`. How long the call ran follows in
kanji inside full-width parentheses — `（十一分一秒）` — and an arrow in front says whether it came
in, went out, or rang unanswered, each in its own colour.

Every part of that is a setting: headlines on or off, 和暦 / 西暦 / system dates, Japanese / system /
24-hour / 12-hour times, kanji or digital durations, the arrow shown or hidden. The UI page draws a
live sample of a headline and a record while you turn the knobs.

---

## 🔤 An address book that folds by kana

Contacts are grouped under one-letter headings and each one folds shut with a tap. The letters are
Japanese-aware: gojūon rows (あ か さ た な は ま や ら わ, with voiced, semi-voiced and small kana
folded into their base row and ん in the わ row), then A–Z with diacritics stripped, then ＃.
Crucially the grouping runs on the contact's **reading** (フリガナ) where the address book stores
one — bucketing a kanji name by its kanji puts every Japanese contact under ＃, which is exactly
what it did before.

Which letters stand open is remembered across restarts, separately for each tab. Rows carry the
photo spanning both lines with the number written under the name, closed off by a full-width rule.

---

## ⭐ Favorites, as a wall of faces

Favourites used to be a strip pinned above the contacts list, eating its top and showing a handful.
They are their own tab now — a grid of large round photos with the name centred beneath. **Long-press
a tile and drag it anywhere on the grid**; the arrangement is yours and it is remembered, because
the SIP core knows only that a contact is starred, not where you want it.

---

## 🤖 Backs itself up unattended

A token-gated broadcast contract (`EXPORT_STATE` / `LIST_CATEGORIES` / `CANCEL_EXPORT`) lets 白い熊
自由作業盤 trigger this app's export headlessly as part of backing up every sister app in one run —
reporting real progress counts and replying with the path and size it wrote. The category list says
which items should start ticked, so the picker on the other side reflects this app's answer instead
of guessing, and a cancel stops a run in flight and takes the half-written archive with it, leaving
the backup directory exactly as it was found. Off by default; the switch and its token live inside
the Export/Import section, where backup lives.

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
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME="$HOME/android-sdk"
./gradlew buildApk
```

To produce a *signed* APK, put your signing key in `~/.gradle/gradle.properties` — deliberately
outside the repo, so no branch switch or upstream sync can clobber it:

```properties
RINDENWA_RELEASE_STORE_FILE=/path/to/your.jks
RINDENWA_RELEASE_STORE_PASSWORD=…
RINDENWA_RELEASE_KEY_ALIAS=…
RINDENWA_RELEASE_KEY_PASSWORD=…
```

`buildApk` assembles the signed release APK, copies it to `~/tmp/` as
`shiroikuma-rindenwa_<version>_arm64-v8a.apk`, and bumps the build counter. The Linphone SDK is
pulled as a prebuilt AAR from `download.linphone.org`, so no NDK toolchain work is needed. With no
signing key configured the build still succeeds — the release APK is simply unsigned.

## Branch model

| Branch | Role |
| --- | --- |
| `master` | Mirrors `upstream/master`, fast-forward only. No fork work lives here. |
| `custom` | All of our work, rebased onto `master` on each upstream sync. **Default branch.** |
