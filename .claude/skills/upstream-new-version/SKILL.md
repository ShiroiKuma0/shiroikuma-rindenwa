---
name: upstream-new-version
description: Sync the shiroikuma-rindenwa fork onto a newer upstream Linphone (BelledonneCommunications/linphone-android) and rebuild. Checks upstream for new commits; ALWAYS presents a proceed-gated tabular summary of the new upstream features BEFORE any rebase; then fast-forwards master, rebases custom, resets BUILD_NUMBER, and builds the new +1. Use when 白い熊 runs /upstream-new-version, says a new Linphone version is out, or asks to update/sync/bump to upstream, rebase custom onto upstream, or rebase-and-rebuild the fork.
---

# Sync shiroikuma-rindenwa onto a newer upstream Linphone

This fork tracks [BelledonneCommunications/linphone-android](https://github.com/BelledonneCommunications/linphone-android).
`master` mirrors `upstream/master` (fast-forward only, never carries fork work); `custom` carries all
our patches and is rebased onto each new upstream tip.

> **Never `git push`, `git commit` or `adb install` unprompted.** After the rebase + build you stop
> and let 白い熊 test on-device. You push only when they explicitly say **"Push"**.

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors `upstream/master`. No fork work here. | fast-forward only |
| `custom` | Our patches; the working/dev branch. Default branch on GitHub. | rebased onto `master` each sync |

- `origin` = `git@github.com:ShiroiKuma0/shiroikuma-rindenwa.git` (ssh, **push here**).
- `upstream` = `https://github.com/BelledonneCommunications/linphone-android.git` (https, **fetch only**).
- Pin `gh` with `-R ShiroiKuma0/shiroikuma-rindenwa` — the `upstream` remote otherwise wins.

## Versioning

- Upstream's `versionCode` / `versionName` literals in `app/build.gradle.kts` are **left exactly as
  upstream writes them**. The fork block just below `defaultConfig` reads them back and derives ours,
  so an upstream bump flows through with **no hand-editing and no conflict on those two lines**.
- `BUILD_NUMBER` (in `gradle.properties`) is our increment: bumped by every `buildApk`,
  **reset to `1`** on each new upstream version.
- Fork `versionName = "<upstream>.<upstream base date>.g<upstream base sha>+<BUILD_NUMBER, padded to 3>"`;
  `versionCode = <upstream versionCode> * 1000 + BUILD_NUMBER` (the sha never enters the code).
- The `.<date>.g<sha>` pin is `git merge-base HEAD master` (first 8 chars, plus that commit's own
  committer date so versions sort chronologically) — the upstream commit `custom`
  is rebased onto (global **`git-versioning`** skill). **Nothing to hand-edit on a sync:** the
  rebase moves the merge-base, so the next build picks the new sha up by itself.
- **The multiplier is `1000`, not the sister forks' `10000`.** Linphone's upstream `versionCode` is
  already ~602003; ×10000 would be 6,020,030,001 and blow through Android's hard ceiling of
  2,100,000,000. ×1000 keeps every code legal (`602003001`) and still monotonic across upstream
  bumps. **Never "restore" ×10000 during a conflict resolution.**

## Step 1 — check for a newer upstream version

```bash
cd ~/git/shiroikuma-rindenwa
git fetch upstream
git fetch origin

if git merge-base --is-ancestor upstream/master master; then
  echo ">>> No new upstream version — master is already at or above upstream/master."
else
  old_vn=$(git show master:app/build.gradle.kts          | grep -oP 'versionName = "\K[^"]+' | head -1)
  new_vn=$(git show upstream/master:app/build.gradle.kts | grep -oP 'versionName = "\K[^"]+' | head -1)
  old_vc=$(git show master:app/build.gradle.kts          | grep -oP 'versionCode = \K[0-9]+'  | head -1)
  new_vc=$(git show upstream/master:app/build.gradle.kts | grep -oP 'versionCode = \K[0-9]+'  | head -1)
  echo ">>> New upstream: versionName ${old_vn} -> ${new_vn}, versionCode ${old_vc} -> ${new_vc}"
  echo ">>> $(git rev-list --count master..upstream/master) new upstream commit(s)."
fi
```

If nothing is new, **stop here** — report the current version and that we are up to date. Do not ff,
do not rebase, do not build.

**Sanity-check the new `versionCode` against the ceiling** before going further:
`new_vc * 1000 + 999` must stay under `2100000000` (i.e. upstream code < 2,100,000). If upstream ever
crosses that, stop and re-plan the multiplier with 白い熊 rather than shipping an uninstallable APK.

## Step 2 — ⛔ proceed-gated table of what the new upstream version introduces

**Mandatory on every single sync. Present the table, then WAIT.** Do not fast-forward `master`, do
not rebase, do not build until 白い熊 explicitly says proceed / continue / yes. The rebase is never
started silently — this is 白い熊's standing request.

Capture the old tip **before** any fast-forward, and read the commits:

```bash
old=$(git rev-parse master)     # capture BEFORE the Step 3 ff
git log --format='%h | %an | %s' "$old"..upstream/master
git log --stat --format='%n### %h  %s%n%b' "$old"..upstream/master   # bodies + files touched
git diff --stat "$old"..upstream/master -- CHANGELOG.md              # upstream's own release notes
git show upstream/master:CHANGELOG.md | head -60
```

Present a **Markdown table**, one row per non-trivial change (fold the recurring Weblate/i18n
translation commits into a single "translations" row), with these columns:

| Column | What goes in it |
| --- | --- |
| **Commit** | short SHA |
| **Area** | subsystem — calls, chat, conference, contacts, telecom/ConnectionService, push/FCM, SDK bump, UI/theme, build, i18n |
| **What it changes** | a plain-language sentence drawn from the commit *body*, not just the subject — what is actually new or fixed, described so 白い熊 can judge it without reading the diff |
| **Relevance to this fork** | **High / Medium / Low, and why** — does it touch a file in our customization layer (identity, versioning, signing, icon, de-branding strings), the Linphone SDK version, or a feature 白い熊 actually uses? Flag anything likely to **conflict on rebase** and anything that is a **genuinely useful fix** |

Then add a short **"New features"** section in prose for anything user-visible that the table's
one-liners undersell (a new screen, a new setting, a protocol capability), and end with a one-line
takeaway — e.g. "one valuable fix (the early-media audio routing bug) plus a chat QoL batch; nothing
touches our identity or icon layer, so the rebase should be clean".

**Then stop and wait for the go-ahead.**

## Step 3 — fast-forward master, rebase custom (after the go-ahead)

> ### ⚠️ Never create a repo-root `keystore.properties`
>
> The signing credentials live in **`~/.gradle/gradle.properties`** (`RINDENWA_RELEASE_*`),
> deliberately outside the repo, so the branch switches below cannot touch them.
>
> They used to sit in a gitignored `keystore.properties` at the repo root, and the
> `6441c21e → 5c0ed6a3` sync on 2026-08-01 destroyed them: **upstream tracks that file and `custom`
> deletes it**, so `git checkout master` silently overwrote the real one with upstream's
> empty-valued version (git clobbers ignored files without warning) and `git checkout custom` then
> deleted it. Unrecoverable from git. If you ever find yourself re-adding that file, you are
> re-arming the same trap — put the values in `~/.gradle/gradle.properties` instead.

```bash
cd ~/git/shiroikuma-rindenwa
git status --short          # must be clean before rebasing

git checkout master
git merge --ff-only upstream/master

git checkout custom
git rebase master
```

Do **not** push here — both pushes are deferred to Step 7. If the rebase goes irrecoverable,
`git rebase --abort` and re-plan with 白い熊 (an aborted rebase leaves `custom` untouched; `master`
stays safely fast-forwarded).

## Step 4 — reconcile conflicts

Re-derive the *intent* against the new upstream files rather than blindly taking either side. If
upstream restructured a file we patch, port our change to the new structure.

**If the conflicts are significant, stop and plan with 白い熊 before continuing.**

Conflict-prone files, and the shape each must end up in:

- **`app/build.gradle.kts`** — the likeliest conflict. Keep all of:
  1. `val packageName = "shiroikuma.rindenwa"` (and `android.namespace = "org.linphone"` **unchanged**).
  2. `val shiroikumaBuild = (providers.gradleProperty("BUILD_NUMBER").orNull ?: "1").toInt()`.
  3. The fork-version block after `defaultConfig` (`upstreamVersionCode`/`upstreamVersionName` read
     back, `* 1000 + shiroikumaBuild`). **Upstream's own `versionCode`/`versionName` lines inside
     `defaultConfig` stay untouched** — if a conflict lands there, take *upstream's* side verbatim.
  4. Single-ABI `abiFilters += listOf("arm64-v8a")`.
  5. The `outputFileName` → `shiroikuma-rindenwa_<forkVersionName>_arm64-v8a.apk`.
  6. The `signingSetting(...)` helper and the `RINDENWA_RELEASE_*` reads — credentials come from
     `~/.gradle/gradle.properties`, with `keystore.properties` only as an optional fallback, and
     configuration must never fail when nothing is configured.
  7. The `buildApk` task at the end of the file.
- **`gradle.properties`** — keep `BUILD_NUMBER` (and reset it, Step 5). Keep upstream's other flags.
- **`.gitignore`** — keep `/keystore.properties` and `.claude/settings.local.json` un-ignored/ignored
  as we set them; `CLAUDE.md` and `.claude/skills/` stay **committed**.
- **`app/src/main/res/values/strings.xml`** — the `<!ENTITY appName "白い熊 臨電話">` in the DOCTYPE,
  plus every de-branded string (repo/help/about links). Upstream edits this file often.
- **Icon assets** — `mipmap-*/ic_launcher*.png`, `mipmap-anydpi/ic_launcher*.xml`,
  `mipmap-*/linphone_launcher_icon_foreground.png` and any in-app logo drawable must stay our
  black-yellow traced icon. A binary conflict here means upstream redrew theirs — keep **ours**.

## Step 5 — reset the build tail

In `gradle.properties`, set **`BUILD_NUMBER=1`** — the new upstream line starts its `+N` at 1.

## Step 6 — verify the customization layer survived, then build

| What | Expected value | Where |
| --- | --- | --- |
| Installed app id | `shiroikuma.rindenwa` | `app/build.gradle.kts` → `val packageName` |
| Code namespace | `org.linphone` (**never rename**) | `app/build.gradle.kts` → `android.namespace` |
| App label | `白い熊 臨電話` | `<!ENTITY appName>` in `app/src/main/res/values/strings.xml` |
| Launcher icon | black-yellow traced handset | `mipmap-*/ic_launcher*`, `mipmap-anydpi/` |
| Fork version logic | `shiroikumaBuild`, `* 1000 +`, `forkVersionName` | `app/build.gradle.kts` |
| APK naming | `shiroikuma-rindenwa_…_arm64-v8a.apk` | `app/build.gradle.kts` → `outputFileName` |
| Signing | `RINDENWA_RELEASE_*` → `~/.android-keystores/shiroikuma-rindenwa.jks` | `~/.gradle/gradle.properties`, read by `app/build.gradle.kts` |
| **No stray credentials file** | `keystore.properties` **absent** from the repo root (see Step 3) | repo root |
| Build tail | `BUILD_NUMBER=1` | `gradle.properties` |
| De-branding | no "Linphone"/`linphone.org`/upstream GitHub links in user-visible strings, Help or About | `values/strings.xml`, About/Help screens |
| Committed agent files | `CLAUDE.md`, `.claude/skills/` tracked | `.gitignore` |

Sanity-check that the script still evaluates, then build the new `+1` via the **build-apk** skill:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
grep -q '^RINDENWA_RELEASE_STORE_PASSWORD=.' ~/.gradle/gradle.properties \
  || echo '!!! signing config missing from ~/.gradle/gradle.properties — see Step 3'
./gradlew :app:tasks --console=plain | head      # config sanity
./gradlew buildApk --console=plain < /dev/null   # the new <newVersion>+1
```

The build log must contain `Signing config release is using keystore [...]`; if it says the
keystore doesn't exist, **stop** — the APK is unsigned and won't install. After the build, confirm
the APK's certificate SHA-256 still matches the previously shipped one (commands in Step 3).

`build-apk` then delivers the APK automatically via the global **`/after-build`** skill (adb push if
the phone is reachable, else scp to skhw — no prompt, no transfer question).

## Step 7 — push ONLY after 白い熊 tests and says "Push"

Stop after the build with a signed APK delivered, and **wait**. On their explicit **"Push"**:

```bash
cd ~/git/shiroikuma-rindenwa
git checkout master
git push origin master                        # fast-forward, safe

git checkout custom
git push --force-with-lease origin custom     # rebased history
```

## One-line summary of the flow

`fetch upstream` → new version? (else stop) → **tabular feature summary + WAIT for go-ahead** →
ff `master` → rebase `custom` (reconcile per Step 4) → `BUILD_NUMBER=1` → verify the layer →
**build the new `+1` via build-apk** → 白い熊 tests → on "Push": push `master`, force-with-lease `custom`.

## Hard rules

- **Never move the signing credentials into the repo.** They belong in `~/.gradle/gradle.properties`
  (`RINDENWA_RELEASE_*`); a repo-root `keystore.properties` is destroyed by the branch switches in
  Step 3, with no warning and no way to recover it from git.
- Never `adb install` / `adb uninstall` — 白い熊 installs manually from `/sdcard/tmp/`.
- Never commit or push unprompted; wait for **"Push"**.
- Never rename the `org.linphone` namespace — only the installed `applicationId` differs.
- Never restore a ×10000 versionCode multiplier (it overflows — see Versioning above).

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
