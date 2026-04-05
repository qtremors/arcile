# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.5.7
> **Last Updated:** 2026-04-05
> **Source:** Engineering audit refresh based on the current repository state, passing JVM tests, successful debug compilation, failing `:app:assembleRelease`, and failing `:app:lintDebug`.

---

## Remediation Backlog

### Critical
- [ ] [Severity: Critical] [Category: Build/Release] Unblock release builds by fixing the invalid backup XML and aligning it with the app's actual backup policy - Location: `arcile-app/app/src/main/res/xml/backup_rules.xml`, `arcile-app/app/src/main/res/xml/data_extraction_rules.xml`, `arcile-app/app/src/main/AndroidManifest.xml` - Problem: the backup files use the unsupported `domain="cache"` value, and `allowBackup="false"` means this backup configuration is dead weight anyway. - Impact: `./gradlew.bat :app:assembleRelease` currently fails in `lintVitalRelease`, so the app cannot produce a releasable APK from the checked-in project state. - Fix: remove or correct the invalid excludes, and either delete the unused backup XML references or enable a valid, intentional backup strategy. - Verification: `./gradlew.bat :app:assembleRelease` succeeds cleanly.

### High
- [ ] [Severity: High] [Category: Correctness/Reliability] Harden foreground bulk copy/move orchestration against service-start failure and start/cancel races - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationCoordinator.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/delegate/ClipboardDelegate.kt` - Problem: the coordinator marks an operation active before `startForegroundService` is proven to succeed, does not roll back on launch failure, and cancellation is sent without tying it to a started request. - Impact: users can get stuck state, failed cancellations, or a crashing paste flow if service startup is rejected or a cancel arrives before the service has attached the request. - Fix: make service launch transactional, attach cancellation to an operation id, surface launch failure back into state, and verify cancellation from the service side before clearing UI state. - Verification: add tests for immediate cancel-after-paste and simulated service-start failure, and confirm the UI ends in a consistent idle/error state.
- [ ] [Severity: High] [Category: Performance] Stop the Home dashboard from recomputing full storage analytics on every resume and storage-volume emission - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/home/HomeViewModel.kt` - Problem: `onResumeRefresh()` and `observeStorageVolumes()` both drive `loadHomeData(HomeRefreshMode.SILENT)`, and that method still performs recent-file queries plus global and per-volume storage analytics. - Impact: opening the home screen, returning to it, or seeing volume state churn can trigger repeated heavy MediaStore/stat queries, increasing resume latency, battery use, and the chance of visible jank on slower devices. - Fix: split cheap refreshes from expensive analytics, add staleness/TTL guards, and prevent duplicate initial or resume-triggered reloads. - Verification: instrument repository call counts and trace a home-screen resume to confirm a single cheap refresh unless storage insights are actually stale.
- [ ] [Severity: High] [Category: Performance/Startup] Move storage-volume discovery off the main-thread singleton initialization path - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/data/provider/VolumeProvider.kt` - Problem: `DefaultVolumeProvider.init` eagerly calls `discoverPlatformVolumes()`, which performs `canonicalPath` resolution and `StatFs` reads before any async boundary. - Impact: cold-start and ViewModel creation can block on slow external media and raise ANR or startup-jank risk. - Fix: make `activeStorageRoots` and volume discovery lazy/async, and only refresh them from IO-backed paths. - Verification: collect a startup trace and confirm no `StatFs` or storage canonicalization work runs on the main thread during first composition.
- [ ] [Severity: High] [Category: Performance/Storage] Bound and clean the staged-file cache used for open/share flows - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ExternalFileAccessHelper.kt` - Problem: every open/share operation copies the full source file into `cacheDir/external_access/...` with no eviction, reuse policy, or size cap. - Impact: repeated opens/shares of large media can silently consume gigabytes of cache space and push the app or device into storage pressure. - Fix: reuse staged files when possible, evict old entries with a size budget, and clean per-purpose staging directories before or after use. - Verification: repeatedly open/share a large file and confirm cache growth stays bounded.

### Medium
- [ ] [Severity: Medium] [Category: Correctness/UX] Standardize search completeness so Arcile does not silently omit matches - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/MediaStoreClient.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/recentfiles/RecentFilesViewModel.kt` - Problem: folder search hard-stops after `1000` results or `10000` visited nodes, MediaStore-backed search hard-stops at `500` rows, and Recent Files search only filters whatever pages were already loaded. - Impact: users can get incomplete or inconsistent search results across Home, Browser, and Recent Files without any indication that results were truncated or local-only. - Fix: define a single search contract, add pagination or an explicit partial-results flag, and either broaden Recent Files search to repository-backed search or label it as local filtering. - Verification: populate a large synthetic dataset and confirm search either returns all matches or clearly reports partial results.
- [ ] [Severity: Medium] [Category: Correctness/Date-Time] Recompute "Today" and "Yesterday" anchors when the day or timezone changes - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/home/HomeViewModel.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/recentfiles/RecentFilesViewModel.kt` - Problem: both ViewModels snapshot day-boundary timestamps once during initialization and keep using them for grouping/filtering until the ViewModel is recreated. - Impact: leaving the app open across midnight or a timezone change can mislabel recents and hide/show the wrong files. - Fix: derive boundaries from a clock-aware helper and refresh them on resume, date change, or scheduled boundary rollover. - Verification: keep the app alive across midnight or change timezone and confirm the sections update without requiring process recreation.
- [ ] [Severity: Medium] [Category: UI/Rendering] Replace unstable Lazy list/grid keys with stable identifiers - Location: `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt`, `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt` - Problem: several screens key items by `absolutePath + index` or `absolutePath + hashCode`, which changes under sorting, refreshes, and metadata updates. - Impact: Compose can lose item identity, producing avoidable list churn, brittle animations, and more visible scroll-state instability during resorting or reloads. - Fix: use stable keys such as `absolutePath` for files and explicit ids for trash items. - Verification: sort and refresh long lists while profiling recomposition and confirm item identity remains stable.
- [ ] [Severity: Medium] [Category: Testing] Add regression coverage and CI gates for the flows that escaped the current suite - Location: `arcile-app/app/src/test`, `arcile-app/app/src/androidTest`, `arcile-app/app/build.gradle.kts` - Problem: the project has solid JVM coverage for repository/ViewModel basics, but it does not currently gate release assembly/lint, foreground bulk-operation lifecycle edge cases, or cross-midnight recent-file grouping. - Impact: regressions like the current release-blocking backup XML and service-orchestration edge cases can survive until manual validation. - Fix: add CI tasks for `:app:assembleRelease` and lint, plus targeted tests for bulk-operation start/cancel behavior and date-boundary refresh logic. - Verification: CI fails when those regressions are reintroduced.

### Low
- [ ] [Severity: Low] [Category: Documentation/Maintainability] Refresh the repo docs and backlog so they match the actual code and test surface - Location: `DEVELOPMENT.md`, `TASKS.md` - Problem: the current docs and task file still claim missing data-layer tests and unresolved audit items that no longer reflect the repository state. - Impact: future contributors can waste time on already-closed issues and miss the real risks that remain. - Fix: keep the architecture/testing docs and remediation backlog in sync with the checked-in code after each audit or release-prep pass. - Verification: the documented test surface and outstanding tasks match the current source tree and build results.

---

## Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **Advanced Storage Access (SAF)**: Implement a `DocumentFile` / Storage Access Framework fallback DataSource specifically for restricted directories (`Android/data`, `Android/obb`), allowing users to modify app data folders like top-tier file managers.
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.
- **Storage Health Diagnostics**: Basic S.M.A.R.T. status checks, disk health metrics, and repair/trim suggestions for mounted volumes.

### File Operations & Automation
- **File/Folder Properties Dialog**: Display detailed metadata for selected items.
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser.
- **Automated Task Rules**: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.
- **Operation Queue & FAB Manager**: Implement a bank for operations. A running operation replaces the floating action button with a progress ring. Tapping it shows a queue of paused/running tasks with the ability to gracefully cancel.

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` where a main action regularly needs a secondary dropdown or overflow action.
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press, successful file operations, and error states.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally scrollable carousel of the 10 most recently modified images and videos.
- **Customizable Quick Access**: Allow users to hardcode, add, remove, and restore pinned folders in the Quick Access section on the Home screen.
- **Header Logo Integration**: Add a subtle Arcile logo into the `ArcileTopBar` for stronger visual identity.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations such as Lottie for empty folders, trash, and search results.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Browsing & Organization
- **Rich Media Previews**: Implement custom Coil `Fetcher` components to extract APK icons and PDF thumbnails.
- [ ] PDF thumbnails pending
- **Starred / Favorited Files**: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Enhanced Category Browsing**: When opening a file category, display all related folders containing matching files with tabbed or segmented navigation.
- **Storage Analyzer ("Filelight" view)**: A dedicated radial map or sunburst chart to visualize storage usage by folder and file type.
- **Folder Subtitle Metadata**: Display a compact subtitle below each folder name showing item count and total size.

### Tools & Development
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations for auditing purposes.
- **Dummy File Generator**: A developer/testing tool to quickly create fake files of a specified size to fill space or test transfers.
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.

### Settings & About
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive flower petal UI.
- **Combined Changelog & Version**: Streamline the About screen by merging the version info and changelog buttons into a cleaner layout.
- **External Link Indicators**: Update the About page so all external links explicitly show an open-in-browser trailing icon.

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
