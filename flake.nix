{
  description = "Wallpaper Moe - an Android app that rotates lock and home screen wallpapers";

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

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ platformVersion emulatorPlatformVersion ];
          buildToolsVersions = [ buildToolsVersion ];
          cmdLineToolsVersion = "22.0";
          includeEmulator = true;
          includeSystemImages = true;
          # AOSP rather than google_apis: `adb root` works on it.
          systemImageTypes = [ "default" ];
          # x86_64 images run under KVM, at native speed.
          abiVersions = [ "x86_64" ];
          includeSources = false;
          includeNDK = false;
        };

        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
      in
      {
        devShells.default = pkgs.mkShell {
          name = "wallpaper-moe";

          packages = with pkgs; [
            jdk25
            gradle_9
            androidSdk
            android-tools # adb, fastboot
            jq
            zip
            unzip
            curl
          ];

          JAVA_HOME = "${pkgs.jdk25}";
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;

          # Points AGP at the patched aapt2 NixOS ships, which is the one that
          # runs against the Nix dynamic loader.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

          # `nix develop --command <cmd>` runs this hook too, so the banner is
          # gated on a terminal and written to stderr, keeping it out of a pipe.
          shellHook = ''
            if [ -t 1 ]; then
              {
                echo "wallpaper-moe dev shell"
                echo "  jdk        : $(java -version 2>&1 | head -1)"
                echo "  gradle     : $(gradle --version 2>/dev/null | awk '/^Gradle/{print $2}')"
                echo "  sdk        : ${sdkRoot}"
                echo "  platform   : android-${platformVersion} / build-tools ${buildToolsVersion}"
                echo "  adb        : $(adb version 2>/dev/null | head -1)"
              } >&2
            fi
          '';
        };
      });
}
