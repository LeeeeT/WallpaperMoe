import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import release
from tools.release import ReleaseError, Version, read_version, write_version


class VersionTest(unittest.TestCase):
    def test_parses_version_and_code(self):
        self.assertEqual(Version.parse("12.34.56", "78"), Version(12, 34, 56, 78))

    def test_rejects_noncanonical_or_prerelease_versions(self):
        for value in ("1", "1.2", "01.2.3", "1.02.3", "1.2.03", "1.2.3-beta"):
            with self.subTest(value=value), self.assertRaises(ReleaseError):
                Version.parse(value, 1)

    def test_rejects_android_codes_outside_the_platform_range(self):
        for value in (0, -1, 2_100_000_001):
            with self.subTest(value=value), self.assertRaises(ReleaseError):
                Version.parse("1.2.3", value)

    def test_bumps_semver_and_always_increments_android_code(self):
        version = Version(1, 2, 3, 41)
        self.assertEqual(version.bump("patch"), Version(1, 2, 4, 42))
        self.assertEqual(version.bump("minor"), Version(1, 3, 0, 42))
        self.assertEqual(version.bump("major"), Version(2, 0, 0, 42))

    def test_rejects_a_bump_past_the_android_code_limit(self):
        with self.assertRaisesRegex(ReleaseError, "cannot be incremented"):
            Version(1, 0, 0, 2_100_000_000).bump("patch")

    def test_version_file_round_trip_is_canonical(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            write_version(Version(2, 5, 0, 19), path)
            self.assertEqual(path.read_text(), "versionName=2.5.0\nversionCode=19\n")
            self.assertEqual(read_version(path), Version(2, 5, 0, 19))

    def test_version_file_rejects_unknown_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            path.write_text("versionName=1.0.0\nversionCode=1\nunexpected=1\n")
            with self.assertRaises(ReleaseError):
                read_version(path)


class ChangelogTest(unittest.TestCase):
    def fastlane(self, directory, codes):
        root = Path(directory) / "metadata"
        for locale in ("zh-CN", "en-US", "ja"):
            logs = root / locale / "changelogs"
            logs.mkdir(parents=True)
            for code in codes:
                (logs / f"{code}.txt").write_text(f"{locale} notes for {code}")
        return root

    def test_collects_one_locale_ordered_file_per_version_code(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fastlane(directory, (4, 5))
            with mock.patch.object(release, "FASTLANE", root):
                found = release.changelogs(Version(0, 4, 0, 5))

            self.assertEqual(
                [str(path.relative_to(root)) for path in found],
                [
                    "en-US/changelogs/5.txt",
                    "ja/changelogs/5.txt",
                    "zh-CN/changelogs/5.txt",
                ],
            )

    def test_rejects_a_release_when_any_locale_has_no_notes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fastlane(directory, (4,))
            (root / "ja" / "changelogs" / "5.txt").write_text("Japanese notes")
            with (
                mock.patch.object(release, "FASTLANE", root),
                self.assertRaisesRegex(ReleaseError, "missing release notes"),
            ):
                release.changelogs(Version(0, 4, 0, 5))


class AssetTest(unittest.TestCase):
    def paths(self, directory):
        root = Path(directory)
        return root / "release-output", root / "dist"

    def successful_command(self, release_output):
        def run(*parts, **_kwargs):
            if parts[0] == "./gradlew":
                release_output.mkdir(parents=True)
                (release_output / "app-release.apk").write_bytes(b"signed apk")
            return subprocess.CompletedProcess(
                parts,
                0,
                stdout=(
                    "Verified using v3 scheme (APK Signature Scheme v3): true\n"
                    "Signer #1 certificate DN: CN=Wallpaper Moe"
                ),
                stderr="",
            )

        return run

    def test_build_creates_only_the_versioned_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            release_output, dist = self.paths(directory)
            dist.mkdir()
            (dist / "wallpaper-moe-v1.3.0.apk").write_bytes(b"an earlier release")
            with (
                mock.patch.object(release, "RELEASE_OUTPUT", release_output),
                mock.patch.object(release, "DIST", dist),
                mock.patch.object(
                    release,
                    "command",
                    side_effect=self.successful_command(release_output),
                ) as command,
            ):
                asset = release.build_asset(Version(1, 4, 0, 12))

            self.assertEqual(asset.name, "wallpaper-moe-v1.4.0.apk")
            self.assertEqual(asset.read_bytes(), b"signed apk")
            self.assertEqual([path.name for path in dist.iterdir()], [asset.name])
            gradle = command.call_args_list[0].args
            self.assertEqual(
                gradle,
                (
                    "./gradlew",
                    ":app:test",
                    ":app:lintRelease",
                    ":app:assembleRelease",
                ),
            )

    def test_build_rejects_unsigned_output(self):
        with tempfile.TemporaryDirectory() as directory:
            release_output, dist = self.paths(directory)

            def unsigned(*parts, **_kwargs):
                if parts[0] == "./gradlew":
                    release_output.mkdir(parents=True)
                    (release_output / "app-release-unsigned.apk").write_bytes(
                        b"unsigned"
                    )
                return subprocess.CompletedProcess(parts, 0, stdout="", stderr="")

            with (
                mock.patch.object(release, "RELEASE_OUTPUT", release_output),
                mock.patch.object(release, "DIST", dist),
                mock.patch.object(release, "command", side_effect=unsigned),
                self.assertRaisesRegex(ReleaseError, "unsigned APK"),
            ):
                release.build_asset(Version(1, 0, 0, 1))

    def test_build_rejects_android_debug_certificate(self):
        with tempfile.TemporaryDirectory() as directory:
            release_output, dist = self.paths(directory)

            def debug_signed(*parts, **_kwargs):
                if parts[0] == "./gradlew":
                    release_output.mkdir(parents=True)
                    (release_output / "app-release.apk").write_bytes(b"debug signed")
                    return subprocess.CompletedProcess(parts, 0, stdout="", stderr="")
                return subprocess.CompletedProcess(
                    parts,
                    0,
                    stdout=(
                        "Verified using v3 scheme (APK Signature Scheme v3): true\n"
                        "Signer #1 certificate DN: CN=Android Debug, O=Android"
                    ),
                    stderr="",
                )

            with (
                mock.patch.object(release, "RELEASE_OUTPUT", release_output),
                mock.patch.object(release, "DIST", dist),
                mock.patch.object(release, "command", side_effect=debug_signed),
                self.assertRaisesRegex(ReleaseError, "debug certificate"),
            ):
                release.build_asset(Version(1, 0, 0, 1))

    def test_build_rejects_an_apk_without_v3_signing(self):
        with tempfile.TemporaryDirectory() as directory:
            release_output, dist = self.paths(directory)

            def old_signature(*parts, **_kwargs):
                if parts[0] == "./gradlew":
                    release_output.mkdir(parents=True)
                    (release_output / "app-release.apk").write_bytes(b"old signature")
                return subprocess.CompletedProcess(
                    parts,
                    0,
                    stdout="Verified using v2 scheme (APK Signature Scheme v2): true",
                    stderr="",
                )

            with (
                mock.patch.object(release, "RELEASE_OUTPUT", release_output),
                mock.patch.object(release, "DIST", dist),
                mock.patch.object(release, "command", side_effect=old_signature),
                self.assertRaisesRegex(ReleaseError, "Signature Scheme v3"),
            ):
                release.build_asset(Version(1, 0, 0, 1))


class FdroidTest(unittest.TestCase):
    RECIPE = (
        "RepoType: git\n"
        "Repo: https://github.com/LeeeeT/WallpaperMoe\n"
        "Binaries: \n"
        "  https://github.com/LeeeeT/WallpaperMoe/releases/download/v%v/wallpaper-moe-v%v.apk\n"
        "\n"
        "Builds:\n"
        "  - versionName: 1.0.0\n"
    )

    def test_points_only_repo_and_binaries_at_the_local_release(self):
        recipe, binary = release.point_recipe_at(self.RECIPE, "https://127.0.0.1:4443")
        self.assertEqual(binary, "wallpaper-moe-v%v.apk")
        self.assertEqual(
            recipe,
            "RepoType: git\n"
            "Repo: https://127.0.0.1:4443/source.git\n"
            "Binaries: https://127.0.0.1:4443/wallpaper-moe-v%v.apk\n"
            "\n"
            "Builds:\n"
            "  - versionName: 1.0.0\n",
        )

    def test_rejects_a_recipe_without_binaries(self):
        with self.assertRaisesRegex(ReleaseError, "Repo or Binaries"):
            release.point_recipe_at(
                "Repo: https://example.org/app\n", "https://127.0.0.1:1"
            )

    def test_fails_unless_the_fdroid_job_compares_the_binary_successfully(self):
        with tempfile.TemporaryDirectory() as directory:
            asset = Path(directory) / "wallpaper-moe-v2.0.0.apk"
            asset.write_bytes(b"signed apk")
            data = Path(directory) / "fdroiddata"
            (data / "metadata").mkdir(parents=True)
            recipe = data / "metadata" / "dev.leeeet.wallpapermoe.yml"
            recipe.write_text(self.RECIPE)

            def run(*parts, **kwargs):
                if parts[:2] == ("docker", "run") and "checkupdates" in parts[-1]:
                    recipe.write_text(
                        recipe.read_text()
                        + "  - versionName: 2.0.0\n    versionCode: 2\n"
                    )
                return subprocess.CompletedProcess(parts, 0, stdout="bundle", stderr="")

            for log, expected in (
                (f"... {release.FDROID_SUCCESS}\n", None),
                (
                    "compared built binary to supplied reference binary but failed\n",
                    ReleaseError,
                ),
            ):
                with (
                    self.subTest(log=log),
                    mock.patch.object(
                        release, "fdroiddata_checkout", return_value=data
                    ),
                    mock.patch.object(release, "git"),
                    mock.patch.object(release, "command", side_effect=run),
                    mock.patch.object(
                        release,
                        "certificate_authority",
                        return_value=(
                            Path("server.pem"),
                            Path("server.key"),
                            Path("bundle.pem"),
                        ),
                    ),
                    mock.patch.object(
                        release,
                        "serve_https",
                        return_value=mock.Mock(server_address=("127.0.0.1", 4443)),
                    ),
                    mock.patch.object(release, "run_logged", return_value=(0, log)),
                ):
                    recipe.write_text(self.RECIPE)
                    if expected is None:
                        release.verify_fdroid_build(Version(2, 0, 0, 2), asset)
                    else:
                        with self.assertRaises(expected):
                            release.verify_fdroid_build(Version(2, 0, 0, 2), asset)


if __name__ == "__main__":
    unittest.main()
