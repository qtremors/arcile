# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.8.0
> **Last Updated:** 2026-05-24

---

## Consolidated Tasks

### Visual System / Interaction Tasks

- [ ] **UI-0017 - Motion System** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** Motion is present but not systematic: route transitions slide horizontally, file grid press scales, FAB icon rotates, toolbars slide vertically, storage bars spring, thumbnails crossfade, and empty states bounce. These motions do not share duration, easing, hierarchy, or interruption rules.
  - **Impact:** The UI can feel animated but not choreographed. Premium apps feel calm because motion explains hierarchy.
  - **Fix:** Create `ArcileMotion` tokens for quick, standard, emphasized, container transform, list item placement, and destructive emphasis. Audit all `animate*`, `AnimatedVisibility`, `AnimatedContent`, `animateContentSize`, and `spring` calls. Define interruption behavior for file operations, selection, search, and folder navigation.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0024 - Navigation Motion** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt
  - **Problem:** All route transitions use the same horizontal slide/fade pattern regardless of destination type.
  - **Impact:** Settings, trash, recent files, storage dashboard, quick access, and archive viewer all move like peer pages, even when they are detail/supporting/modal-like destinations.
  - **Fix:** Define destination classes: top-level, detail, modal-ish utility, archive viewer. Use fade-through for settings/about/licenses, shared bounds for archive/category/file navigation, and standard predictive back for route pops. Respect reduced motion.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0043 - Premium Feel** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** Screen quality varies noticeably: Browser and Home are richer, while ArchiveViewer, Tools, Settings rows, About, Licenses, and some dialogs feel closer to template Material UI.
  - **Impact:** The app can feel like several design eras in one product.
  - **Fix:** Create reusable screen primitives: `ArcileScreenScaffold`, `ArcileSectionHeader`, `ArcileListSurface`, `ArcileActionSheet`, `ArcileStateView`. Convert lower-polish screens first: ArchiveViewer, Tools, Licenses, About, Settings. Add screenshot QA for compact/light/dark/OLED.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Architecture / Maintainability Tasks

- [ ] **COMPOSE-0011 - Compose Performance** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/StorageDashboardScreen.kt`
  - **Problem:** Several expensive sorts/groupings/transforms happen in composables or on every state-level change. Some are remembered, but state objects are broad so unrelated changes can still invalidate calculations.
  - **Impact:** Scrolling/searching/selection can feel less responsive in large datasets.
  - **Fix:** Move stable display lists/groupings into ViewModel state or dedicated memoized selectors. Use narrower immutable UI models. Add Compose compiler stability reports and macrobenchmarks. Recommended Refactor: Add `BrowserDisplayState` with already-filtered/sorted file lists and separate transient UI state from data state. Safer Alternative: Use `derivedStateOf` for scroll/progress-triggered derived values and tighten `remember` keys.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0034 - Compose Stability** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/BrowserViewModel.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/home/HomeViewModel.kt
  - **Problem:** Screen state classes pass standard `List`, `Set`, and `Map` collections through high-level composables. Compose treats standard collections as unstable unless wrapped/annotated/using immutable collections.
  - **Impact:** Large file lists and frequent folder stats updates may recompose more UI than necessary.
  - **Fix:** Run Compose compiler stability reports. Convert hot UI state collections to `kotlinx.collections.immutable` persistent collections or stable UI wrappers. Split large state into smaller state holders for browser files, selection, operation, search, and overlays.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **ARCH-0002 - Architecture / Storage Abstraction** `[Critical]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/FileRepository.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/data/LocalFileRepository.kt`
  - **Problem:** `FileRepository` is a broad god interface covering browsing, mutation, trash, archive operations, analytics, search, volume discovery, cached stats, and properties.
  - **Impact:** New features can regress unrelated workflows because every ViewModel depends on the same large abstraction.
  - **Fix:** Split into `FileBrowserRepository`, `FileMutationRepository`, `SearchRepository`, `StorageAnalyticsRepository`, `TrashRepository`, `ArchiveRepository`, and `VolumeRepository`. Give each interface explicit capability and error contracts. Keep an app facade only if needed for composition, not as the primary domain API. Recommended Refactor: Move use cases to depend on narrow interfaces. ViewModels should depend on use cases or feature facades, not the monolithic repository. Safer Alternative: Create adapter interfaces incrementally around current implementation and migrate one feature at a time.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **ARCH-0009 - Compose Architecture** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt`
  - **Problem:** `BrowserScreen.kt` is roughly 1,500 lines and owns too many dialogs, toolbars, state effects, operation UI, browser content, archive flows, clipboard UI, and bottom sheets in one file.
  - **Impact:** UI regressions become more likely as new file-manager features are added.
  - **Fix:** Split into `BrowserContent`, `BrowserDialogs`, `BrowserOperationOverlay`, `BrowserTopBars`, `BrowserEmptyStates`, and archive dialog files. Define a stable `BrowserUiActions` holder. Keep ephemeral UI state close to owning component. Recommended Refactor: Move browser-specific dialogs and operation surfaces into dedicated composables with focused previews/tests. Safer Alternative: Extract only dialogs and operation progress first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **ARCH-0012 - Presentation State Ownership** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/BrowserViewModel.kt`
  - **Problem:** `BrowserState` contains persistent navigation data, list data, search data, selection, clipboard, dialog visibility, native pending actions, properties, progress overlay state, and presentation preferences in one wide state object.
  - **Impact:** Unrelated UI changes can trigger wide recompositions.
  - **Fix:** Split into `BrowserNavigationState`, `BrowserListingState`, `BrowserSelectionState`, `BrowserSearchState`, `BrowserDialogState`, and `OperationUiState`. Expose separate StateFlows or a composed immutable screen state. Recommended Refactor: Use reducers/events for browser state transitions and test them directly. Safer Alternative: At minimum, move dialog visibility and operation state out of `BrowserState`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **MAINT-0030 - Maintainability / Code Organization** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/**`
  - **Problem:** The code is package-layered (`data`, `domain`, `presentation`) but not feature-modular. Feature concerns cross directories and files are growing large.
  - **Impact:** Indirect: feature velocity and regression risk will worsen.
  - **Fix:** Move toward feature packages/modules. Define public APIs per feature. Add architecture rules for dependencies. Recommended Refactor: Start with `feature:browser`, `feature:trash`, `feature:archive`, and `core:storage`. Safer Alternative: Within single module, reorganize packages under `feature/*` and `core/*`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### API / Domain Tasks

- [ ] **API-0003 - API Design / Type Safety** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/FileRepository.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/FileModel.kt`
  - **Problem:** Most APIs accept raw `String` paths for identities, selections, clipboard contents, destinations, archive entries, trash restoration, and routing.
  - **Impact:** Edge cases around paths with unusual separators, volume boundaries, virtual documents, and stale selections are more likely.
  - **Fix:** Add value classes for path/id concepts. Canonicalize once at boundary creation. Make destructive APIs require validated handles, not arbitrary strings. Recommended Refactor: Introduce `StorageNodeRef` containing backend id, volume id, display path, canonical identity, and capability flags. Safer Alternative: Start with value classes around existing strings and migrate high-risk methods first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **API-0031 - Kotlin API Quality** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/*.kt`
  - **Problem:** Several domain models use primitive strings/longs for semantically distinct values and lack stronger invariants.
  - **Impact:** Edge-case bugs around sizes, timestamps, paths, ids, and category names can leak into UI.
  - **Fix:** Add value classes for bytes, timestamps where useful, ids, paths, category ids. Use sealed capability models. Keep display names distinct from identifiers. Recommended Refactor: Introduce invariant constructors/factories for high-risk model types. Safer Alternative: Add value classes only for path/id/storage volume concepts first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Filesystem Performance / Memory Tasks

- [ ] **MEM-0010 - Memory / Large Directory Handling** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/FileSystemDataSource.kt`
  - **Problem:** Directory listing loads all children into memory, maps all to `FileModel`, then sorts the full list before returning.
  - **Impact:** Huge directories can load slowly, freeze visible progress, or consume excessive memory.
  - **Fix:** Introduce paged/incremental directory listing. Emit loading batches through Flow. Add configurable sorting that can operate on chunks or defer expensive metadata. Recommended Refactor: Create `DirectoryListingDataSource.list(path): Flow<ListingPage>`. Safer Alternative: Add a max initial batch and "load more" fallback for huge folders.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Build / Startup Tasks

- [ ] **BUILD-0023 - Build System / Dependency Freshness** `[Medium]`
  - **Location:** `arcile-app/gradle/libs.versions.toml` `arcile-app/app/build.gradle.kts`
  - **Problem:** The build uses a single app module with no convention plugins, no feature modules, no benchmark module, and no modular dependency boundaries.
  - **Impact:** Indirect: slower iteration and higher regression risk as the app grows.
  - **Fix:** Add convention plugins. Split modules by `core:domain`, `core:storage`, `core:ui`, `feature:browser`, `feature:home`, `feature:trash`, `feature:archive`, `benchmark`. Add dependency analysis and version update checks as ongoing tooling, not as a one-off dependency freshness task. Recommended Refactor: Start by extracting pure Kotlin domain and storage interfaces. Safer Alternative: Keep one APK module but add Gradle convention logic and package-level architecture checks.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Testing / QA Tasks

- [ ] **UI-0044 - Testing / Visual QA** `[Medium]`
  - **Location:** arcile-app/app/src/androidTest/java/dev/qtremors/arcile/ui arcile-app/app/src/test/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** There are UI tests, but the audit found no evidence of systematic screenshot/golden testing for theme modes, font scales, RTL, screen sizes, foldable layouts, or 1,000-file stress states.
  - **Impact:** Pixel-level regressions can ship unnoticed.
  - **Fix:** Add Paparazzi/Roborazzi or Compose screenshot testing. Cover compact phone, landscape, tablet, RTL, fontScale 1.5/2.0, light/dark/OLED/dynamic fallback. Add macrobenchmarks for browser scroll, thumbnail grid, search, and storage load.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **TEST-0025 - Testability / Production Verification** `[High]`
  - **Location:** `arcile-app/app/src/test/**` `arcile-app/app/src/androidTest/**`
  - **Problem:** The project has useful unit/UI tests, but lacks stress, benchmark, mutation recovery, SAF compatibility, process-death, and real large-directory tests.
  - **Impact:** Critical regressions may only appear on real devices with large storage.
  - **Fix:** Add contract tests for storage backends. Add large directory synthetic tests. Add transfer cancellation/recovery tests. Add archive safety tests. Add macrobenchmarks for startup, listing, scrolling, search, thumbnail grid. Recommended Refactor: Create test fixtures for in-memory, temp filesystem, SAF-like fake, and failure-injecting storage backends. Safer Alternative: Add stress tests for `FileTransferEngine`, `TrashManager`, and `FolderStatsCalculator` first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

---
## Backlog / Future Ideas

> A parking lot for future ideas, enhancements, and unprioritized Android file-manager features.

### Architecture & Foundation (Comparison Insights)
- **Persistent Operation Manager**: Move away from fire-and-forget Coroutines for file operations (Copy/Move/Delete). Implement a persistent Service-backed queuing architecture (similar to WorkManager or Foreground Services) to ensure heavy I/O operations survive UI destruction and provide robust pause/resume/retry capabilities.
- **Storage Provider Interfaces (VFS)**: Decouple the data layer from hardcoded `java.io.File` and Android Scoped Storage APIs. Introduce a `StorageProvider` interface that returns standard domain `FileModel`s and handles `InputStream/OutputStream` streams. This lays the groundwork for seamless future integration of SMB, FTP, or Cloud storage without modifying the UI layer.
- **Native File Viewers**: Integrate native, in-app viewers for common developer and text formats (e.g., Markdown, JSON, XML, TXT). Explore integrating lightweight code editors (like Sora) with syntax highlighting to keep users engaged within Arcile rather than bouncing them to external apps via Intents.

### File Browsing & Navigation
- **Dual-Pane File Browser**: Add a tablet, foldable, and landscape mode with two live folder panes for drag/copy/move workflows.
- **Breadcrumb Path Editing**: Let users tap the current path and type or paste a filesystem path directly.
- **Folder Tabs**: Keep multiple folders open at once with tab restore after process death.
- **Starred / Favorited Files**: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Recent Locations**: Track recently opened folders separately from recently modified files.
- **Quick Jump Drawer**: Provide fast access to Download, DCIM, Documents, Android/media, SD cards, OTG drives, network locations, and pinned folders.

### File Operations & Reliability
- **Queued Operation Manager**: Queue copy, move, delete, rename, compress, extract, and trash actions with pause, resume, cancel, retry, and background notifications.
- **Conflict Resolution Dialogs**: Offer replace, skip, rename, apply-to-all, compare metadata, and keep-both choices during copy/move/extract operations.
- **Checksum Verification**: Add MD5, SHA-1, and SHA-256 calculation plus optional post-copy verification for large transfers.
- **Batch Rename Tool**: Support numbered patterns, date tokens, find/replace, case conversion, extension changes, and live preview.
- **Undo Recent Operations**: Provide a short-lived undo stack for rename, move, trash, and folder creation actions where safe.
- **Safe Large Transfer Mode**: Warn before moving large folders across storage volumes and fall back to copy-then-delete with verification.
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations for auditing and troubleshooting.

### Archives & File Formats
- **APK / AAB Inspector**: Show package name, version, signatures, permissions, icons, split APK details, and install/open actions.
- **Text / Code Previewer**: Add a lightweight viewer with encoding detection, syntax highlighting, line numbers, search, and share/copy actions.
- **PDF / Document Preview Hooks**: Provide better thumbnails and safe handoff to installed viewers for PDFs, Office files, and ebooks.
- **Expanded Format Support**: Add read-only extraction support for RAR (e.g., via junrar), and add TAR, GZIP, BZIP2, and XZ using existing commons-compress dependencies.
- **Advanced Archive Creation Options**: Add compression levels, archive splitting (multi-volume), and AES-256 encryption options using Zip4j.
- **Archive Modification**: Allow in-place editing of ZIP archives (adding or removing files) without full extraction using Zip4j.
- **In-Memory Previews**: Stream entries directly into image or text previewers within the ArchiveViewerScreen without extracting to disk.
- **Archive Integrations**: Register Share intent for creating ZIPs from external apps, and add smart extraction rules (e.g., auto-delete archive after extraction).

### Storage, Access & Android Integration
- **Storage Health Diagnostics**: Basic storage status checks, mount state, free-space trends, and repair/trim suggestions for mounted volumes.
- **Mount / Unmount Awareness**: Detect SD card and USB OTG changes immediately, refresh affected screens, and recover pending operations gracefully.
- **MediaStore Rescan Tools**: Manually rescan selected files or folders so gallery, music, and downloads apps see changes sooner.
- **MTP / USB Transfer Mode Notes**: Surface helpful guidance when Android's USB file transfer state blocks desktop access.

### Search, Filters & Organization
- **Indexed Search**: Build an optional local index for faster filename, extension, MIME type, size, and modified-date searches.
- **Advanced Search Filters**: Add filters for date range, file size range, media duration, image dimensions, duplicate candidates, hidden files, and storage volume.
- **Saved Searches**: Allow users to pin reusable searches such as "large videos", "old APKs", or "recent downloads".
- **Duplicate Finder**: Detect duplicates with size pre-filtering and optional content hashing before deletion.
- **Large / Old Files Cleanup**: Add focused views for files likely worth reviewing when freeing space.
- **Empty Folder Finder**: Scan and clean empty folders with preview and exclusions.
- **Smart Collections**: Auto-group screenshots, screen recordings, APKs, downloads, documents, and WhatsApp/Telegram media.
- **Storage Analyzer ("Filelight" View)**: A dedicated radial map or sunburst chart to visualize storage usage by folder and file type.

### Media & Preview Experience
- **Rich Media Previews**: Continue expanding custom Coil `Fetcher` components for rich file previews.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally scrollable carousel of the 10 most recently modified images and videos.
- **Image Detail Sheet**: Show resolution, EXIF, location presence, orientation, color profile, and quick rotate/share actions.
- **Video Detail Sheet**: Show duration, resolution, codec, bitrate, frame rate, subtitles, and thumbnail refresh actions.
- **Audio Detail Sheet**: Show album art, artist, album, duration, bitrate, and quick metadata cleanup hints.
- **Thumbnail Cache Controls**: Add settings to clear, size-limit, or rebuild thumbnail caches.
- **Hidden Media Controls**: Create or remove `.nomedia` files with clear warnings about gallery visibility.

### Automation & Power Tools
- **Automated Task Rules**: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.
- **Watched Folders**: Monitor selected folders for new files and offer rule actions such as rename, move, compress, or notify.
- **Quick Actions**: Let users configure a small set of folder-specific actions like "send to SD", "compress here", or "clean old files".
- **Scheduled Cleanup**: Periodically empty trash, remove temporary files, or prompt for old downloads review.

### Sharing, Transfers & Network
- **Nearby Share / Share Sheet Polish**: Improve multi-file sharing flow, MIME grouping, and error messages for unsupported targets.
- **Local HTTP File Drop**: Temporarily host selected files or a folder over LAN with a QR code and one-tap shutdown.
- **WebDAV / SMB Client**: Browse and transfer files from NAS, routers, and desktop shares.
- **FTP / SFTP Client**: Add optional remote location support for advanced users.
- **Wi-Fi Direct Transfer**: Explore device-to-device transfer without an external network.

### Security, Privacy & Safety
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
- **Secure Delete Option**: Provide best-effort overwrite/delete workflows where storage type makes it meaningful, with honest limitations.
- **Private Folder Bookmarks**: Hide selected pinned folders behind biometric confirmation.
- **Trash Privacy Audit**: Review shared `.trash` behavior, visibility to other apps, and options for app-private trash on supported storage.
- **Sensitive Metadata Warnings**: Warn before sharing images with location EXIF or documents with embedded author metadata.
- **App Lock**: Add optional biometric/PIN lock for opening Arcile.

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` where a main action regularly needs a secondary dropdown or overflow action.
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press, successful file operations, and error states.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations such as Lottie for empty folders, trash, and search results.
- **Selection Mode Polish**: Improve multi-select toolbar actions, selected-count affordances, drag selection, and range selection.
- **Adaptive Bottom Actions**: Keep high-frequency actions thumb-friendly on phones while preserving dense toolbars on tablets.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Settings, Personalization & Accessibility
- **File Icon Packs**: Allow optional icon style packs for file types, folders, archives, media, and app packages.
- **Compact / Comfortable Density**: Add a display-density setting for list rows, grid cells, and toolbars.
- **Large Text Audit**: Verify folder lists, dialogs, and bottom sheets at Android large-font accessibility settings.
- **High Contrast Theme**: Add a contrast-focused theme profile beyond light, dark, and OLED modes.
- **Gesture Customization**: Configure swipe actions, long-press behavior, and double-tap shortcuts.

### Multi-Window, Layout & Devices
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.
- **Foldable Layouts**: Add hinge-aware navigation and dual-pane behavior for large foldable devices.
- **Tablet Navigation Rail**: Use a rail or permanent navigation surface on wider screens.
- **Keyboard & Mouse Support**: Add keyboard shortcuts, hover states, context menus, and precise right-click behavior.
- **Chromebook Polish**: Verify resizable windows, external storage access, drag/drop, and system file picker interoperability.

### Developer, Build & Release
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.
- **Internal Diagnostics Export**: Export anonymized app state, settings, storage classifications, and recent operation errors for bug reports.
- **Performance Benchmarks**: Add repeatable benchmarks for large folder listing, recursive stats, thumbnail loading, and search.
- **StrictMode Debug Profile**: Enable stricter debug-only checks for disk I/O, leaked resources, and slow main-thread work.
