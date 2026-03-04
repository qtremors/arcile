# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.1.6
> **Last Updated:** 2026-03-04

---

### Medium Priority

- [ ] [Feature] Search is completely unimplemented
  - Search icon visible on every screen but `onSearchClick` is always a no-op.
- [ ] [Feature] Sort is completely unimplemented
  - Sort icon visible but `onSortClick` is always a no-op.
- [ ] [Feature] Theme preference not persisted — resets on app restart (`MainActivity.kt:57`)
  - `ThemeState` held in `remember` — changes lost on process death. Needs DataStore persistence.
- [ ] [Feature] "Grid View" menu option does nothing (`ArcileTopBar.kt:48,97-102`)
  - Appears in overflow menu but no handler processes the action.
- [ ] [Refactor] ViewModel directly instantiates `LocalFileRepository` — no DI (`FileManagerViewModel.kt:29`)
  - Hardcoded concrete implementation makes unit testing impossible without real filesystem.
- [ ] [Refactor] `FileModel` holds a `java.io.File` reference — breaks domain separation (`FileModel.kt:6`)
  - Leaks data layer into domain; makes `FileModel` non-serializable/non-parcelable.
- [ ] [Refactor] Single ViewModel for all screens — poor separation of concerns (`FileManagerViewModel.kt`)
  - Manages home, file browser, and file operations. Should be split.
- [ ] [Refactor] String-based navigation routes without type safety (`MainActivity.kt`)
  - Raw strings `"home"`, `"explorer"`, `"settings"`, `"tools"` — typos cause silent failures.
- [ ] [Refactor] Action dispatch via magic strings in `ArcileTopBar` (`ArcileTopBar.kt:23`)
  - `onActionSelected("New Folder")` etc. — no compile-time verification.

### Low Priority

- [ ] [Performance] `getRecentFiles()` performs recursive file walk on every home screen load (`LocalFileRepository.kt:91-121`)
  - `walkTopDown().maxDepth(3)` on 4 directories — can be slow on devices with many files. Cache with TTL.
- [x] [Performance] `java.util.Stack` adds unnecessary synchronization overhead (`FileManagerViewModel.kt:35`)
  - ~~Replace with `ArrayDeque`.~~ Already using `ArrayDeque` (verified in v0.1.6).
- [x] [Refactor] `StorageInfo` co-located with `FileRepository` interface (`FileRepository.kt:6-9`)
  - ~~Move to its own file in the domain package for consistency.~~ Done in v0.1.6.
- [ ] [Refactor] Top-level composables in `MainActivity.kt` (`MainActivity.kt:126-255`)
  - `ArcileAppShell` and `PermissionRequestScreen` should be in `presentation/ui`.
- [ ] [Docs] No test infrastructure — only template tests exist
  - Add unit tests for `FileManagerViewModel`, `LocalFileRepository`, and UI tests.

---

### PR Review Findings

- [ ] [Security] Fix prefix-bypass vulnerability in `LocalFileRepository.kt` boundary check (`canonical.startsWith(storageRoot)`).
- [x] [Bug] Preserve and log exception in `MainActivity.kt` when no app is found to open a file, instead of swallowing it. (v0.1.6)
- [ ] [Bug] Retain per-file failure messages in `FileManagerViewModel.kt` deletion logic (prevent `refresh()` from clearing the error immediately).
- [x] [Bug] Fix `showRenameDialog` logic in `FileManagerScreen.kt` so it only shows when exactly one item is selected. (v0.1.6)
- [ ] [Security] Restrict `file_provider_paths.xml` external-path exposure by normalizing path and enforcing a base directory allowlist in `FileProvider`.
- [ ] [Config] Fix Compose BOM conflict in `libs.versions.toml`: align `lifecycleViewmodelCompose` and `navigationCompose` versions constraint.

---

### Comprehensive Audit Findings

#### A. Correctness & Reliability
- [ ] [Bug] `FileManagerViewModel` state loss on process death.
  - **Problem:** ViewModel state (`currentPath`, `isHomeScreen`) is not persisted across process deaths.
  - **Location:** `FileManagerViewModel.kt:18` (`FileManagerState`)
  - **Impact:** User loses their navigation position if the app is killed in the background.
  - **Fix:** Integrate `SavedStateHandle` to persist navigation state variables.

#### C. Performance & Resource Efficiency
- [ ] [Performance] Massive I/O bottleneck in category sizing.
  - **Problem:** `getCategoryStorageSizes()` performs a full recursive `walkTopDown()` of the entire external storage.
  - **Location:** `LocalFileRepository.kt:174-212`
  - **Impact:** Delays Home screen loading significantly by blocking I/O and burning CPU/battery on devices with many files.
  - **Fix:** Track category sizes incrementally via MediaStore queries or cache with long TTLs.

#### D. Architecture & Design Quality
- [ ] [Architecture] Domain layer depends on Android UI framework.
  - **Problem:** Domain models `CategoryStorage` and `FileCategories` import and hold `androidx.compose.ui.graphics.Color`.
  - **Location:** `FileCategories.kt:3,8,57`
  - **Impact:** Violates Clean Architecture by coupling domain logic to Jetpack Compose.
  - **Fix:** Use hex strings or a domain-specific enum/wrapper for colors, resolving them in the UI layer.
- [ ] [Architecture] Hardcoded Android framework dependencies in ViewModel.
  - **Problem:** `FileManagerViewModel` initializes `storageRootPath` via `Environment.getExternalStorageDirectory()`.
  - **Location:** `FileManagerViewModel.kt:39`
  - **Impact:** Makes the ViewModel impossible to unit test without Robolectric.
  - **Fix:** Inject `storageRootPath` via the constructor or retrieve it asynchronously via `FileRepository`.

#### F. UI / UX & Accessibility
- [x] [A11y] Missing accessibility labels on core navigation icons. (v0.1.6)
  - **Problem:** Critical interactive icons pass `null` to `contentDescription`.
  - **Location:** `HomeScreen.kt:174,443`, `FileManagerScreen.kt:218`, `Breadcrumbs.kt:70`
  - **Impact:** App is largely unusable via TalkBack screen readers.
  - ~~**Fix:** Add descriptive `stringResource` values for all `contentDescription`s.~~ Done.
- [ ] [UX] Missing adaptive layouts for large screens.
  - **Problem:** Root composables force narrow vertical lists regardless of window width.
  - **Location:** `HomeScreen.kt`, `FileManagerScreen.kt`
  - **Impact:** Poor utilization of screen real estate on tablets and foldables.
  - **Fix:** Implement `WindowSizeClass` checks and use `LazyVerticalGrid` or standard row/column dual-pane layouts for expanded widths.

#### G. Material Design 3 Expressive Implementation
- [ ] [Design] Hardcoded alpha overlays instead of M3 tonal palettes.
  - **Problem:** Components manually apply `Modifier.background(color.copy(alpha = 0.15f))` for surfaces.
  - **Location:** `HomeScreen.kt:367`, `FileManagerScreen.kt:214`
  - **Impact:** Breaks MD3 dynamic theming contrast guarantees and expressive palette transitions.
  - **Fix:** Map directly to `MaterialTheme.colorScheme.primaryContainer` or `secondaryContainer`.
- [ ] [Design] Incorrect manual tonal palette generation for custom seeds.
  - **Problem:** `generateColorSchemeFromSeed` fakes tonal palettes by applying alpha transparency.
  - **Location:** `Theme.kt:53-63`
  - **Impact:** Seed color themes lack accessibility mapping, causing poor text contrast.
  - **Fix:** Use the Material Color Utilities library (`m3color` / HCT) to generate a mathematically correct `ColorScheme`.

#### H. Interaction & Motion Design
- [ ] [Motion] Abrupt internal navigation transitions.
  - **Problem:** `NavHost` switching between Home and Browse cuts instantly.
  - **Location:** `MainActivity.kt:206-277`
  - **Impact:** The app feels disjointed and lacks spatial awareness between screens.
  - **Fix:** Implement `enterTransition` and `exitTransition` using `slideIn` / `fadeIn` animations in `composable` routes.
- [x] [Motion] Static list updates lacking micro-interactions. (v0.1.6)
  - **Problem:** Deleting or renaming files instantly snaps the `LazyColumn` without visual feedback.
  - **Location:** `FileManagerScreen.kt:113`
  - **Impact:** Users miss context about what happened to the items they interacted with.
  - ~~**Fix:** Add `Modifier.animateItemPlacement()` to `FileItemRow` in the `LazyColumn`.~~ Done (using `animateItem()`).

---

### Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

- SAF (Storage Access Framework) integration for better Android 11+ compatibility
- Copy/Move/Paste file operations with progress tracking
- File sharing via `Intent.ACTION_SEND`
- File properties dialog (size, permissions, creation date)
- Thumbnail previews for images and videos
- Compress/extract ZIP support
- Bookmarks / favorites for directories
- Multi-window / split-screen support
- Root access support for power users
- SD card and USB OTG storage support
