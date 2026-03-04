# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.1.5
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
- [ ] [Performance] `java.util.Stack` adds unnecessary synchronization overhead (`FileManagerViewModel.kt:35`)
  - Replace with `ArrayDeque`.
- [ ] [Refactor] `StorageInfo` co-located with `FileRepository` interface (`FileRepository.kt:6-9`)
  - Move to its own file in the domain package for consistency.
- [ ] [Refactor] Top-level composables in `MainActivity.kt` (`MainActivity.kt:126-255`)
  - `ArcileAppShell` and `PermissionRequestScreen` should be in `presentation/ui`.
- [ ] [Docs] No test infrastructure — only template tests exist
  - Add unit tests for `FileManagerViewModel`, `LocalFileRepository`, and UI tests.

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
