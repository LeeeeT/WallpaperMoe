#!/usr/bin/env python3
"""Create a signed Wallpaper Moe release from a local, Nix-provided toolchain."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
from dataclasses import dataclass


SEMVER = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
VERSION_FILE = Path("version.properties")
FASTLANE = Path("fastlane/metadata/android")
RELEASE_OUTPUT = Path("app/build/outputs/apk/release")
DIST = Path("dist")


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class Version:
    major: int
    minor: int
    patch: int
    code: int

    @classmethod
    def parse(cls, name: str, code: str | int) -> "Version":
        match = SEMVER.fullmatch(name)
        if match is None:
            raise ReleaseError(f"invalid stable semantic version: {name!r}")
        try:
            parsed_code = int(code)
        except (TypeError, ValueError) as error:
            raise ReleaseError(f"invalid Android version code: {code!r}") from error
        if not 0 < parsed_code <= 2_100_000_000:
            raise ReleaseError("Android version code must be between 1 and 2100000000")
        return cls(*(int(part) for part in match.groups()), parsed_code)

    @property
    def name(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"

    @property
    def tag(self) -> str:
        return f"v{self.name}"

    def bump(self, kind: str) -> "Version":
        if kind == "major":
            values = self.major + 1, 0, 0
        elif kind == "minor":
            values = self.major, self.minor + 1, 0
        elif kind == "patch":
            values = self.major, self.minor, self.patch + 1
        else:
            raise ReleaseError(f"unsupported version bump: {kind}")
        return Version(*values, self.code + 1)


def read_version(path: Path = VERSION_FILE) -> Version:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ReleaseError(f"cannot read {path}: {error}") from error
    values: dict[str, str] = {}
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        key, separator, value = stripped.partition("=")
        if not separator or not key or not value:
            raise ReleaseError(f"malformed {path}:{number}")
        if key in values:
            raise ReleaseError(f"duplicate {key} in {path}")
        values[key] = value
    expected = {"versionName", "versionCode"}
    if values.keys() != expected:
        missing = expected - values.keys()
        extra = values.keys() - expected
        details = []
        if missing:
            details.append(f"missing {', '.join(sorted(missing))}")
        if extra:
            details.append(f"unknown {', '.join(sorted(extra))}")
        raise ReleaseError(f"invalid {path}: {'; '.join(details)}")
    return Version.parse(values["versionName"], values["versionCode"])


def write_version(version: Version, path: Path = VERSION_FILE) -> None:
    staged = path.with_name(f".{path.name}.tmp")
    staged.write_text(
        f"versionName={version.name}\nversionCode={version.code}\n",
        encoding="utf-8",
    )
    os.replace(staged, path)


def command(*parts: str, capture: bool = False, check: bool = True) -> subprocess.CompletedProcess[str]:
    print(f"+ {shlex.join(parts)}", file=sys.stderr)
    return subprocess.run(
        parts,
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def output(*parts: str) -> str:
    return command(*parts, capture=True).stdout.strip()


def git(*parts: str, capture: bool = False, check: bool = True) -> subprocess.CompletedProcess[str]:
    return command("git", *parts, capture=capture, check=check)


def git_output(*parts: str) -> str:
    return output("git", *parts)


def optional_output(*parts: str) -> str | None:
    result = command(*parts, capture=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else None


def require_tools() -> None:
    missing = [name for name in ("git", "gh", "gradle", "apksigner") if shutil.which(name) is None]
    if missing:
        raise ReleaseError(f"release environment is missing: {', '.join(missing)}")


def enter_repository() -> Path:
    top = optional_output("git", "rev-parse", "--show-toplevel")
    if top is None:
        raise ReleaseError("run this command from the Wallpaper Moe Git repository")
    root = Path(top).resolve()
    os.chdir(root)
    for required in (VERSION_FILE, Path("flake.nix"), Path("app/build.gradle.kts")):
        if not required.is_file():
            raise ReleaseError(f"repository is missing {required}")
    return root


def ensure_release_branch() -> None:
    if git_output("branch", "--show-current") != "main":
        raise ReleaseError("releases must start on the main branch")
    if optional_output("git", "remote", "get-url", "origin") is None:
        raise ReleaseError("the repository needs an origin remote")


def ensure_worktree_clean(allowed: tuple[Path, ...] = ()) -> None:
    permitted = {str(path) for path in allowed}
    unexpected = sorted(
        path
        for path in (
            line[3:]
            for line in git_output("status", "--porcelain=v1", "--untracked-files=all").splitlines()
        )
        if path not in permitted
    )
    if unexpected:
        raise ReleaseError(
            f"only this release's changelogs may be uncommitted; found {', '.join(unexpected)}"
        )


def changelogs(version: Version) -> tuple[Path, ...]:
    """The per-locale release notes, which stores key by Android version code."""
    return tuple(sorted(FASTLANE.glob(f"*/changelogs/{version.code}.txt")))


def tag_target(tag: str) -> str | None:
    return optional_output("git", "rev-list", "-n", "1", tag)


def remote_tag_target(tag: str) -> str | None:
    result = command(
        "git",
        "ls-remote",
        "--tags",
        "origin",
        f"refs/tags/{tag}",
        f"refs/tags/{tag}^{{}}",
        capture=True,
        check=False,
    )
    if result.returncode not in (0, 2):
        raise ReleaseError(f"could not inspect {tag} on origin")
    refs = {}
    for line in result.stdout.splitlines():
        sha, ref = line.split(maxsplit=1)
        refs[ref] = sha
    return refs.get(f"refs/tags/{tag}^{{}}") or refs.get(f"refs/tags/{tag}")


def releases() -> list[dict[str, object]]:
    text = output("gh", "release", "list", "--limit", "1000", "--json", "isDraft,tagName")
    try:
        value = json.loads(text)
    except json.JSONDecodeError as error:
        raise ReleaseError("GitHub returned invalid release-list data") from error
    if not isinstance(value, list):
        raise ReleaseError("GitHub returned an invalid release list")
    return value


def release_info(tag: str) -> dict[str, object] | None:
    summary = next((release for release in releases() if release.get("tagName") == tag), None)
    if summary is None:
        return None
    text = output("gh", "release", "view", tag, "--json", "isDraft,url")
    try:
        value = json.loads(text)
    except json.JSONDecodeError as error:
        raise ReleaseError(f"GitHub returned invalid release data for {tag}") from error
    return value


def other_draft(current_tag: str) -> str | None:
    return next(
        (
            release["tagName"]
            for release in releases()
            if release.get("isDraft") and release.get("tagName") != current_tag
        ),
        None,
    )


def build_asset(version: Version) -> Path:
    if RELEASE_OUTPUT.exists():
        shutil.rmtree(RELEASE_OUTPUT)
    if DIST.exists():
        shutil.rmtree(DIST)
    DIST.mkdir()
    command("gradle", ":app:verifyReleaseSigning", ":app:lintRelease", ":app:assembleRelease")

    built = RELEASE_OUTPUT / "app-release.apk"
    unsigned = RELEASE_OUTPUT / "app-release-unsigned.apk"
    if not built.is_file():
        if unsigned.is_file():
            raise ReleaseError(
                "Gradle produced an unsigned APK; configure all four moe.* release-signing properties"
            )
        raise ReleaseError(f"Gradle did not produce {built}")

    verification = command(
        "apksigner",
        "verify",
        "--verbose",
        "--print-certs",
        str(built),
        capture=True,
    )
    certificate = f"{verification.stdout}\n{verification.stderr}"
    if "Android Debug" in certificate:
        raise ReleaseError("release APK is signed with an Android debug certificate")

    asset = DIST / f"wallpaper-moe-{version.tag}.apk"
    shutil.copy2(built, asset)
    return asset


def verify_release_commit(tag: str) -> None:
    expected = f"Release {tag}"
    if git_output("log", "-1", "--format=%s") != expected:
        raise ReleaseError(f"local version is not represented by an exact {expected!r} commit")
    git("verify-commit", "HEAD")


def create_release_commit(version: Version, notes: tuple[Path, ...], original: bytes) -> None:
    paths = [str(VERSION_FILE), *(str(path) for path in notes)]
    try:
        git("add", "--", *paths)
        changed = git_output("diff", "--cached", "--name-only").splitlines()
        unexpected = [path for path in changed if path not in set(paths)]
        if unexpected:
            raise ReleaseError(f"release commit would contain unexpected files: {', '.join(unexpected)}")
        # Notes already committed by hand stage nothing; the bump never can.
        if str(VERSION_FILE) not in changed:
            raise ReleaseError("release commit would not contain the version bump")
        git("diff", "--cached", "--check")
        git("commit", "-S", "-m", f"Release {version.tag}")
    except Exception:
        if git_output("log", "-1", "--format=%s") != f"Release {version.tag}":
            git("restore", "--staged", "--", *paths, check=False)
            VERSION_FILE.write_bytes(original)
        raise


def ensure_signed_tag(version: Version) -> None:
    head = git_output("rev-parse", "HEAD")
    existing = tag_target(version.tag)
    if existing is None:
        git("tag", "-s", version.tag, "-m", f"Wallpaper Moe {version.tag}")
    elif existing != head:
        raise ReleaseError(f"{version.tag} already points to another commit")
    git("verify-tag", version.tag)


def push_release(version: Version, origin_main: str) -> None:
    head = git_output("rev-parse", "HEAD")
    remote_tag = remote_tag_target(version.tag)
    if origin_main == head and remote_tag == head:
        return
    if remote_tag not in (None, head):
        raise ReleaseError(f"origin/{version.tag} points to another commit")
    parent = git_output("rev-parse", "HEAD^")
    if origin_main == parent and remote_tag is None:
        git("push", "--atomic", "origin", "HEAD:refs/heads/main", f"refs/tags/{version.tag}")
    elif origin_main == head and remote_tag is None:
        git("push", "origin", f"refs/tags/{version.tag}")
    else:
        raise ReleaseError("origin changed during the release; inspect it before retrying")


def publish_draft(version: Version, asset: Path) -> str:
    existing = release_info(version.tag)
    if existing is not None:
        if not existing.get("isDraft"):
            raise ReleaseError(f"GitHub release {version.tag} is already published")
        command(
            "gh",
            "release",
            "upload",
            version.tag,
            str(asset),
            "--clobber",
        )
    else:
        command(
            "gh",
            "release",
            "create",
            version.tag,
            str(asset),
            "--draft",
            "--generate-notes",
            "--verify-tag",
            "--fail-on-no-commits",
        )
    current = release_info(version.tag)
    if current is None or not current.get("isDraft"):
        raise ReleaseError("GitHub did not retain the release as a draft")
    return str(current.get("url", ""))


def release(kind: str) -> None:
    require_tools()
    enter_repository()
    ensure_release_branch()
    command("gh", "auth", "status")
    git("fetch", "--tags", "--prune", "origin")

    current = read_version()
    head = git_output("rev-parse", "HEAD")
    origin_main = optional_output("git", "rev-parse", "refs/remotes/origin/main")
    if origin_main is None:
        raise ReleaseError("origin has no main branch; push main to origin before releasing")
    current_target = tag_target(current.tag)
    current_release = release_info(current.tag)
    draft = other_draft(current.tag)
    if draft is not None:
        raise ReleaseError(f"finish or delete the existing draft release {draft} first")

    if current_target == head and current_release is not None and not current_release.get("isDraft"):
        raise ReleaseError(f"{current.tag} is already published and main contains no later commits")
    if current_target != head and current_release is not None and current_release.get("isDraft"):
        raise ReleaseError(f"finish the existing draft release {current.tag} before bumping again")

    if current_target == head or git_output("log", "-1", "--format=%s") == f"Release {current.tag}":
        version = current
        ensure_worktree_clean()
        verify_release_commit(version.tag)
        asset = build_asset(version)
    else:
        if origin_main != head:
            raise ReleaseError("main must exactly match origin/main before starting a new release")
        version = current.bump(kind)
        notes = changelogs(version)
        ensure_worktree_clean(notes)
        if tag_target(version.tag) is not None or remote_tag_target(version.tag) is not None:
            raise ReleaseError(f"release tag {version.tag} already exists")
        original = VERSION_FILE.read_bytes()
        write_version(version)
        try:
            asset = build_asset(version)
        except Exception:
            VERSION_FILE.write_bytes(original)
            raise
        create_release_commit(version, notes, original)
        verify_release_commit(version.tag)

    if read_version() != version:
        raise ReleaseError("version changed unexpectedly during the release")
    ensure_signed_tag(version)
    push_release(version, origin_main)
    url = publish_draft(version, asset)
    print(f"Draft release ready: {url or version.tag}")


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(
        prog="nix run .#release --",
        description="Bump, build, sign, tag, push, and draft a Wallpaper Moe release.",
    )
    value.add_argument("bump", choices=("patch", "minor", "major"))
    return value


def main(argv: list[str] | None = None) -> int:
    arguments = parser().parse_args(argv)
    try:
        release(arguments.bump)
    except (ReleaseError, subprocess.CalledProcessError, OSError) as error:
        print(f"release failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
