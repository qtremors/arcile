# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.5.4
> **Last Updated:** 2026-03-27

---

### Testing

- [ ] [Severity: High] [Category: Testing] **Add integration tests for critical file operation flows**
  - Location: Missing: `FileSystemDataSourceTest`, `TrashManagerTest`, `LocalFileRepositoryTest`
  - Problem: The data layer (all destructive file operations) has zero test coverage.
  - Impact: Regressions in copy/move/delete/trash are undetectable without manual testing.
  - Fix: Write Robolectric or instrumented tests for `FileSystemDataSource` operations using temp directories.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with new tests.

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

### Audit Remediation Backlog

- [x] [Severity: Medium] [Category: UX & Architecture] **Model `Android/data` and `Android/obb` Quick Access entries explicitly as native Files-app handoff shortcuts**
  - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/data/QuickAccessPreferencesRepository.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt`
  - Problem: The current `Android/data` and `Android/obb` shortcuts intentionally rely on Android Files / DocumentsUI handoff because Arcile cannot directly own access to those restricted folders, but they are currently mixed into the same shortcut model as regular Quick Access entries.
  - Impact: The implementation works as a practical non-root access path, but the product model can confuse future maintenance and user expectations about what Arcile can actually browse itself versus what opens externally.
  - Fix: Keep the current handoff approach, but represent these entries as a distinct external/native handoff type in the data model and UI, with copy that makes it clear they open in the Android Files app due to platform restrictions.
  - Verification: Completed in `0.5.4`. Native handoff entries now use an explicit external-handoff type, route through Files-app launch helpers, and display distinct explanatory copy in Quick Access.

- [x] [Severity: High] [Category: Reliability] **Make browser native-confirmation events one-shot and clear pending action state after handling**
  - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/BrowserViewModel.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt`
  - Problem: Browser uses `MutableSharedFlow<IntentSender>(replay = 1)` for native requests and never clears `pendingNativeAction`, so a new collector can replay an already-consumed `IntentSender`.
  - Impact: Configuration changes or collector reattachment can relaunch stale system confirmation flows and repeat destructive prompts unexpectedly.
  - Fix: Remove replay from the native-request flow, model confirmation requests as consumable one-shot events, and clear the pending action once the result is handled.
  - Verification: Completed in `0.5.4`. `BrowserViewModelTest` now covers one-shot delivery and pending-action clearing after handling.

- [x] [Severity: High] [Category: Correctness] **Write browser navigation state after target location is committed so process-death restore cannot reopen the previous folder**
  - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/delegate/NavigationDelegate.kt`
  - Problem: `loadDirectory()` and `loadCategory()` call `saveNavState()` before updating `currentPath`, `currentVolumeId`, and category flags, so `SavedStateHandle` can temporarily hold the previous screen while a new load is already in progress.
  - Impact: If the process dies during navigation or refresh, restore can land the user in the wrong folder/category and break the "return to exact location" contract documented in `DEVELOPMENT.md`.
  - Fix: Save the destination state after synchronously updating the in-memory state, and add explicit restore tests for directory navigation, category navigation, and in-flight refresh.
  - Verification: Completed in `0.5.4`. Navigation now persists committed destination state, and `BrowserViewModelTest` covers directory/category restore state persistence.

- [x] [Severity: High] [Category: Reliability] **Make bulk copy/move cancellation truthful and cooperative instead of only dismissing the foreground service**
  - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationCoordinator.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/FileSystemDataSource.kt`
  - Problem: `ACTION_CANCEL` stops the service and emits a cancelled event, but the underlying file operation is executed as a single blocking repository call with no cooperative cancellation checkpoints or progress reporting.
  - Impact: Users can be told an operation was cancelled while file I/O may still continue, leaving partial filesystem changes and unreliable UI state around long-running moves/copies.
  - Fix: Thread cancellation tokens or coroutine checkpoints through copy/move loops, surface real progress, and only emit cancellation after the worker has actually stopped and cleanup is complete.
  - Verification: Completed in `0.5.4` for the production pipeline. Copy/move now cooperate with coroutine cancellation and emit progress/cancelling/cancelled states; dedicated filesystem integration tests are still tracked separately under Testing.

- [x] [Severity: Medium] [Category: Security & Privacy] **Narrow `FileProvider` exposure to supported share/open roots instead of exposing the entire filesystem**
  - Location: `arcile-app/app/src/main/res/xml/file_provider_paths.xml`, `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ShareHelper.kt`
  - Problem: `file_provider_paths.xml` includes both `<external-path path=\".\"/>` and `<root-path path=\".\"/>`, while the open/share call sites only block `.arcile` and `cacheDir` by convention.
  - Impact: Any future bug or unexpected code path that passes an internal or non-user file into `FileProvider.getUriForFile()` can grant a third-party app read access far beyond the intended share surface.
  - Fix: Remove the global root mappings, replace them with the minimal set of external/app-specific roots the app truly needs, and centralize allowlist validation before generating URIs.
  - Verification: Completed in `0.5.4`. FileProvider now exposes only staged cache handoff roots, with centralized outbound allowlist checks in `ExternalFileAccessHelper`.

- [x] [Severity: Medium] [Category: Performance] **Profile and optimize path-scoped browser search so sparse queries do not walk entire directory trees on every keystroke**
  - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/MediaStoreClient.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/delegate/SearchDelegate.kt`
  - Problem: Path-scoped search falls back to `File.walkTopDown()` for every query, with no indexing, pruning beyond hidden folders, or cooperative cancellation during deep traversal.
  - Impact: Searching inside large trees can become slow, battery-heavy, and difficult to interrupt, especially when the first 1000 matches are sparse.
  - Fix: Profile the current traversal on large folders, add cancellation checkpoints and traversal limits, and consider a more incremental or indexed search strategy for path-scoped queries.
  - Verification: Completed in `0.5.4` for production behavior. Path-scoped search now uses bounded traversal with hidden-folder pruning and explicit cancellation checkpoints; deeper profiling/indexing remains future optimization work if needed.

- [x] [Severity: Medium] [Category: UX & Documentation] **Finish string-resource extraction for production UI so localization and accessibility stay consistent**
  - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/StorageManagementScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/TrashScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/ArcileTopBar.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/SearchFiltersBottomSheet.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/SortOptionDialog.kt`
  - Problem: Multiple production screens and shared components still hardcode visible labels, placeholders, and content descriptions instead of using `strings.xml`.
  - Impact: Localization coverage is incomplete, TalkBack phrasing becomes inconsistent, and future copy changes stay scattered across Kotlin files instead of one resource surface.
  - Fix: Extract the remaining hardcoded UI text into string resources and add a lightweight lint/checklist step to keep new production copy out of composables.
  - Verification: Completed in `0.5.4`. Remaining targeted production copy was extracted to `strings.xml`, and `:app:checkProductionStrings` now guards the audited composables.

- [ ] [Severity: Medium] [Category: Build & Release] **Finish toolchain cleanup by removing the remaining AGP/KSP workaround before the next upgrade**
  - Location: `arcile-app/gradle.properties`, `arcile-app/app/build.gradle.kts`
  - Problem: The internal `VariantOutputImpl` APK rename dependency has been removed, but the current AGP/KSP combination still requires the experimental `android.disallowKotlinSourceSets=false` compatibility flag to build.
  - Impact: Future Android Gradle Plugin or KSP upgrades can still break builds unexpectedly until the remaining workaround is retired.
  - Fix: Track the upstream KSP/AGP compatibility point where built-in Kotlin no longer requires the flag, then remove it and re-verify debug/release builds under the upgraded toolchain.
  - Verification: `./gradlew :app:compileDebugKotlin` and `./gradlew :app:compileDebugUnitTestKotlin` succeed without `android.disallowKotlinSourceSets=false`.


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
