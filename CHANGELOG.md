# Arcile Changelog

> **Project:** Arcile
> **Version:** 0.3.6
> **Last Updated:** 2026-03-13

---

## [0.3.6] - 2026-03-13

### Fixed
- [Bug] "Recent Files" list failed to float newly copied duplicate items to the top; fixed by primarily querying `MediaStore.Files.FileColumns.DATE_ADDED` in `LocalFileRepository.getRecentFiles` with a secondary `DATE_MODIFIED` fallback.
- [Bug] "Recent Files" list reported 0-bytes for freshly duplicated files because MediaStore asynchronous indexation lagged behind execution; implemented a native `java.io.File(path).length()` bypass.

### Improved
- [Architecture] Extracted massive `FileManagerScreen.kt` "God Composable" logic into hyper-specific isolated modular components under `presentation/ui/components/` including dialogs, lists, grids, and menus for drastically improved maintainability.

---

## [0.3.5] - 2026-03-13

### Added
- [Feature] Persistent Sorting Filters: File browsing sorting preferences are now persisted per directory or globally via DataStore.
- [Feature] Subfolder Sorting Application: Users can now explicitly choose to apply a sorting filter dynamically to a selected folder and all its subfolders directly from the Sort option dialog.
- [Feature] Category Sort Defaults: The categories on the home screen now automatically configure sorting to "Date Newest" instead of alphabetically by default. 

### Improved
- [UX] Process death states explicitly re-apply "Date Newest" sort configuration cleanly when recovering active Category screens.

---

## [0.3.4] - 2026-03-11

### Added
- [Feature] Smart Paste Conflict Resolution: Built a robust, step-by-step conflict dialog to handle copy/move filename collisions gracefully instead of silently overwriting user data.
- [Feature] Smart Paste for Folders: Dynamically restyled conflict UI explicitly for directories, replacing generic "Replace" actions with a precise "Merge" paradigm.
- [Feature] Contextual Batch Processing: Added intuitive "Do this for all remaining conflicts" checkbox for rapid ingestion of large paste operations.
- [Feature] Intelligent Auto-Renaming: Resolving a conflict via "Keep Both" now automatically increments file suffixes recursively (e.g., `(1)`, `(2)`) instead of polluting the name with generic words.

### Fixed
- [Bug] SavedStateHandle: ViewModels now persist navigation stack (`pathHistory`) and current active directory across process death natively, securing user state against sudden OS terminations.
- [Bug] Corrected `navigateBack()` returning boolean values incorrectly when attempting to traverse backwards up a deeply-linked directory node on a fresh launch process.
- [Bug] Deprecated duplicate boolean state checking `isCategoryScreen` looping across the `NavController` recompositions. The deep link intent is now cleanly pushed to initialization inside `BrowserViewModel` via `SavedStateHandle` parameters, enforcing a single source of truth.
- [Bug] `copyFiles()` overwrites destination files without user confirmation; fixed via new Smart Paste flow.
- [Bug] Infinite Recursion File-System loop: prevented users from pasting a parent directory inside of its own child folder.
- [Bug] Identical same-folder paste operations triggered duplicates instead of acting gracefully as an atomic no-op.

---

## [0.3.3] - 2026-03-11

### Fixed
- [Bug] Trash restore data corruption risk: orphaned metadata implicitly handled and skipped gracefully.
- [Bug] `moveFiles()` copy+delete fallback is now atomic and triggers a complete rollback on partial failure.

### Improved
- [Security] Added internal KDoc highlighting that the `.arcile_trash` vault on shared external storage natively lacks encryption.
- [Performance] Verified `FileModel` constructor skips inherently expensive disk I/O operations natively via default values.
- [Performance] `getRecentFiles()` stops blindly querying the MediaStore unconditionally by instituting hard limits per feature view.
- [Accessibility] Added rich `contentDescription` semantics to both `FileItemRow` and `FileGridItem` allowing precise TalkBack announcements.
- [Accessibility] Adjusted Home Screen categorized shortcut elements to universally respect minimal 48dp interaction touch targets.
- [UX] `ToolsScreen` and identical components now properly restrict click behaviors and dynamically dim opacity for uninitialized capabilities.
- [Smoothness] Removed redundant `SimpleDateFormat` recreations triggering costly UI Thread Garbage Collection sweeps in `RecentFilesScreen`.
- [Smoothness] Stripped problematic layout shifting flags (`animateContentSize`) off the `StorageSummaryCard` stopping native redraw jittering.

---

## [0.3.2] - 2026-03-11

### Refactored
- [Architecture] `FileModel` domain model decoupled from `java.io.File`, preventing implicit I/O operations and allowing proper serialization/parceling without leaking data layer concerns.
- [Architecture] Introduced Hilt Dependency Injection framework. Replaced direct instantiation of `LocalFileRepository` inside ViewModels with constructor injection, massively improving testability.
- [Architecture] Split monolith `FileManagerViewModel` into four feature-scoped view models: `HomeViewModel`, `BrowserViewModel`, `TrashViewModel`, and `RecentFilesViewModel` to enhance performance and code maintainability.

### Fixed
- [Bug] Clipboard operations failed or behaved unreliably due to UI recompositions; states are now correctly hoisted within a dedicated `ClipboardState` tied uniquely to `BrowserViewModel`.
- [Bug] Share operations failed out-of-context for `RecentFilesScreen`; added `shareSelectedFiles` capability directly to the `RecentFilesViewModel` to fix intent drops.
- [Bug] `FileManagerScreen` crashed due to missing Opt-In annotations for modern Compose 1.4+ Shared Transitions.

### Improved
- [Component] Removed monolithic `FileManagerState`. Each feature screen (`HomeScreen`, `StorageDashboardScreen`, etc.) now strictly binds to precisely tailored isolated states (`HomeState`, `TrashState`, etc.).

---

## [0.3.1] - 2026-03-11

### Fixed
- [Bug] `SearchFiltersBottomSheet`: Date filter chip selected-state checks now use live time-window range comparison instead of exact equality against a stale `remember { System.currentTimeMillis() }` — chips correctly highlight after the sheet is closed and reopened.
- [Bug] `FileManagerScreen`: Long-press range-selection anchor `lastInteractedIndex` is now reset to `null` whenever the file list changes (directory navigation, sort change), preventing stale-index out-of-bounds errors and incorrect batch selections in both list and grid views.
- [Bug] `RecentFilesScreen`: System back gesture now clears selection mode (via `BackHandler`) instead of navigating away with `selectedFiles` still populated.
- [Bug] `RecentFilesScreen`: Delete action now shows a confirmation `AlertDialog` ("items will be moved to Trash Bin") before invoking `onDeleteSelected()`, matching the behavior in `FileManagerScreen`.
- [Bug] `AudioAlbumArtFetcher`: `return@try null` replaced with `return null` — `try` is not a valid label target in Kotlin; the `finally { retriever.release() }` block still executes correctly.
- [Bug] `FileManagerViewModel`: `isPullToRefreshing` state now exclusively drives the pull-to-refresh indicator; general `isLoading` no longer conflated with the swipe gesture.
- [Bug] `FileManagerViewModel`: `navigateBack()` now returns `true` after switching state to `isHomeScreen`, preventing the `NavController` from also popping the back stack (double-navigation).
- [Bug] `FileManagerViewModel`: `refresh()` now routes to the correct loader for each screen type (home, trash, recent files, category, directory browser).
- [Bug] `FileManagerViewModel`: `debouncedSearch()` uses `pathScope = null` (MediaStore-wide) on home, category, and recent screens; scopes to `currentPath` only when inside a directory browser.
- [Bug] `LocalFileRepository`: Copy/move operations now reject ancestor targets, preventing infinite filesystem loops.
- [Bug] `LocalFileRepository`: `restoreFromTrash` is non-destructive — if the restore target already exists a conflict-safe filename is generated instead of overwriting.
- [Bug] `LocalFileRepository`: `searchFiles` validates `pathScope` and prunes directories outside the storage root during recursive walks.

### Improved
- [A11y] `SettingsScreen`: Theme mode card rows now use `Modifier.selectableGroup()` and each card exposes `Modifier.selectable(role = Role.RadioButton)`, allowing TalkBack to announce them as an exclusive radio group.
- [A11y] `SettingsScreen`: Accent color label replaced `currentAccent.name` (raw enum constant) with a friendly `accentLabel()` helper returning "Dynamic", "Monochrome", "Blue", etc.

### Added
- [Theme] `AccentColor.MONOCHROME` now resolves to dedicated `MonochromeLightScheme` / `MonochromeDarkScheme` grayscale color schemes instead of falling through to the purple default.
- [Security] `file_provider_paths.xml`: Added root `external-path path="/"` entry so `FileProvider.getUriForFile()` succeeds for any file on external storage, not just the ten named standard folders.

### Docs
- [KDoc] `FileRepository.deleteFile` KDoc corrected to describe soft-delete (trash) behavior rather than permanent deletion.

---

## [0.3.0] - 2026-03-11

### Fixed
- [Bug] `loadHomeData()` no longer triggers double recomposition — recent files, storage info, and category sizes are now merged into a single `_state.update` call.
- [Bug] `SimpleDateFormat` instance in `RecentFilesScreen` is now wrapped in `remember` to prevent per-recomposition GC pressure.

### Improved
- [UX] Error messages now display as non-blocking `Snackbar` notifications at the bottom of the screen, replacing the modal `AlertDialog` that forced user interaction before retrying.
- [UX] Clipboard copy/cut operations now show feedback via `Snackbar`: "N item(s) copied to clipboard" or "N item(s) cut to clipboard".
- [Motion] FAB expansion menu now shows a dismissible scrim overlay — tapping outside the expanded menu closes it cleanly.
- [Motion] File selection padding transition (0dp → 8dp) is now smoothly animated via `animateDpAsState` instead of an abrupt layout jump.
- [Architecture] Category color mapping (`getCategoryColor()`) centralized into `utils/CategoryColors.kt`, replacing 3 duplicated `when` blocks across `HomeScreen.kt` and `StorageDashboardScreen.kt`.

### Changed
- [Naming] `deleteSelectedFiles()` renamed to `moveSelectedToTrash()` in `FileManagerViewModel` to accurately reflect the trash-based deletion pipeline.

### Removed
- [Security] Removed `android:requestLegacyExternalStorage="true"` from `AndroidManifest.xml` — ignored on Android 11+ and a security regression on Android 10.

---

## [0.2.9] - 2026-03-10

### Fixed
- [Bug] Delete confirmation dialog now correctly states "Selected items will be moved to the Trash Bin. You can restore them later" instead of the misleading "This action cannot be undone."
- [Bug] Recent Files screen now always reloads data when navigated to, preventing stale file lists after file operations.
- [Bug] `FileRepository.searchFiles()` parameter changed from `Any?` to `SearchFilters?` for compile-time type safety.

### Improved
- [Architecture] Moved `SearchFilters` data class from `presentation` to `domain` package, fixing an architectural dependency violation (data layer was importing from presentation layer).
- [Architecture] Extracted `formatFileSize()` utility from `FileManagerScreen.kt` into a shared `utils/FormatUtils.kt` module, resolving cross-file coupling.
- [Design] Restored the bundled Outfit font across all 13 Material typography styles, replacing the system `SansSerif` fallback that was set in v0.2.5.
- [Design] Fixed OLED theme `surfaceContainer`, `surfaceContainerHigh`, and `surfaceContainerHighest` tokens to use properly dark values, ensuring cards and elevated surfaces render near-black in OLED mode.
- [Design] Removed deprecated `window.statusBarColor` and `window.navigationBarColor` API calls from `ArcileTheme` — `enableEdgeToEdge()` already handles system bar colors.

### Removed
- [Cleanup] Removed dead `animatedShape` animation code from `FileItemRow` and `TrashScreen` — the `TwoWayConverter` was a no-op that always returned the same shape value.
- [Cleanup] Removed duplicate imports (`AnimatedVisibility`, `fadeIn`, `fadeOut`, `slideInVertically`, `slideOutVertically`, `LazyRow`) from `FileManagerScreen.kt`.

---

## [0.2.8] - 2026-03-07

### Added
- [Feature] Contextual Content Search: Integrated local file search logic directly bound to the user's current directory exploration scope. Searching in `Downloads` will now yield results strictly contained within the `Downloads` node mapping, bypassing the global MediaStore dump. 
- [Feature] Search Filters: Introduced an inline bottom sheet for real-time granular file filtering during search operations. Support logic dynamically targets:
  - File Type (Images, Videos, Audio, Docs)
  - Item Type (Folders, Files)
  - Minimum and Maximum Size spans (< 10 MB, 10 - 100MB, etc.)
  - Date Modified ranges (Today, Last 7 Days, Last 30 Days)
- [Feature] Smart Range Selection: Enabled intuitive multi-select logic in the File Browser. Once in selection mode, long-pressing two discrete files now automatically calculates and selects the spatial bounds bridging the two items.
- [Feature] Select All: Injected a global 'Select All' toggle into the navigation bar during selection mode to batch select the entire directory instantly.

### Improved
- [UI] Search interface cleanly docks directly into the `FileManagerScreen`'s application bar alongside dynamic removable chips plotting active filters on a secondary layout row.
- [UI] Filter Bottom sheet horizontally scrolls constraint dimensions to cleanly block native text wrapping glitches on tight pixel screens. 

---

## [0.2.7] - 2026-03-07

### Added
- [Feature] Implemented a comprehensive Storage Management Dashboard providing a visual breakdown of device storage by category.
- [Feature] Added deep category navigation—clicking any storage category in the dashboard instantly opens that specific media group in the file explorer.
- [Navigation] Integrated the dashboard via a long-press gesture on the primary Home screen storage card.

### Improved
- [UI] Refined the `MultiColorStorageBar` to provide accurate real-time feedback and better visual clarity between categorized data and system overhead.

### Fixed
- [Bug] Resolved Jetpack Compose `@Composable` context violations preventing builds when prepping data inside `LazyColumn` scopes.
- [Bug] Fixed critical missing imports and fully-qualified name resolution failures in the `HomeScreen` and `StorageDashboard` UI modules.
- [Bug] Corrected deprecated icon usage to the modern `AutoMirrored` standards.

## [0.2.6] - 2026-03-07

### Added
- [Feature] Expanded the Recent Files experience via a dedicated "See All" screen that organizes recent items chronologically by full calendar days (Today, Yesterday, etc.) with explicit sticky headers natively mapped across timezone and daylight saving boundaries.
- [Security] Applied `validateFileName` middleware across the repository to aggressively screen all directory/file creations and renames for invalid characters and explicit path traversal (`..` escapes) vectors.
- [Security] Locked down `FileProvider` (`file_provider_paths.xml`) so the internal system no longer exposes the entire root `external_files` path to external apps.

### Improved
- [Performance] Eliminated severe "Recent Files" UI loading stutters by natively querying `SIZE`, `DISPLAY_NAME`, `DATE_MODIFIED`, and `MIME_TYPE` from the raw SQL MediaStore cursors, bypassing tens of thousands of simultaneous disk properties queries.
- [Performance] `getRecentFiles` now securely pulls unbounded recent artifacts up to exactly 7 trailing days ago flawlessly, without an arbitrary query cap truncating results.
- [Performance] Upgraded `AudioAlbumArtFetcher.kt` to natively sample large Bitmaps into memory rather than triggering OutOfMemory errors when loading raw byte streams into the cache. 

### Changed
- [Build] Integrated the Stable Artifacts API alongside standard configuration outputs (`Arcile-dev.qtremors.arcile-0.2.6.apk`) over the obsolete undocumented `VariantOutputImpl` internal API.

### Fixed
- [Bug] Prevented random directories from incorrectly populating the `RecentFilesScreen` as "0-byte files" by explicitly excluding rows where `MIME_TYPE` is null.
- [Bug] Repositioned the high-refresh rate (120FPS) hardware configuration block in `MainActivity.kt` safely out of the `setContent` composable block and wired it directly to the Android 30+ non-deprecated `Display` subsystem.
- [Bug] Stopped the Storage `ArcileAppShell` pathing tree from crashing and causing double-launches due to an improperly scoped `LaunchedEffect` listener on the App Shell backstack.


## [0.2.5] - 2026-03-07

### Fixed
- [Bug] Resolved fully qualified name resolution failures breaking builds across UI screens.
- [Bug] Fixed missing Compose runtime imports causing `HomeScreen` compilation errors.

### Changed
- [Design] Corrected `ExpandableFabMenu` internal actions to use `ExtendedFloatingActionButton` paired with `ExpressivePillShape` for proper pill construction.
- [Design] Main internal storage `FloatingActionButton` now properly morphs its shape radius from an expressive squircle (16dp) to a perfect circle (28dp) when expanding its options.
- [Design] Typography system globally swapped from the custom 'Outfit' font to the native `SansSerif` font to immediately enhance text legibility and contrast against vibrant Material 3 backgrounds.
- [Design] Removed hard-coded alpha layer transparencies from various text components on the `HomeScreen` to solve legibility challenges and ensure pure M3 text color semantic rendering.
- [Design] The `FileGridItem`, `FileItemRow`, and `TrashList` items now properly physically morph into an `ExpressiveShapes.large` container when selected, instead of using a baseline flat color switch.
- [Architecture] `ArcileTopBar` refactored to allow parent screens to granularly pass booleans determining the overflow menu actions (e.g., hiding "New Folder" from the Home Screen, exposing "Settings" and "About" instead).
- [Component] The `ToolCard` components on the Home Screen now check an internal `isImplemented` flag to decide whether to render the "Coming Soon" badge, unflagging the active Trash Bin.

### Removed
- [Cleanup] Removed the redundant `ExtendedFloatingActionButton` for "Empty Trash" situated at the bottom of the `TrashScreen` since the action is already unified within the `TopAppBar`.

---

## [0.2.4] - 2026-03-07

### Added
- [Design] Fully adapted the app to Google's Material 3 Expressive guidelines, including new shape scales, dynamic color themes, and typography refinements.
- [Component] Replaced the single FAB inside the File Manager with a new Material 3 Expressive animated Expandable FAB Menu containing `New File` and `New Folder`.
- [Motion] Implemented `SharedTransitionLayout` to support smooth shared element bounds transitions across navigation screens.
- [Motion] Added `animateContentSize` for dynamic layout adjustments across expanding containers.

### Changed
- [Component] Overhauled `ArcileTopBar`, `GlobalSearchBar`, and Button components to map tightly against modern M3 surface container roles and expressive variants.
- [Design] Transitioned the default system font family to `Outfit` to align with the expressive aesthetic profile.

### Fixed
- [Bug] Fixed the `Trash Bin` utility card on the Home Screen being unclickable due to redundant touch interception hierarchies.

---

## [0.2.3] - 2026-03-07

### Added
- [Feature] Core File Operations (`Copy`, `Cut`, `Paste`, `Share`) supported natively through application domain/repository architecture.
- [Feature] Full `Trash Bin` system utilizing a highly-secured hidden system `.arcile_trash` vault. Default `delete` flows now map through `moveToTrash`.
- [Feature] Added new AppRoutes screen for the Trash system, injected inside the `HomeScreen` as a fast shortcut.
- [Feature] Embedded system clipboard monitoring inside `FileManagerState` memory to dynamically expose floating clipboard paste/cancel panels inside the main toolbar across location mutations.
- [Feature] Intelligent copy conflict overrides mapping duplicate files sharing namespaces via a new automatic ` - Copy (*)` suffix resolution protocol to prevent filesystem erasure.
- [Design] Integrated Material 3 Expressive `PullToRefresh` layout wrapper across all directory traversal screens inside `FileManagerScreen`.

---

## [0.2.2] - 2026-03-07

### Added
- [Feature] Introduced a global "Show All" button on the Home screen's Utilities dashboard to seamlessly dive into the dedicated Tools screen.
- [Component] Abstracted the `ToolCard` feature out of the `ToolsScreen` into a unified layout module accessible across the app.

### Changed
- [Design] Radically transformed the Home screen Folders layout from a hidden horizontal scroll row into a rigid 3-row grid block that renders all shortcuts instantly.
- [Design] Relocated 4 primary system Utilities out of the `ToolsScreen` and planted them directly onto the Home screen as a responsive 2x2 grid.

### Fixed
- [Bug] Cleaned up unused variables, dead topbar action properties, and dangling Material Icon imports from `HomeScreen.kt` and `ArcileAppShell.kt`.
- [Bug] Fixed IDE compilation warnings throwing Unresolved Reference errors on missing UI elements.

---

## [0.2.1] - 2026-03-07

### Added
- [Feature] Bumped Compose Material 3 dependency to `1.4.0-alpha08` to introduce M3 Expressive API features.

### Changed
- [Design] Migrated traditional standard `CircularProgressIndicator` across all app screens to the new morphing `LoadingIndicator`.
- [Design] Restructured setting and file browser lists to utilize proper `OneLineListItem` and `TwoLineListItem` formatting structure instead of generic ListItems.
- [Motion] Added spring-based physics motion and scaling to `Card` and file grid components.
- [UX] Contextual selection items in the `ArcileTopBar` are now grouped cleanly inside a tonal surface container to behave like a unified ButtonGroup.

---

## [0.2.0] - 2026-03-06

### Added
- [Feature] The search field now queries the Android MediaStore to provide instant search results across the entire device storage globally, rather than just filtering the local items strictly within the currently opened folder view.
- [Feature] Image and Video thumbnails now load and display locally in the File Browser lists and Grids seamlessly via Coil caching.
- [Feature] Peak Refresh Rate request implemented on app launch `onCreate` frame, supporting 120Hz/144Hz displays natively to achieve buttery-smooth 120fps scrolling. 
- [Feature] Added an interactive layout with new developer and hardware information tiles to the Settings Screen.

### Fixed
- [Bug] Fixed a build issue causing `SettingsScreen.kt` to fail compilation (`Unresolved reference: Color`).
- [Bug] The Search bar will now dismiss properly when the phone's back button is pressed instead of getting stuck on the screen or exiting the app prematurely.

### Changed
- [Design] Settings page completely redesigned and modularized using Jetpack Compose Elevated Tonal Cards, strictly conforming to the Google Material 3 Standards.

---

## [0.1.9] - 2026-03-06

### Added
- [Feature] Categories on the Home Screen now list all files associated with that extension category globally, instead of shortcutting to specific directories.

### Improved
- [Performance] `MultiColorStorageBar` category sizing operation now queries `MediaStore.Files` instead of recursively walking the entire filesystem via `walkTopDown()`, removing massive I/O delays.
- [Design] `MultiColorStorageBar` math logic accurately calculates category sizing vs unmanaged `Other` vs actual free storage space block allocations.
- [Design] `MultiColorStorageBar`, its bottom legend, and the main `CategoryGrid` now sort dynamically, presenting the largest categories first in descending order instead of static alphabetical listing.

### Fixed
- [Bug] Application no longer violently reloads the file directory (resetting scroll and selection state) every time the device wakes up from the lock screen.
- [Bug] Using the back button while deep inside a Category view now correctly returns the user directly to the Home screen instead of dropping them into the root internal storage directory.

---

## [0.1.8] - 2026-03-06

### Added
- [Feature] Added DataStore persistence for Theme and Accent Color selection, restoring state across process deaths
- [Feature] Enabled `buildConfig` in Gradle to dynamically display the current App Version under Settings
- [Feature] Search: Search icon filtering recent files on Home and current-folder contents in Browse
- [Feature] Sort: Sort dialogs controlling recent-file ordering on Home and current-folder ordering in Browse
- [Feature] Grid View: Overflow menu toggling between list and adaptive grid layouts in Browse
- [Design] Refactored SettingsScreen to utilize Material Design 3 `ListItem` components with standardized surfacing

---

## [0.1.7] - 2026-03-04

### Fixed
- [Bug] Per-file deletion error messages are now retained after directory refresh — previously `refresh()` cleared the error before the user could see it

### Refactored
- [Refactor] Replaced magic string action dispatch in `ArcileTopBar` with `TopBarAction` sealed class — compile-time verified actions
- [Refactor] Replaced raw string navigation routes (`"home"`, `"explorer"`, etc.) with `AppRoutes` constants
- [Refactor] Extracted `ArcileAppShell`, `PermissionRequestScreen`, and `getCategoryPath` from `MainActivity.kt` into `ArcileAppShell.kt`

### Improved
- [Motion] Added slide + fade navigation transitions to all `NavHost` routes — screen switching now feels fluid
- [Design] Replaced hardcoded `color.copy(alpha = 0.15f)` overlays with proper M3 tonal tokens (`primaryContainer`, `secondaryContainer`)

---

## [0.1.6] - 2026-03-04

### Fixed
- [Bug] `showRenameDialog` now only triggers when exactly one item is selected — prevents silent no-op on multi-selection
- [Bug] `openFile()` exception is now logged via `Log.e` and the toast includes the actual error message instead of a generic string

### Improved
- [A11y] Added descriptive `contentDescription` labels to all core navigation icons (`HomeScreen`, `FileManagerScreen`, `Breadcrumbs`) for TalkBack support
- [Motion] File list items now animate smoothly on add/remove/reorder via `Modifier.animateItem()` in `LazyColumn`

### Refactored
- [Refactor] Extracted `StorageInfo` data class to its own file (`StorageInfo.kt`) in the domain package
- [Verified] `java.util.Stack` was already replaced with `ArrayDeque` — task marked complete

---

## [0.1.5] - 2026-03-04
### Features & Fixes
- Added click handlers to Home screen Category icons to open their respective media folders.
- Recent files list on Home screen are now clickable, opening the file directly instead of just selecting it.
- Prevented potential crashes / logic bugs with sibling folder navigation in `navigateToFolder`.
- Optimized performance by hoisting `SimpleDateFormat` to prevent per-row instantiation during scroll.
- Fixed a resource linking error ensuring `Theme.Material3.DayNight.NoActionBar` is correctly targeted.
- Code cleanup: Fixed irregular indentations in `ArcileTopBar.kt`.
- Verified and marked off several issues (`formatFileSize` crashes, settings back navigation, storage parsing logic) that were already effectively solved by existing app logic implementations.

---

## [0.1.4] - 2026-03-04

### Added
- File-open capability — tapping a file launches `Intent.ACTION_VIEW` via `FileProvider` with MIME type detection
- Rename UI — rename dialog, ViewModel `renameFile()` function, and edit icon in selection bar (single-selection)
- `RenameDialog` composable in `FileManagerScreen.kt`
- `file_provider_paths.xml` and `FileProvider` registration in `AndroidManifest.xml`

### Fixed
- Permission state now reactive to lifecycle — `onResume` updates a hoisted `mutableStateOf` so the UI recomposes when returning from system settings
- Replaced deprecated `Icons.Default.InsertDriveFile` with `Icons.AutoMirrored.Filled.InsertDriveFile`
- `android:allowBackup` set to `false` (template backup rules not configured)
- Added explicit `kotlinOptions { jvmTarget = "11" }` in `build.gradle.kts`

### Changed
- [Security] Release builds now use `isMinifyEnabled = true` and `isShrinkResources = true`
- Navigation dependency moved from hardcoded string to version catalog (`libs.versions.toml`)
- Lifecycle ViewModel Compose version aligned from `2.8.2` to `2.10.0` (matches `lifecycleRuntimeKtx`)

### Removed
- `local.properties` confirmed not tracked (already in `.gitignore`)
- Unused import `kotlinx.coroutines.launch` confirmed not present in `MainActivity.kt`

---

## [0.1.3] - 2026-03-04

### Added
- Delete confirmation dialog before file/directory deletion
- "Coming Soon" label on all Tools screen cards
- Folder name validation (rejects `/`, `\`, `..`, null, blank)
- Per-file error reporting in batch delete ("Failed to delete N of M files")

### Fixed
- [Security] Path traversal protection on all file operations — canonical path must resolve within external storage
- [Security] `renameFile` rejects names containing `/`, `\`, `..`, or null characters
- `deleteSelectedFiles()` now surfaces per-file failures to the user
- `themes.xml` uses `Theme.Material3.Light.NoActionBar` (was legacy `android:Theme.Material.Light.NoActionBar`)

### Removed
- Empty placeholder `ThemePreferences.kt` (0 bytes)
- Unused import `kotlinx.coroutines.flow.Flow` from `FileRepository.kt`
- Template colors from `Color.kt` (`Purple80`, `PurpleGrey80`, `Pink80`, `Purple40`, `PurpleGrey40`, `Pink40`)
- Unused template colors from `res/values/colors.xml` (`purple_200/500/700`, `teal_200/700`)

---

## [0.1.2] - 2026-03-04

### Added
- Bottom `NavigationBar` with Home / Browse / Tools tabs (replaces hamburger drawer)
- Multi-colored storage progress bar segmented by file category
- 6 color-coded file categories: Images (green), Videos (pink), Audio (orange), Docs (blue), Archives (purple), APKs (cyan)
- Per-category storage size calculation and display
- Category legend in storage card showing size used per type
- Clickable `StorageSummaryCard` → opens file browser
- Folder shortcuts navigate to actual directories (DCIM, Downloads, Pictures, Documents, Music, Movies)
- Settings accessible via gear icon in Home screen top bar
- Back arrow navigation in file browser and settings
- Edge-to-edge display support (`enableEdgeToEdge()`)
- `FileCategories.kt` — category definitions with colors and extensions

### Fixed
- Breadcrumbs now strip `/storage/emulated/0` prefix, show "Internal Storage" as root
- Breadcrumb segment clicks wired to actual navigation (was a TODO stub)
- "Empty Directory" when clicking Internal Storage — file browser now auto-loads
- `Modifier.padding` invalid overload in `HomeScreen.kt`
- Double status bar padding on Android 15/16 (`contentWindowInsets = WindowInsets(0)`)
- `formatFileSize` crash potential (clamped `digitGroups`)

### Changed
- Folder shortcut cards are uniform size (130×48dp)

### Removed
- Hamburger menu and `ModalNavigationDrawer`
- `NavigationDrawerContent.kt` (deleted)

## [0.1.1] - 2026-03-04

### Added
- Project documentation: `README.md`, `DEVELOPMENT.md`, `CHANGELOG.md`, `LICENSE.md`
- Comprehensive project audit in `TASKS.md` (40 findings across 8 categories)
- Consolidated `.gitignore` with full Android/IDE/OS coverage
- App icon source image (`ic_launcher-playstore.png`)

### Fixed
- Stale `com.qtremors.filemanager` package declarations → `dev.qtremors.arcile` across 8 source files
- `"File Manager"` → `"Arcile"` in `settings.gradle.kts`, `strings.xml`, `themes.xml`, `AndroidManifest.xml`
- Missing `Color` import in `FileManagerScreen.kt` causing build failure
- Invalid `Modifier.padding(horizontal, top, bottom)` overload in `HomeScreen.kt` (3 occurrences)
- Version references updated from `1.0.0` to `0.1.1`

### Changed
- Reformatted `TASKS.md` to use prioritized task template

### Removed
- Stale `build_log.txt` and `nav_build_log.txt` (referenced old package name)

---

## [0.1.0] - 2026-03-04

### Added
- File browsing with sorted directory listings (folders first, then alphabetical)
- Breadcrumb navigation bar with auto-scroll and segment tap navigation
- Multi-file selection via long-press with contextual top bar actions
- Create folder via FAB and overflow menu dialog
- Delete selected files and directories (recursive)
- Home screen dashboard with storage summary card, category shortcuts, folder quick-access, and recent files list
- Material You dynamic theming (wallpaper-based colors on Android 12+)
- Custom accent color selection (Blue, Cyan, Green, Red, Purple, Monochrome, Dynamic)
- Theme modes: System, Light, Dark, OLED
- Settings screen with theme mode and accent color bottom sheet pickers
- Tools screen with placeholder cards (FTP Server, Analyze Storage, Clean Junk, Duplicates, Large Files, App Manager, Secure Vault, Network Share)
- Storage permission handling for Android 11+ (MANAGE_EXTERNAL_STORAGE) and pre-11 (READ/WRITE_EXTERNAL_STORAGE)
- Composable top bar component (`ArcileTopBar`) with selection mode, overflow menu, search/sort icons
- Error dialogs for file operation failures
- Bottom navigation bar (Home, Storage, Tools)

### Known Issues
- Search and sort buttons are non-functional (UI stubs only)
- All category/folder shortcuts navigate to storage root instead of specific folders
- Recent file clicks open the file browser instead of the actual file
- Tools screen items are non-functional placeholders
- Theme preferences are not persisted across app restarts
- No file-open capability exists
- No rename UI despite backend support
- Delete has no confirmation dialog
- Settings screen has no back navigation button

---
