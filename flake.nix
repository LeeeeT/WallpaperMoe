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

        emulatorPlatformVersion = "36";

        mkAndroidComposition = withEmulator:
          pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ platformVersion ]
              ++ pkgs.lib.optionals withEmulator [ emulatorPlatformVersion ];
            buildToolsVersions = [ buildToolsVersion ];
            cmdLineToolsVersion = "22.0";
            includeEmulator = withEmulator;
            includeSystemImages = withEmulator;
            systemImageTypes = pkgs.lib.optionals withEmulator [ "default" ];
            abiVersions = pkgs.lib.optionals withEmulator [ "x86_64" ];
            includeSources = false;
            includeNDK = false;
          };

        developmentSdk = (mkAndroidComposition true).androidsdk;
        developmentSdkRoot = "${developmentSdk}/libexec/android-sdk";
        releaseSdk = (mkAndroidComposition false).androidsdk;
        releaseSdkRoot = "${releaseSdk}/libexec/android-sdk";

        buildJdk = pkgs.jdk21;

        # must be in sync with `agp` in `gradle/libs.versions.toml`
        aapt2Version = "9.3.1-15703166";
        aapt2 = pkgs.stdenv.mkDerivation {
          pname = "aapt2";
          version = aapt2Version;

          src = pkgs.fetchurl {
            url = "https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/${aapt2Version}/aapt2-${aapt2Version}-linux.jar";
            hash = "sha256-53Kj2ug1R2TxsHk5AyGEJ/SDmCRFIH8uT/yMICZ1W9Q=";
          };

          nativeBuildInputs = with pkgs; [ autoPatchelfHook unzip ];
          buildInputs = with pkgs; [ stdenv.cc.cc.lib zlib ];

          dontUnpack = true;

          installPhase = ''
            runHook preInstall
            unzip -p $src aapt2 > aapt2
            install -Dm755 aapt2 $out/bin/aapt2
            runHook postInstall
          '';
        };

        gradleOptions = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}/bin/aapt2";

        releaseCommand = pkgs.writeShellApplication {
          name = "wallpaper-moe-release";
          runtimeInputs = with pkgs; [
            coreutils
            gh
            git
            gradle_9
            buildJdk
            python3
            releaseSdk
          ];
          text = ''
            export JAVA_HOME=${buildJdk}
            export ANDROID_HOME=${releaseSdkRoot}
            export ANDROID_SDK_ROOT=${releaseSdkRoot}
            export GRADLE_OPTS="${gradleOptions} ''${GRADLE_OPTS:-}"
            export PATH="${releaseSdkRoot}/build-tools/${buildToolsVersion}:$PATH"
            exec ${pkgs.python3}/bin/python3 ${./tools/release.py} "$@"
          '';
        };
      in
      {
        devShells.default = pkgs.mkShell {
          name = "wallpaper-moe";

          packages = with pkgs; [
            buildJdk
            gradle_9
            developmentSdk
            android-tools
            jq
            zip
            unzip
            curl
          ];

          JAVA_HOME = "${buildJdk}";
          ANDROID_HOME = developmentSdkRoot;
          ANDROID_SDK_ROOT = developmentSdkRoot;

          GRADLE_OPTS = gradleOptions;

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
            buildJdk
            python3
            releaseSdk
          ];
          JAVA_HOME = "${buildJdk}";
          ANDROID_HOME = releaseSdkRoot;
          ANDROID_SDK_ROOT = releaseSdkRoot;
          GRADLE_OPTS = gradleOptions;

          shellHook = ''
            export PATH="${releaseSdkRoot}/build-tools/${buildToolsVersion}:$PATH"
          '';
        };

        apps.release = {
          type = "app";
          program = "${releaseCommand}/bin/wallpaper-moe-release";
          meta.description = "Build and draft a signed Wallpaper Moe release";
        };

        checks.release-script = pkgs.runCommand "wallpaper-moe-release-tests" {
          nativeBuildInputs = [ pkgs.python3 ];
        } ''
          cp -r ${./tools} tools
          python3 -m unittest -v tools/test_release.py
          touch $out
        '';
      });
}
