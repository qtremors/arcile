# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.5.0
> **Last Updated:** 2026-03-23

---

## Table of Contents

1. [PR Review Findings](#1-pr-review-findings)
2. [Architecture & Refactoring](#2-architecture--refactoring)
3. [Performance & Efficiency](#3-performance--efficiency)
4. [General & Code Quality](#4-general--code-quality)
5. [Comprehensive Audit Findings](#5-comprehensive-audit-findings)

---

## 1. PR Review Findings

### Security & Encryption
- [ ] **Decrypt before JSON sniffing**: In `TrashManager.kt`, parse trash metadata by decrypting before JSON sniffing. Avoid unsafe `startsWith("{")` checks that skip decryption on random IV collisions.
- [ ] **Secure SALT Generation**: In `TrashManager.kt`, replace the hardcoded `SALT` constant with a securely generated random salt persisted per-device.
- [ ] **Secure Encryption & KeyStore**: In `TrashManager.kt`, transition from `androidId` based key derivation to Android KeyStore for secure key management. Remove plaintext fallbacks in catch blocks; implement retry loops and log sanitized errors.

### Architecture & Data
- [ ] **Blocking Volume Discovery**: In `VolumeProvider.kt`, move blocking calls to `Dispatchers.IO` and fix the registration race in `callbackFlow`.
- [ ] **Missing Import in Repository**: Add missing `kotlinx.serialization.decodeFromString` import in `StorageClassificationRepository.kt`.

### ViewModels & State
- [ ] **`_nativeRequestFlow` Replay**: In `BrowserViewModel.kt`, change `MutableSharedFlow` to use `replay = 1` for `IntentSender` to survive configuration changes.
- [ ] **`loadRecentFiles` State Handling**: In `RecentFilesViewModel.kt`, prevent clobbering pagination and error states by capturing the previous state before updating.

### UI Components
- [ ] **Lazy DSL Imports**: Add missing `items` and `animateItem` imports in `FileList.kt`.
- [ ] **Midnight-based "Today" Preset**: In `SearchFiltersBottomSheet.kt`, use local midnight for the "Today" filter and exact equality checks for presets.
- [ ] **Snackbar Order in Trash**: In `TrashScreen.kt`, ensure `showSnackbar` completes before calling `onClearError()` in the `LaunchedEffect`.
- [ ] **Locale-aware Date Formatting**: In `DateUtils.kt`, use the active configuration locale for `SimpleDateFormat`.

### Misc & Cleanup
- [ ] **Incorrect ProGuard Rule**: Fix/remove the rule keeping members instead of classes for `@Serializable` in `proguard-rules.pro`.
- [ ] **Sanitize Share Logging**: In `ShareHelper.kt`, sanitize exception logging to avoid leaking filesystem paths.
- [ ] **Test Double for `IntentSender`**: In `RecentFilesViewModelTest.kt`, replace `sun.misc.Unsafe` usage with a proper test double (`TestIntentSender`).

## 5. Comprehensive Audit Findings

### Performance & Smoothness
- [ ] **Coil Image Memory Overhead**: In `FileList.kt` and `FileGrid.kt`, `AsyncImage` directly loads `File(file.absolutePath)` for media files. This causes massive memory spikes, GC thrashing, and dropped frames during scrolling as full-resolution images/videos are loaded. Modify `ImageRequest` with explicit `.size()` constraints or switch to MediaStore `loadThumbnail`.
- [ ] **Redundant String Allocations in Compose**: In `FileList.kt` and `FileGrid.kt`, the `isMedia` check repeatedly performs `file.name.substringAfterLast('.').lowercase()` during list item composition. Use the pre-calculated `extension` or `mimeType` properties already available in `FileModel` to reduce memory allocations and improve scrolling smoothness.
- [ ] [NEW] **Excessive Disk I/O in MediaStore Loops**: In `MediaStoreClient.kt`, avoid calling `File.canonicalPath` inside cursor loops. This causes severe UI jank on devices with many files. Refactor `matchesScope` to use pre-calculated canonical roots.
- [ ] [NEW] **Sequential Repository Fetches**: In `HomeViewModel.kt`, parallelize independent data fetches in `loadHomeData` using `async`/`awaitAll` to reduce perceived loading latency.

### Architecture & Background Processing
- [ ] **Bulletproof Background Execution**: In `ClipboardDelegate.kt` (and `FileSystemDataSource.kt`), migrate bulk file operations (`copyFiles`, `moveFiles`) to `WorkManager` or a standard Foreground Service. Current `viewModelScope` execution will silently abort if the app is backgrounded and killed by the OS.

### Core Functionality & Edge Cases
- [ ] **FileProvider Path Restriction Crash**: In `file_provider_paths.xml`, the `<external-path>` entries are hardcoded to specific standard directories (e.g., `Download/`, `Documents/`). Opening or sharing a file from an unlisted custom directory (e.g., `/storage/emulated/0/MyFolder/`) will cause `FileProvider.getUriForFile` to throw an `IllegalArgumentException` and fail. Replace the specific paths with `<external-path name="external_files" path="." />` to support all accessible external storage files.
- [ ] [NEW] **Deprecated MediaStore Deletion**: In `TrashManager.kt`, replace `ContentResolver.delete` based on `DATA` path with URI-based deletion using MediaStore IDs to ensure reliability on Android 11+.
- [ ] [NEW] **Splash Screen Timeout**: In `MainActivity.kt`, add a timeout to the `themeState.first()` call in the splash screen logic to prevent permanent hangs on startup.

### App Size & Resource Optimization
- [ ] **APK Size - Resource Configurations**: In `app/build.gradle.kts`, add `resConfigs("en", "es")` (for your supported languages) in `defaultConfig` to strip megabytes of unused translated strings and resources bundled by AndroidX and Material3 libraries.
- [ ] **APK Size - ABI Splits**: If distributing APKs instead of App Bundles (.aab), configure `splits { abi { enable = true ... } }` in `build.gradle.kts` to prevent bundling x86, ARMv7, and ARM64 native libraries into a single massive APK.
- [ ] **Post-Install Size - Native Libs**: Add `android:extractNativeLibs="false"` to the `<application>` tag in `AndroidManifest.xml`. This prevents the OS from copying `.so` files into the internal data partition, saving duplicate space.
- [ ] **Storage Usage - Auto-Empty Trash**: `TrashManager.kt` currently leaves files in `.arcile/.trash` indefinitely. Implement a `WorkManager` periodic task to auto-delete trash items older than 30 days to prevent silent massive storage bloat.
- [ ] **Memory Usage - Thumbnail Caching**: Limit Coil's disk cache size in `ImageLoader` configuration so thumbnail caches don't grow infinitely in the app's cache directory.

---

# Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **Advanced Storage Access (SAF)**: Implement a `DocumentFile` / Storage Access Framework (SAF) fallback DataSource specifically for restricted directories (`Android/data`, `Android/obb`), allowing users to modify app data folders just like top-tier file managers.
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.
- **Storage Health Diagnostics**: Basic S.M.A.R.T. status checks, disk health metrics, and repair/trim suggestions for mounted volumes.

### File Operations & Automation
- **File/Folder Properties Dialog**: Display detailed metadata for selected items.
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser.
- **Automated Task Rules**: Implement trigger-based operations (e.g., "Auto-move video files from Camera to Videos folder daily").
- **Operation Queue & FAB Manager**: Implement a "Bank" for operations. A running operation replaces the floating action button (FAB) with a progress ring. Tapping it shows a queue of paused/running tasks with the ability to gracefully cancel (e.g., stop after 10 of 100 files are copied).

### Home Screen & UI Polish
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press (selection mode), successful file operations, and error states to make the app feel alive and premium.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally-scrollable carousel of the 10 most recently modified images and videos.
- **Customizable Quick Access**: Allow users to hardcode, add, remove, and restore pinned folders to the Quick Access section on the Home screen.
- **Header Logo Integration**: Add a subtle, branded Arcile logo into the `ArcileTopBar` for stronger visual identity.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations (e.g., Lottie) for empty folders, trash, and search results.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes (e.g., heavily rounded "squircle" vs. standard rounded corners for cards and buttons).

### Browsing & Organization
- **Rich Media Previews**: Implement custom Coil `Fetcher` components to extract APK icons (using `PackageManager`) and PDF thumbnails, making the grid view visually rich regardless of file type.
- **Starred / Favorited Files**: Add a "Starred" section to the Home screen and a star toggle on individual files/folders.
- **Enhanced Category Browsing**: When opening a file category, display all related folders containing matching files with a tabbed or segmented navigation bar.
- **Storage Analyzer ("Filelight" view)**: A dedicated radial map or sunburst chart to visualize storage usage by folder/file type (similar to Filelight or WinDirStat).
- **Folder Subtitle Metadata**: Display a compact subtitle below each folder name showing the item count and total size (e.g., `24 items · 1.3 GB`).

### Tools & Development
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations (moves, copies, deletions) for auditing purposes.
- **Dummy File Generator**: A developer/testing tool to quickly create "fake" files of a specified size to fill space or test transfers.
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding/tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor (F-Droid ready) without any proprietary blobs or dependencies.

### Settings & About
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive "flower petal" UI. The middle acts as the dynamic color, surrounded by preset colors, which shrink dynamically on long-press into a monochrome mode.
- **Combined Changelog & Version**: Streamline the About screen by merging the version info and changelog buttons into a cleaner layout.
- **External Link Indicators**: Update the About page so all external links explicitly show an "open in browser" trailing icon.

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android's multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.

---
