# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.3.3
> **Last Updated:** 2026-03-11

---

### A. Correctness & Reliability

- [ ] [Bug] `FileManagerViewModel` state loss on process death.
  - **Problem:** ViewModel state (`currentPath`, `isHomeScreen`, `isTrashScreen`, `isRecentFilesScreen`) is not persisted across process deaths.
  - **Location:** `FileManagerViewModel.kt` — `FileManagerState` data class
  - **Impact:** User loses navigation position when system kills app in background.
  - **Fix:** Inject `SavedStateHandle` and persist critical nav state fields.

- [ ] [Bug] `pathHistory` (back-stack) is lost on process death.
  - **Problem:** `ArrayDeque<String>` for navigation history is an in-memory field with no persistence.
  - **Location:** `FileManagerViewModel.kt:66`
  - **Impact:** Pressing back after process death does nothing or navigates incorrectly.
  - **Fix:** Serialize `pathHistory` into `SavedStateHandle` or switch to Navigation Compose's built-in stack.

- [ ] [Bug] `navigateBack()` returns `false` when `pathHistory` is empty but not on home screen.
  - **Problem:** When history is empty, the function sets `isHomeScreen = true` but returns `false`, which causes `ArcileAppShell` to also call `navController.popBackStack()`.
  - **Location:** `FileManagerViewModel.kt:152-154`
  - **Impact:** Double-back navigation; user may unintentionally exit the app.
  - **Fix:** Return `true` when transitioning to home, or delegate entirely to Navigation Compose.

- [ ] [Bug] Dual navigation state: ViewModel flags vs NavController back stack are unsynchronized.
  - **Problem:** `isHomeScreen`, `isTrashScreen`, `isRecentFilesScreen` in ViewModel duplicate the route tracked by `NavController`. If either is changed independently, they diverge.
  - **Location:** `FileManagerViewModel.kt:32-54`, `ArcileAppShell.kt`
  - **Impact:** Possible inconsistent UI state after configuration changes or deep-linking.
  - **Fix:** Use Navigation Compose route as the single source of truth. Derive the ViewModel flags from the current route, or remove them entirely.

- [x] [Bug] Trash restore data corruption risk: orphaned metadata silently deleted.
  - **Problem:** In `getTrashFiles()`, orphaned metadata files (where the trashed blob is missing) are deleted without warning or logging.
  - **Location:** `LocalFileRepository.kt:597-600`
  - **Impact:** If a trash blob is accidentally missing, user loses all record of the file.
  - **Fix:** Log a warning instead of silently deleting. Consider exposing a UI indicator for corrupted items.

- [ ] [Bug] `copyFiles()` overwrites destination files without user confirmation.
  - **Problem:** `sourceFile.copyRecursively(targetFile, overwrite = true)` silently replaces existing files at the destination.
  - **Location:** `LocalFileRepository.kt:401-404`
  - **Impact:** Data loss when pasting to a folder that already contains files with the same name.
  - **Fix:** Check for existence and prompt user (keep both, replace, skip).

- [x] [Bug] `moveFiles()` copy+delete fallback does not revert on partial failure.
  - **Problem:** If `copyRecursively` succeeds but `deleteRecursively` fails, data exists in two places with no cleanup.
  - **Location:** `LocalFileRepository.kt:436-442`
  - **Impact:** Inconsistent file state; user may not realize duplicates exist.
  - **Fix:** Wrap in try/catch and revert the copy on delete failure, or report a partial-failure error.

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

- [x] [Security] Trash directory (`.arcile_trash`) stored on shared external storage with no encryption.
  - **Problem:** "Deleted" files are simply renamed and moved to `/storage/emulated/0/.arcile_trash/`. Any app with `MANAGE_EXTERNAL_STORAGE` can read them.
  - **Location:** `LocalFileRepository.kt:453-460`
  - **Impact:** Sensitive files are accessible after "deletion". The `.nomedia` only hides from gallery, not from other file managers.
  - **Fix:** Document this limitation. For sensitive data, consider using app-private storage or encryption.

- [ ] [Security] `MANAGE_EXTERNAL_STORAGE` permission has no graceful degradation.
  - **Problem:** The app requests `MANAGE_EXTERNAL_STORAGE` on Android 11+ but provides no fallback if the user denies it.
  - **Location:** `AndroidManifest.xml:7`, `MainActivity.kt:159-178`
  - **Impact:** App is completely blocked if the user denies — no partial-access mode via SAF.
  - **Fix:** Implement SAF fallback for granular per-folder access.

---

### C. Performance & Resource Efficiency

- [x] [Performance] `getRecentFiles()` queries MediaStore with `limit = Int.MAX_VALUE`.
  - **Problem:** On home screen load, `loadHomeData()` requests `getRecentFiles(limit = Int.MAX_VALUE, minTimestamp = oneWeekAgo)`. On devices with thousands of recent files, this creates a massive list in memory.
  - **Location:** `FileManagerViewModel.kt:78`
  - **Impact:** High memory usage and slow home screen load on devices with many files.
  - **Fix:** Use a reasonable limit (e.g., 100) for the home screen preview. Only fetch all files for the dedicated Recent Files screen.

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

- [x] [Performance] `FileModel` constructor triggers I/O in default parameter expressions.
  - **Problem:** `val size = if (file != null && file.isFile) file.length() else 0L` and `file?.lastModified()` perform filesystem I/O at construction time.
  - **Location:** `FileModel.kt:5-14`
  - **Impact:** Constructing `FileModel(File(...))` anywhere triggers synchronous disk reads.
  - **Fix:** Remove the `file` parameter and require explicit values, or compute lazily.

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

- [x] [Accessibility] No `contentDescription` on file item rows.
  - **Problem:** `FileItemRow` and `FileGridItem` have no semantic description for the entire clickable unit.
  - **Location:** `FileManagerScreen.kt:620-683`, `FileManagerScreen.kt:685-768`
  - **Impact:** TalkBack users get fragmented information instead of "file name, size, date".
  - **Fix:** Add `Modifier.semantics { contentDescription = "..." }` or use `ListItem` semantics properly.

- [x] [Accessibility] Category items lack proper touch target sizes.
  - **Problem:** `CategoryItem` icons are 64dp but the clickable area is not guaranteed to meet the 48dp minimum accessibility guideline in all cases.
  - **Location:** `HomeScreen.kt:553-614`
  - **Impact:** Potential touch target issues on high-density screens.
  - **Fix:** Ensure minimum 48dp clickable area via `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`.

- [x] [UX] `ToolsScreen` items are not clickable (except Trash Bin).
  - **Problem:** All tool cards except "Trash Bin" have no `onClick` handler or show "Coming Soon" but are still visually identical to actionable cards.
  - **Location:** `ToolsScreen.kt:73-74`, `ToolCard.kt:38`
  - **Impact:** User taps a tool card and nothing happens — confusing.
  - **Fix:** Visually distinguish unimplemented tools (e.g., reduce opacity, add a badge) and/or prevent click events.

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

### I. App Smoothness & Rendering Stability

- [x] [Smoothness] `remember {}` around `SimpleDateFormat` is not keyed — creates a new formatter on each recomposition in some cases.
  - **Problem:** `RecentFilesScreen.kt:48` uses `val formatter = SimpleDateFormat(...)` outside `remember` — a new instance every recomposition.
  - **Location:** `RecentFilesScreen.kt:48`
  - **Impact:** Minor GC pressure during scrolling.
  - **Fix:** Wrap in `remember { }`.

- [x] [Smoothness] `animateContentSize()` on `StorageSummaryCard` column may cause layout flickering.
  - **Problem:** `Column(modifier = Modifier.padding(24.dp).animateContentSize())` animates size for content that doesn't change dynamically.
  - **Location:** `HomeScreen.kt:331`
  - **Impact:** Unnecessary animation measurement on every composition; potential flicker if storage data arrives asynchronously.
  - **Fix:** Remove `animateContentSize()` unless needed for dynamic content changes.

---

### J. Documentation Quality

- [x] [Docs] `README.md` version is outdated (references v0.2.3, actual version is v0.3.0).
  - **Location:** `README.md`, `TASKS.md` header
  - **Impact:** Confusing for contributors checking out the project.
  - **Fix:** Keep version references consistent with `build.gradle.kts`.

- [x] [Docs] No developer onboarding or build instructions.
  - **Problem:** `README.md` does not include how to clone, configure signing, or build the app.
  - **Location:** `README.md`
  - **Impact:** New contributors cannot build the project without reading Gradle files.
  - **Fix:** Add a "Getting Started" section with clone, signing config, and build/run commands.

- [x] [Docs] `DEVELOPMENT.md` exists but may not reflect current architecture.
  - **Problem:** `DEVELOPMENT.md` is 19KB but needs verification against the actual codebase structure.
  - **Location:** `DEVELOPMENT.md`
  - **Impact:** Potentially misleading documentation.
  - **Fix:** Review and update to match current file structure, architecture, and conventions.

- [x] [Docs] No KDoc/documentation on public API surfaces.
  - **Problem:** `FileRepository`, `FileModel`, `FileManagerViewModel`, and all screen composables have zero documentation comments.
  - **Location:** All `domain/` and `presentation/` files
  - **Impact:** Contributors must read implementation to understand contracts.
  - **Fix:** Add KDoc to all public interfaces, data classes, and top-level composables.

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
- **Smart Paste Conflict Resolution**: When pasting files to a destination that already contains items with the same name, show a conflict resolution dialog with side-by-side comparison of the new and existing files (thumbnail, name, size, date). Options include:
  - **Keep Both** — auto-rename the incoming file (e.g., `photo (1).jpg`)
  - **Replace** — overwrite the existing file with the new one
  - **Skip** — skip this file and continue
  - **Compare** — open a side-by-side detail view for manual decision
  - **Remember** — apply the chosen action to all remaining conflicts in this batch

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