# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.1.3
> **Last Updated:** 2026-03-04

---

### High Priority

- [x] [Security] No path traversal protection on file operations (`LocalFileRepository.kt`)
  - `listFiles`, `deleteFile`, `renameFile` accept arbitrary paths with no validation that they resolve within external storage. A bug could allow deletion of protected files.
- [x] [Security] `renameFile` does not sanitize `newName` for path traversal (`LocalFileRepository.kt:76`)
  - A name like `../../etc/hosts` constructs a path outside the current directory.
- [x] [Bug] `deleteSelectedFiles()` silently swallows per-file deletion errors (`FileManagerViewModel.kt:161-171`)
  - Failed deletions are ignored; user sees no error feedback.
- [ ] [Bug] Permission state uses `remember` — not reactive to lifecycle (`MainActivity.kt:64`)
  - `hasPermission` never auto-updates when returning from system settings on Android 11+.
- [ ] [Feature] No file-open capability exists anywhere
  - The app can browse, create, select, and delete — but cannot open files. Tapping a file does nothing.
- [x] [Bug] Delete has no confirmation dialog (`FileManagerScreen.kt:58`)
  - "Delete Selected" immediately deletes (including recursive directory deletion) with zero user confirmation.
- [ ] [Bug] `navigateToFolder` history logic has false positives for sibling navigation (`FileManagerViewModel.kt:71-91`)
  - String-prefix path comparison misidentifies siblings as parents, corrupting the history stack.
- [ ] [Security] `isMinifyEnabled = false` in release build (`app/build.gradle.kts:26`)
  - Release APKs are unobfuscated and unshrunk — easy reverse engineering and bloated size.

### Medium Priority

- [ ] [Feature] Search is completely unimplemented
  - Search icon visible on every screen but `onSearchClick` is always a no-op.
- [ ] [Feature] Sort is completely unimplemented
  - Sort icon visible but `onSortClick` is always a no-op.
- [ ] [Feature] No rename UI despite backend support (`FileRepository.kt:15`, `LocalFileRepository.kt:70-89`)
  - `renameFile()` is implemented but never called from ViewModel or exposed in any UI.
- [ ] [Feature] Category shortcuts all route to generic file browser (`HomeScreen.kt:167-248`)
  - "Images", "Audio", "DCIM", "Downloads" etc. all call `onOpenFileBrowser()` to storage root instead of their specific folder.
- [ ] [Feature] Recent files click opens generic file browser instead of the file (`HomeScreen.kt:95-96`)
  - Should open the file via an intent or navigate to its parent directory.
- [ ] [Feature] Theme preference not persisted — resets on app restart (`MainActivity.kt:57`)
  - `ThemeState` held in `remember` — changes lost on process death. Needs DataStore persistence.
- [ ] [Feature] Settings screen has no back navigation button (`SettingsScreen.kt:31-41`)
  - `onNavigateBack` is received but never wired to a navigation icon. User can only use system back.
- [ ] [Feature] "Grid View" menu option does nothing (`ArcileTopBar.kt:48,97-102`)
  - Appears in overflow menu but no handler processes the action.
- [x] [Bug] `refresh()` does nothing on home screen (`FileManagerViewModel.kt:109-113`)
  - `onResume` calls `refresh()` but home data (recent files, storage info) only refreshes via `loadHomeData()`.
- [ ] [Bug] `StorageSummaryCard` shows garbage when `storageInfo` is null (`HomeScreen.kt:108`)
  - Fallback `totalBytes = 1L` causes misleading math; should show a loading/empty state instead.
- [ ] [Bug] Breadcrumbs display raw path segments like "emulated" and "0" (`Breadcrumbs.kt:59-61`)
  - Should strip external storage root prefix and label it "Internal Storage".
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

- [x] [Feature] All 8 tools on `ToolsScreen` are non-functional stubs (`ToolsScreen.kt:40-49`)
  - FTP Server, Analyze Storage, Clean Junk, etc. display cards but do nothing. ~~Add "Coming Soon" indicators or hide.~~ Added "Coming Soon" labels.
- [x] [Bug] Creating folders with invalid names (e.g., `/`, `\0`) not validated (`FileManagerViewModel.kt:147-158`)
  - Raw user input passed directly to `File()` constructor.
- [ ] [Bug] `formatFileSize()` can crash for files ≥ 1 PB (`FileManagerScreen.kt:177-182`)
  - `digitGroups` exceeds `units` array size. Clamp to `units.size - 1`.
- [ ] [Performance] `getRecentFiles()` performs recursive file walk on every home screen load (`LocalFileRepository.kt:91-121`)
  - `walkTopDown().maxDepth(3)` on 4 directories — can be slow on devices with many files. Cache with TTL.
- [ ] [Performance] `SimpleDateFormat` recreated per `FileItemRow` (`FileManagerScreen.kt:145`)
  - 1000 files = 1000 instances. Hoist to parent composable or use `CompositionLocal`.
- [ ] [Performance] `java.util.Stack` adds unnecessary synchronization overhead (`FileManagerViewModel.kt:35`)
  - Replace with `ArrayDeque`.
- [ ] [Performance] Navigation dependency hardcoded outside version catalog (`app/build.gradle.kts:52`)
  - `"androidx.navigation:navigation-compose:2.8.5"` should be in `libs.versions.toml`.
- [ ] [Performance] Lifecycle version mismatches in version catalog (`gradle/libs.versions.toml`)
  - `lifecycleViewmodelCompose = "2.8.2"` vs `lifecycleRuntimeKtx = "2.10.0"` — align versions.
- [ ] [Refactor] `StorageInfo` co-located with `FileRepository` interface (`FileRepository.kt:6-9`)
  - Move to its own file in the domain package for consistency.
- [ ] [Refactor] Top-level composables in `MainActivity.kt` (`MainActivity.kt:126-255`)
  - `ArcileAppShell` and `PermissionRequestScreen` should be in `presentation/ui`.
- [x] [Cleanup] Empty file: `ThemePreferences.kt` (`presentation/ui/components/ThemePreferences.kt`)
  - 0 bytes — dead placeholder. ~~Implement or delete.~~ Deleted.
- [x] [Cleanup] Unused import: `kotlinx.coroutines.flow.Flow` (`FileRepository.kt:4`)
- [ ] [Cleanup] Unused import: `kotlinx.coroutines.launch` (`MainActivity.kt:36`)
- [ ] [Cleanup] Inconsistent indentation in `ArcileTopBar.kt` (lines 68-126)
- [ ] [Cleanup] `build_log.txt` and `nav_build_log.txt` committed to repo
  - Build logs reference old package `com.qtremors.filemanager`. Add to `.gitignore` and remove.
- [ ] [Cleanup] `local.properties` committed to repo
  - Machine-specific SDK paths. Add to `.gitignore` and untrack.
- [ ] [Cleanup] Deprecated `Icons.Default.InsertDriveFile` (`FileManagerScreen.kt:156`)
  - Replace with `Icons.AutoMirrored.Filled.InsertDriveFile`.
- [x] [Cleanup] `Color.kt` uses default Android Studio template colors
  - ~~Replace with intentional brand colors or remove.~~ Removed template colors.
- [x] [Cleanup] `res/values/colors.xml` contains unused template colors
  - `purple_200`, `teal_200`, etc. never referenced. ~~Remove.~~ Removed.
- [x] [Config] `rootProject.name = "File Manager"` doesn't match "Arcile" (`settings.gradle.kts:25`)
- [x] [Config] `themes.xml` uses legacy `android:Theme.Material.Light.NoActionBar` (`res/values/themes.xml:4`)
  - ~~Should use `Theme.Material3.Light.NoActionBar` to avoid launch theme flash.~~ Fixed.
- [ ] [Config] Missing explicit `kotlinOptions { jvmTarget = "11" }` (`app/build.gradle.kts`)
- [ ] [Config] `android:allowBackup="true"` with only template backup rules (`AndroidManifest.xml:11`)
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
