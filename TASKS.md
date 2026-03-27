# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.5.3
> **Last Updated:** 2026-03-27

---

### Security & Privacy

- [x] [Severity: Critical] [Category: Security] **FileProvider paths are incomplete — opening/sharing most files crashes**
  - Location: `res/xml/file_provider_paths.xml`, `MainActivity.kt` line 110, `ShareHelper.kt` line 23
  - Problem: `file_provider_paths.xml` only declares 11 specific `<external-path>` entries (Downloads, Documents, Pictures, etc.). Any file outside these directories throws `IllegalArgumentException` when `FileProvider.getUriForFile()` is called — crashing share and silently failing open.
  - Impact: Opening or sharing any file outside the 11 hardcoded directories fails. For a file manager, this affects the majority of user files.
  - Fix: Add `<external-path name="external_root" path="." />` and `<root-path name="all_files" path="." />` to cover all external storage and mounted volumes.
  - Verification: Open and share a file from the root of internal storage or from an SD card — should succeed without crash.

- [x] [Severity: High] [Category: Security] **`.gitignore` has corrupted UTF-16 bytes — `signing.properties` may not be ignored**
  - Location: `.gitignore` line 62-63, `arcile-app/.gitignore` line 16-17
  - Problem: Both `.gitignore` files have the `signing.properties` pattern encoded in UTF-16LE with null bytes, which git cannot interpret. If `signing.properties` is created, it would be tracked and potentially committed with keystore passwords.
  - Impact: Risk of signing credential exposure if `signing.properties` is ever created.
  - Fix: Replace the corrupted lines with proper ASCII: `signing.properties`.
  - Verification: `echo "test" > signing.properties && git status` should show the file as ignored.

- [x] [Severity: Medium] [Category: Security] **Log statements in release builds expose internal paths and error details**
  - Location: `TrashManager.kt` (19 log calls), `MediaStoreClient.kt` (7 log calls), `StorageClassificationRepository.kt` (2 log calls), `ShareHelper.kt`, `MainActivity.kt`
  - Problem: ~30 `android.util.Log.e/w` calls exist throughout the data layer, including file paths, volume names, trash IDs, and crypto state. These are present in release builds.
  - Impact: Any user with logcat access can see internal file paths and operational details.
  - Fix: Wrap all log calls in `if (BuildConfig.DEBUG)` guards, or use a logging abstraction that strips in release.
  - Verification: Build release APK, reproduce a trash operation, and verify no sensitive output in logcat.

### Correctness & Reliability

- [x] [Severity: Medium] [Category: Correctness] **`TrashManager.restoreFromTrash` doesn't verify copy success before deleting source on cross-volume fallback**
  - Location: `data/manager/TrashManager.kt` lines 398-407
  - Problem: In the fallback path (when `renameTo` fails), `copyRecursively` then `deleteRecursively` are called, but the delete return value is not checked and the copy is not verified before deletion.
  - Impact: Potential for duplicate files or data left in trash after a failed cross-volume restore.
  - Fix: Check `deleteRecursively()` return value and verify target file existence/size before deleting source.
  - Verification: Force a cross-filesystem restore and simulate a delete failure.

- [x] [Severity: Medium] [Category: Correctness] **`VolumeProvider.discoverPlatformVolumes` caches volumes permanently — `StatFs` values become stale**
  - Location: `data/provider/VolumeProvider.kt` lines 50-52
  - Problem: `discoverPlatformVolumes()` returns cached data if non-null. The cache is only cleared on media mount/unmount broadcasts, but never after file operations. Free space reporting becomes increasingly inaccurate.
  - Impact: Storage dashboard shows stale free space. After delete/copy operations, storage info does not refresh.
  - Fix: Invalidate the cache after file operations, or add a short TTL, or recalculate `StatFs` values independently.
  - Verification: Delete a large file, navigate to home dashboard, and verify free space updates.

### Performance & Efficiency

- [x] [Severity: Medium] [Category: Performance] **Limit `detectCopyConflicts` recursive directory walk**
  - Location: `data/source/FileSystemDataSource.kt` lines 258-274
  - Problem: For conflicting directories, walks the entire source tree checking each descendant file against destination, with no depth or count limit.
  - Impact: Long UI freeze when pasting large directories.
  - Fix: Show a summary conflict dialog for directories instead of enumerating every descendant.
  - Verification: Paste a 1000+ file directory and measure the delay before conflict dialog.

### Architecture & Code Health

- [x] [Severity: Medium] [Category: Architecture] **Wire existing `DeleteFlowDelegate` into ViewModels — it exists but is unused**
  - Location: `presentation/delegate/DeleteFlowDelegate.kt` (unused), `BrowserViewModel.kt` (lines 226-309), `RecentFilesViewModel.kt` (lines 148-235)
  - Problem: `DeleteFlowDelegate` was created to consolidate delete flow logic, but is never instantiated or referenced. Delete logic remains duplicated across 2 ViewModels (~80 lines each).
  - Impact: Bug fixes must be applied in 2 places; `DeleteFlowDelegate` is dead code.
  - Fix: Wire `DeleteFlowDelegate` into both ViewModels, or delete the unused delegate.
  - Verification: Change delete behavior in one place and confirm all screens adopt it.

### Testing

- [ ] [Severity: High] [Category: Testing] **Add integration tests for critical file operation flows**
  - Location: Missing: `FileSystemDataSourceTest`, `TrashManagerTest`, `LocalFileRepositoryTest`
  - Problem: The data layer (all destructive file operations) has zero test coverage.
  - Impact: Regressions in copy/move/delete/trash are undetectable without manual testing.
  - Fix: Write Robolectric or instrumented tests for `FileSystemDataSource` operations using temp directories.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with new tests.

- [x] [Severity: Medium] [Category: Testing] **Fix `LocalFileOperationsTest` to test actual production code, not a local copy**
  - Location: `test/data/LocalFileOperationsTest.kt` lines 57-72
  - Problem: Tests a `getUniqueFileName` helper copied into the test file, not the real `FileSystemDataSource` implementation. Changes to production code won't be caught.
  - Fix: Import and test the actual `getUniqueFileName` from `FileSystemDataSource` (or its extracted utility).
  - Verification: Intentionally break the production rename logic and confirm the test fails.

- [ ] [Severity: Medium] [Category: Testing] **Consolidate 6 duplicated `FakeFileRepository` implementations into a shared test double**
  - Location: `DeletePolicyTest`, `StorageScopeViewModelTest`, `BrowserViewModelTest`, `HomeViewModelTest`, `RecentFilesViewModelTest`, `TrashViewModelTest`
  - Problem: Each test file has its own `FakeFileRepository` with ~30 duplicate stub methods. Bug-fix in the interface requires updating all 6.
  - Fix: Create a shared `testutil/FakeFileRepository.kt` with configurable result overrides.
  - Verification: All existing tests pass against the shared fake.

- [ ] [Severity: Medium] [Category: Testing] **Add `MediaStoreClient` test coverage for category size queries**
  - Location: Missing: `MediaStoreClientTest`
  - Problem: 618 lines of MediaStore SQL query construction, cache management, and category size calculation have zero test coverage. The `requiresFullScan` performance bug cannot be regression-tested.
  - Fix: Write Robolectric-backed tests using `ContentResolver` shadows for query verification.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with new `MediaStoreClientTest`.

- [ ] [Severity: Medium] [Category: Testing] **Add repository and helper coverage for Android-dependent storage utilities**
  - Location: Missing: `BrowserPreferencesRepositoryTest`, `StorageClassificationRepositoryTest`, `ShareHelperTest`
  - Problem: DataStore-backed preferences, classification parsing, and share intent/file-provider behavior still rely mostly on manual verification.
  - Impact: Regressions in persisted browser settings, corrupted classification recovery, or share launch behavior can slip through local refactors.
  - Fix: Add Robolectric/JVM tests covering DataStore reads/writes, parse-failure cleanup, and `ShareHelper` success/failure cases.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with the new repository/helper tests.

- [ ] [Severity: Medium] [Category: Testing] **Add fetcher coverage for rich media preview components**
  - Location: Missing: `ApkIconFetcherTest`, `AudioAlbumArtFetcherTest`
  - Problem: Custom Coil fetchers for APK icons and album art have no automated coverage.
  - Impact: Preview regressions can break silently and only surface on-device when browsing media-heavy folders.
  - Fix: Add Robolectric-backed tests for fetcher factory matching and decode/load behavior with mocked package manager and media metadata inputs.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with the new fetcher tests.

- [ ] [Severity: Medium] [Category: Testing] **Add navigation and saved-state restore coverage for type-safe routes**
  - Location: Missing: `AppRoutesTest`, `AppNavigationGraphTest`, additional saved-state tests for `BrowserViewModel`
  - Problem: Route serialization, argument parsing, and browser state restoration are only partially covered.
  - Impact: Navigation regressions and process-death restore bugs can escape into release builds.
  - Fix: Add tests for route encoding/decoding, key navigation transitions, and saved-state restoration branches.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with route and restore coverage.

- [ ] [Severity: Medium] [Category: Testing] **Add screen-level Compose interaction coverage for primary flows**
  - Location: Missing: `BrowserScreenTest`, expanded coverage for `HomeScreen`, `RecentFilesScreen`, `SettingsScreen`, `StorageDashboardScreen`, `StorageManagementScreen`, `ToolsScreen`, `TrashScreen`, `ArcileAppShell`
  - Problem: Most full-screen Compose flows still lack automated UI interaction coverage.
  - Impact: Search/back handling, snackbars, dialogs, pull-to-refresh, and screen wiring can regress without fast feedback.
  - Fix: Add Robolectric Compose tests for the highest-risk screens first, especially browser and navigation flows.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with the new screen-level tests.

- [ ] [Severity: Low] [Category: Testing] **Add Turbine for direct Flow emission assertions where state timing matters**
  - Location: `arcile-app/app/build.gradle.kts`, ViewModel and coordinator test suites
  - Problem: Flow-heavy behavior is currently tested mostly through final state snapshots rather than direct emission assertions.
  - Impact: Timing-sensitive regressions in one-shot events and multi-emission flows are harder to catch precisely.
  - Fix: Add `app.cash.turbine:turbine` to the test dependencies and use it for selected Flow/event tests.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with Turbine-backed flow assertions.


### Architecture & Background Processing
- [x] **Bulletproof Background Execution**: In `ClipboardDelegate.kt` (and `FileSystemDataSource.kt`), migrate bulk file operations (`copyFiles`, `moveFiles`) to `WorkManager` or a standard Foreground Service. Current `viewModelScope` execution will silently abort if the app is backgrounded and killed by the OS.

### Build / Release / Configuration

- [x] [Severity: High] [Category: Build] **ProGuard rules may not preserve Navigation-Compose route serialization**
  - Location: `proguard-rules.pro`
  - Problem: The rules only handle generic `kotlinx.serialization` keeps and Coil fetchers. `AppRoutes.*` `@Serializable` data classes and `TrashMetadataEntity` may have fields renamed/stripped by R8, breaking route deserialization and trash metadata persistence in release builds.
  - Impact: Potential release-only crash when navigating to Explorer with arguments, or when reading/writing trash metadata.
  - Fix: Verify via release APK testing. If routes break, add `-keep class dev.qtremors.arcile.navigation.AppRoutes** { *; }` and `-keep class dev.qtremors.arcile.data.manager.TrashMetadataEntity { *; }`.
  - Verification: Build release APK, navigate to all routes with arguments, perform trash and restore operations.

- [x] [Severity: Medium] [Category: Build] **`HomeViewModel.loadHomeData` launches unbounded concurrent coroutines with no timeout**
  - Location: `presentation/home/HomeViewModel.kt` lines 120-177
  - Problem: Launches 4+ `async` calls (recent, volumes, storage, categories) plus N per-volume category fetches, with no timeout. If any MediaStore query hangs, the home screen loading spinner shows indefinitely.
  - Impact: Home screen could block indefinitely on devices with slow or problematic MediaStore.
  - Fix: Add `withTimeout(15_000)` around the `coroutineScope` block and fail gracefully with partial data.
  - Verification: Simulate a slow ContentResolver query and verify the home screen eventually shows partial data or an error.

- [x] [Severity: Medium] [Category: Build] **README version badge shows `0.5.0` while `build.gradle.kts` and `TASKS.md` show `0.5.2`**
  - Location: `README.md` line 17
  - Fix: Update the README badge to `0.5.2`.
  - Verification: `grep -r "0.5.0" .` returns no stale references.

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
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` in areas where a main action consistently needs a secondary dropdown/overflow action (e.g., creating files vs folders, or sorting).
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press (selection mode), successful file operations, and error states to make the app feel alive and premium.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally-scrollable carousel of the 10 most recently modified images and videos.
- **Customizable Quick Access**: Allow users to hardcode, add, remove, and restore pinned folders to the Quick Access section on the Home screen.
- **Header Logo Integration**: Add a subtle, branded Arcile logo into the `ArcileTopBar` for stronger visual identity.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations (e.g., Lottie) for empty folders, trash, and search results.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes (e.g., heavily rounded "squircle" vs. standard rounded corners for cards and buttons).

### Browsing & Organization
- **Rich Media Previews**: Implement custom Coil `Fetcher` components to extract APK icons (using `PackageManager`) and PDF thumbnails.
  - [x] APK icons implemented (`ApkIconFetcher.kt`)
  - [ ] PDF thumbnails pending
  - Goal: Making the grid view visually rich regardless of file type.
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
