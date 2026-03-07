# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.2.2
> **Last Updated:** 2026-03-07

---

### Medium Priority

- [ ] [Refactor] ViewModel directly instantiates `LocalFileRepository` — no DI (`FileManagerViewModel.kt:29`)
  - Hardcoded concrete implementation makes unit testing impossible without real filesystem.
- [ ] [Refactor] `FileModel` holds a `java.io.File` reference — breaks domain separation (`FileModel.kt:6`)
  - Leaks data layer into domain; makes `FileModel` non-serializable/non-parcelable.
- [ ] [Refactor] Single ViewModel for all screens — poor separation of concerns (`FileManagerViewModel.kt`)
  - Manages home, file browser, and file operations. Should be split.

### Low Priority

- [ ] [Performance] `getRecentFiles()` performs recursive file walk on every home screen load (`LocalFileRepository.kt:91-121`)
  - `walkTopDown().maxDepth(3)` on 4 directories — can be slow on devices with many files. Cache with TTL.
- [ ] [Docs] No test infrastructure — only template tests exist
  - Add unit tests for `FileManagerViewModel`, `LocalFileRepository`, and UI tests.

---

### PR Review Findings

- [ ] [Security] Restrict `file_provider_paths.xml` external-path exposure by normalizing path and enforcing a base directory allowlist in `FileProvider`.

---

### Comprehensive Audit Findings

#### A. Correctness & Reliability
- [ ] [Bug] `FileManagerViewModel` state loss on process death.
  - **Problem:** ViewModel state (`currentPath`, `isHomeScreen`) is not persisted across process deaths.
  - **Location:** `FileManagerViewModel.kt:18` (`FileManagerState`)
  - **Impact:** User loses their navigation position if the app is killed in the background.
  - **Fix:** Integrate `SavedStateHandle` to persist navigation state variables.

#### D. Architecture & Design Quality
- [ ] [Architecture] Hardcoded Android framework dependencies in ViewModel.
  - **Problem:** `FileManagerViewModel` initializes `storageRootPath` via `Environment.getExternalStorageDirectory()`.
  - **Location:** `FileManagerViewModel.kt:39`
  - **Impact:** Makes the ViewModel impossible to unit test without Robolectric.
  - **Fix:** Inject `storageRootPath` via the constructor or retrieve it asynchronously via `FileRepository`.

#### F. UI / UX & Accessibility
- [ ] [UX] Missing adaptive layouts for large screens.
  - **Problem:** Root composables force narrow vertical lists regardless of window width.
  - **Location:** `HomeScreen.kt`, `FileManagerScreen.kt`
  - **Impact:** Poor utilization of screen real estate on tablets and foldables.
  - **Fix:** Implement `WindowSizeClass` checks and use `LazyVerticalGrid` or standard row/column dual-pane layouts for expanded widths.

#### G. Material Design 3 Expressive Implementation
- [ ] [Design] Incorrect manual tonal palette generation for custom seeds.
  - **Problem:** `generateColorSchemeFromSeed` fakes tonal palettes by applying alpha transparency.
  - **Location:** `Theme.kt:53-63`
  - **Impact:** Seed color themes lack accessibility mapping, causing poor text contrast.
  - **Fix:** Use the Material Color Utilities library (`m3color` / HCT) to generate a mathematically correct `ColorScheme`.

---

### PR Review Findings

- [ ] [Chore] Replace internal `VariantOutputImpl` with Stable Artifacts API for APK renaming (`build.gradle.kts:64-71`)
- [ ] [Performance] Downsample album art in `AudioAlbumArtFetcher` to prevent OOM (`AudioAlbumArtFetcher.kt:25`)
- [ ] [Correctness] Move refresh-rate side-effect out of `setContent` to prevent redundant execution (`MainActivity.kt:57-69`)
- [ ] [Chore] Use non-deprecated `display` API on Android 30+ (`MainActivity.kt:59`)
- [ ] [Performance] Prevent redundant `openFileBrowser()` calls in `ArcileAppShell` (`ArcileAppShell.kt:136-140`)
- [ ] [Chore] Replace deprecated `Divider` with `HorizontalDivider` in `SettingsScreen.kt` (`SettingsScreen.kt:66, 84, 92, 100`)
- [ ] [Docs] Update stale architecture and configuration details in `DEVELOPMENT.md` (`DEVELOPMENT.md:5, 46, 76, 308, 339`)

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

