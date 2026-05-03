# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.6.2
> **Last Updated:** 2026-05-03

---

### 🎨 UI & Rendering

- [ ] **Polished Snackbar aesthetics for Material 3 Expressive** `[Medium]`
  - **Location:** `BrowserScreen.kt`, `ArcileAppShell.kt`
  - **Problem:** The current snackbars use default styling which feels detached from the app's custom "squircle" and tonal container aesthetic.
  - **Impact:** Visual inconsistency between file operation feedback and the rest of the polished M3 Expressive UI.
  - **Fix:** Customize the `SnackbarHost` to use `MaterialTheme.shapes.extraLarge` (squircle), apply tonal container colors, and ensure font-weight/spacing matches the app's design language.
  - **Verification:** Operation feedback snackbars appear with rounded squircle shapes and cohesive theme-aware coloring.

- [ ] **Client-side sorting in `BrowserScreen` recomputes on every state update** `[Low]`
  - **Location:** `BrowserScreen.kt` (sort logic applied via `LaunchedEffect` on lines ~297–318)
  - **Problem:** File sorting and search filtering are performed inside `LaunchedEffect` blocks keyed on `state.browserSortOption`, `state.currentPath`, and `state.browserSearchQuery`. These produce new `List` instances that trigger recomposition of the entire file list even when the actual content hasn't changed.
  - **Impact:** Redundant list re-creation and recomposition when unrelated state fields update.
  - **Fix:** Move sort/filter logic into a `remember`/`derivedStateOf` block or perform sorting in the ViewModel before emitting to the state flow.
  - **Verification:** Changing an unrelated state field (e.g., `selectedFiles`) does not re-trigger file list sorting.

### 🏗️ Architecture

- [ ] **Eliminate `pathWithAncestors` duplication** `[Low]`
  - **Location:** `TrashManager.kt:217–242`, `FileSystemDataSource.kt:83–113`
  - **Problem:** Identical `pathWithAncestors()` implementations exist in both files, duplicating ~30 lines of non-trivial path traversal logic.
  - **Impact:** Bug fixes must be applied in two places; inconsistent behavior possible if one copy drifts.
  - **Fix:** Extract to a shared utility function (e.g., `data/util/PathUtils.kt`).
  - **Verification:** Both consumers delegate to the shared function; existing tests pass.

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

- [ ] **`validatePath` is duplicated across `TrashManager` and `FileSystemDataSource`** `[Low]`
  - **Location:** `TrashManager.kt:244–253`, `FileSystemDataSource.kt:39–49`
  - **Problem:** Identical `validatePath()` implementations exist in both classes, checking canonical paths against `volumeProvider.activeStorageRoots`. This is a third instance of cross-class duplication (alongside `pathWithAncestors` and `finalizeMutation`).
  - **Impact:** Security-sensitive path validation logic must be maintained in three places. A fix to one may not propagate.
  - **Fix:** Extract to a shared `PathValidator` utility injected into both classes.
  - **Verification:** Both classes delegate to the shared validator; existing tests pass.

### 🛠️ Build & CI

- [ ] **Add regression coverage and CI gates for recent escapes** `[Medium]`
  - **Location:** `app/src/test`, `app/src/androidTest`, `app/build.gradle.kts`
  - **Problem:** Current CI/suite lacks release assembly/lint gating, bulk-operation lifecycle edge cases, and cross-midnight grouping tests.
  - **Impact:** Regressions like the backup XML error and service-orchestration edge cases can survive until manual validation.
  - **Fix:** Add CI tasks for `:app:assembleRelease` and lint; add targeted tests for bulk operations and date boundaries.
  - **Verification:** CI fails correctly when those regressions are reintroduced.

- [ ] **Ensure ProGuard rules cover all serialized models** `[Medium]`
  - **Location:** `proguard-rules.pro`
  - **Problem:** `proguard-rules.pro` explicitly keeps `TrashMetadataEntity` (line 57) but does not keep `BulkFileOperationRequest`, `BulkFileOperationProgress`, `CategoryCacheEntity`, `CacheRootEntity`, `FolderStatsCacheEntity`, or other `@Serializable` data classes that are serialized to disk or Intent extras. The generic `kotlinx.serialization` keep rules (lines 27–52) rely on companion-object-based serializer resolution, which may break if the class itself is renamed/obfuscated.
  - **Impact:** Release builds may crash on deserialization of serialized models (e.g., resuming a bulk operation after process death, or reading analytics cache).
  - **Fix:** Add explicit `-keep` rules for all `@Serializable` data classes that are persisted to disk or passed via Intent extras. Alternatively, add a blanket rule for all `@Serializable` classes in the `dev.qtremors.arcile` package.
  - **Verification:** `assembleRelease` followed by a bulk copy/move and trash operation works correctly.

- [ ] **`BulkFileOperationService` notification uses launcher icon instead of monochrome** `[Low]`
  - **Location:** `BulkFileOperationService.kt:107`
  - **Problem:** `setSmallIcon(R.mipmap.ic_launcher)` uses the full-color launcher mipmap as the notification small icon. Android requires notification small icons to be monochrome (alpha-only) — the system will render a solid white/colored square on most devices.
  - **Impact:** Broken notification icon appearance during file operations.
  - **Verification:** Foreground service notification displays a recognizable monochrome icon.

### 🧪 Testing

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
