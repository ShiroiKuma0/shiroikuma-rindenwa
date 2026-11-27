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
  explicit go-ahead**; then fast-forward `master`, rebase `custom`, reset `BUILD_NUMBER` only if upstream's
  `versionCode` moved, build the next `+N`.
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
  `BUILD_NUMBER` (`gradle.properties`) is our increment — bumped every build, and reset to 1
  **only when upstream's own `versionCode` moves** (see the note on the counter below). Fork
  `versionName = "<upstream>+<upstream base date>.<HH-MM>.g<upstream base sha>+<BUILD_NUMBER padded to 3>"`,
  `versionCode = <upstream versionCode> * 1000 + BUILD_NUMBER` (602003 → `602003001`).
  **The multiplier is 1000, not the sister forks' 10000** — Linphone's upstream code is already
  ~602003, and ×10000 would overflow Android's 2,100,000,000 versionCode ceiling. Never "restore"
  ×10000 while resolving a rebase conflict.
- **Upstream tracking: `git`** — `custom` is rebased onto every upstream commit, and upstream's
  `6.3.0-alpha` literal stands still for months, so the versionName pins the **upstream base**:
  `git merge-base HEAD master`, as `+<YYYY-MM-DD>.<HH-MM>.g<8-char sha>` before the `+N`
  (e.g. `6.3.0-alpha+2026-08-07.11-31.g5bc702e4+002`). The `HH-MM` and the `+` grouping arrived
  2026-08-12, together with a fix to render the timestamp in **UTC** rather than the commit's own
  timezone. It changes only on an upstream sync, so two builds
  under the same pin are built on the same upstream base. See the global **`git-versioning`** skill.
  Never use our own HEAD sha, and never `master`'s tip. The pin never touches `versionCode`.
- **Both halves of the pin exist to sort.** The date is the base commit's own committer date (not
  build time), because a bare sha is random text: `g5c0ed6a3` (newer) sorts *before* `g6441c21e`
  (older), which buried the newest APK mid-list in the phone's file manager. `BUILD_NUMBER` is
  zero-padded to 3 digits in the **name only** for the same reason (`+002` before `+010`; unpadded,
  `+10` reads as earlier than `+3`). `versionCode` keeps the plain integer. **This caps
  `BUILD_NUMBER` at 999** — beyond that the ×1000 tail would collide with the next upstream
  version's range.
- **The counter runs on across a sync that leaves upstream's `versionCode` alone.** Upstream sits
  on one `versionCode` for months while `custom` is rebased onto commit after commit. Resetting
  `BUILD_NUMBER` on such a sync would give the new build a **lower** `versionCode` than the one
  installed (`602010001` under an installed `602010035`), and Android refuses to install a
  downgrade. So: upstream `versionCode` unchanged → keep counting (`+035` → `+036`, 2026-11-19, the
  `92318a24 → 2199af83` sync); upstream `versionCode` raised → reset to 1, since the ×1000 head has
  moved up and every new code is above every old one. The pin in the name changes either way.
- **APK filename:** `shiroikuma-rindenwa_<versionName>_arm64-v8a.apk`, single-ABI arm64-v8a build
  (upstream ships armeabi-v7a as well; we drop it).
- **Signing:** credentials live **outside the repo**, in `~/.gradle/gradle.properties`:
  ```properties
  RINDENWA_RELEASE_STORE_FILE=/home/shiroikuma/.android-keystores/shiroikuma-rindenwa.jks
  RINDENWA_RELEASE_STORE_PASSWORD=<password>
  RINDENWA_RELEASE_KEY_ALIAS=rindenwa
  RINDENWA_RELEASE_KEY_PASSWORD=<password>
  ```
  Keystore `~/.android-keystores/shiroikuma-rindenwa.jks` (alias `rindenwa`); password recorded in
  `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org` (jks backup in `android-keystores/` next
  to it). Losing both loses the signing identity. **Never put them back in a repo-root
  `keystore.properties`:** upstream tracks that file with empty values and our `custom` branch
  deletes it, so `git checkout master` during a sync overwrites the real one and the switch back
  deletes it — the password is gone, unrecoverable (it is gitignored). This cost us the key on
  2026-08-01. The build still *reads* `keystore.properties` if present (upstream's GitLab CI writes
  one from CI secrets) but never depends on it; with nothing configured the release APK is simply
  unsigned and won't install.
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
| Signing | `RINDENWA_RELEASE_*` in `~/.gradle/gradle.properties` → `~/.android-keystores/shiroikuma-rindenwa.jks` | `app/build.gradle.kts` |
| Single ABI | `abiFilters += listOf("arm64-v8a")` | `app/build.gradle.kts` |

Because `applicationId` (`shiroikuma.rindenwa`) differs from `namespace` (`org.linphone`), the
`packageName` val also drives the **FileProvider authority**, the **AppAuth redirect scheme** and the
**OpenID callback scheme** — that is deliberate, and it is what makes the fork installable alongside
stock Linphone. Watch for the sister-fork trap: leading-dot class names in *runtime* layout attrs
(`android:name=".Foo"`, `app:layoutManager=".Bar"`) resolve via the applicationId, not the namespace,
so they would `ClassNotFound` here — fully-qualify them to `org.linphone.…` if lint flags them.

## Call stamps — a call keeps what it was made through

The SDK's call log keeps who, when, how long and whether a call connected; the 臨N badge used to be
worked out on every read from today's settings, so renumbering or repainting an account rewrote the
calls already made. Since 2026-11-19 (+031) each call has a **stamp** (`shiroikuma/SkCallStamps.kt`:
the `SkCallStamp` model, the store and the recorder) and a row whose stamp carries a badge is drawn
from it alone. Asked for by 白い熊 through the Denwa chat, after 電話's SIM stamps.

- **Key: the SIP Call-ID**, which the SDK's own log carries and which therefore survives an export
  and import of the history database; a record without one is keyed `<startDate>|<remote URI>`.
- **Store:** the preferences file `sk_call_stamps`, one JSON string per call (keys starting with `_`
  are bookkeeping, never exported). It travels with the call history in the export/import as
  `call_stamps.json` (`SkEximport`, categories `history` / `history.calls`).
- **What it holds** (`SkCallStamp.toJson()`; an absent key means “not known” — **renaming or
  re-meaning a key breaks old exports**): `src`, the badge `n` / `color` / `text`, `id` (the account's
  identity), `srv`, `tr`, `end`, `sip`, `phrase`, `ring`, `codec`, `rate`, `enc`, `net`, `net_name`,
  `vpn`, `route`, `q`, `ua`.
- **Where it comes from:** `SkCallStamps.onCallStateChanged`, driven from `CoreContext`'s call-state
  listener (source `call`): account, badge and network at the first state, codec / encryption /
  audio route whenever the media runs, the ending at `End` / `Error`. “Who hung up” is known only
  because `CoreContext.terminateCall` reports every local hang-up (`onTerminateAsked`) — the SDK does
  not say. A call nobody watched is stamped from its log record with the badge of that moment
  (source `log`; once for the whole log on the first history read). A `call` stamp is never replaced
  by a weaker one. **A stamp without a badge number (the account had none) keeps following today's
  badge**, as 電話 does for a stamp without a digit.
- **The dialog** (`shiroikuma/SkCallStampDialog.kt`) opens from a tap on a row's badge, in 電話's
  look: the badge large, the account's user part and domain, a rule, then one line per known fact.
  A fact that is not known is left out, never shown empty.
- **Never put a real phone number in a comment, a string or an example** — this repository is
  public. Use made-up ones (`+420-601-123-456`).

## An incoming call's line is read from the INVITE — never from the SDK's record

白い熊, 2026-11-26 (+063 to +065): every missed call was filed under whichever account's tab stood
open. The SDK looks the `To` line up among the accounts by the whole address; Odorik sends
`To: <sip:123456@sip.odorik.cz:6688>`, the port fits no identity, and the SDK then **writes the
selected account into the call record** as its local side. Confirmed in the phone's database.

- **`SkCallStamps.accountOf(call)`** is the one place an incoming call's account is worked out: the
  `To` header (`call.getToHeader("To")`), then the request line, each by the whole address and then
  by the user part; the record's local side only when neither names an account. Never use
  `call.callLog.localAddress`, `callLog.toAddress` or `LinphoneUtils.getAccountForAddress` on it for
  an incoming call again — use `SkCallStamps.identityOf` / `localAddressOf` (for a live call or for
  a record).
- **The stamp is written the moment a call is first seen**, not only when it ends: the SDK
  announces the record while the call still rings, and a list made then must find the line.
  `SkCallStamps.addListener` tells the history to make its list again whenever a stamp is written.
- **The history never uses the SDK's per-account lists** (`account.callLogs`,
  `getCallLogsForAddress`): they go by the record's local side. An account's tab is
  `core.callLogs` filtered by the stamp's identity. Switching to 全 calls `filter()`, never
  `applyFilter()` — that one does nothing while the search text is unchanged.
- **Missed calls are counted by us** (`shiroikuma/SkMissedCalls.kt`, preferences file
  `sk_missed_calls`, not exported): noted when a call ends missed, forgotten when that account's
  history is opened (全 forgets all). `account.missedCallsCount` is not read anywhere.
- **The badges** (白い熊, 2026-11-26): no red badge on the bottom 臨電話 tab. Each account tab
  carries its count in accent on black over the bottom-right corner of its picture
  (`sk_count_badge`, roles `tab_count_ground` / `tab_count_text`); the 全 tab carries the total.
- **The repair** (`SkCallStamps.repairIncomingAccounts`, once, flag `_incoming_accounts_repaired`)
  moved old incoming stamps by the tail Odorik puts after the caller's name (`601123456›009`) —
  only where exactly one account's name holds a number ending in it. That tail is a label of the
  line at Odorik, and two of 白い熊's lines carry the same one: those rows could not be told
  apart and stay where they were.
- The “calls” diagnostic writes one line per incoming call: `incoming local side: to=… request=…
  record=… account=…`.

## Call screens — the picture card and the name card

白い熊, 2026-11-26 (+065), chosen from five proposals: incoming, outgoing and ongoing calls show
who the call is with in 連絡先's two cards.

- **`sk_call_party.xml`** (stacked) and its twin in **`layout-w600dp`** (side by side: two panels
  and wider) hold the picture card and include **`sk_call_party_text.xml`**, the name card. The
  six fragment layouts (portrait and `layout-land`, three screens) only include the block.
- **The picture is shown whole** — `fitCenter`, never round, never cropped, whatever its shape
  (`skWholePicture` in `SkCallPicture.kt`). Only a caller without one gets the round stand-in.
- **The name card:** the name largest; the number in the house reading (`skPartyNumber`, never a
  `sip:` address); then, small, the line — its 臨N badge and the account's name; the status line
  where the screen has no other place for it. The caller is the subject of the screen, the line
  is not: never make the line the largest thing again.
- **Incoming:** Decline and Answer are worded pills side by side (`sk_call_decline_pill` thin
  red, `sk_call_answer_pill` thick), the heading is the plain “Incoming call”, and the small
  actions shrink their labels rather than cut them.

## Silencing a ringing call — and why the volume button comes round through 自由作業盤

`shiroikuma/SkRinger.kt` silences ringtone and vibration without touching the call: the Silence
button, the notification's button, **turning the phone face down** (accelerometer; the screen must
first be seen facing another way, so a phone already lying on its screen rings), and **a volume
button**.

The volume button cannot be caught in the app on 白い熊's phone, for two reasons found on
2026-11-19 — do not “simplify” this back to `onKeyDown` alone:

1. 自由作業盤's 物理鍵 key grabber takes the volume keys below the framework.
2. Even when a tap is handed back, **Android's telecom layer swallows it**: our calls are registered
   with Telecom (self-managed, androidx core-telecom), so a volume key during a ring means “silence
   the ringer” to the system, which stops a ringer it does not have. core-telecom does not pass
   `Connection.onSilence` on. `dumpsys telecom` shows `SILENCE` at the second of the press.

So the two apps talk directly:

- While a call rings unsilenced, 臨電話 sends an explicit broadcast to `shiroikuma.jiyusagyoban`:
  action `shiroikuma.rindenwa.RINGING`, boolean extra `ringing` (`SkRinger.announceRinging`).
- 自由作業盤 (0.2.96+…+025 on) then answers a short volume tap by calling the call door:
  `ContentResolver.call(content://shiroikuma.rindenwa.calls, "silence_ringer")` → `OK`,
  `OK:nothing ringing` or `ERROR:not allowed`. That one method admits the dial allowlist **and** the
  data door's certificate-pinned callers (`AutomationCallers`), so 自由作業盤 needs no switch.
- `CallActivity.onKeyDown` still takes the key for a phone where nothing stands in front of it.

**Never bring back the `VOLUME_CHANGED_ACTION` broadcast as a stand-in for the key** (+030 to +032):
a volume moves when the ringer starts, and every call went silent a split second into its ring.

## A call during a call in another app is announced quietly

白い熊, 2026-11-27 (+066): a call rang loudly into the ear in the middle of a WhatsApp call — with
the audio in communication mode the ringtone comes out of the earpiece.

- **`SkRinger.isQuiet(call)`** decides it once per call, the first time the ringer or the
  notification asks: the device's audio mode says a call is up (`MODE_IN_COMMUNICATION`,
  `MODE_IN_CALL`, `MODE_CALL_SCREENING`) and that call is not ours (`core.callsNb <= 1`). Telecom
  is not asked — it would count this very call. A second call of our own is left to the SDK's
  waiting tone.
- **Such a call:** is silenced from its first state (the stop is repeated at 60 / 250 / 600 ms —
  the SDK may start its ringer just after the listeners ran); vibrates **once** (two short
  pulses at 700 ms, behind the last stop, because the SDK's vibrator is the same one; not with
  the vibration setting off or the phone on silent); does **not** raise the call screen; and its
  notification has **no full-screen intent** and goes to the quiet call channel.
- **`shiroikuma/SkIncomingPanel.kt`** announces it instead: a card at the top of the display over
  whatever app is in front (the app's one overlay window, on the “Display over other apps”
  grant) — who, the number, the line's badge and name, and the pills Hide / Decline / Answer; a
  touch on the card opens the call screen. It goes when the call leaves its incoming state or
  the incoming screen is shown. Without the grant the notification heads up instead.
- **Messages and missed calls** are posted `setSilent` while `SkRinger.inCallAudio` — any call,
  ours included.
- The “calls” diagnostic says `announced quietly: a call in another app is up (audio mode N)`.
  A call that did not ring for no reason: look for that line — some app left the mode set.

## Blocked numbers — our own register, and a door before the ringer

`shiroikuma/SkBlocked.kt`, asked for by 白い熊 on 2026-11-19 after 電話's block. 電話 uses Android's
`BlockedNumberContract`, which only the default dialer may touch; 臨電話 is not that, so the register
is ours.

- **Store:** the preferences file `sk_blocked_numbers`, one JSON string per caller (`raw`, `name`,
  `at`). The key is `tel:<international digits>` for a number — every spelling of one number is one
  entry, via `SkNumbers.international` — or `sip:<user>@<domain>` for a caller who is not a number.
  A withheld number has no key and cannot be blocked. **Renaming or re-meaning a key breaks old
  exports**: the file travels as `blocked_numbers.json`, export category `blocked` (merged on
  import, never cleared).
- **Three ways in, one way out in bulk:** the Block button on the incoming-call panel (the touch
  silences the ring, then asks; red pill Block, plain pill Cancel — a caller who gives up while the
  question stands is blocked all the same), “Block number” / “Unblock number” in the history's
  long-press menu, and the “Blocked numbers” row in the Calls section of the 臨電話 UI page
  (`SkBlockedNumbersDialog`: unblock, or add by hand).
- **The door:** `SkBlocked.rejectIfBlocked` runs first in `CoreContext`'s call-state listener. A
  blocked call is marked (`isRejecting`), and the mark is what keeps it unannounced — `SkRinger` is
  skipped, and `NotificationsManager` and `TelecomManager` each carry a one-line guard. The decline
  is **posted**, not made inside the callback: declining there would hand the listeners behind ours
  an `End` before the `IncomingReceived` it belongs to. It is judged **before** `SkOdorikCallback`
  repoints a callback's first leg, so a callback *to* a blocked number still rings.
- The call stays in the history; its stamp ends `blocked` (“Blocked” in the stamp dialog).
- **The history marks a blocked caller:** a filled `#FF0000` pill with white “Blocked” after the
  number on the row's middle line (白い熊, 2026-11-20; `CallLogModel.skBlocked`, `sk_blocked` in
  `history_list_cell.xml`). It follows today's register, not the call — every row of that caller
  carries it, and unblocking takes it off them all. It is a mark, not a button, which is why it is
  the one filled pill. The list redraws through `SkBlocked.addListener`.

## Unknown callers are looked up online — and marked ID

`shiroikuma/SkOnlineId.kt`, asked for by 白い熊 on 2026-11-20 (+044). A caller in no address book
is only a number; for a Czech one, `https://www.tellows.cz/num/<nine national digits>` often says
who it is.

- **When:** only as a call **rings in** (`CoreContext`'s call-state listener, behind the blocked
  door and the callback's repointing), only for `+420` and nine digits, only for a number 連絡先
  does not hold, only with the switch on (“Identify unknown callers online”, Calls section of the
  臨電話 UI page, on by default — off also hides the names already found). **The history never
  asks by itself**; a row carries a name because that number rang here once. Other countries wait
  for a source of their own.
- **The page:** the name is the text of `<span class="callerId">`; a page without that span knows
  nothing. It is a page for people read by a program — when the markup moves, names stop arriving
  and the “calls” diagnostic says `online id … no name` for every number.
- **Never held up:** the call rings at once under its number; the name takes its place when the
  answer comes (4 s timeout). **Every ring asks again** — never add a “fresh enough” check (白い熊,
  2026-11-20). A name found before is shown at once and replaced if the fresh answer differs; a
  fresh “nothing known” takes it away, a failed request changes nothing.
- **On demand:** the history's long-press menu has “ID online” (`SkOnlineId.lookUp`), only for a
  number in no address book that a source covers, with the switch on. A found name needs no word —
  the rows take it; “nothing found” and a request that did not get through are flashed.
- **The flash** is `shiroikuma/SkFlash.kt`: theme ground, theme text, accent frame, in the **centre
  of the display**; only the icon keeps a colour (green done, blue note, red wrong). **Every flash
  of the app goes through it** (白い熊, 2026-11-20): `GenericActivity.showGreenToast` /
  `showBlueToast` / `showRedToast` hand theirs over, the fork's pages call `SkFlash.show`. Never
  write a new `Toast.makeText` or a `ToastUtils` toast. Two exceptions stand: upstream's persistent
  red banner (a standing notice, still at the top) and `SkDiagReceiver` (no window to draw in).
- **Store:** the preferences file `sk_online_ids`, keyed `tel:<international digits>`, value
  `name` / `src` / `at`. **Renaming or re-meaning a key breaks old exports**: it travels with the
  call history as `online_ids.json`.
- **The mark:** “ID” in black on a filled `#FFFF00` rounded square (`sk_online_id_pill`), after
  the name on the incoming screen and on the first line of the history row. A mark, not a button.
  白い熊 wanted it generic — never write the source's name there.
- **Saving the caller:** `SkSisterApps.newContact` hands 連絡先 the number in international form
  (`+420601123456`, never the nine bare digits the provider delivers) and the found name as a
  `StructuredName` data row with only the family name set — 白い熊 files these under the surname.
  `Insert.NAME` is not used: 連絡先 puts that in the first name.

## The call door — which apps may dial through 臨電話

`SkCallsProvider` (`content://shiroikuma.rindenwa.calls`: `accounts`, `dial`, `callback`,
`silence_ringer`) lets other apps place calls through an account here. Who is let in is
`SkCallDoor`: the string set `sk_call_door_apps` in the default preferences.

- **A selection, not fixed switches** (白い熊, 2026-11-21): the row “Apps that may place calls” in
  the “Accounts and apps” section of the 臨電話 UI page opens `SkAppPickerDialog` — every app with
  a launcher entry (hence the `MAIN` / `LAUNCHER` intent in the manifest’s `<queries>`), each row
  the app’s icon, its name and an outlined switch that stores at once.
- **Three defaults:** 連絡先, 電話 and メッセージ (`SkCallDoor.KNOWN`). The set is stored whole, so a
  default added later does not reach an install that already stored one — `SkCallDoor.migrate`
  adds each new default **once** (`sk_call_door_defaults_seen`), and what 白い熊 switches off
  afterwards stays off. Add a future default to `KNOWN` and nothing else.
- A `.debug` build is let in when its release id is; the caller’s package is confirmed against
  its uid.

## The 臨電話 UI page is a folding accordion with a chip index

`SkUiActivity`, per the black-yellow-theme skill (applied fully 2026-11-21):

- **Sections come from a registry** (`sections()`): a stable string key, a heading, a builder.
  **Never rename a key** — `SkPageState` stores the order (`sk_ui_section_order`) and the open
  set (`sk_ui_sections_open`) by key, in the default preferences, so both travel in the export’s
  appearance category. A new section goes into `sections()` at its built place.
- **“App language” (`language`) is the first section and the first chip** (白い熊, 2026-11-24):
  one pill row, “Language: ‹current›”, opening `SkLanguageDialog` — the form of 2026-11-27
  (+067): the title with the globe; 日本語 and English as two full-width pills that never scroll;
  one framed box that scrolls by itself, “System default” first and the rest by their own name;
  Cancel. Every line carries a bundled flag (`drawable-nodpi/sk_flag_*.png`, made by
  `tools/sk_flags.py` from Noto Color Emoji — a new language's country goes into its `COUNTRIES`
  and into `SkLanguage.flag`), the country beneath the name and the tag at the right. Upstream
  declares bare languages, so country, flag and shown tag come from the likely country
  (`ja` → `ja-JP`); what is stored and applied stays the declared tag. **The list is
  `locales_config.xml`, cut down to 日本語, English and the languages holding four fifths or more
  of upstream's strings** (cs, de, fr, it, nl, pt-BR, ru, sk, uk, zh-CN at +067) — measure again
  after an upstream sync; a language chosen earlier that left the list stays in force. `SkLanguage` keeps the tag as
  `sk_app_language` (default preferences, so it travels in the appearance export), reads the list
  from `res/xml/locales_config.xml`, asserts the choice again in `LinphoneApplication.onCreate`
  (EMUI forgets the per-app language across a sideloaded update) and, below Android 13, gives
  the application's own resources the language too, so notifications and services follow.
  `SkPageState.putFirstOnce` put the section first once in an order stored before it existed.
- Every section is folded by default; a folded section’s rows are **not built** until it is first
  opened. Row builders therefore add to `body`, never to `binding.skHolder`.
- On top: the “App settings” pill (`SkSisterApps.EXTRA_OPEN_SETTINGS` → `MainActivity`), then the
  chip index (`SkSectionIndex`: tap jumps, long press drags, chips reorder live, sections follow
  on drop). “Reset section order” is in Maintenance.
- **Every section names its icon and its tone** in `sections()`, and heading and chip both draw
  them (白い熊, 2026-11-23). The tone is one of seven settable roles — `section_base` (yellow: the
  app and its settings), `section_controls` (green), `section_words` (light blue),
  `section_frame` (heavy blue), `section_reach` (orange), `section_warning` (red: Maintenance,
  Diagnostics) and `section_flash` (rose, one section only) — shown under “This page” →
  “Section icons of this page”. **In the built order no two neighbours share a tone**; keep it
  so when adding a section. A stored order is never touched. Chips are as wide as their content.
- Every row carries an icon coloured by role (`rowIcon`); a row with an explanation carries the
  2 dp dim-gold left rule (`ruled`, after `indentRow`).
- **Every icon before a label goes through `SkLabelIcon.set`**: 1.25 × the label's text size
  (rows never under 18 dp), read again at every layout, the gap half the icon, centred on the
  label's first line. The gutter is reported to what stands under the label's text (a row's
  explanation, a heading's underline).
- **`SkViewInflater`** (named by `viewInflaterClass` in `Theme.SkBlackYellow`) is where every
  inflated switch, radio button and check box takes the settable accent / switch-off colours.
  Radios and check boxes stay selection marks (白い熊, 2026-11-21).

## Colours — no XML holds one; every item is a settable role

白い熊, 2026-11-21: *“nothing hardcoded/pinned in XML, everything settable via UI settings”* — done
the **mixed** way, chosen by 白い熊 because this fork is rebased onto every upstream commit.

- **The role table is the one place a colour is written:** `shiroikuma/SkRoles.kt` (about 175
  roles in `SkArea`s) and the `SkSlot`s of `SkTheme.kt`. `#FFFF00` / `#000000` are the defaults of
  `SkSlot.ACCENT` / `TEXT` and `BACKGROUND`; every other role falls back to them, directly or
  through another role, unless it carries a meaning (danger red, success green, …).
- **XML names a role, never a value.** `values/sk_colors.xml` declares the role resources with
  **placeholder** values (dark magenta ground, pink ink — seeing one on the phone means the loader
  missed that surface). `Theme.SkBlackYellow` points each of upstream’s 61 colour attributes at
  its own role (`sk_a_…`). Upstream’s own colour names used directly in its layouts (`bc_white`,
  `gray_900`, …) are roles too (`up_…`), fed by id. **Upstream’s layouts are not edited for
  colour in the repository** — that is the point: no rebase conflicts.
- **Every item still has roles of its own — made at build time.** The Gradle task `skItemRoles`
  runs `tools/sk_colour_roles.py --out app/build/generated/sk-item-roles` before every build: it
  copies `res/`, and in the copy rewrites each shared colour name to a role of the item — per
  screen group and kind for layouts (`sk_hist__a_main2_600__t`: history, secondary labels, text),
  per file for shapes (`sk_d__…`) and state selectors (`sk_c__…`), per style for text styles
  (`sk_s__…`) — and writes `sk_roles_generated.xml` and `SkRolesGenerated.kt` beside it. The build
  takes its resources and that table from the copy. About 515 item roles; each falls back to the
  general role it replaced. **Never run the script on the sources and never commit its output**;
  a layout upstream adds or changes gets its roles at the next build with nothing to do by hand.
  A build needs `python3`.
- **The fork’s own shapes are drawn in code** (`shiroikuma/SkShapes.kt`). `drawable/sk_*.xml` that
  are not icons are colourless **stubs** keeping the name layouts use; `SkShapes.install` puts a
  factory in front of each activity’s inflater that replaces a stub given as `android:background`
  by the shape built from its own roles. A background chosen by a binding expression uses the
  `skShape` adapter instead. A new shape: a stub file, a branch in `SkShapes.build` and
  `isStub`, and its roles.
- **`SkPalette`** lays the roles over the resources with a `ResourcesLoader` (Android 11+), built
  by Material’s colour-table creator through `com.google.android.material.color.SkColorLoaderBridge`
  (a class of ours in Material’s package, because the creator is package-private — check it still
  compiles after a Material update). `SkPalette.apply` runs in `LinphoneApplication.onCreate` and
  in every activity’s `getTheme()`; a stored colour bumps the generation and an activity built
  from older colours recreates itself in `onResume`.
- **The fork’s own layouts carry no colour at all**; they are coloured in code — by the binding
  adapters `skInk` / `skTint` / `skGround` (a role key), or where they are inflated.
- **A new coloured thing gets a role in the same commit**, and a role key is never renamed: it is
  the preference key (`sk_role_<key>`, default preferences, so it travels in the export’s
  appearance category). Every role has one swatch row on the 臨電話 UI page, in the section of its
  area; the picker’s Default resets one role, “Reset all colours” in Maintenance resets all.
- **What the platform draws before any code runs** stays in XML: `sk_window_black` (window ground
  and system bars in `Theme.SkBlackYellow`, repainted by `SkPalette.paintWindow`), the splash
  ground and the launcher icon ground (`bc_black`, read by the system without our loader).
- Not roles: the fill of a vector icon (a mask, tinted where it is used) and upstream’s
  multi-colour illustrations.

## Japanese — every string has one

白い熊, 2026-11-23. Upstream has no Japanese translation, so `values-ja/strings.xml` is wholly the
fork's: **every translatable key of `values/strings.xml`, upstream's and ours, has an entry**
(1377 at +061). A string added or reworded gets its Japanese in the same change — and after an
upstream sync, so does every string upstream added. Compare the key sets before a build is
called done.

- The fork's strings are **not** marked `translatable="false"` (they were until +060, which hid
  the gap). Only what is not text keeps the mark: sample names and numbers, formats, 電話 / 臨電話.
- **Colour-role labels** are English literals in `SkRoles.kt`; their Japanese is
  `tools/sk_role_labels_ja.tsv` (`key<TAB>label`). `tools/sk_colour_roles.py` writes both into
  the build's copy of the resources (`sk_role_labels.xml`, `values` and `values-ja`) with
  `SkRoleLabels.kt`, and composes the labels of the item roles in both languages.
  **A hand-written role without a line in the tsv stops the build.** The page reads a label
  through `SkRoleLabels.of`.
- Terms: 通話 / 発信 / 着信 / 不在着信, 連絡先, 会話, 会議, 録音, 端末, 着信拒否 (block), 背景 (ground),
  枠 (frame), 通知表示 (flash). A directory is ディレクトリ.

## Numbers are written one way

`shiroikuma/SkNumbers.kt` is the one place a phone number is formatted: `+420-601-123-456`,
`+1-808-555-0123`; a bare nine-digit number is read as Czech. A caller in no address book is named
by that number — Odorik's display name `<number>><tail of the line it rang>` is not a name
(`SkNumbers.unknownCallerName`), in the history and on the call screen alike.

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
own keystore, and the three skills above. Icon and full de-branding follow next, then the first
build.

**Signing moved out of the repo (2026-08-01).** The `6441c21e → 5c0ed6a3` upstream sync destroyed
the gitignored `keystore.properties` — upstream tracks it, `custom` deletes it, so the branch
switches overwrote and then deleted our real one. Credentials now live in
`~/.gradle/gradle.properties` as `RINDENWA_RELEASE_*`, where no branch switch can reach them.
