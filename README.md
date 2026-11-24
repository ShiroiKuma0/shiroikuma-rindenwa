<div align="center">

<img src="metadata/en-US/images/icon.png" width="120" alt="白い熊 臨電話 icon" />

# 白い熊 臨電話

**A SIP/VoIP phone that looks the way I want it to, and can restore itself from a single file.**

A fork of [Linphone for Android](https://github.com/BelledonneCommunications/linphone-android) with
**major additions**: an incoming call that takes the whole screen, with buttons to silence the
ringing, answer on speaker, or refuse the call and answer it by SMS instead, a dial pad that slides
up over the call history and filters it as you type, a call history written in Japanese with
imperial-era day headlines, an address book with foldable gojūon letter headings, a Favorites tab of
draggable tiles, a black-yellow theme applied through the whole app, a card-folder tab navigation
with an account strip you can drag into order, a settings page that makes colours, fonts and shapes
live-editable, settings groups boxed in yellow that remember what you folded, a full-app
Export/Import backup, an automation contract that lets a sister app back this one up — and its data
— without being asked twice, a keep-alive door a sister app can knock on to confirm that a call can
really reach this phone, and diagnostics that record nothing until you ask them to.

Installs **side-by-side** with stock Linphone (app id `shiroikuma.rindenwa`).

**📥 Latest release: [`6.3.0-alpha+2026-11-23.08-45.g02e7f6ea+061`](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-rindenwa/releases)

</div>

---

## 🎨 Black and yellow, everywhere

Upstream resolves every colour in the app through about sixty theme attributes. Rather than repaint
views one by one, this fork overrides all of them in a single theme overlay applied last — so every
fragment, dialog and list row comes out black-on-yellow natively, at inflation time, with no flicker
and nothing missed. Top bars are black with the accent carried by their text and icons; field pills
have yellow borders; semantic colours keep their meaning, so a hang-up button is still red.

The overlay reaches everything upstream resolves through an attribute, which is very nearly
everything — but a view that names a colour outright escapes it, and each one upstream adds has to
be repainted by hand. The newest is the All calls / Missed filter above the call history: upstream
fills the chosen segment with the accent and writes its label in white, which here would be white
on yellow, so the selection is marked the way an accent chip is instead — the house frame over a
yellow wash, label in full ink, the other segment dim.

---

## 🗂 The app is a card folder

Both bars of the main screens are one folder seen from two sides. A continuous bold line runs the
full width of the window — over the detail pane too, not just the list — and where the tab in the
foreground is, it curls outward, runs down both sides and around the far corners, open on the panel
side because that tab *is* the panel you are looking at. Every other tab is the same box, thinner
and half transparent, filed behind that line. Switching interpolates, so the line dives around the
tab you are moving to while the one you left closes up.

At the bottom that folder is the navigation, and it is the same bar in three apps: Favorites,
Contacts, 電話, 臨電話, each inside its own tab (see “One phone in three apps” below). Which screen you are on is told by the folder, so the screen no longer has to name
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

**No colour is fixed.** Yellow and black are only what a fresh install shows: every item — the
labels of the history, the icons of the drawer, the frame of a menu, each of upstream's shapes and
text styles — has a role of its own with a swatch row here, about 700 in all, and a role you leave
alone follows Accent, Text or Background. The item roles are made when the app is built, from
upstream's own layouts, so a screen upstream adds is settable the day it arrives.

A row that *does* something when you tap it is drawn as an **outlined pill** — never filled, and
never wider than the label it carries — with its explanation beneath in dim gold and a **›** only
when a screen opens; the one action a section is about wears a 3 dp frame and a bold label, every
other a thin 1.25 dp one. Rows that carry a switch, a swatch or a slider stay plain, because the
control already says they can be touched. Nothing in the page is a band of colour you have to guess
at.

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

## 📲 An incoming call takes the whole screen

Stock Linphone announces a call with a notification carrying a full-screen intent, and Android
honours that only on a locked or dark screen — so a call arriving while the phone was in your hand
dropped a banner at the top and nothing else. This fork starts the call screen itself instead, once
per call, and keeps upstream's notification for the locked screen and the shade. The one thing it
needs is Android's "Display over other apps" permission, which the app asks for on start — without
it a call can only raise a notification.

The screen it raises is the fork's own, in black and yellow, and it carries five controls where
stock carries two. **Silence** does neither of the obvious things: it takes the ringtone and the
vibration away and leaves the call standing, so the caller goes on hearing it ring and you can still
answer when you are ready. **Speaker** answers with the audio already on the loudspeaker.
**Message** refuses the call and answers it in writing. **Decline** and **Answer** sit on their own
row below, far enough apart that the one you did not mean is not the one you hit.

The lock-screen slider is gone — one panel serves a locked and an unlocked screen alike — and the
panel measures its own height, so a longer label moves the screen with it instead of overlapping the
caller's name.

---

## ✉️ Refuse a call, and answer it anyway

The lines this phone answers are SIP DIDs forwarded from real SIMs sitting in a bridge phone
somewhere else, which means a reply sent back down a DID reaches the gateway and not the caller's
handset. So **Message** hands the text to the sister messenger (白い熊 メッセージ) instead, which
sends it as a **real SMS from the SIM behind the line the call came in on**: this app reports which
DID rang, and the DID → SIM table lives in 携帯, so one list is edited rather than two.

**One tap does the whole thing.** The call is declined first, always, before a single byte is handed
over — bookkeeping must never be what keeps a phone ringing. The ended-call screen then stays up
until the hand-off has answered, so a refusal from the other side is something you see rather than
something written to a log. Type a new line instead of picking one and it is sent first and only
then offered for keeping, to everyone or to this caller alone. A missed call in the history carries
the same button, where there is nothing left to decline and only the writing back remains.

Both lists — the one offered for every caller, and the one offered only to one — are edited in the
CALLS section of the UI page.

---

## ⌨️ The dial pad rises over the history

Tapping "new call" used to navigate away to a screen of its own — its own search field, its own
contact list, a grey numpad underneath. That is backwards, because the number you want is nearly
always one the history already holds. Here the pad is a sheet that slides up **over** the list
instead, and **every digit you type filters the history underneath it**, so the call you are looking
for walks up to meet the number you are dialling.

Nothing navigates and nothing is lost: the chevron puts the sheet away, Back closes it rather than
leaving the tab, and closing it clears the digits, because a filter left behind by a panel that is no
longer on screen is a list that lies. The keypad keeps its shape on the Mate XT's folded cover panel
as well as unfolded, and a half-typed number survives an unfold.

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

## 🧩 One phone in three apps

臨電話 is the SIP half of a set of three: [白い熊 電話](https://github.com/ShiroiKuma0/shiroikuma-denwa)
for the SIM cards and [白い熊 連絡先](https://github.com/ShiroiKuma0/shiroikuma-renrakusaki) for the
people. All three show **one bottom bar — Favorites, Contacts, 電話, 臨電話** — and each owns the
tabs that are its own. A tab that belongs to another app opens that app on that tab, without an
animation, so it reads as a tab change and not as a launch.

The contacts are the address book's and nobody else's. This app keeps **no contact store at all**:
names and pictures are read from the address book's private provider when the SIP core starts and
whenever it says something changed, and nothing is written down here. The copy of the system
contacts and the contacts made in Linphone's own editor are gone, and so is the `READ_CONTACTS`
permission. Tap a picture in the history and the contact opens in the address book; an unknown
number opens its editor with the number filled in.

The call history is drawn like the SIM dialer's — same row, same arrows, same colours — because it
**reads the dialer's theme** through a small provider: change a colour, a font or a time format
there and it changes here.

---

## 臨 A number for every line

Each SIP account carries a number of its own — 臨0, 臨1, 臨2 … — and a badge in colours of its own.
A call is marked with it in the history; the address book shows the same badge beside the SIM
badges on every phone number, and can be told to dial a contact, or a swipe, through 臨2.

For that the app has a **call door**: the sister apps list the accounts and place a call through
one of them, by number. Only the apps switched on for it on the UI page are let in.

The account strip begins with a **全** tab. An account's tab shows that account's calls and nothing
else; 全 shows every account's in one list. Its picture is the character itself, in a font of your
choosing, or a picture you put in its place.

---

## 🔍 Tap a call, see everyone's calls with them

Tapping a record does not open a detail page. The same list narrows to that person's calls, in the
same rows, under a banner — “Calls with …” — with a ✕ that widens it again. At the right of every
row sit a message bubble that opens the messenger, a handset that calls back **through the account
that row's call used**, and the row's own menu.

---

## 💊 Menus you can read by colour

Every menu row that does something is an outlined pill across a framed menu, with its icon inside
— and the icon is coloured by what the row does: green places a call, blue writes, cyan is a
person, orange copies or sends out, magenta silences, red deletes. Rows that open something carry
a ›. Switches are outlined pills with a dot, never filled; the pressable rows of the settings pages
are pills as wide as their label, so nothing that can be tapped looks like a heading.

---

## 🔁 Let the provider place the call

On an Odorik line, a long hold on a row's phone icon asks which way to call: **Callback** first,
the ordinary **Call** second. A callback is one order to Odorik's API with the line itself as the
first leg — its exchange rings this app, and once that is answered it rings the other person and
joins the two. They see the line's caller ID, exactly as on a call dialled the usual way.

The incoming first leg is recognised and shown as what it is: **Callback → name** on the call
screen and in the notification, and in the history it is filed under the person called, with its
own arrow and the word *Callback*. Each Odorik account has a callback block in its settings — which
phone rings first, the line's password, and whether the first leg answers itself. The address book
can order one through the call door.

---

## 🏷 Tap the badge, see what the call went through

Every call keeps a **stamp**, frozen while it happens: the 臨N badge with the colours it had that
day, the account and its server, how long it rang, how it ended and with which SIP answer, the
codec, whether the media was encrypted, the network the phone was on, where the sound went, the
stack's own quality rating and what the other end was running. A tap on a row's badge opens it all
in one dialog, in the sister dialer's look — the badge drawn large, one line per fact, and nothing
shown that is not known.

Because the row is drawn from its stamp, giving 臨1 to another account or repainting it no longer
rewrites the calls already made. The stamps travel with the call history in the export.

---

## 🔕 Silence the ring without looking

Turn the phone **face down**, or press **a volume button**, and the ringtone and the vibration
stop — the call stays up, the caller goes on hearing it ring, and it can still be answered. A phone
that was already lying on its screen rings as usual; only turning it over silences.

The volume button takes a detour worth knowing about: Android's telecom layer swallows a volume
key during a ring, and on this phone 自由作業盤's key grabber holds the keys anyway. So this app
tells 自由作業盤 when it rings, and 自由作業盤 asks it to go quiet through the call door.

---

## 🚫 Block a caller in one touch

**Block** sits on the incoming-call screen beside Silence, Speaker and Message. The touch silences
the ring at once and then asks; the red pill puts the caller in the blocked numbers and declines
the call. The history's long-press menu blocks and unblocks too, and “Blocked numbers” in the top
row's menu is the register itself — unblock a caller, or add one by hand. A blocked caller is
turned away on every account before the screen, the ringer or a notification stirs; the call stays
in the history, marked as blocked, and every row of a blocked caller carries a red “Blocked”
pill after the number. The register is the app's own, written as a number is written
everywhere else, and it travels in the backup.

---

## 🪪 Unknown callers get a name

A Czech number that is in no address book is looked up online while it rings — on tellows.cz, where
people report who called them. The name found there takes the number's place on the incoming-call
screen and on that number's rows in the history, followed by a small yellow **ID** mark, so it is
never mistaken for one of your contacts. The call rings at once and the name arrives a moment
later; every ring asks afresh. “ID online” in the history's long-press menu asks about any unknown
number on the spot, and “add to contacts” hands 連絡先 the number in international form with the
found name as the surname. A switch on the UI page turns the lookups off, since they send the
number to that site.

---

## 👉 Swipe a call to dial it

Sweep any row of the history aside — left or right — and the call is placed, through the account
that row's call used. A green handset appears in the gap and stops where letting go dials. On a
narrow, folded screen the rows keep their handset, message bubble and menu at full size, and the
number is always shown whole: the label after it gives way first.

---

## 🗣 A call screen that says what is happening

The rotating circle is gone. Under the number the call screen says it in words — 発信中…, 呼び出し中…,
着信中…, 接続中… — and when the call is over, why: 話し中, 応答拒否, 応答なし. The same reason is
written on the call's row in the history.

At the top of the screen, outgoing and incoming alike, stands the account the call goes through:
its 臨N badge, large and in its own colours, with the account's name beside it.

---

## 🤖 Backs itself up unattended

A broadcast contract (`EXPORT_STATE` / `LIST_CATEGORIES` / `CANCEL_EXPORT`) lets 白い熊 自由作業盤
trigger this app's export headlessly as part of backing up every sister app in one run — reporting
real progress counts and replying with the path and size it wrote. The category list says which
items should start ticked, so the picker on the other side reflects this app's answer instead of
guessing, and a cancel stops a run in flight and takes the half-written archive with it, leaving the
backup directory exactly as it was found.

Alongside it, a **data door**: a content provider that hands this app's whole state — accounts, call
and chat history, settings, appearance, fonts — straight down a file descriptor the caller opened,
and takes it back the same way. That is what lets 白い熊 応用管理 rebuild this app *with its data* on
a phone that has just been wiped, where an APK alone would restore an empty phone book. It answers
only 応用管理 and 自由作業盤, each checked by exact package name, process uid **and pinned signing
certificate** — the identity check a broadcast cannot make, which is why restoring data lives here
and never on the receiver above.

**It ships switched on**, because a backup that needs configuring first is no use on a clean phone.
An authorization token is still available for a caller that should have to prove itself, but it is
opt-in now; both switches live inside the Export/Import section, where backup lives.

---

## 🩺 Ask it whether it can take a call

A softphone can be running and still be deaf: a process the system calls alive, with not one account
registered, receives exactly as many calls as one that was force-stopped — and this fork has no push
to fall back on. So the phone answers that question itself. 白い熊 自由作業盤's watchdog knocks on an
exported, permission-free door every 15 minutes and on every unlock — the only kind of door that
still opens after a force-stop — and gets back one word built from the **registration state of the
accounts**, not from the existence of the process: `ALIVE`, `STARTED` (it was dead and has been
started), `RESTORED` (an account was not registered, was kicked, and came back), `OFFLINE`, `OFF`,
or `ERROR:` with a reason that names the account and the SIP code behind it.

It repairs what can be repaired on the way: brings the keep-alive service back the one way that is
legal from the background, tells the core the network is reachable when it disagrees with the system,
refreshes the registrations, and waits out the settle before judging anything. What it cannot fix it
**names** rather than works around — notifications or a channel switched off, background restriction,
or the foreground-service app-op that Huawei's PowerGenie likes to set to ignore, which is the usual
reason a call dies with no app code at fault. Every answer is one line in the app's own trace, so a
call that never arrived at 03:00 can be explained the next morning.

---

## 🧾 Diagnostics, and only when asked

Five logs — every account's REGISTER answer with the SIP code behind a failure, a line per incoming
call and how it ended, every state of an outgoing call with the SIP code behind it, every SIP message
whole, and the process and keep-alive service going up and down — each behind its own switch and **every one off until you turn it on**. While anything is recording, a notification stays
up carrying **Mark**, for marking the moment something went wrong, and **Turn all off**.

What is recorded is plain text in the app's own directory, one file per kind per day, readable over
`adb` with no permission: the page shows how many files there are, how big and which days, keeps
them forever or for N days as you choose, exports them into a dated subdirectory of a directory you
pick, shares them, or purges them behind a warning that says exactly what goes. Nothing is named
`.log`, because EMUI's nightly clean-up deletes those as junk, and no file is held open, so one
deleted under the app is simply recreated instead of every further line disappearing.

---

## 📴 A hang-up the other end actually hears

Hanging up a call that is still ringing sends a SIP `CANCEL`, and some providers refuse a perfectly
good one with `481 Transaction Does Not Exist` — the app shows the call as over while the called
phone rings on until somebody refuses it there. When that happens this fork ends the call a second
way: the ringing already opened an early dialog, and the app closes it with a `BYE` of its own. The
other phone stops at once. A provider that honours CANCEL never sees any of it.

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

The build needs `python3` (it derives the colour roles of every item from the layouts).

`buildApk` assembles the signed release APK, copies it to `~/tmp/` as
`shiroikuma-rindenwa_<version>_arm64-v8a.apk`, and bumps the build counter. The Linphone SDK is
pulled as a prebuilt AAR from `download.linphone.org`, so no NDK toolchain work is needed. With no
signing key configured the build still succeeds — the release APK is simply unsigned.

## Branch model

| Branch | Role |
| --- | --- |
| `master` | Mirrors `upstream/master`, fast-forward only. No fork work lives here. |
| `custom` | All of our work, rebased onto `master` on each upstream sync. **Default branch.** |
