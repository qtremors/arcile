<p align="center">
  <img src="docs/assets/Arcile.svg" alt="Arcile Logo" width="120"/>
</p>

<h1 align="center"><a href="https://qtremors.github.io/arcile/">Arcile</a></h1>

<p align="center">
  A private, modern Android file manager.
</p>

<p align="center">
  <a href="https://github.com/qtremors/arcile/releases/latest">
    <img src="https://img.shields.io/github/v/release/qtremors/arcile?label=Download%20APK&color=2da44e&logo=android&logoColor=white" alt="Download APK" height="32">
  </a>
</p>

<p align="center">
  <a href="https://github.com/qtremors/arcile/releases"><img src="https://img.shields.io/github/downloads/qtremors/arcile/total?label=Total%20Downloads&color=0969da" alt="Total Downloads"></a>
  <a href="https://github.com/qtremors/arcile/releases"><img src="https://img.shields.io/github/downloads/qtremors/arcile/latest/total?label=Latest%20Downloads&color=2da44e" alt="Latest Downloads"></a>
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B-34A853?logo=android" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-TSL-red" alt="License">
</p>

> [!NOTE]
> **Privacy first:** Arcile does not request `android.permission.INTERNET`. Your files and usage data stay on your device.

## Why Arcile

Arcile is an offline Android file manager built for speed, privacy, and a clean native interface. It has no ads, trackers, accounts, network access, or hidden data collection.

## Screenshots

<table>
  <tr>
    <td align="center" width="33%"><a href="docs/Screenshots/Homepage.webp"><img src="docs/Screenshots/Homepage.webp" alt="Home screen with storage, categories, and Quick Access" width="240"></a><br><b>Home</b><br><sub>Storage, categories, and shortcuts</sub></td>
    <td align="center" width="33%"><a href="docs/Screenshots/Browser.webp"><img src="docs/Screenshots/Browser.webp" alt="Browser listing folders in Internal Storage" width="240"></a><br><b>Browser</b><br><sub>Folders with counts, sizes, and dates</sub></td>
    <td align="center" width="33%"><a href="docs/Screenshots/QuickAccess.webp"><img src="docs/Screenshots/QuickAccess.webp" alt="Manage Quick Access screen" width="240"></a><br><b>Quick Access</b><br><sub>Pick and reorder Home shortcuts</sub></td>
  </tr>
  <tr>
    <td align="center"><a href="docs/Screenshots/StorageSummary.webp"><img src="docs/Screenshots/StorageSummary.webp" alt="Storage Dashboard summary by category" width="240"></a><br><b>Storage summary</b><br><sub>What uses space, by category</sub></td>
    <td align="center"><a href="docs/Screenshots/UsageMap.webp"><img src="docs/Screenshots/UsageMap.webp" alt="Storage usage map shown as a sunburst chart" width="240"></a><br><b>Usage map</b><br><sub>Every folder as a sunburst</sub></td>
    <td align="center"><a href="docs/Screenshots/Cleaner.webp"><img src="docs/Screenshots/Cleaner.webp" alt="Storage Cleaner with large, old, and duplicate files" width="240"></a><br><b>Storage Cleaner</b><br><sub>Large, old, and duplicate files</sub></td>
  </tr>
  <tr>
    <td align="center"><a href="docs/Screenshots/Theme.webp"><img src="docs/Screenshots/Theme.webp" alt="Appearance settings with theme modes, presets, and accent colors" width="240"></a><br><b>Appearance</b><br><sub>Theme modes, presets, and accents</sub></td>
    <td align="center"><a href="docs/Screenshots/Settings.webp"><img src="docs/Screenshots/Settings.webp" alt="Settings home screen" width="240"></a><br><b>Settings</b><br><sub>Every option in clear pages</sub></td>
    <td align="center"><a href="docs/Screenshots/FileDefaults.webp"><img src="docs/Screenshots/FileDefaults.webp" alt="File opening settings by file type" width="240"></a><br><b>File opening</b><br><sub>Choose what opens in Arcile</sub></td>
  </tr>
</table>

## Download

Download the latest APK from [GitHub Releases](https://github.com/qtremors/arcile/releases) and install it on a device running Android 11 or newer.

Arcile needs Android's all-files access permission for full shared-storage management. Notification permission is requested on supported Android versions so long-running file operations can show progress.

## Features

- **Private and offline:** No ads, accounts, trackers, data collection, or internet permission.
- **Full file browser:** Browse Internal Storage, SD cards, USB drives, and folders available through Android.
- **Everyday file tools:** Create, search, copy, move, rename, share, delete, restore, and securely erase files. Tabs, split view, batch rename, sorting, and filters are included.
- **Photos, video, and music:** Browse galleries, songs, folders, albums, artists, and playlists. Edit photo and music details, follow timed lyrics, watch videos with subtitles and gesture controls, and play music in the background.
- **Documents:** Read PDFs, edit text and Markdown, preview formatting, print documents, and open other formats in installed apps.
- **Archives and Android apps:** Open, create, and extract common archives, including password-protected ZIP and 7z files. Inspect and install APK, APKS, APKM, and XAPK packages.
- **Storage tools:** See what uses space and find large, old, duplicate, empty, or unnecessary files.
- **Trash:** Restore deleted files or remove them permanently.
- **OnlyFiles vaults:** Encrypt private files, unlock with a password or biometrics, and safely import, export, view, open, or share them.
- **Personalization:** Customize Home, themes, layouts, shortcuts, thumbnails, and how files open.

## Community and support

- Join the [Discord community](https://discord.gg/QgUjuNj9U8).
- Report bugs or request features through [GitHub Issues](https://github.com/qtremors/arcile/issues).
- Review changes in the [changelog](CHANGELOG.md) and public [release notes](RELEASES.md).
- Read the [privacy policy](PRIVACY.md).

## Credits

Arcile is built by [Tremors](https://github.com/qtremors) with Kotlin and the Android platform. Thanks to the maintainers of:

- [AndroidX](https://developer.android.com/jetpack/androidx), [Jetpack Compose](https://developer.android.com/compose), [Material 3](https://m3.material.io/), [Room](https://developer.android.com/training/data-storage/room), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore), and [Media3](https://developer.android.com/media/media3)
- [Kotlin](https://kotlinlang.org/), [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines), [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization), and [Immutable Collections](https://github.com/Kotlin/kotlinx.collections.immutable)
- [Dagger and Hilt](https://dagger.dev/hilt/), [Coil](https://coil-kt.github.io/coil/), and [MaterialKolor](https://github.com/jordond/MaterialKolor)
- [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/), [Tukaani XZ for Java](https://tukaani.org/xz/java.html), and [Zip4j](https://github.com/srikanth-lingala/zip4j)
- [Bouncy Castle](https://www.bouncycastle.org/)
- [Lucide](https://lucide.dev/), [Simple Icons](https://simpleicons.org/), and [Outfit](https://github.com/Outfitio/Outfit-Fonts) for the project website

The app's **Settings → About → Open Source Licenses** screen lists its runtime libraries and their licenses. Each project remains the property of its respective authors and is used under its own license.

## For developers

Architecture, project structure, technology choices, setup, build commands, testing, release signing, and maintenance guidance live in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

Arcile is source-available under the **Tremors Source License (TSL) Version 2.0**. Viewing, forking, and derivative works require attribution; commercial use requires written permission.

Read [LICENSE.md](LICENSE.md) or the [web version](https://qtremors.github.io/license).

---

<p align="center">
  Made by <a href="https://github.com/qtremors">Tremors</a>
</p>
