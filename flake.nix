{
  description = "Wallpaper Moe - a live wallpaper for Android";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";

      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      platformVersion = "37.0";
      buildToolsVersion = "37.0.0";

      androidSdk =
        (pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ platformVersion ];
          buildToolsVersions = [ buildToolsVersion ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "default" ];
          abiVersions = [ "x86_64" ];
          includeSources = false;
          includeNDK = false;
        }).androidsdk;

      androidSdkRoot = "${androidSdk}/libexec/android-sdk";

      jdk = pkgs.jdk25;

      aapt2 = "${androidSdkRoot}/build-tools/${buildToolsVersion}/aapt2";

      avdName = "wallpaper-moe";

      avdConfig = pkgs.writeText "config.ini" ''
        AvdId=${avdName}
        avd.ini.displayname=Wallpaper Moe
        avd.ini.encoding=UTF-8
        abi.type=x86_64
        hw.cpu.arch=x86_64
        hw.cpu.ncore=4
        hw.ramSize=4096
        disk.dataPartition.size=8G
        image.sysdir.1=system-images/android-${platformVersion}/default/x86_64/
        tag.id=default
        tag.display=Default Android System Image
        PlayStore.enabled=false
        hw.lcd.width=1080
        hw.lcd.height=2424
        hw.lcd.density=420
        hw.keyboard=yes
        hw.gpu.enabled=yes
        hw.gpu.mode=host
        showDeviceFrame=no
      '';

      emulator = pkgs.writeShellApplication {
        name = "moe-emulator";
        runtimeInputs = [ androidSdk ];
        text = ''
          avd_home="''${XDG_STATE_HOME:-$HOME/.local/state}/wallpaper-moe/avd"
          avd_dir="$avd_home/${avdName}.avd"
          mkdir -p "$avd_dir"
          install -m 644 ${avdConfig} "$avd_dir/config.ini"
          printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=android-${platformVersion}\n' "$avd_dir" \
            > "$avd_home/${avdName}.ini"

          export ANDROID_HOME=${androidSdkRoot}
          export ANDROID_SDK_ROOT=${androidSdkRoot}
          export ANDROID_AVD_HOME="$avd_home"
          export VK_DRIVER_FILES=${pkgs.mesa}/share/vulkan/icd.d
          export __EGL_VENDOR_LIBRARY_FILENAMES=${pkgs.mesa}/share/glvnd/egl_vendor.d/50_mesa.json
          export LD_LIBRARY_PATH=${pkgs.mesa}/lib:${pkgs.libglvnd}/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}

          exec emulator -avd ${avdName} -gpu host "$@"
        '';
      };
    in
    {
      packages.${system}.emulator = emulator;

      apps.${system}.emulator = {
        type = "app";
        program = "${emulator}/bin/moe-emulator";
        meta.description = "Run the Wallpaper Moe emulator on the host GPU";
      };

      devShells.${system}.default = pkgs.mkShell {
        packages = [
          jdk
          androidSdk
          emulator
        ];

        JAVA_HOME = jdk.home;
        ANDROID_HOME = androidSdkRoot;
        ANDROID_SDK_ROOT = androidSdkRoot;
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}";
      };

      formatter.${system} = pkgs.nixfmt;
    };
}
