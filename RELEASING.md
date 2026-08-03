# Releasing

## One-time setup

Put the production keystore in `~/.gradle/gradle.properties`:

```properties
moe.storeFile=/absolute/path/to/wallpaper-moe.jks
moe.storePassword=...
moe.keyAlias=...
moe.keyPassword=...
```

You also need `gh auth login` and Git configured to sign — the release commit
and tag are both signed.

## For each release

First write the release notes, one file per locale, at
`fastlane/metadata/android/*/changelogs/<the next version code>.txt`. Leave them
uncommitted.

Then, from `main` at `origin/main`:

```bash
nix run .#release -- patch
# or: minor / major
```

That commits the version bump, builds a signed APK from that commit, pushes the
commit and tag, and uploads a **draft** GitHub release. A build that fails
leaves the release commit behind unpushed. Rerunning the same command picks it
up and carries on from the build.
