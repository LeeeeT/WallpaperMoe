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
3. It pushes the commit and a signed tag.
4. It uploads a **draft** GitHub release.

If the build fails, the release commit stays local and is not pushed. Run the same command again to continue from the build.
