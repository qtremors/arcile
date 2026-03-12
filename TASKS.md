# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.3.4
> **Last Updated:** 2026-03-11

---

### 0. Modularity & Manageability

- [ ] [Refactor] Split `FileManagerScreen.kt` (1046 lines) into modular components.
  - **Problem:** Massive "God Composable" mixing scaffold, state, lists, grids, and dialogs.
  - **Location:** `FileManagerScreen.kt`
  - **Fix:** Extract `CreateFolderDialog`, `CreateFileDialog`, `RenameDialog`, `FileList`, `FileGrid`, `ExpandableFabMenu`, and `ActiveFiltersRow` into specific files under `components/`.

- [ ] [Refactor] De-monolith `LocalFileRepository.kt` (736 lines).
  - **Problem:** Handles basic CRUD, MediaStore categories, Trash subsystem, and Copy/Move conflict resolution all in one file, violating SRP.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Extract Trash logic to `TrashRepository`. Extract MediaStore queries to `MediaStoreDataSource` or `CategoryRepository`. Extract copy/move logic to `FileTransferHandler`.

- [ ] [Refactor] Modularize `HomeScreen.kt` composables (663 lines).
  - **Problem:** Embeds local composables for storage UI and category grid.
  - **Location:** `HomeScreen.kt`
  - **Fix:** Move `StorageSummaryCard`, `MultiColorStorageBar`, `CategoryLegend`, `CategoryGrid`, and `MainFoldersGrid` to `components/`.

- [ ] [Refactor] Offload logic from `BrowserViewModel.kt` (495 lines).
  - **Problem:** ViewModel acts as a God object handling navigation, file loading, clipboard, trash, error handling, and search.
  - **Location:** `BrowserViewModel.kt`
  - **Fix:** Extract isolated UseCases (e.g., `ExecutePasteUseCase`, `MoveToTrashUseCase`) and move pure state formatting to helper extensions.

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
  - **Fix:** Extract `ConflictCard` and `FileInfoColumn` into smaller, reusable pieces if possible, or move to `components/dialogs/`.

- [ ] [Refactor] Modularize Color Themes in `Color.kt` (351 lines).
  - **Problem:** Contains hardcoded definitions for 10+ distinct dynamic and static color schemes in one massive file.
  - **Location:** `ui/theme/Color.kt`
  - **Fix:** Split palettes by core hue (e.g., `theme/colors/BluePalettes.kt`, `theme/colors/MonochromePalettes.kt`) or generate them algorithmically.

- [ ] [Refactor] Clean up `TrashScreen.kt` (257 lines).
  - **Problem:** Mixes the scaffold, empty state logic, alert dialogs, and the main lazy list.
  - **Location:** `TrashScreen.kt`
  - **Fix:** Move `TrashList` and the Empty Trash AlertDialog to separate composables in `components/trash/`.

---

### B. Security

- [ ] [Security] Release keystore file committed to version control.
  - **Problem:** `app/my-release-key.jks` is checked into the repository.
  - **Location:** `arcile-app/app/my-release-key.jks`
  - **Impact:** Critical — anyone with repo access can sign APKs as the developer.
  - **Fix:** Add `*.jks` to `.gitignore`, remove from Git history (BFG or filter-branch), rotate the key.

- [ ] [Security] Signing credentials may be in `local.properties` which is in `.gitignore`, but the pattern is fragile.
  - **Problem:** Keystoe password, key alias, and key password are read from `local.properties` at build time. If `local.properties` is accidentally committed, secrets are exposed.
  - **Location:** `app/build.gradle.kts:26-32`
  - **Impact:** Credential exposure risk.
  - **Fix:** Use environment variables or a dedicated CI secrets mechanism instead of a local file.

- [ ] [Security] `MANAGE_EXTERNAL_STORAGE` permission has no graceful degradation.
  - **Problem:** The app requests `MANAGE_EXTERNAL_STORAGE` on Android 11+ but provides no fallback if the user denies it.
  - **Location:** `AndroidManifest.xml:7`, `MainActivity.kt:159-178`
  - **Impact:** App is completely blocked if the user denies — no partial-access mode via SAF.
  - **Fix:** Implement SAF fallback for granular per-folder access.

---

### C. Performance & Resource Efficiency

- [ ] [Performance] `getCategoryStorageSizes()` scans the entire MediaStore without filtering.
  - **Problem:** Queries all files from MediaStore to compute per-category sizes. On devices with 100K+ indexed files, this is very slow.
  - **Location:** `LocalFileRepository.kt:212-255`
  - **Impact:** Slow home screen load; blocks UI data until complete.
  - **Fix:** Use `GROUP BY` SQL aggregation on MIME types in the ContentResolver query, or cache results with a time-based invalidation.

- [ ] [Performance] `getFilesByCategory()` loads all MediaStore entries then filters client-side.
  - **Problem:** `SELECT DATA FROM files` with no selection clause, then filtering by extension in Kotlin.
  - **Location:** `LocalFileRepository.kt:265-280`
  - **Impact:** Loads the entire MediaStore index into memory when browsing a category.
  - **Fix:** Use a `LIKE` or `IN` clause on `MIME_TYPE` in the MediaStore query.

- [ ] [Performance] Scoped `searchFiles()` uses `File.walkTopDown()` — recursive filesystem traversal on the main thread.
  - **Problem:** Although wrapped in `Dispatchers.IO`, walking the entire filesystem tree for a scoped search is inherently slow and produces an unbounded list.
  - **Location:** `LocalFileRepository.kt:297-305`
  - **Impact:** Search in the root directory can take 10+ seconds and allocate many `FileModel` objects.
  - **Fix:** Cap results (e.g., first 200 matches), add cancellation checks in the walk, or use MediaStore for scoped search too.

- [ ] [Performance] `LazyColumn` / `LazyVerticalGrid` items re-keyed by `absolutePath` may conflict.
  - **Problem:** Multiple files from different directories could have the same `absolutePath` key if the list content changes during recomposition (e.g., during search switching).
  - **Location:** `FileManagerScreen.kt:531`, `HomeScreen.kt:287`
  - **Impact:** Potential recomposition issues and key collisions.
  - **Fix:** Validate key uniqueness or include additional context (e.g., directory path prefix).

- [ ] [Performance] `AudioAlbumArtFetcher` opens `MediaMetadataRetriever` on the UI thread call path.
  - **Problem:** Coil dispatches fetchers, but `MediaMetadataRetriever.setDataSource()` can be slow on some devices.
  - **Location:** `AudioAlbumArtFetcher.kt:20-22`
  - **Impact:** Minor jank when scrolling lists with many audio files.
  - **Fix:** Ensure Coil's dispatcher configuration routes this to an IO thread (it likely does, but verify).

---

### D. Architecture & Design Quality

- [ ] [Architecture] Hardcoded Android framework dependencies in ViewModel.
  - **Problem:** `FileManagerViewModel` initializes `storageRootPath` via `Environment.getExternalStorageDirectory()`.
  - **Location:** `FileManagerViewModel.kt:68`
  - **Impact:** Makes the ViewModel impossible to unit test without Robolectric.
  - **Fix:** Inject `storageRootPath` via constructor or retrieve it via the Repository.

- [ ] [Architecture] `FileManagerViewModel` is a 515-line god-ViewModel.
  - **Problem:** Handles home data, file browsing, search, clipboard, trash, selection, and navigation for 7+ screens.
  - **Location:** `FileManagerViewModel.kt`
  - **Impact:** Very hard to test, maintain, and extend. Adding any feature grows this class further.
  - **Fix:** Split into feature-scoped ViewModels: `HomeViewModel`, `BrowserViewModel`, `TrashViewModel`, `SearchViewModel`.

- [ ] [Architecture] No dependency injection framework.
  - **Problem:** Repositories, preferences, and ViewModels are manually wired. `LocalFileRepository` is instantiated inside `FileManagerViewModel`.
  - **Location:** `FileManagerViewModel.kt:61`
  - **Impact:** Impossible to swap implementations, difficult to test, tight coupling.
  - **Fix:** Adopt Hilt (or Koin) for constructor injection across the app.

- [ ] [Architecture] `ShareSelectedFiles()` in ViewModel creates an `Intent` — Android framework concern.
  - **Problem:** ViewModel directly constructs and launches `Intent` and calls `context.startActivity()`.
  - **Location:** `FileManagerViewModel.kt:403-436`
  - **Impact:** Violates separation of concerns; ViewModel now has Activity-level side effects.
  - **Fix:** Expose a `shareEvent` flow/channel and handle the Intent in the Activity or a dedicated use case.

- [ ] [Architecture] `HomeScreen.kt` directly accesses `Environment.getExternalStorageDirectory()`.
  - **Problem:** `MainFoldersGrid` composable directly reads from `Environment`, embedding Android framework knowledge in the UI layer.
  - **Location:** `HomeScreen.kt:621`
  - **Impact:** Untestable, non-previewable composable.
  - **Fix:** Pass folder paths from ViewModel or a provider.

- [ ] [Architecture] Navigation routes use string constants — no type-safe navigation.
  - **Problem:** `AppRoutes` uses `const val` strings and direct `navController.navigate()` calls.
  - **Location:** `AppRoutes.kt`, `ArcileAppShell.kt`
  - **Impact:** Refactoring risk; no compile-time route validation; no arguments type checking.
  - **Fix:** Migrate to Navigation Compose type-safe routes (Kotlin serialization-based route classes).

---

### E. Maintainability & Code Quality

- [ ] [Code Quality] `FileManagerScreen.kt` is 985 lines.
  - **Problem:** Single file contains the main screen, file list, file grid, dialogs, FAB menu, and filter row.
  - **Location:** `FileManagerScreen.kt`
  - **Impact:** Hard to navigate, review, and maintain.
  - **Fix:** Extract dialogs, FAB menu, and filter row into separate files in `components/`.

- [ ] [Code Quality] `ThemePreferences` instantiated inside `setContent{}` with `remember`.
  - **Problem:** `val themePreferences = remember { ThemePreferences(applicationContext) }` creates a non-lifecycle-aware instance inside composition.
  - **Location:** `MainActivity.kt:79`
  - **Impact:** If the Activity is recreated, a new `DataStore` flow is created, potentially racing with the old one.
  - **Fix:** Hoist `ThemePreferences` to the Activity level or inject via DI.

- [ ] [Code Quality] No unit tests exist.
  - **Problem:** `ExampleInstrumentedTest.kt` is the only test file — it's the default template.
  - **Location:** `app/src/androidTest/`
  - **Impact:** Zero test coverage; regressions are undetectable.
  - **Fix:** Add unit tests for `FileManagerViewModel`, `LocalFileRepository`, `filterAndSortFiles`, and domain logic.

---

### F. UI / UX & Accessibility

- [ ] [UX] Missing adaptive layouts for large screens.
  - **Problem:** Root composables force narrow vertical lists regardless of window width.
  - **Location:** `HomeScreen.kt`, `FileManagerScreen.kt`
  - **Impact:** Poor screen real estate utilization on tablets and foldables.
  - **Fix:** Implement `WindowSizeClass` checks and use dual-pane layouts for expanded widths.

---

### G. Material Design 3 Expressive Implementation

- [ ] [Design] Custom color schemes hardcoded instead of using Material Color Utilities.
  - **Problem:** All accent color schemes (Blue, Cyan, Green, Red, Purple) are manually defined with hardcoded hex colors rather than generated from seed colors via HCT.
  - **Location:** `Color.kt:64-302`
  - **Impact:** Tonal palettes may not pass WCAG contrast checks; no guarantee of accessibility mapping.
  - **Fix:** Use the `material-color-utilities` library to generate `ColorScheme` from seed color at runtime. The current approach is acceptable if the values were exported from the Material Theme Builder.


- [ ] [Design] `ExpressiveSquircleShape` is a simple `RoundedCornerShape(28.dp)`, not a true squircle.
  - **Problem:** A squircle (superellipse) has continuously changing curvature, while `RoundedCornerShape` uses circular arc corners.
  - **Location:** `Shape.kt:19`
  - **Impact:** Visual discrepancy with true M3 Expressive squircle shapes.
  - **Fix:** Use `RoundedCornerShape` with `CornerSize` percent or implement a custom `CutoutShape`. Alternatively, accept this as a reasonable approximation. Note: Compose does not natively support superellipse shapes.

---

### H. Interaction Quality & Motion Design

- [ ] [Motion] No loading/skeleton states — content jumps in after load.
  - **Problem:** Home screen shows a centered `LoadingIndicator` then snaps to full content. No progressive loading or shimmer.
  - **Location:** `HomeScreen.kt:148-157`
  - **Impact:** Abrupt visual change; poor perceived performance.
  - **Fix:** Add shimmer/skeleton placeholders for the storage card, categories, and recent files sections.

- [ ] [Motion] Navigation between Explorer and sub-folders has no folder-specific transition.
  - **Problem:** The global slide-in/slide-out transitions are used for all routes including folder navigation within the Explorer, but folder-to-folder navigation within the same route has no transition.
  - **Location:** `FileManagerViewModel.kt:123-128` (same-route nav), `ArcileAppShell.kt:57-60`
  - **Impact:** Folder-to-folder navigation feels abrupt — content just swaps.
  - **Fix:** Add a crossfade or shared-element transition for the file list when changing directories.

---

### General Anomalies

- [ ] [Anomaly] Compose BOM version `2024.09.00` conflicts with explicit Material3 `1.4.0-alpha08`.
  - **Problem:** The BOM pins Compose libraries to Sept 2024 versions, but Material3 is overridden to a much newer alpha. This can cause binary incompatibilities.
  - **Location:** `libs.versions.toml:10,16`, `app/build.gradle.kts:86,90`
  - **Impact:** Potential runtime crashes or API mismatches between Compose foundation and Material3.
  - **Fix:** Either update the BOM to match the M3 alpha requirement, or pin all Compose libraries to compatible versions.

- [ ] [Anomaly] `compileSdk` uses non-standard block syntax with `minorApiLevel`.
  - **Problem:** `compileSdk { version = release(36) { minorApiLevel = 1 } }` is an unusual AGP 9.x API. Most projects use `compileSdk = 36`.
  - **Location:** `app/build.gradle.kts:10-14`
  - **Impact:** Surprising to contributors; version clarity is reduced.
  - **Fix:** Document this in `DEVELOPMENT.md` if this is intentional (e.g., to target a specific platform release).

- [ ] [Anomaly] `VariantOutputImpl` cast in `androidComponents` block.
  - **Problem:** Casting to `com.android.build.api.variant.impl.VariantOutputImpl` is an internal API that may break with AGP updates.
  - **Location:** `app/build.gradle.kts:64-73`
  - **Impact:** Build failure on future AGP versions.
  - **Fix:** Check if a stable API for `outputFileName` exists in AGP 9.x, or accept the risk with a pinned AGP version.

- [ ] [Anomaly] `Kotlin 2.2.10` version may be unreleased/preview.
  - **Problem:** Kotlin `2.2.10` in the version catalog — Kotlin version numbering typically uses `2.x.y` where `y` is 0-20ish.
  - **Location:** `libs.versions.toml:9`
  - **Impact:** May require special repository configuration; contributors may not be able to resolve it.
  - **Fix:** Verify this is an official release and document any special repository requirements.

---

## Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

---

### Storage & Access

- **SAF (Storage Access Framework) Fallback**: Integrate SAF as a fallback when `MANAGE_EXTERNAL_STORAGE` is denied, enabling granular per-folder access on Android 11+ without requiring the all-files permission.
- **SD Card & USB OTG Support**: Detect and browse removable storage volumes (SD cards, USB drives) alongside internal storage, with proper mount/unmount lifecycle handling.
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.

---

### File Operations

- **File/Folder Properties Dialog**: Display detailed metadata for selected items — size (with recursive calculation for folders), MIME type, permissions, creation/modification dates, and absolute path.
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser, with progress indication and cancellation.

---

### Home Screen Enhancements

- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally-scrollable carousel of the 10 most recently modified images and videos. Each card should show a rounded thumbnail with the file name overlaid at the bottom, a three-dot overflow menu in the top-right corner, and a "See all" link that navigates to the full Recent Files screen. Style inspired by Google Files.
- **Customizable Quick Access Folders**: Allow users to pin/unpin folders to the Quick Access section on the Home screen, replacing the current hardcoded folder shortcuts (DCIM, Downloads, etc.) with user-selectable favorites.
- **Folder Subtitle Metadata**: Display a compact subtitle below each folder name showing the item count and total size (e.g., `24 items · 1.3 GB`) along with the last modified date.

---

### Browsing & Organization

- **Starred / Favorited Files**: Add a "Starred" section to the Home screen and a star toggle on individual files/folders, persisted locally via DataStore or Room, for quick access to frequently used items.
- **Enhanced Category Browsing**: When opening a file category (e.g., Images, Videos), display all related folders containing matching files with a tabbed or segmented navigation bar, allowing users to browse by folder rather than a flat file list.
- **Folder-Level Storage Dashboard**: Add an option in the Storage Dashboard to view storage consumption broken down by top-level folders, showing each folder's total size and percentage of used space — useful for identifying space hogs.

---

### Multi-Window & Layout

- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android's multi-window mode, with proper layout reflow and no state loss when entering/exiting split-screen.

---

### Security & Privacy

- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders.
  - Authenticate via PIN/password and optional biometric unlock.
  - Send files directly from the file browser into the vault.
  - First-time setup flow to configure vault location and master credentials.
  - Full file operations (create, rename, delete, move) within the vault.
  - Vault contents are encrypted at rest using AES-256 and inaccessible to other apps.