# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.4.5
> **Last Updated:** 2026-03-19

---

## Table of Contents

1. [PR Review Findings (Beta Blockers & Polish)](#1-pr-review-findings-beta-blockers--polish)
2. [Architecture & Refactoring](#2-architecture--refactoring)
3. [Performance & Efficiency](#3-performance--efficiency)
4. [General & Code Quality](#4-general--code-quality)
5. [Comprehensive Audit Findings](#5-comprehensive-audit-findings)
6. [Backlog / Ideas](#6-backlog--ideas)
7. [Completed Tasks](#7-completed-tasks)

---

## 2. Architecture & Refactoring

- [ ] [Refactor] De-monolith `LocalFileRepository.kt` (1139 lines).
  - **Problem:** Handles basic CRUD, MediaStore categories, Trash subsystem, and Copy/Move conflict resolution all in one file, violating SRP.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Extract Trash logic to `TrashRepository`. Extract MediaStore queries to `MediaStoreDataSource` or `CategoryRepository`. Extract copy/move logic to `FileTransferHandler`.

- [ ] [Refactor] Modularize Color Themes in `Color.kt` (351 lines).
  - **Problem:** Contains hardcoded definitions for 10+ distinct dynamic and static color schemes in one massive file.
  - **Location:** `ui/theme/Color.kt`
  - **Fix:** Split palettes by core hue or generate them algorithmically.

---

## 3. Performance & Efficiency

- [ ] [Performance] `LazyColumn` / `LazyVerticalGrid` items re-keyed by `absolutePath` may conflict.
  - **Problem:** Multiple files from different directories could have the same `absolutePath` key if the list content changes during recomposition.
  - **Location:** `FileManagerScreen.kt`, `HomeScreen.kt`
  - **Fix:** Validate key uniqueness or include additional context (e.g., directory path prefix).

---

## 5. Comprehensive Audit Findings

### A. UI, UX & Accessibility

- [ ] [i18n] Direct String Interpolation in UI Text.
  - **Problem:** Rather than using formatted string resources (e.g., `<string name="delete_items">Delete %1$d item(s)?</string>`), the app dynamically interpolates state variables directly into hardcoded strings.
  - **Location:** `RecentFilesScreen.kt`, `FileManagerScreen.kt`, `AboutScreen.kt`.
  - **Fix:** Use string resources with format arguments (e.g. `stringResource(R.string.delete_items, count)`).

- [ ] [Accessibility] Hardcoded Content Descriptions.
  - **Problem:** Over 40 instances of hardcoded `contentDescription = "..."` for Icons and interactive elements, rendering screen readers useless for non-English users.
  - **Location:** `ArcileTopBar.kt`, `TrashScreen.kt`, `GlobalSearchBar.kt`, `FileList.kt`, etc.
  - **Fix:** Extract `contentDescription` strings to `strings.xml` and use `stringResource()`.

- [x] [UI/UX] Nested Scaffolds causing potential double-padding issues.
  - **Problem:** `ArcileAppShell` provides a root `Scaffold`, but child screens (`HomeScreen`, `SettingsScreen`, etc.) also define their own `Scaffold` without explicitly consuming or propagating the root padding correctly, which can cause inset glitches.
  - **Location:** `ArcileAppShell.kt` and all screen composables.
  - **Fix:** Remove nested Scaffolds or use `WindowInsets` carefully with `.consumeWindowInsets()` to avoid overlapping or double-applied padding.

### B. Security

- [ ] [Security] Signing credentials read from `local.properties` without validation.
  - **Problem:** `app/build.gradle.kts` loads signing config properties from `local.properties` with null-unsafe casts (`as String?`). If any property is missing, the release build will crash with a `ClassCastException` at configuration time, leaking the build environment structure in error logs.
  - **Location:** `app/build.gradle.kts:25-39`
  - **Fix:** Use `?.toString()` and validate presence before creating the `signingConfig`. Consider a dedicated `signing.properties` file excluded from VCS.

- [x] [Security] Empty catch blocks silently swallow errors in critical paths.
  - **Problem:** Multiple `catch (e: Exception) {}` blocks silently discard errors in critical operations — e.g., `.nomedia` file creation in trash dir (`LocalFileRepository.kt:1226`), MediaStore deletion after trash (`LocalFileRepository.kt:1330`), `StorageStatsManager` query (`LocalFileRepository.kt:686`), and `BroadcastReceiver` registration context.
  - **Location:** `LocalFileRepository.kt:686,1226,1330`
  - **Impact:** Silent failures make debugging impossible and can leave the filesystem in an inconsistent state.
  - **Fix:** At minimum, log the exception. For critical paths (trash, MediaStore cleanup), propagate or handle the error.

- [ ] [Security] `registerReceiver` called without `RECEIVER_NOT_EXPORTED` flag.
  - **Problem:** `observeStorageVolumes()` registers a `BroadcastReceiver` via `appContext.registerReceiver(receiver, filter)` without specifying `ContextCompat.RECEIVER_NOT_EXPORTED`, which is required on API 34+ to prevent other apps from sending spoofed intents.
  - **Location:** `LocalFileRepository.kt:240`
  - **Fix:** Use `ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)`.

### C. Performance & Resource Efficiency

- [x] [Performance] Unbounded MediaStore cursor traversal in `getFilesByCategory()`.
  - **Problem:** `getFilesByCategory()` iterates the entire MediaStore cursor for each category query without a `LIMIT` clause (which MediaStore doesn't directly support). For devices with 100K+ files, this blocks the IO dispatcher for potentially seconds. Additionally, each row calls `File(path).exists()` and `file.isFile`, causing redundant filesystem stat calls.
  - **Location:** `LocalFileRepository.kt:774-861`
  - **Impact:** UI hangs when opening a category with many files; heavy disk IO.
  - **Fix:** Remove the `File.exists()` / `File.isFile` check (trust the MediaStore), and consider pagination or streaming results to the ViewModel.

- [ ] [Performance] `searchFiles()` filesystem walk has no depth or count limit.
  - **Problem:** When searching within a `StorageScope.Path`, the method uses `rootDir.walkTopDown()` with no `maxDepth()` or result count limit. On a deeply nested directory tree (e.g., `Android/data`), this can traverse millions of entries and block the coroutine for tens of seconds.
  - **Location:** `LocalFileRepository.kt:887-896`
  - **Fix:** Add `.maxDepth()` and collect at most N results before returning.

- [ ] [Performance] `discoverPlatformVolumes()` called redundantly.
  - **Problem:** `discoverPlatformVolumes()` performs `StatFs` and `StorageManager` queries. It is called both in `getStorageVolumes()` and from the `callbackFlow` inside `observeStorageVolumes()`, and also during class initialization (`activeStorageRoots` assignment). On devices with many volumes, this triples the platform discovery cost.
  - **Location:** `LocalFileRepository.kt:119,221,298-300`
  - **Fix:** Cache the result and invalidate only when storage broadcasts are received.

- [ ] [Performance] `RecentFilesViewModel` queries MediaStore for 500 items with `limit = 500`.
  - **Problem:** The recent files screen requests 500 results. Internally, `getRecentFiles()` over-fetches by `limit * 2 = 1000` rows from MediaStore, checks each against `File.exists()`, and then sorts and truncates. This is expensive on devices with many files.
  - **Location:** `RecentFilesViewModel.kt:63`, `LocalFileRepository.kt:479`
  - **Fix:** Use a more reasonable initial limit or implement pagination/lazy loading.

- [ ] [Performance] `HomeViewModel` makes 4+ parallel repository calls on every `SILENT` refresh.
  - **Problem:** Every time `observeStorageVolumes` emits (which happens on media mount/unmount AND on classification changes), `loadHomeData(SILENT)` is called, triggering `getRecentFiles`, `getStorageVolumes`, `getStorageInfo`, `getCategoryStorageSizes`, plus per-volume `getCategoryStorageSizes` queries. This fan-out produces heavy IO on every trivial change.
  - **Location:** `HomeViewModel.kt:72-78`
  - **Fix:** Debounce the `SILENT` refresh or diff the incoming volumes before triggering a full reload.

- [ ] [Performance] `filterAndSortFiles()` recomputed in `remember()` but keyed poorly.
  - **Problem:** In `FileManagerScreen.kt`, `filterAndSortFiles()` is cached with `remember(state.files, state.browserSortOption)` but `state.files` is a `List<FileModel>` that can be structurally identical across recompositions (same content, different instance) if the ViewModel emits a new copy. This triggers re-sort on every `state.copy(...)`.
  - **Location:** `FileManagerScreen.kt:242-244`
  - **Fix:** Use `@Stable` or `@Immutable` annotations on `FileModel` / `BrowserState`, or use `derivedStateOf` with snapshot reads.

### D. Architecture & Code Quality

- [ ] [Architecture] Activity State managed via `mutableStateOf`.
  - **Problem:** Permission state (`_hasPermission`) in `MainActivity` is managed via `mutableStateOf` directly in the Activity class.
  - **Location:** `MainActivity.kt`
  - **Fix:** Move permission logic and state into a dedicated `PermissionManager` or a scoped `ViewModel` to improve testability and separation of concerns.

- [ ] [Architecture] `shareSelectedFiles()` duplicated across `BrowserViewModel` and `RecentFilesViewModel`.
  - **Problem:** Both ViewModels contain near-identical `shareSelectedFiles(context: Context)` methods (~30 lines each) that also receive `Context` as a parameter — a violation of the ViewModel contract (ViewModels should not hold Android framework references).
  - **Location:** `BrowserViewModel.kt:683-716`, `RecentFilesViewModel.kt:160-193`
  - **Fix:** Extract to a shared `ShareHelper` class or use case. Expose a share event via `Channel`/`SharedFlow` and handle the `Intent` in the UI layer.

- [ ] [Architecture] `ThemePreferences` instantiated directly in `MainActivity.onCreate()`.
  - **Problem:** `themePreferences = ThemePreferences(applicationContext)` creates a non-injected, non-testable dependency directly in the Activity. This bypasses Hilt's DI graph.
  - **Location:** `MainActivity.kt:53`
  - **Fix:** Provide `ThemePreferences` via Hilt `@Provides` or `@Inject constructor`.

- [ ] [Architecture] Dead code: top-level `mergeStorageClassifications()` function.
  - **Problem:** There is a top-level function `mergeStorageClassifications()` at `LocalFileRepository.kt:39-56` that is identical to the internal member function `mergeClassifications()` at lines 199-216. The top-level function appears unreferenced.
  - **Location:** `LocalFileRepository.kt:39-56`
  - **Fix:** Remove the dead top-level function.

- [ ] [Architecture] `BrowserPreferencesRepository` not abstracted behind an interface.
  - **Problem:** `BrowserPreferencesRepository` is a concrete class injected directly. Unlike `FileRepository` (which has the `FileRepository` interface), there is no abstraction for browser preferences, making it untestable with fakes.
  - **Location:** `data/BrowserPreferencesRepository.kt`, `di/RepositoryModule.kt:47-51`
  - **Fix:** Extract a `BrowserPreferencesStore` interface and bind the concrete implementation via DI.

- [ ] [Architecture] `activeStorageRoots` is a mutable `var` without thread safety.
  - **Problem:** `activeStorageRoots` is a mutable `List` assigned from `discoverPlatformVolumes()` which can race with `validatePath()` executing concurrently on the IO dispatcher. A read of `activeStorageRoots` while another coroutine is reassigning it is a data race.
  - **Location:** `LocalFileRepository.kt:119`
  - **Fix:** Use `@Volatile` or wrap in a `Mutex`/`AtomicReference`.

- [ ] [Architecture] `StorageMountState` enum defined but never used dynamically.
  - **Problem:** `StorageMountState` has `MOUNTED` and `UNMOUNTED` variants, but `UNMOUNTED` is never assigned anywhere. All `StorageVolume` instances are created with the default `MOUNTED` state. The enum provides no runtime value.
  - **Location:** `domain/StorageScope.kt:10-13`, `domain/StorageInfo.kt:66`
  - **Fix:** Either implement unmounted volume detection or remove `StorageMountState` and `mountState` from `StorageVolume` until needed.

### E. Maintainability & Code Quality

- [ ] [CodeQuality] Deprecated `SearchFilters` typealias kept in `presentation/SearchFilters.kt`.
  - **Problem:** A deprecated typealias `presentation.SearchFilters → domain.SearchFilters` exists with `@file:Suppress("DEPRECATION_WARNING")`. This is dead code adding confusion — the canonical type is in `domain/SearchFilters.kt`.
  - **Location:** `presentation/SearchFilters.kt`
  - **Fix:** Verify no external references, then delete the file.

- [ ] [CodeQuality] Manual JSON serialization instead of using `kotlinx.serialization`.
  - **Problem:** `LocalFileRepository` and `StorageClassificationRepository` manually construct `JSONObject` instances for trash metadata and storage classifications. The project already depends on `kotlinx.serialization.json` — this is wasted complexity.
  - **Location:** `LocalFileRepository.kt:1287-1293,1474`, `StorageClassificationRepository.kt:48-56,98-103`
  - **Fix:** Define `@Serializable` data classes and use `Json.encodeToString()` / `Json.decodeFromString()`.

- [ ] [CodeQuality] `FileSortOption.label` property uses hardcoded English strings.
  - **Problem:** The `label` property on `FileSortOption` (e.g., `"Name (A-Z)"`) is not backed by string resources. This will display English text regardless of locale.
  - **Location:** `presentation/FilePresentation.kt:5-12`
  - **Fix:** Remove the `label` property and resolve display names via `stringResource()` in composables.

- [ ] [CodeQuality] Duplicate filename validation logic across `BrowserViewModel` and `LocalFileRepository`.
  - **Problem:** Both `BrowserViewModel.createFolder()` / `createFile()` and `LocalFileRepository.validateFileName()` independently check for `'/'`, `'\\'`, `".."`, `'\u0000'`. The ViewModel check is redundant and can drift from the repository's authoritative validation.
  - **Location:** `BrowserViewModel.kt:475-478,494-497`, `LocalFileRepository.kt:268-273`
  - **Fix:** Remove the ViewModel validation and rely on the repository's `Result.failure` for error messaging.

- [ ] [CodeQuality] Missing ViewModel unit tests for critical flows.
  - **Problem:** There are no unit tests for any ViewModel (`BrowserViewModel`, `HomeViewModel`, `RecentFilesViewModel`, `TrashViewModel`). These classes contain substantial business logic (delete policy evaluation, clipboard management, navigation state restoration, debounced search) that is completely untested.
  - **Location:** `src/test/` (missing files)
  - **Fix:** Add ViewModel tests with a `FakeFileRepository`.

- [ ] [CodeQuality] No Compose UI tests beyond the example `ExampleInstrumentedTest.kt`.
  - **Problem:** The `androidTest` directory contains only the auto-generated `ExampleInstrumentedTest`. No screen-level or component-level Compose tests exist.
  - **Location:** `src/androidTest/`
  - **Fix:** Add `@Composable` preview tests and screen-level integration tests for critical flows (file browsing, delete confirmation, search).

### F. UI / UX Implementation Quality

- [ ] [UI/UX] Hardcoded "Delete" and "Cancel" button text in delete confirmation dialogs.
  - **Problem:** The `FileManagerScreen.kt` delete confirmation dialogs use `Text("Delete")` and `Text("Cancel")` instead of `stringResource()`.
  - **Location:** `FileManagerScreen.kt:588,593,612,618`
  - **Fix:** Extract to string resources.

- [ ] [UI/UX] `SimpleDateFormat` created inside `remember()` but not locale-aware on configuration change.
  - **Problem:** `SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())` is created inside a `remember` block. If the user changes the system locale while the app is open, the formatter won't update because `remember` does not re-execute.
  - **Location:** `FileManagerScreen.kt:392`, `HomeScreen.kt:483`
  - **Fix:** Create the formatter outside `remember` (each recomposition is cheap) or key on locale.

- [ ] [UI/UX] `HomeScreen.kt` directly accesses `android.os.Environment` for folder paths.
  - **Problem:** `MainFoldersGrid` builds folder paths using `Environment.getExternalStorageDirectory()` directly in the composable. This is an existing known issue (see section 2) but the composable also lacks existence checks — if a standard folder (e.g., `Movies`) doesn't exist, the entry is still shown.
  - **Location:** `HomeScreen.kt` (inside `MainFoldersGrid`)
  - **Fix:** Pass folder paths from ViewModel and filter out non-existent directories.

- [x] [UI/UX] `StorageDashboardScreen` uses `hiltViewModel<HomeViewModel>()` — creates separate instance.
  - **Problem:** The Storage Dashboard composable calls `hiltViewModel<HomeViewModel>()`, which creates a new ViewModel instance scoped to that composable's `NavBackStackEntry`. This means it doesn't share state with the Home screen's `HomeViewModel` and triggers a redundant full reload.
  - **Location:** `ArcileAppShell.kt:133`
  - **Fix:** Either accept `HomeState` as a parameter (hoisted from parent) or use a shared ViewModel scope via `navController.previousBackStackEntry`.

### G. Material Design 3 Expressive Implementation

- [ ] [M3] Inconsistent use of theme spacing — mix of hardcoded `dp` and `MaterialTheme.spacing`.
  - **Problem:** Some composables use `MaterialTheme.spacing.medium` while nearby code uses hardcoded `12.dp`, `16.dp`, `20.dp` for the same logical spacing. This makes the spacing system partially useless.
  - **Location:** `HomeScreen.kt` (e.g., `Spacer(Modifier.height(16.dp))` vs `MaterialTheme.spacing.medium`), `StorageSummaryCard`, `StorageVolumeCard`
  - **Fix:** Audit all `dp` values and replace with the appropriate `MaterialTheme.spacing.*` token.

- [x] [M3] `PermissionRequestScreen` does not use M3 expressive components.
  - **Problem:** The permission request screen uses only basic `Text` and `Button` with minimal styling. It lacks the polish of the rest of the app — no icon, no card, no surface elevation, minimal spacing.
  - **Location:** `ArcileAppShell.kt:260-286`
  - **Fix:** Redesign with an M3 card, illustration/icon, and consistent theming.

### H. Interaction Quality & Motion Design

- [ ] [Motion] Pull-to-refresh indicator logic duplicated between `HomeScreen` and `FileManagerScreen`.
  - **Problem:** The custom pull-to-refresh indicator (floating `Card` with `LoadingIndicator`) is copy-pasted across `HomeScreen.kt` and `FileManagerScreen.kt` with identical implementations (~30 lines each).
  - **Location:** `HomeScreen.kt:330-357`, `FileManagerScreen.kt:458-493`
  - **Fix:** Extract to a shared `ArciclePullRefreshIndicator` composable.

- [x] [Motion] No entry animation for list items on initial load.
  - **Problem:** When a directory first loads, all items appear instantly. There is no staggered fade-in or slide-in animation for list items, which makes the transition from loading to content feel abrupt.
  - **Location:** `FileList.kt`, `FileGrid.kt`, `FileManagerScreen.kt`
  - **Fix:** Add `animateItem()` modifier on LazyColumn/LazyGrid items.

### I. App Smoothness & Rendering Stability

- [x] [Smoothness] `AnimatedContent` key uses string concatenation instead of data class key.
  - **Problem:** `AnimatedContent(targetState = if (searchHasCompleted) "search" else state.currentPath + state.activeCategoryName + state.isVolumeRootScreen)` — concatenating a Boolean into a String creates ambiguous keys (e.g., `"/storage/emulated/0" + "" + "true"` could match a different path). This can cause unexpected cross-fade animations.
  - **Location:** `FileManagerScreen.kt:373`
  - **Fix:** Use a sealed class or data class as the key type.

- [ ] [Smoothness] `HomeViewModel.loadHomeData()` causes UI flicker on `SILENT` refresh.
  - **Problem:** On `SILENT` refresh, `isCalculatingStorage` is set to `true` before the async work starts, and `isLoading` may briefly toggle. This can cause the shimmer effect to flash momentarily on the storage bar when volumes are re-enumerated.
  - **Location:** `HomeViewModel.kt:87-94`
  - **Fix:** Only set `isCalculatingStorage = true` for `INITIAL` and `MANUAL` modes.

### J. Documentation Quality

- [ ] [Docs] `proguard-rules.pro` is the default template — no app-specific rules.
  - **Problem:** The ProGuard rules file contains only the auto-generated comments. With `isMinifyEnabled = true` and `isShrinkResources = true` in release builds, the app may crash at runtime if any reflection-based libraries require keep rules (e.g., `kotlinx.serialization`, Coil custom fetchers, Hilt-generated code).
  - **Location:** `arcile-app/app/proguard-rules.pro`
  - **Fix:** Add keep rules for `@Serializable` data classes, custom Coil `Fetcher` factories, and verify release builds don't crash.

### K. General Anomalies

- [ ] [Anomaly] `BrowserState` holds `android.content.IntentSender` directly in data class.
  - **Problem:** `BrowserState.nativeRequest` and `TrashState.nativeRequest` hold `IntentSender` — a Parcelable Android framework object. Storing platform objects in state classes violates separation of concerns and prevents trivial unit testing of state assertions.
  - **Location:** `BrowserViewModel.kt:64`, `TrashViewModel.kt:27`
  - **Fix:** Expose the `IntentSender` via a `SharedFlow<IntentSender>` one-shot event instead of embedding it in the state data class.

- [ ] [Anomaly] `HomeScreen` constructs `Calendar` and `SimpleDateFormat` on every recomposition.
  - **Problem:** Inside `remember(state.recentFiles, ...)`, a `Calendar.getInstance()` is created to compute `todayStart`. While `remember` prevents re-execution within the same composition, the `Calendar` locale and timezone are baked at creation time. More importantly, the `SimpleDateFormat` at line 483 is created inside a `LazyColumn` item scope — one instance per recomposition.
  - **Location:** `HomeScreen.kt:269-276,483`
  - **Fix:** Hoist `todayStart` calculation to the ViewModel and pass it as part of `HomeState`.

---

## 6. Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.

### File Operations
- **File/Folder Properties Dialog**: Display detailed metadata for selected items.
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser.

### Home Screen Enhancements
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally-scrollable carousel of the 10 most recently modified images and videos.
- **Customizable Quick Access Folders**: Allow users to pin/unpin folders to the Quick Access section on the Home screen.

### Browsing & Organization
- **Starred / Favorited Files**: Add a "Starred" section to the Home screen and a star toggle on individual files/folders.
- **Enhanced Category Browsing**: When opening a file category, display all related folders containing matching files with a tabbed or segmented navigation bar.
- **Folder-Level Storage Dashboard**: Add an option in the Storage Dashboard to view storage consumption broken down by top-level folders.
- **Folder Subtitle Metadata**: Display a compact subtitle below each folder name showing the item count and total size (e.g., `24 items · 1.3 GB`).

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android's multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.

---
