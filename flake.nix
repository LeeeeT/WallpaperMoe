{
  description = "Wallpaper Moe - a customizable live wallpaper for Android";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        buildToolsVersion = "37.0.0";
        platformVersion = "37";

        # The newest system image nixpkgs carries.
        emulatorPlatformVersion = "36";

        mkAndroidComposition = withEmulator:
          pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ platformVersion ]
              ++ pkgs.lib.optionals withEmulator [ emulatorPlatformVersion ];
            buildToolsVersions = [ buildToolsVersion ];
            cmdLineToolsVersion = "22.0";
            includeEmulator = withEmulator;
            includeSystemImages = withEmulator;
            # The development SDK uses AOSP so `adb root` works, and x86_64
            # runs under KVM. The release SDK omits both large emulator inputs.
            systemImageTypes = pkgs.lib.optionals withEmulator [ "default" ];
            abiVersions = pkgs.lib.optionals withEmulator [ "x86_64" ];
            includeSources = false;
            includeNDK = false;
          };

        developmentSdk = (mkAndroidComposition true).androidsdk;
        developmentSdkRoot = "${developmentSdk}/libexec/android-sdk";
        releaseSdk = (mkAndroidComposition false).androidsdk;
        releaseSdkRoot = "${releaseSdk}/libexec/android-sdk";

        gradleOptions = sdkRoot:
          "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

        releaseCommand = pkgs.writeShellApplication {
          name = "wallpaper-moe-release";
          runtimeInputs = with pkgs; [
            coreutils
            gh
            git
            gradle_9
            jdk25
            python3
            releaseSdk
          ];
          text = ''
            export JAVA_HOME=${pkgs.jdk25}
            export ANDROID_HOME=${releaseSdkRoot}
            export ANDROID_SDK_ROOT=${releaseSdkRoot}
            export GRADLE_OPTS="${gradleOptions releaseSdkRoot} ''${GRADLE_OPTS:-}"
            export PATH="${releaseSdkRoot}/build-tools/${buildToolsVersion}:$PATH"
            exec ${pkgs.python3}/bin/python3 ${./tools/release.py} "$@"
          '';
        };
      in
      {
        devShells.default = pkgs.mkShell {
          name = "wallpaper-moe";

          packages = with pkgs; [
            jdk25
            gradle_9
            developmentSdk
            android-tools # adb, fastboot
            jq
            zip
            unzip
            curl
          ];

          JAVA_HOME = "${pkgs.jdk25}";
          ANDROID_HOME = developmentSdkRoot;
          ANDROID_SDK_ROOT = developmentSdkRoot;

          # Points AGP at the patched aapt2 NixOS ships, which is the one that
          # runs against the Nix dynamic loader.
          GRADLE_OPTS = gradleOptions developmentSdkRoot;

          # `nix develop --command <cmd>` runs this hook too, so the banner is
          # gated on a terminal and written to stderr, keeping it out of a pipe.
          shellHook = ''
            if [ -t 1 ]; then
              {
                echo "wallpaper-moe dev shell"
                echo "  jdk        : $(java -version 2>&1 | head -1)"
                echo "  gradle     : $(gradle --version 2>/dev/null | awk '/^Gradle/{print $2}')"
                echo "  sdk        : ${developmentSdkRoot}"
                echo "  platform   : android-${platformVersion} / build-tools ${buildToolsVersion}"
                echo "  adb        : $(adb version 2>/dev/null | head -1)"
              } >&2
            fi
          '';
        };

        devShells.release = pkgs.mkShell {
          name = "wallpaper-moe-release";
          packages = with pkgs; [
            gh
            git
            gradle_9
            jdk25
            python3
            releaseSdk
          ];
          JAVA_HOME = "${pkgs.jdk25}";
          ANDROID_HOME = releaseSdkRoot;
          ANDROID_SDK_ROOT = releaseSdkRoot;
          GRADLE_OPTS = gradleOptions releaseSdkRoot;

          # apksigner lives in build-tools, which the SDK package does not put
          # on PATH; the release command exports the same directory.
          shellHook = ''
            export PATH="${releaseSdkRoot}/build-tools/${buildToolsVersion}:$PATH"
          '';
        };

        apps.release = {
          type = "app";
          program = "${releaseCommand}/bin/wallpaper-moe-release";
          meta.description = "Build and draft a signed Wallpaper Moe release";
        };

        # Only tools/ is copied in, so unrelated edits do not rebuild this.
        checks.release-script = pkgs.runCommand "wallpaper-moe-release-tests" {
          nativeBuildInputs = [ pkgs.python3 ];
        } ''
          cp -r ${./tools} tools
          python3 -m unittest -v tools/test_release.py
          touch $out
        '';
      });
}
