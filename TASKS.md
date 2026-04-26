# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.6.1
> **Last Updated:** 2026-04-26

---

## Remediation Backlog

### 🔐 Security & Privacy

- [x] **Harden TrashManager crypto and copy integrity** `[Critical]`
  - **Location:** `TrashManager.kt`
  - **Problem:** Multiple compounding risks in the trash crypto subsystem:
    1. **Ambiguous dual-key path** — `getKey()` tries KeyStore first, silently falls back to PBKDF2. No marker byte distinguishes which key encrypted a given metadata file. If the KeyStore key is created _after_ files were encrypted with the fallback, those files become permanently unrestorable.
    2. **Weak PBKDF2 iterations** — `PBEKeySpec` uses 10,000 iterations (line 104), well below the OWASP minimum of 600,000 for PBKDF2-HMAC-SHA256.
    3. **`ANDROID_ID` as passphrase** — The fallback key is derived from `Settings.Secure.ANDROID_ID`, which is a weak, non-secret per-device identifier that can be read by other apps on older Android versions.
    4. **Unverified copy before delete** — `moveToTrash` (line 321) uses `file.copyRecursively(targetTrashFile, overwrite = true)` but never verifies the destination integrity (size/hash) before deleting the source. A partial copy (e.g., disk full, I/O error on one nested file) silently destroys the original.
    5. **Restore copy verification is superficial** — `verifyRestoreCopy` (line 256–261) only checks `exists()`, `isFile/isDirectory`, and `length()` — no content hash verification. Bit-rot or truncation passes this check.
  - **Impact:** Data loss on trash/restore failures. Crypto key confusion renders metadata permanently unreadable. Weak fallback key could be brute-forced if device-ID is known.
  - **Fix:** (a) Prepend a key-ID byte (0x01=KeyStore, 0x02=PBKDF2) to encrypted blobs. (b) Increase PBKDF2 iterations to ≥600,000. (c) Verify copy integrity with length + sampled SHA-256 before source deletion. (d) Check `copyRecursively` return value and abort on failure.
  - **Verification:** Unit tests simulating disk-full mid-copy, KeyStore unavailability, and key migration; trash/restore round-trip with integrity checks.

- [x] **Harden `TrashCryptoHelper` retry logic** `[Medium]`
  - **Location:** `TrashManager.kt:TrashCryptoHelper` (lines 116–169)
  - **Problem:** The encrypt/decrypt retry loops retry 3 times on _any_ exception, including deterministic failures like `InvalidKeyException`, `BadPaddingException`, and `IllegalBlockSizeException`. These will never succeed on retry.
  - **Impact:** Up to 3× latency on deterministic failures. No log differentiation between transient and fatal errors.
  - **Fix:** Only retry on transient/platform errors (e.g., `ProviderException`). Fail fast on `InvalidKeyException`, `BadPaddingException`, `IllegalBlockSizeException`. Log which attempt number failed.
  - **Verification:** Corrupted KeyStore causes immediate failure rather than a 3-retry delay.

- [x] **Ensure `TrashCryptoHelper` keys are not cached across invalidation** `[Low]`
  - **Location:** `TrashManager.kt:TrashCryptoHelper` (lines 56–57)
  - **Problem:** `keystoreKey` and `fallbackKey` are cached in `private var` fields at the object level. If the KeyStore key is deleted externally (e.g., factory reset partial, user clears app data), the stale in-memory reference will be used for encryption, producing data encrypted with a now-inaccessible key.
  - **Impact:** Encrypted trash metadata becomes permanently unreadable after KeyStore invalidation until process restart.
  - **Fix:** Verify cached key validity periodically, or always re-fetch from KeyStore on encrypt operations. Cache only for decrypt retries within a single call.
  - **Verification:** Simulated KeyStore key deletion followed by a trash operation correctly detects the stale key.

- [x] **Prevent path traversal via symlinks in trash operations** `[Medium]`
  - **Location:** `TrashManager.kt:validatePath()` (lines 244–253), `FileSystemDataSource.kt:validatePath()` (lines 39–49)
  - **Problem:** `validatePath` resolves `canonicalPath` and checks against `activeStorageRoots`. However, if a symlink is placed inside a storage root that points outside it, `canonicalPath` resolves it, and the check passes because the _canonical_ path may still start with a root prefix on certain filesystem layouts. The check also doesn't guard against TOCTOU — a symlink could be created between `validatePath` and the actual I/O.
  - **Impact:** A crafted symlink could allow trash/restore/delete operations to target files outside the intended storage boundaries.
  - **Fix:** Add an explicit symlink check (`Files.isSymbolicLink`) and reject symlinked paths in destructive operations. Consider resolving only the parent and verifying the leaf entry.
  - **Verification:** A symlink pointing outside the storage root is rejected by `validatePath`.

- [x] **Backup rules should exclude KeyStore alias metadata** `[Low]`
  - **Location:** `backup_rules.xml`, `data_extraction_rules.xml`
  - **Problem:** The backup rules exclude `trash_crypto_prefs.xml` (the PBKDF2 salt) but the KeyStore alias `arcile_trash_key` is hardware-bound and not transferable. If a backup is restored to a new device, the salt is excluded but any metadata encrypted with the KeyStore key from the old device becomes permanently unreadable. There is no migration path documented.
  - **Impact:** Trash metadata becomes permanently unrecoverable after device migration via backup/restore.
  - **Fix:** Document the limitation. On restore, detect missing KeyStore key and surface a user-facing warning to empty trash before migration. Consider a metadata format version byte.
  - **Verification:** Restore to a new device with existing trash data produces a clear user-visible warning.

### 🐞 Correctness & Reliability

- [x] **Route all deletion flows through `BulkFileOperationCoordinator`** `[Critical]`
  - **Location:** `BulkFileOperationModels.kt`, `BulkFileOperationService.kt`, `DeleteFlowDelegate.kt`, `FileRepository.kt`
  - **Problem:** Trashing and permanent deletions currently bypass the foreground service and progress UI. `DeleteFlowDelegate.moveSelectedToTrash()` and `deleteSelectedPermanently()` execute directly in the ViewModel coroutine scope, not as foreground service operations.
  - **Impact:** (a) No progress FAB or completion snackbar for delete operations. (b) If the app is backgrounded during a large batch delete, the process may be killed, causing partial deletion with no recovery. (c) No cancellation support for delete operations.
  - **Fix:** Expand `BulkFileOperationType` to include `TRASH` and `DELETE`. Route them through `BulkFileOperationService`. Update `DeleteFlowDelegate` to use the coordinator.
  - **Verification:** Deleting/trashing 100+ items triggers the progress FAB. Backgrounding the app mid-operation does not kill the operation. A completion snackbar appears.

- [x] **Harden foreground bulk copy/move orchestration** `[High]`
  - **Location:** `BulkFileOperationCoordinator.kt`, `BulkFileOperationService.kt`, `ClipboardDelegate.kt`
  - **Problem:** Multiple concurrency and lifecycle issues:
    1. `startOperation()` sets `_activeRequest.value` and emits `Started` _before_ `startForegroundService` is called (line 68–75). If the service fails to start (e.g., background restriction), the coordinator is permanently stuck in "active" state.
    2. `cancelActiveOperation()` sends a cancel intent but does not validate that the cancel was for the same operation ID. A stale cancel can kill a newly started operation.
    3. `BulkFileOperationService.onStartCommand` uses `stopSelf(startId)` (line 84), which is incorrect when multiple `onStartCommand` calls can interleave (e.g., START + CANCEL). The CANCEL command does not call `stopSelf`, so the service may linger.
    4. The `MutableSharedFlow` events buffer uses `DROP_OLDEST` (line 49). Under heavy progress reporting, terminal events (Completed/Failed/Cancelled) can be dropped, leaving the UI permanently stuck.
  - **Impact:** Stuck UI states, failed cancellations, orphaned foreground services, or ANR if service fails to start within the 10-second foreground notification deadline.
  - **Fix:** (a) Wrap service launch in try/catch; revert `_activeRequest` on failure. (b) Attach cancel to operation IDs. (c) Track latest `startId` in the service. (d) Use `replay = 1` or a Channel for terminal events to prevent drops.
  - **Verification:** Immediate cancel-after-paste and simulated start failures result in a clean idle/error UI state.

- [x] **Protect against duplicate event consumption in `ClipboardDelegate`** `[High]`
  - **Location:** `ClipboardDelegate.kt` (lines 23–56), `BrowserViewModel.kt` (lines 276–347)
  - **Problem:** Both `ClipboardDelegate.init{}` and `BrowserViewModel.init{}` independently subscribe to `bulkFileOperationCoordinator.events`. Both react to `Started`, `Progress`, `Completed`, `Failed`, `Cancelled` events by mutating the _same_ `MutableStateFlow<BrowserState>`. This produces duplicate state transitions.
  - **Impact:** Redundant state updates, potential brief UI flickering as `isLoading` is toggled by two independent collectors. The `clipboardState = null` in `ClipboardDelegate` line 45 races with `activeFileOperation = null` in `BrowserViewModel` line 321.
  - **Fix:** Consolidate event handling to a single subscriber. Let either the ViewModel or the delegate own the event processing, not both. The ViewModel already handles the FAB state; the delegate should only manage clipboard-specific state (clearing `clipboardState`).
  - **Verification:** A single paste operation triggers exactly one `isLoading = false` + `clipboardState = null` transition.

- [x] **Fix `BrowserViewModel` operation state race conditions** `[Medium]`
  - **Location:** `BrowserViewModel.kt` (lines 270–347)
  - **Problem:** The `init` block seeds initial state from `bulkFileOperationCoordinator.activeRequest.value` (line 271) but then subscribes to `events` via `collectLatest` (line 277). If an operation completes between the state seed and the subscription, the completion event is missed, leaving `activeFileOperation` permanently set.
  - **Impact:** Stale progress FAB after operation completion during ViewModel initialization.
  - **Fix:** Observe `activeRequest` StateFlow directly via `combine` with events, or re-verify event relevance against `activeRequest.value` when processing terminal events.
  - **Verification:** FAB correctly reflects reality even when navigation occurs during operation transitions.

- [x] **Fix `BulkFileOperationService` not calling `stopForeground`/`stopSelf` on cancel** `[Medium]`
  - **Location:** `BulkFileOperationService.kt:43–47`
  - **Problem:** When `ACTION_CANCEL` is received, the service cancels the job but immediately returns `START_NOT_STICKY` without calling `stopForeground` or `stopSelf`. The service continues running with a stale notification until the cancelled coroutine's `finally` block executes — which may be delayed by I/O cleanup.
  - **Impact:** Stale foreground notification visible to the user for an indeterminate period after cancel.
  - **Fix:** Ensure the cancel path triggers `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()` either directly or by ensuring the finally block runs promptly.
  - **Verification:** Cancel action results in immediate notification removal.

- [x] **Handle `HomeViewModel.loadHomeData` timeout edge case** `[Low]`
  - **Location:** `HomeViewModel.kt:152–171`
  - **Problem:** `withTimeoutOrNull(15_000)` wraps all async results including the nested per-volume category storage lookups (lines 158–170). If the timeout fires while the nested `async` blocks are running, `categoryByVolume` may contain partial data (some volumes calculated, others not) but the result is still surfaced to the UI as if it were complete.
  - **Impact:** Partial category data displayed without a clear indication of which volumes were completed.
  - **Fix:** Either treat timeout as a full failure (clear partial data) or explicitly mark which volumes timed out so the UI can show a retry prompt.
  - **Verification:** Simulated slow volume produces a clear "partial data" indicator rather than silently incomplete categories.

- [x] **`FolderStatsStore.workerScope` is never cancelled** `[Medium]`
  - **Location:** `FolderStatsStore.kt:63`
  - **Problem:** `DefaultFolderStatsStore` creates its own `CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))` which is never cancelled. Since it's a `@Singleton`, it lives for the process lifetime, but the scope has no structured cancellation path for instrumented tests, and any exception in the `SupervisorJob` children is silently swallowed.
  - **Impact:** Leaked coroutines in instrumented tests. Calculation errors in `FolderStatsCalculator` are silently caught but the error path in the `finally` block (line 116) can re-queue, creating an infinite retry loop for permanently inaccessible folders.
  - **Fix:** Inject the `CoroutineScope` via Hilt so tests can provide a `TestScope`. Add a max-retry guard to prevent infinite re-queueing of failed paths.
  - **Verification:** A permanently inaccessible folder path does not produce infinite calculation retries.

### ⚡ Performance & Optimization

- [ ] **Avoid allocating ancestor-path lists on every mutation** `[Low]`
  - **Location:** `TrashManager.kt:pathWithAncestors()`, `FileSystemDataSource.kt:pathWithAncestors()`
  - **Problem:** `pathWithAncestors()` is duplicated between `TrashManager` and `FileSystemDataSource`. Both create temporary `File` objects, call `canonicalFile`, and build ancestor lists on every mutation event.
  - **Impact:** Minor GC pressure on multi-file operations; code duplication increases maintenance burden.
  - **Fix:** Extract `pathWithAncestors` to a shared utility with a single implementation; consider caching canonical root lookups.
  - **Verification:** No behavioral change; verify with existing tests.

- [ ] **Use `Dispatchers.IO` for `BrowserPreferencesStore` disk reads in `NavigationDelegate`** `[Low]`
  - **Location:** `NavigationDelegate.kt:110, 235, 291`
  - **Problem:** `browserPreferencesRepository.preferencesFlow.first()` is called inside `viewModelScope.launch {}` which defaults to `Dispatchers.Main`. DataStore reads are suspending but may perform disk I/O on first access, blocking the main thread.
  - **Impact:** Potential ANR on first navigation if DataStore has not been accessed before and the disk read takes >5ms.
  - **Fix:** Wrap the `preferencesFlow.first()` calls in `withContext(Dispatchers.IO) {}` or use `flowOn(Dispatchers.IO)` in the DataStore layer.
  - **Verification:** StrictMode disk-read violations on the main thread are eliminated.

- [ ] **Optimize `MediaStoreClient.getRecentFiles` pagination** `[Medium]`
  - **Location:** `MediaStoreClient.kt:199–244`
  - **Problem:** Pagination for recent files is implemented client-side: the cursor iterates through _all_ results from the beginning, skipping `offset` valid entries via `validFilesSkipped` counter (line 207–219). For offset=500, this scans 500+ rows just to skip them.
  - **Impact:** Increasing latency as the user scrolls deeper into the recent files list. O(n²) total work across all pages.
  - **Fix:** Use `ContentResolver.QUERY_ARG_OFFSET` on API 26+ to push pagination to the provider. Alternatively, cache a cursor position or use `DATE_MODIFIED < lastSeenTimestamp` as a keyset-style cursor.
  - **Verification:** Load time for page 10 is roughly equal to page 1.

- [ ] **Replace `File.exists()` check per MediaStore row** `[Low]`
  - **Location:** `MediaStoreClient.kt:214–215, 591–592`
  - **Problem:** `getRecentFiles` calls `File(path).exists()` for every row returned by the MediaStore cursor (line 214–215). Each `exists()` call is a filesystem stat syscall. For a cursor with 1000+ rows, this is 1000+ blocking I/O calls.
  - **Impact:** Slow recent files loading, especially on SD cards or USB storage where I/O latency is higher.
  - **Fix:** Remove the `exists()` check or make it optional. MediaStore entries for deleted files are typically cleaned up by the media scanner. If stale entries are a concern, batch-verify in a separate pass or use a LRU staleness cache.
  - **Verification:** Recent files loading time decreases measurably on external storage.

- [ ] **`FolderStatsCalculator` does unbounded recursive traversal** `[Medium]`
  - **Location:** `FolderStatsCalculator.kt:33–52`
  - **Problem:** `calculate()` traverses the entire directory tree without any depth or node limit. For a folder with millions of files (e.g., a large media library, or a symlink cycle not caught by the OS), this blocks the limited-parallelism IO dispatcher indefinitely.
  - **Impact:** One deeply nested folder can starve the `FolderStatsStore` worker pool (limited to 2 threads), blocking stats for all other visible folders.
  - **Fix:** Add a configurable node limit (e.g., 100,000) and return `FolderStatsStatus.Partial` when exceeded. Check for cancellation periodically via `currentCoroutineContext().ensureActive()`.
  - **Verification:** A folder with 1M+ files returns `Partial` status within a reasonable time.

- [ ] **`estimateTransferBytes` does unbounded `walkTopDown` on source directories** `[Low]`
  - **Location:** `FileSystemDataSource.kt:119–125`
  - **Problem:** `estimateTransferBytes()` calls `source.walkTopDown().filter { it.isFile }.sumOf { it.length() }` before copy/move starts. For large directory trees, this duplicates the traversal cost (once for estimation, once for the actual copy). No cancellation check is present.
  - **Impact:** Double traversal cost for large directory moves. The estimation blocks the start of the actual operation.
  - **Fix:** Either skip pre-estimation and use indeterminate progress initially, or add a node cap and cancellation checks to the estimation walk.
  - **Verification:** Moving a 10,000-file directory starts the progress UI within 1 second.

### 🎨 UI & Rendering

- [ ] **Polished Snackbar aesthetics for Material 3 Expressive** `[Medium]`
  - **Location:** `BrowserScreen.kt`, `ArcileAppShell.kt`
  - **Problem:** The current snackbars use default styling which feels detached from the app's custom "squircle" and tonal container aesthetic.
  - **Impact:** Visual inconsistency between file operation feedback and the rest of the polished M3 Expressive UI.
  - **Fix:** Customize the `SnackbarHost` to use `MaterialTheme.shapes.extraLarge` (squircle), apply tonal container colors, and ensure font-weight/spacing matches the app's design language.
  - **Verification:** Operation feedback snackbars appear with rounded squircle shapes and cohesive theme-aware coloring.

- [ ] **Client-side sorting in `BrowserScreen` recomputes on every state update** `[Low]`
  - **Location:** `BrowserScreen.kt` (sort logic applied via `LaunchedEffect` on lines ~297–318)
  - **Problem:** File sorting and search filtering are performed inside `LaunchedEffect` blocks keyed on `state.browserSortOption`, `state.currentPath`, and `state.browserSearchQuery`. These produce new `List` instances that trigger recomposition of the entire file list even when the actual content hasn't changed.
  - **Impact:** Redundant list re-creation and recomposition when unrelated state fields update.
  - **Fix:** Move sort/filter logic into a `remember`/`derivedStateOf` block or perform sorting in the ViewModel before emitting to the state flow.
  - **Verification:** Changing an unrelated state field (e.g., `selectedFiles`) does not re-trigger file list sorting.

### 🏗️ Architecture

- [ ] **Eliminate `pathWithAncestors` duplication** `[Low]`
  - **Location:** `TrashManager.kt:217–242`, `FileSystemDataSource.kt:83–113`
  - **Problem:** Identical `pathWithAncestors()` implementations exist in both files, duplicating ~30 lines of non-trivial path traversal logic.
  - **Impact:** Bug fixes must be applied in two places; inconsistent behavior possible if one copy drifts.
  - **Fix:** Extract to a shared utility function (e.g., `data/util/PathUtils.kt`).
  - **Verification:** Both consumers delegate to the shared function; existing tests pass.

- [ ] **Reduce `FileSystemDataSource` surface area** `[Low]`
  - **Location:** `FileSystemDataSource.kt` (676 lines)
  - **Problem:** Handles directory listing, path validation, cancellable copy, cancellable move, rename, create, permanent delete, conflict detection, and progress reporting. This is a "god object" accumulating unrelated responsibilities.
  - **Impact:** High cognitive load, difficult to test in isolation, merge conflicts as features grow.
  - **Fix:** Extract copy/move logic into a dedicated `FileTransferEngine`. Keep listing/create/delete in the data source. Extract conflict detection to a helper.
  - **Verification:** No behavioral change; existing tests pass against the refactored classes.

- [ ] **Reduce `finalizeMutation` duplication** `[Low]`
  - **Location:** `TrashManager.kt:210–215`, `FileSystemDataSource.kt:81–86`
  - **Problem:** `finalizeMutation()` is implemented identically in both `TrashManager` and `FileSystemDataSource`, calling `mediaStoreClient.invalidateCache()`, `volumeProvider.invalidateCache()`, `folderStatsStore.invalidate()`, and `scanMediaFiles()` with the same logic.
  - **Impact:** Identical to `pathWithAncestors` duplication — drift risk and double maintenance burden.
  - **Fix:** Extract to a shared `MutationFinalizer` utility or inject it as a dependency into both classes.
  - **Verification:** All mutation paths still trigger media scan and cache invalidation.

- [ ] **`validatePath` is duplicated across `TrashManager` and `FileSystemDataSource`** `[Low]`
  - **Location:** `TrashManager.kt:244–253`, `FileSystemDataSource.kt:39–49`
  - **Problem:** Identical `validatePath()` implementations exist in both classes, checking canonical paths against `volumeProvider.activeStorageRoots`. This is a third instance of cross-class duplication (alongside `pathWithAncestors` and `finalizeMutation`).
  - **Impact:** Security-sensitive path validation logic must be maintained in three places. A fix to one may not propagate.
  - **Fix:** Extract to a shared `PathValidator` utility injected into both classes.
  - **Verification:** Both classes delegate to the shared validator; existing tests pass.

### 🛠️ Build & CI

- [ ] **Add regression coverage and CI gates for recent escapes** `[Medium]`
  - **Location:** `app/src/test`, `app/src/androidTest`, `app/build.gradle.kts`
  - **Problem:** Current CI/suite lacks release assembly/lint gating, bulk-operation lifecycle edge cases, and cross-midnight grouping tests.
  - **Impact:** Regressions like the backup XML error and service-orchestration edge cases can survive until manual validation.
  - **Fix:** Add CI tasks for `:app:assembleRelease` and lint; add targeted tests for bulk operations and date boundaries.
  - **Verification:** CI fails correctly when those regressions are reintroduced.

- [ ] **Ensure ProGuard rules cover all serialized models** `[Medium]`
  - **Location:** `proguard-rules.pro`
  - **Problem:** `proguard-rules.pro` explicitly keeps `TrashMetadataEntity` (line 57) but does not keep `BulkFileOperationRequest`, `BulkFileOperationProgress`, `CategoryCacheEntity`, `CacheRootEntity`, `FolderStatsCacheEntity`, or other `@Serializable` data classes that are serialized to disk or Intent extras. The generic `kotlinx.serialization` keep rules (lines 27–52) rely on companion-object-based serializer resolution, which may break if the class itself is renamed/obfuscated.
  - **Impact:** Release builds may crash on deserialization of serialized models (e.g., resuming a bulk operation after process death, or reading analytics cache).
  - **Fix:** Add explicit `-keep` rules for all `@Serializable` data classes that are persisted to disk or passed via Intent extras. Alternatively, add a blanket rule for all `@Serializable` classes in the `dev.qtremors.arcile` package.
  - **Verification:** `assembleRelease` followed by a bulk copy/move and trash operation works correctly.

- [ ] **`BulkFileOperationService` notification uses launcher icon instead of monochrome** `[Low]`
  - **Location:** `BulkFileOperationService.kt:107`
  - **Problem:** `setSmallIcon(R.mipmap.ic_launcher)` uses the full-color launcher mipmap as the notification small icon. Android requires notification small icons to be monochrome (alpha-only) — the system will render a solid white/colored square on most devices.
  - **Impact:** Broken notification icon appearance during file operations.
  - **Verification:** Foreground service notification displays a recognizable monochrome icon.

### 🧪 Testing

- [ ] **Add `ClipboardDelegate` unit tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/browser/delegate/`
  - **Problem:** `ClipboardDelegate` has no dedicated tests. Its event handling, conflict resolution flow, and interaction with `BulkFileOperationCoordinator` are untested.
  - **Impact:** The duplicate event consumption bug (see above) was not caught by tests.
  - **Fix:** Create `ClipboardDelegateTest.kt` with tests for: paste flow, conflict detection, resolution, cancel, coordinator event handling, and edge cases (empty clipboard, already-running operation).
  - **Verification:** Test suite passes; duplicate event handling is detected if reintroduced.

- [ ] **Add `NavigationDelegate` unit tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/browser/delegate/`
  - **Problem:** `NavigationDelegate` manages browser navigation state, path history, volume resolution, and saved state restoration — all untested.
  - **Impact:** Navigation bugs (e.g., history corruption, volume resolution failures on hot-plug) are not caught.
  - **Fix:** Create `NavigationDelegateTest.kt` covering: folder navigation, back navigation, history stack, volume root fallback, saved state round-trip, category navigation, and refresh behavior.
  - **Verification:** Test suite passes; back-navigation edge cases are covered.

- [ ] **Add `DeleteFlowDelegate` unit tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/delegate/`
  - **Problem:** `DeleteFlowDelegate` orchestrates the complex delete policy evaluation, trash/permanent delete branching, and native confirmation flow — all untested directly (only indirectly via `BrowserViewModelTest`).
  - **Impact:** Delete flow regressions (e.g., wrong dialog shown for mixed selections) may not be caught.
  - **Fix:** Create `DeleteFlowDelegateTest.kt` with tests for: trash confirmation, permanent delete confirmation, mixed selection explanation, native confirmation flow, and empty selection guard.
  - **Verification:** All delete flow branches are exercised.

- [ ] **Add integration tests for `TrashManager` crypto round-trip** `[High]`
  - **Location:** `app/src/test/.../data/manager/TrashManagerTest.kt`
  - **Problem:** Existing `TrashManagerTest` may not fully cover the encrypt/decrypt/fallback round-trip, especially the scenario where KeyStore is unavailable and the fallback key is used.
  - **Impact:** The dual-key ambiguity bug (see Security section) would not be caught.
  - **Fix:** Add test cases for: (a) KeyStore-based round-trip, (b) PBKDF2 fallback round-trip, (c) metadata encrypted with fallback should still decrypt after KeyStore becomes available, (d) corrupted metadata should be handled gracefully.
  - **Verification:** All crypto paths produce correct round-trip results.

- [ ] **Add `BulkFileOperationService` lifecycle tests** `[Medium]`
  - **Location:** `app/src/test/.../presentation/operations/`
  - **Problem:** The foreground service orchestration (start, cancel, completion, failure, process death) has no test coverage. The identified race conditions (stuck active request, stale cancel, dropped terminal events) are untested.
  - **Fix:** Create `BulkFileOperationCoordinatorTest.kt` and `BulkFileOperationServiceTest.kt` covering: start/complete lifecycle, cancel mid-operation, duplicate start rejection, terminal event delivery guarantee, and coordinator state cleanup.
  - **Verification:** All identified orchestration edge cases are exercised.

- [ ] **Add `HomeViewModel.loadHomeData` timeout and partial-data tests** `[Low]`
  - **Location:** `app/src/test/.../presentation/home/`
  - **Problem:** The 15-second timeout with partial category data (see Correctness section) has no test coverage. The `supervisorScope` + nested `async` + `withTimeoutOrNull` composition is complex and untested.
  - **Fix:** Create tests simulating slow volume responses and verifying partial vs. complete data indicators.
  - **Verification:** Timeout scenario produces the expected partial-data error message.

---

## Backlog / Ideas

> A repository for future ideas, enhancements, and unprioritized features.

### Storage & Access
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.
- **Storage Health Diagnostics**: Basic S.M.A.R.T. status checks, disk health metrics, and repair/trim suggestions for mounted volumes.

### File Operations & Automation
- **Compress & Extract Archives**: Add support for creating and extracting ZIP, TAR.GZ, and 7z archives directly within the file browser.
- **Automated Task Rules**: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` where a main action regularly needs a secondary dropdown or overflow action.
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press, successful file operations, and error states.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally scrollable carousel of the 10 most recently modified images and videos.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations such as Lottie for empty folders, trash, and search results.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Browsing & Organization
- **Rich Media Previews**: Implement custom Coil `Fetcher` components to extract APK icons and PDF thumbnails.
- [ ] PDF thumbnails pending
- **Starred / Favorited Files**: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Enhanced Category Browsing**: When opening a file category, display all related folders containing matching files with tabbed or segmented navigation.
- **Storage Analyzer ("Filelight" view)**: A dedicated radial map or sunburst chart to visualize storage usage by folder and file type.

### Tools & Development
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations for auditing purposes.
- **Dummy File Generator**: A developer/testing tool to quickly create fake files of a specified size to fill space or test transfers.
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.

### Settings & About
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive flower petal UI.

### Multi-Window & Layout
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.

### Security & Privacy
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
