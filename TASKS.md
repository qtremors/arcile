# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.4.9
> **Last Updated:** 2026-03-21

---

## Table of Contents

1. [PR Review Findings](#1-pr-review-findings)
2. [Architecture & Refactoring](#2-architecture--refactoring)
3. [Performance & Efficiency](#3-performance--efficiency)
4. [General & Code Quality](#4-general--code-quality)
5. [Comprehensive Audit Findings](#5-comprehensive-audit-findings)
6. [Backlog / Ideas](#6-backlog--ideas)

---

## 5. Comprehensive Audit Findings

### A. UI, UX & Accessibility

- [ ] [Accessibility] Hardcoded Content Descriptions.
  - **Problem:** Over 40 instances of hardcoded `contentDescription = "..."` for Icons and interactive elements, rendering screen readers useless for non-English users.
  - **Location:** `ArcileTopBar.kt`, `TrashScreen.kt`, `GlobalSearchBar.kt`, `FileList.kt`, `ConflictCard.kt`, `FileGrid.kt`, etc.
  - **Fix:** Extract `contentDescription` strings to `strings.xml` and use `stringResource()`.

- [ ] [Correctness] Android 10 is documented as unsupported but still passes the permission gate.
  - **Problem:** The app advertises `minSdk = 24` and explicitly documents that Android 10 (API 29) is fundamentally unsupported, yet `MainActivity.checkStoragePermission()` still treats API 29 as supported by accepting `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE`. On Android 10 the app can install, pass the permission screen, and then fail across core file operations because the repository depends on unrestricted `java.io.File` access that scoped storage blocks.
  - **Location:** `arcile-app/app/build.gradle.kts:17`, `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt:153-179`, `README.md:98`, `DEVELOPMENT.md:317-318`
  - **Fix:** Either raise `minSdk` to 30, or hard-block API 29 at startup with a dedicated unsupported-device screen instead of granting access to the main shell.

### B. Security

- [ ] [Security] Signing credentials read from `local.properties` without validation.
  - **Problem:** `app/build.gradle.kts` loads signing config properties from `local.properties` with null-unsafe casts (`as String?`). If any property is missing, the release build will crash with a `ClassCastException` at configuration time, leaking the build environment structure in error logs.
  - **Location:** `app/build.gradle.kts:33-40`
  - **Fix:** Use `?.toString()` and validate presence before creating the `signingConfig`. Consider a dedicated `signing.properties` file excluded from VCS.

- [ ] [Security] The app hard-requires `MANAGE_EXTERNAL_STORAGE` instead of degrading gracefully on modern Android.
  - **Problem:** The manifest declares `MANAGE_EXTERNAL_STORAGE`, and `MainActivity` gates the entire app shell behind that permission on Android 11+. This creates a high-risk permission posture, complicates Play distribution, and gives the app filesystem access well beyond the minimum needed for many flows.
  - **Location:** `app/src/main/AndroidManifest.xml:7`, `MainActivity.kt:102-118`, `MainActivity.kt:153-183`
  - **Fix:** Audit which flows truly require broad file access, fall back to SAF/MediaStore where possible, and keep the app partially usable when special access is denied.

- [ ] [Security] `FileProvider` exposes the entire external storage tree.
  - **Problem:** `file_provider_paths.xml` defines `<external-path name="external_root" path="/" />`, so any future URI-generation bug in `openFile()` or `ShareHelper.shareFiles()` can expose arbitrary external-storage files through the app's provider authority instead of only the small set of intended share/open targets.
  - **Location:** `arcile-app/app/src/main/res/xml/file_provider_paths.xml:4`, `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt:130-145`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ShareHelper.kt:21-35`
  - **Fix:** Replace the root mapping with the narrowest required paths, or move sharing/opening behind SAF/content URIs so the provider does not need blanket external-path coverage.

- [ ] [Security] Trash metadata is stored in public external storage without protection.
  - **Problem:** The custom trash subsystem writes JSON sidecars under `.arcile/.metadata` on each storage root, including the original absolute path, deletion time, and storage kind. Any app or user with broad storage access can inspect this history and recover sensitive file names and locations even after the file has been "deleted".
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/LocalFileRepository.kt:1301-1364`
  - **Fix:** Move metadata into app-private storage keyed by trash ID, or encrypt the sidecars before writing them to shared storage.

### E. Maintainability & Code Quality

- [ ] [CodeQuality] Manual JSON serialization instead of using `kotlinx.serialization`.
  - **Problem:** `LocalFileRepository` and `StorageClassificationRepository` manually construct `JSONObject` instances for trash metadata and storage classifications. The project already depends on `kotlinx.serialization.json` — this is wasted complexity.
  - **Location:** `LocalFileRepository.kt`, `StorageClassificationRepository.kt`
  - **Fix:** Define `@Serializable` data classes and use `Json.encodeToString()` / `Json.decodeFromString()`.

- [ ] [CodeQuality] Expand ViewModel branch coverage for destructive and refresh-heavy flows.
  - **Problem:** The project now has JVM tests for `BrowserViewModel`, `HomeViewModel`, `RecentFilesViewModel`, and `TrashViewModel`, but the most failure-prone branches are still only partially covered. Rename conflicts, destination-required restore flows, and repeated refresh/state-restoration paths remain high-risk logic.
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/presentation/`
  - **Fix:** Add focused tests for rename collisions, restore fallback flows, selection persistence, and repeated volume-emission refresh behavior.

- [ ] [CodeQuality] Instrumented Compose UI coverage is still effectively absent.
  - **Problem:** The project now has Robolectric-backed JVM Compose component tests, but `androidTest` still contains only the auto-generated `ExampleInstrumentedTest`. There is no device/runtime validation for navigation, permissions, or full-screen workflows.
  - **Location:** `arcile-app/app/src/androidTest/`
  - **Fix:** Add screen-level integration tests for critical flows such as permission gating, file browsing, destructive actions, and search on a real device/emulator.

- [ ] [CodeQuality] APK naming relies on internal AGP implementation classes.
  - **Problem:** The build script casts outputs to `com.android.build.api.variant.impl.VariantOutputImpl` to rename APKs. That type is outside the public AGP API surface, so Gradle/AGP upgrades can break builds even when the app code is unchanged.
  - **Location:** `app/build.gradle.kts:72-79`
  - **Fix:** Move APK naming to a supported public API when available, or isolate the workaround behind version checks and documented build-tool constraints.

### F. UI / UX Implementation Quality

- [ ] [UI/UX] Hardcoded "Delete" and "Cancel" button text in delete confirmation dialogs.
  - **Problem:** The `FileManagerScreen.kt` delete confirmation dialogs use `Text("Delete")` and `Text("Cancel")` instead of `stringResource()`.
  - **Location:** `FileManagerScreen.kt`
  - **Fix:** Extract to string resources.

- [ ] [UI/UX] `SimpleDateFormat` created inside `remember()` but not locale-aware on configuration change.
  - **Problem:** `SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())` is created inside a `remember` block. If the user changes the system locale while the app is open, the formatter won't update because `remember` does not re-execute. This is prevalent in many files.
  - **Location:** `RecentFilesScreen.kt`, `FileManagerScreen.kt`, `HomeScreen.kt`, `FileGrid.kt`, `FileList.kt`, `PasteConflictDialog.kt`, `TrashList.kt`
  - **Fix:** Create the formatter outside `remember` (each recomposition is cheap) or key on locale.

- [ ] [UI/UX] `HomeScreen.kt` directly accesses `android.os.Environment` for folder paths.
  - **Problem:** `MainFoldersGrid` builds folder paths using `Environment.getExternalStorageDirectory()` directly in the composable. This is an existing known issue (see section 2) but the composable also lacks existence checks — if a standard folder (e.g., `Movies`) doesn't exist, the entry is still shown.
  - **Location:** `HomeScreen.kt` (inside `MainFoldersGrid`)
  - **Fix:** Pass folder paths from ViewModel and filter out non-existent directories.

- [ ] [UI/UX] Recent files screen exposes refresh plumbing but no refresh affordance.
  - **Problem:** `RecentFilesScreen` accepts `onRefresh`, but the screen never renders a `PullToRefreshBox`, refresh action, or any other UI that can invoke it.
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt:60-75`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt:209-212`
  - **Impact:** The recent-files list can become stale during a session with no user-visible way to refresh it manually.
  - **Fix:** Add pull-to-refresh or an explicit refresh action, or remove the unused callback if manual refresh is intentionally unsupported.

- [ ] [UI/UX] Trash operation failures are never surfaced to the user.
  - **Problem:** `TrashViewModel` populates `state.error` for load, restore, empty, and permanent-delete failures, but `TrashScreen` never renders a snackbar, dialog, or inline error state, and it never calls `onClearError`.
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/trash/TrashViewModel.kt:74-76`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/trash/TrashViewModel.kt:118-123`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/trash/TrashViewModel.kt:150-153`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/trash/TrashViewModel.kt:171-174`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/trash/TrashViewModel.kt:203-205`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/TrashScreen.kt:91-106`
  - **Impact:** Failed restore or delete actions appear to do nothing, which is especially confusing for destructive file-management flows.
  - **Fix:** Observe `state.error` in `TrashScreen`, show a snackbar or dialog, and clear the error after acknowledgement.

### G. Material Design 3 Expressive Implementation

- [ ] [M3] Inconsistent use of theme spacing — mix of hardcoded `dp` and `MaterialTheme.spacing`.
  - **Problem:** Some composables use `MaterialTheme.spacing.medium` while nearby code uses hardcoded `12.dp`, `16.dp`, `20.dp` for the same logical spacing. This makes the spacing system partially useless.
  - **Location:** `HomeScreen.kt` (e.g., `Spacer(Modifier.height(16.dp))` vs `MaterialTheme.spacing.medium`), `StorageSummaryCard`, `StorageVolumeCard`
  - **Fix:** Audit all `dp` values and replace with the appropriate `MaterialTheme.spacing.*` token.

### H. Interaction Quality & Motion Design

- [ ] [Motion] Pull-to-refresh indicator logic duplicated between `HomeScreen` and `FileManagerScreen`.
  - **Problem:** The custom pull-to-refresh indicator (floating `Card` with `LoadingIndicator`) is copy-pasted across `HomeScreen.kt` and `FileManagerScreen.kt` with identical implementations (~30 lines each).
  - **Location:** `HomeScreen.kt`, `FileManagerScreen.kt`
  - **Fix:** Extract to a shared `ArciclePullRefreshIndicator` composable.

### J. Documentation Quality

- [ ] [Docs] `proguard-rules.pro` is the default template — no app-specific rules.
  - **Problem:** The ProGuard rules file contains only the auto-generated comments. With `isMinifyEnabled = true` and `isShrinkResources = true` in release builds, the app may crash at runtime if any reflection-based libraries require keep rules (e.g., `kotlinx.serialization`, Coil custom fetchers, Hilt-generated code).
  - **Location:** `arcile-app/app/proguard-rules.pro`
  - **Fix:** Add keep rules for `@Serializable` data classes, custom Coil `Fetcher` factories, and verify release builds don't crash.


### K. General Anomalies

- [ ] [Anomaly] `BrowserState` and `TrashState` hold `android.content.IntentSender` directly in data class.
  - **Problem:** `BrowserState.nativeRequest` and `TrashState.nativeRequest` hold `IntentSender` — a Parcelable Android framework object. Storing platform objects in state classes violates separation of concerns and prevents trivial unit testing of state assertions.
  - **Location:** `BrowserViewModel.kt:66`, `TrashViewModel.kt:27`
  - **Fix:** Expose the `IntentSender` via a `SharedFlow<IntentSender>` one-shot event instead of embedding it in the state data class.

- [ ] [Anomaly] `HomeScreen` constructs `Calendar` and `SimpleDateFormat` on every recomposition.
  - **Problem:** Inside `remember(state.recentFiles, ...)`, a `Calendar.getInstance()` is created to compute `todayStart`. While `remember` prevents re-execution within the same composition, the `Calendar` locale and timezone are baked at creation time. More importantly, the `SimpleDateFormat` is created inside a `LazyColumn` item scope — one instance per recomposition.
  - **Location:** `HomeScreen.kt`, `RecentFilesScreen.kt`
  - **Fix:** Hoist `todayStart` calculation to the ViewModel and pass it as part of `HomeState`.

- [ ] [Anomaly] Folder-scoped search silently stops at depth 10.
  - **Problem:** Path-scoped search uses `walkTopDown().maxDepth(10)`, so anything nested deeper than ten directory levels is omitted without any user-facing indication.
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/LocalFileRepository.kt:919-935`
  - **Impact:** Deep project trees and archive-like folder structures return incomplete search results, making the search feature look unreliable rather than intentionally bounded.
  - **Fix:** Remove the hardcoded depth cap, or surface a clear depth limit in the UI and switch to paginated traversal to keep deep searches responsive.

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
