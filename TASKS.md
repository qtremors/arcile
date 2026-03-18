# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.4.2
> **Last Updated:** 2026-03-18

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

## 1. PR Review Findings (Beta Blockers & Polish)

- [ ] [Design] Material2 theme bases used instead of Material3
  - **Problem:** XML theme bases still use Material2 components (`Theme.Material.Light.NoActionBar`).
  - **Location:** `themes.xml` (lines 6 - 11)
  - **Fix:** Update parent styles to `Theme.Material3.*`. *(Note: App is 100% Compose and does not include the `com.google.android.material:material` XML library, making this change unnecessary/bloat. Left as is.)*

- [ ] [Performance] Analytics cache lacks TTL and robust error handling.
  - **Problem:** `saveCategorySizesToCache` has no timestamp, and `invalidateCache` swallows exceptions.
  - **Location:** `LocalFileRepository.kt` (lines 533 - 603)
  - **Fix:** Embed "cachedAt" timestamp in cache JSON, implement TTL check in `getCategorySizesFromCache`, and add proper logging/rethrowing in `invalidateCache`.

- [ ] [Performance] Optimistic cache hit ignored in `getCategoryStorageSizes`.
  - **Problem:** `val cached = getCategorySizesFromCache(scope)` is called but never used or returned.
  - **Location:** `LocalFileRepository.kt` (lines 606 - 608)
  - **Fix:** Return `cached` immediately if non-null, or implement a background refresh pattern.

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

- [ ] [Performance] `LazyColumn` / `LazyVerticalGrid` items re-keyed by `absolutePath` may conflict.
  - **Problem:** Multiple files from different directories could have the same `absolutePath` key if the list content changes during recomposition.
  - **Location:** `FileManagerScreen.kt`, `HomeScreen.kt`
  - **Fix:** Validate key uniqueness or include additional context (e.g., directory path prefix).

---

## 4. General & Code Quality

- [ ] [Security] Signing credentials may be in `local.properties` which is in `.gitignore`, but the pattern is fragile.
  - **Problem:** Keystore password, key alias, and key password are read from `local.properties` at build time.
  - **Location:** `app/build.gradle.kts`
  - **Fix:** Use environment variables or a dedicated CI secrets mechanism instead of a local file.

- [ ] [Design] Custom color schemes hardcoded instead of using Material Color Utilities.
  - **Problem:** All accent color schemes are manually defined with hardcoded hex colors rather than generated from seed colors via HCT.
  - **Location:** `Color.kt`
  - **Fix:** Use the `material-color-utilities` library to generate `ColorScheme` from seed color at runtime.

- [ ] [Design] `ExpressiveSquircleShape` is a simple `RoundedCornerShape(28.dp)`, not a true squircle.
  - **Problem:** A squircle (superellipse) has continuously changing curvature, while `RoundedCornerShape` uses circular arc corners.
  - **Location:** `Shape.kt`
  - **Fix:** Use `RoundedCornerShape` with `CornerSize` percent or implement a custom `CutoutShape`.

---

## 5. Comprehensive Audit Findings

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

## 7. Completed Tasks

- [x] [Accessibility] Missing selection state for TalkBack
  - **Problem:** `FileList` item sets merged `contentDescription` but doesn't expose selection state.
  - **Location:** `FileList.kt`
  - **Fix:** Set `selected = isSelected` in the `semantics` block.

- [x] [UI/UX] Shimmer animation captures range at zero size
  - **Problem:** Animation range is captured while `size == IntSize.Zero`.
  - **Location:** `ShimmerModifier.kt`
  - **Fix:** Use normalized range and compute offset during render.

- [x] [UI/UX] Wrong UI rendered for empty root screens
  - **Problem:** `EmptyState` branch is evaluated before `volume-root` branch.
  - **Location:** `FileManagerScreen.kt`
  - **Fix:** Reorder checks so `state.isVolumeRootScreen` is tested first.

- [x] [Design] Unreadable secondary colors in light theme
  - **Problem:** `buildScheme` light theme produces nearly-white secondary colors.
  - **Location:** `Color.kt`
  - **Fix:** Increase opacity of secondary or composite stronger tint.

- [x] [Bug] Detached coroutines in flow mapper.
  - **Problem:** Spawns detached `CoroutineScope(Dispatchers.IO).launch` inside a flow mapper.
  - **Location:** `StorageClassificationRepository.kt`
  - **Fix:** Call `resetClassification` synchronously.

- [x] [Bug] Invalid empty volumeId in Category restore.
  - **Problem:** `StorageBrowserLocation.Category` restores with an invalid/empty `volumeId`.
  - **Location:** `BrowserViewModel.kt`
  - **Fix:** Fallback to `StorageScope.AllStorage`.

- [x] [Bug] Error message wiped immediately by refresh().
  - **Problem:** Failure handlers wipe error before UI can show it.
  - **Location:** `BrowserViewModel.kt`
  - **Fix:** Preserve error through the `refresh()` call.

- [x] [Bug] Optimistic dismissal silent failure.
  - **Problem:** UI stays silent if classification persistence fails.
  - **Location:** `HomeViewModel.kt`
  - **Fix:** Wrap in try/catch and restore state on failure.

- [x] [Navigation] Missing query parameters in EXPLORER routes.
  - **Problem:** Routes missing full parameter set breaking state restoration.
  - **Location:** `ArcileAppShell.kt`
  - **Fix:** Standardize and force full query parameter set in routes.

- [x] [Architecture] Default no-op lambdas hide missing wiring.
  - **Problem:** Some critical callbacks defaulted to `{}`.
  - **Location:** `TrashScreen.kt`
  - **Fix:** Remove defaults to enforce explicit wiring.

- [x] [UI/UX] Destination picker doesn't close immediately.
  - **Problem:** Reliance on slow upstream state for dialog dismissal.
  - **Location:** `TrashScreen.kt`
  - **Fix:** Dismiss dialog immediately in the `clickable` handler.

- [x] [Documentation] Missing StorageScope concept definition.
  - **Fix:** Added "StorageScope" section to `DEVELOPMENT.md`.

- [x] [Documentation] Stale test coverage claim.
  - **Fix:** Updated `TASKS.md` to reflect current test suites.

- [x] [Bug] `moveToTrash` reports success on copy/delete fallback failure.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Correctly propagate failure in the move fallback branch.

- [x] [Bug] `getFilesByCategory` misses files due to strict `mimePrefix` matching.
  - **Location:** `LocalFileRepository.kt`
  - **Fix:** Expand query to include extension-based matching.

- [x] [Bug] `TrashViewModel` loses destination path during native confirmation.
  - **Location:** `TrashViewModel.kt`
  - **Fix:** Add persistent pending state for native SCOPED_STORAGE flows.

- [x] [Performance] `AudioAlbumArtFetcher` opens `MediaMetadataRetriever` on the UI thread.
  - **Fix:** Confirmed usage of `Dispatchers.IO`.

- [x] [Code Quality] `ThemePreferences` instantiated inside `setContent{}`.
  - **Fix:** Hoisted initialization to `MainActivity` level.

- [x] [Code Quality] Minimal unit tests exist.
  - **Fix:** Added several unit test suites for repositories and view models.

- [x] [Anomaly] Compose BOM version conflict.
  - **Fix:** Pinned versions to allow M3 Expressive while maintaining stability.

- [x] [Anomaly] `compileSdk` uses non-standard block syntax.
  - **Fix:** Standardized to `compileSdk = 36`.

- [x] [Anomaly] `VariantOutputImpl` cast in `androidComponents`.
  - **Fix:** Maintained as documented AGP workaround.

- [x] [Correctness] `runBlocking` used inside coroutine flows in tests.
  - **Fix:** Migrated to standard coroutine test practices.
