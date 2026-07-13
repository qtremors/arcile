# Arcile - Developer Documentation

> Architecture, implementation notes, conventions, and verification guidance for Arcile development.

**Version:** 1.5.0 | **Last Updated:** 2026-07-13
**Scope:** Internal development, storage architecture, UI paradigms, testing, and release maintenance.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Runtime Flow](#runtime-flow)
- [Core Concepts](#core-concepts)
- [Navigation & State](#navigation--state)
- [Storage & File Operations](#storage--file-operations)
- [Room Cache Database](#room-cache-database)
- [Archive System](#archive-system)
- [Trash System](#trash-system)
- [Plugin System](#plugin-system)
- [UI & Design System](#ui--design-system)
- [Feature Modules Deep Dive](#feature-modules-deep-dive)
- [Naming Conventions](#naming-conventions)
- [Configuration](#configuration)
- [Security & Privacy Practices](#security--privacy-practices)
- [Error Handling](#error-handling)
- [Testing Suite](#testing-suite)
- [Build & Release Engineering](#build--release-engineering)
- [Intended Changes & Anomalies](#intended-changes--anomalies)
- [Project Auditing & Quality Standards](#project-auditing--quality-standards)
- [Troubleshooting](#troubleshooting)
- [Maintenance Notes](#maintenance-notes)
- [Feedback](#feedback)

---

## Architecture Overview

Arcile is a **modular multi-module Android app** with strict Gradle-enforced architecture boundaries. It is designed with clean MVVM, feature-scoped ViewModels, Hilt dependency injection, StateFlow-backed UI state, and data-source encapsulation.

```mermaid
graph TD
    A["Compose UI<br/>Routes + Screens"] -->|typed intents| B["Feature ViewModels<br/>StateFlow + controllers"]
    B -->|use cases / repository calls| C["Focused Domain Contracts"]
    C -->|implemented by| D["Focused Storage Repositories"]
    D --> E["VolumeProvider"]
    D --> F["MediaStoreClient"]
    D --> G["DefaultFileSystemDataSource"]
    D --> H["TrashManager"]
    D --> I["ArchiveManager"]
    D --> K["ExternalFileAccessHelper"]
    G --> J["FileTransferEngine + Conflict Detector"]
    D --> L["FolderStatsStore"]
    D --> M["MutationFinalizer"]
```

### Key Architectural Decisions

| Decision | Rationale |
|----------|-----------|
| **Multi-module Gradle Architecture** | Enforces clean boundaries between features and core services, isolates compilation units, speeds up incremental builds, and prevents architectural degradation as features expand. |
| **Feature-owned Routes** | Every stateful product area creates its own ViewModel, collects lifecycle-aware state, owns platform launchers, and emits neutral destination events. The app shell only maps those events to root routes. |
| **Focused Storage Capabilities** | Features inject only listing, mutation, search, analytics, trash, archive, volume, or clipboard contracts that they actually use. |
| **Typed Navigation** | `AppRoutes.kt` uses `kotlinx.serialization` route objects instead of raw route strings to verify routing correctness at compile time. |
| **Offline-first Privacy** | The manifest does not request `android.permission.INTERNET`; app behavior is local-only by design. |
| **Foreground Operation Pipeline** | Long-running copy, move, archive, extract, and fake-file work runs through a dedicated foreground service and emits progress updates. |
| **Room Database Caching** | Cache metadata, aggregate folder sizes, categories, and thumbnail listings are persisted in a local Room database to prevent repeated, expensive disk scans. |
| **Intent-based Plugins** | Optional heavyweight viewers run as separately installed, same-signer APKs and communicate through versioned explicit intents and temporary read-only content URI grants. |

---

## Project Structure

Arcile's codebase is divided into **28 Gradle modules** split into application composition, neutral core capabilities, independent feature modules, and optional plugin infrastructure:

```text
arcile/
├── arcile-app/
│   ├── build-logic/                             # Convention and release-verification plugins
│   ├── app/                                     # Activities, Hilt composition, root shell, route mapping
│   │   └── src/main/java/dev/qtremors/arcile/
│   │       ├── AppSessionTracker.kt              # Cold-launch vs configuration recreation ownership
│   │       ├── MainActivity.kt                   # Splash preload and app-shell entry
│   │       └── presentation/ui/                  # Root graph, shell coordinator, destination mappers
│   ├── core/
│   │   ├── navigation/api/                      # Serializable app route contracts
│   │   ├── operation/
│   │   │   ├── api/                             # Requests, progress, events, recovery, coordinator contract
│   │   │   └── android/                         # Foreground service, journal, importer, coordinator
│   │   ├── plugin/android/                      # Plugin discovery and compatibility validation
│   │   ├── presentation/                        # Shared reducers, search, clipboard, properties, UI text
│   │   ├── runtime/                             # Dispatcher injection, logging, authorization gateway
│   │   ├── storage/
│   │   │   ├── domain/                          # Focused storage contracts, models, path helpers
│   │   │   └── data/                            # Focused repositories, Room, MediaStore, filesystem engines
│   │   ├── testing/                             # Shared repository and operation fakes
│   │   └── ui/
│   │       ├── testing/                         # Shared Compose test infrastructure
│   │       └── src/main/                        # Design system, external-file and metadata adapters
│   ├── feature/
│   │   ├── activitylog/                         # Operation history
│   │   ├── archive/                             # Archive viewer and extraction workflows
│   │   ├── browser/                             # Browser route, state slices, controllers, file UI
│   │   ├── home/                                # Home dashboard and shortcuts
│   │   ├── imagegallery/                        # Independent Gallery and Viewer routes
│   │   ├── import/                              # Save-to-Arcile share target
│   │   ├── onboarding/                          # First-run setup and permission guidance
│   │   ├── plugins/                             # Installed/available plugin management UI
│   │   ├── quickaccess/                         # Local shortcuts and SAF bookmarks
│   │   ├── recentfiles/                         # Recent-file timeline and actions
│   │   ├── settings/                            # Preferences, backup, cache controls
│   │   ├── storagecleaner/                      # Cleaner scans and cleanup workflows
│   │   ├── storageusage/                        # Dashboard, management, sunburst map
│   │   └── trash/                               # Trash listing, restore, permanent deletion
│   ├── plugin-api/                              # Stable dependency-light intent contract
│   └── plugin-ui/                               # Stateless UI shared with plugin APKs
├── docs/                                        # Landing page website files
├── beta/                                        # Beta phase archived changelog & releases
├── CHANGELOG.md                                 # Stable release changelog
├── DEVELOPMENT.md                               # Architecture & development guide (This Document)
├── Releases.md                                  # Stable user-facing release notes
├── TASKS.md                                     # Roadmap, tracker of issues and features
└── README.md                                    # Main entry point overview
```

---

## Runtime Flow

1. **Launch Classification:** `AppSessionTracker` classifies the Activity creation as `ColdLauncher` or `ConfigurationRecreation`. A cold launcher start consumes Android-restored navigation behind a neutral surface, replaces the restored stack with `AppRoutes.Main(initialPage = 0)`, and reveals Home without flashing another feature.
2. **Splash Screen Preloading:** `MainActivity` installs the Android splash screen and preloads theme preferences and onboarding settings asynchronously from Jetpack DataStore (with a 2-second fallback timeout) to prevent UI flicker.
3. **Onboarding Gate:** If the user hasn't completed onboarding, `OnboardingScreen` guides them through welcome slides, All Files Access settings, and notification permissions (Android 13+).
   - *Auto-Detection:* If the app discovers permissions are already active, onboarding automatically completes.
   - *Manual Reset:* Users can reset onboarding in settings.
4. **App Shell Assembly:** Once permissions are granted, `ArcileAppShell` sets up the typed navigation graph and bottom navigation. Configuration recreation retains the restored route; warm resumes do not reset navigation.
5. **Main-Shell Coordination:** `MainShellCoordinator` owns the Home/Browser pager, explicit Browser entry, viewer return, and Browser initialization. Browser restores its persistent location only when Browser is entered and exposes explicit uninitialized/restoring/ready/failed UI state.
6. **Route-Order Back Stack:** Browser handoffs from Cleaner, Recent Files, Quick Access, Storage Dashboard, Archive Viewer, and similar detail routes keep the originating app route on the Navigation Compose back stack. Browser consumes local modal/search/selection and folder/archive history first, then lets the app route pop back in navigation order.

---

## Core Concepts

### Storage Scopes

`StorageScope` represents the query bounds for file retrieval, search, or storage calculations:

| Scope | Kotlin Model | Purpose |
|-------|--------------|---------|
| **All Storage** | `StorageScope.AllStorage` | All indexed permanent volumes. |
| **Volume Scoped** | `StorageScope.Volume(volumeId)` | A specific storage volume (e.g., Internal vs SD Card). |
| **Path Scoped** | `StorageScope.Path(volumeId, absolutePath)` | A concrete directory path within a volume. |
| **Category Scoped** | `StorageScope.Category(volumeId, category)` | A conceptual category (Images, Videos, Audio, Docs, Archives, APKs). |

### Volume Classification

Arcile classifies storage volumes as either permanent or temporary to determine deletion and analytics routing:

- **Permanent Storage:** Internal storage and SD cards. Surfaced in storage dashboard metrics, included in category analytics, and supported by the Trash system.
- **Temporary Storage:** USB OTG, external drives, and unclassified mounts. Excluded from global dashboard calculations by default. File deletion on temporary storage is routed to delete permanently, completely bypassing the trash folder.

*Configuration:* `StorageClassificationRepository` persists user volume classifications, and `VolumeProvider` loads them into runtime `StorageVolume` models.

---

## Navigation & State

Type-safe navigation is built with `kotlinx.serialization` on Jetpack Navigation Compose.

### Route Definitions (`AppRoutes.kt`)
The navigation endpoints are modeled as serializable classes/objects under the `AppRoutes` namespace:
- `AppRoutes.Main(initialPage, path, archivePath, category, volumeId, focusPath, restorePersistentLocation, seedInitialPathHistory)`
- `AppRoutes.Home`
- `AppRoutes.Explorer(path, category, volumeId, restorePersistentLocation)`
- `AppRoutes.Tools`
- `AppRoutes.ActivityLog`
- `AppRoutes.Settings`
- `AppRoutes.Plugins`
- `AppRoutes.Trash`
- `AppRoutes.RecentFiles(volumeId)`
- `AppRoutes.ImageGallery(volumeId)`
- `AppRoutes.ImageViewer(initialPath, albumPath, searchQuery, volumeId, returnToBrowserPage)`
- `AppRoutes.StorageDashboard(volumeId)`
- `AppRoutes.StorageCleaner`
- `AppRoutes.StorageManagement`
- `AppRoutes.QuickAccess`
- `AppRoutes.ArchiveViewer(archivePath)`
- `AppRoutes.About`
- `AppRoutes.Licenses`

Each navigation-hosted stateful feature registers its route through `NavGraphBuilder.registerXRoute(...)`. The route owns ViewModel creation, lifecycle-aware state collection, platform launchers, screen-state mapping, and callback wiring. Standalone entry points such as the Import share target keep the same ownership inside their feature Activity/route. Features emit typed destination contracts such as `RecentFilesDestination.ContainingFolder`; only the app shell translates those contracts into `AppRoutes`.

### Screen Transitions & Animation Rules
To maintain premium design aesthetics, Arcile implements customized bouncy spring transitions:
- **Detail Screens** (Dashboard, Settings, Archive Viewer): Slide horizontally with volumetric scale modifiers (`scaleIn` starting at 0.94f and `scaleOut` expanding to 1.04f).
- **Utility / Modals** (Trash, Recents, Gallery, Cleaner): Slide vertically from the bottom edge (`initialOffsetY = { it / 8 }`).
- **Reduced Motion Support:** All transitions check `LocalReducedMotionEnabled.current` and fall back to zero-duration crossfades if enabled in system or app settings.
- **Browser Handoff Back Rules:** Back from a Browser handoff first closes local Browser UI, then moves up folder/archive history, and only then returns to the previous app route. Volume-root fallback is reserved for Browser sessions without a previous app route so route sequences such as `a > b > c > d > e` unwind as `e > d > c > b > a`.

---

## Storage & File Operations

### Focused Storage Repositories
Storage is split into independently bound implementations for file browsing, mutation, media search and analytics, trash, archives, volumes, and clipboard state. Each implementation delegates only to the lower-level services required by its contract:

- `VolumeProvider`: Discovers mounted storage volumes, resolves pathways, and parses volume details.
- `MediaStoreClient`: Queries MediaStore for files, recent assets, categories, and folder statistics.
- `DefaultFileSystemDataSource`: Conducts direct JVM `java.io.File` listing and mutations behind storage contracts.
- `FolderStatsStore`: Manages async aggregate size calculations.
- `TrashManager`: Handles custom volume-scoped trash.
- `ArchiveManager`: Coordinates archive packaging and extraction.
- `ExternalFileAccessHelper`: Manages temporary handoffs and staging caches.
- `MutationFinalizer`: Updates media scanners and database tables post-mutation.

Feature composables and ViewModels must not inspect filesystem existence, writability, canonical paths, or `Dispatchers.IO` directly. Domain/data services own those decisions and expose typed results or presentation-ready state.

### Conflict Detection & Resolution Preflight
To prevent accidental data loss, file conflict resolution runs *before* operations execute:
1. `detectCopyConflicts` performs a high-speed top-level name comparison in the destination directory to identify collisions before starting heavy recursive jobs.
2. The user resolves conflicts in the `PasteConflictDialog` with options to **Replace**, **Keep Both**, or **Skip** (supports batch application).
3. `FileConflictNameGenerator` resolves Keep Both options into stable name modifications (e.g. `image (1).png`).

---

## Room Cache Database

To avoid repeated recursive file-system scans, Arcile uses a local Room database `ArcileDatabase` (file: `arcile-cache.db`, schema version 2) to cache directories and calculations.

Room schemas are exported under `core/storage/data/schemas/` and checked into source control. Cache schema changes are release events: every database version bump must include an updated schema JSON file, a migration or explicit cache-reset test from the previous version, and a changelog note explaining whether cached rows are preserved or intentionally invalidated. Destructive cache invalidation is acceptable for disposable cache data only when it is scoped to the known previous version with `fallbackToDestructiveMigrationFrom(...)`.

### Database Entities

#### FolderStatsEntity
Caches computed directory details (item counts and sizes).
- **Table Name:** `folder_stats`
- **Fields:**
  - `path: String` (PrimaryKey)
  - `file_count: Long`
  - `total_bytes: Long`
  - `cached_at: Long`
  - `status: String` (domain `FolderStatsStatus` mapping)

#### StorageNodeEntity
Caches directory contents for faster rendering and search.
- **Table Name:** `storage_nodes`
- **Indices:** `parent_path`, `volume_id`, `content_uri`, `media_store_id`, `last_modified`
- **Fields:**
  - `path: String` (PrimaryKey)
  - `parent_path: String?`
  - `name: String`
  - `extension: String`
  - `mime_type: String?`
  - `size_bytes: Long`
  - `last_modified: Long`
  - `is_directory: Boolean`
  - `is_hidden: Boolean`
  - `content_uri: String?`
  - `media_store_id: Long?`
  - `media_store_volume: String?`
  - `volume_id: String?`
  - `width: Int?`
  - `height: Int?`
  - `date_added: Long?`
  - `scanned_at: Long`
  - `stale: Boolean`

#### CategorySummaryEntity
Stores aggregated stats for file types across volumes.
- **Table Name:** `category_summaries`
- **Primary Keys:** `scope_key`, `category_name`
- **Fields:**
  - `scope_key: String`
  - `category_name: String`
  - `size_bytes: Long`
  - `item_count: Long`
  - `cached_at: Long`

#### ThumbnailEntryEntity
Tracks cached visual previews for images, video frames, and document pages.
- **Table Name:** `thumbnail_entries`
- **Indices:** `source`, `content_uri`, `last_modified`, `last_success_at`, `last_failure_at`
- **Fields:**
  - `identity_key: String` (PrimaryKey)
  - `source: String`
  - `extension: String`
  - `size_bytes: Long`
  - `last_modified: Long`
  - `content_uri: String?`
  - `type: String`
  - `last_success_at: Long?`
  - `last_failure_at: Long?`
  - `failure_count: Int`
  - `failure_message: String?`

#### ThumbnailVariantEntity
Tracks size variations of cached thumbnails for responsive UI loading.
- **Table Name:** `thumbnail_variants`
- **Foreign Key:** `identity_key` referencing `thumbnail_entries.identity_key` with `ON DELETE CASCADE`
- **Indices:** `identity_key`, `last_accessed_at`
- **Fields:**
  - `variant_key: String` (PrimaryKey)
  - `identity_key: String`
  - `size_bucket_px: Int`
  - `generated_at: Long`
  - `last_accessed_at: Long`

---

## Archive System

Arcile supports browsing, creating, and extracting multiple archive formats:
- **Supported Formats (`ArchiveFormat`):**
  - *Full Support (Browse + Create + Extract):* ZIP (with Zip4j AES password support), 7z (with password support), TAR, TAR.GZ (TGZ), TAR.BZ2 (TBZ2), and TAR.XZ (TXZ).
  - *Read-only Support (Browse + Extract):* GZIP (GZ), BZIP2 (BZ2), and XZ.
  - *Unsupported Format (Mapped but disabled):* RAR.
- **Archive Encoding (`ArchiveNameEncoding`):** Users and developers can configure custom filename character sets: UTF-8, CP437, Windows-1252, Shift JIS, GBK, and Big5.
- **Compression Levels (`ArchiveCompressionLevel`):** STORE (No compression), FAST, DEFAULT (Balanced), and MAXIMUM.
- **Metadata Inspection:** `getArchiveSummary` parses format configurations, entry counts, and encryption profiles without unpacking the archive.
- **Selective Listing:** `listArchiveEntries` lists contents page-by-page and supports directory traversal within the archive.
- **Extraction Safety:** The extraction pipeline validates every entry using `PathSafety.isArchiveEntrySafe` to block Zip Slip directory traversal attacks (absolute path escapes, `..` patterns).
- **Keep Both Naming:** If extraction targets already exist, they are auto-renamed using `FileConflictNameGenerator` to prevent accidental overwrites.
- **Pre-scan Limits:** Preflight scan limits before archive creation: `ARCHIVE_CREATION_PRE_SCAN_MAX_ENTRIES` (max 2048 entries) and `ARCHIVE_CREATION_PRE_SCAN_MAX_BYTES` (max 512MB).

---

## Trash System

Custom Trash is enforced on all permanent volumes:
- **Storage Layout:** Trashed files are moved to `.arcile/.trash` at the volume root, with original attributes and paths stored in JSON sidecars in `.arcile/.metadata`.
- **Media Scanner Protection:** A `.nomedia` file is created in trash folders to prevent trashed files from appearing in third-party gallery applications.
- **Restore Logic:** Reads metadata sidecars and moves files back to their original locations.
  - If the original parent directory was deleted, a `DestinationRequiredException` is thrown, prompting the user to select a new folder.
  - Conflicts are resolved using Keep Both renaming.
- **Undo Operations:** Trash actions can be immediately undone using snackbar options before the directory caches invalidate.

---

## Plugin System

Arcile plugins are independent Android application APKs. They are discovered and launched through Android intents; Arcile never loads plugin classes, resources, or native libraries into its own process.

### Modules and Ownership

| Module | Responsibility |
|--------|----------------|
| `plugin-api` | Stable contract constants, API version, MIME constants, and immutable plugin metadata/status models. It must not acquire runtime library dependencies. |
| `plugin-ui` | Stateless Compose viewer primitives that can be packaged into both Arcile and plugin APKs. It must not depend on app storage, feature modules, Hilt, or privileged file APIs. |
| `core:plugin:android` | Plugin discovery, signature/API validation, and installed-plugin compatibility checks. |
| `feature:plugins` | Installed/available plugin management UI and route ownership. |
| `app` | Root file routing, temporary URI creation, plugin prompt mapping, and application-level composition. |

The current contract version is `PluginContract.PLUGIN_API_VERSION = 1`.

### Intent Contract

Plugins expose an exported activity with the registration action:

```text
dev.qtremors.arcile.plugin.REGISTER
```

Arcile launches the discovered activity explicitly with:

```text
Action: dev.qtremors.arcile.plugin.VIEW_FILE
Data: content:// URI
Type: resolved MIME type
Flag: Intent.FLAG_GRANT_READ_URI_PERMISSION
ClipData: the same content URI
```

The following namespaced extras accompany the data URI:

| Extra | Type | Meaning |
|-------|------|---------|
| `dev.qtremors.arcile.plugin.extra.FILE_URI` | `Uri` | Read-only file URI; intent data remains the primary source. |
| `dev.qtremors.arcile.plugin.extra.FILE_NAME` | `String` | User-facing display name. |
| `dev.qtremors.arcile.plugin.extra.MIME_TYPE` | `String` | Normalized MIME type. |

Registration metadata:

| Key | Format |
|-----|--------|
| `dev.qtremors.arcile.plugin.API_VERSION` | Integer plugin API version. |
| `dev.qtremors.arcile.plugin.NAME` | Display name or string resource. |
| `dev.qtremors.arcile.plugin.SUPPORTED_MIME_TYPES` | Lowercase comma-separated MIME types; type wildcards such as `image/*` are allowed. |
| `dev.qtremors.arcile.plugin.SUPPORTED_EXTENSIONS` | Lowercase comma-separated extensions without leading dots. |
| `dev.qtremors.arcile.plugin.HOMEPAGE` | Optional project or release URL. |

`PackageInfo.versionName` and `longVersionCode` are authoritative for the plugin version. Do not duplicate them in metadata.

### Discovery, Compatibility, and Security

1. Arcile declares the registration action under `<queries>` for Android 11+ package visibility.
2. `PluginManager` calls `queryIntentActivities()` with metadata enabled.
3. Disabled/non-exported activities, malformed metadata, and unsigned or differently signed packages are rejected.
4. A plugin is launchable only when its API version exactly matches the current contract.
5. Matching prefers exact MIME, then extension, then MIME wildcard; version code and package name provide deterministic tie-breaking.
6. File access is staged through Arcile's read-only `ExternalFileAccessProvider`. Plugins must accept `content://` URIs and must not request all-files access.

Arcile and every official plugin release must use the same signing certificate. Debug variants naturally use the shared debug certificate. Release signing reads `signing.properties`, then `local.properties`; absolute and app-relative keystore paths are supported.

### Creating a Plugin

1. Add an Android application module under `arcile-app/`, include it in `settings.gradle.kts`, and give it a unique application ID such as `dev.qtremors.arcile.plugin.stl`.
2. Depend on `:plugin-api`; add `:plugin-ui` only when the plugin uses Arcile viewer primitives.
3. Add one exported registration/viewer activity. A viewer intended only for Arcile must not declare `MAIN`, `LAUNCHER`, or a generic `ACTION_VIEW` filter.
4. Declare all registration metadata on that activity.
5. Validate the custom action, `content` URI scheme, MIME/extension, and readability before rendering.
6. Keep heavyweight rendering/codec dependencies inside the plugin module.
7. Add contract, request-validation, rendering-failure, and build tests.
8. Add the plugin to `PluginManager.catalog` only when Arcile should advertise installation while it is absent.

Minimal manifest registration:

```xml
<activity
    android:name=".ViewerActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="dev.qtremors.arcile.plugin.REGISTER" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data
        android:name="dev.qtremors.arcile.plugin.API_VERSION"
        android:value="1" />
    <meta-data
        android:name="dev.qtremors.arcile.plugin.NAME"
        android:value="@string/plugin_name" />
    <meta-data
        android:name="dev.qtremors.arcile.plugin.SUPPORTED_MIME_TYPES"
        android:value="model/stl" />
    <meta-data
        android:name="dev.qtremors.arcile.plugin.SUPPORTED_EXTENSIONS"
        android:value="stl" />
</activity>
```

Verify the generic plugin contract, Android discovery layer, and management UI:

```bash
./gradlew :plugin-api:testDebugUnitTest :core:plugin:android:testDebugUnitTest :feature:plugins:testDebugUnitTest
```

Plugin implementations are distributed separately from this repository. Before publishing a compatible plugin, install Arcile and the plugin release APK together, verify their certificate digests match with `apksigner verify --print-certs`, exercise missing/incompatible flows, open a real file, share/open-with it, and confirm Back returns to Arcile.

---

## UI & Design System

Arcile implements a high-end, premium design system built on **Material 3 Expressive** design tokens, custom spring physics, responsive gesture mechanics, and wallpaper-reactive accents.

### 1. Theme & Customization Engine (`Theme.kt`, `ThemeState`)
- **Theme Modes:**
  - `LIGHT`, `DARK`, `SYSTEM`: Dynamically updates layouts and system status/navigation bars via `WindowCompat`.
  - `OLED`: Pure-black overrides that set the background, surface, and container variants to `Color.Black` for maximum battery savings and deep-contrast aesthetics.
- **Visual Presets:**
  - `DRACULA` & `TOKYO_NIGHT`: Premium preconfigured developer color palettes, each featuring standard dark and OLED-specialized color maps.
  - `CUSTOM`: Allows the user to specify hexadecimal colors for primary and background tones. The engine dynamically calculates the contrast text color (`getContrastColor`) using a relative luminance formula: `luminance = (R * 0.299f) + (G * 0.587f) + (B * 0.114f)`. Backgrounds above `0.5f` luminance resolve to black text, others to white.
- **Accent Color Routing:**
  - *Dynamic Accent:* Blends with Android 12+ wallpaper dynamic palettes via `dynamicLightColorScheme(context)` and `dynamicDarkColorScheme(context)`.
  - *Monochrome / Monochrome OLED Scheme:* Clean greyscale layouts.
  - *Predefined Colors:* Blue, green, purple, orange, etc., dynamically built into a Material 3 palette via a helper seed builder.
- **Color Harmonization:**
  - When `harmonizeColors` is enabled in `ThemeState`, category-specific colors (e.g., folder item types) and semantic colors are blended with the primary color scheme using color harmonization to prevent visual clashes.
- **Composition Locals:**
  - `LocalCategoryColors` / `LocalSemanticColors`: Resolved color lists.
  - `LocalSpacing`: Access spacing coordinates.
  - `LocalHapticsEnabled` / `LocalHapticFeedback`: Tracks and respects user vibration settings.
  - `LocalDoubleLineFilenames` / `LocalMarqueeFilenames`: Inject configuration properties into file list rows.
  - `LocalReducedMotionEnabled`: Checks system accessibility flags to skip animations if requested by the OS.

### 2. Motion & Animation Tokens (`Motion.kt`, `AnimationTokens`)
- **Bouncy Spring Physics:** Standard Jetpack Compose spring animations are customized with under-damped bouncy physics (`dampingRatio = 0.75f`, `stiffness = Spring.StiffnessMediumLow`) to make press interactions feel tactile and alive.
- **Bouncy Card Press Actions (`bounceClickable`):** Interactive cards, cleaner category selectors, and grid cells use a custom press-to-scale modifier that shrinks the component slightly and plays a subtle haptic click (`HapticUtils`) on touch down and up.
- **Margin & Padding Safeguards:** Animated margins, paddings, or grid dimensions are coerced to be non-negative (e.g., `coerceAtLeast(0.dp)`) to prevent spring overshoots from sending layout coordinates below zero and triggering layout crashes.
- **Transition States:**
  - **Volumetric Scale:** Navigating to details scales screens using `scaleIn(initialScale = 0.94f)` and `scaleOut(targetScale = 1.04f)`.
  - **Predictive Back Navigation:** Core screens (Browser, Gallery, Recents, Trash, Archive Viewer) integrate with Android's progressive back gestures, scaling, translating, and fading the active layouts in real-time response to back swipes.

### 2. Custom Layout Components
- **Split Selection Toolbar (`SplitButtonGroup`):** Standardizes multiple primary bulk actions (e.g., Copy, Cut, Delete, Edit) into cohesive side-by-side button segments with a detached circular dropdown menu for secondary operations, floating freely above lists.
- **Expandable FAB Menu (`ExpandableFabMenu`):** An animated floating action button that rotates 45 degrees upon expansion, revealing secondary actions (New Folder, New File, Create Archive, etc.) styled as vertical circular items that fade and slide into place.
- **Glassmorphic Bottom Navigation (`ArcileNavigationBar`):** A floating bottom navigation container utilizing blurred translucent glass backdrops.
  - *Collapsible Labels:* To minimize clutter, inactive tabs collapse their text labels, displaying only the icon. The active tab expands to show both icon and title with a smooth spring-based width transition.
- **Draggable Fast Scrollbar (`FileList`, `FileGrid`):** A custom fast scroll track aligned with staggered grids and lists. As the user drags the scroll thumb, a floating date pill tooltip (displaying `Month Year`, e.g., `June 2026`) is projected nearby, updating dynamically as the scroll position transitions.
- **Shimmering Segmented Storage Bar (`StorageProgressBar`):** A custom progress bar on the Home screen that displays a shimmering loading animation on startup. When the filesystem stats resolve, the bar morphs into a segment-colored indicator representing space occupied by Images, Videos, Audio, Documents, Archives, APKs, and System files.

### 3. Gesture Physics & Interactive Components
- **Pinch-to-Resize Columns:** Photos and Albums grids in the Image Gallery feature real-time multi-touch pinch-to-resize gestures. Pinch-in and pinch-out gestures scale the layout's grid columns dynamically with spring feedback.
- **Elastic Viewer Dismissal (`ImageViewerScreen`):** The fullscreen media viewer is gesture-driven:
  - *Double-Tap Zoom:* Double-tapping on an image zoom-targets the tapped coordinate.
  - *Boundary-Resisting Pan:* Dragging an zoomed image past its bounds shows elastic drag resistance.
  - *Vertical Swipe-to-Dismiss:* Swiping down or up on an unzoomed image triggers an elastic dismiss animation, scaling and sliding the image while fading the black backdrop.
- **Viewer Thumbnail Strip:** The bottom thumbnail filmstrip uses stable absolute-path keys, fixed item layout dimensions, no thumbnail crossfade, immediate centered jumps for far-opened gallery items, and animated scrolling only for nearby page changes.
- **EXIF Slide-up Page Pager (`ImageViewerMetadata`):** The details panel wraps camera parameters and EXIF details inside a `VerticalPager` with scroll snapping. Swiping up on the viewer page slides the metadata panel upward, pushing the image above it. Swiping down slides the panel away.

### 4. UI/UX Rules & Guidelines for Developers
- **Hidden File Alpha:** Files or folders starting with a dot (`.`) are styled with lower opacity (typically `0.6f` alpha) and styled with italic text to distinguish them from standard files.
- **Scroll Preservation:** When returning from sub-screens (e.g., returning to the Browser from the Image Viewer, or to the Gallery from a photo), the list/grid scroll position (index and pixel offset) is preserved.
- **Gesture Blocking:** Categories or specific path-scoped navigations temporarily disable the main horizontal pager's swipe actions (`userScrollEnabled = false`) to prevent accidental transitions while browsing files.
- **App Risk Mapping:** In the Storage Cleaner, package names are mapped to user-friendly local application icons (e.g., resolving WhatsApp package to its recognizable icon) and risk badges, rather than listing raw package labels.

### 5. Material 3 Expressive APIs & Typography Guidelines
- **`ExperimentalMaterial3ExpressiveApi` Coverage:** This API tag is opted-in across features to access next-generation components, including:
  - `LoadingIndicator`: A vector-animated loading state spinner replacing the traditional circular progress indicators with premium motion curves.
  - `PullToRefreshBox` & `rememberPullToRefreshState`: Expressive gestures-based pull containers featuring customizable progress physics.
- **Expressive Motion Specs (`Motion.kt`):**
  - *Duration Scale:* Ranging from `Short1` (100ms), `Short2` (150ms), `Short3` (200ms), `Medium1` (250ms), `Medium2` (300ms), `Medium3` (350ms), `Medium4` (400ms), to `Long1` (450ms) through `Long4` (600ms).
  - *Easing Curves:* Maps Google's Expressive specs: `Emphasized` and `EmphasizedDecelerate` curves govern entrance transitions, while `EmphasizedAccelerate` controls exit transitions.
- **Semantic Typography (`Type.kt`):**
  Developers must use semantic text styles defined as extensions on the Material 3 `Typography` configuration to keep screens consistent:
  - `Typography.filename`: `titleMedium` styling with `Medium` weight and zero letter spacing, optimized for files and folders.
  - `Typography.fileMetadata`: `bodySmall` styling with normal weight, reserved for file sizes, timestamps, and extensions.
  - `Typography.pathBreadcrumb`: `labelLarge` styling with `Medium` weight, used in breadcrumb bars.
  - `Typography.storageMetric`: `headlineMedium` styling with `SemiBold` weight, for storage numbers.
  - `Typography.sectionHeader`: `titleSmall` styling with `Bold` weight, for screen headers and settings groups.
  - `Typography.dangerLabel`: `labelLarge` styling with `SemiBold` weight, indicating file deletion, junk counts, or risks.
  - *Bold/Semi-bold helpers:* `titleLargeBold`, `titleMediumBold`, `titleMediumSemiBold`, `titleSmallSemiBold`, `bodyLargeMedium`, `bodyMediumBold`, and `bodySmallMedium`.

---


## Feature Modules Deep Dive

### 1. Browser (`feature/browser`)
- **Entry:** `BrowserRoute` owns lifecycle collection, route callbacks, and platform interactions; `BrowserScreen` remains presentation-only.
- **State:** `BrowserUiState` composes focused navigation, listing, selection, search, operation, properties, archive, and undo state.
- **Controllers:** Navigation, selection, search, clipboard, conflicts, mutations, operations, reveal, archive, properties, and transient feedback have focused owners. Cross-domain transitions remain coordinated through the Browser owner rather than controllers mutating one another.

### 2. Home (`feature/home`)
- Owns dashboard state, recent-file presentation, category shortcuts, and neutral navigation destinations.

### 3. Gallery and Viewer (`feature/imagegallery`)
- `ImageGalleryViewModel` owns albums, timelines, filters, selection, and gallery presentation.
- `ImageViewerViewModel` independently owns the current viewer session, metadata panel, rotations, and chrome.
- `ImageGalleryFileActionController` owns reusable file actions. EXIF reads/writes and editability decisions stay behind the shared metadata adapter rather than feature composables.

### 4. Archive (`feature/archive`)
- Owns archive viewing, selection, password prompts, extraction presentation, and typed return-to-Browser destinations. Archive path creation and extraction destinations are resolved by storage-domain services.

### 5. Import (`feature/import`)
- `SaveToArcileActivity` handles `ACTION_SEND` and `ACTION_SEND_MULTIPLE`, while the route owns share parsing, destination navigation, save state, and feedback.
- Destination browsing revalidates storage-owned paths and distinguishes readable navigation from writable save destinations.

### 6. Storage Cleaner (`feature/storagecleaner`)
- Owns cleaner scans, duplicate comparison, app-risk presentation, exclusions, cleanup operations, and thumbnail-cache controls.

### 7. Storage Usage (`feature/storageusage`)
- Owns the storage dashboard, storage management, category/path destinations, and interactive sunburst usage map.

### 8. Trash (`feature/trash`)
- Owns trash listing, search, selection, restore, permanent deletion, empty-trash flows, and destination handling.

### 9. Recent Files (`feature/recentfiles`)
- Owns scoped recent-file loading, debounced search, selection/actions, properties, and `ContainingFolder` destinations without Browser dependencies.

### 10. Quick Access (`feature/quickaccess`)
- Owns local shortcuts, SAF bookmarks, restricted Documents-provider entries, and typed local/external destinations.

### 11. Settings (`feature/settings`)
- Owns preferences, backup/restore UI, navigation destinations, staging-cache statistics, and cache clearing.

### 12. Activity Log (`feature/activitylog`)
- Owns operation-history state and presentation independently of Browser and the app shell.

### 13. Plugins (`feature/plugins`)
- Owns installed/available plugin management UI; discovery and compatibility checks remain in `core:plugin:android`.

### 14. Onboarding (`feature/onboarding`)
- Owns first-run permission guidance, persisted completion state, and automatic completion when required access is already available.

---

## Naming Conventions

Arcile uses clear, descriptive names to ensure readability.

### Directory & File Names
- **Compose Screens:** PascalCase with `Screen` suffix (e.g. `TrashScreen.kt`).
- **Composables:** PascalCase without suffix (e.g. `ArcileTopBar.kt`).
- **ViewModels:** PascalCase with `ViewModel` suffix (e.g. `RecentFilesViewModel.kt`).
- **Repositories & Stores:** PascalCase with `Repository` or `Store` suffix (e.g. `FolderStatsStore.kt`).

### Method Signatures

| Prefix | Intent | Example |
|--------|--------|---------|
| `load` | Read state / data | `loadTrashItems()` |
| `navigate` | Transition screens | `navigateToFolder(path)` |
| `on` | Event callbacks | `onOpenFile` |
| `toggle` | Flip boolean state | `toggleSelection(file)` |
| `clear` | Reset variables | `clearClipboard()` |
| `create` | Build a resource | `createDirectory(name)` |
| `delete` | Remove a resource | `deletePermanently(paths)` |
| `get` | Fetch values | `getStorageInfo(scope)` |
| `format` | Convert data for display | `formatFileSize(bytes)` |
| `is` / `has` | Boolean checks | `isDirectory()`, `hasPermission()` |

---

## Configuration

### Compilation Metrics

| Attribute | Configuration Value |
|-----------|--------------------|
| **Namespace** | `dev.qtremors.arcile` |
| **Compile SDK** | 37 |
| **Target SDK** | 37 |
| **Min SDK** | 30 |
| **Version Code** | 150 |
| **Version Name** | `1.5.0` |
| **Java Target** | JVM 11 |
| **Kotlin Version** | 2.2.10 |
| **AGP Version** | 9.2.1 |
| **Compose BOM** | 2026.05.00 |

### Manifest Declarations

```xml
<!-- Storage Permissions -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
```

*Important:* Arcile does **not** request `android.permission.INTERNET` to guarantee user privacy.

---

## Security & Privacy Practices

1. **Path Traversal Protection:** All file inputs are validated using `PathSafety.isPathSafe` to block path traversal attacks.
2. **Safe Decompression:** `isArchiveEntrySafe` rejects absolute entries and relative directory escapes (`..`) during archive extraction.
3. **External Handoff Encapsulation:** `file_provider_paths.xml` and the app-owned external handoff provider restrict external access to the cache-backed `external_access/` staging root; local open/share grants are copied through `external_access/open/` or `external_access/share/`, and staged shares expose only display-name and size metadata.
4. **Staging Cache Lifecycle:** Staged handoff files are tracked in stats and deleted when the staging cache is cleared or after a configurable retention period.
5. **No Telemetry:** The app has no network access, preventing data leaks.
6. **Room Database Security:** Caches exclude user keys and access credentials.
7. **Scoped Deletions:** OTG/temporary storage deletes files permanently instead of moving them to trash folders.

---

## Error Handling

- **ViewModels:** Catch repository exceptions and display them to users via UI states, dialogs, or snackbars.
- **Repository APIs:** Return `Result<T>` wrappers with custom exceptions (`DestinationRequiredException`, `PasswordRequiredException`).
- **Cancellation Safety:** Coroutine blocks catch and rethrow `CancellationException` to ensure proper coroutine cancellation.

```kotlin
try {
    executeMutation()
} catch (e: Exception) {
    if (e is CancellationException) throw e
    Result.failure(e)
}
```

---

## Testing Suite

Arcile uses **JVM unit, Robolectric, architecture-boundary, build-logic, and instrumented UI tests**.

### Test Distribution

- **JVM Unit & Robolectric Tests:** Cover feature ViewModels/controllers, storage services, Room migrations, metadata, operation recovery, navigation restoration, and reusable presentation logic.
- **Architecture Boundary Tests:** Enforce dependency direction, package ownership, feature API visibility, production file/ViewModel size, composable parameter limits, and forbidden feature-side filesystem ownership.
- **Instrumented UI Tests:** Verify device-dependent layouts and platform interactions on an emulator or device.
- **Robolectric Configuration:** Compose-facing tests generally use SDK 35, while lower-level platform tests may use SDK 34. Both remain below compile SDK 37 where Robolectric support requires it.

### Verification Commands

```bash
# All JVM and Android unit/Robolectric tasks
./gradlew test testDebugUnitTest

# Release-oriented non-device verification
./gradlew :app:lintDebug checkProductionStrings :app:verifyArcileBuildConventions

# Architecture boundaries only
./gradlew :app:testDebugUnitTest --tests dev.qtremors.arcile.ArchitectureBoundaryTest

# Validate production string assets (checks for non-resource text)
./gradlew checkProductionStrings

# Run app-module instrumented UI tests
./gradlew :app:connectedDebugAndroidTest
```

During implementation, run only the affected module tests. Run architecture checks after changing dependencies, package ownership, public APIs, feature ViewModels, or production UI boundaries. Reserve the complete unit/Robolectric and lint passes for release milestones. Run `connectedCheck` only when an emulator or device is intentionally available; process-death behavior is covered by focused Robolectric/unit gates where possible but still requires final device validation.

### Per-Module Test Commands

Use module-scoped tasks when iterating on a focused area:

```bash
# App shell and integration-facing app tests
./gradlew :app:testDebugUnitTest

# Core modules
./gradlew :core:storage:data:testDebugUnitTest
./gradlew :core:storage:domain:test
./gradlew :core:ui:testDebugUnitTest
./gradlew :core:presentation:testDebugUnitTest
./gradlew :core:navigation:api:test
./gradlew :core:operation:android:testDebugUnitTest

# Feature modules
./gradlew :feature:browser:testDebugUnitTest
./gradlew :feature:imagegallery:testDebugUnitTest
./gradlew :feature:storagecleaner:testDebugUnitTest
./gradlew :feature:storageusage:testDebugUnitTest
./gradlew :feature:recentfiles:testDebugUnitTest
./gradlew :feature:archive:testDebugUnitTest
./gradlew :feature:trash:testDebugUnitTest
./gradlew :feature:onboarding:testDebugUnitTest
./gradlew :feature:quickaccess:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :feature:settings:testDebugUnitTest
./gradlew :feature:activitylog:testDebugUnitTest
./gradlew :feature:plugins:testDebugUnitTest
./gradlew :feature:import:testDebugUnitTest
./gradlew :plugin-api:testDebugUnitTest

# Instrumented tests for modules that define androidTest sources
./gradlew :app:connectedDebugAndroidTest
./gradlew :feature:quickaccess:connectedDebugAndroidTest
```

Pure Kotlin/JVM modules use `test`; Android modules use `testDebugUnitTest` for local unit/Robolectric tests and `connectedDebugAndroidTest` for instrumented tests.

---

## Build & Release Engineering

To package Arcile:

```bash
# Generate the Debug APK
./gradlew :app:assembleDebug

# Generate the signed, minified Release APK
./gradlew :app:assembleRelease
```

### APK Naming Standards
- **Arcile Debug:** `app/build/outputs/apk/debug/Arcile-1.5.0-debug.apk`
- **Arcile Release:** `app/build/outputs/apk/release/Arcile-1.5.0.apk`

---

## Intended Changes & Anomalies

| Aspect | Custom Implementation | Design Rationale |
|--------|-----------------------|------------------|
| **Public Trash Roots** | Trash directories reside at `/storage/emulated/0/.arcile/` instead of app-private folders. | Preserves trash items across app uninstall and reinstalls. |
| **OTG Deletion** | Removable USB OTG storage deletes files permanently. | Avoids creating hidden, persistent directories on removable drives. |
| **No Network Access** | The application does not declare the network permission. | Ensures user privacy by preventing files or metadata from being sent over the network. |

---

## Project Auditing & Quality Standards

When reviewing code changes, ensure:
1. **Scope Compliance:** Mutations must remain within target volume boundaries.
2. **Memory Efficiency:** Avoid loading large directories or image lists directly into memory; use paginated loaders or database cache lookups instead.
3. **Resource Management:** String literals should be defined in `strings.xml` to pass `checkProductionStrings` validation.
4. **Module Independence:** Features must not depend on other features; app code may consume only route registration and destination contracts from feature modules.
5. **State Ownership:** A controller mutates only its own private state. Cross-feature navigation uses typed destinations, and cross-controller transitions stay with the owning coordinator.
6. **Platform Boundaries:** Feature ViewModels must not use `Context`, `Dispatchers.IO`, concrete storage implementations, or direct filesystem policy. Feature composables must not inspect existence, permissions, traversal, or canonical paths.
7. **Hard Limits:** Production files remain at or below 500 lines, ViewModels at or below 400 lines, and public composables at or below 15 parameters. The architecture suite has no grandfathered size baselines.
8. **Focused Verification:** Run affected module tests while iterating; run architecture, lint, strings, and build-convention gates at release milestones.

---

## Troubleshooting

- **Onboarding loops:** Check DataStore preferences state loading in `MainActivity` (ensure the 2-second timeout is not tripped).
- **Missing files:** Run a MediaStore sync via `MutationFinalizer` after making file mutations.
- **Archive errors:** Verify password credentials and check for path safety violations.
- **Build configuration errors:** Ensure Android SDK 37 is installed via the Android SDK Manager.

---

## Maintenance Notes

- **Changelogs:** Update the current-version `CHANGELOG.md` for every user-visible change, keeping entries concise and omitting intermediate refactors.
- **Version alignment:** Bump versions only when explicitly requested. Keep project/build versions identical and derive the integer code by removing dots (`1.5.0` → `150`).
- **Architecture direction:** Treat the current boundaries as guardrails, not a reason for speculative abstraction. Prefer features, measurable improvements, and concrete bug fixes unless a guard exposes a real ownership problem.
- **Archive handlers:** Keep extraction path validation intact for all archive formats.

---

## Feedback

Arcile is a solo project. Forking for personal use is welcome under the license terms, but external code contributions via pull requests are not accepted at this time.

To report bugs, request features, or suggest improvements, please open an issue on the [GitHub issue tracker](https://github.com/qtremors/arcile/issues).

---

<p align="center">
  <a href="README.md">Back to README</a>
</p>
