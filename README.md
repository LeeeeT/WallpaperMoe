# Wallpaper Moe / ウォールペーパー萌え

**English** · [日本語](README.ja.md) · [简体中文](README.zh-CN.md)

[![Android 14+](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)](#install)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Wallpaper Moe is a live wallpaper that changes itself. It gets wallpapers from online collections, from a folder on your device, or from your favorites. A change occurs when the screen goes off: each time, or at most each hour, each 6 hours or each day.

| Today | Studio | Favorites |
| :---: | :---: | :---: |
| ![Today screen with the current wallpaper and history](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Studio screen with wallpaper sources](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) | ![Favorites that you can put in order](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) |

## Features

- **Home and lock screen.** Both screens can use one source, or each screen can have its own source and frequency. Each screen has its own brightness, contrast and blur. At unlock, the wallpaper changes smoothly from the lock look to the home look.
- **Sources that you build.** A source can use other sources. You configure a source one time and use it in many places.
- **History and favorites.** The last 20 wallpapers stay on the device at full size. Tap a heart to keep a wallpaper. You can put favorites in order.
- **Viewer.** Swipe and zoom through the history or the favorites. Set an image as the wallpaper, save it to the gallery, share it, or open its source page.
- **Next wallpaper tile.** Add the tile to Quick Settings to change the wallpaper from anywhere.
- **Wallpaper colors.** The app gives the colors of the current wallpaper to Android, so the system theme can follow the wallpaper.
- **Unmetered networks only.** Automatic changes can wait for Wi‑Fi.
- **Backup.** Export the sources, settings and favorites to one file, and import them on a different device.

## Sources

| Source | What it does |
| --- | --- |
| **Wallhaven** | Searches wallhaven.cc by text, category, purity, aspect ratio and order. |
| **Nekos.best** | Gets anime artwork from the kinds that you select, at random or in turn. |
| **Pandora** | Gets images from Xiaomi's public wallpaper channels. |
| **Device folder** | Uses the images in a folder on your device and in its subfolders. |
| **Favorites** | Uses the wallpapers that you marked with a heart, at random or in your order. |
| **Keep ready** | Prepares wallpapers before a change, saves wallpapers for later, or both, for a different source. |
| **Combine** | Uses two or more sources at random, in turn, or as fallbacks. |

## Install

Wallpaper Moe requires **Android 14 or newer**.

[![Get it on Obtainium](https://github.com/ImranR98/Obtainium/raw/main/assets/graphics/badge_obtainium.png)](https://apps.obtainium.page/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522dev.leeeet.wallpapermoe%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252FLeeeeT%252FWallpaperMoe%2522%252C%2522author%2522%253A%2522LeeeeT%2522%252C%2522name%2522%253A%2522Wallpaper%2520Moe%2522%257D)

1. Download the latest APK from [GitHub Releases](../../releases).
2. Install it. Android can ask you to allow installs from the app that opened the APK.
3. On **Today**, tap **Activate Wallpaper Moe**. In the Android preview, apply the wallpaper to the home screen and the lock screen.

## Privacy

Wallpaper Moe has no ads, no analytics and no tracking. All data stays on the device. Android cloud backup does not include it.

The app connects only to the providers of the sources that you configure:

- Wallhaven requests contain your filters. The app sends your API key only for searches that include NSFW images.
- Nekos.best requests contain the kind of artwork.
- Pandora requests contain the app version, the language, the screen width, the time and a random identifier for this installation.
- The device folder source reads only the folder that you choose.

The backup file does not contain the Wallhaven API key.

## For contributors

The repository has a Nix development environment:

```bash
nix develop --command ./gradlew :app:installDebug
```

The debug build has the application ID `dev.leeeet.wallpapermoe.debug`, so it installs next to the release build.

To start an emulator with the host GPU:

```bash
nix run .#emulator
```

To make a release, read [RELEASING.md](RELEASING.md).

## License and services

Wallpaper Moe is available under the [MIT license](LICENSE).

Wallpaper Moe has no connection with wallhaven.cc, Nekos.best or Xiaomi, and they do not endorse it. The terms of the creators and providers apply to the wallpaper images.
