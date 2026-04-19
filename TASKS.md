# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.6.0
> **Last Updated:** 2026-04-19

---

## Remediation Backlog

### 🛠️ Build & CI

- [ ] **Add regression coverage and CI gates for recent escapes** `[Medium]`
  - **Location:** `app/src/test`, `app/src/androidTest`, `app/build.gradle.kts`
  - **Problem:** Current CI/suite lacks release assembly/lint gating, bulk-operation lifecycle edge cases, and cross-midnight grouping tests.
  - **Impact:** Regressions like the backup XML error and service-orchestration edge cases can survive until manual validation.
  - **Fix:** Add CI tasks for `:app:assembleRelease` and lint; add targeted tests for bulk operations and date boundaries.
  - **Verification:** CI fails correctly when those regressions are reintroduced.

### ⚡ Performance & Optimization

- [ ] **Avoid allocating ancestor-path lists on every mutation** `[Low]`
  - **Location:** `TrashManager.kt:pathWithAncestors()`, `FileSystemDataSource.kt:pathWithAncestors()`
  - **Problem:** `pathWithAncestors()` is duplicated between `TrashManager` and `FileSystemDataSource`, and both create temporary `File` objects, call `canonicalFile`, and build ancestor lists on every mutation event.
  - **Impact:** Minor GC pressure on multi-file operations; code duplication increases maintenance burden.
  - **Fix:** Extract `pathWithAncestors` to a shared utility with a single implementation; consider caching canonical root lookups.
  - **Verification:** No behavioral change; verify with existing tests.

### 🐞 Correctness & Reliability
- [ ] **Harden foreground bulk copy/move orchestration** `[High]`
  - **Location:** `BulkFileOperationCoordinator.kt`, `BulkFileOperationService.kt`, `ClipboardDelegate.kt`
  - **Problem:** UI marks operation active before `startForegroundService` succeeds. Cancellations aren't tied to operation IDs. The service `stopSelf(startId)` uses a single `startId`, which can conflict if a new command arrives quickly.
  - **Impact:** Stuck UI states, failed cancellations, or crashes if service fails to start or a cancel arrives early.
  - **Fix:** Make service launch transactional, attach cancel to operation IDs, handle launch failures gracefully. Use `stopSelf()` without `startId` or track latest `startId` correctly.
  - **Verification:** Immediate cancel-after-paste and simulated start failures result in a clean idle/error UI state.

- [ ] **Guard `suppressedVolumeKeys` against concurrent mutation** `[Medium]`
  - **Location:** `HomeViewModel.kt:74`
  - **Problem:** `suppressedVolumeKeys` is a plain `mutableSetOf<String>()` mutated from both the main thread (`hideClassificationPrompt`) and from coroutines launched on unspecified dispatchers (`setVolumeClassification`, `resetVolumeClassification`). `MutableSet` is not thread-safe.
  - **Impact:** Potential `ConcurrentModificationException` or lost suppressions on rapid volume plug/unplug events.
  - **Fix:** Replace with `ConcurrentHashMap.newKeySet()` or move all mutations onto the same dispatcher.
  - **Verification:** Stress test volume classification toggling under concurrent volume change events.

- [ ] **Protect against duplicate event consumption in `ClipboardDelegate`** `[Low]`
  - **Location:** `ClipboardDelegate.kt`
  - **Problem:** `ClipboardDelegate` subscribes to `bulkFileOperationCoordinator.events` independently from `BrowserViewModel`, leading to duplicate handling of the same events. Both the delegate (lines 33–56) and the ViewModel `init` block (lines 276–347) react to `Started`, `Progress`, `Completed`, etc.
  - **Impact:** Redundant state updates, potential brief UI flickering as `isLoading` is toggled by two independent collectors.
  - **Fix:** Consolidate event handling to a single subscriber; let either the ViewModel or the delegate own the event processing, not both.
  - **Verification:** A single paste operation triggers exactly one `isLoading = false` + `clipboardState = null` transition.

- [ ] **Route all deletion flows through `BulkFileOperationCoordinator`** `[High]`
  - **Location:** `BulkFileOperationModels.kt`, `BulkFileOperationService.kt`, `DeleteFlowDelegate.kt`, `FileRepository.kt`
  - **Problem:** Trashing and permanent deletions currently bypass the foreground service and progress UI, leading to inconsistent feedback and risk of operation death if the app is backgrounded.
  - **Impact:** No progress FAB for deletes, no completion snackbars, and potential partial deletion if the process is killed during a large batch.
  - **Fix:** Expand `BulkFileOperationType` to include `TRASH` and `DELETE`, route them through the background service, and update `DeleteFlowDelegate` to request these operations.
  - **Verification:** Deleting/Trashing items triggers the progress FAB and a completion snackbar once finished.

### 🔐 Security & Privacy
- [ ] **Log production-relevant errors in release builds** `[Medium]`
  - **Location:** `AppLogger.kt`
  - **Problem:** `AppLogger` is gated by `BuildConfig.DEBUG`, meaning all `e()` and `w()` calls are silently swallowed in production. This includes critical crypto failures in `TrashCryptoHelper` and I/O errors in destructive operations.
  - **Impact:** No error telemetry in production; crash-less failures (e.g., encrypted metadata corruption) are invisible.
  - **Fix:** Allow `Log.e` calls through in release builds, or integrate a crash-reporting backend (e.g., Firebase Crashlytics) for non-fatal errors. Keep `Log.w` debug-only if preferred.
  - **Verification:** A simulated crypto failure in release build produces a Logcat or crash-report entry.

- [ ] **Harden `TrashCryptoHelper` retry logic** `[Low]`
  - **Location:** `TrashManager.kt:TrashCryptoHelper`
  - **Problem:** The encrypt/decrypt retry loops retry 3 times on *any* exception. For non-transient errors (e.g., corrupted KeyStore, wrong key), retries are pointless and add latency. The retry loop also does not distinguish between transient and fatal errors.
  - **Impact:** Up to 3× slower failure path for deterministic errors; logs don't indicate which attempt failed.
  - **Fix:** Only retry on transient/platform errors; fail fast on `InvalidKeyException`, `BadPaddingException`, etc.
  - **Verification:** Corrupted KeyStore causes an immediate failure rather than a 3-retry delay.

### 🎨 UI & Rendering
- [ ] **Polished Snackbar aesthetics for Material 3 Expressive** `[Medium]`
  - **Location:** `BrowserScreen.kt`, `ArcileAppShell.kt`
  - **Problem:** The current snackbars use default styling which feels detached from the app's custom "squircle" and tonal container aesthetic.
  - **Impact:** Visual inconsistency between file operation feedback and the rest of the polished M3 Expressive UI.
  - **Fix:** Customize the `SnackbarHost` to use `MaterialTheme.shapes.extraLarge` (squircle), apply tonal container colors, and ensure font-weight/spacing matches the app's design language.
  - **Verification:** Operation feedback snackbars appear with rounded squircle shapes and cohesive theme-aware coloring.

- [ ] **Replace unstable Lazy list/grid keys with stable identifiers** `[Medium]`
  - **Location:** `FileList.kt`, `FileGrid.kt`, `BrowserScreen.kt`, `HomeScreen.kt`, `RecentFilesScreen.kt`
  - **Problem:** Lists key items by `absolutePath + index` or `absolutePath + hashCode`, breaking on sort/refresh. Evidence:
    - `FileList.kt:83` — `"${files[index].absolutePath}_$index"`
    - `FileGrid.kt:86` — `"${files[index].absolutePath}_$index"`
    - `HomeScreen.kt:551` — `"${it.absolutePath}_${it.hashCode()}"`
    - `RecentFilesScreen.kt:287` — `"${it.absolutePath}_${it.hashCode()}"`
    - `BrowserScreen.kt:619` — `"${it.absolutePath}_${it.hashCode()}"`
  - **Impact:** Loss of item identity causes list churn, brittle animations, and scroll-state jumps.
  - **Fix:** Use stable keys like `absolutePath` for files and explicit IDs for trash items. The `absolutePath` alone is unique within a directory listing.
  - **Verification:** Profiling recomposition during list sorting/refreshing confirms item identity remains stable.

- [ ] **Add `contentType` to `LazyList`/`LazyGrid` items** `[Low]`
  - **Location:** `FileList.kt`, `FileGrid.kt`, `HomeScreen.kt`, `RecentFilesScreen.kt`, `TrashList.kt`
  - **Problem:** `items()` calls do not specify `contentType`, preventing Compose from reusing item composable slots efficiently.
  - **Impact:** Additional recomposition and allocation when scrolling heterogeneous lists (e.g., files vs. headers).
  - **Fix:** Add `contentType = { /* type discriminator */ }` to `items()` calls where item types differ.
  - **Verification:** Recomposition counts remain stable during fast scrolling in Layout Inspector.

### 🏗️ Architecture
- [ ] **Eliminate `pathWithAncestors` duplication** `[Low]`
  - **Location:** `TrashManager.kt:217–242`, `FileSystemDataSource.kt:83–113`
  - **Problem:** Identical `pathWithAncestors()` implementations exist in both files, duplicating ~30 lines of non-trivial path traversal logic.
  - **Impact:** Bug fixes must be applied in two places; inconsistent behavior possible if one copy drifts.
  - **Fix:** Extract to a shared utility function (e.g., in a `data/util/PathUtils.kt` or existing `data/util` package).
  - **Verification:** Both consumers delegate to the shared function; existing tests pass.

- [ ] **Reduce `FileSystemDataSource` surface area** `[Low]`
  - **Location:** `FileSystemDataSource.kt` (676 lines)
  - **Problem:** Handles directory listing, path validation, cancellable copy, cancellable move, rename, create, permanent delete, conflict detection, and selection properties. This is a "god object" accumulating unrelated responsibilities.
  - **Impact:** High cognitive load, difficult to test in isolation, merge conflicts as features grow.
  - **Fix:** Extract copy/move logic into a dedicated `FileTransferEngine`, keep listing/create/delete in the data source. Extract properties/conflict detection to helpers.
  - **Verification:** No behavioral change; existing tests pass against the refactored classes.

---

## Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.
- **Storage Health Diagnostics**: Basic S.M.A.R.T. status checks, disk health metrics, and repair/trim suggestions for mounted volumes.

### File Operations & Automation
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser.
- **Automated Task Rules**: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` where a main action regularly needs a secondary dropdown or overflow action.
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press, successful file operations, and error states.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally scrollable carousel of the 10 most recently modified images and videos.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations such as Lottie for empty folders, trash, and search results.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Browsing & Organization
- **Rich Media Previews**: Implement custom Coil `Fetcher` components to extract APK icons and PDF thumbnails.
- [ ] PDF thumbnails pending
- **Starred / Favorited Files**: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Enhanced Category Browsing**: When opening a file category, display all related folders containing matching files with tabbed or segmented navigation.
- **Storage Analyzer ("Filelight" view)**: A dedicated radial map or sunburst chart to visualize storage usage by folder and file type.

### Tools & Development
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations for auditing purposes.
- **Dummy File Generator**: A developer/testing tool to quickly create fake files of a specified size to fill space or test transfers.
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.

### Settings & About
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive flower petal UI.

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
