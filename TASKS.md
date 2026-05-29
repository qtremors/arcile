# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.9.1
> **Last Updated:** 2026-05-29

---

### Bugs / Fixes From Field Notes

- [ ] **BUG-0038 - Refresh Storage Usage After Large Deletes** `[High]`
  - **Location:** `StorageUsageViewModel.kt`, `StorageUsageScanner.kt`, `FolderStatsStore.kt`, delete/trash operation finalization paths.
  - **Problem:** Storage size can remain stale after deleting a large chunk of files and may only correct itself after a force restart.
  - **Impact:** Users lose trust in cleanup results because Arcile reports old storage totals after a major delete operation.
  - **Fix:** Invalidate storage usage, folder stats, and affected volume summaries after permanent delete, trash, restore, move, and cleaner operations. Ensure operation completion events trigger a fresh scan or targeted recomputation without requiring app restart.
  - **Verification:** Add regression coverage for large delete/trash completion refreshing storage cards, storage dashboard totals, and folder aggregate sizes.

- [ ] **BUG-0039 - Support Case-Only Rename Operations** `[High]`
  - **Location:** `FileSystemDataSource.kt`, rename flow, conflict detection/name generation.
  - **Problem:** Renaming a file or folder by only changing letter casing can fail or be treated as a conflict, for example `file.txt` to `File.txt`.
  - **Impact:** Basic rename behavior feels broken on case-insensitive or case-preserving storage providers.
  - **Fix:** Detect case-only renames and route them through a safe temporary-name hop when required by the filesystem/provider. Make conflict detection compare normalized paths without blocking the selected item's own target.
  - **Verification:** Add unit tests for case-only file and folder renames, including mixed-case extensions and same-directory conflict checks.

- [ ] **BUG-0040 - Audit Properties Accuracy For Files And Folders** `[High]`
  - **Location:** `PropertiesUiModel.kt`, `PropertiesDialog.kt`, selection properties and folder stats code.
  - **Problem:** Properties should show complete, accurate details for every supported file and folder selection, including recursive folders and mixed selections.
  - **Impact:** Users may make cleanup or transfer decisions from incomplete metadata.
  - **Fix:** Review single-file, single-folder, multi-select, hidden-item, recursive-size, modified-date, type/extension, access-status, and limited-access cases. Surface partial/failed aggregate reads clearly instead of silently showing misleading totals.
  - **Verification:** Expand properties tests for files, folders, hidden entries, mixed selections, inaccessible descendants, and empty folders.

- [ ] **BUG-0041 - Standardize Toasts/Snackbars With Icons And Undo** `[High]`
  - **Location:** `ArcileSnackbarHost.kt`, browser/recent/trash/cleaner operation feedback paths, `DeleteFlowDelegate.kt`, operation coordinator events.
  - **Problem:** Toast/snackbar feedback is inconsistent, and undo is not available for every operation where it is technically safe.
  - **Impact:** Users cannot reliably understand what changed or quickly recover from mistakes.
  - **Fix:** Use one app-level feedback model with icons, success/error/warning states, action labels, and operation-aware undo hooks. Provide undo for rename, move, trash, restore, create folder/file, and cleaner actions where rollback is safe; clearly omit undo for irreversible permanent deletes.
  - **Verification:** Add UI/state tests for feedback icon selection, action labels, undo dispatch, and non-undoable operation messaging.

### Architecture

- [x] **ARCH-0042 - Consolidate Test Fixtures and Create `:core:testing` Module** `[High]`
  - **Location:** Gradle modules (`settings.gradle.kts`, module build scripts), test packages.
  - **Problem:** Core test fakes (`FakeFileRepository.kt`, `FakeBrowserPreferencesStore.kt`), test rules, and themes are duplicated across 7 separate modules, causing severe code redundancy and huge maintenance friction when the repository API shifts.
  - **Fix:** Create a new Gradle module `:core:testing`. Centralize all shared test fakes, fixtures, dispatcher rules, and themes into this module. Clean up the duplicate files in all other module test suites and declare `testImplementation(project(":core:testing"))` dependencies.
  - **Verification:** Run all tests across the app and modules via `./gradlew test` to ensure all tests compile and pass using the shared testing module.

- [x] **ARCH-0043 - Centralize Shared Strings and Eliminate XML Resource Duplication** `[Medium]`
  - **Location:** `strings.xml` in `:app`, `:core:ui`, `:feature:archive`, `:feature:browser`, `:feature:recentfiles`, and `:feature:trash`.
  - **Problem:** The entire 648-line `strings.xml` file containing all application strings is duplicated across 6 modules, requiring redundant manual synchronization and increasing merge conflict risks.
  - **Fix:** Centralize all shared string resources into `:core:ui` (or a dedicated `:core:resources` module) and remove the duplicate string resource files. Feature modules should only declare strings unique to their features.
  - **Verification:** Run `./gradlew checkDebugAndroidTest` / `./gradlew assembleDebug` and verify that all string resources resolve successfully and no keys collide.

- [ ] **ARCH-0044 - Decouple Core Domain from Compose Runtime Framework** `[Medium]`
  - **Location:** `:core:storage:domain` module, `FileModel.kt`, `ArchiveModels.kt`, `FolderStats.kt`, `ListingPage.kt`, `SelectionProperties.kt`, `StorageCleanerModels.kt`, `StorageNodeTypes.kt`, `StorageUsageModels.kt`.
  - **Problem:** The core storage domain module depends directly on the Jetpack Compose Runtime and BOM purely for the `@Immutable` annotation used on domain models, violating clean architecture boundary isolation.
  - **Fix:** Create a custom JVM-neutral `@Immutable` annotation in `:core:runtime` or remove Compose `@Immutable` from the domain models. Remove the Compose runtime and BOM dependencies from `:core:storage:domain/build.gradle.kts`.
  - **Verification:** Verify that `:core:storage:domain` builds successfully as a pure Kotlin/JVM library without any Compose or Android dependencies.

- [ ] **ARCH-0045 - Segment `FileRepository` Facade into Segregated ViewModel Dependencies** `[Medium]`
  - **Location:** ViewModels in `:app` and `:feature:*` (e.g. `TrashViewModel.kt`, `ArchiveViewerViewModel.kt`, `BrowserViewModel.kt`), `FileRepository.kt` facade.
  - **Problem:** All ViewModels depend directly on the fat `FileRepository` compatibility facade, coupling feature modules to the entire capabilities surface.
  - **Fix:** Update ViewModels to inject only the specific sub-interfaces they require (e.g. `TrashViewModel` should inject `TrashRepository`; `ArchiveViewerViewModel` should inject `ArchiveRepository`).
  - **Verification:** Compile the codebase and run unit tests for all ViewModels, ensuring correct behavior and narrower bindings.

- [ ] **ARCH-0046 - Decentralize Monolithic Dependency Injection Module** `[Low]`
  - **Location:** `RepositoryModule.kt` in `:app` module, core/data modules.
  - **Problem:** A single giant Hilt module `RepositoryModule.kt` under the `:app` module configures all core and data bindings, preventing other modules from declaring their own DI modules and slowing incremental builds.
  - **Fix:** Split `RepositoryModule.kt` into module-specific Hilt modules (e.g. define `StorageDataModule` inside `:core:storage:data`, `OperationModule` inside `:core:operation`, and `BrowserPrefsModule` in `:core:storage:data`).
  - **Verification:** Compile and run Hilt-dependent instrumentation tests to confirm all bindings resolve.

- [ ] **ARCH-0047 - Expand Architecture Boundary Test Coverage** `[Low]`
  - **Location:** `ArchitectureBoundaryTest.kt` in `:app` module test source set.
  - **Problem:** The architecture budget check (700 LOC) only scans a limited set of directories and ignores build files (`.gradle.kts`), configuration XMLs, and other core source folders.
  - **Fix:** Update `ArchitectureBoundaryTest.kt` to scan all modules (`core/*` and `feature/*`) for line size limits, include `.xml`, `.gradle.kts`, and `.gradle` files in the line budget check, and guard `:core:storage:domain` against importing any `androidx.compose` or `android` classes.
  - **Verification:** Run `ArchitectureBoundaryTest` to confirm it passes and fails appropriately if violations are introduced.

- [ ] **ARCH-0048 - Extract Remaining Features from `:app` Module** `[Low]`
  - **Location:** `:app` module (`dev.qtremors.arcile.feature.*`), settings.gradle.kts.
  - **Problem:** Features like Onboarding, Quick Access, Storage Cleaner, and Storage Usage still reside in the `:app` module, preventing the app module from being a pure shell.
  - **Fix:** Migrate each of these 4 features into their own isolated Gradle modules (e.g. `:feature:onboarding`, `:feature:quickaccess`, `:feature:storagecleaner`, `:feature:storageusage`).
  - **Verification:** Assemble and verify the app compiles and navigates correctly across the newly extracted modules.

---
## Backlog / Future Ideas

> A parking lot for future ideas, enhancements, and unprioritized Android file-manager features.
> Audited against the current codebase on 2026-05-26. Items marked "Expand existing" already have a shipped foundation and should be treated as polish or completion work, not greenfield features.

### Architecture & Foundation (Comparison Insights)
- **Persistent Operation Manager** `[Expand existing]`: `BulkFileOperationCoordinator`, foreground service execution, operation events, progress smoothing, and `OperationJournal` already exist. Extend this into a real queue with multiple pending operations, pause/resume/retry, restart recovery UI, and background notifications for copy, move, delete, rename, archive, extract, trash, and cleaner jobs.
- **Storage Provider Interfaces (VFS)** `[New]`: Decouple the data layer from hardcoded `java.io.File` and Android Scoped Storage APIs. Introduce a `StorageProvider` interface that returns standard domain `FileModel`s and handles `InputStream`/`OutputStream` streams. This lays the groundwork for SMB, FTP, SFTP, WebDAV, cloud, and richer SAF providers without modifying UI layers.
- **Native File Viewers** `[New]`: Integrate in-app viewers for Markdown, JSON, XML, TXT, logs, and code-like formats with encoding detection, syntax highlighting, line numbers, search, share, and copy actions.

### File Browsing & Navigation
- **Dual-Pane File Browser** `[New]`: Add tablet, foldable, and landscape browsing with two live folder panes for drag/copy/move workflows.
- **Breadcrumb Path Editing** `[New]`: Let users tap the current path and type or paste a filesystem path directly.
- **Folder Tabs With Restore** `[Expand existing]`: `FolderTabs.kt` currently builds folder grouping tabs for file lists. Extend this into user-managed open folder tabs with add/close/reorder and process-death restore.
- **Starred / Favorited Files** `[New]`: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Recent Locations** `[New]`: Track recently opened folders separately from recently modified files.
- **Quick Jump / Quick Access Expansion** `[Expand existing]`: Quick Access already supports standard folders, custom paths, SAF trees, pinned home items, and restricted-folder handoff. Expand it into a drawer or sheet with Download, DCIM, Documents, Android/media, Android/data, Android/obb, SD cards, OTG drives, network locations, recent locations, and pinned folders.
- **Native Android Files Shortcut** `[Expand existing]`: `QuickAccessPreferencesRepository`, `QuickAccessScreen`, and `ExternalFileAccessHelper.openInFilesApp` already hand off `Android/data` and `Android/obb` to DocumentsUI. Add a clearer trigger/shortcut from Home/Tools or a launcher shortcut that opens the system Files app directly.
- **Global Hidden Files Toggle** `[Partially exists]`: Search filters already support `includeHidden`, and hidden files have UI semantics. Add a persistent browser-level show/hide hidden files control that applies consistently across folder browsing, search, recents, trash, and picker surfaces.

### File Operations & Reliability
- **Queued Operation Manager** `[Expand existing]`: Operation service/coordinator/journal exist, but the app still needs a visible queue with pause, resume, cancel, retry, and persistent multi-operation state.
- **Conflict Resolution Dialogs** `[Partially exists]`: Conflict preflight/dialog code exists for paste flows. Expand choices to replace, skip, rename, apply-to-all, compare metadata, and keep-both across copy, move, and archive extraction.
- **Checksum Verification** `[New]`: Add MD5, SHA-1, and SHA-256 calculation plus optional post-copy verification for large transfers.
- **Batch Rename Tool** `[New]`: Support numbered patterns, date tokens, find/replace, regex and reverse-regex workflows, case conversion, extension changes, conflict detection, and a live before/after diff preview.
- **Undo Recent Operations** `[Expand existing]`: Trash undo already exists for browser trash moves. Extend safe undo to rename, move, restore, create folder/file, and cleaner moves where rollback is reliable.
- **Safe Large Transfer Mode** `[Partially exists]`: `FileTransferEngine` already uses staged transfer and metadata verification paths. Add user-facing warnings before large cross-volume moves and copy-then-delete fallback with verification.
- **Operation Logs Page** `[Expand existing]`: `OperationJournal` records active/interrupted operation state. Add a dedicated history page for completed, failed, cancelled, cleanup-required, toast/snackbar, undo, and cleaner events.

### Archives & File Formats
- **APK / AAB Inspector** `[Expand existing]`: APK icon thumbnails already exist. Add package name, version, signatures, permissions, split APK details, and install/open actions.
- **PDF / Document Preview Hooks** `[Expand existing]`: PDF thumbnails and safe external handoff exist. Add richer document metadata, thumbnail refresh, and safer handoff hints for PDFs, Office files, and ebooks.
- **Expanded Archive Format Support** `[Expand existing]`: ZIP and 7z create/list/extract, password support, archive viewer, safety checks, and keep-both extraction already exist. Add read-only RAR plus TAR, GZIP, BZIP2, and XZ support.
- **Advanced Archive Creation Options** `[Expand existing]`: Archive creation and password support exist. Add compression levels, archive splitting/multi-volume creation, and explicit encryption controls in the create dialog.
- **Archive Modification** `[New]`: Allow in-place editing of ZIP archives by adding/removing entries without full extraction.
- **In-Memory Archive Previews** `[New]`: Stream archive entries directly into image or text previewers within `ArchiveViewerScreen` without extracting to disk.
- **Archive Integrations** `[New]`: Register Share intent for creating ZIPs from external apps and add smart extraction rules such as auto-delete archive after extraction.

### Storage, Access & Android Integration
- **Storage Health Diagnostics** `[Expand existing]`: Storage volumes, classification, usage summaries, category sizes, and usage map already exist. Add mount state details, free-space trends, health checks, and repair/trim suggestions for mounted volumes.
- **Mount / Unmount Awareness** `[Partially exists]`: `HomeViewModel` observes storage volumes and refreshes summaries. Extend this to immediate browser/trash/operation recovery when SD card or USB OTG devices mount/unmount.
- **MediaStore Rescan Tools** `[New]`: Manually rescan selected files or folders so gallery, music, and downloads apps see changes sooner.
- **MTP / USB Transfer Mode Notes** `[New]`: Surface helpful guidance when Android's USB file transfer state blocks desktop access.

### Search, Filters & Organization
- **Indexed Search** `[New]`: Current search is repository/MediaStore-backed with local helper filtering. Add an optional local index for faster filename, extension, MIME type, size, date, tag, and folder searches.
- **Advanced Search Filters** `[Expand existing]`: `SearchFilters` already supports type, item type, size, date, extension, hidden, volume, folder scope, MIME, and saved-preset metadata. Add UI/storage for saved presets plus media duration, image dimensions, and duplicate candidates.
- **Tags And Tag Search** `[New]`: Let users assign Arcile-owned tags to files/folders and search or filter by one or more tags. Store tag metadata locally without requiring network access.
- **Saved Searches** `[Partially exists]`: `SearchFilters.savedPresetName` exists as metadata. Add persistence, management UI, and pinning reusable searches such as "large videos", "old APKs", or "recent downloads".
- **Duplicate Finder** `[Partially exists]`: Cleaner already groups duplicate candidates by name and size. Add a dedicated duplicate finder with size pre-filtering, optional content hashing, preview, exclusions, and safe delete/trash flow.
- **Large / Old Files Cleanup** `[Expand existing]`: Storage cleaner already has large files and old downloads groups. Expand with richer filters, folder exclusions, risk labels, and saved cleaner presets.
- **Empty Folder Finder** `[New]`: Scan and clean empty folders with preview and exclusions.
- **Smart Collections** `[Partially exists]`: Category browsing already groups common file types. Add smarter collections for screenshots, screen recordings, WhatsApp/Telegram media, APKs, downloads, documents, and other app/media-specific patterns.
- **Storage Analyzer ("Filelight" View)** `[Expand existing]`: `StorageUsageMap` already provides a radial usage-map view. Improve drill-down, labels, selection actions, legends, filtering, and large-folder performance.

### Media & Preview Experience
- **Rich Media Previews** `[Expand existing]`: Custom Coil fetchers already cover APK icons, audio album art, PDFs, and videos. Continue adding richer thumbnails/metadata for images, documents, ebooks, archives, and developer files.
- **Image Detail Sheet** `[New]`: Show resolution, EXIF, location presence, orientation, color profile, and quick rotate/share actions.
- **Video Detail Sheet** `[New]`: Show duration, resolution, codec, bitrate, frame rate, subtitles, and thumbnail refresh actions.
- **Audio Detail Sheet** `[Expand existing]`: Album art fetcher exists. Add artist, album, duration, bitrate, embedded-art status, and metadata cleanup hints.
- **Thumbnail Cache Controls** `[Partially exists]`: Global thumbnail visibility is already in settings, and external handoff cache cleanup exists. Add dedicated thumbnail cache clear, size-limit, rebuild, and failure-cache controls.
- **Thumbnail Cleaner** `[Partially exists]`: Folder stats and cleaner scanning already skip `.thumbnails`; add explicit thumbnail/cache detection with preview, exclusions, and risk labels for locations that are not recommended to remove.
- **Hidden Media Controls** `[New]`: Create or remove `.nomedia` files with clear warnings about gallery visibility.

### Automation & Power Tools
- **Automated Task Rules** `[New]`: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.
- **Watched Folders** `[New]`: Monitor selected folders for new files and offer rule actions such as rename, move, compress, or notify.
- **Quick Actions** `[New]`: Let users configure a small set of folder-specific actions like "send to SD", "compress here", or "clean old files".
- **Scheduled Cleanup** `[New]`: Periodically empty trash, remove temporary files, or prompt for old downloads review.
- **Custom Cleaner Rules** `[New]`: Let users define cleaner rules by folder, extension, size, age, filename pattern, and exclusions. Show preview results before any deletion.
- **Cleaner Recommendation Risk Labels** `[New]`: Mark risky cleanup candidates as not recommended or review-required, especially app-owned folders, thumbnail stores, and unknown cache-like directories.

### Sharing, Transfers & Network
- **Nearby Share / Share Sheet Polish** `[Expand existing]`: Share/open handoff is centralized through `ExternalFileAccessHelper` and `ShareHelper`. Improve multi-file target grouping, Nearby Share behavior, unsupported-target messaging, and post-share cleanup visibility.
- **Local HTTP File Drop** `[New]`: Temporarily host selected files or a folder over LAN with a QR code and one-tap shutdown.
- **WebDAV / SMB Client** `[New]`: Browse and transfer files from NAS, routers, and desktop shares.
- **FTP / SFTP Client** `[New]`: Add optional remote location support for advanced users.
- **Wi-Fi Direct Transfer** `[New]`: Explore device-to-device transfer without an external network.

### Security, Privacy & Safety
- **"OnlyFiles" Encrypted Vault** `[New]`: A secure, encrypted vault for sensitive files and folders using AES-256 encryption.
- **Secure Delete Option** `[New]`: Provide best-effort overwrite/delete workflows where storage type makes it meaningful, with honest limitations.
- **Private Folder Bookmarks** `[New]`: Hide selected pinned folders behind biometric confirmation.
- **Trash Privacy Audit** `[Expand existing]`: Trash already uses `.arcile/.trash`, metadata sidecars, `.nomedia`, restore, filters, sorting, properties, and permanent routing for temporary volumes. Review visibility to other apps and options for app-private trash on supported storage.
- **Sensitive Metadata Warnings** `[New]`: Warn before sharing images with location EXIF or documents with embedded author metadata.
- **App Lock** `[New]`: Add optional biometric/PIN lock for opening Arcile.

### Home Screen & UI Polish
- **Material 3 Expressive SplitButton Rollout** `[Expand existing]`: `SplitButtonGroup` exists and is used in Trash. Expand usage to browser, cleaner, archive, and selection actions where a main action needs secondary options.
- **Material 3 Expressive WavyProgressIndicator** `[New]`: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Progress FAB** `[New]`: Let the floating action button transform into a circular percentage/progress control during scans and long-running operations, with tap behavior for details or cancellation where appropriate.
- **Haptics & Interaction Quality** `[Expand existing]`: `rememberArcileHaptics` and global vibration settings already exist. Audit coverage for long-press, successful operations, errors, selection changes, and destructive confirmations.
- **Animated Empty States** `[Partially exists]`: Reusable `EmptyState` composables exist. Add motion/Lottie or lightweight animation for empty folders, trash, and search results.
- **Selection Mode Polish** `[Partially exists]`: Multi-select, select-all, invert-selection, and floating/toolbar actions exist. Improve selected-count affordances, drag selection, range selection, and action grouping.
- **One UI-Style Scroll Action Chips** `[New]`: Collapse or transform prominent actions into compact icon chips while scrolling so browser and home surfaces keep primary actions reachable without crowding content.
- **Customizable Home Screen** `[New]`: Let users reorder, hide, and restore Home sections such as storage cards, categories, quick access, recent files, starred files, and cleaner shortcuts.
- **Adaptive Bottom Actions** `[New]`: Keep high-frequency actions thumb-friendly on phones while preserving dense toolbars on tablets.
- **Shape Customization Toggle** `[New]`: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Settings, Personalization & Accessibility
- **File Icon Packs** `[New]`: Allow optional icon style packs for file types, folders, archives, media, and app packages.
- **Named Theme Presets** `[Expand existing]`: Theme modes, dynamic color, OLED, accents, harmonization, filename behavior, haptics, and thumbnail settings exist. Add named full-palette presets such as Dracula, Nord, Catppuccin, and high-contrast profiles.
- **Compact / Comfortable Density** `[Partially exists]`: Browser presentation already stores list zoom and grid min cell size. Add a user-facing density preset that applies across rows, grid cells, toolbars, dialogs, and dashboard sections.
- **Large Text Audit** `[New]`: Verify folder lists, dialogs, and bottom sheets at Android large-font accessibility settings.
- **High Contrast Theme** `[New]`: Add a contrast-focused theme profile beyond light, dark, dynamic, accent, and OLED modes.
- **Gesture Customization** `[New]`: Configure swipe actions, long-press behavior, and double-tap shortcuts.

### Multi-Window, Layout & Devices
- **Multi-Window / Split-Screen Support** `[New]`: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.
- **Foldable Layouts** `[New]`: Add hinge-aware navigation and dual-pane behavior for large foldable devices.
- **Adaptive Layout System** `[New]`: Define shared window-size classes and adaptive scaffolds for phones, tablets, foldables, landscape, desktop mode, and large external displays.
- **Tablet Navigation Rail** `[New]`: Use a rail or permanent navigation surface on wider screens.
- **Keyboard & Mouse Support** `[Partially exists]`: Pointer/scroll interactions exist in Compose lists, but desktop-grade keyboard shortcuts, hover states, context menus, and right-click behavior are still needed.
- **Gamepad / Remote Support** `[New]`: Add predictable D-pad focus order, remote-friendly actions, and gamepad navigation for large-screen and accessibility use cases.
- **Chromebook Polish** `[New]`: Verify resizable windows, external storage access, drag/drop, and system file picker interoperability.

### Developer, Build & Release
- **Developer Mode Toggle** `[New]`: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **Older Android Compatibility Audit** `[New]`: Verify behavior across Android 17+ and older supported versions, especially storage permissions, DocumentsUI handoff, MediaStore queries, notifications, and background operation behavior.
- **FOSS / Libre Build** `[New]`: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.
- **Internal Diagnostics Export** `[New]`: Export anonymized app state, settings, quick access, operation journal state, staging cache stats, storage classifications, and recent operation errors for bug reports.
- **Performance Benchmarks** `[New]`: Add repeatable benchmarks for large folder listing, recursive stats, thumbnail loading, storage cleaner scanning, storage usage map building, and search.
- **StrictMode Debug Profile** `[New]`: Enable stricter debug-only checks for disk I/O, leaked resources, and slow main-thread work.
