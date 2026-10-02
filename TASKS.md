# Arcile - Tasks

> **Project:** Arcile
>
> **Version:** 2.1.7
>
> **Last Updated:** 2026-10-03

---

## Storage and Recovery

- [x] **STORAGE-0001 - Preserve Verified Trash Copies After Partial Source Cleanup** `[Critical]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/DefaultTrashManager.kt` -> `moveToTrashTargets`, `restoreFromTrash`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/MutationJournal.kt` -> `cleanupTrashFallback`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileTransferEngine.kt` -> `moveToTarget`.
  - **Problem:** If rename fails, the transfer engine copies and verifies a directory before deleting its source children. It deliberately retains the complete destination when a later child deletion fails or cancellation interrupts cleanup. Both trash callers then delete that retained destination on failure. Startup recovery likewise deletes a trash payload whenever the original source directory still exists, including a partially deleted source. For a folder containing `a.txt` and `b.txt`, successful deletion of `a.txt` followed by failed deletion of `b.txt` can therefore destroy the only remaining copy of `a.txt`.
  - **Impact:** Irreversible file loss during fallback trash or restore operations and their recovery.
  - **Resolution (2.1.7):** Let the transfer engine own rollback, preserving retained trash/restore outputs after source cleanup fails or is cancelled. Keep trash metadata and the fallback record with retained payloads. Persist the trash cleanup phase alongside the source-cleanup record; recovery also recognizes older source-cleanup entries independently of processing order.
  - **Verification:** All 42 focused trash-manager, mutation-journal, and transfer-engine tests pass. New manager tests fail rename, delete one child, then fail or cancel cleanup for both trash and restore; recreated journals and repeated recovery preserve every original byte and identifiable trash metadata. Additional tests cover pre-publication rollback, legacy record ordering, and durable phase retention. Physical-device interruption testing remains outstanding.

- [x] **STORAGE-0002 - Keep Existing Destinations When a Move Fails Before Publication** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileTransferEngine.kt` -> `moveFiles`, `moveToTarget`, `copyAtomically`, `promoteStagedTarget`.
  - **Problem:** The move catch blocks call `deleteTarget(target)` whenever source cleanup has not started. When replacing an existing file, a staging read/write failure leaves the original destination untouched inside `copyAtomically`, but the outer catch deletes it. The same happens after failed promotion successfully restores the old destination. `moveToTarget` also deletes an existing target after rejecting the conflict.
  - **Impact:** A failed move can erase a previously existing destination even though no replacement was published.
  - **Resolution (2.1.7):** Return an owned publication from staging instead of deleting destination paths unconditionally. Roll back only that output and restore its original backup before source cleanup starts. Keep backups through incomplete source cleanup, and require full checksum verification before replacement publication.
  - **Verification:** All 17 focused transfer-engine tests pass, including staging failure/cancellation, failed promotion, occupied direct targets, verification failure after publication, retained backups after partial source cleanup, and same-sized replacement corruption. Original source and destination bytes survive pre-cleanup failures. Physical-device testing remains outstanding.

- [x] **STORAGE-0003 - Journal Replacement Backups as Recoverable Originals** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileTransferEngine.kt` -> `promoteStagedTarget`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/MutationJournal.kt` -> `recordTemporaryPath`, `cleanupTemporaryPath`, `isKnownTemporaryName`.
  - **Problem:** Replacement renames the old target to `.arcile-replace-*.bak`, recorded as an ordinary temporary path. If the process dies after that rename and before staged promotion, startup cleanup deletes both the staging file and the backup. The destination remains absent and its previous contents are lost. The backup record contains no original destination or publication phase.
  - **Impact:** Interrupted copy/replace can permanently lose the previous destination, independently of the in-process move failure in STORAGE-0002.
  - **Resolution (2.1.7):** Journal the destination, staging path, original backup, verified contents, and publication/source-cleanup phases. Restore verified originals into absent destinations; retain ambiguous backups and unavailable-storage records. Migrate legacy backup entries conservatively, and preserve full or unreadable journals instead of discarding recovery information.
  - **Verification:** Focused journal tests cover interruption at five publication boundaries, failed rollback, unavailable storage, move rollback before source cleanup, partial-cleanup restart, legacy backups, and restoration without stable file IDs. Complete original or verified new destination bytes remain available, with ambiguous originals retained. Windows tests inject file IDs for automatic-recovery scenarios; physical-device testing remains outstanding.

- [x] **STORAGE-0004 - Revalidate Pending Source Cleanup Before Deleting Files** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/MutationJournal.kt` -> `recordSourceCleanup`, `cleanupSourceCleanup`; sibling `SourceTreeCleanup.kt` -> `deleteSourceTree`.
  - **Problem:** Recovery records only source and destination paths. On a later launch it deletes the current source tree if the destination merely exists. After an interrupted move, a user can modify the destination or add a new source child while Arcile is closed; recovery then deletes data never verified in the destination.
  - **Impact:** Automatic recovery can remove newly created or changed files without a valid surviving copy.
  - **Resolution (2.1.7):** Persist bounded snapshots of verified source/destination nodes, including identities, file metadata, and checksums. Recovery revalidates each recorded node and its ancestors, deletes only matched files and empty recorded folders, and retains new/changed files and pending records. Legacy records and unavailable file identities never authorize automatic source deletion.
  - **Verification:** Focused journal tests cover changed destination bytes with matching size/timestamp, identical-byte destination replacement, added source children, changed source contents, replaced source folders, missing destinations, legacy records, and unavailable IDs. Unmatched data and recovery records survive repeated recovery. Windows identity-dependent tests use an injected provider; physical-device testing remains outstanding.

- [ ] **STORAGE-0005 - Undo Restore Using Actual Published Paths** `[High]`
  - **Location:** `arcile-app/feature/trash/src/main/java/dev/qtremors/arcile/feature/trash/TrashViewModel.kt` -> `restoreTrashItems`, `restoreToDestination`, `undoLastRestore`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/DefaultTrashManager.kt` -> `restoreFromTrash`.
  - **Problem:** Restoring `report.txt` beside an existing `report.txt` publishes the restored item under a `.restore-conflict-<timestamp>` name. The ViewModel records the original path for Undo and then trashes that path, removing the unrelated existing file while leaving the restored file in place. Destination-picker restoration also guesses output names instead of receiving the published paths.
  - **Impact:** Undo performs a destructive action on the wrong file. The misplaced file is recoverable from trash, but the requested undo is not performed.
  - **Fix:** Return per-item restore results with actual output identities/paths from the manager and repository. Use only successful, verified outputs for Undo, with identity validation if the target changes afterward.
  - **Verification:** Restore beside a conflicting file, invoke the snackbar Undo, and assert the existing file is unchanged while only the restored output returns to trash. Cover destination selection, partial success, and a target changed before Undo.

- [ ] **STORAGE-0006 - Make Text Saves Recoverable Before Truncating the Original** `[High]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt` -> `writeAndVerifyTextFile`, `persistVerifiedText`, `readMatchingDraft`.
  - **Problem:** Local saves call `File.writeText` directly; provider saves open `"wt"`. Both truncate before verification. A partial write leaves the original damaged. The draft is not a reliable recovery fallback: on reopen, `readMatchingDraft` deletes it if the source hash differs, which is exactly what a truncated save causes.
  - **Impact:** Low storage, write failure, or interruption can lose the original document and then discard the recoverable edited draft.
  - **Fix:** Use a verified sibling staging file and atomic replacement for local paths. For providers, preserve a durable draft/recovery copy before writing and report the provider's actual guarantees. Retain mismatching drafts for explicit recovery instead of silently deleting them.
  - **Verification:** Inject short writes, write exceptions, and interruption before verification for local and provider targets. Reopening must offer the complete draft, and local originals must survive failed publication. A successful save must still verify the persisted bytes.

## Vaults

- [x] **VAULT-0001 - Preserve Skipped Source Children During Directory Moves** `[Critical]`
  - **Location:** `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/VaultTransferEngine.kt` -> `cloneMergedDirectory`, `moveOneWithinVault`; sibling `VaultTransferLayer.kt` -> `transferAcrossVaults`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesTransferController.kt` -> `paste`.
  - **Problem:** Move a folder into a destination containing a same-named folder, select Merge, then Skip for a conflicting child with different contents. The merged clone keeps only the destination child. Within-vault move removes the entire source folder and marks all source objects obsolete; cross-vault move receives `COMPLETED` from `copyOne` and deletes the entire source folder. Neither path retains skipped source children.
  - **Impact:** Choosing Skip can permanently destroy the source version of an encrypted file.
  - **Resolution (2.1.7):** Track skipped source nodes and their ancestor folders during merges. Publish source manifests removing only transferred nodes, in the destination transaction for within-vault moves and after destination publication for cross-vault moves. Preserve skipped objects, return partial outcomes, and retain the move clipboard when skipped files remain.
  - **Verification:** All 9 focused vault merge-move, transfer-coordinator, and transaction tests pass, plus the transfer-controller UI test. New tests use distinct encrypted source/destination contents, nested skipped files and entire skipped folders, fresh sessions, complete merges, interrupted commit recovery, and safe rejection of an oversized partial merge. Skipped bytes remain readable, transferred children leave the source, and existing destination bytes remain unchanged. Physical-device testing remains outstanding.

- [ ] **VAULT-0002 - Validate Transaction Limits Before Writing a Durable Commit Marker** `[High]`
  - **Location:** `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/VaultTransactionManager.kt` -> `commit`, `writeMarker`, `readMarker`, `validate`; sibling `VaultImportEngine.kt` -> `importOne` and `VaultTransferEngine.kt` -> `CloneContext.commit`.
  - **Problem:** `validate` accepts at most 32 publications, but `commit` writes the durable marker before calling `readMarker`, which performs that validation. Importing a directory tree with 32 directories prepares those 32 manifests plus the destination manifest. The marker is written, rejected on reread, and deliberately retained as committed. Subsequent recovery rejects the same marker, including on unlock. Object-count and encoded-root limits have the same write/read asymmetry.
  - **Impact:** An ordinary folder import or copy can leave a vault unable to recover or unlock through the normal flow. This is an availability defect; underlying encrypted data is not proven destroyed.
  - **Progress (2.1.7):** Writer-side validation now applies the reader's limits before committing, protecting partial merge moves that require extra source manifests. Recovery for already-written oversized markers remains unresolved, so this task stays open.
  - **Fix:** Validate all writer/reader limits before the durable commit point. Support larger trees with a bounded transaction representation or reject them safely before publication. Provide a data-preserving recovery path for already-written oversized markers; do not simply delete a possibly committed marker.
  - **Verification:** Exercise 31, 32, and 33 imported directories, copying an equivalent tree, and boundary object/root sizes. Failure must leave no unrecoverable marker and must allow relock/unlock. Include recovery of a marker produced by the current implementation. Existing transaction tests do not cover these limits.

## Lifecycle and Resource Use

- [ ] **REL-0001 - Prevent Viewer Processes from Cleaning Live Operations** `[High]`
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml` -> `:imageviewer`, `:pdfviewer`, `:texteditor` activities; `arcile-app/app/src/main/java/dev/qtremors/arcile/ArcileApp.kt` -> `onCreate`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/MutationJournal.kt` -> `cleanupAbandonedMutations`.
  - **Problem:** Every application process unconditionally starts shared mutation cleanup. Opening a standalone viewer while the main process is copying a file creates another `ArcileApp` instance, which can delete the active `.arcile-transfer-*` file from the shared journal. The lock is process-local and entries carry no live owner. The same initialization also performs shared APK-staging and plaintext-fallback cleanup.
  - **Impact:** Launching a viewer can interrupt active storage work or remove a handoff still in use. Replacement and trash recovery hazards compound the impact.
  - **Fix:** Give shared recovery a single process owner and coordinate it with operation startup. Distinguish live work from abandoned work using durable ownership/liveness, including explicit recovery actions. Keep viewer-process startup from cleaning resources owned by another live process.
  - **Verification:** With a disposable build, pause an operation after journaling staging, cold-start each standalone viewer, and verify staging and recovery records remain intact. Repeat for APK staging and vault fallback handoffs. Separately verify genuine process-death cleanup. Process isolation follows [Android's process model](https://developer.android.com/guide/components/processes-and-threads), checked 2026-09-26; no device reproduction was available.

- [ ] **REL-0002 - Keep Document Contents Out of Activity Saved State** `[High]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt` -> `textState`, `sessionInitialized`, load effect.
  - **Problem:** The complete document is held in `rememberSaveable(..., stateSaver = TextFieldValue.Saver)`, including read-only documents. Loading has no size limit. Opening a multi-megabyte text file therefore places its contents in the activity's saved-state payload when backgrounded or recreated; the separate disk draft does not prevent that serialization.
  - **Impact:** Large documents can exceed Android's saved-state transaction budget and fail activity state saving. This is source-established unbounded payload growth, not a measured crash threshold.
  - **Fix:** Save only a document/draft identifier and bounded cursor/scroll metadata. Retain editing state across configuration changes in an appropriate state owner and reload a durable draft after process death. Ensure initialization flags cannot suppress content restoration.
  - **Verification:** Inspect the saved-state payload after loading and editing a multi-megabyte document; its size must remain bounded independently of document length. Test recreation and actual process death separately, including read-only documents and unsaved edits. Follow [Compose state-saving guidance](https://developer.android.com/develop/ui/compose/state-saving), checked 2026-09-26.

- [ ] **PERF-0001 - Dispatch Biometric Preparation and Recovery Off the Main Thread** `[Medium]`
  - **Location:** `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/VaultSessionLayer.kt` -> `prepareBiometricEnrollment`, `prepareBiometricUnlock`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesAdministrationController.kt`; sibling `OnlyFilesVaultAdministration.kt` -> `showBiometricPrompt`.
  - **Problem:** Enrollment is launched in `viewModelScope` and calls the synchronous header/password decoder without switching dispatchers. The decoder runs Argon2 with a default 64 MiB cost and three iterations. Authentication completion runs in `lifecycleScope`; its callback performs transaction recovery, object hashing, and manifest reads on the same thread. Normal password unlock already switches to `dispatchers.io`, but these biometric paths do not.
  - **Impact:** Biometric setup and recovery can block UI input and rendering. ANR duration and device timings have not been measured.
  - **Fix:** Make the repository's biometric preparation and completion main-safe using injected dispatchers, preserve cancellation, and serialize session publication with the existing lifecycle coordination. Keep only prompt presentation and UI callbacks on Main.
  - **Verification:** Assert dispatcher use around header decoding and recovery with injected test dispatchers. On a test device, check responsiveness for enrollment and unlock with a pending transaction, including cancellation and background locking.

- [ ] **PERF-0002 - Bound Text Loading and Undo Memory** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt` -> `readTextFileContent`, `updateTextWithHistory`, `undoStack`, `redoStack`.
  - **Problem:** Local files and provider streams are read completely with `readText`, without a byte/character budget. Editing retains up to 50 complete previous document strings in each history stack. A large log opened through the exported editor can consume memory proportional to the full input and many document-sized revisions; unknown provider sizes bypass no additional guard because none exists.
  - **Impact:** Large inputs can exhaust the viewer process and lose recent edits. No heap or timing measurements were collected.
  - **Fix:** Enforce an actual counted-read limit with clear large-file feedback or a bounded viewing mode, and bound undo history by retained bytes or use edit deltas. Apply limits to unknown-length streams as well as declared file sizes. Coordinate restoration with REL-0002.
  - **Verification:** Use a large local text file and an unknown-length test provider. Reads must stop at the documented budget with recoverable UI feedback; repeated edits must keep history memory bounded while preserving ordinary undo/redo behavior.

- [ ] **PERF-0003 - Show Browser Files Before Persistence and Indexing Finish** `[High]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserDirectoryNavigation.kt` -> `loadDirectory`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/FilePreferencesDataSource.kt` -> `updateLastOpenedLocation`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/DefaultFileSystemDataSource.kt` -> `list`.
  - **Problem:** Every folder opening awaits a DataStore location write and activity-log append before listing. The data source then reads and sorts the whole directory, validates each child path, and deletes/upserts its database index before emitting the first 256-item page. Cached folder-stat lookup also precedes publishing that page. These operations run off Main where appropriate, but the user still waits for all of them before files appear.
  - **Impact:** Folder opening can feel delayed, especially for large directories, slow storage, or a growing activity log. No tap-to-first-file timing has been measured.
  - **Fix:** Keep the location change immediate, publish usable files without waiting for activity logging or index maintenance, and load saved folder stats without holding back the first page. Preserve ordered last-location writes and eventual search-index consistency. Measure whether full sorting and per-child safety checks need further reduction without weakening path validation.
  - **Verification:** Measure tap-to-first-file and completed-list times for empty, small, and large folders on internal and removable storage. Check rapid folder switching, cancellation, restart restoration, activity history, search-index updates, inaccessible paths, and correct list ordering.

- [ ] **PERF-0004 - Avoid Rebuilding the Full Browser Display for Each Folder-Stat Update** `[High]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/BrowserDisplayState.kt` -> `buildBrowserDisplayState`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserNavigationController.kt` -> `updateFolderStat`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/BrowserNavigationPreferences.kt` -> `applyNavigationPreferences`.
  - **Problem:** Display-state construction sorts the file list twice and builds both list and grid rows on Main. Each completed subfolder-size calculation triggers another full rebuild; preference emissions can also rebuild the display even when only the remembered location changes. Saved sizes are refreshed after a cold launch, so this can repeat while a folder remains visible.
  - **Impact:** Main-thread work can stutter scrolling, taps, and navigation after the listing appears. The updated debug log reports 53 skipped startup frames and another 34 later, but contains no folder-tap markers that attribute either stall to this path.
  - **Fix:** Skip display rebuilds for unchanged presentation settings, batch or update folder-stat rows incrementally, and prepare only the active view mode. Preserve deliberate reordering for file-count sort modes and cancel obsolete work when changing folders.
  - **Verification:** Profile Main-thread time during first listing, repeated folder-stat updates, sorting, and list/grid switching. Test many subfolders, rapid navigation, file-count sorting, selection, scroll restoration, and saved subtitle behavior.

- [ ] **PERF-0005 - Trace Startup and Navigation Frame Drops on a Device** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/MainRoute.kt` -> `HorizontalPager`; `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/MainShellCoordinator.kt` -> `animateTo`; browser listing and thumbnail paths.
  - **Evidence:** A 2026-09-27 debug log records 53 skipped frames near startup and 34 about six seconds later, alongside runtime compilation and image/PDF thumbnail work. It does not timestamp folder taps or identify the blocked Main-thread call. No device was connected for tracing.
  - **Investigation:** Capture a system trace with markers for page taps, folder taps, preference writes, directory enumeration, index writes, display-state construction, and first rendered files. Compare cold and warmed debug runs with a representative optimized build. Check whether pager animation, browser initialization, or thumbnail concurrency overlap with missed frames before changing their behavior.
  - **Verification:** Record frame times and tap-to-visible-content latency for Home-to-Browser, nested folder open/back, large media folders, and repeated warm navigation. Apply a targeted fix only after the trace identifies the responsible work, then repeat the same scenarios.

## UI and Accessibility

- [ ] **UI-0001 - Normalize Reversed Markdown Selections Before Formatting** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt` -> `insertFormatting`, formatting-toolbar callback.
  - **Problem:** Formatting uses `selection.start` and `selection.end` directly in `substring` and `replaceRange`. A backward selection such as `TextRange(8, 3)`, reachable with Shift+Left or reverse dragging, has start greater than end and throws instead of formatting the selected text.
  - **Impact:** A normal editor interaction can crash the screen and interrupt editing.
  - **Fix:** Use the normalized minimum and maximum selection offsets for slicing, replacement, and cursor calculation while preserving collapsed-selection behavior.
  - **Verification:** Exercise forward, backward, and collapsed selections through Bold, Italic, and Link actions. The same text must be formatted for either selection direction, and undo must restore the previous text.

- [ ] **A11Y-0001 - Resource the Shared File-Item Accessibility Labels** `[Low]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/lists/FileItemSemantics.kt` -> `fileItemSemantics`; `arcile-app/build-logic/src/main/kotlin/dev/qtremors/arcile/buildlogic/ArcileBuildVerificationPlugin.kt` -> `checkProductionStrings`.
  - **Problem:** Shared rows build spoken descriptions and actions from hardcoded English strings including Hidden, Folder, Modified, Toggle selection, and Open folder. These bypass resources. The production-string check passes because these labels use conditional expressions and `label` assignments outside its matching rules.
  - **Impact:** These core TalkBack announcements cannot follow localized resources, and the existing string gate does not detect regressions in this path. Actual screen-reader behavior was not tested.
  - **Fix:** Pass resource-resolved labels into the semantics helper or resolve them in its composable callers. Cover these specific semantics patterns in the existing string check without flagging non-user-facing animation labels.
  - **Verification:** Check hidden files, folders, and selected/unselected rows under a pseudo-locale and TalkBack. Verify localized action names, a single coherent description, and unchanged activation behavior. Add a focused negative fixture for the production-string check.

## Maintainability

- [ ] **ARCH-0001 - Separate Cleaner Version Detection from Scan Orchestration** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageCleanerScanner.kt` -> `DefaultStorageCleanerScanner`, `parseFilenameVersion`, `findFilenameVersionFamilies`, `parseManifestPackageAndVersion`, `extractApkPackageInfo`.
  - **Problem:** This 1,118-line production file combines traversal/progress, snapshot caching, duplicate hashing, risk classification, filename-version ranking, and binary APK manifest parsing. Version heuristics and binary parsing are embedded in the scanner alongside deletion-candidate policy, making isolated rule changes and tests harder to maintain. It is explicitly allowlisted up to 1,150 lines, so length alone is not a rule violation.
  - **Impact:** Changes to version ranking or APK parsing require editing the same class that coordinates scan safety and resource limits.
  - **Fix:** Extract filename parsing/ranking and family formation into `CleanerVersionFamilyDetector.kt`, and binary manifest/package reading into `CleanerApkVersionReader.kt`. Preserve the injected APK resolver, keep implementation details internal, and leave traversal/cancellation/progress ownership in the scanner.
  - **Verification:** Run the existing cleaner scanner and filename-version tests, including camera/document-number exclusions, same-package APK grouping, duplicate suffixes, and retained-newest selection. Run the architecture boundary check after extraction; do not change candidate selection behavior.

## Requested Features and Workflow Improvements

These additions come from the user's idea list and were checked against the current source on 2026-09-26. They describe requested capabilities or extensions, not confirmed defects. Existing partial support is noted; verification steps below are future acceptance checks.

- [ ] **FEAT-0001 - Edit the Browser Path Bar** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/Breadcrumbs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserDirectoryNavigation.kt`.
  - **Verified current behavior:** Breadcrumbs renders clickable segments without text entry; directory navigation accepts paths.
  - **Requested change:** Allow typing/pasting accessible directory paths with validation, clear errors, and cancellation.
  - **Verification:** Test spaces, denied/missing directories, removable storage, and retaining the original location on cancel/error.

- [ ] **FEAT-0002 - Pin Individual Files in Quick Access** `[Requested feature]`
  - **Location:** `arcile-app/feature/quickaccess/src/main/java/dev/qtremors/arcile/feature/quickaccess/QuickAccessAddSurfaces.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessDestinationMapper.kt`.
  - **Verified current behavior:** Custom-path and SAF additions target folders; the mapper has no individual-file opening branch.
  - **Requested change:** Add file shortcuts routed through the normal opener, with missing/renamed-target handling.
  - **Verification:** Pin and reopen files after restart; shortcut removal must not delete files.

- [ ] **FEAT-0003 - Add Local File and Folder Tags with Tag Search** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/db/ArcileDatabase.kt`; `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/QuickAccessItem.kt`.
  - **Verified current behavior:** The inspected database stores storage/cache records and Quick Access stores shortcuts. No user-defined file-tag model was found.
  - **Requested change:** Persist tags and assignments, expose assignment/removal and tag search, and define rename/move/delete/backup behavior.
  - **Verification:** Tag files/folders, combine tag and filename search, restart, and move targets without changing file contents.

- [ ] **FEAT-0004 - Add Virtual Albums and Groups of Folders** `[Requested feature]`
  - **Location:** `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/MediaGalleryFolderPresentation.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/MediaGalleryFoldersGrid.kt`.
  - **Verified current behavior:** Albums derive physical parent folders and a special Favorites album; arbitrary named memberships/groups are absent from the inspected presentation.
  - **Requested change:** Persist virtual albums and separately named folder groups. Membership changes must not move/delete physical files.
  - **Verification:** Create cross-folder albums/groups, restart, and update memberships while preserving originals.

- [ ] **FEAT-0005 - Add Triggered File-Operation Rules** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`.
  - **Verified current behavior:** Cleaner rules describe sections/exclusions; Browser operations are interactive. No automation-rule/scheduler implementation was found.
  - **Requested change:** Add opt-in folder rules with new-file/scheduled/manual triggers, extension/size/age/name filters, and move/rename/compress/clean/notify actions. Include preview, exclusions, cancellation, collisions, and loop prevention. Resolve STORAGE-0001 through STORAGE-0004 before automating destructive operations.
  - **Verification:** Use disposable camera videos; repeated events/restart must not duplicate work, process incomplete writes, or delete unmatched files.

- [ ] **FEAT-0006 - Extend Cleaner Rules with Combined File Predicates** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`; `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/CleanerSectionSettingsDialog.kt`.
  - **Verified current behavior:** Existing rules support exclusions, large-file thresholds, and old-download age, but not reusable combinations of positive folder/extension/size/age/name predicates.
  - **Requested change:** Extend the existing rule model/editor with combined predicates and match previews while preserving exclusions/defaults.
  - **Verification:** Check size/age boundaries, nested paths, exclusions, and migration of saved rules.

- [ ] **FEAT-0007 - Save Folder-Specific Action Presets** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/Breadcrumbs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`; `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`.
  - **Verified current behavior:** Navigation and archive/cleaner actions exist; no saved per-folder action-preset model was found.
  - **Requested change:** Save actions such as send to SD, compress here, clean old files, or share recent files. Reuse operation/rule infrastructure with affected-file previews.
  - **Verification:** Persist different presets for two folders; verify scope, denied destinations, and cancellation.

- [ ] **FEAT-0008 - Share Images with Metadata Removed from a Copy** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ShareHelper.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`.
  - **Verified current behavior:** ShareHelper prepares URI targets without a metadata-removal option; eraseMetadata is a separate editing action.
  - **Requested change:** Offer sanitized-copy sharing that preserves displayed orientation and original bytes, with managed temporary output. Do not fabricate EXIF.
  - **Verification:** Inspect received metadata, original bytes, multiple images, unsupported formats, cancellation, and cleanup.

- [ ] **FEAT-0009 - Modify Entries in Existing ZIP Archives** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ArchiveRepository.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ZipArchiveHandler.kt`; `arcile-app/feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerViewModel.kt`.
  - **Verified current behavior:** The contract lists, extracts, and creates archives but has no add/remove-entry operation. Selected extraction already exists.
  - **Requested change:** Add selected-file insertion and entry removal for supported ZIPs using staged replacement; report format/password restrictions.
  - **Verification:** Validate reopened output and ensure interruption leaves the original archive usable.

- [ ] **FEAT-0010 - Allow Explicit Names for Both Sides of a Paste Conflict** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ConflictResolution.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/PasteConflictDialog.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileConflictDetector.kt`.
  - **Verified current behavior:** Resolutions are KEEP_BOTH, REPLACE, and SKIP, without user-provided names.
  - **Requested change:** Allow validated names for incoming and existing items. Recheck collisions during execution and journal renames.
  - **Verification:** Test two custom names, folders, duplicate names, cancellation, failure, and retry without losing either original.

- [ ] **FEAT-0011 - Offer Content Comparison Alongside Name Conflicts** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileConflictDetector.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageCleanerScanner.kt`.
  - **Verified current behavior:** Paste detects an existing same-named target; cleaner duplicate/version logic is not exposed as paste comparison.
  - **Requested change:** Keep collisions separate from optional duplicate/version suggestions. Use size prefilters and cancellable hashing for equality; label heuristic version matches.
  - **Verification:** Distinguish same-name/different-content, different-name/equal-content, and equal-size/different-content pairs without automatic deletion.

- [ ] **FEAT-0012 - Add an Appearance Shape Preference** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/theme/UiPreferences.kt`; `arcile-app/feature/settings/src/main/java/dev/qtremors/arcile/feature/settings/ui/SettingsAppearanceSection.kt`.
  - **Verified current behavior:** UiPreferences and Appearance settings expose no user-selectable shape preference.
  - **Requested change:** Persist a shape option and apply it through shared shapes, including selected states, while preserving touch targets.
  - **Verification:** Inspect buttons, rows, menus, and dialogs after restart in both themes and large text.

- [ ] **FEAT-0013 - Select the Filename Stem When Renaming** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/RenameDialog.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/FileNameInput.kt`.
  - **Verified current behavior:** RenameDialog uses a String field without selection state and does not override FileNameInput's false autoFocus default.
  - **Requested change:** Focus rename, show the keyboard, and initially select the stem. Handle folders/dotfiles/extensionless names without resetting user-adjusted selection.
  - **Verification:** Verify selection, extension preservation, hardware keyboards, and REPORT-0002's mini-player scenario.

- [ ] **FEAT-0014 - Offer a Numeric Keyboard for Vault Password Entry** `[Requested feature]`
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesDialogs.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesCreateDialog.kt`.
  - **Verified current behavior:** Password fields use KeyboardType.Password without a layout option.
  - **Requested change:** Offer a numeric keyboard with an alphanumeric fallback; keep this an input preference rather than changing encryption.
  - **Verification:** Test create/confirm/unlock/change-password flows with both layouts without storing plaintext passwords.

- [ ] **FEAT-0015 - Configure Vault Auto-Lock and Background Retention** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/ArcileApp.kt`; `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/DefaultVaultSecurityPreferences.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesDialogs.kt`.
  - **Verified current behavior:** Background handling invokes lockAll; persisted vault security settings currently contain screenshot protection only.
  - **Requested change:** Add an explicit lock policy retaining current defaults. Define background retention, screen-off, exit, process-death, and grant behavior without persisting unlocked keys.
  - **Verification:** Exercise lifecycle transitions and manual lock; chosen policy must hold and manual lock must revoke access.

- [ ] **FEAT-0016 - Send Browser Selections Directly into OnlyFiles** `[Requested feature]`
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesScreen.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesTransferController.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserClipboardController.kt`.
  - **Verified current behavior:** OnlyFiles already imports local files/folders and has its own clipboard; inspected Browser sources lack a vault-selection action.
  - **Requested change:** Choose/unlock a vault from Browser and reuse boundary transfers. Distinguish copy/move and use typed clipboard references. Resolve VAULT-0001 and VAULT-0002 before extending destructive paths.
  - **Verification:** Import selected files/folders with conflicts/cancellation; remove originals only for verified moves and preserve existing vault paste.

- [ ] **FEAT-0017 - Schedule Settings Backups and Discover Restore Candidates** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/backup/PreferencesBackupManager.kt`; `arcile-app/feature/onboarding/src/main/java/dev/qtremors/arcile/feature/onboarding/OnboardingRoute.kt`.
  - **Verified current behavior:** Export/preview/restore and an onboarding picker exist; recurring export and backup-location discovery are absent from these flows.
  - **Requested change:** Add opt-in scheduled settings export to a selected destination and discover compatible backups in authorized locations. Retain preview/validation; do not imply file/vault-content backup.
  - **Verification:** Verify scheduled export, revoked access, discovery, malformed backups, and previewed restoration.

- [ ] **FEAT-0018 - Save Image Rotation, Mirroring, and Freeform Crops** `[Requested feature]`
  - **Location:** `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerZoomable.kt`.
  - **Verified current behavior:** rotateViewerImage changes viewerRotationDegrees/saved state rather than image bytes. No mirror/freeform-crop operation was found in the inspected gallery flow.
  - **Requested change:** Add transform preview and explicit save-copy/replace. Distinguish persisted edits from viewer rotation and support crop transparency in suitable formats. Use safe publication as in STORAGE-0006.
  - **Verification:** Reopen saved pixels elsewhere; verify orientation, edges, originals, cancellation, and large images.

- [ ] **FEAT-0019 - Export Rotated and Mirrored Videos** `[Requested feature]`
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerOverflowMenu.kt`; `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Verified current behavior:** Inspected controls have no persisted rotate/mirror export operation.
  - **Requested change:** Add transform preview and explicit export with progress/cancellation and safe publication. Distinguish playback orientation from saved edits; do not promise instant re-encoding.
  - **Verification:** Verify saved orientation, dimensions, audio synchronization, cancellation, and original preservation.

- [ ] **FEAT-0020 - Add a Vertical Video-to-Video Browsing Mode** `[Requested feature]`
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Verified current behavior:** The existing VerticalPager switches video and metadata surfaces rather than successive videos.
  - **Requested change:** Add optional vertical media navigation, resolving gesture conflicts with metadata/seek/brightness/volume while preserving the existing mode.
  - **Verification:** Swipe between videos, reverse, rotate, and background; verify item identity and release of inactive players.

- [ ] **FEAT-0021 - Expand Comparison to Synchronized Documents and Audio Metadata** `[Requested feature]`
  - **Location:** `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/StorageCleanerDuplicateCompareSheet.kt`.
  - **Verified current behavior:** Cleaner comparison presents candidate previews/properties, not synchronized document/audio comparison.
  - **Requested change:** Add two independently usable panes with vertical split, optional synchronized document scrolling, and audio metadata comparison.
  - **Verification:** Compare unequal documents, resize, toggle synchronization, and compare audio metadata without modifying files.

- [ ] **FEAT-0022 - List Installed User and System Apps Separately from APK Files** `[Requested feature]`
  - **Location:** `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryViewModel.kt`; `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryScreen.kt`.
  - **Verified current behavior:** The APK feature builds a file library via SearchRepository and does not enumerate installed packages in inspected sources.
  - **Requested change:** Add installed-user/system-app views alongside APK files, with visibility-aware icons/actions.
  - **Verification:** Verify package/file distinction, refresh after install/uninstall, restricted visibility, and missing icons.

- [ ] **FEAT-0023 - Add HTML Editing and an Explicit Preview Mode** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/MarkdownRenderer.kt`.
  - **Verified current behavior:** The inspected editor handles text/Markdown; no HTML preview/highlighting integration was found.
  - **Requested change:** Add HTML highlighting/formatting and explicit preview. Define script/network/local-resource access; build on STORAGE-0006 and REL-0002.
  - **Verification:** Edit/save/reopen markup and verify unsaved edits, malformed HTML, resources, and the defined execution/access policy.

- [ ] **FEAT-0024 - Add Book and Comic Reading to Documents** `[Requested feature]`
  - **Location:** `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryViewModel.kt`.
  - **Verified current behavior:** Documents has file browsing/PDF presentation but no dedicated EPUB/comic reading flow in the inspected feature.
  - **Requested change:** Define initial formats and add reading position, navigation, layout preferences, and bounded decoding. Keep external projects as implementation references.
  - **Verification:** Open supported books/comics, restore position, and handle malformed/large archives and unsupported formats.

- [ ] **FEAT-0025 - Create a PDF from Selected Images** `[Requested feature]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`.
  - **Verified current behavior:** No PDF-creation implementation was found in the inspected Browser/Documents sources.
  - **Requested change:** Add selection-based export with image ordering, page size/orientation, destination, and cancellation.
  - **Verification:** Reopen output and verify order, dimensions, quality, cancellation, and no partial published file.

- [ ] **FEAT-0026 - Extend the Existing Plugin API for Editor Workflows** `[Requested feature]`
  - **Location:** `arcile-app/plugin-api/src/main/java/dev/qtremors/arcile/plugin/api/PluginContract.kt`; `arcile-app/core/plugin/android/src/main/java/dev/qtremors/arcile/core/plugin/android/PluginManager.kt`.
  - **Verified current behavior:** The existing public file action is VIEW_FILE; no editor result/save contract exists.
  - **Requested change:** Define editing capabilities, negotiation, writable-copy/output handling, results/cancellation, and access lifetime for reusable clients. Preserve viewer compatibility and document trust.
  - **Verification:** Use a sample editor from Arcile and another client; verify incompatibility, cancellation, revoked access, and crash-safe output.

- [ ] **FEAT-0027 - Design an FTP Storage Plugin Contract** `[Requested feature]`
  - **Location:** `arcile-app/plugin-api/src/main/java/dev/qtremors/arcile/plugin/api/PluginContract.kt`; `arcile-app/core/plugin/android/src/main/java/dev/qtremors/arcile/core/plugin/android/PluginManager.kt`.
  - **Verified current behavior:** The current API is a viewer handoff; no FTP implementation was found.
  - **Requested change:** Define a storage-provider capability for listing/streaming/transfers/authentication/reconnect/cancellation before FTP implementation. Distinguish secure transport options and redact credentials.
  - **Verification:** Prototype with a disposable server and interrupted/large transfers; never assume remote paths are local.

- [ ] **FEAT-0028 - Add Optional Category Launcher Entries** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml`; `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt`.
  - **Verified current behavior:** The inspected app manifest exposes the main launcher and viewer/share entry points, without configurable category aliases.
  - **Requested change:** Allow separate launcher entries for supported categories, initially Gallery, with normal permissions/back navigation.
  - **Verification:** Enable/disable, cold-launch, deny permissions, and navigate Back without duplicate/stale state.

- [ ] **FEAT-0029 - Add Play Folder to Browser** `[Requested feature]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserClipboardController.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlaybackController.kt`.
  - **Verified current behavior:** AudioPlaybackController.playQueue exists; no Browser play-folder entry point was found.
  - **Requested change:** Build a deterministic supported-media queue from a folder action; define subfolder inclusion and ordering and reuse playback.
  - **Verification:** Test mixed files, starting item, queue order, empty folders, and renamed/deleted entries.

- [ ] **FEAT-0030 - Document Features and Practical Workflows** `[Requested feature]`
  - **Location:** `README.md`.
  - **Verified current behavior:** README gives a feature overview; no detailed workflow guide/per-category capability matrix was found in the documentation inventory.
  - **Requested change:** Add a guide/table covering hidden actions, audio library/player/editor distinctions, vaults, archives, backup, and plugins. Link it without broad wording changes; describe shipped behavior only.
  - **Verification:** Follow each workflow, label limitations, and exclude planned features from claims of current availability.

- [ ] **FEAT-0031 - Use Compact Paste-Status Text** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/category/CategoryLibraryContent.kt`.
  - **Verified current behavior:** The shared clipboard plural says '%d item(s) ready to paste'. The user reports clipping; it has not been visually reproduced.
  - **Requested change:** Use compact pluralized item counts in constrained toolbars and retain clear paste/action semantics for accessibility.
  - **Verification:** Check narrow windows, large fonts, long translations, and large item counts without clipping adjacent controls.

- [ ] **FEAT-0032 - Use Tap-to-Trash and Hold-for-Options** `[Requested feature]`
  - **Location:** `arcile-app/core/presentation/src/main/java/dev/qtremors/arcile/core/presentation/delegate/DeleteFlowDelegate.kt`.
  - **Verified current behavior:** requestDeleteSelected evaluates storage policy then opens a trash/permanent/mixed confirmation; no distinct tap-versus-hold route exists in this delegate.
  - **Requested change:** For trash-capable selections, tap moves to trash with Undo and long-press opens the detailed deletion card. Preserve confirmation for irreversible/non-trash paths and provide an accessible way to open options. Resolve STORAGE-0001 and STORAGE-0005 first.
  - **Verification:** Test tap, long-press, accessibility actions, mixed storage, partial success, and Undo of the actual affected items.

## User-Reported Behavior to Verify

The reports below have relevant source paths and bounded reproduction plans. They have not been reproduced on a device in this follow-up; the investigation must establish the cause before a fix is claimed.

- [x] **REPORT-0001 - Preserve Folder Subtitles Across Launches and Refreshes** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/FolderStatsStore.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserDirectoryNavigation.kt`.
  - **Root cause:** Observer registration scheduled cache invalidation on every process launch. Invalidation deleted saved stats, failed scans replaced known values with unavailable results, and the row renderer used generic folder labels when stats were missing.
  - **Resolution:** Remove registration-time invalidation; mark saved stats stale on actual changes while retaining their values; preserve usable counts after scan failures; render explicit pending/unavailable subtitles. Home also restores saved recents/category totals and avoids duplicate resume refreshes and recurring storage loading animations.
  - **Verification:** Focused folder-store, observer, category-cache, recent-snapshot, Home ViewModel, Compose presentation, and browser folder-stat tests pass. Coverage includes persisted values after store recreation, stale values during refresh, failed scans, real notification invalidation, and non-generic subtitles. Physical-device startup timing and large-folder scan counts remain unmeasured.

- [ ] **REPORT-0002 - Investigate Audio Mini-Player Blocking Rename and Back** `[Medium]`
  - **Location:** `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioLibraryScreen.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioLibraryPlayerBars.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/FileNameInput.kt`.
  - **Evidence:** The user reports blocked keyboard/back gestures and a crash while the mini-player is present. AudioLibraryScreen owns predictive back and rename-dialog state; the mini-player owns pointer gestures. Source review alone does not establish the crash cause.
  - **Investigation:** Reproduce rename with collapsed/expanded playback and capture the stack trace, focus owner, IME state, and back-handler dispatch. Correct the responsible interaction before redesigning player placement; coordinate with FEAT-0013.
  - **Verification:** Rename, type, cancel/confirm, use predictive Back, and dismiss/expand playback without stuck input or crashes on a disposable device build.

- [ ] **REPORT-0003 - Investigate Rainbow Artifacts During Video Playback** `[Medium]`
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Evidence:** The user reports intermittent rainbow regions. Playback owns player-surface attachment and loaded/rendered-path state; no affected media sample or device reproduction was available.
  - **Investigation:** Record device/codec/HDR format and reproduce with a nonprivate sample. Isolate decoder output versus surface reuse/composition and test the smallest correction; avoid blanket codec fallbacks without evidence.
  - **Verification:** Replay the failing sample across seek, pause, next/previous, rotation, and background transitions; retain a reproduction fixture or document unsupported-device limits.

- [ ] **REPORT-0004 - Investigate OnlyFiles Viewers Closing on Rotation** `[Medium]`
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesScreen.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesViewModel.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/ArcileApp.kt`.
  - **Evidence:** The user reports rotation closes viewers. Viewer state is ViewModel-owned and is cleared when the selected vault is no longer unlocked; application background handling also locks vaults. This does not prove rotation triggers that path.
  - **Investigation:** Trace configuration recreation, route identity, vault-lock events, and internal versus external viewer handoffs. Preserve the active item through recreation while retaining intentional locking behavior; coordinate with FEAT-0015.
  - **Verification:** Rotate image/text/video viewers in both directions, with settings/pickers open; distinguish configuration change from real background/process death and verify both policies.

- [ ] **REPORT-0005 - Investigate APK and Documents Grid-Size Controls** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/category/CategoryLibraryViewOptions.kt`; `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryScreen.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`.
  - **Evidence:** The user reports a broken slider. Shared options translate a slider index to column count and gridMinCellSize, while category screens consume persisted presentation; no visual failure was reproduced.
  - **Investigation:** Trace available width, padding, index conversion, preference updates, and actual grid-cell sizing in both categories. Establish whether the control, persistence, or layout disagrees and fix that specific path.
  - **Verification:** Each enabled slider position produces the advertised column count; test resize/rotation, restart, narrow screens, and independent item/folder presentation.

- [ ] **REPORT-0006 - Investigate Browser Location Restoration by Entry Route** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/MainShellCoordinator.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserNavigationController.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessDestinationMapper.kt`.
  - **Evidence:** The user reports swipe and storage-bar entry behavior is reversed. Navigation has restorePersistentLocation, seedInitialPathHistory, explicit paths, and remembered folder preferences.
  - **Investigation:** Trace gestures versus explicit storage-root navigation. Require swipe entry to resume the last browser folder and storage-bar entry to open its selected root; keep explicit shortcut/deep-link paths authoritative.
  - **Verification:** Exercise both entries after visiting a nested folder, switching volumes, and restarting; check Back history and inaccessible remembered folders.

- [ ] **REPORT-0007 - Investigate Playback Queue and Viewer Deletion Consistency** `[Medium]`
  - **Location:** `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlaybackController.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioQueueSheet.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`.
  - **Evidence:** The user reports mismatched queue/file lists and failure to advance after deletion. Audio derives queue IDs from the player timeline. Image applyViewerDelete removes the item but falls back to the first remaining displayed item, so existing deletion handling must be examined rather than assumed absent.
  - **Investigation:** Reproduce sorted/filtered/shuffled playback and deletion of the current item in each affected viewer. Compare actual queue IDs, displayed order, and completion callbacks; define next-item behavior at the last item and on failed deletion.
  - **Verification:** Delete first/middle/last items and test failed deletion, shuffle, filtered lists, and externally removed files. UI and playback must agree, advance only on success, and close gracefully when empty.

- [ ] **REPORT-0008 - Investigate Markdown Overlay Opacity and Sizing** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt`.
  - **Evidence:** The user reports translucent/oversized overlays. The editor composes controls and a full-size animated information-sheet host; the responsible overlay and visual failure were not established.
  - **Investigation:** Reproduce with long Markdown, formatting controls, overflow menus, and document information at different IME/font/window sizes. Make the relevant card opaque and content-sized while retaining scrolling/insets for long content.
  - **Verification:** Verify text behind the card does not impair reading, cards use only needed space, controls remain reachable, and dismissal/Back works with the keyboard.

- [ ] **REPORT-0009 - Investigate Stale APK Confirmation Status** `[Medium]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/apk/PackageInstallerEngine.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/apk/ApkInstallStatusReceiver.kt`.
  - **Evidence:** onUserConfirmationRequested sets 'Awaiting user confirmation...' at 95%; state changes next on a terminal result. There is no intermediate session reconciliation in the inspected engine, matching the user's stale-label report without proving installation itself is stuck.
  - **Investigation:** Reproduce accept/cancel and trace session callbacks/result broadcasts and activity return. Represent waiting versus active installation truthfully using observable state, reconcile recreated UI, and avoid claiming success before the terminal result.
  - **Verification:** Accept and cancel installs, include slow installs and process/UI recreation, and ensure the label and final success/failure follow actual session state.

## Audit Coverage

Audit completed 2026-09-26, with inspection and checks across 2026-09-23 through 2026-09-26, against `b11b150d5d4607768bee038b2fa67ba1d99386b1` (2.1.0). The working tree was clean at initial inspection and on continuation. Findings above are source-based unless a completed check is explicitly identified. Only this backlog is changed.

| Area | Inspected scope | Evidence/checks | Gaps or not applicable |
|---|---|---|---|
| User idea-list follow-up, 2026-09-26 | Checked current implementation at `05f37c25277428a2d4bf44c498efe7ce0acc0d19`: browser/navigation, clipboard/conflicts, archive contract, cleaner rules, Quick Access, gallery/albums/transforms, audio/video controls, OnlyFiles/session settings, APK install state, editor, shared grid options, backup/onboarding, database, plugins and docs | Added requested extensions with existing support identified and bounded investigations for user-reported behavior; source inspection and repository searches only. Working tree was clean before this follow-up. | No builds, tests, device reproduction, or new dependency/platform feasibility claims in this follow-up. Existing selective extraction, split-APK installation, vault paste/import, settings restore, folder-stat persistence, and plugin API were not duplicated as missing features. Ambiguous phrases, unspecified integrations, generic cleanup questions, and claims not established by inspection were omitted. Earlier audit checks below were not rerun. |
| Architecture/modularity | All 36 included modules inventoried; app composition, core runtime/navigation/presentation/operation/storage/vault/UI, plugin API/UI, testing helpers, and all 19 feature modules | Settings/build dependency declarations; representative ViewModels, controllers, DI, typed routes, repository contracts, and architecture-test rules | Source inspection, not an exhaustive review of every implementation or dependency edge; architecture test was read, not executed |
| Maintainability/naming/comments/file size | First-party Kotlin line-count scan; cleaner, preferences, accent selector, playback surface; source-cleanup contracts and build-convention naming | Production files over 700 lines: cleaner 1,118, accent selector 784, playback surface 746, preferences 726; checked explicit allowlists | No arbitrary size-only tasks; not every name/comment individually reviewed |
| Startup/navigation/permissions | MainActivity, ArcileApp, shell/MainRoute, Browser initialization, typed routes, Onboarding state | Source tracing of splash timeout, permission refresh, restored state, browser ownership, back handlers | No cold-start, revoked-permission, process-death, or back-stack device run |
| Browser, archives, trash, import | Browser controller wiring; archive load/extract/conflicts; local transfer and mutation journals; trash restore/Undo; incoming-share preflight and destination route | Detailed failure/recovery traces; path/symlink policy and archive safety/rollback inspection; focused storage tests below | Cloud/virtual providers, external drives, low-storage and interrupted operation scenarios not reproduced on hardware |
| Home, recent files, quick access | Dashboard/search/refresh state, recent paging and date boundaries, shortcut mutations and SAF handoff representation | Representative ViewModel and primary-screen source review | No provider-latency, midnight/time-zone, or shortcut grant-revocation runtime checks |
| Settings, activity log, plugins | Preferences/backup state, log clear, plugin discovery/ranking/same-signer checks and explicit URI handoffs | Representative ViewModel/screen review, plugin manager, backup exclusions, build/catalog/docs comparison | Preference import/export and installed companion APK behavior not exercised |
| Documents, APKs, images | Category-library state/actions, editor load/save/drafts, image viewer state, APK staging/install entry points | Detailed editor trace; representative gallery/library screens and bounded APK staging entry points | PDF rendering/search/printing, full image metadata edits, APK installation and every viewer dialog were not fully exercised or exhaustively inspected |
| Audio/video | Playback service/session, audio edit plan/export/cancel, video playback-surface ownership and controls | Audio-focus/noisy handling, player release, export cleanup, primary-screen source triage | Codec/device behavior, subtitles, PiP transitions, audio interruption, and release media performance not tested |
| Vaults/feature-specific behavior | OnlyFiles controllers; domain contracts; crypto/KDF bounds; imports, transfer/merge, sessions/biometrics, transaction recovery, external grants | Detailed transaction and deletion traces; focused vault tests below | Not an independent cryptographic audit; biometric hardware, Keystore invalidation, portable-storage loss, and all encrypted viewer paths remain untested |
| Lifecycle/background work | Bulk service, coordinator, journal/recovery, application initialization, viewer processes, biometric coroutine callers | Source review includes service cancellation, Android 15+ timeout override, and session disposal | No Doze, reboot, force-stop, foreground-service timeout, or real multiprocess device tests |
| UI/UX | Primary-screen source triage across all feature modules; deeper editor, trash, browser, OnlyFiles, category and playback flows | Loading/error/back/selection hooks, lazy-item keys, dialogs and shared scaffold reviewed selectively | No screenshots or visual evaluation; typography, contrast, animation and every dialog state remain unverified |
| Material/current APIs | Catalog: Compose BOM 2026.08.00, Material 3 1.5.0-alpha26, Adaptive 1.3.0; theme, motion, shared controls, adaptive MainRoute | [Official Material release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) checked again 2026-09-26: stable 1.4.0 and alpha29; Expressive APIs and API churn reviewed | No blanket upgrade proposed; custom components were not presumed defective simply because official alternatives exist |
| Screens/windows/input | Supported API 30+; compact/medium/expanded branches, landscape and separating-hinge layout; keyboard field helper, insets and editor selections | Source-only compatibility matrix: phone portrait/landscape, tablet, folded/unfolded, split screen and desktop resize; [API 37 large-screen requirements](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored) checked during audit | Every matrix configuration requires runtime validation, including gesture/three-button navigation, cutouts, IME, keyboard/mouse, and 200% font size; no connected device |
| Accessibility/languages | Shared file semantics, resource usage, selection actions, reduced-motion theme hooks, responsive/scrolling containers | Source checks; `checkProductionStrings` passed, with the specific semantics gap documented above | TalkBack, switch access, focus traversal, RTL, pseudo-locales, contrast, and text expansion not visually or interactively tested |
| Database/persistence | Room v2, v1 destructive cache fallback, restore invalidation, storage-node/folder/thumbnail entities and DAOs, snapshot stores, DataStore ownership | Source review of cache-only schema, thumbnail foreign-key cascade, indexes, writes and invalidation paths | Migration/corruption tests not run; query plans and large-database metrics not measured; remaining schema/history review is partial |
| Storage/networking/security/privacy | PathSafety, exports/providers, staging, plugin signatures, manifests/backup rules, logger, vault grants and KDF limits | Reachability traced for accepted findings; no INTERNET permission in app manifest; bounded import/archive paths inspected | No live network client/account/payment/sync feature identified; merged release manifest and every dependency SDK were not independently audited |
| Resources/performance | Traversal/hash limits, cleaner workload, thumbnail cache setup, text history, biometric dispatchers | Static bounds and thread ownership only | No fabricated timings/heap/battery figures; startup, scrolling, thermal behavior, idle cost and realistic release workloads remain unmeasured |
| Build/dependencies/native/upgrades | AGP 9.3.1, Kotlin 2.4.10, Gradle 9.5.0, KSP 2.3.11; compile/target 37, min 30, Java 21 daemon/JVM 11; debug suffix/coverage and release shrinking/signing references | Catalog/wrapper/build conventions inspected; offline targeted Gradle commands succeeded; app remains version 2.1.0/code 210 | No fresh APK, R8/release build, binary native-library/page-size inspection, install/update test or signing operation; GitHub CI/CD untouched |
| Focused storage validation | `:core:storage:data:testDebugUnitTest --tests '*FileTransferEngineTest' --tests '*MutationJournalTest' --tests '*TrashManagerTest' --offline --max-workers=1 --console=plain` | Passed: 34 tests, zero failures/errors/skips (11 transfer, 8 journal, 15 trash) | Tests cover existing scenarios, not the new negative cases requested above |
| Focused vault validation | `:core:vault:data:testDebugUnitTest --tests '*VaultTransactionManagerTest' --tests '*VaultTransferCoordinatorTest' --tests '*VaultImport*Test' --offline --max-workers=1 --console=plain -q` | Passed: 8 tests, zero failures/errors/skips (1 transaction, 3 transfer, 4 import) | No new tests or source edits made; oversized commit and merge-move Skip regressions are not covered |
| Tests/docs/environment | README, relevant DEVELOPMENT sections, privacy policy, test fixtures and architecture gates; existing TASKS fully read | `checkProductionStrings :app:verifyArcileBuildConventions --offline --max-workers=1 --console=plain -q` passed; `adb devices -l` returned no devices | Full lint/unit suites and instrumented tests not run; source-only coverage is not release readiness |
