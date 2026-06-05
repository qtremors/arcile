<p align="center">
  <img src="assets/Arcile.png" alt="Arcile Logo" width="120"/>
</p>

<h1 align="center"><a href="https://qtremors.github.io/arcile/">Arcile</a></h1>

<p align="center">
  A private, source-available Android file manager built with Kotlin, Jetpack Compose, and Material 3 Expressive.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0-blueviolet" alt="Version">
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose_BOM-2026.05.00-4285F4?logo=jetpackcompose" alt="Compose BOM">
  <img src="https://img.shields.io/badge/Min_SDK-30-34A853?logo=android" alt="Android">
  <img src="https://img.shields.io/badge/License-TSL-red" alt="License">
</p>

> [!IMPORTANT]
> **Beta Status** Arcile is currently in **Beta**. Development is active, but release cadence depends on available weekend time.

> [!NOTE]
> **Privacy Model** Arcile does not request `android.permission.INTERNET`. File management, indexing, archive handling, thumbnails, and preferences are designed to stay local to the device.

---

## Features

| Feature | Description |
|---------|-------------|
| **Multi-Volume File Browser** | Browse and manage internal storage, SD cards, and USB OTG volumes with scoped storage labels and user-controlled volume classification. |
| **Home Dashboard** | Volume-aware storage summaries, category shortcuts, quick access entries, and a Material 3 Expressive recent-files carousel. |
| **Storage Dashboard** | Volume/category storage breakdown, Trash usage visibility, and a Filelight-inspired radial folder usage map (Usage Map) with breadcrumb drill-in. |
| **Quick Access** | Pin local folders, custom folders, and external handoff targets such as Android/data and Android/obb. |
| **Recent Files** | Browse recents with scoped volume support, date grouping, search, filters, list/grid controls, thumbnails, selection, properties, and containing-folder jumps. |
| **Storage Cleaner** | Early-access scanner for large files, duplicate-name candidate groups (segmented duplicate cards with details), APKs, downloads, videos, and conservative cache/junk cleanup routed through Trash. |
| **Archive Workflows** | Create, browse, and extract ZIP and 7z archives, including password-protected ZIP/7z flows and safe extraction path checks. |
| **Foreground File Operations** | Copy, move, archive, extract, fake-file generation, Trash, and delete flows run through foreground operation plumbing with progress events and a lightweight operation journal. |
| **Conflict Resolution** | Smart paste detects top-level name collisions, compares metadata for conflicting files, and supports replace, keep both, skip, and batch resolution choices. |
| **Trash Subsystem** | Permanent volumes use `.arcile/.trash` plus schema-versioned metadata sidecars for restore, recovered payloads, filters, sorting, properties, and undo where possible; temporary OTG-style volumes route deletions permanently. |
| **Selection Properties** | Single and multi-select metadata includes paths, counts, sizes, hidden-item counts, MIME/extension details, and folder aggregate access status. |
| **Search & Filters** | MediaStore-backed search and category browsing support type, size, date, extension, hidden-file, volume, folder-scope, MIME, and saved-preset metadata filters across relevant scopes. |
| **Material You Theming** | Dynamic wallpaper colors, MaterialKolor custom accents, color harmonization, light/dark/OLED modes, haptic tactility (with global toggle), filename display controls, and thumbnail controls. |
| **First-Run Onboarding** | Guided setup for features, theme/accent choice, All Files Access, and Android 13+ notification permission context. |
| **Safe Open/Share** | Outbound file access is centralized through allowlisted staging, cache cleanup controls, batch guards, MIME-aware grouping, and `FileProvider` handoff paths. |
| **Offline & Ad-Free** | No internet permission, no ads, no telemetry, and no tracker dependency. |

---

## Quick Start

Download the latest APK from [GitHub Releases](https://github.com/qtremors/arcile/releases) and install it on your Android device.

> **Runtime permission:** Arcile requires Android 11+ **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) for full filesystem management. Android 13+ notification permission is requested during onboarding for foreground operation updates.

### Release Signing

Release builds read signing credentials from `signing.properties` first, then `local.properties` as a fallback. Neither file should be committed.

```properties
signing.storeFile=/absolute/path/to/my-release-key.jks
signing.storePassword=your_store_password
signing.keyAlias=your_key_alias
signing.keyPassword=your_key_password
```

Build the release APK:

```bash
./gradlew assembleRelease
```

Release builds enable R8 minification and resource shrinking.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.2.10 |
| **Android Gradle Plugin** | 9.1.1 |
| **UI** | Jetpack Compose BOM 2026.05.00, Material 3 1.5.0-alpha19, Material 3 Adaptive |
| **Architecture** | Modular MVVM with Gradle boundaries, feature-scoped ViewModels, StateFlow, and Hilt DI |
| **Navigation** | Navigation Compose with `kotlinx.serialization` typed routes |
| **Storage** | `java.io.File`, `StatFs`, MediaStore, FileProvider, foreground service operations |
| **Persistence** | DataStore Preferences for theme, browser presentation, storage classification, quick access, and onboarding |
| **Media** | Coil with custom APK icon, audio album art, PDF, and video thumbnail fetchers |
| **Archives** | Apache Commons Compress, Tukaani XZ, and Zip4j |
| **Min / Target / Compile SDK** | 30 / 36 / 37 |

---

## Project Structure

```text
arcile/
├── arcile-app/
│   ├── app/                                     # App entry point, Hilt composition, and shell UI
│   │   ├── src/main/java/dev/qtremors/arcile/
│   │   │   ├── ArcileApp.kt                     # Hilt application startup & image loader
│   │   │   ├── MainActivity.kt                  # App activity, splash, and main layout navigation shell
│   │   │   ├── navigation/                      # Serializable typed routes & navigation graph
│   │   │   └── di/                              # Dagger Hilt dependency injection modules
│   ├── core/                                    # Shared business logic and UI frameworks
│   │   ├── runtime/                             # Dispatcher injection, app logger, and common helpers
│   │   ├── ui/                                  # Common UI design tokens, theme, haptics, and reusable Compose nodes
│   │   ├── operation/                           # Foreground services and operation journal tracking
│   │   │   ├── api/                             # Task progress events and operations interfaces
│   │   │   └── src/                             # Concrete operation coordinator and background service
│   │   └── storage/                             # File system data orchestrator
│   │       ├── domain/                          # Domain models, volume references, and repository interfaces
│   │       └── data/                            # FileSystem, MediaStore client, volume discovery, and transfers
│   └── feature/                                 # Feature Gradle modules with isolated ViewModels and screens
│       ├── archive/                             # ZIP/7z creation, password prompt, extraction UX
│       ├── browser/                             # File browser layout, selection bar, clipboard, and file lists
│       ├── onboarding/                          # First-run setup and permission guidance
│       ├── quickaccess/                         # Pinned folders, SAF handoffs, and folder shortcuts
│       ├── recentfiles/                         # Scoped recent files timeline and visual carousel
│       ├── storagecleaner/                      # Cleanup scanner and review workflow
│       ├── storageusage/                        # Storage dashboard and usage-map UI
│       └── trash/                               # Volume-scoped trash listings, restore workflows, and properties
├── docs/                                        # Promotional landing page website
├── CHANGELOG.md                                 # Release logs and history
├── DEVELOPMENT.md                               # Architecture & development guide
├── Releases.md                                  # Structured release notes
├── TASKS.md                                     # Roadmap, tracker of issues and features
└── README.md                                    # Main entry point overview
```

---

## Testing

Run from `arcile-app/`:

```bash
# JVM unit + Robolectric tests
./gradlew :app:testDebugUnitTest

# Production string guard for audited composables
./gradlew :app:checkProductionStrings

# Instrumented tests, requires device/emulator
./gradlew :app:connectedDebugAndroidTest
```

Current test surface:

| Area | Coverage |
|------|----------|
| **JVM/Robolectric** | 74 Kotlin test files across data, domain, image, navigation, presentation, UI, operations, and utilities |
| **Instrumented** | 3 Android test files for Home, Quick Access, and shared empty-state rendering |
| **Approximate test declarations** | 641 `@Test`/test-style function hits |
| **Key helpers** | `FakeFileRepository`, `FakeBulkFileOperationCoordinator`, `FakeBrowserPreferencesStore`, `MainDispatcherRule`, `ArcileTestTheme`, `TestFixtures` |

---

## Documentation

| Document | Description |
|----------|-------------|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Architecture, storage model, testing, conventions, and maintenance notes |
| [CHANGELOG.md](CHANGELOG.md) | Version history and release notes |
| [TASKS.md](TASKS.md) | Audit findings, planned features, and known issues |
| [PRIVACY.md](PRIVACY.md) | Privacy policy |
| [LICENSE.md](LICENSE.md) | License terms and attribution |

---

## License

**Tremors Source License (TSL)** - source-available license allowing viewing, forking, and derivative works with **mandatory attribution**. Commercial use requires written permission.

Web Version: [github.com/qtremors/license](https://github.com/qtremors/license)

See [LICENSE.md](LICENSE.md) for full terms.

---

<p align="center">
  Made by <a href="https://github.com/qtremors">Tremors</a>
</p>
