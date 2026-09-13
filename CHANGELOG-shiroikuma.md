# Changelog — 白い熊 臨電話

Fork-only changes. Upstream Linphone's own changelog stays in `CHANGELOG.md`.

## 6.3.0-alpha+2026-09-11.12-50.g75c4ce26+002 — current

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
