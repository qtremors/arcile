# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.8.5
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

- [x] **ARCH-0031 - Move Operation Models Out of Presentation** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/*`, `arcile-app/app/src/main/java/dev/qtremors/arcile/core/storage/**`
  - **Problem:** Core/domain and core/data code import `presentation.operations.BulkFileOperationProgress`, which inverts the dependency direction.
  - **Impact:** Storage domain APIs are coupled to UI/presentation naming, making future modules harder to extract cleanly.
  - **Fix:** Move `BulkFileOperationType`, `BulkFileOperationRequest`, `BulkFileOperationProgress`, and operation events into a core operation package such as `core.operation` or `core.storage.domain.operation`. Keep UI-only progress smoothing/status rendering in presentation.
  - **Status:** Complete in 0.8.5. Shared operation request/progress/type/event models now live in `core.operation`; the foreground coordinator, service, journal, and progress smoothing remain presentation-owned.
  - **Verification:** Unit tests compile without any `core -> presentation` imports.

- [x] **ARCH-0032 - Expand Architecture Boundary Tests** `[High]`
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/ArchitectureBoundaryTest.kt`
  - **Problem:** The current boundary test only protects `feature -> concrete storage data` imports. It does not catch `core -> presentation`, broad feature/presentation cross-imports, or accidental dependency direction regressions.
  - **Impact:** Modularization can silently become harder even when tests pass.
  - **Fix:** Add rules forbidding `core -> presentation`, `core -> feature`, and concrete data imports from feature/presentation code except explicitly allowed store interfaces. Add a small allowlist for app shell/navigation wiring if needed.
  - **Status:** Complete in 0.8.5. The boundary test now protects `core -> presentation/feature`, feature storage-data imports, and presentation storage-data imports with a narrow public-service allowlist.
  - **Verification:** `ArchitectureBoundaryTest` fails with readable file:line output when a forbidden import is introduced.

- [x] **MAINT-0033 - Enforce Large File Budget** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/**/*.kt`, `arcile-app/app/src/test/java/**/*.kt`, `docs/**/*.html`
  - **Problem:** Files above 700 LOC are already present, and several files are close enough to cross the limit during normal feature work.
  - **Impact:** Review, testing, and refactoring cost grows nonlinearly when screens/ViewModels/tests become catch-all files.
  - **Fix:** Add a lightweight Gradle verification task or architecture test that flags Kotlin/HTML test and production files above 700 LOC, with a temporary allowlist for known files while they are being split.
  - **Status:** Complete in 0.8.5. `ArchitectureBoundaryTest` now enforces the 700 LOC budget for Kotlin and docs HTML files, with temporary allowlist entries for known split work.
  - **Current Hotspots:** `BrowserViewModelTest.kt` (~1031 LOC), `BrowserViewModel.kt` (~853 LOC), `RecentFilesScreen.kt` (~747 LOC), and `MediaStoreClient.kt` (~719 LOC). Near-threshold: `StorageCleanerScreen.kt` (~693 LOC) and `TrashScreen.kt` (~653 LOC).
  - **Verification:** CI/check task reports clear file sizes and fails only for non-allowlisted growth.

- [ ] **MAINT-0034 - Split Browser ViewModel Tests by Behavior** `[Medium]`
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/feature/browser/BrowserViewModelTest.kt`
  - **Problem:** The browser ViewModel test file is over 700 LOC and covers many unrelated workflows in one suite.
  - **Impact:** Future browser changes will make the test file harder to scan, slower to update, and more conflict-prone.
  - **Fix:** Split into behavior-focused suites: navigation, search, clipboard, delete, archive, properties, folder stats, and operation events. Keep shared builders in `testutil` or a browser-specific fixture file.
  - **Status:** Still open. Current file is ~1031 LOC.
  - **Verification:** Same behavioral coverage remains, with no individual test file above 700 LOC.

- [ ] **MAINT-0035 - Split Recent Files Screen Route and Content** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt`
  - **Problem:** The screen file mixes route wiring, activity result launchers, snackbar behavior, top bars, content rendering, selection toolbar, and grouping logic.
  - **Impact:** UI changes to recent files risk touching unrelated orchestration/effects code.
  - **Fix:** Split into `RecentFilesRoute`, `RecentFilesContent`, `RecentFilesTopBars`, `RecentFilesSelectionToolbar`, and small list/grouping helpers. Keep public API stable for navigation.
  - **Status:** Still open. Current file is ~747 LOC.
  - **Verification:** Existing RecentFiles UI/ViewModel tests pass and the main screen file drops below 700 LOC.

- [ ] **MAINT-0036 - Preemptively Split Near-Threshold Screens and Data Sources** `[Medium]`
  - **Location:** `StorageCleanerScreen.kt`, `TrashScreen.kt`, `AppNavigationGraph.kt`, `MediaStoreClient.kt`
  - **Problem:** Several files are below 700 LOC but likely to grow as backlog features land.
  - **Impact:** Storage cleaner, trash, navigation, and MediaStore querying can become future bottlenecks.
  - **Fix:** For screens, split route/effects from composable content and local widgets. For `MediaStoreClient` (~719 LOC), extract query building, cursor mapping, category cache, and scope/volume filtering helpers. Also watch `StorageCleanerScreen.kt` (~693 LOC) and `TrashScreen.kt` (~653 LOC).
  - **Verification:** No behavior changes; targeted unit/UI tests continue passing after each split.

- [ ] **ARCH-0037 - Prepare Gradle Module Extraction Roadmap** `[Medium]`
  - **Location:** `arcile-app/settings.gradle.kts`, `arcile-app/app/build.gradle.kts`, `arcile-app/build-logic/**`
  - **Problem:** The repo currently has a single Android `:app` module, so package boundaries are social conventions instead of compile-time guarantees.
  - **Impact:** Planned backlog items like VFS/storage providers, persistent operation manager, network storage, archive expansion, and dual-pane browsing will add pressure to shared code.
  - **Fix:** Document and stage module extraction in small passes: first core operation/domain models, then storage data, then shared UI, then feature modules. Keep `:app` as composition/navigation shell.
  - **Verification:** Each extraction pass has a compiling intermediate state and does not require moving unrelated features at the same time.

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
