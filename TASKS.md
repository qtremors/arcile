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

### Performance Tasks
- [ ] **PERF-0001 - Page Directory Listings Before Full Materialization** `[High]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileSystemDataSource.kt` `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/BrowserDisplayState.kt`
  - **Problem:** `FileSystemDataSource.list()` calls `directory.listFiles()`, upserts every child, sorts the full child array, converts the full list to models, and only then emits chunked pages. `BrowserDisplayState` then rebuilds derived visible lists, paths, names, and row models for the whole file set.
  - **Impact:** Very large directories can delay first paint, allocate heavily, and increase ANR or low-memory risk even though the UI is modeled as paged.
  - **Fix:** Stream or index directory children incrementally, emit the first page before all children are sorted and persisted, and move expensive row-model derivation behind visible-item boundaries or cached paging data.
  - **Verification:** Add a benchmark or stress test with a 50,000-file directory that records first-page latency, peak memory, and scroll responsiveness; verify StrictMode shows no main-thread file I/O during browsing.

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
