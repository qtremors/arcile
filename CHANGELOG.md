# Arcile Changelog

> **Project:** Arcile
> **Version:** 0.2.6
> **Last Updated:** 2026-03-07

---

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
