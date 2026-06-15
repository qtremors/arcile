# Arcile - Tasks

> **Project:** Arcile
> **Version:** 1.1.0
> **Last Updated:** 2026-06-14

---

### UI / UX Tasks
- [ ] **COMPOSE-0001 - Preserve Image Viewer State Across Recreation** `[Medium]`
  - **Location:** `arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageViewerScreen.kt` `arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageViewerZoomable.kt`
  - **Problem:** `ImageViewerScreen` collects `viewModel.state` with `collectAsState()` and keeps viewer UI state such as metadata-sheet visibility, UI chrome visibility, visual rotations, erase dialogs, and current scale in local `remember` state. Those values are lost on activity recreation and the collection is not lifecycle-aware.
  - **Impact:** Rotation, multi-window changes, process recreation, or backgrounding can reset the viewer experience and keep collecting gallery state while the screen is not active.
  - **Fix:** Use `collectAsStateWithLifecycle()` for ViewModel state, move durable viewer state into the ViewModel or `SavedStateHandle`, and use `rememberSaveable` with explicit savers only for truly screen-local values.
  - **Verification:** Add a Compose recreation test that opens an image, toggles metadata/chrome/rotation state, recreates the activity, and verifies the expected viewer state is restored; manually verify rotation and "Don't keep activities" behavior.

### Security / Privacy Tasks
- [ ] **SEC-0001 - Narrow FileProvider Grants To Staged Handoff Roots** `[High]`
  - **Location:** `arcile-app/app/src/main/res/xml/file_provider_paths.xml` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ExternalFileAccessHelper.kt` `DEVELOPMENT.md`
  - **Problem:** `file_provider_paths.xml` exposes `<external-path name="external_user_files" path="." />`, while the docs describe a staged-only FileProvider boundary. Current helper code validates direct open/share paths, but the provider authority itself can grant URIs for any external-storage file if future code calls it.
  - **Impact:** A file manager with all-files access has an unnecessarily broad sharing authority, increasing the blast radius of a helper bypass or future regression and making the documented privacy boundary inaccurate.
  - **Fix:** Remove the external-storage root from the provider paths and route outbound file access through cache-backed staged files or narrowly scoped provider roots owned by a single audited helper contract.
  - **Verification:** Add an XML regression test that fails on broad external roots, update open/share tests to prove staged handoff still works, and manually verify open-with/share flows for normal files, blocked private paths, and Android/data or Android/obb paths.

- [ ] **SEC-0002 - Exclude Local File Metadata Caches From Backup And Transfer** `[High]`
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml` `arcile-app/app/src/main/res/xml/backup_rules.xml` `arcile-app/app/src/main/res/xml/data_extraction_rules.xml` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/db/ArcileDatabase.kt` `arcile-app/app/src/test/java/dev/qtremors/arcile/core/storage/data/BackupRulesTest.kt`
  - **Problem:** Backups are enabled and the current backup rules exclude several preferences and metadata folders, but they do not exclude the Room cache database `arcile-cache.db`. That database stores local file paths, MediaStore-derived metadata, recent-file snapshots, storage-usage snapshots, cleaner snapshots, and thumbnail metadata.
  - **Impact:** Private local file metadata can be copied into Android cloud backup or device transfer, then restored as stale or sensitive storage history on another install.
  - **Fix:** Exclude the database domain or the specific `arcile-cache.db` database from both backup and device-transfer rules, and invalidate any restored cache state defensively on startup.
  - **Verification:** Extend `BackupRulesTest` to assert database exclusions in both XML files, inspect an `adb backup` or device-transfer rule dump where available, and verify a restored install starts without stale file index or thumbnail metadata.

- [ ] **SEC-0003 - Make Secure Shred Report Overwrite Failures** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileSystemDataSource.kt`
  - **Problem:** `overwriteSecurely()` returns without error when a file is not writable and catches all overwrite exceptions with an ignored failure, after which `shredDetailed()` can still delete the file and report a successful secure shred.
  - **Impact:** Users can receive a stronger privacy guarantee than the app actually performed, especially on restricted, read-only, or provider-backed storage where overwrite semantics are weak.
  - **Fix:** Make the overwrite step return a typed result, fail or downgrade the shred operation when overwriting cannot be completed and flushed, and adjust UI copy to state any platform limitations of best-effort shredding.
  - **Verification:** Add unit tests for non-writable files and injected write failures, verify failed overwrites surface as `BatchMutationFailure`, and manually confirm the destructive-action UI distinguishes secure shred from ordinary delete.

### Performance Tasks
- [ ] **PERF-0001 - Page Directory Listings Before Full Materialization** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileSystemDataSource.kt` `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/BrowserDisplayState.kt`
  - **Problem:** `FileSystemDataSource.list()` calls `directory.listFiles()`, upserts every child, sorts the full child array, converts the full list to models, and only then emits chunked pages. `BrowserDisplayState` then rebuilds derived visible lists, paths, names, and row models for the whole file set.
  - **Impact:** Very large directories can delay first paint, allocate heavily, and increase ANR or low-memory risk even though the UI is modeled as paged.
  - **Fix:** Stream or index directory children incrementally, emit the first page before all children are sorted and persisted, and move expensive row-model derivation behind visible-item boundaries or cached paging data.
  - **Verification:** Add a benchmark or stress test with a 50,000-file directory that records first-page latency, peak memory, and scroll responsiveness; verify StrictMode shows no main-thread file I/O during browsing.

### Reliability Tasks
- [ ] **REL-0001 - Route Save-To-Arcile Imports Through Durable Foreground Work** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/SaveToArcileActivity.kt` `arcile-app/app/src/test/java/dev/qtremors/arcile/SaveToArcileActivityTest.kt`
  - **Problem:** The exported share target allows imports up to 10 GB, but the actual copy runs from a Compose `rememberCoroutineScope()` with local `remember` state and direct target-file writes. It is not backed by the foreground operation service, operation journal, mutation journal, progress notification, or process-death recovery.
  - **Impact:** Rotation, task removal, background process death, or cancellation can interrupt large imports with only per-stream exception cleanup, leaving users without durable progress, retry, or recovery information and possibly leaving partial target files.
  - **Fix:** Model incoming shares as durable foreground operations or equivalent WorkManager foreground work, persist accepted URIs and selected destination safely, write through staged temp files, journal mutation checkpoints, and expose cancel/retry/cleanup progress.
  - **Verification:** Add tests proving Save-to-Arcile enqueues durable work instead of launching screen-local copies, add cancellation/process-death cleanup tests for partial files, and manually import a large file while rotating and killing the process.

- [ ] **REL-0002 - Persist Mutation Checkpoints For Interrupted Foreground Operations** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/OperationJournal.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationCoordinator.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/MutationJournal.kt`
  - **Problem:** `OperationJournalRecord` has `stagedPaths`, `rollbackHints`, and `trashResultIds`, but records are created without those fields and service progress updates do not populate them. Recovery currently marks interrupted work as `CLEANUP_REQUIRED` and cleanup only runs abandoned-temp cleanup.
  - **Impact:** After process death during copy, move, delete, trash, archive, or extract, the app cannot accurately reconcile which user-visible outputs completed, which trash records exist, or which rollback actions are still possible.
  - **Fix:** Persist operation-level mutation checkpoints from transfer, trash, archive, and extraction code into the operation journal, then make recovery actions use those checkpoints for precise cleanup, retry, or user-facing status.
  - **Verification:** Add process-kill style unit or instrumentation tests for interrupted copy, move, trash, and archive operations; verify recovered records contain staged paths, finalized outputs, rollback hints, and trash IDs as applicable.

- [ ] **REL-0003 - Reset Archive Extraction Conflict State Per Operation** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveSupport.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ZipArchiveHandler.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/TarArchiveHandler.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/SevenZipHandler.kt`
  - **Problem:** `ArchiveExtractionContext` keeps mutable `directoryAliases` and `skippedDirectories`, and each archive handler stores one context instance as a field. A skip or keep-both directory decision from one extraction can remain in memory and influence later extractions handled by the same object.
  - **Impact:** Consecutive archive operations can skip entries or redirect extracted files based on stale conflict state from a previous extraction, causing missing or misplaced output files.
  - **Fix:** Create a fresh `ArchiveExtractionContext` for each extraction or conflict-detection operation, or explicitly reset its mutable conflict state at the start of every operation.
  - **Verification:** Add regression tests that perform two consecutive extractions through the same handler where the first skips or keep-boths a directory and the second extracts matching entry names into a clean destination.

- [ ] **REL-0004 - Restore Archive Browsing State After Process Recreation** `[Medium]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/delegate/NavigationDelegate.kt` `arcile-app/feature/browser/src/test/java/dev/qtremors/arcile/feature/browser/delegate/NavigationDelegateTest.kt`
  - **Problem:** `saveNavState()` writes `archivePath` and `archiveEntryPrefix`, but `restoreLocationFromState()` immediately returns `null` when `archivePath` is present. The saved archive context is therefore ignored during state restoration.
  - **Impact:** Process recreation can drop users out of the archive folder they were browsing and lose archive-specific navigation context that was already saved.
  - **Fix:** Restore archive navigation from `SavedStateHandle` by reopening the saved archive and entry prefix, preserving any safe password or name-encoding policy separately from ordinary directory restoration.
  - **Verification:** Add a `SavedStateHandle` restoration test with `archivePath`, `archiveEntryPrefix`, and archive history; manually verify archive browsing with "Don't keep activities" enabled.

### Data / Storage / Platform Tasks
- [ ] **STORAGE-0001 - Stop Using Raw MediaStore DATA As File Identity** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/MediaStoreQueryHelpers.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ExternalFileAccessHelper.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/image/VideoThumbnailFetcher.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/image/AudioAlbumArtFetcher.kt` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/TrashManager.kt`
  - **Status:** Partially addressed: MediaStore rows now carry `StorageNodeRef` content-URI identity for several category/recent-file paths, and tests cover rows with missing or unusable `DATA`; raw `DATA = ?` lookup remains in open/share, audio/video thumbnail, and trash fallback paths.
  - **Problem:** Media fallbacks still depend on `MediaStore.*.DATA` projections or `DATA = ?` queries to resolve content URIs from raw paths, even though newer MediaStore-discovered file rows can carry content URI identity.
  - **Impact:** On modern scoped-storage devices, removable volumes, or provider-backed media, missing or stale DATA values can still break open-with, thumbnail loading, trash integration, and cache identity in fallback paths.
  - **Fix:** Finish routing media operations through `StorageNodeRef`/content URIs wherever available, remove raw-path `DATA = ?` lookups from helpers and fetchers, and keep raw path lookup only as a best-effort fallback for genuinely local files.
  - **Verification:** Extend existing missing-`DATA` tests to cover open-with, audio/video thumbnails, and trash fallbacks; verify those flows on Android 10+ scoped-storage emulators.

- [ ] **STORAGE-0002 - Treat Room Cache Schema Changes As Explicit Release Events** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/db/ArcileDatabase.kt` `arcile-app/core/storage/data/build.gradle.kts` `DEVELOPMENT.md`
  - **Problem:** `ArcileDatabase` is at version 2 with `exportSchema = false` and `fallbackToDestructiveMigration(dropAllTables = true)`, while the developer docs still describe schema version 1. Even if the database is cache-oriented, schema drift is not reviewed or tested as part of upgrades.
  - **Impact:** Release upgrades can silently drop or reshape cache tables, hide migration mistakes, and leave documentation and backup rules out of sync with stored local metadata.
  - **Fix:** Enable Room schema export, commit schemas, add explicit migration or cache-reset tests for each version change, and document when destructive cache invalidation is an intentional release choice.
  - **Verification:** Add a Room migration/cache-reset test from the previous schema to version 2, verify schema JSON is generated in source control, and add a docs check or review checklist for future schema bumps.

- [ ] **STORAGE-0003 - Preserve Original Filenames In Shared File Handoffs** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ExternalFileAccessHelper.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ShareHelper.kt` `arcile-app/app/src/test/java/dev/qtremors/arcile/presentation/utils/ExternalFileAccessHelperTest.kt` `arcile-app/app/src/test/java/dev/qtremors/arcile/presentation/utils/ShareHelperTest.kt`
  - **Problem:** Shared local files are staged as `${sha1(file.canonicalPath)}$extension` before creating the FileProvider URI. `ShareTarget.displayName` keeps the original name in app code, but the outbound `ACTION_SEND_MULTIPLE` intent only sends the staged URI stream, so receiving apps can display or save the hash-like staged filename instead of the user's original filename.
  - **Impact:** Shared files can appear renamed in target apps, which is confusing for users and can break workflows that depend on recognizable filenames.
  - **Fix:** Stage files with collision-safe original display names, provide a custom `ContentProvider` or FileProvider strategy that returns the original `OpenableColumns.DISPLAY_NAME`, and include compatible title/ClipData metadata where target apps honor it.
  - **Verification:** Add tests that shared targets expose the original display name through `ContentResolver` metadata and that multiple files with the same name remain collision-safe; manually share files to common targets and verify saved/displayed filenames match the originals.

### Testing / Release Tasks
- [ ] **TEST-0001 - Add Release Gates For Process Death And Large Storage Regressions** `[Medium]`
  - **Location:** `arcile-app/app/src/test/java/dev/qtremors/arcile/presentation/operations` `arcile-app/app/src/test/java/dev/qtremors/arcile/SaveToArcileActivityTest.kt` `arcile-app/core/storage/data/src/test/java/dev/qtremors/arcile/core/storage/data/source/FileSystemDataSourceTest.kt` `arcile-app/core/storage/data/src/test/java/dev/qtremors/arcile/core/storage/data/manager/ArchiveManagerTest.kt`
  - **Status:** Partially addressed: the local suite now includes operation recovery, mutation cleanup, archive conflict, and large-directory/listing tests; it still lacks durable Save-to-Arcile process-death coverage, operation checkpoint assertions for staged/rollback/trash metadata, same-handler archive extraction state regression tests, and first-page latency or macrobenchmark gates for very large directories.
  - **Problem:** `./gradlew check` and `./gradlew assembleRelease` pass, but the release gate still does not exercise the remaining highest-risk production-scale scenarios: Save-to-Arcile process death, operation-journal checkpoint contents, repeated archive extraction through the same stateful handler, and measured first-page latency for very large directories.
  - **Impact:** Core file-manager regressions can pass current CI/local release checks when they affect imports, precise interrupted-operation recovery, archive extraction state isolation, or performance at production scale.
  - **Fix:** Add focused Robolectric, unit, instrumentation, or macrobenchmark tests for the remaining scenarios and wire the stable ones into the standard release verification command used before publishing.
  - **Verification:** Introduce failing-first tests for each remaining scenario, confirm they fail on the current behavior, then make `check`, `connectedCheck`, or a documented release gate run the stabilized tests as appropriate.

### Documentation Tasks
- [ ] **DOC-0001 - Reconcile Storage And Handoff Documentation With Implementation** `[Low]`
  - **Location:** `DEVELOPMENT.md` `README.md` `arcile-app/app/src/main/res/xml/file_provider_paths.xml` `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/db/ArcileDatabase.kt`
  - **Problem:** The docs state that `arcile-cache.db` is schema version 1 and that FileProvider access is restricted to staged open/share folders, while the implementation uses database version 2 and currently declares a broad external FileProvider root.
  - **Impact:** Future maintainers and reviewers can make security, privacy, and migration decisions from stale architecture notes instead of the behavior shipped in the app.
  - **Fix:** Update the storage architecture and safe handoff sections after the FileProvider and Room-cache tasks are addressed, and keep docs in sync with tests or release-review checklist items.
  - **Verification:** Review `DEVELOPMENT.md` and `README.md` against the manifest, FileProvider XML, backup rules, and `ArcileDatabase`; add a lightweight doc consistency check if those sections continue to state enforceable guarantees.
