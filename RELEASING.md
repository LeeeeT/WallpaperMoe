# Releasing

## One-time setup

Put the production keystore in `~/.gradle/gradle.properties`:

```properties
moe.storeFile=/absolute/path/to/wallpaper-moe.jks
moe.storePassword=...
moe.keyAlias=...
moe.keyPassword=...
```

Set all four properties or none. Without them, the release build has no signature.

You also need `gh auth login`, and Git must sign commits and tags. The release commit and the tag have signatures.

You also need Docker. The release command runs F-Droid's build server image, so your user must be able to run `docker`.

## For each release

Write the release notes first. Use one file for each locale, at
`fastlane/metadata/android/*/changelogs/<the next version code>.txt`. Do not commit them.

Then, from `main` at `origin/main`, run one of these commands:

```bash
nix run .#release -- patch
```

```bash
nix run .#release -- minor
```

```bash
nix run .#release -- major
```

The command does these steps:

1. It commits the version change and the release notes.
2. It runs the unit tests and lint, and builds a signed APK from that commit.
3. It runs the `fdroid build` job of fdroiddata's CI on that commit, with F-Droid's current recipe. F-Droid's `checkupdates` adds the new build, as F-Droid's bot does. Only `Repo` and `Binaries` point to local copies of the commit and the signed APK. F-Droid must make an APK that is identical to the signed APK, except for the signature.
4. It pushes the commit and a signed tag.
5. It uploads a **draft** GitHub release.

If the build or the F-Droid check fails, the release commit stays local and is not pushed. Run the same command again to continue from the build.
