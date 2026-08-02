import tempfile
from pathlib import Path
import subprocess
import unittest
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


class AssetTest(unittest.TestCase):
    def paths(self, directory):
        root = Path(directory)
        return root / "release-output", root / "dist"

    def successful_command(self, release_output):
        def run(*parts, **_kwargs):
            if parts[0] == "gradle":
                release_output.mkdir(parents=True)
                (release_output / "app-release.apk").write_bytes(b"signed apk")
            return subprocess.CompletedProcess(
                parts,
                0,
                stdout="Signer #1 certificate DN: CN=Wallpaper Moe",
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
                mock.patch.object(release, "command", side_effect=self.successful_command(release_output)) as command,
            ):
                asset = release.build_asset(Version(1, 4, 0, 12))

            self.assertEqual(asset.name, "wallpaper-moe-v1.4.0.apk")
            self.assertEqual(asset.read_bytes(), b"signed apk")
            self.assertEqual([path.name for path in dist.iterdir()], [asset.name])
            gradle = command.call_args_list[0].args
            self.assertEqual(gradle, ("gradle", ":app:lintRelease", ":app:assembleRelease"))

    def test_build_rejects_unsigned_output(self):
        with tempfile.TemporaryDirectory() as directory:
            release_output, dist = self.paths(directory)

            def unsigned(*parts, **_kwargs):
                if parts[0] == "gradle":
                    release_output.mkdir(parents=True)
                    (release_output / "app-release-unsigned.apk").write_bytes(b"unsigned")
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
                if parts[0] == "gradle":
                    release_output.mkdir(parents=True)
                    (release_output / "app-release.apk").write_bytes(b"debug signed")
                    return subprocess.CompletedProcess(parts, 0, stdout="", stderr="")
                return subprocess.CompletedProcess(
                    parts,
                    0,
                    stdout="Signer #1 certificate DN: CN=Android Debug, O=Android",
                    stderr="",
                )

            with (
                mock.patch.object(release, "RELEASE_OUTPUT", release_output),
                mock.patch.object(release, "DIST", dist),
                mock.patch.object(release, "command", side_effect=debug_signed),
                self.assertRaisesRegex(ReleaseError, "debug certificate"),
            ):
                release.build_asset(Version(1, 0, 0, 1))


if __name__ == "__main__":
    unittest.main()
