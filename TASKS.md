# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.3.6
> **Last Updated:** 2026-03-14

---

## 1. UI & UX Improvements (Prioritized)

- [ ] [UI/UX] Hardcoded Colors & Theme Compliance.
  - **Problem:** Components use hardcoded colors (`Color(0xFF...)`, `Color.Black.copy(...)`) instead of semantic `MaterialTheme.colorScheme`.
  - **Location:** `FileManagerScreen.kt`, `CategoryColors.kt`, `Color.kt`
  - **Fix:** Centralize and use semantic color tokens.

- [ ] [UI/UX] Typography Overrides.
  - **Problem:** Inline overrides of `fontWeight = FontWeight.Bold` bypassing the centralized typography system.
  - **Location:** `HomeScreen.kt`, `StorageDashboardScreen.kt`
  - **Fix:** Update `Type.kt` and use semantic styles.

- [ ] [UI/UX] Spacing Inconsistencies.
  - **Problem:** Fluctuating padding values (16.dp vs 20.dp vs 24.dp) breaking pixel-perfect alignment.
  - **Location:** `HomeScreen.kt`
  - **Fix:** Define and adhere to a standard spacing scale (e.g. 4dp/8dp grid).

- [ ] [UI/UX] Component Unification (Selection States).
  - **Problem:** `ArcileTopBar` uses `surfaceContainerHigh` for selection, while lists use `primaryContainer`.
  - **Location:** `ArcileTopBar.kt`, `GlobalSearchBar.kt`
  - **Fix:** Unify selection color across components.

- [ ] [UI/UX] The "Expressive" vs. Default Material 3 Clash.
  - **Problem:** Highly rounded custom themes (up to 32dp) clash with standard M3 `DropdownMenu` defaults (4dp).
  - **Location:** `ArcileTopBar.kt`
  - **Fix:** Apply custom rounded shapes to Dropdown Menus and Context Menus.

- [ ] [UI/UX] Bypassing the Theme Shape System.
  - **Problem:** Direct hardcoding of `ExpressiveSquircleShape` instead of using `MaterialTheme.shapes`.
  - **Location:** `Shape.kt`, `HomeScreen.kt`, `RenameDialog.kt`, `CreateFileDialog.kt`
  - **Fix:** Map custom shapes to `MaterialTheme.shapes` and use `Modifier.clip(MaterialTheme.shapes.extraLarge)`.

- [ ] [UI/UX] Mixed Shape Systems in Complex Components.
  - **Problem:** Mix-and-match of custom squircle shapes and standard theme medium shapes causes visual misalignment.
  - **Location:** `PasteConflictDialog.kt`
  - **Fix:** Standardize internal and external radiuses for clean nesting.

- [ ] [UI/UX] Bottom Sheets & Overlays.
  - **Problem:** Heavy reliance on Dialogs rather than standard `ModalBottomSheet` for mobile.
  - **Fix:** Evaluate migrating some context menus/dialogs to bottom sheets.

- [ ] [UI/UX] `MultiColorStorageBar` Animation.
  - **Problem:** Uses static weights; lacks animation for segments and lacks a shimmer/loading visual when calculating.
  - **Location:** `HomeScreen.kt`, `StorageDashboardScreen.kt`
  - **Fix:** Wrap fractions in `animateFloatAsState` and add shimmer.

- [ ] [UI/UX] Loading State Inconsistency.
  - **Problem:** `RecentFilesScreen` ignores `isLoading` flag, looking frozen during fetches.
  - **Location:** `RecentFilesScreen.kt`
  - **Fix:** Display `LoadingIndicator` when state is loading.

- [ ] [UI/UX] Empty State Inconsistency.
  - **Problem:** `RecentFilesScreen` has minimal text-only empty state, lacking an icon compared to other screens.
  - **Location:** `RecentFilesScreen.kt`
  - **Fix:** Add a consistent icon (e.g. `Icons.Default.History`) matching other empty states.

- [ ] [UX] Missing adaptive layouts for large screens.
  - **Problem:** Root composables force narrow vertical lists regardless of window width.
  - **Location:** `HomeScreen.kt`, `FileManagerScreen.kt`
  - **Fix:** Implement `WindowSizeClass` checks and use dual-pane layouts for expanded widths.

- [ ] [Motion] No loading/skeleton states — content jumps in after load.
  - **Problem:** Home screen shows a centered `LoadingIndicator` then snaps to full content. No progressive loading or shimmer.
  - **Location:** `HomeScreen.kt:148-157`
  - **Fix:** Add shimmer/skeleton placeholders for the storage card, categories, and recent files sections.

- [ ] [Motion] Navigation between Explorer and sub-folders has no folder-specific transition.
  - **Problem:** Global slide-in transitions are used. Folder-to-folder navigation within the same route has no transition.
  - **Location:** `ArcileAppShell.kt`, ViewModels
  - **Fix:** Add a crossfade or shared-element transition for the file list when changing directories.

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

- [ ] [Anomaly] `Kotlin 2.2.10` version may be unreleased/preview.
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

## 5. Completed Tasks

- [x] [Refactor] Split `FileManagerScreen.kt` into modular components. *(File length reduced from ~1000 to ~655 lines)*
- [x] [Architecture] `FileManagerViewModel` god-ViewModel was split. *(Now broken into Home, Browser, RecentFiles, and Trash ViewModels)*
- [x] [Architecture] No dependency injection framework. *(Hilt implemented via `di/RepositoryModule.kt`)*
- [x] [Refactor] Offload logic from `BrowserViewModel.kt`.
- [x] [Security] Release keystore file committed to version control. *(Verified `app/my-release-key.jks` is ignored in `.gitignore`)*

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