# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.4.0
> **Last Updated:** 2026-03-16

---

## 1. PR Review Findings (Beta Blockers & Polish)

- [x] [Bug] Apply restored browser location before calling refresh
  - **Problem:** When saved navigation state exists, this branch calls refresh() based on restoreLocationFromState() but never writes the restored path/category/volume back into `_state` first.
  - **Location:** `BrowserViewModel.kt`
  - **Fix:** Update `_state` with restored values before calling `refresh()`.

- [x] [Bug] `detectCopyConflicts` misses nested child collisions
  - **Problem:** Only flags top-level name collisions and misses nested child collisions when both source and target are directories.
  - **Location:** `LocalFileRepository.kt` (lines 757 - 797)
  - **Fix:** Update `detectCopyConflicts` to recurse into directory trees so descendant conflicts are discovered.

- [x] [Bug] Missing conflict resolution handling for pre-existing target
  - **Problem:** When a destination exists but `resolutions[sourceFile.absolutePath]` is null, code attempts rename/copy that can erase pre-existing target.
  - **Location:** `LocalFileRepository.kt` (lines 945 - 980)
  - **Fix:** Fail fast and return `Result.failure` when target exists but has no resolution.

- [x] [Bug] Transient lookup failures mask permanent deletes in `DeletePolicy`
  - **Problem:** Uses `repository.getVolumeForPath(path).getOrNull()`, which masks lookup failures as "no volume" and defaults to permanent delete.
  - **Location:** `DeletePolicy.kt` (lines 14 - 25)
  - **Fix:** Inspect the `Result` for failure explicitly instead of `getOrNull()`.

- [x] [Bug] Invalid context access via `navController.context`
  - **Problem:** `NavController` has no `context` property in Compose.
  - **Location:** `ArcileAppShell.kt` (line 188)
  - **Fix:** Obtain `Context` via `LocalContext.current` and pass it to the ViewModel.

- [x] [UI/UX] Dead-end destination picker when no indexed volumes
  - **Problem:** Dialog shown for restore but `indexedVolumes` is empty, trapping user.
  - **Location:** `TrashScreen.kt` (lines 191 - 220)
  - **Fix:** Handle empty `indexedVolumes` by showing a clear message and dismiss action.

- [x] [Bug] Sort MediaStore recents by modified time before limiting
  - **Problem:** Query orders by DATE_ADDED, caps at limit * 2, then sorts by max(dateAdded, dateModified), causing older files that were recently modified to be missed.
  - **Location:** `LocalFileRepository.kt` (getRecentFiles)
  - **Fix:** Sort by modified time before limiting.

- [x] [Bug] Unhandled DataStore read errors in `BrowserPreferencesRepository`
  - **Problem:** `preferencesFlow` maps `context.browserDataStore.data` without handling DataStore read errors.
  - **Location:** `BrowserPreferencesRepository.kt` (lines 17 - 32)
  - **Fix:** Wrap the data flow with a `.catch` operator to intercept `IOException` and emit a safe fallback `BrowserPreferences`.

- [x] [Security] Sensitive path logged in `LocalFileRepository`
  - **Problem:** Warning log writes an absolute path that may contain sensitive info.
  - **Location:** `LocalFileRepository.kt` (lines 1224 - 1226)
  - **Fix:** Log only non-sensitive identifiers (e.g., trash id) or gate the full path behind a debug-only check.

- [x] [Bug] Swallowed JSON parsing exceptions in `StorageClassificationRepository`
  - **Problem:** Exceptions in `JSONObject(value)` parsing are silently swallowed.
  - **Location:** `StorageClassificationRepository.kt` (lines 44 - 58, 69 - 82)
  - **Fix:** Log the failing key/exception and clear or move the bad record instead of silently ignoring it.

- [x] [Bug] Swallowed repository failures in `HomeViewModel`
  - **Problem:** `getOrNull()` is used for repository calls, silently updating `_state` with empty lists on failure.
  - **Location:** `HomeViewModel.kt` (lines 97 - 137)
  - **Fix:** Detect failures and surface them in the UI state by setting an error field.

- [x] [Bug] Unconditional transient UI state reset in `TrashViewModel`
  - **Problem:** `loadTrashFiles()` unconditionally clears `error` and `selectedFiles` on initiation.
  - **Location:** `TrashViewModel.kt` (lines 41 - 49)
  - **Fix:** Only set `isLoading = true` initially, and clear/preserve other transient state appropriately based on success/failure.

- [ ] [Architecture] String matching used for restore failures
  - **Problem:** `TrashViewModel` checks `error.message.startsWith("DESTINATION_REQUIRED")` to show destination picker.
  - **Location:** `TrashViewModel.kt` (lines 79 - 81)
  - **Fix:** Change the restore API to surface a typed failure instead of string matching.

- [ ] [Accessibility] Missing selection state for TalkBack
  - **Problem:** `FileList` item sets merged `contentDescription` but doesn't expose selection state.
  - **Location:** `FileList.kt` (lines 122 - 131)
  - **Fix:** Set `selected = isSelected` in the `semantics` block.

- [ ] [UI/UX] Shimmer animation captures range at zero size
  - **Problem:** Animation range is captured while `size == IntSize.Zero`, so `startOffsetX` stays `0`.
  - **Location:** `ShimmerModifier.kt` (lines 41 - 51)
  - **Fix:** Use a normalized range (e.g., `0f..1f`) and compute concrete pixel offset during render using latest size.

- [ ] [UI/UX] Wrong UI rendered for empty root screens
  - **Problem:** `EmptyState` branch is evaluated before `volume-root` branch.
  - **Location:** `FileManagerScreen.kt` (lines 480 - 493)
  - **Fix:** Reorder checks so `state.isVolumeRootScreen` is tested first.

- [ ] [Design] Unreadable secondary colors in light theme
  - **Problem:** `buildScheme` light theme produces nearly-white secondary colors on `Color.White`.
  - **Location:** `Color.kt` (lines 427 - 473)
  - **Fix:** Increase opacity of secondary or composite stronger tint over white.

- [ ] [Design] Material2 theme bases used instead of Material3
  - **Problem:** XML theme bases still use Material2 components (`Theme.Material.Light.NoActionBar`).
  - **Location:** `themes.xml` (lines 6 - 11)
  - **Fix:** Update parent styles to `Theme.Material3.*`.

- [x] [Build] KSP version mismatch with Kotlin 2.2.10
  - **Problem:** KSP pinned to "2.0.21-1.0.25" while project uses Kotlin 2.2.10.
  - **Location:** `libs.versions.toml` (line 19)
  - **Fix:** Update KSP version to align with Kotlin 2.2.10.

---

## 2. Architecture & Refactoring

- [ ] [Refactor] De-monolith `LocalFileRepository.kt` (1139 lines).
  - **Problem:** Handles basic CRUD, MediaStore categories, Trash subsystem, and Copy/Move conflict resolution all in one file, violating SRP.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Extract Trash logic to `TrashRepository`. Extract MediaStore queries to `MediaStoreDataSource` or `CategoryRepository`. Extract copy/move logic to `FileTransferHandler`.

- [ ] [Refactor] Modularize `HomeScreen.kt` composables (985 lines).
  - **Problem:** Embeds local composables for storage UI and category grid.
  - **Location:** `HomeScreen.kt`
  - **Fix:** Move `StorageSummaryCard`, `MultiColorStorageBar`, `CategoryLegend`, `CategoryGrid`, and `MainFoldersGrid` to `components/`.

- [ ] [Refactor] Split `SettingsScreen.kt` sections (365 lines).
  - **Problem:** Contains both the main settings layout and the detailed UI for theme mode and accent color selection.
  - **Location:** `SettingsScreen.kt`
  - **Fix:** Extract `ThemeModeSelector` and `AccentColorSelector` to `components/settings/`.

- [ ] [Refactor] Extract Navigation from `ArcileAppShell.kt` (244 lines).
  - **Problem:** Combines the app Shell (Scaffold/SharedTransitionLayout) with the massive `NavHost` definition.
  - **Location:** `ArcileAppShell.kt`
  - **Fix:** Move the `NavHost` and its route definitions to a dedicated file like `AppNavigationGraph.kt`.

- [ ] [Refactor] Split `PasteConflictDialog.kt` components (363 lines).
  - **Problem:** Contains the main dialog scaffolding along with detailed comparison cards and thumbnail helpers.
  - **Location:** `components/PasteConflictDialog.kt`
  - **Fix:** Extract `ConflictCard` and `FileInfoColumn` into smaller, reusable pieces if possible.

- [ ] [Refactor] Modularize Color Themes in `Color.kt` (351 lines).
  - **Problem:** Contains hardcoded definitions for 10+ distinct dynamic and static color schemes in one massive file.
  - **Location:** `ui/theme/Color.kt`
  - **Fix:** Split palettes by core hue or generate them algorithmically.

- [ ] [Refactor] Clean up `TrashScreen.kt` (257 lines).
  - **Problem:** Mixes the scaffold, empty state logic, alert dialogs, and the main lazy list.
  - **Location:** `TrashScreen.kt`
  - **Fix:** Move `TrashList` and the Empty Trash AlertDialog to separate composables in `components/trash/`.

- [ ] [Architecture] `ShareSelectedFiles()` in ViewModel creates an `Intent` — Android framework concern.
  - **Problem:** ViewModel directly launches `Intent` via `context.startActivity()`.
  - **Location:** `BrowserViewModel.kt`, `RecentFilesViewModel.kt`
  - **Fix:** Expose a `shareEvent` flow/channel and handle the Intent in the UI layer or a dedicated use case.

- [ ] [Architecture] `HomeScreen.kt` directly accesses `Environment.getExternalStorageDirectory()`.
  - **Problem:** `MainFoldersGrid` composable directly reads from `Environment`, embedding Android framework knowledge in the UI layer.
  - **Location:** `HomeScreen.kt:916`
  - **Fix:** Pass folder paths from ViewModel or a provider.

- [ ] [Architecture] Navigation routes use string constants — no type-safe navigation.
  - **Problem:** `AppRoutes` uses `const val` strings and direct `navController.navigate()` calls.
  - **Location:** `AppRoutes.kt`, `ArcileAppShell.kt`
  - **Fix:** Migrate to Navigation Compose type-safe routes (Kotlin serialization-based route classes).

---

## 3. Performance & Efficiency

- [ ] [Performance] `getCategoryStorageSizes()` scans the entire MediaStore without filtering.
  - **Problem:** Queries all files from MediaStore to compute per-category sizes. On devices with 100K+ indexed files, this is very slow.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Use `GROUP BY` SQL aggregation on MIME types in the ContentResolver query, or cache results with a time-based invalidation.

- [ ] [Performance] `getFilesByCategory()` loads all MediaStore entries then filters client-side.
  - **Problem:** `SELECT DATA FROM files` with no selection clause, then filtering by extension in Kotlin.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Use a `LIKE` or `IN` clause on `MIME_TYPE` in the MediaStore query.

- [ ] [Performance] Scoped `searchFiles()` uses `File.walkTopDown()` — recursive filesystem traversal on the main thread.
  - **Problem:** Although wrapped in `Dispatchers.IO`, walking the entire filesystem tree for a scoped search is inherently slow and produces an unbounded list.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Cap results (e.g., first 200 matches), add cancellation checks in the walk, or use MediaStore for scoped search too.

- [ ] [Performance] `LazyColumn` / `LazyVerticalGrid` items re-keyed by `absolutePath` may conflict.
  - **Problem:** Multiple files from different directories could have the same `absolutePath` key if the list content changes during recomposition.
  - **Location:** `FileManagerScreen.kt`, `HomeScreen.kt`
  - **Fix:** Validate key uniqueness or include additional context (e.g., directory path prefix).

- [ ] [Performance] `AudioAlbumArtFetcher` opens `MediaMetadataRetriever` on the UI thread call path.
  - **Problem:** Coil dispatches fetchers, but `MediaMetadataRetriever.setDataSource()` can be slow on some devices.
  - **Location:** `AudioAlbumArtFetcher.kt`
  - **Fix:** Ensure Coil's dispatcher configuration routes this to an IO thread.

---

## 4. General & Code Quality

- [ ] [Security] Signing credentials may be in `local.properties` which is in `.gitignore`, but the pattern is fragile.
  - **Problem:** Keystore password, key alias, and key password are read from `local.properties` at build time.
  - **Location:** `app/build.gradle.kts`
  - **Fix:** Use environment variables or a dedicated CI secrets mechanism instead of a local file.

- [ ] [Security] `MANAGE_EXTERNAL_STORAGE` permission has no graceful degradation.
  - **Problem:** The app requests `MANAGE_EXTERNAL_STORAGE` on Android 11+ but provides no fallback if the user denies it.
  - **Location:** `AndroidManifest.xml`, `MainActivity.kt`
  - **Fix:** Implement SAF fallback for granular per-folder access.

- [ ] [Code Quality] `ThemePreferences` instantiated inside `setContent{}` with `remember`.
  - **Problem:** `val themePreferences = remember { ThemePreferences(applicationContext) }` creates a non-lifecycle-aware instance inside composition.
  - **Location:** `MainActivity.kt:72`
  - **Fix:** Hoist `ThemePreferences` to the Activity level or inject via DI.

- [ ] [Code Quality] Minimal unit tests exist.
  - **Problem:** `ExampleInstrumentedTest.kt` and `FilePresentationTest.kt` are the only test files.
  - **Location:** `app/src/test/` and `app/src/androidTest/`
  - **Fix:** Add unit tests for `HomeViewModel`, `BrowserViewModel`, `LocalFileRepository`, and domain logic.

- [ ] [Design] Custom color schemes hardcoded instead of using Material Color Utilities.
  - **Problem:** All accent color schemes are manually defined with hardcoded hex colors rather than generated from seed colors via HCT.
  - **Location:** `Color.kt`
  - **Fix:** Use the `material-color-utilities` library to generate `ColorScheme` from seed color at runtime.

- [ ] [Design] `ExpressiveSquircleShape` is a simple `RoundedCornerShape(28.dp)`, not a true squircle.
  - **Problem:** A squircle (superellipse) has continuously changing curvature, while `RoundedCornerShape` uses circular arc corners.
  - **Location:** `Shape.kt`
  - **Fix:** Use `RoundedCornerShape` with `CornerSize` percent or implement a custom `CutoutShape`.

- [ ] [Anomaly] Compose BOM version `2024.09.00` conflicts with explicit Material3 `1.4.0-alpha08`.
  - **Location:** `libs.versions.toml`, `app/build.gradle.kts`
  - **Fix:** Update the BOM to match the M3 alpha requirement, or pin all Compose libraries to compatible versions.

- [ ] [Anomaly] `compileSdk` uses non-standard block syntax with `minorApiLevel`.
  - **Location:** `app/build.gradle.kts`
  - **Fix:** Document this in `DEVELOPMENT.md` if intentional.

- [ ] [Anomaly] `VariantOutputImpl` cast in `androidComponents` block.
  - **Location:** `app/build.gradle.kts`
  - **Fix:** Check if a stable API for `outputFileName` exists in AGP 9.x.

- [x] [Anomaly] `Kotlin 2.2.10` version may be unreleased/preview.
  - **Location:** `libs.versions.toml:9`
  - **Fix:** Verify this is an official release and document any special repository requirements.

## 6. Comprehensive Audit Findings (New)

### A. UI, UX & Accessibility
- [ ] [i18n] Hardcoded UI Strings (No Localization).
  - **Problem:** Over 90 instances of hardcoded string literals in `Text("...")` composables, preventing app localization.
  - **Location:** Pervasive (e.g., `AboutScreen.kt`, `SettingsScreen.kt`, `FileManagerScreen.kt`, Dialogs).
  - **Fix:** Extract all hardcoded strings to `res/values/strings.xml` and use `stringResource(R.string.*)`.
- [ ] [i18n] Direct String Interpolation in UI Text.
  - **Problem:** Rather than using formatted string resources (e.g., `<string name="delete_items">Delete %1$d item(s)?</string>`), the app dynamically interpolates state variables directly into hardcoded strings.
  - **Location:** `RecentFilesScreen.kt`, `FileManagerScreen.kt`, `AboutScreen.kt`.
  - **Fix:** Use string resources with format arguments (e.g. `stringResource(R.string.delete_items, count)`).
- [ ] [Accessibility] Hardcoded Content Descriptions.
  - **Problem:** Over 40 instances of hardcoded `contentDescription = "..."` for Icons and interactive elements, rendering screen readers useless for non-English users.
  - **Location:** `ArcileTopBar.kt`, `TrashScreen.kt`, `GlobalSearchBar.kt`, `FileList.kt`, etc.
  - **Fix:** Extract `contentDescription` strings to `strings.xml` and use `stringResource()`.
- [ ] [UI/UX] Nested Scaffolds causing potential double-padding issues.
  - **Problem:** `ArcileAppShell` provides a root `Scaffold`, but child screens (`HomeScreen`, `SettingsScreen`, etc.) also define their own `Scaffold` without explicitly consuming or propagating the root padding correctly, which can cause inset glitches.
  - **Location:** `ArcileAppShell.kt` and all screen composables.
  - **Fix:** Remove nested Scaffolds or use `WindowInsets` carefully with `.consumeWindowInsets()` to avoid overlapping or double-applied padding.

### B. Architecture & Code Quality
- [ ] [Architecture] Activity State managed via `mutableStateOf`.
  - **Problem:** Permission state (`_hasPermission`) in `MainActivity` is managed via `mutableStateOf` directly in the Activity class.
  - **Location:** `MainActivity.kt`
  - **Fix:** Move permission logic and state into a dedicated `PermissionManager` or a scoped `ViewModel` to improve testability and separation of concerns.
- [ ] [Correctness] `runBlocking` used inside coroutine flows in tests.
  - **Problem:** `StorageScopeViewModelTest` uses `runBlocking { emit(volumes) }` in a test fake's init block. This violates coroutine testing best practices and can lead to deadlocks or flaky tests.
  - **Location:** `app/src/test/.../StorageScopeViewModelTest.kt`
  - **Fix:** Remove `runBlocking`, initialize state flows directly with the initial value, or emit inside a properly scoped test coroutine.

---

## Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **SAF (Storage Access Framework) Fallback**: Integrate SAF as a fallback when `MANAGE_EXTERNAL_STORAGE` is denied, enabling granular per-folder access on Android 11+ without requiring the all-files permission.
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