# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.8.3
> **Last Updated:** 2026-05-26

---

## Consolidated Tasks

### Architecture / Maintainability Tasks

- [ ] **ARCH-0012 - Presentation State Ownership** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/feature/browser/BrowserViewModel.kt`
  - **Problem:** `BrowserState` contains persistent navigation data, list data, search data, selection, clipboard, dialog visibility, native pending actions, properties, progress overlay state, and presentation preferences in one wide state object.
  - **Impact:** Unrelated UI changes can trigger wide recompositions. The ViewModel also remains a workflow hub for navigation, selection, search, delete, clipboard, archive, properties, undo, and operation progress.
  - **Fix:** Continue the reducer/slice work already started in `BrowserStateSlices.kt`. Extract archive actions, properties loading, operation-event handling, and selection sizing into injected interactors or dedicated delegates. Keep `BrowserViewModel` as a thin coordinator that translates UI intents into state reducers and use-case calls.
  - **Status:** Partially complete. `BrowserNavigationState`, `BrowserListingState`, `BrowserSelectionState`, `BrowserSearchState`, `BrowserDialogState`, and `OperationUiState` exist, but `BrowserState` remains the compatibility screen state and `BrowserViewModel.kt` is still over the 700 LOC limit.
  - **Verification:** Add/keep reducer-level unit tests and split the large browser ViewModel test suite by behavior.

- [ ] **MAINT-0030 - Maintainability / Code Organization** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/**`
  - **Problem:** The code is package-layered (`data`, `domain`, `presentation`) but not feature-modular. Feature concerns cross directories and files are growing large.
  - **Impact:** Feature velocity and regression risk will worsen as the app grows, especially around browser, trash, archive, storage analytics, and operation-management features.
  - **Fix:** Move toward explicit Gradle modules after package boundaries are stable. Recommended target modules: `:core:storage-domain`, `:core:storage-data`, `:core:operation`, `:core:ui`, `:feature:browser`, `:feature:trash`, `:feature:archive`, `:feature:recent-files`, and `:app`. Safer Alternative: First enforce the same boundaries inside the single `:app` module with architecture tests.
  - **Status:** Deferred, but should become an architectural runway task before adding network storage, persistent operation queues, dual-pane browsing, or richer previewers.
  - **Verification:** Add architecture tests before moving packages/modules so regressions are caught during normal unit tests.

- [ ] **ARCH-0031 - Move Operation Models Out of Presentation** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/*`, `arcile-app/app/src/main/java/dev/qtremors/arcile/core/storage/**`
  - **Problem:** Core/domain and core/data code import `presentation.operations.BulkFileOperationProgress`, which inverts the dependency direction.
  - **Impact:** Storage domain APIs are coupled to UI/presentation naming, making future modules harder to extract cleanly.
  - **Fix:** Move `BulkFileOperationType`, `BulkFileOperationRequest`, `BulkFileOperationProgress`, and operation events into a core operation package such as `core.operation` or `core.storage.domain.operation`. Keep UI-only progress smoothing/status rendering in presentation.
  - **Verification:** Unit tests compile without any `core -> presentation` imports.

- [ ] **ARCH-0032 - Expand Architecture Boundary Tests** `[High]`
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/ArchitectureBoundaryTest.kt`
  - **Problem:** The current boundary test only protects `feature -> concrete storage data` imports. It does not catch `core -> presentation`, broad feature/presentation cross-imports, or accidental dependency direction regressions.
  - **Impact:** Modularization can silently become harder even when tests pass.
  - **Fix:** Add rules forbidding `core -> presentation`, `core -> feature`, and concrete data imports from feature/presentation code except explicitly allowed store interfaces. Add a small allowlist for app shell/navigation wiring if needed.
  - **Verification:** `ArchitectureBoundaryTest` fails with readable file:line output when a forbidden import is introduced.

- [ ] **MAINT-0033 - Enforce Large File Budget** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/**/*.kt`, `arcile-app/app/src/test/java/**/*.kt`, `docs/**/*.html`
  - **Problem:** Files above 700 LOC are already present, and several files are close enough to cross the limit during normal feature work.
  - **Impact:** Review, testing, and refactoring cost grows nonlinearly when screens/ViewModels/tests become catch-all files.
  - **Fix:** Add a lightweight Gradle verification task or architecture test that flags Kotlin/HTML test and production files above 700 LOC, with a temporary allowlist for known files while they are being split.
  - **Current Hotspots:** `BrowserViewModelTest.kt` (~915 LOC), `BrowserViewModel.kt` (~795 LOC), `RecentFilesScreen.kt` (~722 LOC). Near-threshold: `StorageCleanerScreen.kt`, `MediaStoreClient.kt`, `TrashScreen.kt`, and `AppNavigationGraph.kt`.
  - **Verification:** CI/check task reports clear file sizes and fails only for non-allowlisted growth.

- [ ] **MAINT-0034 - Split Browser ViewModel Tests by Behavior** `[Medium]`
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/feature/browser/BrowserViewModelTest.kt`
  - **Problem:** The browser ViewModel test file is over 700 LOC and covers many unrelated workflows in one suite.
  - **Impact:** Future browser changes will make the test file harder to scan, slower to update, and more conflict-prone.
  - **Fix:** Split into behavior-focused suites: navigation, search, clipboard, delete, archive, properties, folder stats, and operation events. Keep shared builders in `testutil` or a browser-specific fixture file.
  - **Verification:** Same behavioral coverage remains, with no individual test file above 700 LOC.

- [ ] **MAINT-0035 - Split Recent Files Screen Route and Content** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt`
  - **Problem:** The screen file mixes route wiring, activity result launchers, snackbar behavior, top bars, content rendering, selection toolbar, and grouping logic.
  - **Impact:** UI changes to recent files risk touching unrelated orchestration/effects code.
  - **Fix:** Split into `RecentFilesRoute`, `RecentFilesContent`, `RecentFilesTopBars`, `RecentFilesSelectionToolbar`, and small list/grouping helpers. Keep public API stable for navigation.
  - **Verification:** Existing RecentFiles UI/ViewModel tests pass and the main screen file drops below 700 LOC.

- [ ] **MAINT-0036 - Preemptively Split Near-Threshold Screens and Data Sources** `[Medium]`
  - **Location:** `StorageCleanerScreen.kt`, `TrashScreen.kt`, `AppNavigationGraph.kt`, `MediaStoreClient.kt`
  - **Problem:** Several files are below 700 LOC but likely to grow as backlog features land.
  - **Impact:** Storage cleaner, trash, navigation, and MediaStore querying can become future bottlenecks.
  - **Fix:** For screens, split route/effects from composable content and local widgets. For `MediaStoreClient`, extract query building, cursor mapping, category cache, and scope/volume filtering helpers.
  - **Verification:** No behavior changes; targeted unit/UI tests continue passing after each split.

- [ ] **ARCH-0037 - Prepare Gradle Module Extraction Roadmap** `[Medium]`
  - **Location:** `arcile-app/settings.gradle.kts`, `arcile-app/app/build.gradle.kts`, `arcile-app/build-logic/**`
  - **Problem:** The repo currently has a single Android `:app` module, so package boundaries are social conventions instead of compile-time guarantees.
  - **Impact:** Planned backlog items like VFS/storage providers, persistent operation manager, network storage, archive expansion, and dual-pane browsing will add pressure to shared code.
  - **Fix:** Document and stage module extraction in small passes: first core operation/domain models, then storage data, then shared UI, then feature modules. Keep `:app` as composition/navigation shell.
  - **Verification:** Each extraction pass has a compiling intermediate state and does not require moving unrelated features at the same time.

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
