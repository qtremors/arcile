# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.9.9
> **Last Updated:** 2026-06-05

---

## Audit Remediation Backlog

### Reliability Tasks

- [ ] **REL-0001 - Cancel stale browser loads** `[High]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/delegate/NavigationDelegate.kt`
  - **Problem:** `loadDirectory`, `loadCategory`, and `loadArchiveEntries` each start a new `viewModelScope.launch` without retaining or cancelling the previous load job. A fast folder change, refresh, archive navigation, or volume change can leave an older collector updating `BrowserState.files`, loading flags, cached folder stats, and errors after the user has moved to a different location.
  - **Impact:** Browser contents, selection state, folder stats, and error messages can be overwritten by stale work, which is especially risky during file operations and rapid navigation through large directories.
  - **Fix:** Track the active navigation/listing job, cancel it before starting another load, and gate state updates by a monotonically increasing request id or target location so old pages cannot mutate the current screen.
  - **Verification:** Add a `NavigationDelegate` or `BrowserViewModel` test with a delayed first listing and a faster second navigation, proving that only the newest path updates `files`, `currentPath`, `isLoading`, and errors.

- [ ] **REL-0002 - Make foreground operation recovery executable, not just visible** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationCoordinator.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/OperationJournal.kt`
  - **Problem:** The service returns `START_NOT_STICKY`; if the process is killed mid-copy, move, delete, archive, or extraction, the operation is only exposed later as a recovered record that requires manual retry or cleanup. There is no startup repair policy that distinguishes safe resume, rollback, or mandatory cleanup per operation phase.
  - **Impact:** Long-running or destructive operations can be interrupted with partial outputs, staged files, or stale UI state, leaving users to infer the right recovery action.
  - **Fix:** Extend journal records with operation phase checkpoints, temp output paths, completed targets, and retry safety. On app startup, run a recovery coordinator that either resumes idempotent work, rolls back staged outputs, or presents a specific repair action with consequences.
  - **Verification:** Add tests that kill/recreate the coordinator with active copy, move, replace, archive extraction, and trash operations, then verify the recovered action, cleanup result, and UI message for each phase.

- [ ] **REL-0003 - Prevent partial permanent deletes from silently succeeding** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileSystemDataSource.kt`
  - **Problem:** `deletePermanently` and `shred` iterate paths one by one and return failure after the first delete/shred failure, but any earlier paths are already deleted and only `finalizeMutation` is called for those scanned paths. The result model is `Result<Unit>`, so callers cannot report which items were removed, which failed, or what recovery is possible.
  - **Impact:** Batch destructive operations can partially complete while the UI shows a generic failure, eroding user trust and making recovery or support difficult.
  - **Fix:** Return a typed batch mutation result that records succeeded, skipped, failed, and cleanup-required paths. Use it in the foreground service and confirmation UI so partial success is explicit and retry targets are precise.
  - **Verification:** Add unit tests for mixed delete/shred batches where the second item fails, asserting that completed paths, failed paths, mutation finalization, user message, and retry payload are all correct.

### Security / Privacy Tasks

- [ ] **SEC-0002 - Harden Save to Arcile against hostile share intents** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/SaveToArcileActivity.kt` `arcile-app/app/src/main/AndroidManifest.xml`
  - **Problem:** The exported `SaveToArcileActivity` accepts `ACTION_SEND` and `ACTION_SEND_MULTIPLE` from any app, reads every stream immediately, and only sanitizes `/` and `\` in display names. It does not validate URI schemes, item count, filename length, control characters, reserved names, reported size, destination free space, or provider read failures before copying.
  - **Impact:** Malicious or buggy share providers can trigger disk-filling copies, confusing filenames, long-running foreground-less work, or partial imports with weak user feedback.
  - **Fix:** Add an import preflight that accepts only `content://` and safe `file://` sources where appropriate, enforces item and byte limits, normalizes filenames through the shared file-name validator, checks destination capacity when size is known, and reports per-item failures.
  - **Verification:** Add Robolectric tests for malicious display names, unsupported schemes, huge batches, unknown sizes, stream-open failures, duplicate names, and insufficient-space simulation.

- [ ] **SEC-0003 - Bound archive-entry thumbnail decoding** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/image/ArchiveEntryImageFetcher.kt`
  - **Problem:** ZIP entry thumbnails are decoded with `BitmapFactory.decodeStream` directly from archive data. The code checks extension and `entry.size`, but ZIP metadata can be missing or misleading, and the decoder does not first read bounds or apply `inSampleSize`.
  - **Impact:** A crafted archive image can cause excessive memory use or UI jank while browsing archive entries.
  - **Fix:** Decode bounds first, reject images above a pixel-count limit, sample to `options.size`, and enforce a counting input stream cap independent of ZIP metadata.
  - **Verification:** Add tests with unknown-size entries and oversized image dimensions, and manually browse a ZIP containing large images while monitoring memory and thumbnail behavior.

### Data / Storage / Platform Tasks

- [ ] **STORAGE-0001 - Reduce reliance on `MediaStore.DATA` raw paths** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/MediaStoreQueryHelpers.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/MediaStoreClient.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/image/VideoThumbnailFetcher.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/image/AudioAlbumArtFetcher.kt`
  - **Problem:** Media queries project and filter on `MediaStore.Files.FileColumns.DATA`, then convert results into raw filesystem-backed `FileModel`s. The app targets SDK 36, where raw data paths are increasingly fragile across scoped storage, removable volumes, cloud-backed providers, and media rows without stable local paths.
  - **Impact:** Recent files, category browsing, search, thumbnails, and delete/rescan flows can miss items or break on providers that expose content URIs rather than durable filesystem paths.
  - **Fix:** Store MediaStore `_ID`, volume name, relative path, display name, and content URI in the storage model. Use content URIs for thumbnail and metadata reads, and only attach raw paths when they are verified local files inside active storage roots.
  - **Verification:** Add contract tests for rows with null/empty `DATA`, removable-volume names, and content-URI-only media; verify recent files, category search, audio art, and video thumbnails still work.

- [ ] **STORAGE-0002 - Validate archive replacement rollback for directories and partial writes** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveSupport.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ZipArchiveHandler.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/SevenZipHandler.kt`
  - **Problem:** Archive extraction backs up existing replacement targets only when `target.isFile`; directory conflicts and nested partial writes rely on `createdOutputs` cleanup and do not have the same rollback guarantees. Directory entries also call `mkdirs()` after conflict resolution without explicit handling for an existing file at that path.
  - **Impact:** Failed extraction with replace or keep-both conflicts can leave mixed old/new directory contents or unclear failure states.
  - **Fix:** Add a conflict plan for files and directories before extraction, stage replaced directories safely, and make rollback semantics explicit for directory/file collisions.
  - **Verification:** Add archive extraction tests for file-over-directory, directory-over-file, nested replace failure, cancellation mid-directory, and rollback after password or IO failure.

### Performance Tasks

- [ ] **PERF-0001 - Keep storage usage scans within their advertised limits** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageUsageScanner.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/FolderStatsCalculator.kt`
  - **Problem:** When `StorageUsageScanner.scanFile` hits `maxDepth` or `maxNodes`, it calls `FolderStatsCalculator.calculate` for that subtree with a separate node limit. This can add large extra traversals after the scan has already reached its own budget.
  - **Impact:** Usage-map scans can run longer than expected on deep or huge folders, increasing battery, thermal load, and UI wait time.
  - **Fix:** Share a single scan budget across the usage scanner and fallback folder stats, or replace the fallback traversal with a bounded partial node that reports unknown/partial size without recursively scanning again.
  - **Verification:** Add a stress test with a deep tree and low `StorageUsageScanLimits`, asserting total visited nodes and elapsed time remain bounded.

- [ ] **PERF-0002 - Move large file-row UI model derivation out of composition** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileList.kt` `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileGrid.kt` `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/BrowserDisplayState.kt`
  - **Problem:** `FileList` and `FileGrid` map every visible-directory `FileModel` into `FileRowUiModel` inside composition whenever `files`, folder stats, formatter, or thumbnail size changes. Large directories and frequent folder-stat updates can repeatedly format dates, subtitles, thumbnail keys, and display metadata on the UI path.
  - **Impact:** Large folders can suffer avoidable recomposition and frame-time cost, especially while folder stats stream in or thumbnail policy changes.
  - **Fix:** Derive stable row UI models in the ViewModel/display-state layer, update only affected rows when folder stats arrive, and pass immutable row lists to list/grid composables.
  - **Verification:** Add a Compose benchmark or macrobenchmark for a 5,000-item folder with folder-stat updates, comparing recomposition counts and frame timing before and after.

### UI / UX Tasks

- [ ] **UI-0001 - Make browser paging globally sorted while pages stream** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileSystemDataSource.kt` `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/delegate/NavigationDelegate.kt`
  - **Problem:** `DefaultFileSystemDataSource.list` chunks the raw `listFiles()` array, sorts each chunk independently, and emits pages that `NavigationDelegate` appends. Until all pages are loaded and display sorting catches up, streamed folder contents can appear in locally sorted blocks rather than a stable global order.
  - **Impact:** Users can see list rows jump or appear out of order in large folders, and range selection can be confusing while pages are still arriving.
  - **Fix:** Sort the complete child list once before chunking, or keep streaming but merge pages through a globally sorted accumulator in `BrowserDisplayState`.
  - **Verification:** Add a paging test with deliberately unsorted filesystem children across multiple pages, asserting each emitted/visible accumulated list is globally sorted according to the active sort.

### Testing / Release Tasks

- [ ] **BUILD-0001 - Replace private AGP output API usage** `[Medium]`
  - **Location:** `arcile-app/app/build.gradle.kts`
  - **Problem:** APK renaming checks `output is com.android.build.api.variant.impl.VariantOutputImpl`, which is an internal AGP implementation class rather than a stable public Variant API.
  - **Impact:** Android Gradle Plugin upgrades can break release builds even when app code is unchanged.
  - **Fix:** Use the public Android Components output API or a dedicated copy/rename task wired to `assemble` artifacts instead of casting to an internal implementation.
  - **Verification:** Run `./gradlew :app:assembleDebug :app:assembleRelease` after the change and verify APK names without references to `com.android.build.api.variant.impl`.

- [ ] **TEST-0001 - Expand production string checks beyond the app module** `[Medium]`
  - **Location:** `arcile-app/app/build.gradle.kts` `arcile-app/feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerScreen.kt` `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserArchiveDialogs.kt`
  - **Problem:** `checkProductionStrings` scans only `app/src/main/java`, but production UI in feature and core modules still contains hardcoded strings such as archive encoding/conflict labels and destination-field labels.
  - **Impact:** Localization, consistency, and review gates can regress silently outside the app module.
  - **Fix:** Move the string guard to a convention plugin or root verification task that scans all production Android source sets while preserving targeted allowlists for tests and non-user-facing constants.
  - **Verification:** Run the expanded check, migrate current feature-module hardcoded strings to resources, and ensure `./gradlew checkProductionStrings` or its replacement fails on a new hardcoded feature string.

### Documentation Tasks

- [ ] **DOC-0001 - Align README version and structure docs with the current project** `[Low]`
  - **Location:** `README.md` `TASKS.md`
  - **Problem:** `TASKS.md` and Gradle report version `0.9.9`, while the README badge still says `0.9.0`. The README project tree also references older app-internal feature locations even though feature modules now live under `arcile-app/feature/*`.
  - **Impact:** Setup, release review, and contributor onboarding can start from stale project facts.
  - **Fix:** Update the README badge and project-structure section to match the current Gradle version and module layout, and add a lightweight release checklist item that keeps docs in sync with `versionName`.
  - **Verification:** Compare README version/module references against `app/build.gradle.kts` and `settings.gradle.kts`, then run markdown link checking if available.

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
