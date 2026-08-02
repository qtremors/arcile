# Arcile - Tasks

> **Project:** Arcile
> **Version:** 1.8.0
> **Last Updated:** 2026-08-02

---

### Architecture / Maintainability Tasks

- [ ] **ARCH-0001 - Restore Enforced Module and Complexity Boundaries** `[High]`
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/ArchitectureBoundaryTest.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/category/FileCategoryLibrary.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/BrowserPreferencesDataSource.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/pdf/StandalonePdfViewer.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlayerActivity.kt`
  - **Problem:** `:app:testDebugUnitTest` currently reports `FileCategoryLibrary.kt` at 1,569 lines and 41 public-composable parameters, `BrowserPreferencesDataSource.kt` at 770 lines, and `StandalonePdfViewer.kt` at 768 lines. It also reports unapproved app imports of audio implementation APIs and route registrars.
  - **Impact:** The enforced architecture gate is red, oversized components have mixed ownership, and app-to-feature implementation coupling can spread without an explicit public contract.
  - **Fix:** Split the three over-budget files into focused state, action, rendering, and persistence components; replace the 41-parameter file-category composable contract with stable state/action objects; and expose audio launching through one deliberate feature entry-point API. Update the guardrail only for intentionally public entry points; do not increase budgets or broadly allowlist feature packages.
  - **Verification:** Run the four `ArchitectureBoundaryTest` checks for large files, composable parameters, feature public APIs, and presentation-shell imports, then run `./gradlew :app:testDebugUnitTest`; all pass without relaxed thresholds.

### UI / UX Tasks

- [ ] **UI-0002 - Keep Date-Range Dialogs Usable in Short Windows** `[High]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/SearchDateRangeDialog.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/SortDateRangeSection.kt`
  - **Problem:** Both dialogs disable the platform default width and force a 580 dp height. On landscape phones, split-screen windows, and large-font configurations, controls can extend beyond the visible bounds with no reliable route to the actions.
  - **Impact:** Users can be unable to inspect or confirm a date filter on valid Android window and accessibility configurations.
  - **Fix:** Replace the fixed `580.dp` dialog height with inset-aware `heightIn` constraints derived from the current window, make the date content scroll or switch input mode when necessary, and keep confirm/cancel actions reachable above system bars and the IME.
  - **Verification:** Compose tests render and interact with both dialogs at 320 dp and 480 dp heights, portrait and landscape, 2.0x font scale, and with an IME inset; every field and action remains visible or scroll-reachable.

- [ ] **A11Y-0001 - Add Non-Drag Quick-Access Reordering** `[High]`
  - **Location:** `arcile-app/feature/quickaccess/src/main/java/dev/qtremors/arcile/feature/quickaccess/ArrangeQuickAccessDialog.kt`
  - **Problem:** The reorder handle currently exposes a description but implements movement only through `detectDragGestures`; TalkBack, Switch Access, keyboard, and other non-drag users cannot rearrange the list.
  - **Impact:** A core customization is inaccessible to users who cannot perform precise drag gestures.
  - **Fix:** Add semantic move-up and move-down actions (with equivalent focusable controls), announce the resulting position, preserve logical focus after each move, and keep pointer drag as an additional interaction.
  - **Verification:** Compose semantics tests reorder the first, middle, and last items without gestures and assert boundary-action availability, focus retention, and position announcements; manually verify with TalkBack and keyboard navigation.

- [ ] **I18N-0002 - Remove the Hardcoded Audio Favorites Presentation** `[High]`
  - **Location:** `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioLibraryPresentation.kt`; `arcile-app/feature/audio/src/main/res/values/strings.xml`
  - **Problem:** `buildAudioLibraryState` stores the literal title `"Favorites"` and matches the literal English token `"favorites"` even though `R.string.audio_favorites` already exists. `checkProductionStrings` fails at this line.
  - **Impact:** The required production-string build gate is red, the pseudo-folder title bypasses localization, and searching for the translated favorites label does not find it.
  - **Fix:** Model the favorites row with a stable folder kind/key rather than UI text, resolve its title from resources at presentation time, and pass locale-aware search aliases into filtering without introducing a `Context` dependency into the pure state builder.
  - **Verification:** Run `./gradlew checkProductionStrings`; test the favorites row title and search matching in English and one non-English locale, including a locale change while the audio library remains open.

- [ ] **UI-0001 - Adapt the Primary Workspace to Window Size and Posture** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/MainRoute.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/AdaptiveDialogs.kt`; `arcile-app/feature/home/src/main/java/dev/qtremors/arcile/feature/home/ui/HomeScreen.kt`; `arcile-app/feature/home/src/main/java/dev/qtremors/arcile/feature/home/ui/components/RecentFilesCarousel.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserScreen.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioNowPlayingScreen.kt`
  - **Problem:** The project includes Material 3 adaptive dependencies but has no window-size or posture integration; `MainRoute` always presents the phone-oriented horizontal pager, leaving large and resizable windows stretched rather than reorganized. Lint also reports eight `ConfigurationScreenWidthHeight` reads across dialog, home, audio, and image-viewer UI that can be stale for the actual window.
  - **Impact:** Tablet, foldable, landscape, desktop, and multi-window users receive a stretched phone workflow and can see incorrect sizing after live window changes.
  - **Fix:** Drive navigation and content layout from the current adaptive window information: retain the pager on compact widths, use persistent navigation and useful list/detail or dashboard panes at medium and expanded widths, react to fold posture and live multi-window resizing, and replace `LocalConfiguration.screenHeightDp` with actual window metrics.
  - **Verification:** Add screenshot/behavior tests for compact, medium, and expanded widths plus landscape, tabletop/hinge, and split-screen resize cases; manually verify navigation, selection, back behavior, pointer/keyboard use, and no clipped or empty panes on a resizable emulator.

- [ ] **I18N-0001 - Pluralize User-Visible Counts Across Features** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/res/values/strings.xml`; `arcile-app/core/ui/src/main/res/values/save_to_arcile_strings.xml`; `arcile-app/core/ui/src/main/res/values/storage_usage_strings.xml`; `arcile-app/feature/apk/src/main/res/values/strings.xml`; `arcile-app/feature/audio/src/main/res/values/strings.xml`; `arcile-app/feature/documents/src/main/res/values/strings.xml`; `arcile-app/feature/onlyfiles/src/main/res/values/strings.xml`
  - **Problem:** Full-project lint reports 38 `PluralsCandidate` resources for files, folders, items, tracks, pages, apps, operations, and storage results; formatted singular values currently produce wording such as “1 items” and cannot represent locale-specific plural categories.
  - **Impact:** Common file-management and media counts are grammatically wrong and cannot be translated correctly for languages with richer plural systems.
  - **Fix:** Convert all lint-confirmed count strings to `<plurals>` resources, resolve them with `getQuantityString`/Compose quantity resources at their call sites, and remove duplicate singular/plural formatting logic.
  - **Verification:** Run full `./gradlew lintDebug` with zero `PluralsCandidate` findings; add representative locale tests for zero, one, two, and many quantities in each owning module, including a locale with more than two plural categories.

- [ ] **I18N-0003 - Centralize Locale-Aware File-Size Formatting** `[Medium]`
  - **Location:** `arcile-app/core/presentation/src/main/java/dev/qtremors/arcile/core/presentation/FormatFileSize.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/metadata/ImageMetadata.kt`; `arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageViewerZoomable.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesFormatting.kt`
  - **Problem:** File sizes are formatted by multiple implementations with different 1000/1024 rules; the shared formatter forces `Locale.US`, while two image implementations implicitly use the default locale and trigger `DefaultLocale` lint warnings.
  - **Impact:** The same byte count can display differently across screens, decimal separators do not consistently follow the user's locale, and duplicated boundary logic can drift.
  - **Fix:** Replace feature-local formatters with one locale-aware presentation API backed by Android's file-size formatter or an explicitly documented SI/IEC policy, localize unit labels, and inject/provide locale rather than reading it implicitly in domain logic.
  - **Verification:** Unit tests cover byte/unit boundaries and rounding in `en-US`, `de-DE`, and an RTL locale; identical values render identically in image, OnlyFiles, and shared file-list surfaces, and full lint has zero `DefaultLocale` findings.

### Security / Privacy Tasks

- [ ] **SEC-0001 - Keep Operation Secrets and Private Paths Out of Backups** `[Critical]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/OperationJournal.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/MutationJournal.kt`; `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/VaultLocationRegistry.kt`; `arcile-app/core/operation/api/src/main/java/dev/qtremors/arcile/core/operation/BulkFileOperationRequest.kt`; `arcile-app/app/src/main/res/xml/backup_rules.xml`; `arcile-app/app/src/main/res/xml/data_extraction_rules.xml`
  - **Problem:** The operation journal serializes full source paths, destination paths, content URIs, and plaintext `archivePassword`. Backup rules exclude `operation_journal` under `file`/`datastore`, but the actual journals are `sharedpref/operation_journal.xml` and `sharedpref/mutation_journal.xml`; `onlyfiles_vault_locations.xml` is also not excluded. The existing backup-rule test checks only for a matching string and therefore passes despite the domain/path mismatch.
  - **Impact:** Device or cloud backups can retain vault locations, private filenames/URIs, and an archive password beyond the operation lifetime, exposing sensitive user data through an unintended backup surface.
  - **Fix:** Remove archive passwords from persistent request/journal serialization, move recoverable operation and mutation state to a bounded no-backup store, exclude the exact `sharedpref` files for any remaining sensitive registries in both backup schemas, and make recovery discard obsolete sensitive records after a defined retention period.
  - **Verification:** Assert exact `(domain, path)` exclusions for both backup formats, inspect an ADB backup/restore fixture to confirm the journals and vault locations are absent, and test that journal JSON and recovery records never contain a supplied archive password while non-secret interrupted operations still recover.

- [ ] **SEC-0002 - Enforce Archive Limits While Decompressing Single Streams** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/TarArchiveHandler.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveSupport.kt`; `arcile-app/core/storage/data/src/test/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveManagerTest.kt`
  - **Problem:** Single-stream listing currently treats the compressed file length as both compressed and uncompressed size, while extraction copies decoded bytes without an expanded-size or ratio bound.
  - **Impact:** A small crafted GZIP, BZIP2, or XZ stream can bypass the archive policy, exhaust storage, and destabilize an extraction or the device.
  - **Fix:** Wrap decoded output in a counting limiter that enforces actual expanded-byte and compression-ratio policies during reads, preflight available space, propagate cancellation, and delete the staged output on any limit violation.
  - **Verification:** With deliberately small policy limits, tests reject high-ratio GZIP/BZIP2/XZ inputs during streaming, keep memory bounded, leave no partial target, honor cancellation, and successfully extract an input exactly at the limit.

- [ ] **SEC-0003 - Bound and Clean Split-APK Staging** `[High]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/apk/ApkPackageParser.kt`; `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/apk/PackageInstallerEngine.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppApkInstallerDialog.kt`
  - **Problem:** `.apks`, `.xapk`, and `.apkm` containers currently copy every APK entry without count, size, ratio, elapsed-work, or free-space bounds into a filename-hash cache directory. No success-path owner removes `apk_staging_*`.
  - **Impact:** Attacker-controlled packages can exhaust cache storage, collide with concurrent staging, and leave installable package contents on disk indefinitely.
  - **Fix:** Give each parse/install operation a collision-safe staging directory, enforce entry/count/size/ratio/time limits, extract only validated APK entries needed by the selected install set, and delete staging on success, rejection, cancellation, failure, dialog dismissal, and next-start recovery.
  - **Verification:** Parser/installer tests cover zip bombs, excessive entry counts, duplicate/colliding filenames, concurrent installs, cancellation, failed sessions, successful sessions, and startup cleanup; every terminal path leaves no owned staging directory.

### Performance Tasks

- [ ] **PERF-0001 - Replace Recursive File Traversals with Iterative, Cancellable Walks** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageUsageScanner.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileTransferEngine.kt`
  - **Problem:** `StorageUsageScanner` still traverses every descendant after `maxDepth` (the value only limits retained UI nodes), and directory copy recursively calls itself. Very deep, huge, or cyclic trees have no traversal safety bound.
  - **Impact:** Crafted or unusually large trees can overflow the stack, monopolize an IO worker, delay cancellation, or leave a core copy/analysis flow unresponsive.
  - **Fix:** Replace recursive descent with explicit work stacks, file-identity cycle protection, prompt cancellation checks, and node/elapsed-work budgets for analysis; retain complete-copy semantics while returning a clear failure for cycles or unreadable descendants.
  - **Verification:** Tests scan and copy synthetic trees thousands of levels deep without `StackOverflowError`, terminate symlink/file-identity cycles, cancel within a bounded number of visited nodes, and return a documented partial analysis when its budget is exhausted.

- [ ] **PERF-0002 - Coalesce Foreground-Operation Progress Persistence** `[High]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/BulkFileOperationService.kt`; `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/ForegroundBulkFileOperationCoordinator.kt`
  - **Problem:** Each progress callback currently updates the journal in the coordinator and again in `updateNotification`; the second write occurs before notification throttling. Archive copies report every buffer, causing repeated full JSON serialization and SharedPreferences IO hundreds of times per second on fast transfers.
  - **Impact:** Large foreground operations waste IO, CPU, and battery, increase flash writes, and can contend with the transfer they are reporting.
  - **Fix:** Establish one progress owner and coalesce journal writes, state-flow emissions, and notification updates behind time, byte, and phase thresholds, while forcing an exact checkpoint on pause and every terminal state.
  - **Verification:** A fake-journal stress test for a 1 GiB transfer/extraction asserts writes and notifications stay below the defined per-second ceiling, collectors still receive monotonic useful progress, and the final persisted byte/phase state is exact.

- [ ] **PERF-0003 - Remove Plaintext Cleanup from Main-Thread Startup** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/ArcileApp.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/externalfile/ExternalFileAccessHelper.kt`
  - **Problem:** `ArcileApp.onCreate` synchronously calls `clearPrivatePlaintextFallbacks`, whose `deleteRecursively()` can traverse hundreds of megabytes and deeply nested content before the first frame.
  - **Impact:** A prior large share fallback can materially delay startup or cause an ANR, while simply deferring deletion without isolation would leave plaintext accessible longer.
  - **Fix:** Atomically quarantine any prior `external_access/vault_fallback` directory during startup and delete it on the injected IO scope, preventing new readers from reaching old plaintext immediately without recursively walking the cache on the main thread.
  - **Verification:** A startup StrictMode test and macrobenchmark preseed a large/deep fallback tree, assert no main-thread disk violation or material startup regression, confirm old plaintext is inaccessible immediately, and confirm asynchronous cleanup completes.

- [ ] **COMPOSE-0001 - Remove Lint-Confirmed Compose Hot-Path Churn** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/pdf/StandalonePdfViewer.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/EmptyStateBlobs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserScreen.kt`; `arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryScreen.kt`; `arcile-app/feature/onboarding/src/main/java/dev/qtremors/arcile/feature/onboarding/ui/OnboardingScreen.kt`
  - **Problem:** Full-project lint reports 19 boxed primitive state creations, two reads of `@FrequentlyChangingValue` during composition, and three non-lambda offset modifiers across animated viewers, onboarding, browser, gallery, storage, and shared UI.
  - **Impact:** Animation and scrolling paths create avoidable allocations and trigger composition/layout work at frame frequency, increasing jank risk on lower-end devices and large content sets.
  - **Fix:** Use primitive state factories such as `mutableFloatStateOf`/`mutableIntStateOf`, defer rapidly changing reads to draw/layout lambdas or narrowly derived state, and use lambda offset overloads so motion updates skip composition where possible.
  - **Verification:** Run full `./gradlew lintDebug` with zero `AutoboxingStateCreation`, `FrequentlyChangingValue`, and `UseOfNonLambdaOffsetOverload` findings; compare allocation and frame-timing traces for PDF paging, onboarding swipes, gallery zoom, and browser scrolling before and after.

### Reliability Tasks

- [ ] **REL-0001 - Preserve Verified Copies After Partial Source Deletion** `[Critical]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileTransferEngine.kt`
  - **Problem:** Both cross-volume move paths copy and verify the destination, call `deleteRecursively()` on the source, and delete the destination when that call returns false or throws. Recursive deletion can remove some source children before failing.
  - **Impact:** Rolling back the complete destination after a partial source deletion permanently loses every source child that was already removed.
  - **Fix:** Make source deletion resumable at file granularity and never roll back a fully verified destination after irreversible deletion has begun; report the remaining source paths through recovery state so cleanup can be retried safely.
  - **Verification:** Inject a source-delete implementation that removes several children and then fails; tests assert the verified destination remains complete, remaining source paths are recoverable, retry is idempotent, and a failure before any source deletion still rolls back the staged destination.

- [ ] **REL-0002 - Resolve Exported Viewer Metadata Off the Main Thread** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/ImageViewerActivity.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/PdfViewerActivity.kt`; `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerActivity.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlayerActivity.kt`
  - **Problem:** These exported `ACTION_VIEW`/browsable activities synchronously call `ContentResolver.getType` and/or `query` in `onCreate`; the video query is also not exception-contained.
  - **Impact:** A slow, dead, or hostile content provider can freeze first render, crash the external-open flow, or trigger a main-thread ANR.
  - **Fix:** Move MIME and display-name queries plus readable-descriptor validation to an injected IO dispatcher with lifecycle cancellation, a bounded timeout, and explicit loading/error UI before constructing viewer state.
  - **Verification:** Activity tests use delayed, throwing, null-cursor, revoked, and process-death providers; the first frame remains responsive, cancellation closes descriptors/cursors, failures render a recoverable error, and StrictMode reports no main-thread provider/disk access.

- [ ] **REL-0003 - Stop Replaying Terminal File-Operation Events** `[High]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/ForegroundBulkFileOperationCoordinator.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/delegate/BrowserOperationController.kt`
  - **Problem:** The coordinator uses `MutableSharedFlow(replay = 1)` for terminal events. A newly created controller can consume an old completion, refresh unrelated content, show stale success, and expose an undo action for an operation it never initiated.
  - **Impact:** Rotation, navigation, or process-level controller recreation can present misleading results and let the user undo an unrelated prior operation.
  - **Fix:** Make completed/failed/cancelled feedback non-replaying, retain only active/recovery state in durable `StateFlow`s, and attach monotonic operation IDs plus ownership/consumption checks before creating undo actions or messages.
  - **Verification:** Recreate each operation-consuming controller/view model after completion, failure, and cancellation; no stale message, refresh, or undo appears, while an attached collector receives each live terminal event exactly once and recovery state still restores.

- [ ] **REL-0004 - Hand Bulk Operations to Services by Durable ID** `[High]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/ForegroundBulkFileOperationCoordinator.kt`; `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/BulkFileOperationService.kt`; `arcile-app/core/operation/api/src/main/java/dev/qtremors/arcile/core/operation/BulkFileOperationRequest.kt`
  - **Problem:** `startRequest` JSON-encodes every source path, destination, import item, and conflict resolution into `EXTRA_REQUEST_JSON`, with no general selection/path-size limit.
  - **Impact:** A large user selection can exceed Binder transaction limits before the foreground service starts, lose the requested operation, and create oversized journal writes.
  - **Fix:** Validate request item/path limits, atomically store the bounded request in the no-backup operation store, send only its operation ID through the service intent, and let the service claim, checkpoint, and retire that record idempotently.
  - **Verification:** Tests start, recover, and complete a maximum-size request containing thousands of long paths without a large intent extra, reject requests above documented limits before service launch, and prove duplicate delivery of one ID does not execute twice.

- [ ] **REL-0005 - Handle `dataSync` Foreground-Service Timeouts** `[High]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/BulkFileOperationService.kt`; `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/VaultImportService.kt`; `arcile-app/core/operation/android/src/main/AndroidManifest.xml`; `arcile-app/core/vault/data/src/main/AndroidManifest.xml`
  - **Problem:** The app targets API 37 and both long-running services declare `dataSync`, but neither implements the Android 15+ cumulative foreground-service timeout callback.
  - **Impact:** The system can terminate the app with an ANR/crash if a timed-out service does not stop promptly, and later service starts can be denied after quota exhaustion.
  - **Fix:** Implement `Service.onTimeout(startId, foregroundServiceType)` for both services, cancel and checkpoint work, publish an actionable paused/failed state, stop foreground/self within the platform grace period, and route qualifying operations through the user-initiated data-transfer API.
  - **Verification:** Unit tests invoke `onTimeout` and assert cancellation, journal state, notification/UI state, and timely stop; an API 35+ instrumentation test uses shortened device-config timeouts to verify no crash and a resumable user-visible outcome.

- [ ] **REL-0006 - Make Preferences Import Bounded and Transactional** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/backup/PreferencesBackupManager.kt`; `arcile-app/app/src/test/java/dev/qtremors/arcile/backup/PreferencesBackupManagerTest.kt`
  - **Problem:** Import calls unbounded `readBytes()`, base64-decodes every declared store, and replaces individual DataStore files while live singleton instances may retain stale state. Missing stores are reset and a mid-restore failure can leave a partial configuration; the current round-trip test observes `LIGHT` after restoring an expected `DARK` value.
  - **Impact:** A malformed backup can exhaust memory, and even a valid or partially failing restore can silently leave settings stale, reset, or internally inconsistent.
  - **Fix:** Stream and cap the envelope, store count, encoded/decoded store sizes, and total payload; reject duplicate or unknown stores before decoding; validate lengths and hashes; then restore through coordinated typed DataStore APIs or a closed-store atomic swap with full rollback.
  - **Verification:** Make the existing round-trip test pass against live DataStore instances, then cover oversized/duplicate/unknown/corrupt inputs and injected failure at every commit step; all rejection paths preserve the complete pre-import configuration and leave no staged files.

### Data / Storage / Platform Tasks

- [ ] **STORAGE-0001 - Release One-Shot Import URI Grants** `[High]`
  - **Location:** `arcile-app/feature/import/src/main/java/dev/qtremors/arcile/feature/importing/SaveToArcileActivity.kt`; `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/BulkFileOperationService.kt`; `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/SharedFileImporter.kt`
  - **Problem:** `SaveToArcileActivity` takes persistable grants from incoming intents, but the import pipeline never calls `releasePersistableUriPermission`.
  - **Impact:** Repeated shares can retain access to user documents indefinitely, consume the persisted-grant quota, and eventually prevent later imports from retaining needed access.
  - **Fix:** Record only persistable read grants acquired for a Save-to-Arcile import and release each grant after success, rejection, failure, cancellation, or abandoned-operation recovery; keep Quick Access/document-tree grants outside this ownership path.
  - **Verification:** Provider-backed tests assert balanced take/release calls for every terminal outcome and process-recovery path, assert no release for grants the app did not acquire, and confirm Quick Access roots remain readable.

- [ ] **STORAGE-0002 - Reserve Space for Shared-File Imports** `[Medium]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/SharedFileImporter.kt`
  - **Problem:** Import preflight compares declared source sizes with `File.usableSpace` and a fixed safety buffer but does not ask `StorageManager` for allocatable bytes or reserve space; unknown-size streams are bounded only after copying begins.
  - **Impact:** Imports can be rejected despite safely reclaimable cache space or can fail late after writing partial staged data when concurrent storage use invalidates the initial estimate.
  - **Fix:** Use `StorageManager.getAllocatableBytes` and `allocateBytes` for the destination UUID where supported, retain the streaming byte cap for unknown lengths, recheck before commit, and map allocation/ENOSPC failures to a specific cleanup-and-retry result.
  - **Verification:** Instrumented tests exercise sufficient, reclaimable-cache, concurrently consumed, unknown-length, and genuinely full-storage cases; failures remove staging, preserve existing destination files, and show the specific insufficient-space recovery action.

### Testing / Release Tasks

- [ ] **TEST-0001 - Stabilize the Robolectric and Compose App Test Runtime** `[High]`
  - **Location:** `arcile-app/app/src/test/java`; `arcile-app/gradle/libs.versions.toml`; `arcile-app/app/build.gradle.kts`
  - **Problem:** The app suite hits `FileSystemAlreadyExistsException` while loading the Robolectric native runtime, followed by `SQLiteConnection.nativeOpen` `UnsatisfiedLinkError` and `UncaughtExceptionsBeforeTest` cascades across preference, visual-QA, and share-helper tests. Plausible contributors are concurrent/repeated native extraction, a reused corrupt temp cache, incompatible native artifacts, and leaked asynchronous work.
  - **Impact:** The unit gate is release-blocking and non-deterministic; an early infrastructure fault obscures genuine regressions in later test classes.
  - **Fix:** Isolate native runtime initialization and temp/cache directories per fork, align Robolectric/SQLite/Compose test versions with the configured SDK, remove shared global cleanup races, and move device-dependent visual checks to instrumentation where Robolectric cannot provide deterministic native support.
  - **Verification:** From a clean test cache, run `./gradlew :app:testDebugUnitTest --no-parallel --max-workers=1` and the normal parallel suite 20 consecutive times; order-randomized individual classes and the full suite have zero native-loader, SQLite, leaked-coroutine, or cascading-before-test failures.

- [ ] **TEST-0002 - Add Baseline Profiles and User-Journey Macrobenchmarks** `[Medium]`
  - **Location:** `arcile-app/app`; `arcile-app/settings.gradle.kts`; `arcile-app/gradle/libs.versions.toml`
  - **Problem:** There is no benchmark or baseline-profile module despite startup disk cleanup, large file lists, paging transitions, and media/document viewers being core user journeys. Unit and screenshot tests cannot catch startup, jank, or profile regressions on production-compiled code.
  - **Impact:** Performance regressions and poor ahead-of-time coverage can reach release without a measurable gate on the app's most frequent journeys.
  - **Fix:** Add a benchmark/baseline-profile module for cold and warm launch, opening a large browser directory, switching primary destinations, and launching image/PDF/audio/video viewers; consume the generated profile in release builds and set regression thresholds for startup and frame timing.
  - **Verification:** Run the journeys on a pinned physical or managed profileable device, confirm the release APK contains the generated baseline profile, and demonstrate a deliberately injected startup delay and scroll-jank regression fail the recorded thresholds.
