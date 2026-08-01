# Changelog — 白い熊 臨電話

Fork-only changes. Upstream Linphone's own changelog stays in `CHANGELOG.md`.

## 6.3.0-alpha.2026-07-30.g5c0ed6a3+002 — current

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
