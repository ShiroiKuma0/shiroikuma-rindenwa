# Changelog — 白い熊 臨電話

Fork-only changes. Upstream Linphone's own changelog stays in `CHANGELOG.md`.

## 6.3.0-alpha+2026-11-23.08-45.g02e7f6ea+065 — current

Same upstream base as `+062` (`02e7f6ea`); `versionCode` `602010065`. Builds `+063` and `+064`
were test builds and are contained in this one.

### Incoming calls are filed under the line they rang on

- **A missed call no longer lands under whichever account's tab is open.** With several
  accounts at a provider that writes a port into the `To` line of its INVITE, the SDK could not
  place the call and recorded the selected account instead. The line is now read from the
  INVITE itself — the `To` line, then the request line — and the record is asked only when
  neither names an account.
- The call's stamp, the badge on the call screen, the badge of the history row, the account's
  tab and “calls with this person” all follow that line; so do calling back from a row, the
  line a written reply to a caller is sent from, and the recognition of a callback's first leg.
- **The stamp is written the moment a call arrives**, so a call that is still ringing is already
  listed under the right account, and the history is made again whenever a stamp is written.
- **The tab of all accounts shows all accounts' calls again.** It kept the list of the tab it
  was reached from until something else refreshed it.
- **Old rows are repaired once**, on the first opening of the history: an incoming call whose
  caller name carries the provider's line tail is moved to the account whose name holds a
  number ending in that tail — only where exactly one account fits. A moved row takes that
  account's badge of today.
- The “calls” diagnostic records, for every incoming call, the `To` line, the request line,
  what the record says and the account chosen.

### Missed calls, counted per account

- **Missed calls not yet looked at are counted by the account they really rang on.** Opening an
  account's history clears that account's count; the tab of all accounts counts and clears
  them all. A call taken on another device is not counted.
- **Each account tab carries its count** in a badge over the bottom-right corner of its
  picture — accent ground, black number, both settable — and the tab of all accounts carries
  the total.
- **The red badge on the bottom tab is gone.**
- The missed-call notification and the “other accounts” notice use the same count.

### Call screens in the address book's cards

- **Incoming, outgoing and ongoing calls show who the call is with in two cards**: a picture
  card and a name card, one above the other on a narrow screen and side by side on a wide one.
- **The picture is shown whole** — never round, never cropped, whatever its shape. A caller
  without a picture gets the round stand-in in the middle of the card.
- **The caller is the subject of the screen**: the name largest, the number below it in the
  house reading (`+420-601-123-456`), never a `sip:` address; then, small, the line the call
  goes through — its 臨N badge and the account's name. Until now the line stood in the largest
  type and read as if it were the caller.
- **Decline and Answer are worded pills** side by side — Answer with the thick frame, Decline
  with a thin red one — and the four small actions shrink their labels to fit instead of
  cutting them off.
- The heading of an incoming call is the plain “Incoming call”; the line is named in the card.
- Seven new colour roles: the cards' ground and frame, the name, the number, the status line,
  and the tab badge's ground and number.

## 6.3.0-alpha+2026-11-23.08-45.g02e7f6ea+062

Same upstream base as `+061` (`02e7f6ea`); `versionCode` `602010062`.

### App language

- **A language for this app alone.** “App language” is the first section of the 臨電話 UI page
  and the first chip of its index. Its one row, “Language: …”, opens the list of languages:
  “System default” first, then Čeština, Deutsch, English, Español, Français, Magyar, 日本語,
  Nederlands, Português (Brasil), Русский, Slovenčina, Українська and 中文. A choice applies at
  once.
- **The list is easy to read**: each language is written in its own language, with its country
  or region beneath where the tag names one and the tag in a column at the right. Only the
  current language stands in a pill.
- **The choice survives an update of the app** — it is set again at every start — **and reaches
  notifications too**, not only the screens.
- It travels in the export with the rest of the appearance.
- An order of sections arranged by dragging keeps its arrangement; the new section is put on
  top of it once and can be dragged anywhere afterwards.

## 6.3.0-alpha+2026-11-23.08-45.g02e7f6ea+061

Upstream base moved `2199af83` → `02e7f6ea` (one commit: upstream follows a change in the SDK's
Java wrapper for arrays; no behaviour change). `versionCode` `602010061`.

### Japanese, everywhere

- **The whole app is translated into Japanese.** Upstream Linphone has no Japanese translation;
  this one is the fork's own and covers every text: upstream's 909 strings and the fork's 468 —
  calls, conversations, meetings, contacts, settings, the assistant, notifications, and the
  臨電話 UI page with every heading, chip, row, explanation, dialog and flash.
- **The colour roles are named in Japanese too** — all 709 of them, the item roles of upstream's
  screens, shapes, state colours and text styles included.
- With the display set to another language nothing changes.

### The 臨電話 UI page

- **Every section has an icon, on its heading and on its chip** — the same icon in the same
  colour on both, so a chip and the section it jumps to are recognisably one thing.
- **The icon's colour says what kind of thing the section is**, in seven colours: yellow for the
  app and its settings, green for controls, light blue for words, heavy blue for the frame and
  its boxes, orange where the app reaches out (export, menus, dialogs, sheets), red for
  Maintenance and Diagnostics, and rose for Flashes alone. Each is a colour of its own under
  “This page” → “Section icons of this page”.
- **The default order of the sections changed** so that no two neighbours share a colour. An
  order arranged by dragging the chips is kept as it is.
- **Row icons follow the size of their label**: 1.25 times the text size, never under 18 dp, also
  after a font or size change, and level with the label's first line. The explanation under a
  pill or a switch label now starts under the label's text rather than under its icon.

### Menu icons

- **Four default colours changed**, away from colours that are hard to tell from yellow or from
  one another: call is a heavy green `#00A550` (was `#00FF00`), message a heavy blue `#0066FF`
  (was `#4D94FF`), contact a light blue `#00B0FF` (was `#00FFFF`), block a rose `#FF3D7F` (was
  `#FF00FF`). A colour set on the page, or served by 電話, is kept.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+060

Same upstream base as `+058` (`2199af83`); `versionCode` `602010060`. `+059` was an unpublished
step on the way (it pasted at once, with no menu).

### The dial panel's number field

- **A long press on the number opens a menu.** Each row is there only when it has something to
  act on: **Paste** when the clipboard holds a number or a SIP address — the first run that reads
  as a number, without the spaces, dashes, brackets or `tel:` it was written with — and it takes
  the place of what was typed; **Copy**, **New contact** (連絡先's editor, the number in
  international form), **Write a message** (メッセージ) and **Clear** when something is typed.
  With nothing typed and nothing usable in the clipboard, a flash says so.
- **A long number is no longer cut short on a narrow display.** The field shrinks its type from
  30 sp down to 14 sp to fit, and only below that drops the front of the number.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+058

Same upstream base as `+057` (`2199af83`); `versionCode` `602010058`.

### Every item has a colour role of its own

- **About 515 item roles join the general ones.** Until now two things that upstream coloured
  with one name shared one role. Now text, icons, ground and frame are separate roles for each
  group of screens — the main frame, history, contacts, new call and new conversation, call
  screens, conversations, settings, account pages, the drawer, recordings, help pages, file
  viewers, the assistant, meetings, menus, dialogs — and so is each of upstream's shapes, each
  state colour and each text style.
- **Nothing looks different until one is set**: an item role follows the general role it took the
  place of, which follows Accent, Text or Background.
- **New sections on the 臨電話 UI page** hold them: Main frame, History screens, Contact screens,
  New call and new conversation, Settings screens, Account pages, Drawer, Recordings, Help pages,
  File viewers, Other layouts, Text styles, Shapes, State colours. Dialogs, Menus, Calls and
  Conversations each gained a “by item” group.
- **The assistant and meeting roles of `+057` were renamed** by this; a colour stored for one of
  them is no longer read. Every other stored colour is kept.

### The fork's own dialogs and shapes

- **The colour picker, the font picker, the app selection, the export panel and the call stamp**
  use the dialog roles one by one — title, text, button frame, button label, field frame — and
  new ones for group headings, dividers, captions, sliders and the icons of a call stamp's facts.
- **The fork's own frames, pills and grounds are drawn in code**, each from roles of its own: the
  menu ground and frame, the settings groups, the search pill, the status pill, the dial pad's
  keys, line and panel, the round call buttons, the deleting pill, the 臨 badge, the Blocked and
  ID marks. Their XML files hold no colour any more.

### Packaging

- **The item roles are made at build time.** `tools/sk_colour_roles.py`, run by the Gradle task
  `skItemRoles` before every build, rewrites a copy of the resources and writes the role table;
  the layouts in the repository keep upstream's own colour names, so an upstream sync conflicts
  no more than before and a layout upstream adds gets its roles by itself. The assistant, welcome
  and meeting layouts went back to upstream's names. A build now needs `python3`.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+057

Same upstream base as `+052` (`2199af83`); `versionCode` `602010057`. Builds `+053` to `+056` were
steps on the way and were not released.

### Apps that may place calls — a selection, with メッセージ

- **One selection replaces the two fixed switches.** “Apps that may place calls”, in the new
  section “Accounts and apps” of the 臨電話 UI page, opens a list of every app on the phone that
  has a launcher entry: the app's icon, its name, an outlined switch, and a field to filter by
  name. A switch takes effect at once.
- **白い熊 メッセージ is let in by default**, beside 白い熊 連絡先 and 白い熊 電話 — its call button
  can now read the accounts here and dial through a 臨 account. An install that had already
  stored its set gets メッセージ added once; switched off afterwards, it stays off.
- The row names the apps that are let in, and turns red when none is.

### The 臨電話 UI page folds, and has an index

- **Every section folds.** A tap on a heading opens or closes it, with › when folded and ⌄ when
  open. All sections start folded; a folded section's rows are not built until it is opened, so
  the page opens quickly.
- **A chip index stands on top**, one small chip per section. A tap unfolds the section and
  scrolls to it; a long press drags the chip, the chips move under the finger, and the sections
  below take the same order on release. “Reset section order” is in the new Maintenance section.
- The order and what is unfolded are remembered, and travel in the export with the appearance.
- **“App settings”**, a thick pill on top of the page, opens the app's own settings.
- **Every row has an icon** coloured by what the row does, and a row with an explanation carries
  a thin dim-gold rule at its left. A row that deletes — Purge, Remove picture, Reset all colours —
  is red in frame and label.
- 臨 numbers and the call selection moved out of “Export / Import” into “Accounts and apps”.

### Every colour is a role that can be set

- **No colour is fixed any more.** Yellow `#FFFF00` and black `#000000` are only what a fresh
  install shows. About 240 colour roles — the text, frame and ground of a button, a dialog's
  title, text, ground and buttons, a switch on and off, menu rows, lists, bars, text fields,
  status colours, avatars, bubbles, the call screens, the dial pad, badges, sheets, flashes, the
  UI page itself, the assistant and the meetings — each have a swatch row on the UI page, in the
  section of the thing they colour.
- **A role that is not set follows the foundation**: change Accent, Text or Background and
  everything not set by itself follows. Colours that carry a meaning (danger red, success green,
  the menu icon colours) keep their own defaults.
- **Reset:** the picker's Default takes one colour back, “Reset all colours” in Maintenance takes
  all of them back. The roles travel in the export with the appearance.
- Upstream's screens are coloured through a resources loader that feeds each role's value in at
  run time (Android 11 and later); below that the app shows placeholder colours.
- The white text and icons that were left on the call screens now follow the text colour.
- A PDF is shown on white paper, a role of its own.

### Nothing is filled

- **Round buttons are outlined**: the call, message and video circles on the contact, history and
  conversation-details pages, the numpad keys, and the in-call controls. A control that is on
  lends its red or green to its ring and its icon.
- **A dialog that deletes confirms with a red pill** — deleting a contact, a meeting, an account,
  call records, a conversation or its history, removing a participant, leaving a group,
  cancelling a meeting, deleting a stored decline message, purging diagnostics.
- **A main pill is bold and thick, every other pill thin and normal**, in upstream's screens too.
- Skip on the welcome page, the three attach buttons of a conversation and “Create a group call”
  / “Create a group conversation” are pills, where they were bare text.
- **The date and time pickers of a meeting** are black and yellow, with pill buttons.
- The two ZRTP dialogs lost their white ground and grey text.

### Menus, sheets and lists

- **A line divides the groups of a menu** — before the deleting row of the history, contacts,
  recordings, calls, conversation, message and meeting menus, and between the groups of the
  conversation's ⋮ menu.
- The contacts filter menu is made of pills, with a check on the chosen one; “Add to favourites”
  in a contact's menu is a pill like its neighbours.
- **The call sheets colour each control's icon by what it does**: transfer and new call green,
  chat blue, participants cyan, record orange, pause magenta.
- Every line of the call statistics and of the encryption details has an icon; so have the audio
  devices, the conference layouts and the numbers offered when a contact has several.
- **The lists inside our own dialogs are rows of pills** — decline messages, blocked numbers,
  choices (a check marks the chosen one), fonts.
- Radio buttons and check boxes are yellow when chosen and dim yellow when not; no white or grey
  mark is left.
- Every switch in the app follows the two switch colours set on the UI page.

### Landscape and wide screens

- All of the above reaches the landscape layouts of the call screens, sheets, numpad and ZRTP
  dialogs and the wide-screen layouts of the assistant and welcome pages.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+052

Same upstream base as `+048` (`2199af83`); `versionCode` `602010052`. Builds `+049` to `+051` were
steps on the way and were not released.

### The call screens name the account

- **The account a call goes through stands at the top of the call screen** — on the outgoing
  screen and on the incoming one, in portrait and in landscape: its 臨N badge, large, in the
  badge's own colours, and beside it the account's name.
- **The name is the one the account was given in its settings.** An account without a name shows
  the user part of its address. The server is not repeated under it.

### Swipe a call to dial it

- **Sweeping a row of the history aside, to the left or to the right, dials it** — the call the
  row's handset places, through the account that row's call used.
- **A green handset stands in the gap** the row leaves while it moves, and stops at the distance
  from which letting go dials: a third of the row's width, far enough that scrolling the list
  askew never places a call.
- The row comes back to its place; nothing is removed from the list. A meeting's row does not
  swipe.

### The history on a narrow screen

- **Fix: the handset, the bubble and the ⋮ at the end of a row no longer shrink away.** On the
  folded screen a long “number · label” took the width of the line, the handset became a speck and
  the bubble vanished. Each now has a width of its own — as wide as the line is tall — and the
  number is what gives way.
- **The gaps between the three are a third narrower** (19 dp and 13 dp, from 28 dp and 20 dp),
  which leaves the number more room.
- **Fix: the number is never cut short.** Every row read `+420-601-123-45…`, one digit short for
  the sake of an ellipsis wider than the digit. When “number · label” does not fit, the label goes
  and the number stands whole, with no ellipsis; a number still too long is drawn a little
  narrower before anything is cut.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+048

Same upstream base as `+043` (`2199af83`); `versionCode` `602010048`. Builds `+044` to `+047` were
steps on the way and were not released.

### Unknown callers get a name

- **A number in no address book is looked up while it rings.** For a Czech number the public page
  of tellows.cz often says who is calling. The name found there takes the number's place on the
  incoming-call screen, and on the first line of every row of that number in the history.
- **The “ID” mark.** A name found online is followed by “ID” in black on a filled yellow rounded
  square, on the call screen and in the history, so it is never taken for a contact of yours.
- **The call is never held up.** It rings at once under its number; the name arrives a moment
  later, and a lookup slower than four seconds is given up.
- **Every ring asks again.** A name found on an earlier call shows at once and is replaced when the
  fresh answer differs; a fresh “nothing known” takes it away, a request that failed changes
  nothing.
- **Only when it makes sense:** never for a caller who is in 連絡先, never for the first leg of a
  callback ordered here, and for now only for Czech numbers (`+420`).
- **“ID online” in the history's long-press menu** asks about a number on the spot, for a number
  in no address book. The rows take the name when one is found; finding nothing is said in a flash.
- **A switch, because the number leaves the phone:** “Identify unknown callers online” in the Calls
  section of the 臨電話 UI page, on by default. Off stops the lookups and hides the names already
  found.
- **The names travel with the call history** in the export and import, as `online_ids.json`.

### Saving a caller to the address book

- **Fix: the number is handed to 連絡先 in international form.** “Add to contacts” on a call from
  a Czech caller opened the editor with nine bare digits (`601123456`); it now carries
  `+420601123456`.
- **A name found online goes along as the surname** — as a structured name row, the way Android
  hands a name over in parts. 連絡先 needs its own update to read it.

### Flashes

- **Every flash is black and yellow, in the middle of the display.** The short messages the app
  shows — “copied”, “blocked”, an error, the notes on the UI page and in the export panel — were
  upstream's green, blue and red toasts sliding in at the top, or Android's grey toast at the
  bottom. They are now one box in the theme's ground and text with the accent frame, centred.
- **Only the icon keeps its colour:** green for done, blue for a note, red for something that went
  wrong.
- A flash takes no touches, so the list under it stays usable, and a second flash replaces the
  first. The persistent red banner at the top is unchanged.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+043

Same upstream base as `+040` (`2199af83`); `versionCode` `602010043`. Builds `+041` and `+042` were
steps on the way and were not released.

### Blocked numbers

- **The history shows who is blocked.** Every row of a caller who is in the blocked numbers carries
  a filled red pill with white “Blocked” right after the number, on the row's middle line. It marks
  the caller, not the call: calls from before the block carry it too, and unblocking takes it off
  all of them. A long number with a label gives way to the pill before the pill is pushed aside.
- **The list redraws at once** when a caller is blocked or unblocked — from the row's menu, the
  “Blocked numbers” register or an import — without leaving the page.
- **Fix: the confirmation names an unknown caller by the number.** Blocking a caller who is in no
  address book said “720720156›666 is blocked” — the provider's display name, the number followed
  by the tail of the line it rang. It now reads “+420-720-720-156 is blocked”, and that display
  name is no longer kept in the register as the caller's name.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+040

Same upstream base as `+038` (`2199af83`); `versionCode` `602010040`. Build `+039` was a step on
the way and was not released.

### Blocked numbers

- **Block on the incoming-call screen.** A fourth control beside Silence, Speaker and Message: a
  red ring with a struck-through circle. The touch silences the ring at once and asks “Block this
  caller?” with a red Block pill and a plain Cancel pill; Block puts the caller in the register and
  declines the call. A caller who gives up while the question stands is blocked all the same. The
  button is not shown for a caller who withholds the number.
- **The top row of that screen holds four controls:** its columns are 84 dp instead of 112 dp, the
  same 336 dp across.
- **Block and unblock from the history.** The long-press menu has “Block number”, which asks with
  the same red pill, or “Unblock number” for a caller already blocked.
- **The register.** “Blocked numbers” in the top row's menu and in the Calls section of the 臨電話
  UI page lists the blocked callers newest first, each with the name it had and when it was
  blocked. A tap unblocks one; “+ Add” takes a number or `user@domain` typed by hand.
- **A register of our own.** Android's blocked-numbers list is open to the default dialer only, so
  the list lives in the app. A number is one entry however it is spelled (`+420…`, `00420…`, a bare
  nine-digit Czech number); a caller who is not a number is blocked by `user@domain`. A block
  holds for every account.
- **A blocked caller is turned away unannounced:** no call screen, no ringer, no notification, no
  Telecom registration — the call is declined as soon as it arrives. It stays in the history, and
  its stamp dialog says “Blocked”.
- **Export and import** carry the register as `blocked_numbers.json` under a new category,
  “Blocked numbers” (id `blocked`); import merges and never clears.

## 6.3.0-alpha+2026-11-09.14-39.g2199af83+038

Rebased onto upstream `2199af83` (one commit past `92318a24`); `versionCode` `602010038`. Upstream's
version did not move, so the build counter runs on from `+035` instead of restarting — a restart
would have produced a lower `versionCode` than the one installed. Builds `+036` and `+037` were
steps on the way and were not released.

### From upstream

- A guard against a crash when the app's cache directory is missing while a generated initials
  avatar is stored: the directory is recreated, a failed write is caught, and the stock person
  icon stands in (`2199af83`).

### Look — nothing is filled

- **Answer and Call are rings.** Answer on the incoming screen and Call on the dial panel were
  solid yellow discs with a black icon; each is now a 3 dp yellow ring with a yellow icon, told
  from the neutral keys beside it by weight alone.
- **Decline and Hang up are red rings** with a red icon, on the incoming and the outgoing screen,
  where they were solid red discs.
- **Upstream's own call keys follow:** its green call / answer key and its red hang-up key (active
  call, outgoing call, the stock incoming actions, the dial sheet, the ZRTP alert) are 3 dp rings
  in their colour, with the icon or label in that colour instead of white.
- **In-call controls at rest are thin yellow rings**, not grey discs; a disabled one is the same
  ring at 38 %. A control that is on keeps its fill, which marks a selection.
- The white discs behind the voice-recording and ZRTP buttons are thin rings.

### Menus

- **The chat bubble menu is a pill menu.** Resend, Info, Edit, Reply, Copy, Forward and Delete are
  full-width outlined pills in the menu frame, the icon inside at the left and coloured by what
  the row does (message blue, share orange, view yellow), Delete in red. The hairlines between
  rows are gone.

### Dialogs

- **The main action of a dialog is heavier than the rest:** a 3 dp frame and a bold label for OK,
  Send, the confirm pill, and Export in the export panel; 1.25 dp and normal weight for Cancel and
  every other pill. All were 1.5 dp before. Applies to the fork's own dialogs, the colour picker,
  the font picker and the export panel.

## 6.3.0-alpha+2026-11-09.14-07.g92318a24+035

Same upstream base as `+029` (`92318a24`); `versionCode` `602010035`. Builds `+030` to `+034` were
the steps on the way and were not released.

### Silencing a ringing call

- **Turning the phone face down silences the ring.** Read off the accelerometer while a call rings
  unsilenced: the screen has to be seen facing another way first and then lie face down for two
  readings in a row, so a phone already lying on its screen rings as usual. Ringtone and vibration
  stop; the call itself is untouched and can still be answered.
- **A volume button silences the ring.** The call screen takes volume up, down and mute while a
  call rings unsilenced and swallows the press; once the ringer is silent they are volume buttons
  again.
- **On a phone where the key cannot reach the app, it comes round through 自由作業盤.** Android's
  telecom layer takes a volume key during a self-managed ring as “silence the ringer”, swallows it
  and tells only the connection inside androidx core-telecom, which passes nothing on; and
  自由作業盤's key grabber holds the volume keys below the framework. So:
  - while a call rings unsilenced the app sends an explicit broadcast to `shiroikuma.jiyusagyoban`,
    action `shiroikuma.rindenwa.RINGING` with the boolean extra `ringing`, and again as `false`
    when nothing rings unsilenced any more;
  - **the call door (`content://shiroikuma.rindenwa.calls`) gains `silence_ringer`** — no extras,
    answering `OK`, `OK:nothing ringing` or `ERROR:not allowed`. It admits the apps let in to dial
    and, for this one method, the data door's certificate-pinned callers, so 自由作業盤
    (0.2.96+…+025 on) needs no switch.
- The Silence button on the call screen follows a ring silenced any other way, and the Calls
  diagnostic records what silenced it.
- A stand-in tried in `+030` to `+032`, the system's volume-changed broadcast, silenced every call
  a split second into its ring and was removed.

### Call stamps and the badge dialog

- **Each call keeps a stamp, frozen when it is made** (`SkCallStamps`): the 臨N badge with its
  ground and text colour, the account's identity, its server and transport, how long it rang, how
  it ended (you hung up, they hung up, you gave up, busy, declined, no answer, failed, not taken,
  you declined, taken on another device) with the SIP answer of a call that never came about, the
  audio codec and its clock rate, media encryption (none, SRTP, ZRTP, DTLS-SRTP), the network
  (Wi-Fi, mobile data, Ethernet; its name where the system gives one; VPN), the audio route, the
  SDK's quality rating and the other end's user agent.
- **A row's badge is drawn from its stamp.** Renumbering an account or repainting its badge no
  longer changes calls already made. A stamp taken while the account had no number keeps following
  today's badge.
- **Calls from before are stamped once**, on the first read of the history, with the badge of that
  moment and the account their record names.
- **A tap on a row's 臨N badge opens the call's dialog**, in the sister dialer's look: the badge
  drawn large, the account's user part (bold monospace, shrinking rather than wrapping) and its
  domain, a rule, then one line per known fact with an icon coloured by what the line is about —
  Call, Placed as (Odorik callback), Rang, Talked, Ended, Server, Codec, Encryption, Network,
  Audio, Quality, Recording, Other end — and the pills “Copy address” and “Close”. A fact that is
  not known is left out.
- Deleting a call from the history deletes its stamp.

### Export and import

- The stamps travel with the call history (`call_stamps.json`, categories `history` and
  `history.calls`); an import merges them.

### Call history and call screen

- **Every number is written one way** (`SkNumbers`): `+420-601-123-456`, `+1-808-555-0123`. A bare
  nine-digit number is read as Czech, and a `00` prefix as `+`; anything else is left as it came.
- **A caller in no address book is named by that number** on the first line, in the history and on
  the call screen. Odorik's display name — the number, `>`, and the tail of the line it rang — is
  no longer shown; a real name sent by the other end is kept.
- The call screen's number line is written the same way.
- The time of an incoming call that was answered takes the colour of the duration beside it,
  instead of the sister dialer's plain date colour.

### Fixes

- Builds again against linphone-sdk `5.6.0-alpha.234`, whose `onInvitationsSent` hands over a
  non-null list of failed invitations.

## 6.3.0-alpha+2026-11-09.14-07.g92318a24+029

Same upstream base as `+025` (`92318a24`); `versionCode` `602010029`. Builds `+026` to `+028` were
the steps on the way and were not released.

### Odorik callback

- **A callback can be ordered from the call history.** A long hold on a row's phone icon opens a
  two-pill menu — “Callback” first, “Call” second — when the row's account is an Odorik line
  (domain `odorik.cz`), or else when the selected account is one. On any other row the hold does
  nothing; a tap still calls directly.
- **The order** is one request, `POST https://www.odorik.cz/api/v1/callback`, with the line number
  and its SIP password, `caller=*<line>` and the recipient as `00<country>…` (`SkOdorikCallback`).
  A bare nine-digit number takes the account's international prefix, or 420. The line is reached
  as `*<line>`: a bare line number is accepted by the API and then never rings. A short message
  says “Callback ordered — 臨N will ring.”, or why Odorik refused.
- **The first leg is recognised when it comes in.** For 90 seconds after an order, an incoming call
  on that line from `*<line>` is that order's first leg. Its record is pointed at the person
  called before anything draws it, so the call screen and the notification read
  “Callback → name”, with that person's picture.
- **The history files it under the person called**, as a call placed here: its own arrow (up,
  round and back down) in the direction slot and the word “Callback” after the time. The SIP stack
  does not store the changed record, so the app keeps each callback's call id and number itself and
  points the record again whenever it is read back.
- **Each Odorik account has an “Odorik callback” block in its settings:** “Callback rings” (empty
  for the line itself; a number there rings that phone first instead, and nothing arrives here),
  “Line password” with an eye that shows and hides it, and “Answer the callback automatically”
  (off).
- **The line's password is kept for callback.** The SIP stack holds only a hash of it once the
  account has registered, and Odorik's API needs the password itself — so it is captured when an
  Odorik account is added through the third-party SIP login or its password is changed, and is
  typed once for an account that already exists.
- **The call door (`content://shiroikuma.rindenwa.calls`) gains `callback`,** with the same
  extras as `dial`, answering `result` and a `message` to show; it blocks for the length of the
  request. Each account in `accounts` now carries a `callback` flag.
- Each order and each recognised first leg is written to the “Outgoing calls” diagnostic, never
  with the password.

### Export and import

- The callback settings of each Odorik line — which phone rings first, the line's password, the
  auto-answer switch — travel in the **Accounts** category, as `odorik_callback.json`. **The export
  therefore holds those passwords in plain text**; until now it held only their hashes.
- Which calls were callbacks, and to whom, travels with the call history
  (`odorik_callback_calls.json`).

### Call history

- **Each row now reads phone, message bubble, ⋮** — the phone icon first. A row with no number to
  write to keeps the bubble's place empty, so the phone icons stay in one column.
- **The message bubble is the sister dialer's** (`ic_message_outline`, copied verbatim), on the
  row and in the row's menu.
- `SkMenus.popup` builds a menu of action pills in code, for menus that are not one of upstream's
  data-bound layouts.

## 6.3.0-alpha+2026-11-09.14-07.g92318a24+025

Same upstream base as `+004` (`92318a24`); `versionCode` `602010025`. Builds `+005` to `+024` were
the steps on the way and were not released.

This release makes 臨電話 the third of a set with 白い熊 電話 (the SIM dialer) and 白い熊 連絡先 (the
address book): one bottom bar, one contact store, one look.

### Contacts

- **The contacts are the address book's.** Names and pictures are read from 連絡先's
  private-contacts provider (`content://shiroikuma.renrakusaki.contactsprovider/contacts`) into a
  friend list that is never stored (`SkRenrakusaki`). It is loaded when the SIP core starts — so an
  incoming call on a cold start still finds its caller — again when the main screen returns to the
  front, and half a second after 連絡先 signals a change. Favourites come from a second query.
- **Both stores this app kept are purged, once.** The copy of the system contact store
  (`Native address-book`) and the contacts made in the app's own editor (`Linphone address-book`)
  are deleted on the first start of this version.
- **The system contact store is no longer read**, and the `READ_CONTACTS` permission is gone from
  the manifest and from the first-run permissions page.
- The app's own contact list, contact page and editor, “add to contacts”, and the CardDAV and LDAP
  settings are no longer reachable. Tapping a picture in the history opens the contact in 連絡先;
  an unknown number opens 連絡先's editor with the number filled in.

### Main screens

- **Every screen takes the full width.** The split view is gone on all orientations and fold
  states; a detail slides over the list. Upstream's tablet-landscape side rail went with it.
- **The bottom bar is shared by the three apps: Favorites, Contacts, 電話, 臨電話.** The first two
  open 連絡先 on that tab, 電話 opens the dialer's history, 臨電話 is this app's SIP history. A tab
  whose app is not installed is not shown. The other two apps open this one on its history with
  the `shiroikuma_open_history` extra.
- **The top row** is the drawer menu and one outlined search pill, always open, with a ⋮ menu
  (臨電話 UI, Recordings, Help). Long-pressing either end of the row opens the UI page.
- **The account strip begins with a 全 tab.** An account's tab shows that account's calls; 全 shows
  every account's. Choosing 全 changes only the list — calls still go out through the account last
  chosen. Its character has a font slot of its own on the UI page, or a picture in its place.
- The “All calls / Missed” switch and the delete-all bin are removed. The dialpad button is a
  traced rounded square, not a filled disc.

### Call history

- **The row is the dialer's row.** Line 1 the name; line 2 the number and its address-book label;
  line 3 the 臨N badge, the dialer's own direction arrows, the time, the duration or the reason the
  call failed (話し中 / 応答拒否 / 応答なし), a mark when the call was recorded, and the country
  of the number at the right. The picture is a square 90 % as tall as the row.
- **Message, call and ⋮ on the second line.** The bubble opens the messenger on that number; the
  handset calls back through the account that row's call used; ⋮ is the row's menu — call back,
  write a message, reply to a missed call with a stored line, the contact, copy, delete.
- **It follows the dialer's theme.** Colours, per-line fonts (imported font files included), text
  sizes, the rules between records and under the day headline, and the time, duration and date
  formats are read from `content://shiroikuma.denwa.calllogstyle/style` (`SkDenwaStyle`); this
  app's own settings are the fallback when the dialer is not installed or says nothing.
- **Tapping a record narrows the list to that contact's calls**, under a “Calls with …” banner
  with a ✕, in place of upstream's detail page. Back widens it again.
- A thin line between two calls of a day is drawn inset from both edges, and not under the last
  call of a day; its thickness and colour are the dialer's.

### 臨 numbers and the call door

- **Each SIP account carries a number, 臨0–臨9**, unique, set on the UI page (accounts listed in
  the tab strip's order, with their pictures) or in the account's settings, from a picker that
  shows the ten badges themselves. Each number has its own badge ground and text colour.
- **The call door** (`SkCallsProvider`, `content://shiroikuma.rindenwa.calls`): `accounts` lists
  the accounts with number, badge colours, identity, name, registered and default; `dial` places a
  call to `to` through `account` (a 臨 number or an identity; absent = the selected account). Only
  the apps allowed under “Calls from other apps” on the UI page are let in — 連絡先 and 電話 by
  default — and the caller's package is checked against its uid. It is separate from the data door
  on purpose: an app let in to dial is not let in to export.

### Call screens

- **A status line replaces the rotating circle** on the outgoing, incoming and ended screens, in
  both orientations: Dialing… / Ringing… / Incoming call… / Connecting… and, when it is over, Call
  ended, Busy, Declined, No answer, or Call failed with the SIP reason.
- **Japanese texts for it** (発信中…, 呼び出し中…, 着信中…, 接続中…, 通話終了, 話し中, 応答拒否,
  応答なし, 発信失敗) — the app's first `values-ja`, and `ja` in the per-app language list.
- **The outgoing-call panel** takes the incoming panel's geometry: Mute, the audio output and the
  keypad as outlined circles in a row, the red Hang up alone beneath. The audio output is live
  during early media as well as ringing, so the loudspeaker can be switched on with a provider
  that answers `183` on every call.

### Black-yellow rules

- **Menus:** every action row of every popup and bottom-sheet menu is an outlined pill across the
  menu, its icon coloured by what the row does (call green, message blue, contact cyan, copy and
  share orange, view the accent, block magenta, delete red — seven settable colours), a › when it
  opens something, red throughout when destructive. Menus have a 2 dp yellow frame, and a popup
  opened low on the screen shifts up instead of scrolling. A toggle is a plain row with a switch.
- **Switches** are outlined pills with a solid dot — accent when on, a darker settable yellow when
  off — on the UI page and on Linphone's own settings pages alike.
- **Settings and help pages:** the twenty-five rows that open a page or run an action are pills as
  wide as their label, with a › where they open something and in red where they destroy something.
- The account profile no longer shows a devices spinner for an account that is not on Linphone's
  own domain.

## 6.3.0-alpha+2026-11-09.14-07.g92318a24+004

Same upstream base as `+002` (`92318a24`); `versionCode` `602010004`. Build `+003` was the
diagnostics alone and was never released — this release carries it and the fix it led to.

### Calls

- **A call hung up while it rings now stops ringing at the other end, even when the provider
  refuses the CANCEL.** Hanging up an unanswered outgoing call sends a SIP `CANCEL`. Odorik's
  front proxy answers a well-formed one — same branch, Call-ID, From tag and Request-URI as the
  INVITE, on the same TCP connection — with `481 Transaction Does Not Exist`; the app showed the
  call as ended while the called phone went on ringing until somebody refused it there. The
  `183`/`180` that makes the phone ring also opens an early dialog with the server behind the
  proxy, and RFC 3261 §15 lets the caller end one with a `BYE`. So when a CANCEL comes back
  `481`, the app now sends that BYE (`SkEarlyBye`): the called phone stops at once, and the app's
  own call ends with a clean `487 Request Terminated` instead of hanging until a `486`.
  - The Linphone SDK has no call for this, so the fork does both halves. The early dialog — tags,
    route set, remote target — is read from the SIP stack's own log of each provisional answer and
    dropped once the INVITE gets its final one. The BYE goes out on a connection of its own (TCP
    or TLS) to the proxy the refusal came from, and answers a `401`/`407` challenge once with the
    account's credentials (MD5 or SHA-256 digest).
  - It fires only after a refused CANCEL. A provider that honours CANCEL never triggers it.

### Diagnostics

- **Outgoing calls** — a new switch. One line per state of every call placed, with the SIP code,
  the reason and the Call-ID, plus the moment the hang-up was asked for and the state the call was
  in then, and every step of the BYE fallback above with the provider's answer.
- **SIP messages** — a new switch. Every SIP message sent and received, whole, plus the SIP
  stack's warnings and errors — the only record of what was actually said on the wire, since the
  SDK's own log sits in internal storage and only leaves the app by upload. It holds numbers and
  addresses and every REGISTER refresh, so it is one to turn on for a test and off again.
- Both are off by default, like the rest, and write to the same diag directory
  (`rindenwa-outgoing_<day>.txt`, `rindenwa-sip_<day>.txt`). The SIP diagnostic listens through a
  logging listener of its own: upstream's is only registered when Crashlytics is, which this build
  does not have.

## 6.3.0-alpha+2026-11-09.14-07.g92318a24+002

New upstream base: `custom` rebased from `75c4ce26` onto `92318a24` (2026-11-09), which carries
upstream's 6.2.8, 6.2.9 and 6.2.10 releases; the upstream `versionCode` moves 602007 → 602010, so
this build is `602010002`. No fork feature changed — this release is the sync itself, what upstream
brings with it, and the two things the merge could not settle on its own.

### From upstream (6.2.8 / 6.2.9 / 6.2.10)

- **The call screen no longer lands in the recent-tasks list, and answering from the notification
  no longer drops the app into the background.** `CallActivity` gained `taskAffinity=""` and
  `excludeFromRecents`, and the launch-from-notification path moved into `LinphoneUtils` — the one
  upstream change in this batch that touches the fork's own full-screen raise path.
- **The listening UDP and TCP ports moved from Random to Dont Bind** (`sip_port` / `sip_tcp_port`,
  `-1` → `-2`): the stack stops binding a listening socket and relies on the path its own
  registration opened. That is the setting deciding whether an incoming call reaches the phone at
  all, so it is the thing to watch on this build.
- **An All calls / Missed filter above the call history** (6.2.9) — repainted here, see below.
  Upstream added a second copy of it to its landscape layout; this fork needs neither, one layout
  set serving both orientations.
- **Every Intent carrying a URI now grants read permission on it**
  (`FLAG_GRANT_READ_URI_PERMISSION`), which Android 18 will require — it covers sharing a
  recording, a received file, a contact photo.
- A call shown on a watch or other connected device reads better when the remote end's username is
  a phone number; the proximity sensor listener is registered only while the proximity wake lock is
  actually held.
- OIDC login parses the JWT to recover the username when the account did not carry one, which also
  stops a stale `auth_info_X` lingering in the config after logging out; client-secret support had
  arrived just before.
- Chat: a mention is matched whatever its capitalisation; chat rooms are no longer forced to a zero
  ephemeral lifetime (remote provisioning needs that); and a 1-1 room can be created on a Flexisip
  backend without end-to-end encryption instead of silently falling back to a Basic one.
- The usual batch of list and popup repairs: a selected row no longer stays highlighted after the
  pane closes on a reordered list, a popup near the bottom edge gets its real height, menu icons
  stay highlighted while their popup is open, the default account's own address is hidden from the
  participant pickers, and conversation info redraws when administration rights change.
- Tooling: Gradle 9.7.1 → 9.8.2, AGP 9.4.0 → 9.5.1, Kotlin 2.4.20 → 2.4.21,
  `androidx.core:telecom` 1.1.0-beta01 → **1.1.0** stable, navigation 2.10.2, annotations 1.11.0,
  coil 3.6.3, Firebase BOM 35.1.0 (inert here — there is no `google-services.json`). The Linphone
  SDK stays on `5.6.+`.

### Ported through the rebase

- **The hidden separators upstream deleted as unused are kept**, because here they are not:
  `HistoryListAdapter` and `ContactsListAdapter` hand `binding.separator` to the UI page as the row
  divider whose thickness and colour are live controls. Both `@+id/separator` views stay in
  `history_list_cell.xml` and `contact_list_cell.xml`, and the time line that was anchored to one
  of them anchors to its row instead.
- Upstream's new `<attribution>` manifest tag sits beside the fork's `<queries>` block for the two
  automation callers; both are additions, both kept.
- The landscape history layout upstream edited stays deleted — one layout set serves both
  orientations here.

### Repainted and repaired

- **Upstream's new filter, in the house colours.** It marks the chosen segment with an accent fill
  and a white label, written as a raw `bc_white` reference that the sixty-attribute theme overlay
  cannot reach; on this palette that is white text on yellow — illegible, and against the rule that
  a pressable thing is outlined and never filled. The selection now carries the thin 1.25 dp frame
  of every other pill over the yellow wash, its label in full ink, the other segment dim.
- **The call history has its bottom edge back.** The merge took upstream's
  `layout_constraintBottom_toTopOf="@id/bottom_nav_bar"`, but upstream keeps that nav bar inside
  the same `ConstraintLayout` while this fork's single-layout rework moved it out — so the
  constraint pointed at a non-sibling, was dropped at runtime, and left a `0dp`-high list with no
  bottom edge. Caught as the one new lint `NotSibling` error; it anchors to the parent again.

## 6.3.0-alpha+2026-09-11.12-50.g75c4ce26+011

Same upstream base (`75c4ce26`). The phone can now be *asked* whether it is able to take a call, and
answers out of its own registration state rather than out of the fact that its process exists; it
can be told to record what it does, behind switches that are all off until one is turned on; and
every row in the UI page that does something when tapped is drawn as the house pill instead of as a
heading.

### A door 自由作業盤 can knock on

- **Why it exists.** 自由作業盤's 生存監視 watches every sister app on the phone, every 15 minutes and
  on every unlock. For a softphone there is only one question worth asking — *can a call reach it
  right now* — and no app can answer that about another from the outside: a process can be up,
  visible to `ActivityManager`, and still be deaf because not one of its accounts is registered.
- **The door is a broadcast**, `shiroikuma.rindenwa.action.KEEP_ALIVE`, received by an **exported**
  manifest receiver that takes **no permission and no token**. That is deliberate: a manifest
  receiver is the only door that still opens after a force-stop, which is exactly the state worth
  asking about, and a permission or a shared secret would turn the rescue path into one more thing
  that can be mis-configured at 03:00. The door reveals only whether this phone can ring.
- **The answer goes back twice over**, because the caller may be in either of two situations: the
  ordered broadcast's result (`setResultData`) for the one that can wait, and a fresh intent to the
  `reply_action` / `reply_package` it named, carrying its `reply_id` back with
  `FLAG_INCLUDE_STOPPED_PACKAGES`, for the one that cannot. Both are sent **exactly once**, guarded
  so a slow answer and a timeout can never produce two.
- **One word, and a reason when there is one:** `OFF` (nothing to keep alive — 「keep service
  alive」 is off, or no account has registration enabled), `ALIVE`, `STARTED` (the Core or the
  service was dead and has been started; registration takes a few seconds and the next check
  confirms it), `RESTORED` (an account was not Ok, was kicked, and came back), `OFFLINE` (no usable
  network, so nothing is wrong), or `ERROR:<reason>`. **No reason ever carries a password or a
  token**; a SIP identity may, since that is the thing being named.
- **A reason says which account and why**, down to the SIP code —
  `ERROR:not registered: sip:…@… — Failed (403 Forbidden)` — so the notification 生存監視 raises is
  already the diagnosis instead of an invitation to go looking.

### What the door checks, and what it repairs

- The work runs off the broadcast thread (`goAsync`, one named thread) inside a **13 s budget**, so
  the answer is always inside the 15 s the contract allows, whatever the Core is doing.
- The account list is read **on the Core thread** (`postOnCoreThread` and a latch) because the SDK is
  not thread-safe, after waiting up to 6 s for the Core to reach `GlobalState.On` — a check that
  arrives two seconds after a cold start would otherwise report a phone that is merely still waking
  up as broken.
- **Repair, in the order that fixes things:** start the keep-alive foreground service if it is down;
  tell the Core the network is reachable if it disagrees with the system; `refreshRegisters()`; then
  wait out a 3 s settle before deciding anything, because `Progress` is not a failure.
- **The service is started the one way that is legal from the background** —
  `startForegroundService`, never upstream's `startService`, which throws when the app is not in the
  foreground — and only after checking that its notification channel is enabled and that this app
  holds one of the Android 12+ exemptions (「Display over other apps」 or battery-optimisation
  exemption). A `startForegroundService` into a disabled channel is killed moments later by
  `ForegroundServiceDidNotStartInTime`, so that case is **reported rather than attempted**.
- **Blockers the app cannot fix are named rather than worked around**, appended to an otherwise good
  answer as `ERROR:alive, but …`: notifications turned off, the service channel or the incoming-call
  channel turned off, background restriction, and the `android:start_foreground` app-op set to
  ignore — the one EMUI's PowerGenie likes to set, and the usual reason a call dies without a line
  of app code being at fault. Battery optimisation is reported **only when it changes**, since it is
  a standing choice rather than a fault.
- **Measured on the phone**: `ALIVE`, then a force-stop, then `STARTED` with the process and the
  foreground service back up (the system attributed the allowed start to
  `SYSTEM_ALERT_WINDOW_PERMISSION`), then `ALIVE` eleven seconds later with all seven accounts `Ok`.
  自由作業盤's first live check answered `ALIVE` through the reply broadcast in **38 ms**, with the
  matching `reply_id` in the day's trace.

### Diagnostics, off until you ask for them

- **Three logs, each behind its own switch, every one off by default** — Registration (every
  account's REGISTER answer with the SIP code behind a failure), Incoming calls (when a call
  arrived, whether the call screen could be raised, how it ended), Service and process (process
  starts and the keep-alive service going up and down). Turning one on records the app version, so a
  file says what was recording it.
- **Recorded data**, under its own subgroup: **Stored** (files, size and the span of days, counted
  on a background thread), **Keep for N days** (0 — the default — keeps everything; clean-up runs on
  leaving the page and at start), **Export** to a directory picked every time and offered at
  `/sdcard/tmp`, copied into a dated `<yyyy-MM-dd_HH-mm-ss>_rindenwa-diag` subdirectory, **Share**
  through the app's own FileProvider, **Purge** behind a warning that states how many files, how big
  and which days go forever, and a row printing the `adb` path, which needs no permission at all.
- **A notification stays up while anything is recording**, carrying **Mark** (a numbered line, for
  marking the moment something went wrong) and **Turn all off**.
- **One file per kind per day**, `rindenwa-<kind>_<yyyy-MM-dd>.txt` — **never `.log`**, which EMUI's
  nightly clean-up deletes as junk while it charges, and **nothing is held open**, so a file removed
  under us is recreated on the next line instead of every further line vanishing into an unlinked
  inode. A day's file is capped so a runaway logger cannot fill the phone.
- **The keep-alive door's own answers are the one thing recorded unconditionally** — one short line
  per call. They are the receipt of a contract another app depends on, and a switch that was off at
  03:00 would have recorded nothing about the call that never came.

### Every action is a pill

- **The house rule reaches the UI page** (and the nine button shapes behind the rest of the app): a
  pressable thing is an **outlined** pill — never filled — the frame 3 dp and the label bold for the
  one action a section is about, 1.25 dp and normal for the rest, a wash instead of a fill when
  pressed, and frame and label at 38 % when disabled.
- **A pill is as wide as its label and no wider.** Stretching a short action like Export across the
  line makes it read as a banner rather than as a button; a long label still fills most of the line
  on its own, which is the width it should have.
- Each pressable row carries its explanation **beneath** the pill in dim gold, aligned with the
  label, and a **›** only when a screen opens — a row that acts in place after a confirmation (Purge,
  Regenerate) carries none. Rows that have a control of their own — switch, swatch, slider — stay
  plain, the control being affordance enough.
- Regenerate, which destroys the automation token in place, is the same pill in the warning red it
  is written in: a destructive action lends its colour to the frame, never to a fill.
- Every row drops to minimal padding with **no platform 48 dp floor**, so the page reads as a list
  again instead of a column of bands.

## 6.3.0-alpha+2026-09-11.12-50.g75c4ce26+009

Same upstream base (`75c4ce26`). The screen a call arrives on is the fork's own now, in black and
yellow, with five controls instead of two; refusing a call can answer it with a real SMS, sent from
the SIM behind the line it rang on; the dial pad has stopped being a screen of its own and become a
sheet that rises over the call history and filters it as you type; and the main screen no longer
falls apart when the phone is turned sideways.

### An incoming-call screen in the fork's own colours

- **It was still stock Linphone's** — a white sheet, upstream's own tint lists, and a lock-screen
  slider you had to drag. The fork's black-yellow theme works by remapping about sixty theme
  attributes, and the call layouts bypass that entirely by naming raw `@color/` resources, so they
  were the one part of the app the overlay could not reach.
- Repainting is therefore done where the raw references land: the three in-call colour state lists
  (`in_call_button_tint_color`, `in_call_label_color`,
  `in_call_button_background_tint_color`) now carry yellow icons and labels on raised surfaces,
  invert to a black glyph on yellow when pressed, and dim to the fork's ghost and disabled inks.
  `call_header_style`'s white text becomes yellow — that style is used by nothing but the call
  screens. **`bc_white` itself is left alone**, because sixty-eight other resource files read it.
- The panel is the fork's own layout rather than an edit of upstream's, and both orientations use
  it. Three 112 dp columns across the top row make 336 dp, which still fits the Mate XT's folded
  cover panel with room to spare; the decision pair below is 72 + 56 + 72. Labels are centred at
  their full column width with a single line and an ellipsis, so a long one cannot spill into its
  neighbour.
- **No bar height is declared any more.** The panel measures itself, so changing a button size or
  a label moves the whole screen with it instead of leaving the avatar block overlapping or
  floating; the land variant's two hard-coded bottom margins are gone with it.
- **The lock-screen slider is dropped and upstream's two action bars go back to their original
  contents.** One panel now serves a locked and an unlocked screen alike, which is also one layout
  fewer to keep in step on every upstream sync.

### Silence, Speaker, Message — and the decision pair

- **Silence** is unchanged from the previous build and keeps its place, now as the first of the
  three columns.
- **Speaker** answers the call and latches the audio route to the speaker. The latch is applied at
  `StreamsRunning` and not on the tap, because upstream forces the earpiece at `Connected`
  whenever ring-during-early-media is on — routing at the tap is overridden a moment later by the
  SDK itself, which looks exactly like the button not working.
- **Message** refuses the call and writes back; see below.
- **Decline** and **Answer** sit on their own row underneath, red and yellow, far enough apart
  that the one you did not mean is not the one you hit.

### Refusing a call, and still answering it

- **The reply does not leave through this app.** These lines are SIP DIDs forwarded from real SIMs
  in a bridge phone elsewhere, so a SIP `MESSAGE` sent back down a DID arrives at the gateway, not
  at the caller's handset. The previous attempt at this feature did exactly that and could never
  have worked.
- The text is handed instead to the sister messenger (**白い熊 メッセージ**, `shiroikuma.messeji`)
  through its automation provider, which sends it as a real SMS from the SIM behind that DID over
  the 自由作業盤 bridge. The call is identified by **the DID it arrived on** — the user part of the
  account identity it is bound to, read off the call *before* it is declined, because declining
  releases the addresses it is read from.
- **Which SIM that is, is none of this app's business.** The DID → SIM table lives in 携帯, so one
  list is edited instead of two. Note that the DID is whatever the provider puts in the SIP
  username: Odorik's six-digit line id on one account, E.164 on another. A DID missing from the
  table comes back as a refusal naming it, which is the only way a gap in that table can surface
  here — so the refusal is put on screen rather than written to a log.
- **A success means queued, not sent.** The bridge may be asleep and the relay phone out of
  signal. メッセージ never drops a message it has accepted and its own thread carries the delivery
  ticks, so that thread is the record and nothing here polls for one.
- **The call is always declined first**, before a single byte is handed over. Everything after that
  is bookkeeping, and bookkeeping must never be what keeps a phone ringing.
- **The ended-call screen is then held open** — up to a minute — until the hand-off has answered.
  That is what makes the two things that have to be seen visible without a notification: a refusal
  from メッセージ, and the question of whether to keep a line that was just typed.
- **A line that was typed is sent first and only then offered for keeping**, to everyone or to this
  caller alone. Tapping a line that is already stored sends it and asks nothing — one tap, call
  refused, answer on its way.

### The lists, on the UI page

- Two lists: the one offered for every caller, seeded with four defaults, and a per-caller one
  keyed by the caller's bare SIP address, which is how a recurring call gets its own answer without
  that line cluttering every other call.
- Both are edited in the CALLS section of the 臨電話 UI page, which reports how many lines and how
  many callers have their own. Tap a line to edit it, move it up or down, or delete it; the picker
  on the call screen is for using them, not for keeping them in order.
- **Deleting every general line sticks.** An empty list is stored as an empty list rather than by
  removing the key, which would have fallen back to the four defaults at the next call.

### The dial pad is a sheet over the history, not a screen

- **Tapping "new call" used to navigate away** to a whole screen of its own: its own search field,
  its own contact list, a grey numpad in a sheet underneath. Leaving the history to dial is
  backwards, because the number wanted is nearly always one the history already holds.
- It is now a bottom sheet that rises **over** the list, in the sister dialer's shape — the typed
  number over a yellow rule, a backspace whose long press clears it, a chevron that puts the sheet
  away. Nothing navigates and nothing is lost.
- **Every digit is pushed straight into the list's own filter underneath**, so the call being
  looked for walks up to meet the number being typed.
- **Closing the sheet clears the digits.** A filter left behind by a panel that is no longer on
  screen is a list that lies. The hardware back button closes the sheet while it is up rather than
  leaving the Calls tab, and the "new call" button hides itself while it is open rather than
  floating on top of it.
- The twelve keys are twelve includes of **one** layout instead of upstream's four near-identical
  ones, and upstream's own `NumpadModel` is kept untouched behind them, so the DTMF tones, the
  long-press rules (`0` gives `+`, `1` calls the voicemail box) and the system's
  "play tones while dialling" setting are all still its own. Only where its callbacks lead is ours.
- The rows are plain `LinearLayout`s rather than a `ConstraintLayout` `Flow`: the sheet has to
  measure its own height, and rows of fixed-size keys say what that height is without anyone
  having to trust a virtual helper to report it. Three 76 dp keys with 16 dp between them make
  244 dp, so the keypad keeps its shape on the folded cover panel as well as unfolded.
- The digits survive a rotation, which on a Mate XT is a thing that happens by unfolding, and are
  scoped to the Calls tab, so leaving it forgets a half-typed number.
- **A note for whoever touches this next:** the bottom-sheet behaviour is declared on the
  `<include>`, not on the panel's own root. An include that sets its own `layout_width` and
  `layout_height` builds the child's `LayoutParams` from the include tag and **discards every
  `layout_*` attribute written on the included root**, `app:layout_behavior` among them. Getting
  that wrong is not a layout that misbehaves — it is `IllegalArgumentException: The view is not
  associated with BottomSheetBehavior` on startup. Both files now carry a comment saying so.

### A missed call can be answered too

- A missed call in the history carries a **reply button**, which opens the same picker and sends by
  the same route. There is nothing left to decline by then — only the writing back.
- It is shown for a missed call that was not a conference, and the record's duration moves aside
  to make room rather than being covered.

### The main screen survives being turned sideways

- Rotating used to mangle it: the account tab strip disappeared, the Favourites tab with it, and
  the call log was squeezed into a column beside an empty detail pane.
- The cause was never a sizing bug. Upstream keeps a **separate `layout-land/` copy** of the four
  list screens, the bottom navigation bar and the main top bar, and the fork had only ever themed
  the portrait ones — so landscape fell straight through to stock Linphone's tablet arrangement.
- The portrait layouts are already orientation-agnostic (the fork's own Favourites screen was
  written that way from the start), so the landscape copies are **deleted** rather than themed a
  second time. One layout per screen also means the next upstream sync conflicts here as a trivial
  "deleted by us" rather than twice over, in two places that must then be kept in step by hand.

## 6.3.0-alpha+2026-09-11.12-50.g75c4ce26+004

Same upstream base (`75c4ce26`). An incoming call now takes the whole screen even when the phone is
already in your hand, and the ringing can be silenced without answering or declining.

### A call screen that actually appears

- **Upstream only ever asked for one.** A call was announced with a single notification carrying a
  full-screen intent, and Android honours such an intent only while the device is locked or its
  screen is off. On an unlocked phone it is downgraded to a heads-up banner — so a call arriving
  while the phone was in use dropped a strip at the top of the screen and nothing more. No
  notification flag changes that; it is documented platform behaviour.
- The fork therefore **starts the call screen itself** the moment a call comes in, and leaves
  upstream's notification exactly as it was, so the locked screen, the shade and Android Auto are
  all still served by it.
- **It is raised once per call.** The call-state listener fires again for early media and for every
  state after it, and the notification is rebuilt more often still; raising the screen each time
  would drag you back into it every time you pressed Home while the phone rang.
- **"Display over other apps" is the price.** With none of the app's own activities in front, that
  start is a *background activity start*, which Android 10+ allows only to an app holding
  `SYSTEM_ALERT_WINDOW`. No overlay window is ever drawn — the permission is purely the licence to
  start the activity. The app asks for it on start, and the Calls section of the 臨電話 UI page
  reports whether it is held and opens the system screen carrying the switch. Without the grant the
  app falls back to upstream's banner rather than failing silently.

### Silence, as a third button

- The incoming screen and the notification both carry a **Silence** button beside Answer and
  Decline, and it does neither: it takes the ringtone and the vibration away and leaves everything
  else alone. The call stays up, the caller goes on hearing it ring, and you can still answer.
- **One call does it.** Upstream creates the incoming-call notification channel without a ringtone
  and never enables its vibration — both come from the Linphone SDK's own Core, which plays the
  ringtone on the ringer device and drives the vibrator itself. So `Core.stopRinging()` silences
  both with the call untouched, and no second "silent" notification channel is needed.
- It is **re-applied on every later incoming state**, because a call moving on to early media with
  ring-during-early-media enabled can start the ringer up again.
- Silencing is **one-way**: the button dims and relabels itself to "Silenced" rather than offering
  an un-mute, and the notification drops its button once used. A second press is a no-op either way.
- The control sits **above** the answer/decline pair and above the lock screen's answer slider, so
  it is in the same place whichever of the two the screen shows, and clear of the slider's travel.
  The incoming action bar is taller than upstream's to seat it.

## 6.3.0-alpha+2026-09-11.12-50.g75c4ce26+002

Same upstream base (`75c4ce26`). A restore made by 応用管理 onto a freshly installed phone on
2026-09-13 came out with the accounts but without the call history or a single favourite; this
build fixes the Export/Import layer so that cannot happen again, and makes favourites survive a
change of phone at all.

### The restore is staged, and applied at the next cold start

- **Config sections and databases are no longer written into place by the import.** They used to
  be: `.linphonerc` sections merged on disk and `linphone.db` overwritten while the Core held
  those very files open — and on the 応用管理 path the process is *started by* the restore
  request, so the Core's own start-up writes raced the overwrite. The accounts happened to
  survive; the history database did not.
- They now go to `filesDir/sk_pending_import/`, with a `ready` marker written last so a stage the
  process died halfway through is discarded rather than applied. `LinphoneApplication.onCreate()`
  brings the stage live at the next cold start — after the default assets exist, before the Config
  is read, long before the Core opens a database — and removes stale `-journal`/`-wal`/`-shm`
  siblings as each database is moved in.
- 応用管理's force-stop after a restore is therefore the plan, not a hazard; the Export/Import
  panel's "Restart now" does the same, and "Later" simply leaves the stage for the next start.
  The panel's import-done dialog now says which categories wait for the restart, and only offers a
  restart when something is staged.
- **Preferences are `commit()`ted, not `apply()`ed.** `apply()` only promises the disk write
  eventually, and the process is force-stopped or restarted straight after the reply — imported
  settings had gone missing exactly that way.
- **An export re-reads a database that had a hot `-journal` during the copy**, so an archive never
  carries half a transaction.

### Favourites survive a change of phone

- A favourite in the Linphone SDK is a star on a friend keyed by the *native contact id*, and the
  fork's tile order stored the same ids. Neither survives a move: the address book arrives with
  new ids, the first contact sync throws the old friends away, and every star goes with them —
  so even a perfectly restored database lost the Favourites tab.
- When the staged `linphone.db` is applied, its starred friends are read out as something
  portable — the name, the numbers and the SIP addresses of the vCard the SDK stored, in the
  stored tile order — and parked. Once the native contacts have been loaded, each one is matched
  by number (suffix-tolerant, so `0737…` and `+420737…` are the same line), by SIP address, or —
  for a contact with neither — by name; it is starred again and the tile order is rewritten in the
  new phone's ids. A favourite nothing matches is dropped with a log line.
- This reads the archive's own database, so **an archive made by an older build restores its
  favourites too** — no need to update the old phone before exporting from it.

## 6.3.0-alpha+2026-09-11.12-50.g75c4ce26+001

New upstream base: `custom` rebased from `5bc702e4` onto `75c4ce26` (2026-09-11), which carries
upstream's 6.2.6 and 6.2.7 release lines; the upstream `versionCode` moves 602005 → 602007, so
this build is `602007001`. The fork's own code is unchanged apart from what the rebase required.

### From upstream (6.2.6 / 6.2.7)

- **Answering an incoming call from the notification shows the call screen again.** The "Answer"
  action used to go through a broadcast receiver that then started `CallActivity` — a notification
  trampoline, which Android 12+ silently refuses. The action now launches `CallActivity` directly
  with an `AnswerIncomingCall` extra and the activity answers on arrival. The same fix covers the
  call-redirection notification.
- **An account with only an outbound proxy and no registrar now uses that proxy.** Both the
  third-party SIP login and the account settings used to drop the outbound proxy when the server
  field was empty; the proxy now becomes the server address.
- **Copying an address from the call history copies just the number** when it is a phone number,
  with its own toast.
- Selected item stays highlighted in every list when the sliding pane is side-by-side (landscape
  or desktop mode) and survives rotation; smoother sliding-pane open animation; going back onto
  an empty grey pane no longer happens.
- Contact creator can be pre-filled with a display name; the contact editor says when an address
  will not parse instead of dropping it.
- Meeting invitations are sent by chat even without LIME; the active speaker updates as soon as
  the first participant joins; Linphone-Web participants show their name rather than an IP.
- Media player and recordings keep their position across rotation; forwarded incoming messages in
  a 1-1 room no longer draw an empty bubble or extra top space; the conversation list shows a
  queued sent message at once; the contacts list no longer clips at the top when nobody is a
  favourite; a null contact name no longer crashes the contacts list.
- Desktop mode (`sw600dp-land`) keeps the system-bar colour on the device theme rather than
  orange.
- Tooling: Gradle 9.6.1 → 9.7.1, AGP 9.3.1 → 9.4.0, Kotlin 2.4.10 → 2.4.20, `androidx.core:telecom`
  1.1.0-alpha06 → 1.1.0-beta01, navigation 2.10.1, appcompat 1.8.0, coil 3.6.2. The Linphone SDK
  stays on `5.6.+`.

### Ported through the rebase

- Upstream moved list activation and selection into a shared `GenericListAdapter` base and made
  `onSlidingPaneClosed()` abstract on every main fragment. The fork's contacts, history and
  conversations adapters now extend that base — the letter sections, the favourites order and the
  per-row styling are unchanged — and the fork-only favourites tab implements the new member.
- Upstream renamed the name views in the contact and call-log rows (`name` →
  `contact_name` / `call_log_display_name`); the fork's number line, full-width dividers and
  styler calls follow the new ids.
- Favourite tiles now fire upstream's `favouriteContactClickedEvent`; the favourites tab listens
  to it, so tapping a tile still opens the contact.
- Upstream's "highlight the open contact" looks the contact up by index in the view model's list;
  here the letter headings sit between the rows, so the fork asks the adapter for the position
  instead, and a contact hidden inside a folded section clears the highlight rather than
  pointing at a row that is not there.

## 6.3.0-alpha+2026-08-07.11-31.g5bc702e4+003

Same upstream base (`5bc702e4`); the version pin now groups with `+` and carries the minute, so the
name reads `6.3.0-alpha+2026-08-07.11-31.g5bc702e4+003`. The fork's automation grows a second half —
a data door that lets 白い熊 応用管理 rebuild this app *with its data* on a wiped phone — and the
token that used to guard the first half becomes optional. Three faults in the existing automation
surface turned up while doing it, all of which failed silently, and one of which meant the feature
had never worked at all.

### The state-export path had never once worked

- **There was no `<queries>` element in the manifest.** Since Android 11 a `setPackage()` broadcast
  to a package outside your visibility filter is not delivered and no error is raised, so every
  reply this fork has ever sent to 白い熊 自由作業盤 was discarded in silence. The export ran, wrote
  a correct archive, and reported to nobody. The element now names both `shiroikuma.oyokanri` and
  `shiroikuma.jiyusagyoban`; `adb shell dumpsys package queries` shows the mapping, which is what
  distinguishes a fixed manifest from an edited one.
- **The reply and progress broadcasts fell back to an implicit send.** Both passed
  `setPackage(replyPackage.ifEmpty { null })`, and passing `null` there does not broadcast more
  widely — it broadcasts where nothing can hear, because since API 26 an implicit broadcast reaches
  no manifest-declared receiver at all. Both now decline to send without a target package.
- **A stale job id crashed the data service.** `startForeground` was reached only on the path that
  had work to do, so a duplicate or already-answered id returned first and earned a
  `ForegroundServiceDidNotStartInTimeException`. It is now called first and unconditionally, and a
  stale id stops silently rather than sending a second terminal reply for a request that has
  already had one.

### The token is optional, and the switch ships on

- **`automation_enabled` now defaults to on and `automation_require_token` to off.** A pasted
  48-character secret cannot survive a wipe, and the case this whole family now serves is 応用管理
  restoring apps onto a clean phone where nothing has been configured yet. A gate that only works
  once the phone is already set up is no gate for setting the phone up.
- **A token sent to an app that is not asking for one is ignored, never refused.** Tokens outlive
  the setting they were pasted for; refusing them would turn one switch being off into half a batch
  mysteriously failing.
- **Both checks live in one function.** `SkAutomation.refuse()` answers either `null` or the exact
  `ERROR:` line, so "automation disabled" and "bad token" cannot drift apart between the receiver,
  the provider and the service.
- **The switches are written with `commit()`, not `apply()`.** This matters only now that the
  default flipped: under the old default a lost write fell back to "closed", which was merely
  inconvenient. It now falls back to **open**, and 応用管理 force-stops an app the moment it
  reports a successful import.
- 「Use authorization token?」 sits under the master switch in the Export/Import section, and the
  token row appears only while it is on — a long secret under an off switch invites pasting it
  somewhere it will do nothing.

### The data door

- **A `ContentProvider` at `shiroikuma.rindenwa.automation`** with `describe`, `export`, `import`
  and `cancel`. `describe` answers a header — app id, version, format, whether a launch is required
  first, and what the archive would contain — without exporting anything, so a caller can draw a row
  and judge compatibility before streaming tens of megabytes.
- **The caller is identified three ways: exact package name, process uid, and a pinned signing
  certificate.** Not a `shiroikuma.*` prefix, which is not an identity — package names are not a
  namespace anyone owns, so any sideloaded app could take one and, since the caller supplies the
  destination, be handed this app's complete data. The certificate pin is what covers the case the
  restore exists for: whichever caller is *absent* from the device is a name anyone can claim, and a
  clean phone is precisely where not everything is installed yet.
- **The payload travels down a `ParcelFileDescriptor` the caller opened**, duplicated before it
  leaves the provider call and closed in the service's `finally`. Not a path: 応用管理 assembles a
  backup in a temporary directory it renames on commit, and encrypts and checksums per file it knows
  about, so a file this app dropped in itself would be renamed out from under it and would sit in
  plaintext inside an otherwise encrypted archive.
- **`import` exists only here and never as a broadcast action.** The state-export receiver is
  exported without a permission, so an import there would let any app on the phone overwrite this
  one's accounts and history.
- The work runs in a foreground service with the export engine's own cancellation, so a long run is
  stoppable and a cancelled one leaves nothing half-written.

### What the archive carries

- **A backup of this app is a backup of its SIP credentials.** The `accounts` category copies
  `.linphonerc`'s `proxy_*`, `auth_info_*` and `nat_policy_*` sections verbatim, and `auth_info_*` is
  where the password lives — as cleartext `passwd=`, or as the `ha1=` digest, which is equally usable
  to authenticate. A restore therefore puts working credentials on whatever phone it lands on. That
  is arguably what a restore should do, since accounts are most of what makes this app useful, but it
  is a property of the archive worth knowing rather than discovering.

### Note

- **Installing a new build drops SIP registration** while the process restarts, so the app is briefly
  unreachable for calls. Ordinary for a reinstall, and worth expecting rather than reading as a fault.

## 6.3.0-alpha.2026-08-07.g5bc702e4+002

Rebased onto upstream `5bc702e4` (upstream `versionCode` 602004 → 602005), so the build counter
restarts at 1 and the fork `versionCode` becomes 602005002. Upstream fixed the assistant's window
insets and finished the Italian translation; the fork's de-branding follows it into Italian.

### From upstream

- **The assistant's insets land on the right view.** `AssistantActivity` attached its
  `setOnApplyWindowInsetsListener` to `binding.root` and padded that; it now attaches to
  `binding.assistantNavContainer` instead. The listener reads the system-bar and IME insets
  together, so padding the activity root meant the status bar and the keyboard pushed the whole
  window rather than the navigation container inside it. This is the fork's first screen — the
  welcome pages, the permissions list and the third-party SIP login, all of which we restyle — so
  the fix lands directly on our own layouts (`5bc702e4`).
- **A complete Italian translation, and Lao as a new language.** Italian went from a stub to 942
  lines, and `values-lo` arrives whole at 448 lines. Czech gains three strings, Brazilian
  Portuguese seven, and Simplified Chinese ninety-nine — the newer chat and settings strings, plus
  sentence-final punctuation added to several dialogs (`9ed491cf0`).
- **Upstream's 6.2.5 bookkeeping.** `versionCode` moves to 602005 and `CHANGELOG.md` records the
  6.2.5 release: the Italian translation, the photo-capture-survives-rotation fix (already our
  `+016` base) and "delete for all" being hidden on chat messages that have not been sent yet
  (`1d91c40e7`).

### The Italian locale is de-branded

- **`values-it` said "Linphone" everywhere.** Its DOCTYPE still carried
  `<!ENTITY appName "Linphone">` — harmless while the file was a stub, but upstream's full
  translation made twenty-one Italian strings resolve to the upstream name at once. The entity now
  reads `白い熊 臨電話`, matching the other thirteen locales.
- **One line was the whole fix.** Every Italian string that names the app already routes through
  `&appName;`, including the three whose resource names still carry upstream's
  (`contacts_list_filter_popup_see_linphone_only`,
  `assistant_third_party_sip_account_create_linphone_account`,
  `settings_contacts_edit_native_contacts_in_linphone_title`). No hardcoded-name rewrites were
  needed, unlike `values-hu`, which had five.
- **The other locales were audited and need nothing.** `ca`, `eu`, `fi`, `lo`, `mk` and `pt` define
  no `appName` entity because they never name the app. The only `linphone` text left in any
  resource is `https://sip.linphone.org` in `manage_account_dialog_remove_account_message` — the
  real account-deletion service, kept deliberately, exactly as the base `values/strings.xml` keeps
  it.

## 6.3.0-alpha.2026-07-30.g5c0ed6a3+016

Same upstream base (`5c0ed6a3`). The settings pages take the sister apps' shape, and the
Conversations tab stops being shown to accounts that cannot use it.

### The Conversations tab is hidden by default

- **`disableChat` defaults to `true`** instead of upstream's `false`, and became writable so the
  choice can be stored. The tab is SIP instant messaging, never SMS: with a third-party account the
  app can only create a `Backend.Basic` chat room, whose messages are plain SIP `MESSAGE` requests
  that a commercial proxy typically refuses to relay. Group chat, encryption, delivery receipts and
  ephemeral messages all need a `conference_factory_uri` and a LIME server, which only Flexisip
  deployments expose — upstream's own third-party account profile ships both fields empty and sets
  `push_notification_allowed=0` alongside them.
- **A switch to bring it back**, "Show the Conversations tab", under Settings → User interface. That
  section rather than the Conversations one on purpose: the Conversations settings section hides
  itself along with the tab, so a switch placed there would vanish with what it controls and leave
  no way back.
- **The bottom navigation bar rebuilds at once.** Toggling fires the existing
  `forceUpdateAvailableNavigationItems` event, since each main fragment computes its nav bar once in
  a view model that survives the trip to Settings; without it the bar kept its old shape until the
  app restarted. The Conversations settings section follows the tab in the same toggle.

### Settings groups in yellow boxes

- **Every foldable group sits in a `#FFFF00` round-corner box** — three new drawables, a header
  (top corners rounded), a folded header (all four, since nothing shows below it) and a body
  (bottom corners). Yellow border over a near-black fill, never a yellow fill: the ink in this fork
  is yellow too.
- **The seam is one line, not two.** The body drawable sits in a layer-list inset `-2dp` at the top
  so its stroke is drawn over the header's rather than beside it; stacked plainly, the two 2 dp
  strokes read as a 4 dp double rule.
- Applied to **17 group bodies and 12 standalone panels** across Settings, Advanced, Advanced calls,
  Account settings and Account profile. Spinner dropdown backgrounds are deliberately untouched —
  they are popups, not groups.

### Fold state, and which way the chevron points

- **Down when unfolded, right when folded**, the platform's own tree convention and the one the
  sister apps use. Upstream drew up/down, which is ambiguous in both states: a down chevron reads as
  "this is open" just as easily as "tap to open". Applied to all 21 folds, including the three
  outside the settings pages (conversation info, contact detail, the third-party login assistant),
  so the gesture means one thing everywhere.
- **Fold state survives a relaunch**, stored per group under `ui/sk_settings_group_expanded_*`.
  Upstream keeps it in `MutableLiveData` scoped to the navigation graph, so a group folded shut
  reopened on the next cold start and folding was only ever a way to lose a group until the process
  died. Groups still default to open (`+002`'s behaviour); only a deliberate fold is remembered.
  One shared `toggleGroup` helper does it in all three settings view models — 13 groups in Settings,
  2 in account settings, 2 in the account profile.

### #FFFF00 as the main colour

- **`themeMainColor` defaults to a new `yellow` variant** rather than upstream's `orange`, backed by
  `Theme.LinphoneYellow` and `Theme.LinphoneInCallYellow`, registered in both `GenericActivity` and
  `CallActivity` and added to the colour picker's list. The fork palette was already `#FFFF00`
  throughout and `Theme.SkBlackYellow` is applied last, but it does not redefine every attribute
  upstream's colour variants touch, so orange still showed through in the gaps.

## 6.3.0-alpha.2026-07-30.g5c0ed6a3+014

Same upstream base (`5c0ed6a3`). The three main lists — call history, contacts, favourites — were
rebuilt to read the way the sister apps read: **shiroikuma-denwa** for the call log,
**shiroikuma-renrakusaki** for the address book. Formats and sizes were taken from those apps'
own settings and code rather than invented.

### The call history in Japanese

- **Day headlines.** Each calendar day is written once above its calls, underlined, with a band
  closing off the day before it. Today and yesterday are named instead of dated. Upstream drew
  nothing here — the whole history was one undivided run, with the date repeated on every row.
- **Imperial-era or Common-Era dates,** written in Japanese: `令和八年七月三十日（木曜日）` or
  `二〇二六年七月三十日（木曜日）`. Eras resolve from fixed boundaries rather than the platform's
  era table, so the output cannot shift underneath us; the first year of an era is written `元年`.
- **Sino-Japanese clock readings** — `午後三時三十六分`. A whole hour drops its minute part, `:30`
  becomes `半`, and noon and midnight get `正午` / `正子` (and `正午半` / `正子半`).
- **Kanji durations** in full-width parentheses — `（十一分一秒）`, `（三分半）` — shown only for a
  call that actually connected. A trailing 30s or 30m becomes `半` of the unit above it.
- **Direction arrows** coloured by kind: incoming blue, outgoing green, unanswered red. Red is
  reserved for a call that rang here and went unanswered; an outgoing call keeps its own colour
  whatever became of it, since the arrow's shape already says whether it connected.
- **Every format is settable** on the UI page, in one group: headlines on/off, date format
  (和暦 / 西暦 / system), name today and yesterday, time format (日本語 / system / 24h / 12h),
  duration on/off and its format, direction arrow on/off. Colours, fonts, weights and sizes for the
  headline, the name, the time and the duration; underline, band and record-separator thickness;
  row and line spacing; photo size. A live sample of a headline and a record redraws as you drag.

### Contacts

- **One-letter headings, foldable.** Gojūon rows (あ か さ た な は ま や ら わ — voiced,
  semi-voiced and small kana fold into their base row, ん lands in わ), then A–Z with diacritics
  stripped, then ＃; ordered kana → A–Z → ＃. Ported from renrakusaki's own `JapaneseSort`, so both
  apps bucket identically. Headings show their count and a fold indicator, are underlined, and are
  real tappable rows rather than decorations.
- **The fold state persists** across restarts and is kept **separately for each tab** — folding B on
  Contacts has no business folding B on Favorites. Letters start shut. While a search is typed every
  section is forced open, so a fold can never hide a match.
- **Rows rebuilt:** the photo spans both lines, the phone number is written under the name and
  grouped for reading (`+420 737 831 885`), and a full-width rule closes each row off. Photo size,
  row spacing, separator colour and thickness, and the number line's type are all settable.
- **A contact with no photo gets the house 人 mark** instead of an initials tile, as in both sister
  apps. Initials remain one switch away.

### Favorites

- **Their own tab,** between Contacts and Calls, with a star in the bottom bar. The favourites strip
  that used to sit above the contacts list is gone — it cost that list its top and only ever showed
  a handful.
- **A tile grid** — five across by default — of large round photos with the name centred beneath,
  wrapping to two lines.
- **Long-press and drag a tile anywhere on the grid,** and the arrangement is remembered. The SIP
  core records only that a friend is starred, never where it belongs, so the order is stored here as
  a list of contact ids; a newly starred contact appends at the end rather than displacing anything.
- Tiles per row, photo size, tile spacing and the tile name's type are settable.

### Two bugs that were hiding contacts

- **The address book was silently truncated at 300.** Upstream leaves the magic-search result cap on
  for the *unfiltered* list as well as for searches, so a longer address book simply lost its tail —
  with no warning, because the "limit reached" notice only appears while a filter is typed. The cap
  now applies to searching only.
- **Every Japanese contact fell under ＃.** Grouping ran on the kanji name, and kanji belong to no
  gojūon row. Phonetic readings (フリガナ) are now read from the address book in one pass and cached,
  which is what puts kana rows on the screen. A contact stored without a reading still buckets under
  ＃ — renrakusaki behaves the same way.

### An avatar that never loaded

`picturePath` is posted from a worker thread, but the layouts named only the model, so an expression
bound before the path arrived had no reason to re-run: on a cold start the account strip came up as
a row of 人 marks and righted itself the moment a tab was switched. The path is now a binding
dependency in the shared avatar includes and the account tab, so the real picture loads the moment
it is known. The bug predates this release; changing the fallback from an initials tile to the 人
mark is what made it visible.

### Typography, matched rather than guessed

The shipped type scale is now a real thing — each slot carries a default size, and a stored value
still wins — and the numbers came from the sister apps' own exports, with the reasoning recorded in
`SkTheme.kt` so a future reading of those exports does not "correct" them back:

- **Call log:** 22 sp for the caller name and 27 sp for the day headline, which denwa stores as slot
  overrides; 18 sp for the time and duration, which it derives rather than stores.
- **Contacts and conversations:** 24 sp. renrakusaki stores 18, but applies it down a code path that
  scales linearly by the system font scale where this fork's scales differently — the same declared
  18 renders about a third larger there. Calibrated against a control that both apps set the same
  way (the 21 sp letter heading renders 62 px there against 63 px here), the rows needed 24.
- Denwa stores **no weight overrides at all**: what reads as heavy there is the font itself
  (Animo-Regular for names, A-OTF 勘亭流 Std Ultra for the day headline), so nothing here fakes it
  with a weight.

### Everything unfolds

Upstream folds every group in the settings shut, so reaching one setting costs a tap first and the
page hides what else is in it. Settings (security, calls, conversations, contacts, meetings, network,
user interface, tunnel, audio devices, audio and video codecs, early media, auto answer), account
settings (advanced, NAT policy), the account profile's devices, the assistant's third-party SIP
advanced block and the contact detail's device trust all start open. That last one never had a
starting value at all — it unboxed from null to false and stayed shut.

In-call bottom sheets are deliberately untouched: they are drag-up panels, not settings folds, and
forcing them open would cover the call screen.

## 6.3.0-alpha.2026-07-30.g5c0ed6a3+002

Rebased onto upstream `5c0ed6a3` (upstream `versionCode` 602003 → 602004). The version name now
sorts, and the signing key left the repo.

### From upstream

- **Local network access on Android 17.** Android 17 puts LAN access behind a runtime permission, so
  an app can no longer silently reach a SIP server on your own network. Upstream now declares
  `ACCESS_LOCAL_NETWORK`, offers it as an optional row in the assistant's permission screen, and
  requests it automatically when an account fails to register — a local server being the likely
  cause. Irrelevant for public SIP providers; essential for a server on your own LAN.
- **Photos survive rotation in chat.** A picture taken from inside a conversation was lost if the
  device rotated before sending; the pending capture now lives in the send-message view model.
- **Keyboard prediction is back in unencrypted conversations.** The `NoPersonalizedLearning` IME flag
  is now applied only in end-to-end-encrypted chats instead of all of them.
- **No-audio workaround for Samsung S23 family.** Video calls register with Telecom as audio calls on
  S23 / S23+ / S23 Ultra / S23 FE, which otherwise lose all sound held to the ear. Gated on
  `Build.DEVICE`, so it never fires here.
- **Toolchain:** AGP 9.2.1 → 9.3.1, Kotlin 2.4.0 → 2.4.10, Gradle 9.6.0 → 9.6.1, Firebase BOM
  34.15.0 → 34.16.0.

### The new permission row is tappable, like the rest

- Upstream ships the local-network row as display-only — the four rows above it are individually
  tappable here only because this fork wired up the per-row listeners upstream declares but never
  binds. The new row now behaves the same: tapping it requests the permission, or opens the app's
  settings page once Android will no longer show a dialog for it.
- Below Android 17 the row hides itself (the compatibility layer reports the permission as granted),
  so on current devices this is future-proofing rather than a visible change.

### The version sorts chronologically

- `.g<sha>` alone made the name unsortable. A sha is random text, so `g5c0ed6a3` (2026-07-30, newer)
  sorts *before* `g6441c21e` (2026-07-13, older) — the newest APK landed in the middle of the
  phone's file-manager listing instead of at the end.
- The pin is now `.<YYYY-MM-DD>.g<8-char sha>`, e.g. `6.3.0-alpha.2026-07-30.g5c0ed6a3+002`. The date
  is the **base commit's own committer date**, not build time, so it keeps the property that every
  build on one upstream base carries an identical pin — it moves only on a sync.
- `BUILD_NUMBER` is zero-padded to three digits **in the name only** (`+002`), because the same
  defect applied there: under a plain lexicographic sort `+10` reads as earlier than `+3`. This caps
  the counter at 999, which the `× 1000` tail already required.
- `versionCode` is unchanged in form and value — still `<upstream code> × 1000 + BUILD_NUMBER`,
  `602004002` here. Neither the date nor the sha has any business in the number Android upgrades by.
- The `g` is kept. It is the `git describe` idiom, it marks the field as a commit id rather than
  another number, and about one sha in forty is all digits — `…2026-07-30.12345678+002` would
  otherwise read as a second numeric field.
- The APK filename is the versionName verbatim, so both change together; there is only ever one
  string.

### Signing moved out of the repo

- Credentials now live in `~/.gradle/gradle.properties` as `RINDENWA_RELEASE_STORE_FILE` /
  `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD`, and the repo carries none.
- The old repo-root `keystore.properties` was destroyed by this very sync: upstream tracks that file
  with empty values while `custom` deletes it, so `git checkout master` silently overwrote the real
  one (git clobbers ignored files without warning) and the switch back deleted it. It was restored
  from the recorded password and the signing identity verified unchanged, but the trap is now
  disarmed rather than documented.
- `keystore.properties` is still read when present, so upstream's own GitLab CI keeps working; our
  builds never depend on it.

## 6.3.0-alpha.g6441c21e+24

The version name now says which upstream commit the fork stands on. On upstream `6.3.0-alpha`.

### The version pins the upstream base

- Upstream's `versionName` is a release-time literal: `6.3.0-alpha` has stood still across hundreds
  of upstream commits, while `custom` is rebased onto every one of them. `6.3.0-alpha+23` therefore
  said nothing about whether upstream had moved on — the number after the `+` counts **our** builds,
  not upstream's.
- The name now carries the base: `<upstream>.g<8-char sha>+<BUILD_NUMBER>`, this build being
  `6.3.0-alpha.g6441c21e+24`. `6441c21e` is an upstream commit id, so the question "are we behind
  upstream?" is one comparison against `BelledonneCommunications/linphone-android` rather than a
  guess.
- The sha is `git merge-base HEAD master` — the upstream commit our patches sit on. **Not our own
  `HEAD`**, which identifies our commits and is what `+N` and the release tag already do, and **not
  `master`'s tip**, which overstates the base whenever `master` has been fast-forwarded but `custom`
  is not yet rebased onto it.
- It therefore moves **only on an upstream sync**. Two builds under the same sha are, by
  construction, built on the same upstream base — the steadiness is the signal, not a shortcoming.
- `versionCode` is untouched: still `<upstream code> × 1000 + BUILD_NUMBER`, `602003024` here. A sha
  carries no ordering and has no business in the number Android upgrades by.
- The APK filename and the release tag follow the version verbatim, so both grow the pin. The
  separator is `.g`, never `_` — the filename uses `_` as its own field separator, and an underscore
  inside the version would have split it into a field that the delivery and release tooling reads
  as an ABI.
- `-alpha` stays. It is upstream's own designation and it is true; dropping it would claim a stable
  6.3.0 that does not exist, and rewriting upstream's literal would reintroduce a rebase conflict on
  a line that currently flows through untouched.
- A missing sha — shallow clone, source tarball, no git at all — degrades the name back to
  `<upstream>+N` rather than failing the build.

## 6.3.0-alpha+23

The 保存復元 contract's fourth field, and an export that can be stopped from outside. On upstream
`6.3.0-alpha`.

### Categories now state their own default

- `LIST_CATEGORIES` answers `id⇥label⇥parent⇥on|off`. 自由作業盤's backup-item picker is drawn from
  this reply, freshly, each time it opens — so whether an item starts ticked is this app's decision
  to state rather than the picker's to assume.
- The field is positional and optional, so a top-level item, which has no parent, still carries the
  **empty third field**. Nothing already written breaks: an absent fourth field still means `on`.
- **Every category here is `on`.** The flag is for things that are large, derived *and*
  re-creatable — downloaded map tiles, a regenerable thumbnail cache — and this app exports none of
  those. Sending it anyway is the point: the app declares the default instead of leaving it to be
  guessed, and any category added later inherits a field that already exists.
- The in-app Export/Import sheet seeds its checkboxes from the same `Cat.default`, so the sheet and
  the automation picker start from one answer instead of two that can drift.

### The export can be cancelled

- `CANCEL_EXPORT` is a third action on the same exported receiver. Extras are `token` — the same
  gate as the others — and an optional `reply_id`; absent means whatever is running, which is
  unambiguous because two exports at once are forbidden.
- It **answers nothing at all**: not `OK:`, not an error. Fire-and-forget, handled before the
  broadcast is held open.
- **Safe to send at any time.** Nothing running, a `reply_id` naming a different run, or an export
  that already finished are all silent no-ops — not errors, not crashes.
- It is routed through the receiver rather than a service deliberately: an exported receiver is the
  only part of this app a third party can reach, since the services are — correctly —
  `android:exported="false"`. A stop button the batch cannot reach is not a stop button.
- The stop reuses the Export/Import panel's own Cancel path — interrupt the worker, which
  `SkEximport.checkCancelled` picks up at the next entry boundary — so there is one way to unwind
  rather than two. The `java.io` streams underneath are not interruptible, so a `write()` in flight
  completes rather than tearing; the flag is simply seen at the next check.
- Unwinding runs the same `catch` as any other failure, which discards the half-written file. **A
  cancelled export leaves the backup directory exactly as it found it** — no short archive, no
  stray partial. That is the whole point of the action.
- The terminal `ERROR:cancelled` goes out through the normal reply channel under the existing
  `AtomicBoolean`, so it cannot double-fire with a success, and it is sent even though nobody may
  still be listening — it is what proves the run ended rather than carrying on unseen.
- The run in flight is tracked in companion-object state, because a `BroadcastReceiver` instance
  dies with its broadcast and the cancel arrives at a different object entirely. The result is
  decided by the cancelled flag rather than by matching an exception type, so whatever the interrupt
  surfaces as, a run we asked to stop reports as cancelled.

## 6.3.0-alpha+22

One fix, found while chasing incoming calls that rang without ever waking the screen. On upstream
`6.3.0-alpha`.

### The caller's photo no longer holds up the call

- Building a call notification asks the contacts provider for the caller's photo, and upstream does
  that read inline on the Core thread. On a sleeping EMUI phone the provider may simply not answer:
  `openAssetFileDescriptor` and the `ImageDecoder` read behind `Friend.getAvatarBitmap` block
  outright — and because a block throws nothing, nothing is caught and nothing is logged.
- A stalled read stalls the notification, and that notification is the *only* thing that raises the
  incoming call screen: upstream calls `showCallActivity()` for outgoing and connected calls, never
  for an incoming one, leaving that entirely to the notification's full screen intent. So the Core
  rings on into a dark phone with nothing on it.
- Caught in the act on 2026-07-30: the Core thread entered the avatar path at 19:40:09.812 and said
  nothing further until 19:40:17.452, the instant the call ended — 7.6 s — while 492 log lines from
  the app's other threads went past. The process was alive; that one thread alone was stuck.
- Now only the name and the photo path are read on the Core thread, both cheap, and only those: a
  `Friend` is a native object and must not be touched from another thread. The decode runs on a
  throwaway daemon thread with a 400 ms deadline, and a miss falls back to the initials avatar the
  fork already generates for callers with no picture. The thread is deliberately not pooled — a read
  that never returns must not wedge the next call behind it. A responsive provider answers in
  milliseconds, so the photo still shows in the ordinary case.

### Not a fork bug: dead incoming calls on a Huawei phone

Recorded here because it cost an evening and no amount of app code can detect it. Huawei's
PowerGenie had set this app's `START_FOREGROUND` app-op to `ignore`, which makes
`Service.startForeground()` **return success while the system silently discards the notification**.
No exception, no log, nothing for the app to see — and with the notification gone, so is the full
screen intent, so the phone rings with a dark screen. Check it before suspecting the fork:

```
adb shell cmd appops get shiroikuma.rindenwa | grep START_FOREGROUND
```

A healthy app shows `allow`. The fix is on the device: Settings → Battery → App launch → 白い熊
臨電話 → turn off "Manage automatically", then enable Auto-launch, Secondary launch and Run in
background. Related: the app's own logcat lines are invisible on EMUI until
`adb shell setprop log.tag.Rindenwa VERBOSE`, which is why the fork's logger domain is ASCII.

Also looked at and left alone: the missed-call notification sits near the bottom of the EMUI shade.
Android already ranks it first of every notification on the device, above the app's own
`IMPORTANCE_LOW` service notification, which EMUI shows at the top — the shade is simply not
rendering Android's ranking, and no channel importance, category or priority the app can set will
change that.

## 6.3.0-alpha+17

Fixes that came out of tracking down missed incoming calls, plus a call log that says which number
rang. On upstream `6.3.0-alpha`.

### The call history rows

- Each record gained a middle line: the number the call actually used, and the address-book field
  it is filed under ("Home", "Work mobile", …), resolved through the same
  `PhoneNumberUtils.vcardParamStringToAddressBookLabel` the contact detail screen uses. With
  several numbers saved for one contact, the name alone never said which one rang.
- The call's own number is what gets displayed, not the contact's stored one: the provider sends
  E.164, while an address-book entry may be written any which way. The stored entry is matched only
  to fetch the label, comparing the trailing nine digits so `+420601524009`, `0601524009` and
  `601524009` all resolve to the same line.
- Numbers are grouped for reading — `+420-601-524-009` for Czechia, `+1-808-500-5515` for North
  America. A country code that is not in the table, or a digit count that does not match it, is
  left exactly as it arrived: an internal extension or a SIP username is never mangled into
  something that looks like a phone number.
- The avatar rises to 56 dp to span the three lines, through a new `avatarSizeOverride` binding
  adapter rather than by raising `avatar_list_cell_size` — that dimension is shared by every list
  including `contact_avatar`, and the contacts and conversations rows are still two lines.
- The three lines are a packed vertical chain now. They were spread across the row's full height
  before, which is where the loose gaps came from, and no amount of padding would have closed them.
- New live controls under 白い熊 臨電話 UI → Calls → Call history rows: **Call number** (family,
  weight, size and colour, its own `LIST_NUMBER` slot, default 14 sp), **Call row spacing**
  (0–24 dp, default 2) and **Call line spacing** (0–16 dp, default 0). Applied per bind, because
  RecyclerView creates these rows long after the activity styling pass and recycles them freely.

### Registration error notifications

- An account must now stay `Failed` for 45 s before anything is shown. Upstream notifies on the
  first `Failed`, which is reasonable when push notifications carry the load — this build has no
  `google-services.json`, so push is never available, both of upstream's suppression guards are
  structurally dead, and every momentary re-REGISTER hiccup left one notification per account.
- The notification is dismissable (`setOngoing(false)`). Upstream pins it open, so one that had
  already been overtaken by events could only be cleared by opening the app.
- Stale notifications from a previous process are cleared at core start. Their bookkeeping map dies
  with the process, so an orphaned one could previously never be cancelled at all.

### Debuggable on an EMUI phone

- The SDK's logger domain becomes the Android logcat tag, and upstream sets it from the app name —
  for this fork, `白い熊 臨電話`. Android property names accept only `[a-zA-Z0-9_.-]`, so
  `log.tag.白い熊 臨電話` is rejected outright, and on a ROM that filters logcat below error level
  the fork had no reachable logs at all. The domain is the constant `Rindenwa` now, so
  `adb shell setprop log.tag.Rindenwa VERBOSE` works.

## 6.3.0-alpha+15

Navigation and the account switcher redrawn as a card folder, on upstream `6.3.0-alpha`.

### Card-folder tabs

- `SkFolderTabPainter` draws a tab strip: one continuous bold, opaque line across the full width
  that, at the tab in the foreground, curls outward (the browser-tab flare), runs along both sides
  and around the two far corners — open on the baseline side, because that tab is the panel behind
  it. Every other tab is the same box drawn at 1.5 dp and 30 % alpha behind that line; tabs are all
  the same size, what tells them apart is which one is in front.
- One painter serves both bars: the bottom navigation hangs its tabs down from the panel above, the
  account strip stands its tabs up from the panel below.
- Selection changes are interpolated over 260 ms — the bold line dives around the incoming tab while
  the outgoing one closes up — so a tab switch reads as continuous.
- The bottom bar's Contacts / Calls / Conversations labels now sit inside their own tabs
  (`SkFolderTabsView`), which take their geometry from the real labels at draw time, so a hidden
  entry (Conversations and Meetings can both be switched off) leaves no gap.

### Account tabs

- New strip across the top of the main screens (`SkAccountTabsView`): one tab per account. The
  account in use carries its picture at 2× upstream's 45 dp plus its display name; the others the
  same picture without the name. Tapping a tab sets that account as default — the drawer menu's
  action, without opening the drawer.
- Horizontally scrollable when there are more accounts than fit, and it scrolls the active tab into
  view on its own.
- The tab name that used to be the top bar's title ("Contacts", "Calls", "Conversations") is gone —
  the bottom folder says which screen you are on. What is left inside the pane is the controls row:
  drawer menu on the left, search and the screen's own action on the right.

### Drag to reorder, both lists

- `SkAccountOrder` stores 白い熊's own account order, persisted as identity addresses, and is read
  by the tab strip and by the drawer menu's account list alike. Accounts the Core hands over that
  have never been placed keep their relative order and land at the end.
- `SkDragReorder` implements long-press → haptic buzz → drag: the child lifts and follows the
  finger while the others slide out of its way; on release the new order is stored. Sideways in the
  tab strip, up and down in the drawer.
- Whichever list is dragged, the other rebuilds from the same stored order.

### Full-width bars

- Both strips are hoisted out of the `SlidingPaneLayout`, so each main screen is a vertical stack of
  account tabs · sliding pane · navigation tabs. Their lines run the full width of the window,
  across the detail pane as well, instead of stopping at the list pane's edge.

### Fixes & behaviour

- Dialogs are framed on all four sides: upstream's `shape_dialog_background` stacks two rounded
  rectangles offset by 2 dp, which paints an accent line along the bottom edge only — on a black
  panel that reads as a stray underline. It is one shape with a 2 dp yellow stroke now, so every
  dialog in the app is a black panel with a yellow border.
- The empty half of the dual-pane view uses the app's black instead of upstream's lifted grey
  surface, which read as a smudge next to a black list.
- Landscape keeps the single-row bar with the account's display name as its title; the tab strip is
  portrait-only.

## 6.3.0-alpha+9

First public release of the fork, on upstream `6.3.0-alpha` (upstream versionCode 602003).

### Identity & packaging

- Installed app id `shiroikuma.rindenwa`, label 白い熊 臨電話 — side-by-side with stock Linphone.
  The code namespace stays `org.linphone`, so upstream rebases don't become mass-conflicts.
- The app id also drives the FileProvider authority, the AppAuth redirect scheme and the OpenID
  callback scheme, so none of them collide with an upstream install.
- Single-ABI **arm64-v8a** build (upstream also ships armeabi-v7a), roughly half the size.
- APK named `shiroikuma-rindenwa_<version>_arm64-v8a.apk`; backups follow the family convention
  `shiroikuma-rindenwa_<yyyy-MM-dd_HH-mm-ss>.zip`.
- Own signing key, configured **outside the repo** in `~/.gradle/gradle.properties` as
  `RINDENWA_RELEASE_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD`. Upstream ships
  `keystore.properties` tracked-and-empty and loads it unconditionally, while our `custom` branch
  deletes it — the combination silently destroyed our signing password during the 2026-08-01
  upstream sync, since `git checkout master` overwrites a gitignored file without warning and the
  switch back deletes it. Gradle properties leave nothing in the working tree to clobber. The build
  still reads `keystore.properties` when present, so upstream's GitLab CI keeps working, but never
  depends on it; with nothing configured the release APK is simply unsigned.
- Upstream's Firebase config is removed: our package is not in their project, and linphone.org push
  only targets theirs, so the fork builds without FCM and Crashlytics — the same shape as upstream's
  F-Droid build. The keep-alive foreground service covers registration instead.
- `androidx.documentfile` added for SAF handling; view binding enabled alongside data binding.

### Versioning

- Fork versions are `<upstream versionName>+<BUILD_NUMBER>`, with
  `versionCode = <upstream versionCode> × 1000 + BUILD_NUMBER` → `602003009` for this build.
- Upstream's two version literals are left byte-identical and read back by the fork block, so an
  upstream bump flows through with no hand-editing and no conflict on those lines.
- The multiplier is ×1000, not the sister forks' ×10000: Linphone's upstream code is already
  ~602003, and ×10000 would overflow Android's 2,100,000,000 versionCode ceiling.
- `buildApk` builds, signs, copies to `~/tmp/` and bumps the counter.

### Icon & branding

- The Linphone mark re-traced as yellow `#FFFF00` line-art on black — adaptive icon (black
  background + traced foreground), legacy square and round launchers at all five densities, splash
  vector, notification icon, welcome logo, splash wordmark, and the store icon and feature graphic.
- Splash background switched from white to black in the day themes; the night themes were already
  dark and were left alone.
- App name is 白い熊 臨電話 in the default locale and all twelve translated locales that carry it,
  through each file's `&appName;` DOCTYPE entity. Hungarian had it hardcoded in five strings; those
  now use the entity with their grammatical suffixes intact.
- Help → About gains a **Source code** row linking to this repository; the contribute-translations
  row points here too, and the invite-a-contact SMS links to this repo's releases.
- The in-app update check is disabled (`version_check_url_root` emptied) — it would otherwise offer
  upstream Linphone releases. This also hides the "Check update" button.
- Store metadata rebranded: title, description, 512 px icon, new feature graphic.
- Left deliberately functional: the `sip.linphone.org` service, the `subscribe.linphone.org` account
  platform, file-transfer and log-upload endpoints, upstream's user-guide/privacy/terms/licence
  URLs, the Belledonne copyright notices, and internal identifiers.

### Black-yellow theming

- `Theme.SkBlackYellow` overrides all 61 of upstream's colour attributes and is applied last in
  `GenericActivity.getTheme()`, so it wins over every colour variant and re-skins the whole app
  natively at inflation time — no view walking, no flicker, identical in light and dark mode.
- The surface→ink ramps are mapped by the role each level actually plays (`color_main2_400` and
  above are ink, below are surfaces; likewise `color_grey_*`).
- Top bars are black with accent content: the bar drawables and the five list-fragment headers paint
  from a fork colour, while `color_main1_500` stays yellow for tints. `color_on_main` stays black —
  correct for content drawn on yellow fills — and the bar-specific uses take the accent instead,
  including the top-bar icon selector.
- Field pills carry a yellow border: normal 1.5 dp, focused 3 dp, disabled dimmed, plus the
  conversation compose field, the SMS-code boxes and the top-bar search box. Validation errors stay
  red.
- The account registration-status pill is black with a yellow border and yellow text for every
  state, replacing upstream's per-state tinted fill whose label disappeared into it.
- The selected account row is a faint yellow wash rather than a pale band.
- Semantic colours keep their meaning; only their backgrounds go dark.
- `SkStyler` applies at runtime only for slots explicitly overridden in the UI page, so it can never
  fight the theme.

### 白い熊 臨電話 UI page

- Reachable from the first row of Settings (above Security), a long-press on the drawer's Settings
  cog, or a long-press on the top-bar hamburger (bound once for all four tabs).
- kxkb page format: 1 px section hairlines, text-wide underlined headings (20 sp/2.5 dp sections,
  17 sp/1.5 dp sub-groups), a 36/54/72/90 dp indent ladder, and tight rows with padding only between
  top-level groups.
- RGBA colour picker with live preview and one-click boxes of recently chosen colours.
- External fonts imported via SAF, each option **rendered in its own glyphs**; per-slot family,
  numeric weight and size.
- Border and roundness sliders that reach 0, with live previews for list rows, chat bubbles and
  buttons.
- Slots cover foundation, top bar, main screen (rows, avatar, action button), calls, conversations
  and controls, with defaults inherited from the foundation slots.

### Export / Import

- Kōjiki-style single ZIP with a manifest plus one entry per category: **accounts and their full
  configuration**, history (call history and conversations as sub-options), SIP and media settings,
  appearance (with imported fonts as a sub-option), and app settings.
- Accounts and settings are split out of `.linphonerc` by section, so accounts restore independently
  of settings; the SDK databases are carried whole.
- Settable SAF export directory, queried when the page opens for the latest backup — shown in red
  until a directory is set, yellow afterwards.
- Arcanechat button bar: Cancel alone on the left, Import and Export on the right, all round pills.
- Live progress with real counts and a working Cancel that deletes the partial file.
- Success shows a black-yellow, yellow-bordered dialog; acknowledging it closes the info dialog, the
  panel and the UI page. On import, "Restart now" restarts the app. Failures stay open as toasts.
- Import merges per key and skips absent categories; it never clears.

### 保存復元 automation contract

- `SkStateExportReceiver` implements both token-gated actions. `LIST_CATEGORIES` emits `id<TAB>label`
  with a third `parent-id` field for sub-options; `EXPORT_STATE` runs the same export headlessly.
- Directory precedence: `path` extra → configured directory → `ERROR:no-directory`.
- Replies as a fresh broadcast with `FLAG_INCLUDE_STOPPED_PACKAGES`, exactly one terminal result
  guarded by an `AtomicBoolean`, distinct `automation disabled` and `bad token` errors.
- Progress broadcasts carry real counts, throttled to one per 500 ms plus a final one.
- Master switch (off by default) and a tap-to-copy token row with Regenerate, both inside the
  Export/Import section. The token lives in its own preference file and never enters an export.
- `MANAGE_EXTERNAL_STORAGE` declared so an automation export can write to a named absolute path.

### Setup escape hatch & permissions

- The assistant blocked a cleared install from reaching the main screen, which made restoring a
  backup impossible — the accounts are *in* the backup. The landing and permissions screens now
  carry a skip that leaves the assistant entirely; the flag clears itself once an account exists and
  is wiped by the data-clear it exists to survive.
- Permission rows are clickable at last: upstream declares four per-row listeners and binds none of
  them, leaving the list inert. Each row now requests its permission, or opens the app's settings
  page when Android will no longer show a dialog — the case where "grant all" silently does nothing.

### Tooling

- Skills: `build-apk`, `upstream-new-version` (proceed-gated table of new upstream features before
  any rebase), `publish-version`, plus `CLAUDE.md` documenting the whole fork layer.
