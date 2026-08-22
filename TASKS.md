# Arcile - Tasks

> **Project:** Arcile
>
> **Version:** 2.0.6
>
> **Last Updated:** 2026-08-22

## Status

| Status | Count |
| :--- | ---: |
| Remaining | 8 |
| Completed | 15 |
| Total | 23 |

No critical tasks remain. The open queue contains four high-priority release/reliability tasks and four medium-priority follow-ups.

## Remaining Work

### High Priority

- [ ] **REL-0005 - Handle `dataSync` Foreground-Service Timeouts** `[High]` `[Reliability]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/BulkFileOperationService.kt`; `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/VaultImportService.kt`; `arcile-app/core/operation/android/src/main/AndroidManifest.xml`; `arcile-app/core/vault/data/src/main/AndroidManifest.xml`
  - **Problem:** The app targets API 37 and both long-running services declare `dataSync`, but neither implements the Android 15+ cumulative foreground-service timeout callback.
  - **Impact:** The system can terminate the app with an ANR/crash if a timed-out service does not stop promptly, and later service starts can be denied after quota exhaustion.
  - **Fix:** Implement `Service.onTimeout(startId, foregroundServiceType)` for both services, cancel and checkpoint work, publish an actionable paused/failed state, stop foreground/self within the platform grace period, and route qualifying operations through the user-initiated data-transfer API.
  - **Verification:** Unit tests invoke `onTimeout` and assert cancellation, journal state, notification/UI state, and timely stop; an API 35+ instrumentation test uses shortened device-config timeouts to verify no crash and a resumable user-visible outcome.

- [ ] **REL-0006 - Make Preferences Import Bounded and Transactional** `[High]` `[Reliability]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/backup/PreferencesBackupManager.kt`; `arcile-app/app/src/test/java/dev/qtremors/arcile/backup/PreferencesBackupManagerTest.kt`
  - **Problem:** Import calls unbounded `readBytes()`, base64-decodes every declared store, and replaces individual DataStore files while live singleton instances may retain stale state. Missing stores are reset and a mid-restore failure can leave a partial configuration; the current round-trip test observes `LIGHT` after restoring an expected `DARK` value.
  - **Impact:** A malformed backup can exhaust memory, and even a valid or partially failing restore can silently leave settings stale, reset, or internally inconsistent.
  - **Fix:** Stream and cap the envelope, store count, encoded/decoded store sizes, and total payload; reject duplicate or unknown stores before decoding; validate lengths and hashes; then restore through coordinated typed DataStore APIs or a closed-store atomic swap with full rollback.
  - **Verification:** Make the existing round-trip test pass against live DataStore instances, then cover oversized/duplicate/unknown/corrupt inputs and injected failure at every commit step; all rejection paths preserve the complete pre-import configuration and leave no staged files.

- [ ] **TEST-0001 - Stabilize the Robolectric and Compose App Test Runtime** `[High]` `[Testing / Release]`
  - **Location:** `arcile-app/app/src/test/java`; `arcile-app/gradle/libs.versions.toml`; `arcile-app/app/build.gradle.kts`
  - **Problem:** The app suite hits `FileSystemAlreadyExistsException` while loading the Robolectric native runtime, followed by `SQLiteConnection.nativeOpen` `UnsatisfiedLinkError` and `UncaughtExceptionsBeforeTest` cascades across preference, visual-QA, and share-helper tests. Plausible contributors are concurrent/repeated native extraction, a reused corrupt temp cache, incompatible native artifacts, and leaked asynchronous work.
  - **Impact:** The unit gate is release-blocking and non-deterministic; an early infrastructure fault obscures genuine regressions in later test classes.
  - **Fix:** Isolate native runtime initialization and temp/cache directories per fork, align Robolectric/SQLite/Compose test versions with the configured SDK, remove shared global cleanup races, and move device-dependent visual checks to instrumentation where Robolectric cannot provide deterministic native support.
  - **Verification:** From a clean test cache, run `./gradlew :app:testDebugUnitTest --no-parallel --max-workers=1` and the normal parallel suite 20 consecutive times; order-randomized individual classes and the full suite have zero native-loader, SQLite, leaked-coroutine, or cascading-before-test failures.

- [ ] **ARCH-0001 - Restore Enforced Module and Complexity Boundaries** `[High]` `[Architecture / Maintainability]`
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/ArchitectureBoundaryTest.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/category/FileCategoryLibrary.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/BrowserPreferencesDataSource.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/pdf/StandalonePdfViewer.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlayerActivity.kt`
  - **Problem:** `:app:testDebugUnitTest` currently reports `FileCategoryLibrary.kt` at 1,569 lines and 41 public-composable parameters, `BrowserPreferencesDataSource.kt` at 770 lines, and `StandalonePdfViewer.kt` at 768 lines. It also reports unapproved app imports of audio implementation APIs and route registrars.
  - **Impact:** The enforced architecture gate is red, oversized components have mixed ownership, and app-to-feature implementation coupling can spread without an explicit public contract.
  - **Fix:** Split the three over-budget files into focused state, action, rendering, and persistence components; replace the 41-parameter file-category composable contract with stable state/action objects; and expose audio launching through one deliberate feature entry-point API. Update the guardrail only for intentionally public entry points; do not increase budgets or broadly allowlist feature packages.
  - **Verification:** Run the four `ArchitectureBoundaryTest` checks for large files, composable parameters, feature public APIs, and presentation-shell imports, then run `./gradlew :app:testDebugUnitTest`; all pass without relaxed thresholds.

### Medium Priority

- [ ] **UI-0001 - Adapt the Primary Workspace to Window Size and Posture** `[Medium]` `[UI / UX]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/MainRoute.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/AdaptiveDialogs.kt`; `arcile-app/feature/home/src/main/java/dev/qtremors/arcile/feature/home/ui/HomeScreen.kt`; `arcile-app/feature/home/src/main/java/dev/qtremors/arcile/feature/home/ui/components/RecentFilesCarousel.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserScreen.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioNowPlayingScreen.kt`
  - **Problem:** The project includes Material 3 adaptive dependencies but has no window-size or posture integration; `MainRoute` always presents the phone-oriented horizontal pager, leaving large and resizable windows stretched rather than reorganized. Lint also reports eight `ConfigurationScreenWidthHeight` reads across dialog, home, audio, and image-viewer UI that can be stale for the actual window.
  - **Impact:** Tablet, foldable, landscape, desktop, and multi-window users receive a stretched phone workflow and can see incorrect sizing after live window changes.
  - **Fix:** Drive navigation and content layout from the current adaptive window information: retain the pager on compact widths, use persistent navigation and useful list/detail or dashboard panes at medium and expanded widths, react to fold posture and live multi-window resizing, and replace `LocalConfiguration.screenHeightDp` with actual window metrics.

- [ ] **I18N-0003 - Centralize Locale-Aware File-Size Formatting** `[Medium]` `[UI / UX]`
  - **Location:** `arcile-app/core/presentation/src/main/java/dev/qtremors/arcile/core/presentation/FormatFileSize.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/metadata/ImageMetadata.kt`; `arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageViewerZoomable.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesFormatting.kt`
  - **Problem:** File sizes are formatted by multiple implementations with different 1000/1024 rules; the shared formatter forces `Locale.US`, while two image implementations implicitly use the default locale and trigger `DefaultLocale` lint warnings.
  - **Impact:** The same byte count can display differently across screens, decimal separators do not consistently follow the user's locale, and duplicated boundary logic can drift.
  - **Fix:** Replace feature-local formatters with one locale-aware presentation API backed by Android's file-size formatter or an explicitly documented SI/IEC policy, localize unit labels, and inject/provide locale rather than reading it implicitly in domain logic.
  - **Verification:** Unit tests cover byte/unit boundaries and rounding in `en-US`, `de-DE`, and an RTL locale; identical values render identically in image, OnlyFiles, and shared file-list surfaces, and full lint has zero `DefaultLocale` findings.

- [ ] **COMPOSE-0001 - Remove Lint-Confirmed Compose Hot-Path Churn** `[Medium]` `[Performance]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/pdf/StandalonePdfViewer.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/EmptyStateBlobs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserScreen.kt`; `arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryScreen.kt`; `arcile-app/feature/onboarding/src/main/java/dev/qtremors/arcile/feature/onboarding/ui/OnboardingScreen.kt`
  - **Problem:** Full-project lint reports 19 boxed primitive state creations, two reads of `@FrequentlyChangingValue` during composition, and three non-lambda offset modifiers across animated viewers, onboarding, browser, gallery, storage, and shared UI.
  - **Impact:** Animation and scrolling paths create avoidable allocations and trigger composition/layout work at frame frequency, increasing jank risk on lower-end devices and large content sets.
  - **Fix:** Use primitive state factories such as `mutableFloatStateOf`/`mutableIntStateOf`, defer rapidly changing reads to draw/layout lambdas or narrowly derived state, and use lambda offset overloads so motion updates skip composition where possible.
  - **Verification:** Run full `./gradlew lintDebug` with zero `AutoboxingStateCreation`, `FrequentlyChangingValue`, and `UseOfNonLambdaOffsetOverload` findings; compare allocation and frame-timing traces for PDF paging, onboarding swipes, gallery zoom, and browser scrolling before and after.

- [ ] **STORAGE-0002 - Reserve Space for Shared-File Imports** `[Medium]` `[Data / Storage / Platform]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/SharedFileImporter.kt`
  - **Problem:** Import preflight compares declared source sizes with `File.usableSpace` and a fixed safety buffer but does not ask `StorageManager` for allocatable bytes or reserve space; unknown-size streams are bounded only after copying begins.
  - **Impact:** Imports can be rejected despite safely reclaimable cache space or can fail late after writing partial staged data when concurrent storage use invalidates the initial estimate.
  - **Fix:** Use `StorageManager.getAllocatableBytes` and `allocateBytes` for the destination UUID where supported, retain the streaming byte cap for unknown lengths, recheck before commit, and map allocation/ENOSPC failures to a specific cleanup-and-retry result.
  - **Verification:** Instrumented tests exercise sufficient, reclaimable-cache, concurrently consumed, unknown-length, and genuinely full-storage cases; failures remove staging, preserve existing destination files, and show the specific insufficient-space recovery action.

## Completed in 1.9.0

### Critical

- [x] **SEC-0001 - Keep Operation Secrets and Private Paths Out of Backups** `[Critical]` `[Security / Privacy]`
- [x] **REL-0001 - Preserve Verified Copies After Partial Source Deletion** `[Critical]` `[Reliability]`

### High

- [x] **UI-0002 - Keep Date-Range Dialogs Usable in Short Windows** `[High]` `[UI / UX]`
- [x] **A11Y-0001 - Share One Accessible Reorder Pattern Across Customization Screens** `[High]` `[UI / UX]`
- [x] **I18N-0002 - Remove the Hardcoded Audio Favorites Presentation** `[High]` `[UI / UX]`
- [x] **SEC-0002 - Enforce Archive Limits While Decompressing Single Streams** `[High]` `[Security / Privacy]`
- [x] **SEC-0003 - Bound and Clean Split-APK Staging** `[High]` `[Security / Privacy]`
- [x] **PERF-0001 - Replace Recursive File Traversals with Iterative, Cancellable Walks** `[High]` `[Performance]`
- [x] **PERF-0002 - Coalesce Foreground-Operation Progress Persistence** `[High]` `[Performance]`
- [x] **PERF-0003 - Remove Plaintext Cleanup from Main-Thread Startup** `[High]` `[Performance]`
- [x] **REL-0002 - Resolve Exported Viewer Metadata Off the Main Thread** `[High]` `[Reliability]`
- [x] **REL-0003 - Stop Replaying Terminal File-Operation Events** `[High]` `[Reliability]`
- [x] **REL-0004 - Hand Bulk Operations to Services by Durable ID** `[High]` `[Reliability]`
- [x] **STORAGE-0001 - Release One-Shot Import URI Grants** `[High]` `[Data / Storage / Platform]`

### Medium

- [x] **I18N-0001 - Pluralize User-Visible Counts Across Features** `[Medium]` `[UI / UX]`
