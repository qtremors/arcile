# Arcile - Releases

> **Project:** Arcile
> **Version:** 1.0.0
> **Last Updated:** 2026-06-05

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v1.0.0](#v100) | 2026-06-05 | Security Hardening, Recovery, Archive Safety |
| [v0.9.0 Beta](#v090-beta) | 2026-05-28 | Modular Architecture, Feature Extraction, Release Sync |
| [v0.8.0 Beta](#v080-beta) | 2026-05-24 | Storage Cleaner, Trash Overhaul, Safety & Polish |
| [v0.7.0 Beta](#v070-beta) | 2026-05-16 | Onboarding, Archives, Recent Files & Reliability |
| [v0.6.0 Beta](#v060-beta) | 2026-04-19 | UI/UX Overhaul, Quick Access & Unified Controls |
| [v0.5.0 Beta](#v050-beta) | 2026-03-23 | Security, Architecture & Target API Bump |
| [v0.4.5 Beta](#v045-beta) | 2026-03-19 | Stability, Performance & Visual Polish |
| [v0.4.0 Beta](#v040-beta) | 2026-03-16 | SD Card & OTG Support |
| [v0.3.0 Beta](#v030-beta) | 2026-03-11 | Trash Bin & Copy/Paste |
| [v0.2.0 Beta](#v020-beta) | 2026-03-06 | Material 3 Redesign |

---

# v1.0.0

**Release Date:** June 5, 2026  
**Previous release:** v0.9.9 Beta

Arcile v1.0.0 promotes the hardened 0.9.x foundation with stronger import safety, safer archive thumbnails, clearer recovery for interrupted operations, and explicit reporting for partial destructive batches.

## What's New & Improved

### Security & Privacy Hardening
- Save to Arcile now preflights incoming share items before destination selection, accepting `content://` sources and only app-owned `file://` paths after canonical path checks.
- Incoming imports enforce a 200-item limit, a 10 GiB total byte budget, filename normalization, destination free-space checks, counted streaming for unknown sizes, and concise partial-failure messages.
- Archive-entry thumbnails now decode bounds first, reject invalid or oversized images, enforce an independent 75 MiB stream cap, sample large valid images to the requested thumbnail size, and fall back to normal icons on rejection.

### Reliability
- Browser navigation cancels superseded listing jobs and gates state updates by request generation, preventing stale loads from replacing current files, paths, loading flags, or errors.
- Foreground operation recovery now classifies interrupted work into explicit cleanup-required records with retry and cleanup actions.
- Permanent delete and shred batches now report succeeded, skipped, failed, and cleanup-required paths so partial destructive work is visible and retryable.

### Release Maintenance
- Android app metadata is now `versionName` `1.0.0` with `versionCode` `100`.
- Release-facing documentation, changelog metadata, and module-structure docs were synchronized for the 1.0.0 release.

---

# v0.9.0 Beta

**Release Date:** May 28, 2026  
**Previous release:** v0.8.0 Beta

Welcome to **Arcile v0.9.0 Beta**! This release marks a major architectural milestone by transitioning the codebase from a single monolithic application module to a highly optimized, modular Gradle architecture. Over the course of the 0.8.x cycle (versions 0.8.1 through 0.8.9), the project was systematically split into 11 specialized submodules to enforce strict code separation, simplify maintenance, isolate compilation, and accelerate incremental builds.

Here is a comprehensive summary of everything new, improved, and polished in this release:

## 🚀 What's New & Improved

### 🏗️ Modular Gradle Architecture
Decomposed the app into clean functional layers with strict Dagger/Hilt composition and interface-driven dependency boundaries:
- **Core UI Module (`:core:ui`):** Consolidates all reusable Compose primitives, Material 3 design systems, typography tokens, custom layouts (list/grid rows), visual transitions, and the unified thumbnail loading engine.
- **Storage Subsystems (`:core:storage`):** Separated into `:core:storage:domain` (containing typed entities, directory models, and interfaces) and `:core:storage:data` (managing physical storage, disk scanning, media indexing, and mutations).
- **Operation Services (`:core:operation`):** Separated into `:core:operation:api` (progress contracts) and `:core:operation` (foreground service plumbing, state tracking, and operation journals).
- **Feature Modules:** Decoupled major features into independent, isolated Gradle modules:
  - `:feature:browser` for directory listing and clipboard operations.
  - `:feature:trash` for restoration lifecycle and local metadata tracking.
  - `:feature:archive` for ZIP and 7z creation and extraction.
  - `:feature:recentfiles` for the interactive chronological files timeline.
- **App Module (`:app`):** Acts as the navigation router, Hilt composer, and launch shell.

### ⚡ Performance & Directory Optimization
- **Paged Directory Listing:** Replaced blocking directory reads with a paged listing mechanism. Huge folders now load incrementally and render page-by-page, delivering an instant visual overview and keeping scroll performance buttery smooth.
- **Compose Stability:** Rebuilt all UI list representations (Browser, Home, and Recents) to use immutable collection types (`kotlinx.collections.immutable`), eliminating redundant recomposition cycles.

### 🛡️ Safety & API Hardening
- **Typed Storage Node Identifiers:** Introduced invariant value classes for Volume IDs, paths, and metadata keys. Standardized validation on critical file operations to prevent path traversal and guarantee integrity.
- **Automatic Destructive Validation:** Routed all file delete, trash, and extraction workflows through strict pre-flight canonicalization logic.

### 🎨 Visual Motion & UX Polish
- **Choreographed Motion System:** Implemented a system-wide motion easing curve and duration specifier, supporting standard Material 3 deceleration, acceleration, and Emphasized styles, with a global toggle to honor system-wide reduced motion.
- **Dialog Focus Polish:** Overhauled file/folder creation dialogs to automatically gain focus and request the IME keyboard upon opening, minimizing friction for quick operations.

## 🐛 Known Issues (Beta)
- **Background operation resilience:** Foreground file operation coordinates are fully journaled, but recovery of interrupted tasks across unexpected process terminations is still under refinement.
- **Integrity verification overhead:** Heavy directories may take longer to transfer or empty due to mandatory recursive SHA-256 validation checks.

---

# v0.8.0 Beta

**Release Date:** May 24, 2026  
**Previous release:** v0.7.0 Beta

Welcome to **Arcile v0.8.0 Beta**! This release covers everything shipped after v0.7.0. It delivers a rebuilt Trash system, a brand-new Storage Cleaner, a radial storage usage map, smarter thumbnails, edge-to-edge visuals, deeper accessibility, and a large safety and reliability pass across every major file operation path. Here is everything new and improved since v0.7.0:

## 🚀 What's New & Improved

### 🗑️ Trash System Rebuilt
- **Recoverable Metadata:** Trash metadata is now stored as plaintext schema-versioned JSON under each volume's `.arcile/.metadata` folder. Trashed items can be rediscovered after reinstall without requiring app-private keys or preferences.
- **Recovered Item Handling:** Unreadable metadata no longer causes payload loss — if the trash payload still exists, Arcile lists it as a recovered item and lets you choose a restore destination.
- **Safer Restore Flow:** Restore now tells you whether items can return to their original path, need a new destination, or will be renamed to avoid conflicts.
- **Trash Undo:** Completed Trash moves can now be undone when Arcile can match the newly created metadata back to the deleted selection.
- **Trash Filters & Sorting:** Added filters for all items, directly restorable items, destination-required items, and recovered payloads. Sorting by deletion date, name, size, type, and original folder is now supported, with list grouping by Today, Yesterday, and Older.
- **Trash Properties:** View details for selected trash items, including original path, payload path, restore status, size, and source volume.
- **Trash Storage Visibility:** Trash usage now appears in storage dashboard summaries, storage bars, and the storage legend.

### 🧹 Storage Cleaner

> **Early Access** — The Storage Cleaner is functional but still being refined. Scanning heuristics, category grouping, and the duplicates view will continue to improve in future releases.
- **New Cleaner Utility:** A dedicated Storage Cleaner tool scans indexed storage for large files, old downloads, duplicate-name candidates, APKs, videos, and conservative junk/cache-like files.
- **Trash-Safe Cleanup:** Cleaner selections move files to Arcile Trash instead of permanently deleting them, with confirmation, selection controls, refresh, and success/error feedback.
- **Segmented Duplicates:** Duplicate files are grouped into distinct, visually separated cards by name and size, with full details (absolute path, last modified date) and Coil thumbnail rendering.
- **Homepage Utilities:** The utilities tray now displays only fully implemented tools (Trash Bin and Clean Junk), removing all placeholder items.

### 📊 Storage Dashboard & Usage Map

> **Early Access** — The radial usage map is functional but still being refined. Visualization accuracy, scan performance on very large volumes, and drill-in polish will continue to improve.
- **Radial Usage Map:** A new Filelight-inspired Usage Map tab on the storage dashboard offers a bounded folder scanner, radial disk usage visualization, breadcrumb drill-in, selected-item details, and explicit open actions into the file browser.

### 🎨 Visual System & Edge-to-Edge
- **Edge-to-Edge Insets:** All screens now bleed backgrounds behind system bars with proper top app bar and status bar padding to keep UI elements interactive.
- **Visual System Spacing:** Expanded spacing theme with semantic tokens (`screenGutter`, `listItemHorizontal`, `toolbarBottomGap`, `sectionGap`, etc.) replacing raw layout metrics for consistent rhythm across screens.
- **Bottom Toolbar Safe Areas:** Floating selection overlays, lists, and grids now correctly clear system gesture pills and 3-button navigation bars.
- **Semantic Status Colors:** Dedicated light/dark color variants for success, warning, progress, and badges replace hardcoded green/red hex values.
- **Color Harmonization Setting:** A new toggle dynamically blends category and status colors with the active primary theme using MaterialKolor.

### 🛡️ Safety & Reliability
- **Delete Decision Surface:** Reworked delete confirmations show operation destination, selected count, total size, folder count, and irreversible warnings. Trash, permanent delete, Android confirmation, and mixed selections are all clearly distinguished.
- **Durable Foreground Operations:** A lightweight operation journal now persists request, progress, phase, cancellation, and cleanup-needed state across coordinator and service lifecycles.
- **Typed User-Safe Errors:** High-risk storage and destructive flows now surface localized recovery-oriented messages instead of raw exception text.
- **Transfer Verification:** Normal copies use metadata verification; move fallback performs full SHA-256 checksum verification before deleting sources. Directory verification uses streaming traversal to reduce memory pressure.
- **Path Safety Policies:** Centralized path validation rejects symlink traversal and revalidates staging, promotion, delete, rename, and extraction targets.
- **Archive Safety Policy:** Added entry count, uncompressed size, path length, nesting depth, and compression-ratio guards for ZIP and 7z operations.
- **Extraction Cleanup:** Failed or cancelled extraction now removes files created by that attempt when they can be identified safely.
- **Mutation Recovery:** A persistent mutation journal cleans up abandoned transfer, replacement, and incomplete Trash fallback artifacts on startup.
- **Backup Privacy:** Tightened backup and data-extraction rules to exclude local preferences, quick access paths, classification data, operation metadata, analytics, and Arcile storage metadata.

### 📋 Paste & Conflict UI
- **Smart Paste Conflict Dialog:** Paste conflicts now show metadata comparison with check banners for identical files and badge indicators highlighting newer and larger files.
- **Improved Resolution Buttons:** Conflict resolution uses vertically stacked buttons with distinct styles (Filled Replace/Merge, FilledTonal Keep Both, Outlined Skip) to prevent text truncation and establish clear visual priority.

### 🔍 Search & Selection
- **Advanced Search Filters:** Expanded filtering with extension, hidden-file visibility, storage volume, folder scope, exact size/date range, MIME, and saved-preset metadata.
- **Active Filter Chips:** Advanced filters display individual removable chips so users can see and clear precise constraints.
- **Selection Affordances:** Explicit check badges in file list/grid items, select/unselect accessibility actions, and a discoverable "Select range…" menu action.
- **Recent Files Pagination Fix:** Resolved duplicate file rendering caused by database-to-filter offset mismatch during scroll.

### 🖼️ Thumbnails & Media
- **Custom Thumbnail Policy:** A dedicated policy evaluates loading conditions dynamically based on file size bounds, MIME-type support, and screen layout.
- **Scroll & Operation Awareness:** Thumbnail loading defers outside the active viewport and pauses during bulk operations to conserve IO bandwidth.
- **Thumbnail Failure Cache:** A persistent cache blocks repeated attempts to load corrupt or unrenderable media files.
- **Archive Viewer Polish:** Contextual archive header, breadcrumb-style folder location, extraction destination preview, localized labels/plurals, masked password entry with reveal controls, and in-view extraction progress with current-entry context and cancellation.

### ♿ Accessibility & Tactility
- **Accessible File Views:** TalkBack semantics for file row/grid items use single-node announcements, hide decorative elements, format hidden files with a "Hidden" suffix, and provide custom action labels.
- **Haptic Feedback:** System vibration events are integrated across selections, multi-select ranges, FAB transitions, deletion confirms, copy conflicts, operation completion, and error notifications.
- **Haptic Vibration Toggle:** A new setting enables or disables haptic feedback globally.
- **Predictive Back Priority:** A shared back-priority resolver handles modals, sheets, search, selection, folder-up navigation, route pop, and app exit in the correct order.
- **State Restoration:** Workflow UI state across Browser, Quick Access, and Trash is preserved across recreation using `rememberSaveable`.

### ⚙️ Settings & Preferences
- **Double-Line Filenames:** A new preference displays filenames on up to two lines in list and grid layouts.
- **Marquee Filenames:** Long filenames can now scroll horizontally using marquee scrolling.
- **Quick Access Toggles:** Pin controls on the Quick Access page were replaced with direct Home shortcut toggles.
- **Localization Pass:** Raw/hardcoded user-facing strings across settings, archive utilities, browser snackbars, toasts, filter chips, and dialogs were fully replaced with parameterized XML string and plural resources using a shared `UiText` wrapper.

### ⚡ Performance
- **Lazy List/Grid Row Models:** Preformatted file row UI models prepare date, size, subtitle, icon type, and thumbnail metadata outside hot lazy item composition.
- **Thumbnail Rendering:** Removed grid-cell `animateContentSize()` and replaced `SubcomposeAsyncImage` with lighter `AsyncImage` requests sized to the presentation.
- **Operation Work Coordination:** A lightweight work coordinator defers low-priority folder stats while foreground file mutations are active.
- **Folder Stats Backpressure:** Folder stats cancel stale jobs, prioritize the newest request, and avoid publishing superseded scan results.
- **Foreground Progress Notifications:** File-operation notifications show throttled determinate progress with cancel actions and active operation phase context.

### 🏗️ Backend & Compatibility
- **Coroutine Dispatcher Injection:** Backend repositories, data sources, managers, stores, image fetchers, and foreground operations are routed through explicit dispatchers for deterministic tests and future IO tuning.
- **Volume Root Readiness:** Storage roots initialize synchronously so path validation has canonical roots available before the first file operation.
- **External Handoff Controls:** Staged open/share cache now has stats, explicit cleanup APIs, Settings access, shorter stale retention, per-file and batch share guards, and MIME-aware grouping.
- **Startup Debug Safety:** Debug-only StrictMode plus startup trace sections around splash, permissions, preference preload, and Compose setup.

### 🧪 Developer & Testing
- **Comprehensive Test Coverage:** Added and updated tests across trash metadata, recovery, conflict restore, filters, sorting, properties, transfer verification, symlink rejection, archive safety, extraction cleanup, backup exclusions, operation journal, delete decisions, typed errors, cleaner scanning, storage usage, thumbnail policy, advanced filters, back priority, file row models, shared cache cleanup, share guards, mutation journals, home shortcuts, and dispatcher injection.

## 🐛 Known Issues (Beta)

Arcile is still in active beta. The 0.8.0 cycle resolved many trash, safety, conflict, and performance issues, but there are still a few things to watch out for:

- **Operation recovery is not fully durable yet:** Foreground operations now journal state, but active requests are not yet fully recoverable across process death, reboot, or force stop. If Android kills Arcile mid-operation, review affected folders for partial output or temporary files.
- **Large transfers can take longer than expected:** Arcile performs integrity checks before deleting originals in copy, move, trash, and restore paths. This improves safety, but very large folders or media libraries can feel slow while verification runs.
- **Use care with huge or untrusted archives:** Archive-bomb limits and safety guards are now in place, but exercise judgment when extracting archives from unknown sources.
- **Check destructive-operation dialogs carefully:** Trash, permanent delete, Android/native prompts, OTG, unclassified drives, and mixed storage selections can still behave differently. Files on OTG or unclassified drives may be permanently deleted because those locations do not support Arcile Trash.
- **Some platform behavior is device-specific:** Please continue reporting MediaStore oddities, external-storage quirks, archive edge cases, unusual permission flows, SD/USB volume classification problems, and background-operation issues.

---

# v0.7.0 Beta


**Release Date:** May 16, 2026  
**Previous release:** v0.6.0 Beta

Welcome to **Arcile v0.7.0 Beta**! This is a broad feature and polish release covering everything since v0.6.0: a proper first-run setup flow, first-class ZIP/7z archive tools, a much richer Recent Files experience, smoother file-operation feedback, better thumbnails, safer destructive operations, and a large performance/reliability pass across storage, MediaStore, navigation, and foreground work.

Here is what is new and improved since v0.6.0:

## 🚀 What's New & Improved

### 🎛️ First-Run Setup & Settings
- **Complete Onboarding:** New users now get a guided setup flow with a welcome screen, feature overview, theme/accent selection, required storage-access setup, and Android 13+ notification-permission guidance.
- **Skip Without Breaking Setup:** The onboarding skip path still preserves required storage setup, so users can move quickly without ending up in a broken permission state.
- **Rerun Onboarding:** Settings now includes an action to rerun onboarding after restart, useful for testing, demos, or changing the initial setup path.
- **Cleaner Permission Recovery:** If all-files access is revoked after setup, Arcile shows a clearer recovery experience instead of abruptly falling back into startup prompts.
- **Appearance Polish:** Theme and accent controls were redesigned, thumbnail settings now live cleanly in Appearance, and the thumbnail switch has clearer checked/unchecked icons.

### 🗂️ Recent Files Rebuilt
- **Home Carousel:** The Home Recent Files list is now a Material 3 Expressive carousel with 4:5 media previews, cover-flow sizing motion, rounded thumbnail clipping, gradient overlays, and inline actions for opening files or their containing folders.
- **Browser-Grade Controls:** Recent Files now uses the Browser's search filters, sort/view sheet, list/grid mode, zoom, grid sizing, thumbnail toggle, and date/name/size ordering.
- **Active Filter Chips:** Search and filtering state is visible directly in Recent Files, so it is easier to understand why a list is narrowed.
- **Better Recents Ranking:** Newly copied or downloaded files with older modified dates are no longer buried, because Recent Files now accounts for both added and modified timestamps.
- **Stability Fixes:** Duplicate MediaStore rows render safely, sticky date headers stay below the top app bar, and single-file selections can jump directly to the containing folder.

### 📦 ZIP & 7z Archive Tools
- **Create Archives:** Compress selected files into ZIP or 7z archives with naming, format selection, destination choices, and optional password entry.
- **Extract Archives:** Extract archives in place or into a dedicated folder directly from the file browser.
- **Browse Archives In-App:** Open ZIP and 7z files to inspect their contents before extracting them.
- **Password-Protected Archives:** Password prompts are built into archive browsing and extraction, with retry behavior for encrypted archives and wrong-password errors.
- **Safer Extraction:** Arcile blocks unsafe archive paths, preserves keep-both conflict behavior, and refreshes the visible folder when archive work completes.

### 🎨 Browser, Selection & Navigation Polish
- **Material 3 Expressive Selection Toolbar:** Selection actions now use a floating, segmented toolbar with shape morphing, a detached secondary FAB, and a cleaner action layout.
- **Invert Selection & Total Size:** Selection mode now supports inverting selected files and shows the total size of the current selection in the top bar.
- **Back Behaves Like the UI Looks:** Back now closes dialogs, sheets, search, expanded FAB menus, and file selections before leaving the current folder.
- **Better Back Stack Memory:** Opening folders from Storage Dashboard, Recent Files, and Quick Access now preserves where you came from, so back returns to the expected screen.
- **Swipe Navigation Cleanup:** Home-to-Browser swipes restore your last opened folder, category screens remain isolated, and duplicate gesture handlers were removed for smoother scrolling and pull-to-refresh.
- **Category Folder Tabs:** Category views now group files by containing folder, show counts and sizes, and support horizontal swipes between tabs.

### 🖼️ Thumbnails, Media & Visual Feedback
- **Faster Video Thumbnails:** Arcile now leans on Android's MediaStore thumbnail cache, making video previews much faster and more persistent across app restarts.
- **PDF Thumbnails:** PDFs now get Coil-backed previews using Android's `PdfRenderer`.
- **Smarter Grid Loading:** Grid thumbnails are bounded to actual layout constraints, reducing memory pressure and avoiding unnecessary cache eviction.
- **Thumbnail Fallbacks:** Media preview failures now fall back more gracefully instead of leaving awkward blank states.
- **Smoother Operation Progress:** Long-running operations use a frame-clock-driven progress animation with less jitter, better completion snapping, and color-coded success/failure/cancel states.
- **Better Snackbars:** Operation feedback now uses a more polished Material 3 Expressive snackbar host.

### 🛠️ File Operations, Safety & Reliability
- **Foreground Trash/Delete:** Trash and permanent-delete operations now run through the foreground bulk-operation pipeline, giving destructive work the same foreground handling as copy/move.
- **Stronger Trash Integrity:** Trash and restore fallback copies verify source and destination integrity with recursive SHA-256 checks before deleting originals. This is safer, but very large trash/restore jobs may take longer while verification runs.
- **Safer Trash Crypto:** Encrypted trash metadata now records whether it used KeyStore or PBKDF2 fallback keys, strengthens fallback derivation, and preserves migration warnings instead of silently deleting unreadable metadata.
- **Symlink Safety:** Trash and permanent-delete paths reject symlinked entries through shared path validation.
- **Cleaner Operation State:** Copy, move, paste, cancel, trash, and delete flows now handle completion, cancellation, startup failure, and service recreation races more reliably.
- **Clipboard Management:** Clipboard status was redesigned as a live progress pill, paste state clears more reliably after use, and a clipboard management dialog lets users inspect or remove queued items.
- **Properties Fixes:** Properties now works from Recent Files and reports accurate metadata for selected items.

### ⚡ Performance & Storage
- **Faster MediaStore Queries:** Recent Files and search avoid unnecessary per-row filesystem stats, push pagination into MediaStore queries, and scope queries by storage root where possible.
- **Faster Storage Analytics:** Category totals are now aggregated more efficiently, including a single-pass MediaStore path for dashboard summaries.
- **Bounded Folder Stats:** Huge folder scans now respect cancellation and return partial results after a safety cap instead of monopolizing background workers.
- **Less Main-Thread Work:** Browser preferences, external file staging, volume preloading, and large open/share staging work were moved off the main thread or into lifecycle-aware scopes.
- **Stable Lists:** Browser, Home, Recent Files, and Trash lists now use stable item keys and content types, reducing scroll jumps and recomposition churn during refreshes.
- **Release Error Visibility:** Production file-operation, storage, and trash errors are now logged instead of disappearing silently.

### 🧪 Developer & Platform Updates
- **Random Fake File Generator:** A new browser tool can create files of any size and extension through the background operation pipeline, useful for testing transfers and storage behavior.
- **Coverage Reporting:** Debug unit-test coverage is now enabled, so the Gradle coverage task produces a real HTML report for tracking untested areas.
- **Test Suite Maintenance:** Added regression coverage for path safety, conflict detection, use-case dispatch, and progress-state behavior, and refreshed the instrumented Home, Quick Access, and Empty State tests for the current Compose test APIs.
- **Release Build Fix:** Release minification now handles Apache Commons Compress' optional Zstandard reference, preventing R8 from failing on a class Arcile does not package directly.
- **Android 17 Build Readiness:** Arcile now compiles against Android SDK 37 while keeping `targetSdk` at 36.
- **Modern Compose Stack:** Compose, Material 3, Activity, Lifecycle, Navigation, DataStore, Coil, coroutines, Robolectric, MockK, Turbine, Hilt Compose integration, and archive libraries were refreshed.
- **Adaptive UI Foundation:** Material 3 Adaptive libraries were added to prepare for future tablet, foldable, list-detail, and multi-pane layouts.
- **Open Source Licenses Updated:** Archive libraries such as Zip4j, Apache Commons Compress, and Tukaani XZ are documented in the licenses screen.

## 🐛 Known Issues (Beta)

Arcile is still in active beta. The 0.7.0 cycle resolved many older operation, onboarding, archive, navigation, and storage issues, but there are still a few important things to watch out for:

- **Operation recovery is not fully durable yet:** Copy, move, trash, delete, archive, and extraction jobs now use foreground operation handling, but active requests and recovery state are not yet journaled across process death, reboot, or force stop. If Android kills Arcile mid-operation, you may need to review the affected folders for partial output or temporary files.
- **Large transfers can take longer than expected:** Arcile performs full integrity checks before deleting originals in several copy, move, trash, and restore paths. This improves safety, but very large folders or media libraries can look slow while verification is running.
- **Use care with huge or untrusted archives:** ZIP and 7z creation, browsing, password handling, and extraction are now built in, but archive-bomb limits, output-size caps, entry-count caps, and compression-ratio guards are still future work. Avoid extracting archives you do not trust.
- **Check destructive-operation dialogs carefully:** Arcile has safer delete plumbing, but Trash, permanent delete, Android/native delete prompts, OTG, unclassified drives, and mixed storage selections can still behave differently. Files on OTG or unclassified drives may be permanently deleted because those locations do not support Arcile Trash.
- **Some platform behavior is still device-specific:** Please continue reporting MediaStore oddities, external-storage quirks, archive edge cases, unusual permission flows, SD/USB volume classification problems, and background-operation issues.

---

# v0.6.0 Beta

**Release Date:** April 19, 2026  
**Previous release:** v0.5.0 Beta

Welcome to **Arcile v0.6.0 Beta**! This massive update consolidates weeks of iterative improvements into a single, highly polished release. I've completely overhauled how you navigate, view, and manage your files, while fortifying the app's performance and security under the hood. 

Here is a comprehensive look at everything that's new and improved since v0.5.0:

## 🚀 What's New & Improved

### 🎨 Fresh, Smooth UI & Navigation
- **Swipe Gestures:** Navigate intuitively by swiping left on the Home screen to instantly open the file browser, and swiping right to quickly go back.
- **Redesigned File Lists:** Files and folders now feature larger icons inside circular containers with cleaner typography and a tighter layout, letting you see more at once.
- **Unified Browser Controls:** Say goodbye to scattered settings! A single, easy-to-use menu now controls your sort order, list/grid view, zoom levels, and adaptive grid sizing.
- **Smart Layout Memory:** Arcile now remembers your preferred layout (like grid vs. list) and zoom level for *each specific folder* you visit, automatically applying it to subfolders too.
- **Buttery Smooth Performance:** I’ve massively reduced UI stuttering, fixed app launch delays, and eliminated annoying screen flickers when loading large directories.
- **Open Source Licenses:** A new dedicated screen in Settings now transparently lists all third-party libraries powering Arcile.

### 🗂️ Smarter Folders & Metadata
- **Instant Folder Stats:** Folder rows now instantly display the total file count and size directly underneath the name without you having to open them.
- **Selection Properties Dialog:** You can now view detailed properties (total size, file/folder counts, hidden items, and exact timestamps) for any single or multi-file selection via the 3-dot menu.
- **Detailed Timestamps:** File rows now show both the modified date *and* time, making it easier to compare recent file changes at a glance.
- **Lightning Fast Loading:** Loading massive media folders (like Pictures or Downloads) is now nearly instantaneous thanks to heavily optimized caching and database queries.

### 📌 Dynamic Quick Access & Organization
- **Custom Quick Access:** The static Folders section on the Home screen is now fully customizable! You can pin and manage any local folder directly on your dashboard for instant access.
- **System Folder Access:** I've added special support for pinning restricted system folders (like `Android/data`), safely bridging you into the native OEM files app when tapped.
- **Cleaner Action Bar:** During selection, only high-priority actions (Copy, Cut, Delete, Edit) remain visible upfront, keeping the top bar uncluttered and focused.

### 🛠️ Safer, More Reliable File Operations
- **Progress-Aware Actions:** Long-running file operations now replace the main floating button with a live progress circle, allowing you to easily track or cancel the transfer.
- **Foreground Transfers:** Copying and moving files now run reliably as dedicated background services, ensuring they survive and finish even if you switch to another app.
- **Smarter Paste:** Copy conflicts are now detected much faster. Overwriting, skipping, or keeping both files (with clean auto-renaming) is safer than ever.
- **Non-Blocking Feedback:** Success, cancellation, and error messages now appear as unobtrusive snackbars at the bottom of the screen instead of interrupting your flow with popups.
- **Trash Restore Verification:** Restoring files from the Trash is now heavily verified to prevent data loss if a cross-drive transfer fails midway.

### 🔒 Enhanced Security & Privacy
- **Encrypted Trash Bin:** The Trash Bin's internal metadata is now securely encrypted using the native Android KeyStore, making it bulletproof and private.
- **Safer Sharing:** I've locked down how files are exposed to other apps, ensuring your private app cache and metadata can never be accidentally shared.
- **Cloud Backup Protection:** Sensitive application settings, analytics, and encryption keys are now explicitly blocked from being uploaded to Google cloud backups.

## 🐛 Known Issues (Beta)

As this is an active Beta, please be aware of the following tracked issues:

### ⚠️ Medium
- **File Operation Stalls:** Copying or moving large amounts of files might occasionally get stuck in the UI or fail to cancel if the background service encounters an error.
- **Deletion Progress Missing:** Moving files to the Trash or permanently deleting them currently happens in the background without a progress indicator, and could be interrupted if you switch apps.
- **List Jitter:** Sorting or refreshing large file lists may cause the screen to jump or lose your scroll position.
- **Volume Classification Errors:** Rapidly plugging and unplugging USB drives might cause the app to crash or lose your saved drive preferences.

### 🔵 Low
- Brief UI flickering may occur when pasting files.
- Some operation feedback messages (snackbars) don't fully match the new Material 3 design.
- Slight delays when recovering from Trash Bin encryption errors.
- Critical background errors may fail silently, making debugging difficult.

---

# v0.5.0 Beta

**Release Date:** March 23, 2026  
**Previous release:** v0.4.5 Beta

Welcome to **Arcile v0.5.0 Beta**! This release heavily focuses on security enhancements, code quality, and structural robustness. I've completely overhauled the encryption layer for the Trash Bin, locked down external URI exposures, and officially raised the minimum API level to Android 11 to ensure a modern, stable scoped storage architecture.

## 🛡️ Security & Privacy
- **Trash Vault Encryption:** Replaced the plain-text `.arcile/.metadata` JSON storage with a secure AES256-GCM encryption layer. Keys are dynamically derived via hardware `ANDROID_ID`, ensuring that trash metadata stays perfectly hidden from other apps but fully survives app updates and reinstalls.
- **FileProvider Sandbox:** Closed a structural vulnerability by removing the root `external_root` path from `file_provider_paths.xml`. The `FileProvider` now correctly restricts external URIs strictly to standard public media folders (Downloads, Documents, Pictures, etc.).
- **Sensitive File Protection:** Explicitly blocked the ability to open or share internal `.arcile` metadata and application cache files via `MainActivity` and `ShareHelper`, preventing unintended system exposure.
- **Build Hardening & Credentials:** `build.gradle.kts` now securely loads signing properties, falling back to a clean debug configuration rather than failing when keys are missing. Added specific R8/ProGuard keep rules to ensure release build stability.

## 🚀 What's New & Changed
- **Target Platform:** Completely dropped support for Android 10 (API 29). `minSdk` officially raised to 30 (Android 11) to eliminate all scoped storage crash loops and align natively with `MANAGE_EXTERNAL_STORAGE` architecture.
- **Search Robustness:** Removed the arbitrary `.maxDepth(10)` limit on path-scoped searching. Deeply nested repository or archive folder structures can now be fully traversed, constrained solely by the 1000-item memory limit.
- **Accessibility & Localization:** Extracted massive swaths of hardcoded English texts, labels, and iconography `contentDescription` properties into `strings.xml`, instantly granting full TalkBack accessibility scaling for vision-impaired and non-English users.
- **UI/UX Polish:** Replaced hardcoded padding and margin components with strictly bounded `MaterialTheme.spacing` tokens to guarantee Material Design 3 scale compliance. Extracted shared Pull-to-Refresh logic into `ArcilePullRefreshIndicator.kt`.

## 🛠️ Fixes & Polish
- **Recent Files Affordance:** Added a fully wired `PullToRefreshBox` wrap to `RecentFilesScreen` to finally expose the previously inaccessible `onRefresh` manual action to users.
- **UI Performance & Formatting:** Hoisted `Calendar` and `SimpleDateFormat` logic out of recomposition scopes. Introduced reliable, locale-aware date string updates across the entire application interface.
- **Error Handling:** `TrashScreen` now properly surfaces persistent operational failures and errors via unified non-blocking `Snackbar` overlays. Introduced typed `FileOperationException` for granular error recovery.
- **Concurrency Safety:** Fixed unstructured concurrency leaks by properly re-throwing `CancellationException` inside Coroutines/Flows globally.
- **Testing:** Bootstrapped the `androidTest` layer with Robolectric Compose implementations and expanded the JVM test suite to handle high-risk branches, verifying edge cases including rename collisions and missing volume destinations.

---

# v0.4.5 Beta

**Release Date:** March 19, 2026  
**Previous release:** v0.4.0 Beta

Welcome to **Arcile v0.4.5 Beta**! This release focuses heavily on smoothing out the rough edges from the massive 0.4.0 update. I've eliminated UI flickers, drastically improved loading speeds for massive media folders, and redesigned the grid view to feel much more premium and uniform.

## 🚀 What's New & Improved

### ⚡ Lightning Fast Category Loading
- **Instant Media:** Opening categories with thousands of images or videos used to cause the app to freeze momentarily while checking the filesystem. I've optimized the query engine to trust the Android MediaStore index, making large category loading nearly instantaneous.

### 🎨 Visual Polish & Fluidity
- **Dynamic Colors:** I've integrated MaterialKolor to automatically generate beautiful, high-quality Material 3 color schemes based on your selected accent color.
- **Smooth Entry Animations:** Files and folders no longer pop onto the screen abruptly. I've added buttery-smooth staggered entry and reordering animations to all list and grid views.
- **Redesigned Image Grids:** The Grid view has been completely overhauled. Images now stretch edge-to-edge within their cards without awkward borders, and every card is locked to a perfect `1:1` aspect ratio so the grid looks perfectly uniform.
- **No More Startup Flicker:** I fixed a visual glitch where the app would briefly flash the default purple theme or the permission screen on launch. The app now seamlessly holds the launch screen until your saved themes are fully loaded.
- **Instant Dashboard:** Opening the Storage Dashboard from the Home screen is now instantaneous, reusing the data you've already loaded instead of calculating it from scratch twice.
- **Polished Permissions:** The initial permission request screen has been redesigned into a beautiful Material 3 Elevated Card, giving a much more professional first impression.
- **Glitch-Free Navigation:** I fixed subtle overlapping layout bugs and "double-padding" issues by cleaning up the root application scaffolding. Folder-to-folder crossfade animations are also now flawlessly typed, preventing random visual artifacts during fast navigation.

### 🌍 Global Support
- **Full Internationalization (i18n):** Arcile is officially ready for the world. I've extracted over 130 hardcoded text strings into a flexible translation system, laying the groundwork for full multi-language support in future updates.

### 🛠️ Stability & Under-the-Hood Fixes
- **Double Refreshing Fixed:** I eliminated a persistent glitch where the Home and Recent Files screens would load their data twice.
- **Silent Failures Patched:** I've fortified the core File Repository. If edge-case operations fail (like creating hidden `.nomedia` files for the Trash Bin, or dealing with locked media indices), they no longer crash silently in the background, making future debugging much easier.
- **Test Suite Enhancements:** I started bridging the gaps in my automated testing suite, adding new core logic checks to ensure critical file naming conflicts (like copying duplicate files) are resolved correctly and safely.

## 🐛 Known Issues (Beta)

As this is an active Beta, please be aware of the following tracked issues:

### 🚨 Critical
- **Splash Screen Hang:** In rare cases of data corruption, the app may hang on the launch screen. (Workaround: Clear app data).
- **Caching IOException:** On some devices, the new storage caching system may fail due to unsanitized volume identifiers.
- **Category Navigation:** Home screen category shortcuts may not function correctly if the app language is set to anything other than English.

### ⚠️ Medium
- **Unscrollable Themes:** On small screens or in landscape mode, the new Accent Color selector may not scroll, hiding some theme options.
- **Redundant Processing:** Opening Storage Management may trigger a redundant background calculation of storage statistics.

### 🔵 Low
- Minor missing translations in the Paste Conflict dialog.
- Incorrect labeling for "Unclassified" drives in the Trash list.
- Ongoing TalkBack accessibility refinements for the new theme swatches.
- Background log-swallowing during file sharing operations.

---


# v0.4.0 Beta

**Release Date:** March 16, 2026  
**Previous release:** v0.3.0 Beta

Welcome to **Arcile v0.4.0 Beta**! This massive update bridges the gap between v0.3.0 Beta and my current release, bringing a fundamental overhaul to how Arcile handles external storage, a brand-new smart paste system, and a ton of Material 3 visual polish.

Here is everything new, improved, and what you should watch out for in this release.

## 🚀 What's New

### 💾 Complete External Storage & SD Card Support
Arcile now fully understands the difference between a permanent SD card and a temporary USB OTG drive.
- **Smart Storage Classification:** When you insert a new drive, Arcile will prompt you to classify it. You can manage these at any time in the new **Settings > Storage Management** screen.
- **SD Cards as First-Class Citizens:** Drives classified as SD Cards now fully support the Trash Bin, file indexing, Home Screen Categories, the Storage Dashboard, and Recent Files.
- **Per-Volume Trash:** Each permanent drive (Internal and SD) now maintains its own dedicated and safe Trash Bin.
- **USB Tracking:** Inserting or removing an external drive can now be tracked by manually refreshing the app—no restarts required!

### 📋 Smart Paste & Conflict Resolution
Copying and moving files just got a lot safer and smarter.
- **No More Silent Overwrites:** I've introduced a robust conflict dialog when you paste files with overlapping names.
- **Smart Folder Merging:** When pasting folders, Arcile now uses a precise "Merge" paradigm instead of a generic replace action.
- **Batch Processing:** Moving a lot of files? Use the new "Do this for all remaining conflicts" checkbox to blast through large transfers.
- **Intelligent Auto-Renaming:** Choosing "Keep Both" will now cleanly append iterative numbers (e.g., `(1)`, `(2)`) instead of polluting your filenames.

### 🎨 Visual Polish & Material 3 Enhancements
Arcile looks and feels better than ever with deep Material 3 integration.
- **Expanded Themes & Accents:** Choose from 20 Material Design color presets, including a true **Monochrome** grayscale mode.
- **Dynamic Storage Bars:** The storage bar now features liquid fill animations, smooth transitions, and dynamic usage-based colors (Green/Orange/Red) for OTG drives to give you immediate capacity feedback.
- **Silky Smooth Animations:** Enjoy smooth folder-to-folder crossfades while browsing, expressive new "squircle" shapes across the app, and a refined fade overlay for the Expandable FAB menu.
- **Redesigned Utilities & Empty States:** The Utilities section on the Home screen has been upgraded to a modern horizontal carousel. I've also added beautiful, animated "Empty States" across the app when there are no files to show. *(Note: "Secure Vault" has been renamed to "OnlyFiles").*
- **Native SplashScreen:** The app launch screen now fully supports the Jetpack SplashScreen API with seamless Light/Dark mode transitions.

### 🗂️ Better Browsing & Organization
- **App State Memory:** Arcile now remembers exactly which folder you were in and your navigation history, even if your phone's memory manager closes the app in the background!
- **Persistent Sorting:** Your sorting preferences (like Date Newest or A-Z) are now saved per-directory or globally.
- **Subfolder Sorting:** You can now apply a sorting filter dynamically to a folder and *all* of its subfolders in one tap.
- **Pull-to-Refresh:** Manually refresh the Home screen, Storage Dashboard, and Recent Files by swiping down.
- **Smarter Defaults:** Home screen categories now default to sorting by "Date Newest" so your latest files are always at the top.
- **Accessibility Improvements:** We've added rich TalkBack screen reader support for file items and improved touch targets across the Home screen for a more accessible experience.
- **Refreshed About Screen:** Check out the updated About screen for a more complete overview of Arcile.

## ⚠️ Things to Keep in Mind

As I roll out these powerful new storage features, there are a few important details to watch out for:

- **Permanent Deletion on USB/OTG:** Drives classified as "OTG" or left "Unclassified" **do not support the Trash Bin**. Deleting files from these drives is **permanent**. Arcile will display an informational banner while you browse these drives to remind you.
- **Excluded from Global Views:** To keep your main library fast and uncluttered, files on OTG/Unclassified drives will **not** show up in your global Search, Recent Files, or Storage Dashboard totals. You can still search them locally by navigating to the drive first!
- **Trash Restore Fallback:** If you try to restore a file from the Trash Bin but the original drive (like an SD card) is no longer inserted, Arcile won't fail—it will simply prompt you to pick a new destination to restore the file to.
- **Dashboard Loading Speeds:** I am still working on a caching layer for the Storage Dashboard. If you have massive amounts of files, you might notice a slight loading time when Arcile calculates the category sizes on the Home screen or Dashboard.

## 🐛 Known Issues (Beta)

As this is a Beta release, there are a few non-critical bugs and UI quirks you might encounter:
- **UI Glitches:** Sometimes the "Empty Folder" illustration might momentarily appear when opening the app directly to a storage volume's root before files load. Additionally, some screens might exhibit minor layout padding quirks.
- **Light Theme Contrast:** A few "secondary" color pairs in the newly expanded Light Theme palettes may have low readability/contrast.
- **Accessibility & Translation:** The app currently lacks full localization (hardcoded English text) and several buttons/icons are missing proper TalkBack screen reader descriptions. Selection state is also not announced by TalkBack in lists.

## 🗺️ Roadmap & Next Steps

Please note that the next release will be slightly delayed. I am shifting my immediate focus toward strengthening the foundation of Arcile. This includes deep dives into:
* **Codebase Architecture:** Refactoring core modules for better scalability.
* **Code Quality:** Implementing more rigorous testing and linting standards.
* **Maintainability:** Simplifying complex logic to ensure the project remains easy to update and contribute to in the long run.

For a detailed list of all technical changes and commits, please see the [CHANGELOG.md](https://github.com/qtremors/arcile/blob/main/CHANGELOG.md).

*Thank you for testing Arcile Beta! If you encounter any issues, please create an issue.*


---


# v0.3.0 Beta

**Release Date:** March 11, 2026  
**Previous release:** v0.2.0 Beta

This is a large update. A lot has changed since v0.2.0 — new features, a full design overhaul, and many bug fixes. Here's what's new.

## ✨ New Features

### Trash Bin
Files you delete are now moved to a hidden Trash Bin — not permanently deleted. You can restore them any time, or empty the bin to permanently delete everything.

### Copy, Cut & Paste
Full clipboard-based file operations. Copy or cut files, then paste them anywhere. A persistent clipboard bar appears in the toolbar so you always know what's in your clipboard.

### File Search with Filters
Search is now scoped to your current folder. You can filter results by:
- Type (Images, Videos, Audio, Docs, Folders, Files)
- Size (e.g. < 10MB, 10–100MB)
- Date modified (Today, Last 7 Days, Last 30 Days)

### Storage Dashboard
Long-press the storage card on the Home screen to open a visual breakdown of your device storage by category. Tap any category to open it directly in the file browser.

### Recent Files — See All
Tap "See All" on the Recent Files section to see a full chronological view grouped by day — Today, Yesterday, and further back.

### Smart Selection
In selection mode, long-press a second file to automatically select everything between your first and second pick.

---

## 🎨 Design Overhaul
The app has been fully updated to **Material 3 Expressive** — new shapes, smoother animations, spring physics, and the Outfit font restored throughout.

---

## 🐛 Bug Fixes
- Fixed: Delete dialog incorrectly said "This action cannot be undone" — it now correctly says files are moved to Trash.
- Fixed: Recent Files screen sometimes showed stale data after file operations.
- Fixed: Some audio files caused the app to crash when loading album art.
- Fixed: Random directories were appearing as 0-byte files in Recent Files.
- Fixed: Double-launch bug when navigating back in the file browser.
- Fixed: OLED dark theme surfaces were not dark enough.

---

## ⚠️ Known Issues

- **Double-back navigation** — When deep inside folders, you may need to press back once extra to fully return to the Home screen. Being tracked for a fix.
- **Clipboard snackbar on resume** — Returning to the app after backgrounding may re-show the clipboard confirmation toast. Cosmetic only.
- **Search scope** — Search only covers your current directory and its subfolders. Global search across all storage is not yet supported.

---

## 🔐 Security
- File and folder names are now validated to block path traversal attacks.
- The app no longer requests legacy external storage permissions on Android 10.


---


# v0.2.0 Beta

**Release Date:** March 06, 2026  
**Previous release:** v0.1.0 Beta

This beta release marks a major step forward in making **Arcile** a premium, high-performance file manager. We've focused on bringing the UI up to modern Material 3 standards and optimizing core systems for native-level smoothness.

## 🌟 Key Enhancements
*   **Material 3 Redesign**: The Settings screen has been completely rebuilt using Elevated Tonal Cards and M3 `ListItem` components, supporting full "Material You" dynamic theming.
*   **120FPS Fluidity**: Added support for 120Hz/144Hz display peak refresh rates and implemented `Modifier.animateItem()` to ensure every interaction feels buttery-smooth.
*   **Visual Thumbnails**: Image and Video previews now load seamlessly across the app thanks to integrated Coil caching.
*   **MediaStore Integration**: Updated the search engine and category views to utilize the Android MediaStore, providing faster, device-wide indexing.
*   **Architecture & Stability**: Switched to **DataStore** for persistent settings and replaced legacy `Stack` structures with modern `ArrayDeque` for more reliable navigation.

## 📁 What's New
- **Search & Sort**: Improved search bar logic and added sort dialogs for better file organization.
- **Hardware Insights**: New interactive developer and hardware information tiles in Settings.
- **Security & Fixes**: Implemented path traversal protection, resolved directory refresh loops, and fixed critical compilation issues in the settings module.

*This beta sets the stage for my 1.0 milestone. Thanks for helping me test the future of Arcile!*

