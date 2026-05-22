# Arcile Changelog

> **Project:** Arcile
> **Version:** 0.7.3
> **Last Updated:** 2026-05-22

---

## [0.7.3] - 2026-05-22

### Home UI & Storage Insights
- **Quick Access Toggles:** Replaced pin controls on the Quick Access management page with direct Home shortcut toggles while preserving the existing persisted shortcut state.
- **Recent Files Preview Polish:** Kept image and video recents thumbnail-first, added file-type badges, and upgraded APK, archive, document, audio, folder, and unknown-file carousel cards with cleaner icon-led previews.
- **Trash Storage Visibility:** Added Trash usage to storage dashboard summaries, storage bars, and the storage legend without adding Trash to browsable file categories.

### Backend Reliability & Compatibility
- **Coroutine Dispatcher Injection:** Added an injectable dispatcher holder and routed backend repositories, data sources, managers, stores, image fetchers, and foreground operations through explicit dispatchers for deterministic tests and future I/O tuning.
- **Volume Root Readiness:** Initialized storage roots synchronously in the volume provider so path validation has canonical roots available before the first file operation, while mount changes still refresh cached volumes.
- **External Handoff Controls:** Added staged open/share cache stats, explicit cleanup APIs, Settings cleanup access, shorter stale cache retention, per-file and batch share guards, and MIME-aware share grouping.
- **Startup Debug Safety:** Added debug-only StrictMode plus startup trace sections around splash setup, permission checks, preference preload, and Compose setup.
- **Trash Transfer Reuse:** Routed Trash copy-then-delete fallbacks through the shared transfer engine, preserving the existing `.arcile/.trash` layout while using streaming verification, staged promotion, and progress callbacks for large restore/trash operations.
- **Mutation Recovery Cleanup:** Added a persistent mutation journal and startup cleanup for abandoned Arcile transfer, replacement, and incomplete Trash fallback artifacts without deleting completed Trash entries.


### Testing
- **Backend Regression Coverage:** Added tests for staged handoff cache cleanup and oversized share rejection, and updated share helper tests for MIME-aware share targets.
- **Trash & Mutation Regression Coverage:** Added tests for Trash fallback progress, journal cleanup, completed Trash preservation, and transfer temp cleanup.
- **Home Polish Regression Coverage:** Added coverage for Trash storage usage loading and Quick Access Home shortcut toggles.

## [0.7.2] - 2026-05-21

### Backend Reliability & Safety
- **Transfer Verification Policy:** Added explicit metadata and full-checksum verification modes. Normal copies now use metadata verification to avoid full SHA-256 re-reads, while move fallback still performs checksum verification before deleting sources.
- **Streaming Directory Verification:** Replaced directory verification that materialized full file lists with streaming traversal, reducing memory pressure for large folders and emitting progress during longer verification work.
- **Path Safety Policies:** Centralized path validation by operation policy and tightened mutation/recursive traversal paths to reject symlink traversal and revalidate staging, promotion, delete, rename, and extraction targets.
- **Operation Work Coordination:** Added a lightweight storage work coordinator so low-priority folder stats defer while foreground file mutations are active.
- **Folder Stats Backpressure:** Folder stats now cancel stale per-path jobs, prioritize the newest request, and avoid publishing superseded scan results after navigation or invalidation.
- **Foreground Operation Progress:** File-operation notifications now show throttled determinate progress when bytes or item counts are known, keep a cancel action available, and better reflect the active operation phase.

### Archives & Privacy
- **Archive Safety Policy:** Added archive entry count, uncompressed size, path length, nesting depth, and compression-ratio guards for ZIP and 7z listing, creation, metadata, and extraction paths.
- **Extraction Cleanup:** Failed or cancelled extraction now removes files and directories created by that extraction attempt when they can be identified safely.
- **Backup Privacy Rules:** Tightened backup and data-extraction rules to exclude local storage preferences, quick access paths, classification data, operation/staging metadata, analytics, and Arcile storage metadata.

### Testing
- **Backend Regression Coverage:** Added tests for transfer verification policy behavior, symlink mutation rejection, archive safety limits, extraction cleanup, backup/data extraction exclusions, storage work coordination, folder stats cancellation/defer behavior, and foreground operation notification progress.

## [0.7.1] - 2026-05-20

### Trash & Recovery
- **Recoverable Trash Metadata:** Replaced encrypted trash sidecars with plaintext, schema-versioned JSON stored under each volume's `.arcile/.metadata` folder, so trashed items can be rediscovered after reinstall without app-private keys or preferences.
- **Recovered Item Handling:** Unreadable metadata no longer causes payload loss; if the trash payload still exists, Arcile lists it as a recovered item and requires the user to choose a restore destination.
- **Safer Restore Flow:** Trash restore now reports whether items can return to their original path, need a destination, or will be restored with a conflict-safe renamed filename.

### UI & UX
- **Practical Trash Manager:** Added Trash filters for all items, directly restorable items, destination-required items, and recovered payloads.
- **Trash Sorting & Grouping:** Completed the Trash sort action with deletion date, name, size, type, and original-folder sorting, plus list grouping by Today, Yesterday, and Older.
- **Trash Properties:** Finished the Trash properties action with selected-item details including original path, trash payload path, restore status, size, and source volume.
- **Restore Feedback:** Restore actions now show a summary snackbar, including when selected files were restored with conflict names.

### Build
- **Version Bump:** Updated the app version from `0.7.0` to `0.7.1` with `versionCode` 55.

### Testing
- **Trash Regression Coverage:** Added tests for plaintext metadata, reinstall recovery, conflict restore, recovered payload restore, orphan metadata cleanup, unsupported storage, Trash filters, sorting, destination routing, and properties.

## [0.7.0] - 2026-05-16

### UI & UX
- **Home Recent Files Carousel:** Replaced the static Home recent-files list with a Material 3 Expressive `HorizontalMultiBrowseCarousel`, including exact 4:5 item sizing, cover-flow width/shape motion, stable rounded clipping via `Modifier.maskClip`, media thumbnail gradient overlays, and inline overflow actions for opening files or their containing folders.
- **Settings Thumbnail Toggle Polish:** Added dividers around the Appearance controls and upgraded the thumbnail switch with checked/unchecked thumb icons plus stable test tags for the row and switch.

### Build
- **Version Bump:** Updated the app version from `0.6.9` to `0.7.0` with `versionCode` 54.
- **Release Build Hardening:** Fixed release minification by suppressing the optional Apache Commons Compress Zstandard reference that Arcile does not ship or use directly.

### Testing
- **Coverage Reporting:** Enabled debug unit-test coverage so `:app:createCoverageReport` now produces a measurable HTML coverage report instead of an empty/no-data result.
- **Regression Coverage:** Added focused unit tests for path-boundary validation, copy-conflict detection, file use-case dispatch, and smoothed progress state transitions.
- **Instrumented Test Maintenance:** Updated stale Home, Quick Access, and Empty State instrumented tests for the current Compose test APIs and Home screen contract; connected debug Android tests now compile and pass on device.

## [0.6.9] - 2026-05-16

### UI & UX
- **Onboarding Relaunch & Polish:** Fixed the completed-user onboarding flash on app start, added a Settings action to rerun onboarding after restart, combined setup permissions into one page, simplified onboarding accent selection with a picker sheet trigger, and smoothed the onboarding controls and page transitions.
- **Settings & Search Polish:** Moved thumbnail settings and search bar labels/placeholders to resources, merged the thumbnail toggle into the existing Appearance section, and fixed browser root padding to respect RTL layout direction.
- **Recent Files Browser Controls:** Reused the Browser search filters and sort/view sheet on Recent Files, adding active filter chips plus list/grid, zoom, grid-size, thumbnail, and date/name/size ordering controls without changing storage queries.

### Storage
- **MediaStore Scoped-Storage Hardening:** Recent files, category browsing, and search now read modern MediaStore metadata such as display names, relative paths, MIME types, and volume names while preserving raw path fallback behavior for existing file workflows.
- **Faster Storage Analytics:** Category storage totals now aggregate from a single MediaStore cursor pass instead of repeating one query per category, improving dashboard efficiency on large media libraries.

### Testing
- **Onboarding Regression Coverage:** Updated onboarding repository, ViewModel, and Compose tests for reset behavior, combined permission setup, storage gating, accent-picker entry, and the refined navigation controls.
- **MediaStore Regression Coverage:** Added and updated MediaStore tests for modern rows without raw filesystem paths, scoped volume filtering, category aggregation, and cache invalidation.
- **Recent Files Filter/View Coverage:** Added ViewModel and screen tests for Browser-style Recent Files filters, presentation sorting, search-mode filtering, select-all behavior, and load-more preservation.

## [0.6.8] - 2026-05-14

### UI & UX
- **Complete First-Run Onboarding:** Added a polished new-user setup flow with a welcome screen, feature overview, theme/accent selection, required storage-access setup, notification-permission explanation, and a skip path that still preserves required permission setup.
- **Permission UX Upgrade:** Replaced the abrupt startup notification prompt with an onboarding-time request for Android 13+ notifications, and upgraded the storage recovery screen shown when all-files access is revoked after setup.
- **Containing Folder Navigation Fix:** Fixed secondary-screen folder handoffs so Recent Files and Quick Access reliably replace the saved Main browser entry instead of stacking stale browser state.

### Architecture
- **Onboarding Persistence:** Added DataStore-backed onboarding preferences for completion state, completed version, and notification-permission handling.
- **Onboarding State Model:** Added a dedicated onboarding ViewModel to manage setup steps, skip behavior, permission state, and completion without embedding permission logic in Compose screens.

### Testing
- **Onboarding Coverage:** Added repository, ViewModel, and Compose regression tests for first-launch rendering, skip-to-permissions behavior, storage gating, notification denial/skip completion, and revoked-storage recovery.
- **Regression Verification:** Added onboarding UI files to the production string guard and verified the debug unit test suite.

### Dependencies
- **Android 17 Build Readiness:** Updated the project to compile against Android SDK 37 while keeping `targetSdk` at 36 to avoid opting into new runtime behavior prematurely.
- **Compose & Material Refresh:** Updated the Compose BOM to 2026.05.00 and Material 3 to 1.5.0-alpha19, preserving Arcile's existing Material 3 Expressive surfaces while aligning with the newer Compose stack.
- **Adaptive UI Foundation:** Added Material 3 Adaptive libraries for future tablet, foldable, list-detail, and multi-pane file-manager layouts.
- **AndroidX Platform Updates:** Updated Activity Compose, Lifecycle, Navigation Compose, DataStore, Core KTX, SplashScreen, and Hilt Compose integration to compatible newer releases.
- **Runtime/Test Library Updates:** Updated coroutines, Kotlin serialization, Coil, Robolectric, MockK, Turbine, and Tukaani XZ.

### Maintenance
- **Hilt Compose Migration:** Moved `hiltViewModel` imports to the newer lifecycle ViewModel Compose artifact introduced by AndroidX Hilt 1.3.0.
- **Upgrade Verification:** Verified the dependency set with debug Kotlin compilation and debug unit tests.

## [0.6.7] - 2026-05-13

### UI & UX
- **Complete Archive UX:** Replaced the basic ZIP-only archive action with create/extract dialogs for ZIP and 7z, including archive naming, format selection, destination choices, and password entry.
- **Archive Viewer Password Flow:** Archive browsing now supports password-protected ZIP and 7z files with an in-view password prompt and retry path for encrypted archives.
- **Back Gesture Priority:** Fixed Browser back handling so system back gestures and toolbar back first close transient UI state such as dialogs, sheets, search, expanded FAB menus, and file selections before navigating away from the current folder.
- **Selection Back Fix:** File selections now clear on back instead of unexpectedly popping folder history, matching the visible close-selection affordance in the top bar.
- **Back Stack Memory:** Browser entries opened from Storage Dashboard, Recent Files, and Quick Access now preserve the originating screen, so the back gesture returns to the last visible view instead of collapsing to Home or detouring through a synthetic folder root.
- **Operation Refresh:** Completed file operations now automatically refresh the visible folder or category so newly created, moved, copied, archived, extracted, or deleted items appear without a manual pull-to-refresh.
- **Category Tab Swipes:** Category folder tabs can now be switched by swiping horizontally across the file area, in addition to tapping the tab row.
- **Recent Files Simplification:** Removed folder tabs and file-type chips from Recent Files to avoid heavy client-side filtering, odd calculations, and refresh edge cases.
- **Recent Files Stability:** Duplicate MediaStore rows now render safely in Recent Files, and sticky date headers stay below the top app bar while scrolling.

### Testing
- **Archive Password Coverage:** Added tests for password-protected ZIP and 7z create/list/extract flows, wrong-password errors, and archive progress completion.
- **Navigation Regression Coverage:** Added regression tests confirming browser back clears selected files before consuming folder navigation history, category back delegates to app-level navigation, external browser entries can skip seeded root history, browser fallback chooses app-stack pop before Home-pager fallback, and completed file operations reload the current folder.

### Dependencies
- **Open Archive Libraries:** Added Zip4j 2.11.6 for encrypted ZIP support and documented Zip4j, Apache Commons Compress 1.28.0, and Tukaani XZ 1.10 in the Open Source Licenses screen.

## [0.6.6] - 2026-05-13

### UI & UX
- **Archive Support:** Added first-class ZIP and 7z support, including archive creation, extraction, safe metadata viewing, and an in-app archive browser that lets users inspect archive contents without extracting them.
- **Archive Browser Actions:** Added file-browser actions to compress selections to ZIP, extract archives in place, and extract archives into a dedicated folder.
- **Category Gesture Lock:** Disabled Home/Browser pager swipes while viewing category screens so Images, Videos, Audio, Docs, Archives, and APKs remain isolated until the user explicitly navigates back.
- **Stale Content Flash Fix:** Cleared previous folder/category file lists immediately when navigating to a new folder or category, preventing old contents from flashing during async debug builds and slower reloads.
- **Folder Tabs for Categories:** Added horizontal folder tabs to category views, with an `All` tab first and per-folder tabs grouped by containing folder so large categories are easier to browse.
- **Recent Files Folder Tabs:** Added the same folder-tab experience to Recent Files, preserving date grouping while letting users narrow recents by containing folder.
- **Recents Type Filters:** Added file-type chips for Recent Files, including Images, Videos, Audio, Docs, Archives, and APKs.
- **Richer Folder Context:** Folder tabs now show item counts and total size, and single-file Recent selections can jump directly to the containing folder.

### Architecture
- **Archive Engine:** Integrated Apache Commons Compress with an archive manager, repository APIs, foreground bulk-operation support, safe extraction checks, and ZIP/7z runtime dependencies.

### Testing
- **Archive Coverage:** Added unit coverage for archive format detection, ZIP/7z create-list-extract flows, safe extraction path rejection, and keep-both conflict handling.
- **Navigation Regression Coverage:** Added tests confirming folder and category navigation clear stale file lists while the new destination is loading.
- **Folder Tab Coverage:** Added regression tests for folder-tab grouping, duplicate folder names, tab-filtered selection, recents type filters, refresh/search resets, and load-more behavior.

## [0.6.5] - 2026-05-12

### Architecture
- **File Operation Decomposition:** Extracted copy/move execution into `FileTransferEngine`, conflict detection into `FileConflictDetector`, and shared mutation cleanup into `MutationFinalizer`.
- **Search Reuse:** Added a shared local search helper for Recent Files and Trash debounce/filter behavior.

### Performance
- **Category Storage Queries:** Reworked category-size calculation into exact per-category MediaStore scans and reduced volume-selection bind parameters.
- **External File Staging:** Moved open/share staging copies onto `Dispatchers.IO` to avoid blocking callers during large-file access.
- **Gesture Navigation Smoothness:** Removed the browser screen's duplicate full-screen horizontal drag detector so the shared Home/Browser pager exclusively owns swipe gestures, reducing touch competition with scrolling, pull-to-refresh, and file-list interactions.

### UI & UX
- **Swipe Quick Access Restoration:** Fixed Home-to-Browser swipes so they restore the last opened folder from persistent browser preferences, while storage summary bar taps continue to open the internal storage root without overwriting that saved quick-access location.
- **Category Navigation Isolation:** Hardened pager restoration, saved-state restore, and category breadcrumbs so category views remain labeled as Images, Videos, Audio, Docs, Archives, or APKs instead of leaking or restoring the last opened folder path.

### Media
- **PDF Thumbnails:** Added a Coil-backed PDF thumbnail fetcher using Android `PdfRenderer`.

### Maintenance
- **Regression Gates:** Added coverage for the refactored file-operation, MediaStore, share, and thumbnail paths, and verified unit tests, lint, and release assembly locally.
- **Task Tracker Cleanup:** Removed completed remediation items from `TASKS.md`.

### Testing
- **Test Coverage Expansion:** Added comprehensive unit tests for `ClipboardDelegate`, `NavigationDelegate`, and `DeleteFlowDelegate`, covering complex UI state and edge cases.
- **Service Lifecycle Validation:** Implemented integration tests for `BulkFileOperationService` and `BulkFileOperationCoordinator` to ensure correct foreground service lifecycle and event handling.
- **Crypto Reliability:** Added robust test cases to `TrashManagerTest` validating KeyStore/PBKDF2 round-trip interactions and graceful degradation on corrupted metadata.
- **Suite Stability:** Addressed legacy compilation errors and resolved multiple flakiness issues enabling a stable, fully passing test suite.

## [0.6.4] - 2026-05-10

### UI & UX
- **Premium Progress Engine:** Implemented a frame-clock-driven interpolation system for buttery-smooth progress visualization. Features exponential smoothing, velocity clamping, and a 300ms debounce to eliminate jitter and flickering.
- **Visual Feedback:** Added instant-snap completion feedback and status-aware final fill colors (Success: Green, Failure/Cancel: Red) with graceful auto-dismissal.
- **Selection Menu Refinement:** Replaced the generic selection "More" menu in Recent Files with a premium, pill-based design consistent with the core file browser.
- **Thumbnail Performance:** Configured a persistent 50MB+ disk cache and integrated `ContentResolver.loadThumbnail` for video files. By leveraging the Android system's built-in MediaStore thumbnail cache, video previews are now nearly instant and persist across app restarts, eliminating expensive on-the-fly decoding.
- **Grid Thumbnail Optimization:** Removed hardcoded size limits from `FileGrid` media previews. Coil now dynamically bounds memory to the exact layout constraints, drastically reducing memory consumption and stopping aggressive cache evictions. Added crossfade animations and a subtle loading placeholder to ensure buttery-smooth scrolling without jarring pop-ins.
- **Unified Pager Architecture:** Transitioned the core app navigation to a `HorizontalPager` system. The Home and Browser screens now exist side-by-side, enabling a high-end, real-time "live" preview as you swipe between them.
- **Swipe-to-Peek Performance:** Optimized the navigation viewport to keep both screens composed in memory, ensuring zero-latency transitions and eliminating blank flickers during gestures.
- **Smart Session Restoration:** Refined the "last-opened" folder logic. Swipe gestures and app restarts now restore your previous browser session for continuity, while clicking the storage summary bar intelligently defaults to the main internal storage for quick access.
- **Layout Stability:** Locked pill geometry using Canvas drawing to prevent shape distortion. Fixed text alignment and resolved overlapping toolbar button issues.
- **Random Fake File Generator:** Implemented a new utility to generate files of any size and extension filled with pseudo-random data.
  - Features a background operation pipeline using `BulkFileOperationService` for large file creation.
  - Added a dedicated `CreateFakeFileDialog` with size unit selection (KB, MB, GB).
  - Integrated into the file browser FAB menu.

### Architecture
- **State Hardening:** Updated `BrowserViewModel` to track terminal operation states and start times, enabling decoupled UI animation lifecycles.

### Correctness & Reliability
- **Properties Dialog:** Fixed a bug where the Properties action in Recent Files was non-functional. It now correctly computes and displays detailed file metadata.
- **Recent Files Ranking:** Fixed a bug where recently copied files with old modification dates were buried in the "Recent Files" list. Queries now sort by the maximum of `date_added` and `date_modified` to ensure new copies and downloads always appear at the top.

## [0.6.3] - 2026-05-10

### UI & Rendering
- **Appearance Settings Overhaul:** Reimagined theme and accent color selectors with a modern horizontal layout inspired by Material Design 3.
  - Theme modes now use icon-in-container layouts with labels and distinct selection shapes.
  - The accent picker is now a scrollable row of color circles with "squircle" selection markers and long-press support for the full color picker.
- **Enhanced File Visuals:** 
  - Replaced generic file icons with descriptive vector icons for audio, archives, documents, and code.
  - Implemented robust thumbnail fallbacks using `SubcomposeAsyncImage` to gracefully handle missing media previews.
  - Added a "Show Thumbnails" toggle in Settings to optionally disable all media previews for a minimal look.
- **Streamlined File Operations:**
  - Redesigned the clipboard status "pill" to double as a real-time progress indicator with a filling background and remaining size display.
  - Polished the copy/move/paste flow with a persistent status pill and optimized action button placement.
  - Introduced a Clipboard Management dialog for viewing and removing individual items.
- **Polished Feedback:** Upgraded Snackbars to Material 3 Expressive standards with tonal colors and squircle shapes, managed by a new global host.

### Security
- **Platform Cleanup:** Removed unreachable SDK guards redundant on `minSdk 30`.

### Architecture
- **Foreground Service Hardening:** `BulkFileOperationService` now captures `startId` locally to prevent stuck notifications and features improved cancellation guards to reduce unnecessary IPC.
- **State Management:**
  - `ClipboardDelegate` now clears state immediately after paste to prevent duplicate operations.
  - `HomeViewModel` now utilizes a single source of truth for time-anchored data, eliminating redundant object instations.

### Correctness & Reliability
- **Query Optimization:** `MediaStoreClient` now filters category queries by volume at the SQL level, significantly improving performance on multi-volume devices.
- **State Fixes:** Resolved "stuck" loading indicators in the Browser and Recent Files by ensuring immediate state cleanup after bulk handoffs.

### Build & CI
- **ProGuard Hardening:** Unified serialization keep rules under a blanket `@Serializable` rule for all persisted data.
- **Notification Aesthetics:** Swapped the launcher icon for a monochrome alpha-only drawable in foreground service notifications for better system integration.

## [0.6.2] - 2026-05-03

### UI
- **Material 3 Expressive Selection:** Replaced the legacy top-bar selection action icons with a custom `FloatingSelectionToolbar` that emulates the Jetpack Compose Material 3 Expressive design, utilizing connected segmented shape morphing (`SplitButtonGroup`) for selection actions and a detached FAB for secondary actions.
- **Selection Improvements:** Added **Invert Selection** functionality to the selection action bar. The app now also dynamically calculates and displays the **Total Size** of all currently selected items directly within the top bar during selection mode.
- **Top Bar Cleanup & Restoration:** Restored Settings, Search, and Sort actions to the top-level app bar as split buttons for immediate accessibility, while preserving the clean Material 3 Expressive layout.
- **Constrained Operation FAB:** Bounded the active file operation `ExtendedFloatingActionButton` to a maximum width to prevent overflow clipping during long file transfers.

### Performance
- **Shared Mutation Path Ancestors:** Trash and filesystem mutations now use the shared `PathSafety.pathWithAncestors` implementation, removing duplicate ancestor-list allocation logic from the hot mutation path.
- **Browser Preferences IO Dispatch:** Browser presentation preferences now flow from the DataStore layer on `Dispatchers.IO`, avoiding first-read disk work on the main thread while keeping navigation state updates deterministic.
- **Recent Files Provider Pagination:** Recent file paging now pushes `LIMIT`/`OFFSET` to MediaStore queries and scopes queries by storage root where possible, avoiding client-side scans through every previous page.
- **MediaStore Stat Reduction:** Recent-file and MediaStore search rows no longer call `File.exists()`/`length()` for each result, reducing per-row filesystem stat overhead on large or external storage.
- **Bounded Folder Stats:** Folder stats traversal now checks coroutine cancellation and returns `Partial` after a 100,000-node cap instead of monopolizing the worker pool on huge trees.
- **Capped Transfer Estimation:** Copy/move byte estimation now uses a cancellable bounded traversal before transfer starts, avoiding unbounded duplicate walks for very large directory moves.

### Testing
- **Performance Regression Coverage:** Added coverage for folder-stat node-limit behavior and reran the JVM suite plus release assembly for the 0.6.2 performance pass.

## [0.6.1] - 2026-04-26

### Security
- **Trash Metadata Crypto Markers:** New encrypted trash metadata now records whether it used the Android KeyStore or PBKDF2 fallback key, while legacy unmarked metadata remains readable.
- **Trash Crypto Hardening:** PBKDF2 fallback derivation now uses 600,000 iterations, crypto keys are resolved per operation instead of cached across invalidation, and deterministic cipher failures fail fast instead of burning retries.
- **Trash Copy Integrity:** Trash and restore fallback copies now verify source/destination integrity with recursive SHA-256 checks before deleting the original side.
- **Destructive Path Safety:** Trash and permanent-delete paths now reject symlinked entries through shared path validation to reduce traversal and TOCTOU exposure.
- **Device Migration Warning:** Trash metadata that depends on a missing hardware-bound KeyStore key is preserved and surfaced as a user-visible migration warning instead of being silently deleted.

### Reliability
- **Foreground Trash/Delete Operations:** Browser and Recent Files trash/permanent-delete flows now start foreground bulk operations, giving long-running destructive work the same lifecycle protection as copy/move.
- **Bulk Operation Orchestration:** Coordinator startup now rolls back cleanly on service launch failure, cancellation carries operation IDs to avoid stale cancels, terminal events are replay-safe, and the service removes foreground notifications promptly on cancel.
- **Browser Operation State:** Browser operation status now stays aligned with foreground operation state across completion and recreation races; `ClipboardDelegate` remains a single-purpose initiator with event consumption centralized in the ViewModel.
- **Home Timeout Semantics:** Home analytics timeouts now keep prior complete per-volume category data instead of publishing a newly partial map as complete.
- **Folder Stats Retry Guard:** Permanently unavailable folder-stat paths now stop requeueing after bounded retries while preserving injected/test-owned worker scope cleanup.
- **Release Error Visibility:** `AppLogger.e` now logs in release builds so production failures in trash, storage, and file-operation code paths no longer disappear silently.
- **Recent Files Trash Consistency:** Recent Files deletions now follow the shared foreground trash/delete pipeline, matching Browser behavior for long-running destructive operations.
- **Scoped Volume Preload:** `DefaultVolumeProvider` now uses an injected application coroutine scope instead of `GlobalScope`, keeping storage-root preloading lifecycle-aware and test-friendlier.
- **Folder Stats Test Hang Fix:** Eliminated a late-suite JVM test stall caused by `FolderStatsStoreTest` racing a hot `SharedFlow` subscription against background folder-stat emissions.
- **Folder Stats Worker Cleanup:** `DefaultFolderStatsStore` now exposes explicit scope shutdown for tests, preventing its private worker coroutines from lingering past test teardown.
- **Isolated DataStore Tests:** Browser preferences and storage-classification repository tests now use per-test `DataStore` instances, removing shared on-disk state that could overlap across the suite.

### Performance & UI
- **Stable Lazy Item Identity:** Browser, Home, Recent Files, and Trash lists now use stable model-backed keys, reducing list churn and preserving scroll and animation identity during refreshes and resorting.
- **Composable Slot Reuse Hints:** Added `contentType` discriminators to the main lazy file and trash collections so Compose can recycle item content more predictably.
- **Compose State Stability Audit:** Confirmed `BrowserState` is explicitly marked `@Immutable`, keeping the remediation tracker aligned with the current implementation.

### Concurrency
- **Thread-Safe Volume Prompt Suppression:** Home screen prompt suppression now uses a concurrent key set, avoiding races during rapid volume refresh and classification changes.

### Maintenance
- **Task Tracker Sync:** Marked the completed remediation items in `TASKS.md` and advanced the project metadata to `0.6.1`.
- **Regression Coverage Hardening:** Tightened the affected coroutine and persistence tests with eager collectors, explicit teardown, and timeout-bounded assertions so future concurrency regressions fail fast instead of hanging the run.
- **Security/Correctness Tracker Sync:** Marked the 0.6.1 Security & Privacy and Correctness & Reliability remediation items complete after unit and release-build verification. The clipboard duplicate-consumption item was confirmed already resolved by single-owner ViewModel event handling and covered by foreground-operation regression tests.

## [0.6.0] - 2026-04-19

### Browser UX
- **Selection Action Prioritization:** Reordered the selection top bar so high-priority actions stay visible in this order: `Copy`, `Cut`, `Delete`, `Edit`, and `More`, while lower-priority actions move into the overflow menu.
- **Cleaner Selection Overflow:** Moved `Properties`, `Share`, and `Select All` into the 3-dot menu during selection to reduce crowding and keep the action bar focused.
- **Simplified Folder Subtitles:** Browser folder subtitles now show only file count and folder size. Access-status wording like `Limited access` no longer appears inline in the file list/grid.
- **Timestamp Detail Upgrade:** Browser rows now show modified date and time instead of date alone, making it easier to compare recent file changes at a glance.

### File Operations
- **In-App Progress FAB:** Copy and move operations now replace the normal create FAB with a progress-aware Material floating action button that reflects the current operation and doubles as a cancel affordance.
- **Operation Status Feedback:** Completion, cancellation, and failure states now surface through the browser snackbar flow, giving file operations visible feedback without blocking the UI.

### Properties
- **Properties-Only Access Detail:** Limited/partial access details remain available from the Properties surface instead of cluttering subtitles, keeping the main browser rows compact while preserving the extra metadata when needed.

### Performance
- **Volume Discovery:** Offloaded Android `StatFs` reads from the main thread during app launch, eliminating cold-start jank and reducing ANR risk on slow external storage.
- **Home Screen Analytics:** Introduced a 5-minute TTL cache for expensive folder statistics and recent file queries to prevent battery drain and latency when rapidly resuming the app.
- **Staged File Cache Bound:** Implemented an automatic cleanup routine for temporary share/open files, enforcing a 500MB hard limit and 24-hour expiration to prevent silent storage exhaustion.

### Correctness & Reliability
- **Trash Partial Move Atomicity:** Trashing operations that encounter errors mid-batch (e.g., failed to read an individual file) now explicitly report the count of successfully trashed files to the UI, avoiding misleading generic failures.
- **Dynamic Date Anchors:** "Today" and "Yesterday" date headers in the Recent Files and Home screens are now dynamically recalculated on resume, preventing stale grouping if the app is left open across midnight.
- **Search Consistency:** Unified global search across the Recent Files and Home screens, guaranteeing that identical queries return identically filtered, robust results with a consistent 1000-item upper limit.

### Build & CI
- **Release Compilation:** Fixed an issue where invalid `backup_rules` XML exclusions (using the unsupported `cache` domain) and conflicting `allowBackup="false"` settings blocked release APK generation during Lint checks. `allowBackup` is now explicitly enabled with targeted exclusions preserved.

---

## [0.5.9] - 2026-04-19

### UX & Navigation
- **Breadcrumb Alignment:** Fixed a 1px vertical shift that occurred when breadcrumbs appeared in the browser header.
- **Swipe to Browse:** Added a swipe-left gesture to the Home Screen to quickly open the file browser.
- **Swipe to Go Back:** Added a swipe-right gesture to the Browser Screen to quickly navigate back or return to the Home Screen.
- **Improved File List UI:** Redesigned the file list items to match modern file manager aesthetics, featuring larger icons inside circular containers and cleaner, better-aligned typography.
- **Open Source Licenses:** Added a dedicated licenses screen listing all third-party libraries used by the app with their license types and project links.

### Browser UX
- **Instant Folder Subtitles:** Folder rows no longer show `Calculating…`; they render a neutral `Folder` subtitle immediately and upgrade silently when stats arrive.
- **Best-Effort Scoped Storage Stats:** Folder subtitle aggregation now keeps readable counts and size totals even when scoped storage blocks some descendants, preventing `Android`-style folders from standing out as total failures.
- **Limited Access Copy:** Partially readable folders now surface a short `Limited access` hint instead of a generic unavailable/loading state.

### Properties
- **Selection Properties Dialog:** Added a read-only Properties surface for single- and multi-select file/folder selections, exposed from the browser selection 3-dot overflow.
- **Repository-Backed Metadata Summary:** Properties now aggregate file/folder counts, total size, path/location, modification timestamps, hidden-item counts, and access state from the repository layer rather than ad hoc UI logic.

### Architecture
- **Shared Folder Stats Calculator:** Extracted best-effort folder aggregation into a shared calculator so cached browser subtitles and Properties metadata use the same traversal rules.
- **Browser State Wiring:** `BrowserViewModel` and `BrowserScreen` now own explicit Properties dialog state, loading flow, and dismissal behavior.

### Documentation
- **Release Sync:** Synchronized README, development docs, task metadata, and changelog references around version `0.5.9`.
---

## [0.5.8] - 2026-04-12

### Browser UX
- **Folder Metadata Subtitles:** Folder rows in the browser now show aggregate file counts and total size directly under the folder name so users can scan directories without opening each one first.
- **Denser List Layout:** Tightened the folder row spacing to remove excess vertical dead space while keeping the two-line presentation readable.
- **Right-Aligned Modified Date:** The modified-date subtitle now sits on the far right opposite the stats subtitle, matching a more information-dense file-manager layout.

### Performance & Correctness
- **Cached Folder Stats Pipeline:** Added a dedicated folder-stats cache/store so directory totals appear quickly and update incrementally instead of blocking the initial browser render.
- **Visible-Scope Update Filtering:** Browser stat refreshes are now limited to the currently visible directory, preventing unrelated folder updates from causing noisy UI churn.
- **Stale Result Protection:** Folder-stat publishing now guards against older in-flight calculations overwriting fresher results after invalidation or navigation changes.

### File Indexing Rules
- **`.thumbnails` Exclusion:** Parent folder aggregate stats now ignore descendant `.thumbnails` directories so media folders report user-meaningful counts and sizes, while the `.thumbnails` folder itself still shows its own direct metadata when opened.

### Testing
- **Folder Stats Regression Coverage:** Added focused JVM coverage for folder-stat caching, invalidation, and stale-result protection, then re-ran the app JVM suite and debug build successfully.

---

## [0.5.7] - 2026-04-05

### UX
- **Unified Browser Controls:** Replaced the separate sort sheet and grid toggle with a single Material 3 expressive browser controls sheet that manages sort mode, view mode, list zoom, and adaptive grid sizing in one place.
- **Cleaner Layout Tuning:** Browser list density and grid sizing now respond live while browsing, making it easier to dial in a compact or relaxed layout without leaving the file view.

### Persistence
- **Per-Location Layout Memory:** Browser presentation settings now persist alongside sort preferences for folders and categories, including recursive inheritance when applied to subfolders.
- **Smarter Restore Flow:** Opening a folder, category, or volume root now restores the full browser presentation state instead of only restoring sort order.

### Testing
- **Browser Preference Coverage:** Expanded JVM coverage for browser presentation persistence and fallback behavior.
- **Browser UI Coverage:** Added focused Compose coverage for the unified browser controls entry point and supporting browser state updates.

### Documentation
- **Release Sync:** Updated tasks, docs, and website copy to reflect the new unified browser layout controls and bumped the project to `0.5.7`.

---

## [0.5.5] - 2026-03-27

### Testing
- **Shared Repository Test Double:** Consolidated the duplicated `FileRepository` test fakes into a reusable `FakeFileRepository` plus shared storage/file fixture helpers, reducing interface-drift risk across browser, home, recent-files, trash, delete-policy, and storage-scope tests.
- **Data-Layer Coverage Expansion:** Added Robolectric/JVM coverage for `LocalFileRepository`, `DefaultFileSystemDataSource`, `BrowserPreferencesRepository`, `StorageClassificationRepository`, `ShareHelper`, and route serialization to catch regressions in destructive operations, DataStore-backed preferences, classification persistence, and share flows.
- **Flow Assertion Upgrade:** Added Turbine to the test stack and used it for one-shot native confirmation flow assertions in `BrowserViewModel`, improving precision around event delivery timing.
- **Test Suite Verification:** `:app:testDebugUnitTest` now passes with the expanded JVM coverage and the migrated shared test infrastructure.

### Maintenance
- **Task Tracker Sync:** Marked the completed testing checklist items in `TASKS.md` and bumped the project task/changelog version metadata to `0.5.5`.

---

## [0.5.4] - 2026-03-27

### Reliability
- **One-Shot Native Confirmations:** Browser native confirmation requests no longer replay stale `IntentSender` events after collector reattachment, and pending native actions are cleared as soon as the result is handled.
- **Committed Browser Restore State:** Browser navigation persistence now writes the committed destination state after folder/category transitions so process recreation restores the target location instead of the previous one.
- **Truthful Bulk Cancellation:** Foreground copy and move operations now cancel cooperatively through the worker pipeline, emit explicit cancelling/progress states, and only report cancellation after execution has actually stopped.

### Security
- **FileProvider Surface Narrowed:** Replaced broad filesystem `FileProvider` roots with a staged cache handoff model and centralized outbound allowlist validation, blocking cache, app-private, and `.arcile` paths from open/share flows.
- **Unified External File Access:** Added `ExternalFileAccessHelper` so file opening, sharing, and Files-app handoff shortcuts all follow the same guarded external access rules.

### UX
- **Quick Access Handoff Modeling:** `Android/data` and `Android/obb` are now explicit external Files-app handoff entries with dedicated copy and visuals, clearly distinguishing them from folders Arcile can browse directly.
- **Localization Sweep:** Extracted the remaining audited production UI strings from Quick Access, Storage Management, Recent Files, Trash, Home, and shared top bar/filter/sort components into `strings.xml`.

### Performance
- **Path Search Bounds:** Path-scoped browser search now uses bounded traversal with hidden-folder pruning and cancellation checkpoints instead of an unbounded `walkTopDown()` per query.

### Build & Tooling
- **Output API Cleanup:** Removed the internal `VariantOutputImpl` APK renaming dependency from the build, reducing upgrade risk around unsupported AGP APIs.
- **Copy Guardrail:** Added a `checkProductionStrings` verification task to catch new hardcoded production copy in the audited composables.
- **Compatibility Note:** The current AGP/KSP toolchain still requires `android.disallowKotlinSourceSets=false`; this remains documented as an upgrade gate rather than silently preserved.

### Testing
- **Browser Regression Coverage:** Added browser tests covering one-shot native confirmation delivery and committed navigation saved-state behavior.
- **Interface Sync:** Updated repository and coordinator test doubles to cover the new bulk operation progress/cancellation contracts.

---

## [0.5.3] - 2026-03-27

### Security
- **Release Logging Hardening:** Replaced direct `android.util.Log` usage in sensitive file, trash, storage, and sharing flows with a new debug-only `AppLogger`, preventing release builds from exposing internal paths, volume metadata, trash identifiers, and operational failure details through logcat.
- **Sharing/Open Safety:** Preserved the broader `FileProvider` coverage and sanitized open/share failure logging so opening and sharing arbitrary external files continues to work without leaking sensitive filesystem context.
- **Signing Ignore Safety:** Confirmed the repository `.gitignore` entries for `signing.properties` are stored as valid plain-text patterns so signing credentials remain ignored as intended.

### Reliability
- **Trash Restore Verification:** Hardened cross-volume trash restore fallback so copied files and directories are verified before deleting the trashed source. Restore now aborts cleanly if copy verification or source deletion fails, preventing metadata loss and partially restored states.
- **Fresh Storage Metrics:** Added explicit `VolumeProvider.invalidateCache()` support and called it after mutating file and trash operations so `StatFs` values refresh after create, delete, rename, move, copy, trash, and restore flows.
- **Home Screen Timeout:** Wrapped `HomeViewModel.loadHomeData()` in a `15s` timeout and preserved partial results when one of the async storage queries stalls, preventing indefinite loading spinners.

### Architecture
- **Foreground File Operations:** Introduced a foreground-service-backed bulk file operation pipeline with `BulkFileOperationCoordinator`, `BulkFileOperationService`, and manifest service declarations so long-running copy and move operations survive backgrounding more reliably.
- **Delete Flow Consolidation:** Wired the existing `DeleteFlowDelegate` into both `BrowserViewModel` and `RecentFilesViewModel`, removing duplicated delete-policy and confirmation-flow logic.
- **Conflict Name Reuse:** Extracted keep-both naming into a reusable production helper so the app and test suite share the same conflict-resolution behavior.

### Build
- **Release Keeps:** Added explicit R8 keep rules for `AppRoutes**` and `TrashMetadataEntity` to protect navigation route serialization and trash metadata persistence in release builds.
- **Version Bump:** Bumped the app to `0.5.3` (`versionCode 37`) and synchronized release-facing documentation.

### Testing
- **Production Logic Coverage:** Rewrote `LocalFileOperationsTest` to exercise the real production keep-both naming helper rather than a copied local implementation.
- **Home Timeout Regression Test:** Added unit coverage proving the home screen times out gracefully while keeping partial data available.
- **ViewModel Test Wiring:** Updated the browser ViewModel tests to cover the new foreground bulk-operation coordinator dependency.

---

## [0.5.2] - 2026-03-25

### Feature
- **Quick Access Management:** Completely revamped the static "Folders" section on the Home Screen into a dynamic "Quick Access" system. 
  - Users can now explicitly manage their pinned shortcut folders.
  - Added support for users to add any custom local folder explicitly to the Quick Access bar.
  - Implemented deep Storage Access Framework (SAF) integration allowing users to pin 'Scoped Folders' (like Android/data) which dynamically bridges directly into the OEM native files app when tapped.

### Security
- **Permissions:** Restricted `READ_EXTERNAL_STORAGE` permission by adding `maxSdkVersion="29"`, as it's no longer necessary on Android 11+ where `MANAGE_EXTERNAL_STORAGE` is utilized.
- **Data Protection:** Configured `backup_rules.xml` and `data_extraction_rules.xml` to explicitly exclude sensitive application data including `trash_crypto_prefs.xml`, `datastore`, and `analytics` from being backed up to the cloud.
- **FileProvider Sandbox:** Restricted `<external-path>` URI generation capability inside `file_provider_paths.xml` explicitly to standard media directories (Downloads, Documents, Pictures, etc.) instead of the entire external storage root, reducing attack surface.

### Refactoring
- **Codebase Organization:** Renamed heavily utilized UI component files to accurately reflect their exported Compose functions and internal architecture, improving repository readability and developer navigation:
  - `FileManagerScreen.kt` → `BrowserScreen.kt`
  - `MainFoldersGrid.kt` → `QuickAccessGrid.kt`
  - `GlobalSearchBar.kt` → `SearchTopBar.kt`
  - `FileListControls.kt` → `SortOptionDialog.kt`
  - `SettingsComponents.kt` → `SettingsSection.kt`
  - `FileManagerTheme` → `ArcileTheme` (in `Theme.kt` and tests)

### Correctness
- **UI State Stuttering:** Added strict `.debounce(1000L)` and `.distinctUntilChanged()` operators to the storage volume observer inside `RecentFilesViewModel` to completely eliminate UI flickering and redundant database queries during rapid storage classification changes.
- **Documentation Pins:** Fixed `index.html` loading unpinned versions of Tailwind CSS and Lucide Icons, directly pegging their versions to stop unexpected upstream changes from breaking the promotional site.
- **Testing Cleanups:** Standardized `IntentSender` test doubles natively through `mockk` inside JVM environments (removing fragile `sun.misc.Unsafe` bypasses completely), and pruned default unneeded Android examples tests.

### Performance
- **Smart Paste Conflict Scans:** Redesigned directory copy conflict resolution in `FileSystemDataSource` so it strictly identifies top-level collisions rather than exhaustively and recursively walking the entire destination folder tree, removing multi-second thread-blocking freezes for massive directories.
- **Home Screen Load Times:** Fixed a critical performance bug in `MediaStoreClient.getCategoryStorageSizes()` where categories lacking a strict MIME prefix (like Documents) would silently strip all SQL `WHERE` clauses and trigger a raw scan of every single file mapped in Android's MediaStore. Categories now rigidly enforce their extension rules within the `OR` clauses, dropping IO blocking time by over 80% on devices with large media libraries.

### Build
- **ProGuard:** Replaced overly broad ProGuard rules for `kotlinx.serialization` with official, targeted rules. This allows R8 to properly shrink the APK size by removing unused serialization internals while preserving necessary companion objects and serializers.
- **Dependency Alignment:** Updated Jetpack Compose BOM mapping to `2025.02.00` and vertically aligned `lifecycle` and `navigation` variants universally to `2.10.0` ensuring core cross-compatibility runtime guarantees.

---

## [0.5.1] - 2026-03-24

### Security
- **Trash Vault Encryption:** Migrated fallback key generation to use a securely generated, persisted salt instead of a hardcoded string. Re-engineered `TrashCryptoHelper` to use Android `KeyStore` natively with automatic retry logic to eliminate edge case crashes during key generation and cipher initialization. Fixed unstructured JSON sniffing vulnerabilities to prevent random IV collisions.

### Performance
- **Coil Image Memory Overhead:** Reined in severe GC memory thrashing during file list scrolling by explicitly clamping `AsyncImage` requests to a maximum `256px` thumbnail size via `ImageRequest.Builder`.
- **String Allocations:** Substantially reduced UI stuttering by swapping redundant string allocations (`substringAfterLast`) in file list items with the natively pre-calculated `extension` properties exposed via `FileModel`.
- **Media Store Disk I/O:** Bypassed expensive `File.canonicalPath` lookups inside core `MediaStoreClient` mapping arrays by leveraging absolute paths exclusively, dropping unnecessary internal symlink evaluations that bogged down high-density folders.
- **Repository Latency:** Re-architected `loadHomeData` inside the `HomeViewModel` to run asynchronously across parallel `Dispatchers.IO` threads via `coroutineScope` and `awaitAll()`, substantially reducing cold-start fetch times for Home Dashboards.

### Fixed
- **FileProvider Sandbox:** Replaced strict, hardcoded standard directory entries (`Download/`, `Documents/`, etc.) in `file_provider_paths.xml` with a global `<external-path>` mapping, preventing `IllegalArgumentException` crashes when users attempted to share or open files located inside arbitrary unmapped external storage folders.
- **Trash Bin Reliability:** Deprecated explicit string-based file path deletion (`DATA`) within the Android MediaStore content resolver inside `TrashManager`. The deletion cycle now correctly reverse-engineers the file's explicit Content URI `_ID`, guaranteeing execution on strict Android 11+ Scoped Storage boundaries.
- **App Launch Hangs:** Implemented a `2000ms` strict Coroutine execution timeout explicitly wrapping the asynchronous `ThemeState` DataStore emission inside `MainActivity.kt`. If the filesystem stalls during cold boot, the Splash Screen unblocks itself gracefully, stopping permanent app loading freezes.
- **UI State:** Resolved a bug in `RecentFilesViewModel` where pagination and error states would be clobbered by async state updates by utilizing captured state blocks.
- **UI/UX:** Corrected the "Today" preset filter in `SearchFiltersBottomSheet` to correctly compute from local midnight rather than strictly a rolling 24-hour subtraction.
- **UI/UX:** Re-ordered `Snackbar` display hierarchy in `TrashScreen` to ensure asynchronous UI feedback completes execution before state invalidation clears the active error stream.
- **UI/UX:** Upgraded `DateUtils` to safely access active UI `ConfigurationCompat` locale settings dynamically instead of defaulting to hardcoded system fallbacks.
- **Data Integrity:** Sanitized underlying filesystem exception logging inside `ShareHelper` to protect and obscure absolute path leakage from public logcats.

### Architecture & Build
- **Coroutines:** Enforced `Dispatchers.IO` launching to wrap previously blocking volume discovery calls inside `VolumeProvider.kt` and stabilized `callbackFlow` receivers against registration race conditions.
- **State Flow:** Updated `BrowserViewModel` native request mechanisms to natively cache `replay = 1` inside `MutableSharedFlow` to survive and recover cleanly from device rotation / configuration changes.
- **ProGuard:** Repaired aggressive ProGuard optimization syntax to correctly keep `@Serializable` class definitions natively, stopping R8 obfuscation crashes.
- **Testing Setup:** Removed legacy `sun.misc.Unsafe` internal bypass usages inside JVM testing, officially migrating JVM test environments to `io.mockk:mockk` and standardizing `IntentSender` test doubles.

---

## [0.5.0] - 2026-03-21

### Security
- **Trash Vault Encryption:** Replaced the plain-text `.arcile/.metadata` JSON storage with a secure AES256-GCM encryption layer. Keys are dynamically derived via hardware `ANDROID_ID`, ensuring that trash metadata stays perfectly hidden from other apps but fully survives app updates and reinstalls.
- **FileProvider Sandbox:** Closed a structural vulnerability by removing the root `external_root` path from `file_provider_paths.xml`. The `FileProvider` now correctly restricts external URIs strictly to standard public media folders (Downloads, Documents, Pictures, etc.).
- **Signing Credentials:** `build.gradle.kts` now securely loads signing properties from an excluded `signing.properties` file with proper presence validation, falling back to a clean debug configuration rather than failing the entire Gradle sync when keys are missing.
- **Sensitive File Protection:** Explicitly blocked the ability to open or share internal `.arcile` metadata and application cache files via `MainActivity` and `ShareHelper`, preventing unintended system exposure.

### Changed
- **Target Platform:** Completely dropped support for Android 10 (API 29). `minSdk` officially raised to 30 (Android 11) to eliminate all scoped storage crash loops and align natively with `MANAGE_EXTERNAL_STORAGE` architecture.
- **Code Quality:** Completely eliminated unstructured `org.json.JSONObject` usage. Extracted legacy JSON parsing into strictly-typed `@Serializable` entities across `StorageClassificationRepository`, `TrashManager`, and `MediaStoreClient` leveraging `kotlinx.serialization`.     
- **Search Robustness:** Removed the arbitrary `.maxDepth(10)` limit on path-scoped searching. Deeply nested repository or archive folder structures can now be fully traversed, constrained solely by the 1000-item memory limit.
- **Architectural Cleanup:** Decoupled platform-specific `IntentSender` objects from ViewModel `State` data classes. Native confirmation requests are now emitted via a one-shot `SharedFlow`, improving testability and adhering to MVI/Unidirectional Data Flow principles.
- **Build Hardening:** Added specific R8/ProGuard keep rules for `kotlinx.serialization` and custom Coil fetchers to ensure release build stability.
- **UI/UX Polish:** Replaced hardcoded padding and margin components across `HomeScreen.kt` and `StorageSummaryCards.kt` with strictly bounded `MaterialTheme.spacing` tokens to guarantee Material Design 3 scale compliance.
- **Localization:** Extracted hardcoded "Delete" and "Cancel" strings in confirmation dialogs to standard `stringResource` references.      
- **Accessibility:** Extracted massive swaths of hardcoded English texts, labels, and iconography `contentDescription` properties into `strings.xml`, instantly granting full TalkBack accessibility scaling for vision-impaired and non-English users.

### Refactored
- **Home Screen Architecture:** Severed `MainFoldersGrid` dependency on `Environment.getExternalStorageDirectory()`. The component now strictly reads folder targets injected by the ViewModel and dynamically filters out nodes that do not physically exist on the device.
- **Code Quality:** Extracted the massive duplicated Pull-to-Refresh indicator layout logic trapped inside `HomeScreen.kt` and `FileManagerScreen.kt` into a new, universally shared `ArcilePullRefreshIndicator.kt` component.
- **Error Handling:** Introduced typed `FileOperationException` (AccessDenied, IOError, NotFound, Unknown) replacing generic Exception returns in the repository layer to allow granular error recovery.

### Fixed
- **Recent Files Affordance:** Added a fully wired `PullToRefreshBox` wrap to `RecentFilesScreen` to finally expose the previously inaccessible `onRefresh` manual action to users.
- **UI Performance:** Hoisted `Calendar` and `SimpleDateFormat` logic out of recomposition scopes in `HomeScreen` and `RecentFilesScreen` to prevent redundant instantiation and garbage collection.
- **Date Formatting:** Introduced `rememberDateFormatter` in `DateUtils.kt` for reliable, locale-aware date string updates across the entire application interface.
- **Error Surfacing:** `TrashScreen` now properly surfaces persistent operational failures and errors via unified non-blocking `Snackbar` overlays.
- **Concurrency Safety:** Fixed unstructured concurrency leaks by properly re-throwing `CancellationException` inside Coroutines/Flows globally across the repository and UI layers, ensuring clean job termination.
- **Data Initialization:** Corrected `ThemePreferences` Enum value lookups to safely fallback without throwing exceptions during DataStore initializations.

### Testing
- **UI Instrumentation:** Bootstrapped the `androidTest` layer with Robolectric Compose implementations, introducing `HomeScreenTest` and `NavigationTest` covering core layout visibility.
- **ViewModel Coverage:** Expanded the JVM test suite handling high-risk branches, verifying edge cases including Rename collisions inside `BrowserViewModel`, missing volume destinations within `TrashViewModel` (along with an `assertTrue` import fix), and debounced volume emissions inside `HomeViewModel`.

---

## [0.4.9] - 2026-03-21

### Refactored
- **Clean Architecture:** Completely modularized `LocalFileRepository.kt` into dedicated data sources (`VolumeProvider`, `TrashManager`, `MediaStoreClient`, `FileSystemDataSource`) and introduced Domain-level Use Cases.
- **ViewModel Delegates:** Decomposed the massive `BrowserViewModel.kt` into `NavigationDelegate`, `ClipboardDelegate`, and `SearchDelegate` to cleanly isolate state management.
- **UI Modularization:** Extracted complex inline dialogs and views from `FileManagerScreen.kt` into isolated Compose components, completing the eradication of "God Class" monoliths across the presentation and data layers.

---

## [0.4.8] - 2026-03-21

### Fixed
- **Recent Files Pagination:** Increased visibility of historical records in the Recent Files tab by replacing the arbitrary 50-file limit with an infinite-scrolling offset mechanism.
- **Folder Navigation Delay:** Removed redundant `AnimatedContent` wrappers on the `FileManagerScreen` that were doubling composition loads, guaranteeing instantaneous snaps and stutter-free folder loading alongside the new 5ms indicator delay.
- **Sort Dialog UX:** Fixed the "Sort By" bottom sheet dismissing prematurely upon radio selection before users could toggle the subfolder checkbox. Added active local state tracking with dedicated Apply/Cancel confirmation buttons.
- **Filter Dialog Expansion:** Enforced `skipPartiallyExpanded = true` for the `SortOptionDialog` and `SearchFiltersBottomSheet` so that the dialogs immediately open to full height, preventing buttons and options from being hidden off-screen.

### Changed
- **Material 3 Expressive Filter UI:** Upgraded the `SearchFiltersBottomSheet` to M3 Expressive standards, replacing scattered `FilterChips` with cohesive, sliding `SingleChoiceSegmentedButtonRow` pills.
- **Material 3 Expressive Sort UI:** Upgraded the `SortOptionDialog` to M3 Expressive, introducing robust interactive `Surface` elements that responsively color-fill with `secondaryContainer` highlights upon selection, grounded by a solid `FilledTonalButton`.

### Performance
- **Volume Discovery:** Added a memory cache to `discoverPlatformVolumes()` that precisely invalidates upon receiving explicit storage mount/unmount broadcasts, preventing severe redundant `StatFs` I/O during operations and startup.
- **Search Optimization:** Pushed sort and limit logic for global MediaStore searches into the `ContentResolver` query to prevent memory bloat and unstructured result sets.
- **Home Screen Efficiency:** Debounced and diffed incoming storage volume events to eliminate redundant Home screen data fetches on silent refreshes.
- **Recent Files Performance:** Reduced the initial MediaStore query limit from 500 to 50 for the recent files screen to improve startup speed and reduce memory consumption.
- **Compose Recomposition Fixes:** Added `@Immutable` annotations to `FileModel` and `BrowserState` to prevent excessive recompositions and sorting overhead.
- **List Stability:** Implemented unique string keys for `LazyColumn` and `LazyVerticalGrid` elements to prevent item collision during rapid directory transitions.
- **Refresh Rate Management:** Removed unconditional app-wide overrides for device refresh rates, allowing the system to handle display dynamics properly.

### Smoothness
- **Browser State Retention:** Handled volume updates by diffing the active volume and updating state accordingly, stopping the `BrowserViewModel` from destructively resetting path locations and UI selections whenever a volume re-enumerated.
- **Home Screen Flicker:** Debounced and scoped `isCalculatingStorage` so `HomeViewModel.loadHomeData()` no longer forces UI shimmer and flickering on silent background updates.

### Code Quality & Maintainability
- **Sorting Localization:** Removed hardcoded English labels from `FileSortOption` and migrated `FileListControls` to use proper XML string resources for translation support.
- **Validation Logic:** Eliminated duplicate filename validation checks (`/`, `\`, `..`) in `BrowserViewModel` to ensure the repository remains the single source of truth for file creation rules.
- **Dead Code Removal:** Deleted the deprecated `presentation/SearchFilters.kt` typealias file to prevent confusing domain boundary imports.

### Architecture
- **Dependency Injection:** Hoisted `ThemePreferences` instantiation out of `MainActivity` and correctly wired it into the Hilt DI graph for better testability.
- **State Management:** Extracted `MainActivity` permission handling and reactive states (`_hasPermission`) into a dedicated `MainViewModel`, cleanly separating application UI from core logic.
- **ViewModel Sharing:** `StorageManagementScreen` now natively shares the same `HomeViewModel` instance anchored to the `Home` back stack entry, preventing duplicate `Home` data reloading and visual sync bugs.
- **Dead State Pruning:** Removed the completely unused `StorageMountState` enum and its dependent initialization lines across the app and testing environments.
- **Sort Settings Granularity:** Reworked `BrowserPreferencesStore` and its repository logic to fully support isolating folder sorting rules to a single directory (without corrupting the global state) or allowing subdirectories to inherit via standard propagation.

---

## [0.4.7] - 2026-03-20

### Fixed
- **Splash Screen Reliability:** Resolved an issue where the splash screen could hang indefinitely if the theme preference failed to load; added a `try/finally` safety guard.
- **UI Overflow:** Fixed a layout bug in the Accent Color Selector where the color picker could overflow and become unreachable on small screens; implemented a vertical scroll container.
- **Trash Bin Robustness:** Patched a potential crash in the Trash List when extracting parent paths from malformed or root-level files.
- **External Storage Labeling:** Corrected a missing label for "Unclassified External" storage volumes in the Trash metadata view.
- **Silent Share Failures:** Improved the `ShareHelper` by adding explicit error logging to diagnose failures when launching system share intents.
- **Dependency Resolution:** Fixed a build error caused by an incorrect artifact name for the `material-kolor` library in the version catalog.
- **File Size Formatting:** Corrected `formatFileSize` boundary rounding so values near unit thresholds no longer display awkward outputs like `1024.0 KB`.
- **Browser Path Resolution:** Hardened browser volume-path matching so Android-style `/storage/...` paths resolve correctly regardless of the host file separator used by the runtime.

### Added
- **Accent Color Accessibility:** Enhanced the Accent Color Selector with full accessibility semantics, allowing screen readers to correctly identify and announce selected colors.
- **Localization Polish:** Completed the localization of several previously hardcoded UI elements, including conflict resolution messages and category labels.
- **Automated Test Coverage:** Added new JVM tests for `FormatUtils`, `CategoryColors`, `FileModel`, `StorageInfo`, `HomeViewModel`, `BrowserViewModel`, `RecentFilesViewModel`, and `TrashViewModel`.
- **Compose Component Tests:** Added Robolectric-backed Compose tests for DeleteConfirmationDialog, ArcileTopBar, and EmptyState.

### Changed
- **Internal Category IDs:** Decoupled visible category labels from internal logic IDs, ensuring navigation and filtering remain stable across different system languages.
- **Browser Preferences Abstraction:** Introduced a `BrowserPreferencesStore` interface and wired it through DI so browser state logic can be unit tested without changing app behavior.
- **JVM UI Test Setup:** Enabled Robolectric-backed Compose UI tests in the app module and version catalog, including Android-resource support for JVM test runs.

---

## [0.4.6] - 2026-03-20

### Added
- **Category Search Optimization:** Searching within categories (Images, Documents, etc.) is now natively executed at the database level for significantly faster and more accurate results.
- **Unified Deletion Dialog:** Replaced split deletion prompts with a unified dialog. Includes a "Permanently delete" checkbox that auto-enables and disables itself if the target drive (e.g., OTG/USB) does not support a Trash Bin.
- **Search Everywhere:** Added live, debounced search bars to the *Recent Files* and *Trash Bin* screens.
- **Select All in Trash:** The Trash Bin now supports a "Select All" toggle for bulk restoration or permanent deletion.

### Changed
- **Modernized Filters UI:** The sorting and filtering menu has been overhauled from a generic alert popup into a polished Material 3 `ModalBottomSheet`. 
- **Contextual Search place holders:** Search bars now dynamically reflect their context (e.g., displaying "Search images..." when inside the Images category). 
- **Decoupled Category Sorting:** Navigating into a category now intelligently defaults to sorting by "Date Newest", completely decoupled from the generic A-Z sorting rules applied to standard folders.
- **Hidden File Visualization:** Files and folders starting with a dot (e.g., `.nomedia`) now render at 50% opacity, providing an intuitive, "ghostly" visual cue that they are hidden system elements.

### Fixed
- **Loading Flicker:** Eliminated an artificial rendering delay on the Home Dashboard that caused the loading indicator to briefly flash before content appeared.
- **Scroll Position Reset:** Changing the sort order of a folder or category now correctly scrolls the list back to the top, preventing users from getting lost in large directories.
- **Sorting State Persistence:** Returning to a previously visited folder now correctly remembers and applies the user's specific sorting preference for that directory.
- **Crash Prevention:** Removed a brittle `MimeTypeMap` lookup that could crash the app when encountering unindexed or malformed file extensions, replacing it with safe fallbacks.

---

## [0.4.5] - 2026-03-19

### Fixed
- [UI/UX] Resolved a persistent double-refresh glitch on the **Recent Files** and **Home** screens by removing redundant initialization triggers.
- [UI/UX] Eliminated jarring loading indicator flickers and incorrect empty-state priority app-wide by implementing a unified micro-delayed (5ms) display guard and synchronous loading state transitions in ViewModels.
- [UI/UX] Eliminated app launch UI flickering (briefly showing the default purple theme or permission screen) by explicitly holding the `SplashScreen` active until the DataStore finishes resolving and emitting the user's `ThemeState`.
- [UI/UX] Optimized **Recent Files** screen performance by removing excessive lifecycle-driven refresh events that caused redundant data reloads.
- [UI/UX] Fixed overlapping system bar insets and padding bugs by removing redundant nested `Scaffold` hierarchies in the root `ArcileAppShell`.
- [Smoothness] Resolved animation target key collisions during folder-to-folder transitions in `FileManagerScreen` by upgrading the string concatenation state key to a fully typed `FileManagerContentKey` data class.
- [Security] Patched multiple blind failure states in `LocalFileRepository` (e.g. MediaStore explicit deletion, `.nomedia` trash enforcement, and `StorageStatsManager` faults) by replacing silent empty catch blocks with verbose Android System Log errors.

### Changed
- [UI/UX] Refined loading visibility logic to ensure "silent" background refreshes when content is already cached or present, providing a smoother and less disruptive user experience.
- [UI/UX] Polished the `PermissionRequestScreen` with a Material 3 Expressive Card, centralized icon, and structured typography.
- [UI/UX] Completely redesigned `FileGridItem` to use edge-to-edge images (removing inner padding), forcing a strict `1:1` aspect ratio for the visual container, and locking text to exactly 1 max line. This guarantees every card is perfectly uniform in size regardless of content.
- [Architecture] `StorageDashboardScreen` now shares the existing `HomeViewModel` instance mapped to the `Home` route backstack entry, instantly rendering storage statistics instead of unnecessarily triggering parallel disk reads for a second isolated ViewModel scope.

### Added
- [Performance] Optimized MediaStore queries in `getFilesByCategory` by trusting the index over performing redundant `File.exists()` filesystem stat calls on every row, making large media category loading nearly instant.
- [Motion] Added smooth `animateItem()` entry and reordering animations to all list and grid file views.
- [Testing] Added `LocalFileOperationsTest` to verify the core `Smart Paste` conflict resolution engine, specifically ensuring duplicate file suffixes (` - Copy (1)`) are correctly appended without corrupting user data.

---

## [0.4.4] - 2026-03-18

### Refactored
- [Architecture] Extracted `NavHost` from `ArcileAppShell.kt` into a dedicated `AppNavigationGraph.kt` component for a cleaner entry point.
- [Architecture] Modularized `HomeScreen.kt` by separating large UI elements into `StorageSummaryCards.kt`, `CategoryGrid.kt`, and `MainFoldersGrid.kt`.
- [Architecture] Cleaned up `TrashScreen.kt` by extracting the empty state dialog and trash list components to `components/trash/`.
- [Architecture] Split `PasteConflictDialog.kt` into reusable sub-components, migrating the file comparison UI to a new `ConflictCard.kt`.
- [Architecture] Decoupled `Environment.getExternalStorageDirectory()` framework calls from the Compose presentation layer (`MainFoldersGrid`) by pushing standard folder resolution to the `FileRepository`.
- [Architecture] Removed the direct creation of Android `Intent` objects inside ViewModels (`ShareSelectedFiles`), shifting this responsibility to a newly introduced `ShareHelper.kt` utility invoked from the UI layer.

---

## [0.4.3] - 2026-03-18

### Added
- [Feature] Full Internationalization (i18n): Extracted over 130 hardcoded strings into `strings.xml`, enabling multi-language support across all major screens (Home, Browser, Trash, Tools, Recent Files).
- [Feature] Dynamic Color Generation: Integrated `MaterialKolor` for high-quality dynamic color schemes based on user-selected accent colors.
- [Feature] Type-Safe Navigation: Migrated the entire app to Kotlin Serialization-based navigation, eliminating string-based route fragility.
- [Performance] Analytics Cache TTL: Implemented a 5-minute Time-To-Live (TTL) for storage analytics to prevent redundant recomputations while ensuring data freshness.
- [Architecture] Modular Settings UI: Refactored the Settings screen into isolated components (`ThemeModeSelector`, `AccentColorSelector`) for better maintainability.

### Changed
- [Build] Bumped version to `0.4.3` (versionCode `27`) across `build.gradle.kts`, `CHANGELOG.md`, and project documentation.

## [0.4.2] - 2026-03-18

### Added
- [Documentation] Introduced a new "StorageScope" subsection in `DEVELOPMENT.md` defining the logical contexts used for repository operations (`AllStorage`, `Volume`, `Path`, `Category`).

### Fixed
- [Bug] `moveToTrash` now properly aborts the deletion loop, preserves failure states, and invalidates caching when the underlying copy/delete fallback mechanism fails.
- [Bug] Missing file extensions are now reliably captured in Category Views; expanded the MediaStore SQL query to search via `DATA LIKE '%.ext'` string matching when `MIME_TYPE` yields no results.
- [Bug] Category Views now safely check if `File.isFile` before indexing, preventing arbitrary directories carrying specific extensions (like `folder.zip`) from crashing the UI.
- [Bug] `TrashViewModel` no longer drops the target destination string or item IDs when spawning the Android system permission overlay (Scoped Storage), preventing silent task cancellation.
- [Bug] Detached CoroutineScopes have been scrubbed from `StorageClassificationRepository` data mapping streams, eliminating race conditions.
- [Bug] `BrowserViewModel` now halts UI refreshes if an underlying file deletion or movement throws an exception, allowing the user to actually read the Toast/Snackbar error.
- [Bug] Invalid `volumeId` restores from deep linking now safely fall back to `StorageScope.AllStorage` mapping rather than crashing or freezing the screen.
- [Bug] Resolved missing UI warnings when the user's optimistic `StorageClassification` bypass prompt fails to write to the `DataStore` successfully.
- [Bug] Category storage size calculations now strictly exclude hidden files/folders (`/.`), ensuring consistency with the file browser view.
- [Bug] Trash operations (`emptyTrash`, `restoreFromTrash`) now correctly invalidate the storage analytics cache, preventing stale category sizes on the dashboard.
- [Bug] `Category` storage scope now supports optional `volumeId`, enabling cross-volume media discovery and resolving empty category views when navigating from the Home screen.
- [Bug] Replaced API 26 (Oreo) `File.toPath()` references in path traversal security checks with legacy `canonicalPath` string evaluations, stopping app crashes on Android 7 (Nougat).
- [Bug] Removed empty TargetState lambda calls inside `AnimatedContent` (`FileManagerScreen.kt`) causing silent animation frame drops and strict Compose linting failures.
- [Bug] Fixed outdated mock configurations throwing incorrect String Exceptions instead of structured `DestinationRequiredException` inside `StorageScopeViewModelTest`.
- [Navigation] Standardized `EXPLORER` routes in `ArcileAppShell.kt` to always append the full query parameter set (`path`, `category`, `volumeId`), resolving broken state restoration during cross-screen navigation.
- [UI/UX] Fixed Shimmer animations capturing gradient layouts at zero size, preventing UI from appearing frozen while loading.
- [UI/UX] Fixed `FileManagerScreen` showing a blank "Empty Directory" state instead of displaying the volume root menu when exploring the top-level app storage tier.
- [UI/UX] Optimized `TrashScreen` volume item selections to dismiss the selection dialog overlay immediately, skipping slow upstream view-state delays.
- [Performance] `LocalFileRepository.getCategoryStorageSizes` now immediately returns optimistic DataStore cache hits if available, bypassing massive unnecessary disk/MediaStore I/O calculations.
- [Correctness] Removed `runBlocking` calls from `StorageScopeViewModelTest` mocks to align with coroutine testing best practices and prevent potential test deadlocks.
- [Code Quality] Hoisted `ThemePreferences` initialization to the `MainActivity` level, ensuring lifecycle-aware persistence and resolving compilation errors in theme state collection.

### Changed
- [Build] Bumped version to `0.4.2` (versionCode `26`) and synchronized references across `build.gradle.kts`, `TASKS.md`, and project documentation.
- [Design] Dramatically increased the color opacity mapping (`alpha = 0.70f` and `0.16f`) for all dynamic `secondary` and `secondaryContainer` Light Themes inside `Color.kt` to ensure legibility over bright backgrounds.
- [Accessibility] Explicitly forced `FileGrid` and `FileList` Jetpack Compose rows to broadcast `selected = isSelected` into Android's semantics tree, allowing TalkBack and screen readers to actually announce when a file is checked.
- [Architecture] Stripped silent `{}` default callback initializations for `onDismissDestinationPicker` in `TrashScreen.kt`, enforcing correct explicit navigation wiring down the tree.

---

## [0.4.1] - 2026-03-17

### Added
- [Performance] Implemented a persistent caching layer for storage analytics in `Context.cacheDir/analytics/`, providing zero-latency loading for the Home screen and Storage Dashboard.
- [Architecture] Introduced `DestinationRequiredException` for typed error handling during file restoration operations.

### Changed
- [Performance] Replaced manual filesystem scanning for category sizes with `StorageStatsManager` (API 26+) combined with a highly optimized `MediaStore` fallback. This ensures completely accurate storage size calculations while omitting redundant MIME type queries.
- [Performance] Optimized `MediaStore` queries to perform SQL-level filtering by MIME type, significantly reducing CPU and memory overhead during file discovery.
- [Performance] Transitioned file categorization from hardcoded extensions to native `MimeTypeMap` and `MediaStore` MIME type columns for improved accuracy and future-proofing.
- [Performance] Confirmed `AudioAlbumArtFetcher` correctly utilizes `ContentResolver.loadThumbnail()` (API 29+) with a fallback to `MediaMetadataRetriever` on `Dispatchers.IO`.

### Fixed
- [Bug] Custom trash directories (`/.arcile`) are now aggressively filtered from all search, category, and recent file queries, and trashed files are explicitly deleted from the `MediaStore` index to prevent them from incorrectly appearing in the UI.
- [Bug] Enhanced `moveToTrash` fallback logic to safely retain JSON metadata when file rename operations fail, guaranteeing files can always be correctly restored.
- [Bug] Implemented proactive logging and cleanup of invalid JSON metadata files to prevent corrupted trash metadata from causing parsing crashes.
- [Architecture] Eliminated string-based error matching in `TrashViewModel` by migrating to a robust, typed exception system for all trash operations.
- [UX] Refined cache invalidation logic to only clear the specific affected volume's cache during file operations (create, rename, delete, copy, move), preventing unnecessary global dashboard recalculations.

---

## [0.4.0] - 2026-03-15

### Added
- [Feature] Expanded accent colors to 20 Material Design presets with dynamic Material 3 color scheme generation.
- [Feature] Centralized `EmptyState` component with smooth animations and decorative background elements.
- [Feature] Full-screen soft overlay (scrim) for the Expandable FAB menu with refined fade animations.
- [Theme] Unified shape system using Material Design 3 `extraLarge` tokens for expressive squircles (28dp).
- [Feature] Enhanced `MultiColorStorageBar` with liquid fill animations, smooth segment transitions, and indeterminate "flowing colors" shimmer.
- [Feature] Dynamic usage-based colors (Green/Orange/Red) for OTG and unindexed storage bars to provide immediate visual feedback on capacity.
- [Feature] Refined storage segment visibility with a 0.5% minimum width for small categories and subtle 0.1dp pill-gaps.
- [Theme] Implemented Jetpack SplashScreen API with native Light/Dark mode support using DayNight resource resolution.
- [Motion] Implemented smooth folder-to-folder crossfade transitions in the file explorer using `AnimatedContent`.


### Changed
- [UI/UX] Redesigned Utilities section with a modern horizontal carousel and adaptive squircle ToolCards.
- [UI/UX] Renamed 'Secure Vault' to 'OnlyFiles' across the entire application for better clarity.
- [UI/UX] Improved 'Black' accent color contrast in Dark and OLED modes using light gray highlights.
- [UI/UX] Swapped accent selector layout to position color previews at the trailing edge for better ergonomics.
- [Theme] Audited and refined contrast across all light color schemes, ensuring dark "on-surface" text for better readability.
- [Theme] Re-enabled `surfaceTint` and fixed dynamic color generation to ensure proper Material 3 tonal elevation and accent tones across all surfaces.
- [UI/UX] Corrected `ArcileTopBar` background and scroll behavior to properly support Material 3 tonal elevation.
- [UI/UX] Grounded the folder shortcuts and card surface tokens for better contrast.
- [UI/UX] Improved FAB sub-item alignment, shapes, and contrast using `onSecondaryContainer` tokens.
- [Architecture] Migrated all cards, dialogs, and surfaces to the unified MD3 themed shape system and cleaned up legacy constants.
- [Architecture] Replaced high shadow elevations in components like `EmptyState` with cleaner Material 3 `tonalElevation` logic.

### Fixed
- [Bug] Fixed FAB menu overlay only covering a partial area by moving the scrim to the main full-screen content layer.
- [Bug] Standardized UI spacing and resolved expressive menu clashes between `HomeScreen` and `ArcileTopBar`.
- [Bug] Resolved storage bar "Other" category overlap by ensuring distinct segment weights and clipping.
- [UI/UX] Standardized `EmptyState` and `LoadingIndicator` (Material 3 Expressive) usage across all main screens.
- [UI/UX] Implemented non-blocking, centered loading overlays for Storage Management and Dashboard to ensure internal storage remains visible during background calculations.
- [UI/UX] Fixed loading and empty state inconsistencies in `HomeScreen` and `StorageManagementScreen`.
- [UI/UX] Added dedicated `EmptyState` handling for Storage Dashboard when indexed volumes are unavailable.
- [Bug] Fixed regression where `BackHandler` import was missing in `FileManagerScreen.kt`.
- [Bug] Fixed FAB menu dismissal failing when tapping outside the menu; moved the scrim to the top of the Z-order.
- [Bug] Resolved OTG classification prompt getting stuck after user interaction via synchronous optimistic state updates.
- [Bug] Improved background reload job management in `HomeViewModel` to prevent redundant or out-of-order state updates.

### Hotfixes (Beta Release Blockers)
- [Security] **Data Privacy:** Fixed sensitive file paths being written to system logs during orphaned metadata deletion in `LocalFileRepository`.
- [Bug] **Recent Files Accuracy:** Fixed a query sorting bug where older files modified recently were pushed off the Recents list.
- [Bug] **DataStore Crash Prevention:** Handled read errors in `BrowserPreferencesRepository` using a `.catch` operator to emit a safe fallback.
- [Bug] **JSON Parsing Safety:** Fixed swallowed parsing exceptions in `StorageClassificationRepository` to log and safely drop corrupted data.
- [Bug] **Error Surfacing:** Repository failures in `HomeViewModel` are properly propagated to the UI state rather than silently ignored.
- [Bug] **Trash State Reliability:** Preserved transient UI states (selected files and errors) when `TrashViewModel` initiates a load instead of wiping them.
- [Build] **KSP Alignment:** Resolved Kotlin Symbol Processing plugin mapping mismatch, successfully matching version `2.2.10-2.0.2` for build stability.
- [Bug] **Smart Paste Overwrite Safety:** Added a fail-fast mechanism to prevent `LocalFileRepository` from silently deleting pre-existing target files when an explicit conflict resolution is missing.
- [Bug] **Deep Directory Conflict Scans:** Upgraded `detectCopyConflicts` to intelligently recurse through directory trees, flagging nested file collisions instead of only checking the top-level folder names.
- [Bug] **State Persistence:** Fixed an initialization gap where restored UI states (path, volume, category) from process death were loaded from state but not explicitly written back to the ViewModel state, leaving the explorer uninitialized.
- [Bug] **Delete Policy Fallbacks:** Modified the `evaluateDeletePolicy` engine to inspect volume lookup failures directly and default to safe mixed-deletion states, rather than implicitly assuming no-volume and permanently deleting files.
- [Bug] **Trash Restore Traps:** Patched an empty-state lock-in when a user tried to restore a file from a removed drive; the app now displays a dismissible message instead of an un-closable, broken list if no valid destinations exist.
- [Bug] **Intent Sharing Crashes:** Fixed a Jetpack Compose lifecycle context lookup failure (`navController.context` nullability) that crashed the app when sharing multiple files; updated to explicitly use `LocalContext.current`.

### Improved
- [Testing] Added unit test verification for optimistic storage classification updates in `HomeViewModel`.

---

## [0.3.9] - 2026-03-15

### Added
- [Feature] Full SD-card-vs-OTG storage classification workflow, including removable-volume prompting, persistent classification overrides, and Settings-based storage management.
- [Feature] Per-volume trash support for internal storage and SD cards using dedicated `.arcile/.trash` and `.arcile/.metadata` roots on each permanent volume.
- [Feature] Explicit restore destination fallback when a trashed item's original permanent volume is unavailable.
- [Feature] Refreshed About screen with a more complete and polished product overview.

### Changed
- [Architecture] Consolidated external storage behavior around `StorageKind` and policy-driven filtering instead of inferring behavior from raw removability.
- [Architecture] Unified browser, recents, categories, dashboard, search, and trash around indexed-vs-browsable volume policy helpers in `LocalFileRepository`.
- [Behavior] SD cards now behave as first-class permanent storage across indexed surfaces, while OTG and unclassified removable storage remain browsable-only and permanently deleted.
- [Behavior] Release readiness for SD-card secondary-storage use is now backed by targeted fixes and updated unit coverage.

### Fixed
- [Bug] Temporary storage no longer leaks into indexed dashboard flows or opens misleading per-volume dashboard states.
- [Bug] First-time classification of newly detected removable storage now persists `lastSeenName` and `lastSeenPath` correctly.
- [Bug] Delete flows now consistently split trash-enabled permanent storage from permanent-delete-only temporary storage across Browser and Recent Files.
- [Bug] Global search, categories, dashboard totals, and recent files now consistently exclude OTG and unclassified removable volumes while keeping path browsing/search available.
- [Bug] Trash restore fallback now exposes a destination picker instead of failing silently when the original permanent volume is unavailable.

### Improved
- [UX] Browser root lists, Home cards, and in-browser messaging now better communicate whether a volume is an SD card, temporary USB storage, or unclassified external storage.
- [Testing] Added focused test coverage for delete-policy behavior, first-time removable classification persistence, and restore-destination fallback, with `./gradlew testDebugUnitTest` passing for the release state.

---

## [0.3.8] - 2026-03-14

### Added
- [Feature] External storage classification system with `INTERNAL`, `SD_CARD`, `OTG`, and `EXTERNAL_UNCLASSIFIED` policies persisted in DataStore.
- [Feature] Dedicated Storage Management screen in Settings for classifying removable volumes as SD card or OTG and resetting user classification.
- [Feature] Stable `storageKey` identity for mounted volumes using UUID-first and canonical-path fallback matching.

### Changed
- [Architecture] Centralized indexed, browsable, and trash-enabled volume filtering in `LocalFileRepository` so indexed surfaces no longer infer behavior from `isRemovable`.
- [Behavior] Removable volumes now default to temporary/unclassified behavior until the user classifies them.
- [Behavior] Home prompt actions and classification persistence now use stable storage identity instead of transient volume IDs.

### Fixed
- [Bug] Categories, dashboard totals, recent files, and global search now consistently exclude OTG and unclassified removable storage while still allowing browser/path search access.
- [Bug] Delete flows in Browser and Recent Files now correctly block mixed permanent-storage and temporary-storage selections and use permanent delete for OTG/unclassified storage.
- [Bug] Trash routing now rejects temporary storage and keeps trash behavior limited to internal and SD-card policies.
- [Bug] Home per-volume category loading now respects indexed-volume semantics, with updated tests covering the new default behavior.

### Improved
- [UX] Browser now shows an informational banner while browsing temporary storage, clarifying that it is not indexed and that deletion is permanent.
- [UX] Storage dashboard now shows a note when temporary mounted volumes are excluded from indexed insights.
- [Testing] Updated unit tests and release verification now pass with `./gradlew testDebugUnitTest`.

---

## [0.3.7] - 2026-03-13

### Added
- [Feature] Full multi-volume storage awareness across the app via reactive mounted-volume tracking for internal storage, SD cards, and OTG USB drives.
- [Feature] Manual pull-to-refresh on the Home screen so storage, categories, and recent files can be reloaded without restarting the app.

### Fixed
- [Bug] External storage mount and unmount changes now refresh the app live instead of only appearing after a restart.
- [Bug] Browsing an SD card or OTG volume no longer leaks internal-storage navigation context into breadcrumbs, root browsing, categories, or recent files.
- [Bug] Storage dashboard calculations are now scoped correctly per volume, including drive labels, occupied space, and category breakdowns.
- [Bug] "Recent Files" and the "See All" screen now respect storage scope correctly and refresh reliably after returning to the screen.
- [Bug] Background Home refreshes no longer flash the pull-to-refresh indicator or interfere with top bar expansion behavior.

### Improved
- [Architecture] Replaced the old single-root storage model with scoped storage queries and richer volume metadata (`id`, mount state, removable flag, path, and display name).
- [Architecture] Browser, Home, Storage Dashboard, Categories, and Recent Files now share a common volume-aware storage model.
- [Performance] Reduced repeated per-volume category scans on Home by reusing cached scoped category results until the mounted-volume set changes.
- [Safety] Trash remains limited to internal storage for now, with removable-volume deletes explicitly blocked from using Trash to avoid inconsistent restore behavior.

---

## [0.3.6] - 2026-03-13

### Fixed
- [Bug] "Recent Files" list failed to float newly copied duplicate items to the top; fixed by primarily querying `MediaStore.Files.FileColumns.DATE_ADDED` in `LocalFileRepository.getRecentFiles` with a secondary `DATE_MODIFIED` fallback.
- [Bug] "Recent Files" list reported 0-bytes for freshly duplicated files because MediaStore asynchronous indexation lagged behind execution; implemented a native `java.io.File(path).length()` bypass.

### Improved
- [Architecture] Extracted massive `FileManagerScreen.kt` "God Composable" logic into hyper-specific isolated modular components under `presentation/ui/components/` including dialogs, lists, grids, and menus for drastically improved maintainability.

---

## [0.3.5] - 2026-03-13

### Added
- [Feature] Persistent Sorting Filters: File browsing sorting preferences are now persisted per directory or globally via DataStore.
- [Feature] Subfolder Sorting Application: Users can now explicitly choose to apply a sorting filter dynamically to a selected folder and all its subfolders directly from the Sort option dialog.
- [Feature] Category Sort Defaults: The categories on the home screen now automatically configure sorting to "Date Newest" instead of alphabetically by default. 

### Improved
- [UX] Process death states explicitly re-apply "Date Newest" sort configuration cleanly when recovering active Category screens.

---

## [0.3.4] - 2026-03-11

### Added
- [Feature] Smart Paste Conflict Resolution: Built a robust, step-by-step conflict dialog to handle copy/move filename collisions gracefully instead of silently overwriting user data.
- [Feature] Smart Paste for Folders: Dynamically restyled conflict UI explicitly for directories, replacing generic "Replace" actions with a precise "Merge" paradigm.
- [Feature] Contextual Batch Processing: Added intuitive "Do this for all remaining conflicts" checkbox for rapid ingestion of large paste operations.
- [Feature] Intelligent Auto-Renaming: Resolving a conflict via "Keep Both" now automatically increments file suffixes recursively (e.g., `(1)`, `(2)`) instead of polluting the name with generic words.

### Fixed
- [Bug] SavedStateHandle: ViewModels now persist navigation stack (`pathHistory`) and current active directory across process death natively, securing user state against sudden OS terminations.
- [Bug] Corrected `navigateBack()` returning boolean values incorrectly when attempting to traverse backwards up a deeply-linked directory node on a fresh launch process.
- [Bug] Deprecated duplicate boolean state checking `isCategoryScreen` looping across the `NavController` recompositions. The deep link intent is now cleanly pushed to initialization inside `BrowserViewModel` via `SavedStateHandle` parameters, enforcing a single source of truth.
- [Bug] `copyFiles()` overwrites destination files without user confirmation; fixed via new Smart Paste flow.
- [Bug] Infinite Recursion File-System loop: prevented users from pasting a parent directory inside of its own child folder.
- [Bug] Identical same-folder paste operations triggered duplicates instead of acting gracefully as an atomic no-op.

---

## [0.3.3] - 2026-03-11

### Fixed
- [Bug] Trash restore data corruption risk: orphaned metadata implicitly handled and skipped gracefully.
- [Bug] `moveFiles()` copy+delete fallback is now atomic and triggers a complete rollback on partial failure.

### Improved
- [Security] Added internal KDoc highlighting that the `.arcile_trash` vault on shared external storage natively lacks encryption.
- [Performance] Verified `FileModel` constructor skips inherently expensive disk I/O operations natively via default values.
- [Performance] `getRecentFiles()` stops blindly querying the MediaStore unconditionally by instituting hard limits per feature view.
- [Accessibility] Added rich `contentDescription` semantics to both `FileItemRow` and `FileGridItem` allowing precise TalkBack announcements.
- [Accessibility] Adjusted Home Screen categorized shortcut elements to universally respect minimal 48dp interaction touch targets.
- [UX] `ToolsScreen` and identical components now properly restrict click behaviors and dynamically dim opacity for uninitialized capabilities.
- [Smoothness] Removed redundant `SimpleDateFormat` recreations triggering costly UI Thread Garbage Collection sweeps in `RecentFilesScreen`.
- [Smoothness] Stripped problematic layout shifting flags (`animateContentSize`) off the `StorageSummaryCard` stopping native redraw jittering.

---

## [0.3.2] - 2026-03-11

### Refactored
- [Architecture] `FileModel` domain model decoupled from `java.io.File`, preventing implicit I/O operations and allowing proper serialization/parceling without leaking data layer concerns.
- [Architecture] Introduced Hilt Dependency Injection framework. Replaced direct instantiation of `LocalFileRepository` inside ViewModels with constructor injection, massively improving testability.
- [Architecture] Split monolith `FileManagerViewModel` into four feature-scoped view models: `HomeViewModel`, `BrowserViewModel`, `TrashViewModel`, and `RecentFilesViewModel` to enhance performance and code maintainability.

### Fixed
- [Bug] Clipboard operations failed or behaved unreliably due to UI recompositions; states are now correctly hoisted within a dedicated `ClipboardState` tied uniquely to `BrowserViewModel`.
- [Bug] Share operations failed out-of-context for `RecentFilesScreen`; added `shareSelectedFiles` capability directly to the `RecentFilesViewModel` to fix intent drops.
- [Bug] `FileManagerScreen` crashed due to missing Opt-In annotations for modern Compose 1.4+ Shared Transitions.

### Improved
- [Component] Removed monolithic `FileManagerState`. Each feature screen (`HomeScreen`, `StorageDashboardScreen`, etc.) now strictly binds to precisely tailored isolated states (`HomeState`, `TrashState`, etc.).

---

## [0.3.1] - 2026-03-11

### Fixed
- [Bug] `SearchFiltersBottomSheet`: Date filter chip selected-state checks now use live time-window range comparison instead of exact equality against a stale `remember { System.currentTimeMillis() }` — chips correctly highlight after the sheet is closed and reopened.
- [Bug] `FileManagerScreen`: Long-press range-selection anchor `lastInteractedIndex` is now reset to `null` whenever the file list changes (directory navigation, sort change), preventing stale-index out-of-bounds errors and incorrect batch selections in both list and grid views.
- [Bug] `RecentFilesScreen`: System back gesture now clears selection mode (via `BackHandler`) instead of navigating away with `selectedFiles` still populated.
- [Bug] `RecentFilesScreen`: Delete action now shows a confirmation `AlertDialog` ("items will be moved to Trash Bin") before invoking `onDeleteSelected()`, matching the behavior in `FileManagerScreen`.
- [Bug] `AudioAlbumArtFetcher`: `return@try null` replaced with `return null` — `try` is not a valid label target in Kotlin; the `finally { retriever.release() }` block still executes correctly.
- [Bug] `FileManagerViewModel`: `isPullToRefreshing` state now exclusively drives the pull-to-refresh indicator; general `isLoading` no longer conflated with the swipe gesture.
- [Bug] `FileManagerViewModel`: `navigateBack()` now returns `true` after switching state to `isHomeScreen`, preventing the `NavController` from also popping the back stack (double-navigation).
- [Bug] `FileManagerViewModel`: `refresh()` now routes to the correct loader for each screen type (home, trash, recent files, category, directory browser).
- [Bug] `FileManagerViewModel`: `debouncedSearch()` uses `pathScope = null` (MediaStore-wide) on home, category, and recent screens; scopes to `currentPath` only when inside a directory browser.
- [Bug] `LocalFileRepository`: Copy/move operations now reject ancestor targets, preventing infinite filesystem loops.
- [Bug] `LocalFileRepository`: `restoreFromTrash` is non-destructive — if the restore target already exists a conflict-safe filename is generated instead of overwriting.
- [Bug] `LocalFileRepository`: `searchFiles` validates `pathScope` and prunes directories outside the storage root during recursive walks.

### Improved
- [A11y] `SettingsScreen`: Theme mode card rows now use `Modifier.selectableGroup()` and each card exposes `Modifier.selectable(role = Role.RadioButton)`, allowing TalkBack to announce them as an exclusive radio group.
- [A11y] `SettingsScreen`: Accent color label replaced `currentAccent.name` (raw enum constant) with a friendly `accentLabel()` helper returning "Dynamic", "Monochrome", "Blue", etc.

### Added
- [Theme] `AccentColor.MONOCHROME` now resolves to dedicated `MonochromeLightScheme` / `MonochromeDarkScheme` grayscale color schemes instead of falling through to the purple default.
- [Security] `file_provider_paths.xml`: Added root `external-path path="/"` entry so `FileProvider.getUriForFile()` succeeds for any file on external storage, not just the ten named standard folders.

### Docs
- [KDoc] `FileRepository.deleteFile` KDoc corrected to describe soft-delete (trash) behavior rather than permanent deletion.

---

## [0.3.0] - 2026-03-11

### Fixed
- [Bug] `loadHomeData()` no longer triggers double recomposition — recent files, storage info, and category sizes are now merged into a single `_state.update` call.
- [Bug] `SimpleDateFormat` instance in `RecentFilesScreen` is now wrapped in `remember` to prevent per-recomposition GC pressure.

### Improved
- [UX] Error messages now display as non-blocking `Snackbar` notifications at the bottom of the screen, replacing the modal `AlertDialog` that forced user interaction before retrying.
- [UX] Clipboard copy/cut operations now show feedback via `Snackbar`: "N item(s) copied to clipboard" or "N item(s) cut to clipboard".
- [Motion] FAB expansion menu now shows a dismissible scrim overlay — tapping outside the expanded menu closes it cleanly.
- [Motion] File selection padding transition (0dp → 8dp) is now smoothly animated via `animateDpAsState` instead of an abrupt layout jump.
- [Architecture] Category color mapping (`getCategoryColor()`) centralized into `utils/CategoryColors.kt`, replacing 3 duplicated `when` blocks across `HomeScreen.kt` and `StorageDashboardScreen.kt`.

### Changed
- [Naming] `deleteSelectedFiles()` renamed to `moveSelectedToTrash()` in `FileManagerViewModel` to accurately reflect the trash-based deletion pipeline.

### Removed
- [Security] Removed `android:requestLegacyExternalStorage="true"` from `AndroidManifest.xml` — ignored on Android 11+ and a security regression on Android 10.

---

## [0.2.9] - 2026-03-10

### Fixed
- [Bug] Delete confirmation dialog now correctly states "Selected items will be moved to the Trash Bin. You can restore them later" instead of the misleading "This action cannot be undone."
- [Bug] Recent Files screen now always reloads data when navigated to, preventing stale file lists after file operations.
- [Bug] `FileRepository.searchFiles()` parameter changed from `Any?` to `SearchFilters?` for compile-time type safety.

### Improved
- [Architecture] Moved `SearchFilters` data class from `presentation` to `domain` package, fixing an architectural dependency violation (data layer was importing from presentation layer).
- [Architecture] Extracted `formatFileSize()` utility from `FileManagerScreen.kt` into a shared `utils/FormatUtils.kt` module, resolving cross-file coupling.
- [Design] Restored the bundled Outfit font across all 13 Material typography styles, replacing the system `SansSerif` fallback that was set in v0.2.5.
- [Design] Fixed OLED theme `surfaceContainer`, `surfaceContainerHigh`, and `surfaceContainerHighest` tokens to use properly dark values, ensuring cards and elevated surfaces render near-black in OLED mode.
- [Design] Removed deprecated `window.statusBarColor` and `window.navigationBarColor` API calls from `ArcileTheme` — `enableEdgeToEdge()` already handles system bar colors.

### Removed
- [Cleanup] Removed dead `animatedShape` animation code from `FileItemRow` and `TrashScreen` — the `TwoWayConverter` was a no-op that always returned the same shape value.
- [Cleanup] Removed duplicate imports (`AnimatedVisibility`, `fadeIn`, `fadeOut`, `slideInVertically`, `slideOutVertically`, `LazyRow`) from `FileManagerScreen.kt`.

---

## [0.2.8] - 2026-03-07

### Added
- [Feature] Contextual Content Search: Integrated local file search logic directly bound to the user's current directory exploration scope. Searching in `Downloads` will now yield results strictly contained within the `Downloads` node mapping, bypassing the global MediaStore dump. 
- [Feature] Search Filters: Introduced an inline bottom sheet for real-time granular file filtering during search operations. Support logic dynamically targets:
  - File Type (Images, Videos, Audio, Docs)
  - Item Type (Folders, Files)
  - Minimum and Maximum Size spans (< 10 MB, 10 - 100MB, etc.)
  - Date Modified ranges (Today, Last 7 Days, Last 30 Days)
- [Feature] Smart Range Selection: Enabled intuitive multi-select logic in the File Browser. Once in selection mode, long-pressing two discrete files now automatically calculates and selects the spatial bounds bridging the two items.
- [Feature] Select All: Injected a global 'Select All' toggle into the navigation bar during selection mode to batch select the entire directory instantly.

### Improved
- [UI] Search interface cleanly docks directly into the `FileManagerScreen`'s application bar alongside dynamic removable chips plotting active filters on a secondary layout row.
- [UI] Filter Bottom sheet horizontally scrolls constraint dimensions to cleanly block native text wrapping glitches on tight pixel screens. 

---

## [0.2.7] - 2026-03-07

### Added
- [Feature] Implemented a comprehensive Storage Management Dashboard providing a visual breakdown of device storage by category.
- [Feature] Added deep category navigation—clicking any storage category in the dashboard instantly opens that specific media group in the file explorer.
- [Navigation] Integrated the dashboard via a long-press gesture on the primary Home screen storage card.

### Improved
- [UI] Refined the `MultiColorStorageBar` to provide accurate real-time feedback and better visual clarity between categorized data and system overhead.

### Fixed
- [Bug] Resolved Jetpack Compose `@Composable` context violations preventing builds when prepping data inside `LazyColumn` scopes.
- [Bug] Fixed critical missing imports and fully-qualified name resolution failures in the `HomeScreen` and `StorageDashboard` UI modules.
- [Bug] Corrected deprecated icon usage to the modern `AutoMirrored` standards.

## [0.2.6] - 2026-03-07

### Added
- [Feature] Expanded the Recent Files experience via a dedicated "See All" screen that organizes recent items chronologically by full calendar days (Today, Yesterday, etc.) with explicit sticky headers natively mapped across timezone and daylight saving boundaries.
- [Security] Applied `validateFileName` middleware across the repository to aggressively screen all directory/file creations and renames for invalid characters and explicit path traversal (`..` escapes) vectors.
- [Security] Locked down `FileProvider` (`file_provider_paths.xml`) so the internal system no longer exposes the entire root `external_files` path to external apps.

### Improved
- [Performance] Eliminated severe "Recent Files" UI loading stutters by natively querying `SIZE`, `DISPLAY_NAME`, `DATE_MODIFIED`, and `MIME_TYPE` from the raw SQL MediaStore cursors, bypassing tens of thousands of simultaneous disk properties queries.
- [Performance] `getRecentFiles` now securely pulls unbounded recent artifacts up to exactly 7 trailing days ago flawlessly, without an arbitrary query cap truncating results.
- [Performance] Upgraded `AudioAlbumArtFetcher.kt` to natively sample large Bitmaps into memory rather than triggering OutOfMemory errors when loading raw byte streams into the cache. 

### Changed
- [Build] Integrated the Stable Artifacts API alongside standard configuration outputs (`Arcile-dev.qtremors.arcile-0.2.6.apk`) over the obsolete undocumented `VariantOutputImpl` internal API.

### Fixed
- [Bug] Prevented random directories from incorrectly populating the `RecentFilesScreen` as "0-byte files" by explicitly excluding rows where `MIME_TYPE` is null.
- [Bug] Repositioned the high-refresh rate (120FPS) hardware configuration block in `MainActivity.kt` safely out of the `setContent` composable block and wired it directly to the Android 30+ non-deprecated `Display` subsystem.
- [Bug] Stopped the Storage `ArcileAppShell` pathing tree from crashing and causing double-launches due to an improperly scoped `LaunchedEffect` listener on the App Shell backstack.


## [0.2.5] - 2026-03-07

### Fixed
- [Bug] Resolved fully qualified name resolution failures breaking builds across UI screens.
- [Bug] Fixed missing Compose runtime imports causing `HomeScreen` compilation errors.

### Changed
- [Design] Corrected `ExpandableFabMenu` internal actions to use `ExtendedFloatingActionButton` paired with `ExpressivePillShape` for proper pill construction.
- [Design] Main internal storage `FloatingActionButton` now properly morphs its shape radius from an expressive squircle (16dp) to a perfect circle (28dp) when expanding its options.
- [Design] Typography system globally swapped from the custom 'Outfit' font to the native `SansSerif` font to immediately enhance text legibility and contrast against vibrant Material 3 backgrounds.
- [Design] Removed hard-coded alpha layer transparencies from various text components on the `HomeScreen` to solve legibility challenges and ensure pure M3 text color semantic rendering.
- [Design] The `FileGridItem`, `FileItemRow`, and `TrashList` items now properly physically morph into an `ExpressiveShapes.large` container when selected, instead of using a baseline flat color switch.
- [Architecture] `ArcileTopBar` refactored to allow parent screens to granularly pass booleans determining the overflow menu actions (e.g., hiding "New Folder" from the Home Screen, exposing "Settings" and "About" instead).
- [Component] The `ToolCard` components on the Home Screen now check an internal `isImplemented` flag to decide whether to render the "Coming Soon" badge, unflagging the active Trash Bin.

### Removed
- [Cleanup] Removed the redundant `ExtendedFloatingActionButton` for "Empty Trash" situated at the bottom of the `TrashScreen` since the action is already unified within the `TopAppBar`.

---

## [0.2.4] - 2026-03-07

### Added
- [Design] Fully adapted the app to Google's Material 3 Expressive guidelines, including new shape scales, dynamic color themes, and typography refinements.
- [Component] Replaced the single FAB inside the File Manager with a new Material 3 Expressive animated Expandable FAB Menu containing `New File` and `New Folder`.
- [Motion] Implemented `SharedTransitionLayout` to support smooth shared element bounds transitions across navigation screens.
- [Motion] Added `animateContentSize` for dynamic layout adjustments across expanding containers.

### Changed
- [Component] Overhauled `ArcileTopBar`, `GlobalSearchBar`, and Button components to map tightly against modern M3 surface container roles and expressive variants.
- [Design] Transitioned the default system font family to `Outfit` to align with the expressive aesthetic profile.

### Fixed
- [Bug] Fixed the `Trash Bin` utility card on the Home Screen being unclickable due to redundant touch interception hierarchies.

---

## [0.2.3] - 2026-03-07

### Added
- [Feature] Core File Operations (`Copy`, `Cut`, `Paste`, `Share`) supported natively through application domain/repository architecture.
- [Feature] Full `Trash Bin` system utilizing a highly-secured hidden system `.arcile_trash` vault. Default `delete` flows now map through `moveToTrash`.
- [Feature] Added new AppRoutes screen for the Trash system, injected inside the `HomeScreen` as a fast shortcut.
- [Feature] Embedded system clipboard monitoring inside `FileManagerState` memory to dynamically expose floating clipboard paste/cancel panels inside the main toolbar across location mutations.
- [Feature] Intelligent copy conflict overrides mapping duplicate files sharing namespaces via a new automatic ` - Copy (*)` suffix resolution protocol to prevent filesystem erasure.
- [Design] Integrated Material 3 Expressive `PullToRefresh` layout wrapper across all directory traversal screens inside `FileManagerScreen`.

---

## [0.2.2] - 2026-03-07

### Added
- [Feature] Introduced a global "Show All" button on the Home screen's Utilities dashboard to seamlessly dive into the dedicated Tools screen.
- [Component] Abstracted the `ToolCard` feature out of the `ToolsScreen` into a unified layout module accessible across the app.

### Changed
- [Design] Radically transformed the Home screen Folders layout from a hidden horizontal scroll row into a rigid 3-row grid block that renders all shortcuts instantly.
- [Design] Relocated 4 primary system Utilities out of the `ToolsScreen` and planted them directly onto the Home screen as a responsive 2x2 grid.

### Fixed
- [Bug] Cleaned up unused variables, dead topbar action properties, and dangling Material Icon imports from `HomeScreen.kt` and `ArcileAppShell.kt`.
- [Bug] Fixed IDE compilation warnings throwing Unresolved Reference errors on missing UI elements.

---

## [0.2.1] - 2026-03-07

### Added
- [Feature] Bumped Compose Material 3 dependency to `1.4.0-alpha08` to introduce M3 Expressive API features.

### Changed
- [Design] Migrated traditional standard `CircularProgressIndicator` across all app screens to the new morphing `LoadingIndicator`.
- [Design] Restructured setting and file browser lists to utilize proper `OneLineListItem` and `TwoLineListItem` formatting structure instead of generic ListItems.
- [Motion] Added spring-based physics motion and scaling to `Card` and file grid components.
- [UX] Contextual selection items in the `ArcileTopBar` are now grouped cleanly inside a tonal surface container to behave like a unified ButtonGroup.

---

## [0.2.0] - 2026-03-06

### Added
- [Feature] The search field now queries the Android MediaStore to provide instant search results across the entire device storage globally, rather than just filtering the local items strictly within the currently opened folder view.
- [Feature] Image and Video thumbnails now load and display locally in the File Browser lists and Grids seamlessly via Coil caching.
- [Feature] Peak Refresh Rate request implemented on app launch `onCreate` frame, supporting 120Hz/144Hz displays natively to achieve buttery-smooth 120fps scrolling. 
- [Feature] Added an interactive layout with new developer and hardware information tiles to the Settings Screen.

### Fixed
- [Bug] Fixed a build issue causing `SettingsScreen.kt` to fail compilation (`Unresolved reference: Color`).
- [Bug] The Search bar will now dismiss properly when the phone's back button is pressed instead of getting stuck on the screen or exiting the app prematurely.

### Changed
- [Design] Settings page completely redesigned and modularized using Jetpack Compose Elevated Tonal Cards, strictly conforming to the Google Material 3 Standards.

---

## [0.1.9] - 2026-03-06

### Added
- [Feature] Categories on the Home Screen now list all files associated with that extension category globally, instead of shortcutting to specific directories.

### Improved
- [Performance] `MultiColorStorageBar` category sizing operation now queries `MediaStore.Files` instead of recursively walking the entire filesystem via `walkTopDown()`, removing massive I/O delays.
- [Design] `MultiColorStorageBar` math logic accurately calculates category sizing vs unmanaged `Other` vs actual free storage space block allocations.
- [Design] `MultiColorStorageBar`, its bottom legend, and the main `CategoryGrid` now sort dynamically, presenting the largest categories first in descending order instead of static alphabetical listing.

### Fixed
- [Bug] Application no longer violently reloads the file directory (resetting scroll and selection state) every time the device wakes up from the lock screen.
- [Bug] Using the back button while deep inside a Category view now correctly returns the user directly to the Home screen instead of dropping them into the root internal storage directory.

---

## [0.1.8] - 2026-03-06

### Added
- [Feature] Added DataStore persistence for Theme and Accent Color selection, restoring state across process deaths
- [Feature] Enabled `buildConfig` in Gradle to dynamically display the current App Version under Settings
- [Feature] Search: Search icon filtering recent files on Home and current-folder contents in Browse
- [Feature] Sort: Sort dialogs controlling recent-file ordering on Home and current-folder ordering in Browse
- [Feature] Grid View: Overflow menu toggling between list and adaptive grid layouts in Browse
- [Design] Refactored SettingsScreen to utilize Material Design 3 `ListItem` components with standardized surfacing

---

## [0.1.7] - 2026-03-04

### Fixed
- [Bug] Per-file deletion error messages are now retained after directory refresh — previously `refresh()` cleared the error before the user could see it

### Refactored
- [Refactor] Replaced magic string action dispatch in `ArcileTopBar` with `TopBarAction` sealed class — compile-time verified actions
- [Refactor] Replaced raw string navigation routes (`"home"`, `"explorer"`, etc.) with `AppRoutes` constants
- [Refactor] Extracted `ArcileAppShell`, `PermissionRequestScreen`, and `getCategoryPath` from `MainActivity.kt` into `ArcileAppShell.kt`

### Improved
- [Motion] Added slide + fade navigation transitions to all `NavHost` routes — screen switching now feels fluid
- [Design] Replaced hardcoded `color.copy(alpha = 0.15f)` overlays with proper M3 tonal tokens (`primaryContainer`, `secondaryContainer`)

---

## [0.1.6] - 2026-03-04

### Fixed
- [Bug] `showRenameDialog` now only triggers when exactly one item is selected — prevents silent no-op on multi-selection
- [Bug] `openFile()` exception is now logged via `Log.e` and the toast includes the actual error message instead of a generic string

### Improved
- [A11y] Added descriptive `contentDescription` labels to all core navigation icons (`HomeScreen`, `FileManagerScreen`, `Breadcrumbs`) for TalkBack support
- [Motion] File list items now animate smoothly on add/remove/reorder via `Modifier.animateItem()` in `LazyColumn`

### Refactored
- [Refactor] Extracted `StorageInfo` data class to its own file (`StorageInfo.kt`) in the domain package
- [Verified] `java.util.Stack` was already replaced with `ArrayDeque` — task marked complete

---

## [0.1.5] - 2026-03-04
### Features & Fixes
- Added click handlers to Home screen Category icons to open their respective media folders.
- Recent files list on Home screen are now clickable, opening the file directly instead of just selecting it.
- Prevented potential crashes / logic bugs with sibling folder navigation in `navigateToFolder`.
- Optimized performance by hoisting `SimpleDateFormat` to prevent per-row instantiation during scroll.
- Fixed a resource linking error ensuring `Theme.Material3.DayNight.NoActionBar` is correctly targeted.
- Code cleanup: Fixed irregular indentations in `ArcileTopBar.kt`.
- Verified and marked off several issues (`formatFileSize` crashes, settings back navigation, storage parsing logic) that were already effectively solved by existing app logic implementations.

---

## [0.1.4] - 2026-03-04

### Added
- File-open capability — tapping a file launches `Intent.ACTION_VIEW` via `FileProvider` with MIME type detection
- Rename UI — rename dialog, ViewModel `renameFile()` function, and edit icon in selection bar (single-selection)
- `RenameDialog` composable in `FileManagerScreen.kt`
- `file_provider_paths.xml` and `FileProvider` registration in `AndroidManifest.xml`

### Fixed
- Permission state now reactive to lifecycle — `onResume` updates a hoisted `mutableStateOf` so the UI recomposes when returning from system settings
- Replaced deprecated `Icons.Default.InsertDriveFile` with `Icons.AutoMirrored.Filled.InsertDriveFile`
- `android:allowBackup` set to `false` (template backup rules not configured)
- Added explicit `kotlinOptions { jvmTarget = "11" }` in `build.gradle.kts`

### Changed
- [Security] Release builds now use `isMinifyEnabled = true` and `isShrinkResources = true`
- Navigation dependency moved from hardcoded string to version catalog (`libs.versions.toml`)
- Lifecycle ViewModel Compose version aligned from `2.8.2` to `2.10.0` (matches `lifecycleRuntimeKtx`)

### Removed
- `local.properties` confirmed not tracked (already in `.gitignore`)
- Unused import `kotlinx.coroutines.launch` confirmed not present in `MainActivity.kt`

---

## [0.1.3] - 2026-03-04

### Added
- Delete confirmation dialog before file/directory deletion
- "Coming Soon" label on all Tools screen cards
- Folder name validation (rejects `/`, `\`, `..`, null, blank)
- Per-file error reporting in batch delete ("Failed to delete N of M files")

### Fixed
- [Security] Path traversal protection on all file operations — canonical path must resolve within external storage
- [Security] `renameFile` rejects names containing `/`, `\`, `..`, or null characters
- `deleteSelectedFiles()` now surfaces per-file failures to the user
- `themes.xml` uses `Theme.Material3.Light.NoActionBar` (was legacy `android:Theme.Material.Light.NoActionBar`)

### Removed
- Empty placeholder `ThemePreferences.kt` (0 bytes)
- Unused import `kotlinx.coroutines.flow.Flow` from `FileRepository.kt`
- Template colors from `Color.kt` (`Purple80`, `PurpleGrey80`, `Pink80`, `Purple40`, `PurpleGrey40`, `Pink40`)
- Unused template colors from `res/values/colors.xml` (`purple_200/500/700`, `teal_200/700`)

---

## [0.1.2] - 2026-03-04

### Added
- Bottom `NavigationBar` with Home / Browse / Tools tabs (replaces hamburger drawer)
- Multi-colored storage progress bar segmented by file category
- 6 color-coded file categories: Images (green), Videos (pink), Audio (orange), Docs (blue), Archives (purple), APKs (cyan)
- Per-category storage size calculation and display
- Category legend in storage card showing size used per type
- Clickable `StorageSummaryCard` → opens file browser
- Folder shortcuts navigate to actual directories (DCIM, Downloads, Pictures, Documents, Music, Movies)
- Settings accessible via gear icon in Home screen top bar
- Back arrow navigation in file browser and settings
- Edge-to-edge display support (`enableEdgeToEdge()`)
- `FileCategories.kt` — category definitions with colors and extensions

### Fixed
- Breadcrumbs now strip `/storage/emulated/0` prefix, show "Internal Storage" as root
- Breadcrumb segment clicks wired to actual navigation (was a TODO stub)
- "Empty Directory" when clicking Internal Storage — file browser now auto-loads
- `Modifier.padding` invalid overload in `HomeScreen.kt`
- Double status bar padding on Android 15/16 (`contentWindowInsets = WindowInsets(0)`)
- `formatFileSize` crash potential (clamped `digitGroups`)

### Changed
- Folder shortcut cards are uniform size (130×48dp)

### Removed
- Hamburger menu and `ModalNavigationDrawer`
- `NavigationDrawerContent.kt` (deleted)

## [0.1.1] - 2026-03-04

### Added
- Project documentation: `README.md`, `DEVELOPMENT.md`, `CHANGELOG.md`, `LICENSE.md`
- Comprehensive project audit in `TASKS.md` (40 findings across 8 categories)
- Consolidated `.gitignore` with full Android/IDE/OS coverage
- App icon source image (`ic_launcher-playstore.png`)

### Fixed
- Stale `com.qtremors.filemanager` package declarations → `dev.qtremors.arcile` across 8 source files
- `"File Manager"` → `"Arcile"` in `settings.gradle.kts`, `strings.xml`, `themes.xml`, `AndroidManifest.xml`
- Missing `Color` import in `FileManagerScreen.kt` causing build failure
- Invalid `Modifier.padding(horizontal, top, bottom)` overload in `HomeScreen.kt` (3 occurrences)
- Version references updated from `1.0.0` to `0.1.1`

### Changed
- Reformatted `TASKS.md` to use prioritized task template

### Removed
- Stale `build_log.txt` and `nav_build_log.txt` (referenced old package name)

---

## [0.1.0] - 2026-03-04

### Added
- File browsing with sorted directory listings (folders first, then alphabetical)
- Breadcrumb navigation bar with auto-scroll and segment tap navigation
- Multi-file selection via long-press with contextual top bar actions
- Create folder via FAB and overflow menu dialog
- Delete selected files and directories (recursive)
- Home screen dashboard with storage summary card, category shortcuts, folder quick-access, and recent files list
- Material You dynamic theming (wallpaper-based colors on Android 12+)
- Custom accent color selection (Blue, Cyan, Green, Red, Purple, Monochrome, Dynamic)
- Theme modes: System, Light, Dark, OLED
- Settings screen with theme mode and accent color bottom sheet pickers
- Tools screen with placeholder cards (FTP Server, Analyze Storage, Clean Junk, Duplicates, Large Files, App Manager, Secure Vault, Network Share)
- Storage permission handling for Android 11+ (MANAGE_EXTERNAL_STORAGE) and pre-11 (READ/WRITE_EXTERNAL_STORAGE)
- Composable top bar component (`ArcileTopBar`) with selection mode, overflow menu, search/sort icons
- Error dialogs for file operation failures
- Bottom navigation bar (Home, Storage, Tools)

### Known Issues
- Search and sort buttons are non-functional (UI stubs only)
- All category/folder shortcuts navigate to storage root instead of specific folders
- Recent file clicks open the file browser instead of the actual file
- Tools screen items are non-functional placeholders
- Theme preferences are not persisted across app restarts
- No file-open capability exists
- No rename UI despite backend support
- Delete has no confirmation dialog
- Settings screen has no back navigation button

---
