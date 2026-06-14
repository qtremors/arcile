<p align="center">
  <img src="assets/Arcile.png" alt="Arcile Logo" width="120"/>
</p>

<h1 align="center"><a href="https://qtremors.github.io/arcile/">Arcile</a></h1>

<p align="center">
  A private, source-available Android file manager for local-first storage, safe file operations, archives, and Material 3 Expressive polish.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.1.0-blueviolet" alt="Version">
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose_BOM-2026.05.00-4285F4?logo=jetpackcompose" alt="Compose BOM">
  <img src="https://img.shields.io/badge/Min_SDK-30-34A853?logo=android" alt="Android">
  <img src="https://img.shields.io/badge/License-TSL-red" alt="License">
</p>

> [!NOTE]
> **Privacy Model** Arcile does not request `android.permission.INTERNET`. File management, indexing, archive handling, thumbnails, and preferences are designed to stay local to the device.

---

## Why Arcile

Arcile is built for people who want a modern Android file manager without ads, telemetry, or network access. Version 1.1.0 builds on the first stable foundation with a stronger Room-backed cache layer, a richer Storage Cleaner, and a more immersive Gallery and Media Viewer while preserving the local-first storage model.

The 1.1.0 release consolidates the v1.0.x update cycle after the first stable release. It brings exact duplicate verification, custom cleaner rules, cached storage snapshots, gesture-driven media viewing, gallery timeline improvements, persistent page memory, and broader cache invalidation hardening across the core browser, Home dashboard, recents, quick access, storage usage tools, cleaner, archive workflows, recovery plumbing, and modular Kotlin/Compose architecture.

---

## Features

| Feature | Description |
|---------|-------------|
| **Multi-Volume File Browser** | Browse and manage internal storage, SD cards, and USB OTG volumes with scoped storage labels and user-controlled volume classification. |
| **Home Dashboard** | Volume-aware storage summaries, category shortcuts, quick access entries, and a Material 3 Expressive recent-files carousel. |
| **Storage Dashboard** | Volume/category storage breakdown, Trash usage visibility, and a Filelight-inspired radial folder usage map (Usage Map) with breadcrumb drill-in. |
| **Quick Access** | Pin local folders, custom folders, and external handoff targets such as Android/data and Android/obb. |
| **Recent Files** | Browse recents with scoped volume support, date grouping, search, filters, list/grid controls, thumbnails, selection, properties, and containing-folder jumps. |
| **Storage Cleaner** | Scanner for large files, exact duplicate groups, APKs, downloads, videos, marker files, empty folders, ignored items, and conservative cache/junk cleanup routed through Trash. |
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

From `arcile-app/`, build the release APK:

```bash
./gradlew assembleRelease
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
| **Storage** | `java.io.File`, `StatFs`, MediaStore, FileProvider, foreground service operations |
| **Persistence** | Room cache database plus DataStore Preferences for theme, browser presentation, storage classification, quick access, cleaner rules, and onboarding |
| **Media** | Coil with custom APK icon, audio album art, PDF, and video thumbnail fetchers |
| **Archives** | Apache Commons Compress, Tukaani XZ, and Zip4j |
| **Min / Target / Compile SDK** | 30 / 37 / 37 |

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

Run from `arcile-app/`:

```bash
# Full local suite: all module unit/Robolectric tests, lint, and verification checks
./gradlew check

# Entire suite including device/emulator instrumented tests
./gradlew check connectedCheck

# App-module JVM unit + Robolectric tests only
./gradlew :app:testDebugUnitTest

# Production string guard for audited composables
./gradlew checkProductionStrings

# App-module instrumented tests, requires device/emulator
./gradlew :app:connectedDebugAndroidTest
```

Use `./gradlew check` for normal pre-commit verification. Use `./gradlew check connectedCheck` when a device or emulator is attached and you want the entire test suite, including instrumented Android tests.

Current test surface:

| Area | Coverage |
|------|----------|
| **JVM/Robolectric** | 102 Kotlin test files across data, domain, image, navigation, presentation, UI, operations, and utilities |
| **Instrumented** | 3 Android test files for Home, Quick Access, and shared empty-state rendering |
| **Approximate test declarations** | 536 `@Test` annotations |
| **Key helpers** | `FakeFileRepository`, `FakeBulkFileOperationCoordinator`, `FakeBrowserPreferencesStore`, `MainDispatcherRule`, `ArcileTestTheme`, `TestFixtures` |

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
