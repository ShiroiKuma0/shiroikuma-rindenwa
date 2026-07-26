# Changelog — 白い熊 臨電話

Fork-only changes. Upstream Linphone's own changelog stays in `CHANGELOG.md`.

## 6.3.0-alpha+15 — current

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
- Own signing key. `keystore.properties` is untracked here — upstream ships it tracked-and-empty and
  loads it unconditionally; ours carries a real password, so the build now tolerates its absence and
  `keystore.properties_sample` documents the keys.
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
