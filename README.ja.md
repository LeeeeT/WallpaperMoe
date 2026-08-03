# Wallpaper Moe / ウォールペーパー萌え

[English](README.md) · **日本語** · [简体中文](README.zh-CN.md)

[![Android 14+](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)](#インストール)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Wallpaper Moe は Android の壁紙を自動で切り替えます。オンラインのコレクションや自分のお気に入りから選ばれ、切り替わるのは画面がロックされたときで、頻度はロックのたびから 1 日 1 回まで指定できます。**きょう** でハートをタップした壁紙は、削除するまでストレージに残ります。

| きょう | スタジオ | お気に入り |
| :---: | :---: | :---: |
| ![現在の壁紙と履歴を表示する「きょう」画面](fastlane/metadata/android/ja/images/phoneScreenshots/1.png) | ![再利用できる壁紙ソースを並べたスタジオ画面](fastlane/metadata/android/ja/images/phoneScreenshots/2.png) | ![端末内のお気に入りギャラリー](fastlane/metadata/android/ja/images/phoneScreenshots/3.png) |

## ソース

**スタジオ** を開くと、名前を付けたソースを作成して再利用できます。

| ソース | 説明 |
| --- | --- |
| **常に準備** | 既存のソースにキャッシュ、先読み、またはその両方を追加します。 |
| **組み合わせ** | 既存の複数ソースをランダム・順番・フォールバックで使います。 |
| **お気に入り** | ハートを付けた壁紙からランダムまたは順番に選びます。 |
| **Wallhaven** | wallhaven.cc をキーワード・カテゴリ・健全度・アスペクト比・並び順で検索します。 |
| **Nekos.best** | 選んだカテゴリからランダムまたは順番に取得します。 |
| **Pandora** | Xiaomi の公開壁紙チャンネルから選びます。 |

ソースは別のソースの中でも再利用できるので、プロバイダーの設定は一度で済みます。1 つのソースがアクティブに指定され、すべての切り替えはそのソースから行われます。

## インストール

Wallpaper Moe には **Android 14 以降** と `arm64-v8a` または `x86_64` の端末が必要です。ライブ壁紙として動作します。

[![Obtainium で入手](https://github.com/ImranR98/Obtainium/raw/main/assets/graphics/badge_obtainium.png)](https://apps.obtainium.page/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522dev.leeeet.wallpapermoe%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252FLeeeeT%252FWallpaperMoe%2522%252C%2522author%2522%253A%2522LeeeeT%2522%252C%2522name%2522%253A%2522Wallpaper%2520Moe%2522%257D)

1. [GitHub Releases](../../releases) から最新の APK をダウンロードします。
2. インストールします。APK を開いたアプリからのインストール許可を Android が求めることがあります。
3. **スタジオ** を開いてソースを追加し、アクティブに設定します。
4. **きょう** で **Wallpaper Moe を有効にする** をタップし、Android の壁紙プレビューでホーム画面・ロック画面・その両方から選びます。

## プライバシー

Wallpaper Moe には広告・アナリティクス・トラッキング SDK がありません。データはすべて端末内に保存され、要求する権限は `INTERNET` だけです。

アプリが通信するのは、あなたが設定したプロバイダーだけです。

- Pandora へのリクエストには、アプリのバージョン、ロケール、画面幅、現在時刻、そのインストールごとに生成されるランダムな識別子が含まれます。
- Wallhaven へのリクエストには、選択したフィルターと、入力した場合は API キーが含まれます。

## コントリビューター向け

このリポジトリには、JDK・Gradle・Android SDK・エミュレーター・ADB を含む、バージョン固定の Nix 開発環境が含まれています。

```bash
nix develop
gradle :app:assembleDebug
```

環境を通してコマンドを直接実行することもできます。

```bash
nix develop --command gradle :app:lintDebug
nix develop --command gradle :app:connectedDebugAndroidTest
```

主要なコードは `app/src/main/kotlin/dev/leeeet/wallpapermoe/` 以下にあります。

## ライセンスとサービス

Wallpaper Moe は [MIT ライセンス](LICENSE) で提供されています。

本プロジェクトは wallhaven.cc、Nekos.best、Xiaomi とは提携・承認・関連のいずれもありません。壁紙画像は、その制作者および提供元の規約に従います。
