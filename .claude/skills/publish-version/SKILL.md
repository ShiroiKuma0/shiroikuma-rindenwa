---
name: publish-version
description: Publish the latest built shiroikuma-rindenwa APK as a GitHub release of the fork — create the version tag (no "v" prefix), attach the APK, refresh the fork README + CHANGELOG-shiroikuma.md, and keep the GitHub default branch on `custom` so the repo page lands on our work. Use when 白い熊 says publish / release / cut a version / ship this build / make a GitHub release / publish the latest build.
---

# Publish a 臨電話 version to GitHub

Turn the latest tested build into a public GitHub **release** of the fork
(`ShiroiKuma0/shiroikuma-rindenwa`): a version tag, the APK as a downloadable asset, refreshed
README + changelog, and the default branch on `custom` so the landing page shows our work.

> **This is outward-facing — it publishes to GitHub.** 白い熊 invoking this skill *is* the
> authorization. Still, summarise the exact version + assets first, then proceed. **Never publish a
> build 白い熊 hasn't tested.**

> **No `Co-Authored-By: Claude` / "Generated with Claude" trailer** in commits or release notes.

## What gets published

The **latest APK in `~/tmp/`** — the build 白い熊 just tested on-device. Derive the version from the
**APK filename**, NOT `gradle.properties` (whose `BUILD_NUMBER` is already the *next* number,
because `buildApk` bumps it after building).

```bash
APK=$(ls -t ~/tmp/shiroikuma-rindenwa_*.apk 2>/dev/null | head -1)
VERSION=$(basename "$APK" | sed -E 's/^shiroikuma-rindenwa_(.+)_arm64-v8a\.apk$/\1/')   # e.g. 6.3.0-alpha+1
TAG="$VERSION"   # bare version, no "v" prefix
```

If `$APK` is empty, stop and tell 白い熊 there is no built APK to publish (run `build-apk` first).

## Preconditions

1. **The APK matches `HEAD`.** If the working tree has uncommitted source changes, or `HEAD` moved
   past the build, warn — the safe path is to rebuild via `build-apk` so the APK and the tag agree.
   Never publish a tag pointing at code the APK wasn't built from.
2. **On `custom`** (`git rev-parse --abbrev-ref HEAD` = `custom`) and pushed.
3. **The tag doesn't already exist** (`git tag -l "$TAG"` empty and `gh release view "$TAG"` 404s).
   If it exists, confirm with 白い熊 before re-cutting.

## Steps

1. **Keep the GitHub default branch on `custom`** so the repo page lands on our README, not
   upstream's master (idempotent — safe to run every time):
   ```bash
   gh repo edit ShiroiKuma0/shiroikuma-rindenwa --default-branch custom
   gh repo edit ShiroiKuma0/shiroikuma-rindenwa \
     --description "白い熊 臨電話 — a fork of Linphone for Android: SIP/VoIP calls and chat, de-branded, black-yellow, side-by-side installable. GPL-3."
   ```

2. **Update the README badge** — point the "Latest release" line at the new version.

3. **Update `CHANGELOG-shiroikuma.md`.** Upstream owns `CHANGELOG.md`; **our** fork changelog is
   `CHANGELOG-shiroikuma.md` — never fold fork notes into upstream's file. Keep it **specific**:
   rename the `## <old> — current` heading to the released version and add a fresh
   `## <new> — current` section above it, summarising what changed **since the last tag**:
   ```bash
   git log --oneline <previous-tag>..HEAD
   ```
   Group by area (calls / chat / identity / look / fixes), one specific bullet each — not raw commit
   subjects.

4. **Commit the docs** on `custom` and push:
   ```bash
   git add README.md CHANGELOG-shiroikuma.md
   git commit -m "Release <VERSION>: README + changelog"
   git push origin custom
   ```

5. **Tag and release.** A **lightweight** tag at `HEAD` — never `git tag -a`, see the note below —
   then a GitHub release targeting `custom` with the APK attached. **Always pin the repo with
   `-R ShiroiKuma0/shiroikuma-rindenwa`** — the working
   copy has an `upstream` remote (`BelledonneCommunications/linphone-android`), and a bare
   `gh release` will otherwise 404 against upstream. Write the notes to a real file under `~/tmp`
   (do **not** rely on `$TMPDIR`, which is unset when the sandbox is off):
   ```bash
   REPO=ShiroiKuma0/shiroikuma-rindenwa
   git tag "$TAG"                       # lightweight — NEVER -a, see the notes below
   git push origin "$TAG"
   NOTES="$HOME/tmp/rindenwa_release_notes.md"
   sed -n "/^## ${VERSION} —/,/^## [0-9]/p" CHANGELOG-shiroikuma.md | sed '/^## [0-9]/d' | tail -n +2 > "$NOTES"
   gh release create "$TAG" "$APK" -R "$REPO" \
     --target custom \
     --title "白い熊 臨電話 $VERSION" \
     --notes-file "$NOTES"
   rm -f "$NOTES"
   ```
   Keep the APK asset name as built (`shiroikuma-rindenwa_<VERSION>_arm64-v8a.apk`).

6. **Report** the release URL and confirm the default branch:
   ```bash
   gh release view "$TAG" -R ShiroiKuma0/shiroikuma-rindenwa --json url -q .url
   gh repo view ShiroiKuma0/shiroikuma-rindenwa --json defaultBranchRef -q .defaultBranchRef.name
   ```

## Notes

- **Release tags are lightweight — never annotated (`git tag -a`).** Upstream's build block in
  `app/build.gradle.kts` computes the About screen's git string with `git describe --abbrev=0`,
  which considers **only annotated** tags. Today that resolves to upstream's own annotated
  `6.3.0-alpha`; an annotated fork tag would win instead and the Help screen would read
  `6.3.0-alpha.g6441c21e+24.1+<hash>` — our version twice over, once through the fork's versionName
  and once through upstream's describe. Every fork tag so far (`+23`, `+22`, `+17`, …) is
  lightweight; keep it that way. (2026-08-01.)
- `git push`, `gh` and `scp` need `~/.ssh` / `~/.config/gh`, which the command sandbox blocks — run
  the push / `gh` / tag steps with `dangerouslyDisableSandbox: true`, same as the other fork skills.
- This skill **does not build** — it ships whatever is newest in `~/tmp/`. For a fresh build, that's
  the `build-apk` skill's job.
- `master` stays tracking upstream; releases are always cut from `custom`. After an
  `upstream-new-version` rebase, the first release on the new base is `+1`.
