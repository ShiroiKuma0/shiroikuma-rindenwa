# Changelog — 白い熊 臨電話

Fork-only changes. Upstream Linphone's own changelog stays in `CHANGELOG.md`.

## 6.3.0-alpha+2 — current

First build of the fork, on upstream `6.3.0-alpha` (upstream versionCode 602003).

### Identity & packaging

- Installed app id is `shiroikuma.rindenwa`, label 白い熊 臨電話 — side-by-side with stock Linphone.
  The code namespace stays `org.linphone`, so upstream rebases don't turn into mass-conflicts.
- The app id also drives the FileProvider authority, the AppAuth redirect scheme and the OpenID
  callback scheme, so none of them collide with an upstream install.
- Single-ABI **arm64-v8a** build (upstream also ships armeabi-v7a).
- APK named `shiroikuma-rindenwa_<version>_arm64-v8a.apk`.
- Own signing key (`shiroikuma-rindenwa.jks`). `keystore.properties` is untracked here — upstream
  ships it tracked-and-empty and loads it unconditionally; ours carries a real password, so the
  build now tolerates the file's absence and `keystore.properties_sample` documents the keys.

### Versioning

- Fork versions are `<upstream versionName>+<BUILD_NUMBER>`, with
  `versionCode = <upstream versionCode> × 1000 + BUILD_NUMBER` → `602003001` for this build.
- Upstream's two version literals in `app/build.gradle.kts` are left byte-identical and read back by
  the fork block, so an upstream bump flows through without hand-editing and without conflicting.
- The multiplier is ×1000 rather than the sister forks' ×10000: Linphone's upstream versionCode is
  already ~602003, and ×10000 would overflow Android's 2,100,000,000 ceiling.
- `buildApk` builds, signs, copies the APK to `~/tmp/` and bumps the build counter.

### Look

- The Linphone mark re-traced as yellow `#FFFF00` line-art on black, applied to the adaptive icon
  (black background + traced foreground), the legacy square and round launcher icons at all five
  densities, the splash screen vector, the notification icon, the welcome-page logo, and the
  splash branding wordmark.
- Splash background switched from white to black in the day themes, so the yellow mark reads. The
  night themes already used a dark background and were left alone.
- Store metadata rebranded: title, description, 512 px icon and a new feature graphic.

### De-branding

- App name is 白い熊 臨電話 in the default locale and all 12 translated locales that carry the name
  (via each file's `&appName;` DOCTYPE entity). Hungarian had the name hardcoded in five strings —
  those now use the entity, keeping their grammatical suffixes.
- New **Source code** row in Help → About, linking to this repository.
- The "contribute translations" row points here too, rather than upstream's Weblate.
- The invite-a-contact SMS links to this repo's releases rather than upstream's download page.
- The in-app update check is disabled (`version_check_url_root` emptied) — it would otherwise offer
  upstream Linphone releases. This also hides the "Check update" button.

Left deliberately functional, not treated as branding: the `sip.linphone.org` SIP service, the
`subscribe.linphone.org` account platform, the file-transfer and log-upload endpoints, upstream's
user-guide / privacy-policy / terms / licence URLs, the Belledonne copyright notices, and internal
identifiers (`org.linphone` classes, `Theme.Linphone`, notification channel ids, log tags).

### Tooling

- Skills: `build-apk`, `upstream-new-version` (proceed-gated table of new upstream features before
  any rebase), `publish-version`. Plus `CLAUDE.md` describing the whole fork layer.
