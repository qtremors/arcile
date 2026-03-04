# Arcile Changelog

> **Project:** Arcile
> **Version:** 0.1.4
> **Last Updated:** 2026-03-04

---

## [Unreleased]

<!-- Accumulate changes here prior to the next formal release. -->

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
