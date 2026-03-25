# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.5.0
> **Last Updated:** 2026-03-24

---

### Security & Privacy

- [ ] [Severity: Critical] [Category: Security] **Remove committed keystore `.jks` from VCS and rotate signing key**
  - Location: `arcile-app/app/my-release-key.jks`
  - Problem: Release signing keystore (2068 bytes) is checked into the repository. Anyone with repo access can sign APKs as the official app.
  - Impact: Enables APK impersonation and supply-chain compromise.
  - Fix: Remove from repo and Git history (BFG/filter-repo), add `*.jks` to `.gitignore`, rotate the keystore.
  - Verification: Confirm `git log --all -- '*.jks'` returns nothing.

- [x] [Severity: High] [Category: Security] **Restrict FileProvider path scope from entire external storage**
  - Location: `app/src/main/res/xml/file_provider_paths.xml`
  - Problem: `<external-path name="external_files" path="."/>` grants URI generation capability for the entire external storage root.
  - Impact: Overly broad attack surface; Play Store policy risk.
  - Fix: Restrict to specific directories the app actually shares, or use scoped paths.
  - Verification: Attempt to generate a FileProvider URI for `.arcile/.trash/` and confirm rejection.

- [ ] [Severity: Medium] [Category: Security] **Remove unnecessary `READ_EXTERNAL_STORAGE` permission declaration**
  - Location: `AndroidManifest.xml` line 5
  - Problem: Declared without `maxSdkVersion` but unused since `minSdk=30` with `MANAGE_EXTERNAL_STORAGE`.
  - Fix: Remove the declaration or add `android:maxSdkVersion="29"`.
  - Verification: `aapt dump permissions` output no longer includes `READ_EXTERNAL_STORAGE`.

- [ ] [Severity: Medium] [Category: Security] **Configure backup/data extraction rules to exclude sensitive data**
  - Location: `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`
  - Problem: Default templates with no exclusion rules. Trash crypto salt in `SharedPreferences` could be extracted via cloud backup.
  - Fix: Add `<exclude>` rules for `trash_crypto_prefs`, DataStore files, and analytics cache.
  - Verification: Run `adb shell bmgr backupnow` and verify excluded files aren't captured.

### Correctness & Reliability

- [ ] [Severity: Medium] [Category: Correctness] **Add debounce/dedup to `RecentFilesViewModel` volume observation**
  - Location: `presentation/recentfiles/RecentFilesViewModel.kt` lines 80-84
  - Problem: Triggers `loadRecentFiles()` on every `observeStorageVolumes` emission with no debounce or diff check, unlike `HomeViewModel` which debounces by 1000ms.
  - Impact: Redundant MediaStore queries, flickering loading state.
  - Fix: Add `debounce(1000L)` and `distinctUntilChanged()` consistent with `HomeViewModel`.
  - Verification: Rapidly classify/reclassify a volume and observe Recent Files doesn't reload excessively.

### Performance & Efficiency

- [x] [Severity: High] [Category: Performance] **Fix `getCategoryStorageSizes` full MediaStore scan when `requiresFullScan` is true**
  - Location: `data/source/MediaStoreClient.kt` lines 252-386
  - Problem: Categories without MIME prefix (Documents) set `requiresFullScan=true`, which nullifies the SQL selection and scans every file in MediaStore. This is called per-volume on home screen load.
  - Impact: Multi-second blocking IO on devices with many files.
  - Fix: Always build a selection query using OR clauses for known extensions/MIME types, even for the "full scan" case.
  - Verification: Profile on a device with 50k+ MediaStore entries and measure query duration.

- [ ] [Severity: Medium] [Category: Performance] **Limit `detectCopyConflicts` recursive directory walk**
  - Location: `data/source/FileSystemDataSource.kt` lines 258-274
  - Problem: For conflicting directories, walks the entire source tree checking each descendant file against destination, with no depth or count limit.
  - Impact: Long UI freeze when pasting large directories.
  - Fix: Show a summary conflict dialog for directories instead of enumerating every descendant.
  - Verification: Paste a 1000+ file directory and measure the delay before conflict dialog.

### Architecture & Code Health

- [ ] [Severity: Medium] [Category: Architecture] **Extract duplicated delete-policy flow into a shared `DeleteFlowDelegate`**
  - Location: `BrowserViewModel.kt` (lines 226-309), `RecentFilesViewModel.kt` (lines 148-235)
  - Problem: Delete-policy evaluation, confirmation state, and execution logic is copy-pasted across ViewModels.
  - Impact: Bug fixes must be applied in 3 places; high regression risk.
  - Fix: Extract a `DeleteFlowDelegate` similar to existing `ClipboardDelegate`.
  - Verification: Change delete behavior in one place and confirm all screens adopt it.

### Build / Release / Configuration

- [x] [Severity: High] [Category: Build] **Update Compose BOM from `2024.09.00` and align dependency versions**
  - Location: `gradle/libs.versions.toml` lines 8-16
  - Problem: Compose BOM is ~18 months outdated. Material3 `1.4.0-alpha08` overrides the BOM. Lifecycle versions are split (`2.10.0` vs `2.8.5`).
  - Impact: Potential runtime incompatibilities, missing 18 months of bug fixes and security patches.
  - Fix: Update BOM to latest stable, align all lifecycle artifacts to same version, update navigation-compose.
  - Verification: Build and run; verify no `NoSuchMethodError` or runtime crashes.

- [x] [Severity: Medium] [Category: Build] **Replace overly broad ProGuard serialization rules with targeted ones**
  - Location: `app/proguard-rules.pro` lines 24-27
  - Problem: Keeps all classes/members in `kotlinx.serialization.**` including internals, preventing R8 shrinking.
  - Impact: Increased APK size by several hundred KB.
  - Fix: Use official kotlinx.serialization ProGuard rules.
  - Verification: Compare release APK size before/after; verify deserialization still works.

### Testing

- [ ] [Severity: High] [Category: Testing] **Add integration tests for critical file operation flows**
  - Location: Missing: `FileSystemDataSourceTest`, `TrashManagerTest`, `LocalFileRepositoryTest`
  - Problem: The data layer (all destructive file operations) has zero test coverage. Acknowledged in `PLAN.md`.
  - Impact: Regressions in copy/move/delete/trash are undetectable without manual testing.
  - Fix: Write Robolectric or instrumented tests for `FileSystemDataSource` operations using temp directories.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with new tests.

- [ ] [Severity: Medium] [Category: Testing] **Fix `LocalFileOperationsTest` to test actual production code, not a local copy**
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

- [ ] [Severity: Low] [Category: Testing] **Remove or replace `ExampleInstrumentedTest` and rename `NavigationTest`**
  - Location: `androidTest/ExampleInstrumentedTest.kt`, `androidTest/ui/NavigationTest.kt`
  - Problem: `ExampleInstrumentedTest` is the default template. `NavigationTest` tests `EmptyState` rendering, not navigation.
  - Fix: Delete the example test; rename `NavigationTest` to `EmptyStateInstrumentedTest`.
  - Verification: `./gradlew connectedAndroidTest` passes.

- [ ] [Severity: Low] [Category: Testing] **Standardize `IntentSender` mocking approach across tests**
  - Location: `BrowserViewModelTest` and `TrashViewModelTest` use `sun.misc.Unsafe`; `RecentFilesViewModelTest` uses `mockk()`
  - Problem: Inconsistent mocking; `sun.misc.Unsafe` is fragile and non-portable.
  - Fix: Use `mockk()` consistently across all tests or extract a shared `fakeIntentSender()` utility.
  - Verification: All tests pass on both JDK 11 and 17.

### Documentation

- [ ] [Severity: Medium] [Category: Docs] **Pin Tailwind CDN and Lucide versions in `docs/index.html`**
  - Location: `docs/index.html` lines 636-637
  - Problem: Uses `cdn.tailwindcss.com` (play CDN, not production) and `unpkg.com/lucide@latest` (unpinned). Both are fragile.
  - Impact: Site could break from upstream changes; Tailwind play CDN adds ~300KB of JS.
  - Fix: Build Tailwind via CLI/PostCSS or pin version. Pin Lucide to a specific version.
  - Verification: Site loads correctly with pinned versions; verify in browser DevTools.

### Architecture & Background Processing
- [ ] **Bulletproof Background Execution**: In `ClipboardDelegate.kt` (and `FileSystemDataSource.kt`), migrate bulk file operations (`copyFiles`, `moveFiles`) to `WorkManager` or a standard Foreground Service. Current `viewModelScope` execution will silently abort if the app is backgrounded and killed by the OS.

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
