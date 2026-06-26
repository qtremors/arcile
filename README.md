<p align="center">
  <img src="assets/Arcile.png" alt="Arcile Logo" width="120"/>
</p>

<h1 align="center"><a href="https://qtremors.github.io/arcile/">Arcile</a></h1>

<p align="center">
  A Private & Modern Android file manager.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.2.6-blueviolet" alt="Version">
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose_BOM-2026.05.00-4285F4?logo=jetpackcompose" alt="Compose BOM">
  <img src="https://img.shields.io/badge/Android-11%2B-34A853?logo=android" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-TSL-red" alt="License">
</p>

> [!NOTE]
> **Privacy Model** Arcile does not request `android.permission.INTERNET`.

---

## Why Arcile

Arcile is built for people who want a fast Android file manager without ads, trackers, telemetry, or internet access.

It supports internal storage, SD cards, USB drives, Trash, recent files, quick access, gallery browsing, archive workflows, and storage cleanup tools.

---

## Features

| Feature | Description |
|---------|-------------|
| **Multi-Volume File Browser** | Browse and manage internal storage, SD cards, and USB drives. |
| **Home Dashboard** | Storage summaries, category shortcuts, pinned folders, and recent files. |
| **Storage Dashboard** | Volume/category breakdowns, Trash usage, and a folder usage map with breadcrumb drill-in. |
| **Quick Access** | Pin local folders, custom folders, and external handoff targets such as Android/data and Android/obb. |
| **Recent Files** | Browse recent files with grouping, search, filters, thumbnails, selection, properties, and containing-folder jumps. |
| **Gallery, Image & Model Viewers** | Browse photos and albums, view images with focal-point gestures, inspect or edit metadata, favorite items, and open GLB models through the optional Arcile GLB Viewer plugin. |
| **Storage Cleaner** | Review large files, exact duplicates, APKs, downloads, videos, marker files, empty folders, ignored items, and conservative cache cleanup. |
| **Archive Workflows** | Create ZIP, 7z, and TAR-family archives; browse and extract supported archive formats; handle password-protected ZIP/7z files; and block unsafe extraction paths. |
| **Foreground File Operations** | Copy, move, archive, extract, Trash, and delete flows show foreground progress and operation recovery prompts where available. |
| **Conflict Resolution** | Smart paste detects top-level name collisions, compares metadata for conflicting files, and supports replace, keep both, skip, and batch resolution choices. |
| **Trash Subsystem** | Stores deleted files and restore metadata on permanent volumes, with recovered-items handling, filters, sorting, properties, and undo where possible; temporary drives delete permanently. |
| **Selection Properties** | Inspect selected files and folders with paths, counts, sizes, hidden-item counts, file types, and access status. |
| **Search & Filters** | Search and filter by type, size, date, extension, hidden files, volume, folder scope, and saved presets. |
| **Material 3 Theming** | Dynamic colors, custom accents, light/dark/OLED modes, haptics, filename controls, and thumbnail controls. |
| **First-Run Onboarding** | Guided setup for theme, accent, all-files access, and notification permission context for long-running operations. |
| **Open / Share Handoff** | Opens and shares files through a centralized helper with path checks, staging for shared local files, batch guards, MIME grouping, and Android content URI handoff. |
| **Offline & Ad-Free** | No internet permission, no ads, no telemetry, and no tracker dependency. |

---

## Supported File Types

Arcile can browse and manage files of any type, including files with unknown or missing extensions. Copy, move, rename, share, properties, Trash, search, and Open With are not limited to the formats below.

| Category | Recognized extensions | Arcile behavior |
|---------|------------------------|-----------------|
| **Images** | `.jpg`, `.jpeg`, `.png`, `.gif`, `.bmp`, `.webp`, `.svg`, `.heic`, `.heif`, `.ico`, `.raw` | Gallery, thumbnails, built-in image viewer, gestures, and metadata inspection. HEIC, HEIF, ICO, and RAW decoding depends on Android/device codec support. Metadata writing is available for writable `.jpg`, `.jpeg`, `.png`, and `.webp` files. |
| **Videos** | `.mp4`, `.mkv`, `.avi`, `.mov`, `.wmv`, `.flv`, `.webm`, `.m4v`, `.3gp`, `.3g2`, `.ts`, `.mts`, `.m2ts`, `.mpeg`, `.mpg`, `.vob`, `.ogv` | Video thumbnails, categorization, file operations, sharing, and playback through a compatible installed app. |
| **Audio** | `.mp3`, `.wav`, `.flac`, `.aac`, `.ogg`, `.wma`, `.m4a`, `.opus`, `.amr`, `.mid`, `.midi` | Album-art previews, categorization, file operations, sharing, and playback through a compatible installed app. |
| **Documents** | `.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`, `.txt`, `.rtf`, `.odt`, `.ods`, `.odp`, `.csv`, `.epub` | Categorization, search, properties, file operations, sharing, and opening through a compatible installed app. |
| **Archives — browse and extract** | `.zip`, `.7z`, `.tar`, `.tar.gz`, `.tgz`, `.tar.bz2`, `.tbz2`, `.tar.xz`, `.txz`, `.gz`, `.bz2`, `.xz` | Built-in archive browsing and extraction. Password-protected archive handling is available for ZIP and 7z. |
| **Archives — create** | `.zip`, `.7z`, `.tar`, `.tar.gz`, `.tgz`, `.tar.bz2`, `.tbz2`, `.tar.xz`, `.txz` | Built-in archive creation with selectable compression options. |
| **Other recognized archives** | `.rar`, `.zst` | Recognized as archives for categorization and normal file management; use Open With for an installed compatible app. |
| **Android packages** | `.apk`, `.xapk`, `.apks`, `.apkm` | Package icons, categorization, file operations, sharing, and Android-compatible handoff. Installation support depends on the package type and installed system/app handlers. |
| **3D models** | `.glb` | Supported through the optional Arcile GLB Viewer plugin APK, with model controls, metadata, sharing, and background options. |

Actual playback, decoding, preview, and external opening capabilities can vary by Android version, device codecs, and installed apps.

---

## Quick Start

Download the latest APK from [GitHub Releases](https://github.com/qtremors/arcile/releases) and install it on your Android device.

> **Runtime permission:** Arcile requires Android 11 or newer and uses Android's all-files access permission for full file management. Notification permission is requested on newer Android versions so foreground file operations can show progress.

### Build Commands

Run Gradle commands from `arcile-app/` (`gradlew.bat` may be used instead of `./gradlew` on Windows):

```bash
# Build both debug APKs
./gradlew :app:assembleDebug :plugin-glb:assembleDebug

# Run the main plugin-related unit tests
./gradlew :plugin-api:testDebugUnitTest :plugin-glb:testDebugUnitTest :app:testDebugUnitTest

# Build signed, minified release APKs
./gradlew :app:assembleRelease :plugin-glb:assembleRelease
```

Release outputs:

```text
app/build/outputs/apk/release/Arcile-1.2.6.apk
plugin-glb/build/outputs/apk/release/Arcile-GLB-Viewer-1.0.0.apk
```

The GLB plugin is optional. Install both APKs to open `.glb` files inside Arcile:

```bash
adb install -r app/build/outputs/apk/debug/Arcile-1.2.6-debug.apk
adb install -r plugin-glb/build/outputs/apk/debug/Arcile-GLB-Viewer-1.0.0-debug.apk
```

Arcile and its plugins must be signed by the same certificate. The GLB plugin has no launcher entry and is opened only when Arcile hands it a supported file.

### Release Signing

Release builds read signing credentials from `signing.properties` first, then `local.properties` as a fallback. Neither file should be committed.

```properties
signing.storeFile=/absolute/path/to/my-release-key.jks
signing.storePassword=your_store_password
signing.keyAlias=your_key_alias
signing.keyPassword=your_key_password
```

From the Gradle root (`arcile-app/`), build the release APK:

```bash
./gradlew :app:assembleRelease :plugin-glb:assembleRelease
```

Release builds enable R8 minification and resource shrinking.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.2.10 |
| **Android Gradle Plugin** | 9.2.1 |
| **UI** | Jetpack Compose BOM 2026.05.00, Material 3 1.5.0-alpha19, Material 3 Adaptive |
| **Architecture** | Modular MVVM with Gradle boundaries, feature-scoped ViewModels, StateFlow, and Hilt DI |
| **Navigation** | Navigation Compose with `kotlinx.serialization` typed routes |
| **Storage** | `java.io.File`, `StatFs`, MediaStore, cache-backed FileProvider handoffs, foreground service operations |
| **Persistence** | Room cache database (`arcile-cache.db`, schema version 2) plus DataStore Preferences for theme, browser presentation, storage classification, quick access, cleaner rules, and onboarding |
| **Media** | Coil with custom APK icon, audio album art, PDF, and video thumbnail fetchers |
| **Archives** | Apache Commons Compress, Tukaani XZ, and Zip4j |
| **Android Support** | Android 11 or newer |

---

## Project Structure

```text
arcile/
├── arcile-app/
│   ├── app/                                     # App entry point, Hilt composition, and shell UI
│   │   ├── src/main/java/dev/qtremors/arcile/
│   │   │   ├── ArcileApp.kt                     # Hilt application startup & image loader
│   │   │   ├── MainActivity.kt                  # App activity, splash, and main layout navigation shell
│   │   │   ├── presentation/                    # ViewModels, screens, components, and AppNavigationGraph
│   │   │   └── di/                              # Dagger Hilt dependency injection modules
│   ├── core/                                    # Shared business logic and UI frameworks
│   │   ├── runtime/                             # Dispatcher injection, app logger, and common helpers
│   │   ├── ui/                                  # Common UI design tokens, theme, haptics, and reusable Compose nodes
│   │   │   └── testing/                         # Shared compose test theme helper (ArcileTestTheme)
│   │   ├── navigation/
│   │   │   └── api/                             # Serializable typed routes (AppRoutes)
│   │   ├── presentation/
│   │   │   └── api/                             # FolderTabs, LocalSearchHelper, DeleteFlowDelegate, PropertiesUiModel
│   │   ├── testing/                             # Shared unit test fakes (FakeFileRepository, FakeBulkFileOperationCoordinator)
│   │   ├── operation/                           # Foreground services and operation journal tracking
│   │   │   ├── api/                             # Task progress events and operations interfaces
│   │   │   └── src/                             # Concrete operation coordinator and background service
│   │   └── storage/                             # File system data orchestrator
│   │       ├── domain/                          # Domain models, volume references, and repository interfaces
│   │       └── data/                            # FileSystem, MediaStore client, volume discovery, and transfers
│   ├── plugin-api/                              # Dependency-free intent contract and plugin metadata models
│   ├── shared/                                  # UI primitives shared by Arcile and plugin APKs
│   ├── plugin-glb/                              # Independently versioned GLB Viewer application APK
│   └── feature/                                 # Feature Gradle modules with isolated ViewModels and screens
│       ├── archive/                             # ZIP/7z creation, password prompt, extraction UX
│       ├── browser/                             # File browser layout, selection bar, clipboard, and file lists
│       ├── imagegallery/                        # Image gallery photos/albums, viewer, favorites, and metadata
│       ├── onboarding/                          # First-run setup and permission guidance
│       ├── quickaccess/                         # Pinned folders, SAF handoffs, and folder shortcuts
│       ├── recentfiles/                         # Scoped recent files timeline and visual carousel
│       ├── storagecleaner/                      # Cleanup scanner and review workflow
│       ├── storageusage/                        # Storage dashboard and usage-map UI
│       └── trash/                               # Volume-scoped trash listings, restore workflows, and properties
├── docs/                                        # Promotional landing page website
├── beta/                                        # Beta phase archived changelog & releases
│   ├── CHANGELOG-BETA.md                        # Archived beta changelog
│   └── RELEASES-BETA.md                         # Archived beta release notes
├── CHANGELOG.md                                 # Stable release changelog
├── DEVELOPMENT.md                               # Architecture & development guide
├── Releases.md                                  # Stable user-facing release notes
├── TASKS.md                                     # Roadmap, tracker of issues and features
└── README.md                                    # Main entry point overview
```

---

## Testing

Run from the Gradle root (`arcile-app/`). These commands cover all included modules:

```bash
# Full local suite across all modules: unit/Robolectric tests, lint, and verification checks
./gradlew check

# Full device/emulator instrumented suite across all modules
./gradlew connectedCheck

# Full local + device/emulator verification
./gradlew check connectedCheck

# Release Arcile and the optional GLB plugin
./gradlew :app:assembleRelease :plugin-glb:assembleRelease

# Production string guard across production sources
./gradlew checkProductionStrings
```

Use `./gradlew check` for normal pre-commit verification. Use `./gradlew check connectedCheck` when a device or emulator is attached and you want the entire test suite, including instrumented Android tests.

The suite includes JVM/Robolectric tests, Compose UI tests, instrumented Android tests, architecture checks, lint, and release convention checks.

---

## Documentation

| Document | Description |
|----------|-------------|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Architecture, storage model, testing, conventions, and maintenance notes |
| [CHANGELOG.md](CHANGELOG.md) | Stable version history and release notes |
| [beta/CHANGELOG-BETA.md](beta/CHANGELOG-BETA.md) | Archived version history from the beta phase |
| [beta/RELEASES-BETA.md](beta/RELEASES-BETA.md) | Archived release notes from the beta phase |
| [TASKS.md](TASKS.md) | Audit findings, planned features, and known issues |
| [PRIVACY.md](PRIVACY.md) | Privacy policy |
| [LICENSE.md](LICENSE.md) | License terms and attribution |

---

## License

**Tremors Source License (TSL)** - source-available license allowing viewing, forking, and derivative works with **mandatory attribution**. Commercial use requires written permission.

Web Version: [qtremors.github.io/license](https://qtremors.github.io/license)

See [LICENSE.md](LICENSE.md) for full terms.

---

<p align="center">
  Made by <a href="https://github.com/qtremors">Tremors</a>
</p>
