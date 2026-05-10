# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.6.4
> **Last Updated:** 2026-05-10

---

## 🏗️ Architecture

- [ ] **Reduce `FileSystemDataSource` surface area** `[Low]`
  - **Location:** `FileSystemDataSource.kt` (676 lines)
  - **Problem:** Handles directory listing, path validation, cancellable copy, cancellable move, rename, create, permanent delete, conflict detection, and progress reporting. This is a "god object" accumulating unrelated responsibilities.
  - **Impact:** High cognitive load, difficult to test in isolation, merge conflicts as features grow.
  - **Fix:** Extract copy/move logic into a dedicated `FileTransferEngine`. Keep listing/create/delete in the data source. Extract conflict detection to a helper.
  - **Verification:** No behavioral change; existing tests pass against the refactored classes.

- [ ] **Reduce `finalizeMutation` duplication** `[Low]`
  - **Location:** `TrashManager.kt:210–215`, `FileSystemDataSource.kt:81–86`
  - **Problem:** `finalizeMutation()` is implemented identically in both `TrashManager` and `FileSystemDataSource`, calling `mediaStoreClient.invalidateCache()`, `volumeProvider.invalidateCache()`, `folderStatsStore.invalidate()`, and `scanMediaFiles()` with the same logic.
  - **Impact:** Identical to `pathWithAncestors` duplication — drift risk and double maintenance burden.
  - **Fix:** Extract to a shared `MutationFinalizer` utility or inject it as a dependency into both classes.
  - **Verification:** All mutation paths still trigger media scan and cache invalidation.

- [ ] **`RecentFilesViewModel` and `TrashViewModel` duplicate local search/debounce pattern** `[Low]`
  - **Location:** `RecentFilesViewModel.kt:252–272`, `TrashViewModel.kt:215–236`
  - **Problem:** Both ViewModels implement an identical pattern: `searchJob` field, `updateSearchQuery()` method, `debouncedSearch()` with 300ms delay, in-memory list filter. This is the same boilerplate duplicated across two files.
  - **Impact:** Any behavior change (e.g., changing debounce duration, adding match highlighting) must be applied in both places.
  - **Fix:** Extract a reusable `LocalSearchHelper` or delegate that encapsulates the debounce + filter pattern.
  - **Verification:** Both ViewModels delegate to the shared helper; search behavior is unchanged.

---

## 🛠️ Build & CI

- [ ] **Add regression coverage and CI gates for recent escapes** `[Medium]`
  - **Location:** `app/src/test`, `app/src/androidTest`, `app/build.gradle.kts`
  - **Problem:** Current CI/suite lacks release assembly/lint gating, bulk-operation lifecycle edge cases, and cross-midnight grouping tests.
  - **Impact:** Regressions like the backup XML error and service-orchestration edge cases can survive until manual validation.
  - **Fix:** Add CI tasks for `:app:assembleRelease` and lint; add targeted tests for bulk operations and date boundaries.
  - **Verification:** CI fails correctly when those regressions are reintroduced.

---

## ⚡ Performance

- [ ] **`MediaStoreClient.getCategoryStorageSizes` performs unbounded cursor scan for categories needing calculation** `[Medium]`
  - **Location:** `MediaStoreClient.kt:369–397`
  - **Problem:** When `StorageStatsManager` is unavailable or the scope is `AllStorage`, the query fetches every file matching any extension across all categories and iterates the full cursor in-memory. The `selection` string is built from all categories needing calculation but the cursor has no `LIMIT`, so on a device with a large media library this can process hundreds of thousands of rows.
  - **Impact:** Potential ANR or heavy CPU usage during Home screen initial load on large libraries.
  - **Fix:** Consider batching category queries or adding a reasonable limit with incremental progress, or cache more aggressively.
  - **Verification:** Home screen load time on a device with >50k media files stays under 3 seconds.

- [ ] **`ExternalFileAccessHelper.stageFile` copies the entire file synchronously before returning** `[Low]`
  - **Location:** `ExternalFileAccessHelper.kt:94–110`
  - **Problem:** Opening or sharing a file copies its entire contents into the cache staging area on the calling thread. For large files (e.g., 2GB videos), this blocks the UI thread or the calling coroutine for an extended period with no progress indication.
  - **Impact:** UI freeze or ANR when opening/sharing large files.
  - **Fix:** Perform the copy on `Dispatchers.IO` and show progress, or use streaming/deferred access if possible.
  - **Verification:** Sharing a 500MB file does not cause an ANR.

- [ ] **`appendVolumeSelection` generates O(2×volumes) bind parameters per query** `[Low]`
  - **Location:** `MediaStoreClient.kt:142–159`
  - **Problem:** Each volume adds 2 bind parameters (`= ?` and `LIKE ?`) to the selection. With many mounted volumes, the resulting SQL query grows linearly. This is unlikely to hit SQLite's 999-parameter limit in practice but is inefficient for multi-volume devices.
  - **Impact:** Minor query overhead with many volumes. No crash expected.
  - **Fix:** Consider using `IN` clauses or reducing to a single `LIKE` per volume.
  - **Verification:** Query works correctly with 10+ mounted volumes.

---

## 🧪 Testing

- [ ] **Add `ClipboardDelegate` unit tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/browser/delegate/`
  - **Problem:** `ClipboardDelegate` has no dedicated tests. Its event handling, conflict resolution flow, and interaction with `BulkFileOperationCoordinator` are untested.
  - **Impact:** The duplicate event consumption bug (see above) was not caught by tests.
  - **Fix:** Create `ClipboardDelegateTest.kt` with tests for: paste flow, conflict detection, resolution, cancel, coordinator event handling, and edge cases (empty clipboard, already-running operation).
  - **Verification:** Test suite passes; duplicate event handling is detected if reintroduced.

- [ ] **Add `NavigationDelegate` unit tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/browser/delegate/`
  - **Problem:** `NavigationDelegate` manages browser navigation state, path history, volume resolution, and saved state restoration — all untested.
  - **Impact:** Navigation bugs (e.g., history corruption, volume resolution failures on hot-plug) are not caught.
  - **Fix:** Create `NavigationDelegateTest.kt` covering: folder navigation, back navigation, history stack, volume root fallback, saved state round-trip, category navigation, and refresh behavior.
  - **Verification:** Test suite passes; back-navigation edge cases are covered.

- [ ] **Add `DeleteFlowDelegate` unit tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/delegate/`
  - **Problem:** `DeleteFlowDelegate` orchestrates the complex delete policy evaluation, trash/permanent delete branching, and native confirmation flow — all untested directly (only indirectly via `BrowserViewModelTest`).
  - **Impact:** Delete flow regressions (e.g., wrong dialog shown for mixed selections) may not be caught.
  - **Fix:** Create `DeleteFlowDelegateTest.kt` with tests for: trash confirmation, permanent delete confirmation, mixed selection explanation, native confirmation flow, and empty selection guard.
  - **Verification:** All delete flow branches are exercised.

- [ ] **Add integration tests for `TrashManager` crypto round-trip** `[High]`
  - **Location:** `app/src/test/.../data/manager/TrashManagerTest.kt`
  - **Problem:** Existing `TrashManagerTest` may not fully cover the encrypt/decrypt/fallback round-trip, especially the scenario where KeyStore is unavailable and the fallback key is used.
  - **Impact:** The dual-key ambiguity bug (see Security section) would not be caught.
  - **Fix:** Add test cases for: (a) KeyStore-based round-trip, (b) PBKDF2 fallback round-trip, (c) metadata encrypted with fallback should still decrypt after KeyStore becomes available, (d) corrupted metadata should be handled gracefully.
  - **Verification:** All crypto paths produce correct round-trip results.

- [ ] **Add `BulkFileOperationService` lifecycle tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/operations/`
  - **Problem:** The foreground service orchestration (start, cancel, completion, failure, process death) has no test coverage. The identified race conditions (stuck active request, stale cancel, dropped terminal events) are untested.
  - **Fix:** Create `BulkFileOperationCoordinatorTest.kt` and `BulkFileOperationServiceTest.kt` covering: start/complete lifecycle, cancel mid-operation, duplicate start rejection, terminal event delivery guarantee, and coordinator state cleanup.
  - **Verification:** All identified orchestration edge cases are exercised.

- [ ] **Add `HomeViewModel.loadHomeData` timeout and partial-data tests** `[Low]`
  - **Location:** `app/src/test/.../presentation/home/`
  - **Problem:** The 15-second timeout with partial category data (see Correctness section) has no test coverage. The `supervisorScope` + nested `async` + `withTimeoutOrNull` composition is complex and untested.
  - **Fix:** Create tests simulating slow volume responses and verifying partial vs. complete data indicators.
  - **Verification:** Timeout scenario produces the expected partial-data error message.

- [ ] **Add `SearchDelegate` unit tests** `[Low]`
  - **Location:** `app/src/test/.../presentation/browser/delegate/`
  - **Problem:** `SearchDelegate` (84 lines) has no dedicated unit tests. Its debounce, scope resolution, and clear behavior are untested in isolation.
  - **Fix:** Create `SearchDelegateTest.kt` covering: debounced query, scope-based search dispatch, clear search, and empty query guard.
  - **Verification:** Tests pass; search delegate behavior matches BrowserViewModel integration behavior.

---

## 📝 Correctness & Reliability

- [ ] **`TrashViewModel` does not observe `BulkFileOperationCoordinator` events** `[Low]`
  - **Location:** `TrashViewModel.kt`
  - **Problem:** `TrashViewModel.deletePermanentlySelected()` calls `repository.deletePermanentlyFromTrash()` directly (not via the coordinator). This is consistent and correct. However, if `BulkFileOperationService` is used for trash/delete in the future, the TrashVM would need coordinator event observation similar to `BrowserViewModel`.
  - **Impact:** Currently none — this is a maintainability observation. The direct repository call is correct for now.
  - **Fix:** No immediate action required. Document the architectural decision.
  - **Verification:** N/A

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
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.

### Settings & About
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive flower petal UI.

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
