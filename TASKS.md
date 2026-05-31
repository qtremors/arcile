# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.9.4
> **Last Updated:** 2026-05-31

---

## Audit Remediation Backlog

### Reliability Tasks

- [ ] **REL-0001 - Interrupted Operation Recovery** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/OperationJournal.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationCoordinator.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`
  - **Problem:** `recoverInterrupted()` rewrites any non-terminal active operation to `CLEANUP_REQUIRED`, but `ForegroundBulkFileOperationCoordinator` then drops it from `activeRequest` because `CLEANUP_REQUIRED` is terminal. The service clears completed, failed, and cancelled records, and there is no visible recovery state, retry action, cleanup action, or operation-history surface for a process death during copy, move, trash, archive create, or extraction.
  - **Impact:** A killed process can leave users without an explanation of whether a destructive or long-running operation completed, partially applied, or needs manual cleanup. This undermines trust in high-risk file operations.
  - **Fix:** Model interrupted operations as a surfaced recovery state instead of silently clearing them from active UI state. Persist enough phase data, staged paths, completed outputs, and rollback hints to show a recovery card or operation history item with cleanup, retry, and dismiss actions.
  - **Verification:** Add unit tests for recovering `QUEUED`, `RUNNING`, and `CANCELLING` journal records; add a UI test that seeds a `CLEANUP_REQUIRED` record and verifies the app surfaces recovery; manually kill the app mid-copy and mid-extraction and confirm the next launch explains the interrupted operation.

- [ ] **REL-0002 - Archive Creation Temp Cleanup** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveManager.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/MutationJournal.kt`
  - **Problem:** Archive creation writes to a deterministic `.${target.name}.arcile-archive.tmp` staging file, but that path is never recorded in `MutationJournal`; startup cleanup only recognizes `.arcile-transfer-` and `.arcile-replace-` names. If the process dies during archive creation, the staging file is left behind until a later archive with the same target happens to delete it.
  - **Impact:** Large abandoned archive temp files can consume user storage, confuse later operations, and make process-death recovery incomplete.
  - **Fix:** Record archive staging paths in `MutationJournal`, use a unique staging name per operation, recognize archive temp names during cleanup, and forget the journal entry only after successful promotion or explicit deletion.
  - **Verification:** Add a unit test that records an `.arcile-archive` temp path and verifies `cleanupAbandonedMutations()` deletes it inside active roots; add an archive-manager test that simulates failure before `renameTo(target)` and confirms the temp file is journaled and cleaned.

### Data / Storage / Platform Tasks

- [ ] **STORAGE-0001 - Open-With Staging For Large Files** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ExternalFileAccessHelper.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt` `arcile-app/app/src/main/res/xml/file_provider_paths.xml`
  - **Problem:** `openFile()` calls `createOpenIntent()`, which copies the whole source into the app cache via `stageFile()` before launching `ACTION_VIEW`. Share flows enforce per-file and batch size limits, but open-with has no size limit, no progress, no cancellation, and no direct `content://` or platform-provider handoff path.
  - **Impact:** Opening a large video, PDF, or archive can block for a long time, duplicate hundreds of megabytes into cache, fail on low storage, and show no progress or cancel affordance.
  - **Fix:** Prefer direct read grants for supported user files, or route large opens through an explicit progress operation with cancellation and size limits. Keep staging only for targets that truly need FileProvider cache copies, and surface a clear error when a file is too large to stage safely.
  - **Verification:** Add tests for open-with size limits and direct/staged path selection; manually open small and large files and verify large files do not silently copy into cache without progress.

- [ ] **STORAGE-0002 - Cleaner Risk Classification** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageCleanerScanner.kt` `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/StorageCleanerViewModel.kt` `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/StorageCleanerScreen.kt`
  - **Problem:** The cleaner labels any `.tmp`, `.temp`, `.log`, `.bak`, `.old`, or `.dmp` file as junk regardless of folder ownership or user context, and the confirmation dialog only shows a selected count before moving paths to trash.
  - **Impact:** Users can be encouraged to remove meaningful logs, backups, dumps, or app/user data that merely matches an extension. The undo path helps only after trash succeeds and remains risky for temporary volumes or permission edge cases.
  - **Fix:** Add risk levels and reason codes to `CleanerCandidate`, exclude or warn on sensitive/user-owned locations, and make the confirmation list selected paths, sizes, risk labels, and undo limitations before cleanup.
  - **Verification:** Add scanner tests for `.log`, `.bak`, `.old`, and `.dmp` files in Downloads, DCIM, app-like folders, and `.arcile`; add a Compose test that high-risk candidates show warnings and cannot be bulk-cleaned without explicit confirmation.

### Performance Tasks

- [ ] **PERF-0001 - Streaming Archive Source Enumeration** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveSupport.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ZipArchiveHandler.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/SevenZipHandler.kt`
  - **Problem:** `walkArchiveFiles()` materializes every source file with `walkTopDown().drop(1).map`, and both ZIP and 7z creation call `sources.flatMap { it.walkArchiveFiles(...) }` before writing any archive entries. Cancellation checks happen during writing, not during this full pre-enumeration.
  - **Impact:** Creating an archive from a huge directory can consume unnecessary memory, delay progress, and ignore cancellation until after enumeration finishes.
  - **Fix:** Replace the eager list with a cancellable sequence or flow that validates and streams entries, computes progress with bounded pre-scan limits or indeterminate progress, and checks cancellation during traversal.
  - **Verification:** Add tests with a large synthetic directory tree that cancel during enumeration; benchmark archive creation memory usage before and after the streaming traversal change.

### Accessibility / Internationalization Tasks

- [ ] **A11Y-0001 - Storage Usage Map Semantics** `[Medium]`
  - **Location:** `arcile-app/feature/storageusage/src/main/java/dev/qtremors/arcile/feature/storageusage/ui/StorageUsageMap.kt`
  - **Problem:** The sunburst chart is an interactive `Canvas` driven by `pointerInput` and `detectTapGestures`, but it has no semantics, keyboard focus, state description, or custom accessibility actions for selecting segments. Only the details buttons are accessible after a node has already been selected.
  - **Impact:** Screen reader, keyboard, switch access, and D-pad users cannot explore storage usage segments or understand the selected chart state.
  - **Fix:** Add a semantic fallback list or custom accessibility actions for the visible segments, expose selected node and size state, and support keyboard/D-pad navigation between major segments.
  - **Verification:** Add Compose UI semantics tests for chart selection actions and selected state; manually verify TalkBack and keyboard navigation can select a node, drill in, and open the selected path.

- [ ] **I18N-0001 - Locale-Safe Operation And Date Formatting** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt` `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileList.kt` `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileGrid.kt` `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserContent.kt`
  - **Problem:** Operation notifications build progress strings with hardcoded English `"items"`, and file rows/search results use fixed date patterns such as `"MMM dd, yyyy  h:mm a"` and `"MMM dd, yyyy"`.
  - **Impact:** Progress text is not pluralized or translatable, and date order/time formatting can be wrong for users outside the assumed locale.
  - **Fix:** Move operation progress text to string and plural resources, and replace fixed date patterns with locale-aware Android date/time formatters or resource-configurable skeletons.
  - **Verification:** Add unit tests for singular/plural notification text; run pseudolocale/RTL checks and verify file dates and progress notifications remain readable.

### Testing / Release Tasks

- [ ] **TEST-0001 - Automated Release Verification Gates** `[High]`
  - **Location:** `arcile-app/build.gradle.kts` `arcile-app/app/build.gradle.kts` `arcile-app/build-logic/src/main/kotlin/dev/qtremors/arcile/buildlogic/ArcileAndroidApplicationConventionsPlugin.kt` `.github/workflows`
  - **Problem:** The repository documents manual verification commands, but there is no checked-in CI workflow, benchmark/baseline-profile module, or aggregate release gate that runs unit tests, Android lint, production-string checks, and release build checks together. A local `./gradlew.bat testDebugUnitTest` audit run also exceeded a two-minute execution window before completion.
  - **Impact:** Release readiness depends on local discipline, slow tests can go unnoticed, and regressions in storage operations, accessibility, localization, or release shrinker behavior may reach APK builds.
  - **Fix:** Add a CI workflow and Gradle aggregate task that runs module unit tests, lint, `:app:checkProductionStrings`, assemble release, and selected instrumented/benchmark jobs where available. Track and reduce slow tests so the standard local verification path completes predictably.
  - **Verification:** Run the new aggregate task locally and in CI; publish test, lint, and APK artifacts; add a timeout budget and fail the build when verification exceeds it.

### Active Feature Tasks

- [ ] **FEAT-0001 - Codebase Resource and Performance Optimization** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveSupport.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/TrashManager.kt` Coil image fetchers under `arcile-app/app/src/main/java/dev/qtremors/arcile/image/`
  - **Problem:** Eager collection mappings, redundant directories traversal, and lack of fine-tuned cache controls or streaming APIs result in higher resource overhead when managing massive directories or list scroll events.
  - **Impact:** Higher CPU/memory utilization, occasional UI stutters on long file listings, and slower performance on low-end devices.
  - **Fix:** Map operations with lazy sequences; use streams/flows for file processing; scale down image requests exactly to container bounds; use cached directory indexes where appropriate.
  - **Verification:** Monitor memory profiling in Android Studio Profiler during massive file scans and heavy scrolling.

- [ ] **FEAT-0002 - Universal File/Folder Selectability & 3-Dot Menus** `[High]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileList.kt` `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileGrid.kt` `arcile-app/feature/recentfiles/src/main/java/dev/qtremors/arcile/feature/recentfiles/ui/RecentFilesContent.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/home/RecentFilesCarousel.kt`
  - **Problem:** File rows/grids in browser screens, recents, and carousels require long-press for selection/actions but do not display a dedicated 3-dot context menu option on each item.
  - **Impact:** Users are forced to long-press or navigate through indirect actions to access file properties, sharing, renaming, deleting, or archiving, resulting in a less intuitive interface.
  - **Fix:** Add a conditional 3-dot vertical context menu icon on file/folder list and grid items when selection mode is inactive. Tapping the menu launches a bottom action sheet containing all options (Copy, Cut, Delete, Shred, Rename, Archive, Share, Properties).
  - **Verification:** Verify that 3-dot icons render correctly on all item surfaces and successfully invoke the respective file actions when tapped.

- [ ] **FEAT-0003 - Secure Shredding Option** `[High]`
  - **Location:** `arcile-app/core/operation/src/main/java/dev/qtremors/arcile/core/operation/BulkFileOperationModels.kt` `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/FileRepository.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/LocalFileRepository.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`
  - **Problem:** Normal file deletions leave underlying data recoverable on storage volumes until overwritten by newer files.
  - **Impact:** Users lack reassurance when disposing of confidential files from internal storage, older SD cards, or OTG USB flash drives.
  - **Fix:** Introduce `SHRED` to `BulkFileOperationType`. In `LocalFileRepository`, implement a secure deletion routing that overwrites target file sectors with zero-fills/random patterns (1 or 3 passes) before calling `delete()` or system SAF deletion API.
  - **Verification:** Run a secure shred operation on a dummy file, confirm the content is overwritten prior to deletion, and verify performance on large files.

- [ ] **FEAT-0004 - Configurable Trash Location (App-Private vs Volume Root)** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/TrashManager.kt` `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/TrashMetadata.kt` Settings datastores
  - **Problem:** The trash location is hardcoded to a public `.arcile/.trash` directory in each storage volume root, which is visible to third-party scanners.
  - **Impact:** Clutters volume directories and exposes deleted file previews or metadata sidecars to other apps.
  - **Fix:** Add a preference setting `trash_private_storage`. Modify `TrashManager` to conditionally write to the private app directories (e.g., `context.filesDir` or private external folders) instead of the root directory `.arcile/.trash` when enabled.
  - **Verification:** Enable private trash in Settings, trash files/folders, and confirm files are relocated to the secure app-private directory.

- [ ] **FEAT-0005 - Thumbnail Cache & Viewport Lifecycle Fixes** `[High]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/image/ThumbnailPolicy.kt` `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileList.kt` `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileGrid.kt`
  - **Problem:** The `ThumbnailPolicy.isInVisibleBudget()` check completely removes `AsyncImage` composables when they scroll out of a narrow list index threshold. When scrolling back, recompositions cause annoying reloads/flashes even if memory cached.
  - **Impact:** Poor scrolling performance and micro-stutters when navigating image folders.
  - **Fix:** Redesign the budget check to only gate new concurrent image fetch requests, but preserve successfully loaded thumbnails in the visible/reusable view hierarchy. Validate Coil disk and memory cache policies are respected.
  - **Verification:** Scroll rapidly through folders containing high-res photos and verify thumbnails remain visible without reload flashes when scrolling back.

- [ ] **FEAT-0006 - Custom Theme Presets and Custom Color Picker** `[High]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/ui/theme/Theme.kt` `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/ui/theme/ThemeState.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/SettingsScreen.kt`
  - **Problem:** Appearance options are limited to basic light/dark/OLED modes and accent colors seeded from Material3 standard palettes or wallpapers. Custom color presets or user-defined color overrides do not exist.
  - **Impact:** Limited personalization.
  - **Fix:** Expand `ThemeState` to support named theme presets (e.g., Dracula, Nord, Catppuccin) and custom color overrides. Update `SettingsScreen` to present preset cards and a custom HEX/HSL color picker.
  - **Verification:** Verify selectable theme presets and custom color picker inputs update the theme state and instantly apply to the entire UI.

- [ ] **FEAT-0007 - Robust Archive and Extraction UI/UX** `[High]`
  - **Location:** `arcile-app/feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerViewModel.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveManager.kt`
  - **Problem:** Compressed file interactions do not support full-featured conflict resolution, file name encoding selection, or rich nested path tree navigations during preview.
  - **Impact:** Operations fail on invalid path encodings or trigger raw crashes instead of rollback strategies.
  - **Fix:** Enhance archive utilities to handle name encoding, supply multi-option conflict dialogs during extraction, and upgrade `ArchiveViewerScreen` UI/UX for hierarchical navigation.
  - **Verification:** Test ZIP/7z creation and extraction with deeply nested paths, special characters, and verify progress tracking details.

- [ ] **FEAT-0008 - Android Notification Polish & Details** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`
  - **Problem:** Progress notification displays simple hardcoded English strings and lacks app icon branding or detailed metrics (e.g., transfer speed, time remaining).
  - **Impact:** Background file transfers feel detached from the main app's premium aesthetic and lack informative feedback.
  - **Fix:** Set a styled monochrome launcher icon for the notification channel, use translatable plural string resources for progress texts, and calculate/render real-time transfer speeds and remaining time estimate.
  - **Verification:** Trigger copying/moving/archiving of large directories, check the notification tray, and verify details updates.

- [ ] **FEAT-0009 - "Save to Arcile" Share Target** `[High]`
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml` `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt` (or new receiver activity)
  - **Problem:** Arcile is not registered as a share target in the Android OS share sheet.
  - **Impact:** Users cannot send files or streams from external apps directly into Arcile.
  - **Fix:** Add intent filters for `ACTION_SEND` and `ACTION_SEND_MULTIPLE` in the manifest. Implement a receiver activity that displays a directory selection sheet to write incoming streams into the target path.
  - **Verification:** Share a photo from Google Photos or another app, select "Save to Arcile", pick a folder, and confirm it writes successfully.

- [ ] **FEAT-0010 - Configurable Recent Carousel Limit** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt` Settings preference stores
  - **Problem:** The Home screen recent files carousel is locked to a fixed preview limit of 12 items.
  - **Impact:** Users are unable to increase the density of recent files on home or clear the widget block for a cleaner layout.
  - **Fix:** Add a preference key `home_recent_carousel_limit`. Hook this preference to a settings slider, and read the dynamic value in `HomeScreen` to slice `todayRecentFiles.take(limit)`.
  - **Verification:** Modify carousel limits in Settings and verify the Home screen carousel adjusts count dynamically.

- [ ] **FEAT-0011 - Icon Alignment and Style Harmonization** `[High]`
  - **Location:** Layouts under `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt`, file grid cards, and navigation surfaces.
  - **Problem:** Some icons have visual misalignment, incorrect scaling padding, or inconsistent filled/outlined style variants.
  - **Impact:** Visual inconsistency diminishes the premium design aesthetic.
  - **Fix:** Audit component icons, apply standard sizes (e.g., 24dp for generic icons, 48dp for container icons), and use uniform icon packages (e.g. Outlined).
  - **Verification:** Visually inspect icon layouts across multiple device models and screen densities.

- [ ] **FEAT-0012 - Tap Progress Pill for Detailed Context** `[High]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserFloatingSurfaces.kt`
  - **Problem:** The progress pill click listener is disabled when an operation is active, meaning users cannot drill down into operation details.
  - **Impact:** Users cannot view detailed info (such as the current file, queue, speed, or detailed progress log) of background operations.
  - **Fix:** Update the progress pill click action when `activeOp != null` to display a detailed progress modal bottom sheet, showing queue list, transfer speed, and cancel options.
  - **Verification:** Start a bulk copy/move operation, tap the progress pill, and confirm the details modal opens.

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
