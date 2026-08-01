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

From a clean `main` that matches `origin/main`:

```bash
nix run .#release -- patch
# or: minor / major
```

That bumps the version, builds a signed APK, pushes the commit and tag, and
uploads a **draft** GitHub release.
