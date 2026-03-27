# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.5.5
> **Last Updated:** 2026-03-27

---

### Testing

- [x] [Severity: High] [Category: Testing] **Add integration tests for critical file operation flows**
  - Location: Missing: `FileSystemDataSourceTest`, `TrashManagerTest`, `LocalFileRepositoryTest`
  - Problem: The data layer (all destructive file operations) has zero test coverage.
  - Impact: Regressions in copy/move/delete/trash are undetectable without manual testing.
  - Fix: Write Robolectric or instrumented tests for `FileSystemDataSource` operations using temp directories.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with new tests.

- [ ] [Severity: Medium] [Category: Testing] **Add `MediaStoreClient` test coverage for category size queries**
  - Location: Missing: `MediaStoreClientTest`
  - Problem: 618 lines of MediaStore SQL query construction, cache management, and category size calculation have zero test coverage. The `requiresFullScan` performance bug cannot be regression-tested.
  - Fix: Write Robolectric-backed tests using `ContentResolver` shadows for query verification.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with new `MediaStoreClientTest`.

- [ ] [Severity: Medium] [Category: Testing] **Add fetcher coverage for rich media preview components**
  - Location: Missing: `ApkIconFetcherTest`, `AudioAlbumArtFetcherTest`
  - Problem: Custom Coil fetchers for APK icons and album art have no automated coverage.
  - Impact: Preview regressions can break silently and only surface on-device when browsing media-heavy folders.
  - Fix: Add Robolectric-backed tests for fetcher factory matching and decode/load behavior with mocked package manager and media metadata inputs.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with the new fetcher tests.

- [ ] [Severity: Medium] [Category: Testing] **Add navigation and saved-state restore coverage for type-safe routes**
  - Location: Missing: `AppRoutesTest`, `AppNavigationGraphTest`, additional saved-state tests for `BrowserViewModel`
  - Problem: Route serialization, argument parsing, and browser state restoration are only partially covered.
  - Impact: Navigation regressions and process-death restore bugs can escape into release builds.
  - Fix: Add tests for route encoding/decoding, key navigation transitions, and saved-state restoration branches.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with route and restore coverage.

- [ ] [Severity: Medium] [Category: Testing] **Add screen-level Compose interaction coverage for primary flows**
  - Location: Missing: `BrowserScreenTest`, expanded coverage for `HomeScreen`, `RecentFilesScreen`, `SettingsScreen`, `StorageDashboardScreen`, `StorageManagementScreen`, `ToolsScreen`, `TrashScreen`, `ArcileAppShell`
  - Problem: Most full-screen Compose flows still lack automated UI interaction coverage.
  - Impact: Search/back handling, snackbars, dialogs, pull-to-refresh, and screen wiring can regress without fast feedback.
  - Fix: Add Robolectric Compose tests for the highest-risk screens first, especially browser and navigation flows.
  - Verification: `./gradlew :app:testDebugUnitTest` passes with the new screen-level tests.


### App Size & Resource Optimization
- [ ] **APK Size - Resource Configurations**: In `app/build.gradle.kts`, add `resConfigs("en", "es")` (for your supported languages) in `defaultConfig` to strip megabytes of unused translated strings and resources bundled by AndroidX and Material3 libraries.
- [ ] **APK Size - ABI Splits**: If distributing APKs instead of App Bundles (.aab), configure `splits { abi { enable = true ... } }` in `build.gradle.kts` to prevent bundling x86, ARMv7, and ARM64 native libraries into a single massive APK.
- [ ] **Post-Install Size - Native Libs**: Add `android:extractNativeLibs="false"` to the `<application>` tag in `AndroidManifest.xml`. This prevents the OS from copying `.so` files into the internal data partition, saving duplicate space.
- [ ] **Storage Usage - Auto-Empty Trash**: `TrashManager.kt` currently leaves files in `.arcile/.trash` indefinitely. Implement a `WorkManager` periodic task to auto-delete trash items older than 30 days to prevent silent massive storage bloat.
- [ ] **Memory Usage - Thumbnail Caching**: Limit Coil's disk cache size in `ImageLoader` configuration so thumbnail caches don't grow infinitely in the app's cache directory.

---

# Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **Advanced Storage Access (SAF)**: Implement a `DocumentFile` / Storage Access Framework (SAF) fallback DataSource specifically for restricted directories (`Android/data`, `Android/obb`), allowing users to modify app data folders just like top-tier file managers.
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.
- **Storage Health Diagnostics**: Basic S.M.A.R.T. status checks, disk health metrics, and repair/trim suggestions for mounted volumes.

### File Operations & Automation
- **File/Folder Properties Dialog**: Display detailed metadata for selected items.
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser.
- **Automated Task Rules**: Implement trigger-based operations (e.g., "Auto-move video files from Camera to Videos folder daily").
- **Operation Queue & FAB Manager**: Implement a "Bank" for operations. A running operation replaces the floating action button (FAB) with a progress ring. Tapping it shows a queue of paused/running tasks with the ability to gracefully cancel (e.g., stop after 10 of 100 files are copied).

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` in areas where a main action consistently needs a secondary dropdown/overflow action (e.g., creating files vs folders, or sorting).
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press (selection mode), successful file operations, and error states to make the app feel alive and premium.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally-scrollable carousel of the 10 most recently modified images and videos.
- **Customizable Quick Access**: Allow users to hardcode, add, remove, and restore pinned folders to the Quick Access section on the Home screen.
- **Header Logo Integration**: Add a subtle, branded Arcile logo into the `ArcileTopBar` for stronger visual identity.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations (e.g., Lottie) for empty folders, trash, and search results.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes (e.g., heavily rounded "squircle" vs. standard rounded corners for cards and buttons).

### Browsing & Organization
- **Rich Media Previews**: Implement custom Coil `Fetcher` components to extract APK icons (using `PackageManager`) and PDF thumbnails.
  - [x] APK icons implemented (`ApkIconFetcher.kt`)
  - [ ] PDF thumbnails pending
  - Goal: Making the grid view visually rich regardless of file type.
- **Starred / Favorited Files**: Add a "Starred" section to the Home screen and a star toggle on individual files/folders.
- **Enhanced Category Browsing**: When opening a file category, display all related folders containing matching files with a tabbed or segmented navigation bar.
- **Storage Analyzer ("Filelight" view)**: A dedicated radial map or sunburst chart to visualize storage usage by folder/file type (similar to Filelight or WinDirStat).
- **Folder Subtitle Metadata**: Display a compact subtitle below each folder name showing the item count and total size (e.g., `24 items · 1.3 GB`).

### Tools & Development
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations (moves, copies, deletions) for auditing purposes.
- **Dummy File Generator**: A developer/testing tool to quickly create "fake" files of a specified size to fill space or test transfers.
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding/tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor (F-Droid ready) without any proprietary blobs or dependencies.

### Settings & About
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive "flower petal" UI. The middle acts as the dynamic color, surrounded by preset colors, which shrink dynamically on long-press into a monochrome mode.
- **Combined Changelog & Version**: Streamline the About screen by merging the version info and changelog buttons into a cleaner layout.
- **External Link Indicators**: Update the About page so all external links explicitly show an "open in browser" trailing icon.

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android's multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.

---
