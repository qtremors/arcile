# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.7.3
> **Last Updated:** 2026-05-22

---

## Consolidated Tasks

### Browser / File Browsing Tasks

- [ ] **UI-0001 - Adaptive Layout / Dual-Pane Workspace** `[Critical]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt
  - **Problem:** Material 3 adaptive dependencies are present, but the primary app structure still uses a two-page `HorizontalPager` between Home and Browser with no adaptive navigation rail, list-detail layout, dual-pane file browser, or tablet/foldable workspace.
  - **Impact:** Large screens feel stretched rather than upgraded. Power users lose two-pane copy/move, persistent folder panes, drag/drop, side-by-side properties or preview, and multi-folder workspace workflows.
  - **Fix:** Derive layout from `currentWindowAdaptiveInfo()`. Use bottom navigation or the current pager only for compact width. Use navigation rail plus list-detail/supporting pane for medium and expanded width. Add expanded-width dual-browser mode with independent path, sort, selection, and clipboard scopes. Add "send to other pane", drag/drop between panes, operation preview with conflict count before paste, and pane path restoration after process death.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0003 - Predictive Back / Platform Consistency** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt
  - **Problem:** Back behavior is handled through several `BackHandler` branches, but there is no predictive back progress, shared transition integration, or system-preview-aware hierarchy.
  - **Impact:** Back gestures feel less modern and less trustworthy. Users cannot preview whether back will close search, clear selection, move up a folder, return to Home, or leave the app.
  - **Fix:** Define a back priority stack: modal, sheet, search, selection, folder up, route pop, app exit. Use Navigation Compose predictive back support where route transitions apply. Use `PredictiveBackHandler` for browser folder-up progress and shared element/bounds opportunities. Add tests for back priority ordering.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0009 - File list semantics** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt
  - **Problem:** The row sets a merged `contentDescription`, but child icons and thumbnails also supply content descriptions like `"Folder"`, `"File"`, or thumbnail descriptions.
  - **Impact:** TalkBack can become verbose or duplicate information, especially for file rows with thumbnails.
  - **Fix:** Keep a single row-level semantic label. Set decorative child icons/thumbnails to `contentDescription = null`. Add role/state details: selected, folder/file, hidden, action hint. Add custom accessibility actions for Open, Select, Rename, Delete where useful.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0010 - File grid semantics** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** Grid item semantics mirror list issues: merged row content plus child icon/thumbnail descriptions and no clear action hints for open/select.
  - **Impact:** Grid browsing with TalkBack is less efficient, and long-press selection is not discoverable.
  - **Fix:** Introduce `Modifier.fileItemSemantics(file, selected, formattedDate, folderStats)`. Add `onClick` and `onLongClick` labels through semantics. Remove child descriptions from decorative images.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0018 - Compose Performance / Lazy Lists** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** `animateContentSize()` is applied to every grid item container, and thumbnails use `SubcomposeAsyncImage` with crossfade in lazy grid cells.
  - **Impact:** Huge directories with media thumbnails can feel less responsive, especially on low-end devices or during fast fling.
  - **Fix:** Remove or strictly bound `animateContentSize()` from grid cells. Prefer `AsyncImage` with a stable placeholder for common cases; reserve subcomposition for complex states. Use thumbnail size requests based on cell dimensions. Add macrobenchmark for 1,000 mixed files with thumbnails.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0020 - Search / Filtering** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/SearchFiltersBottomSheet.kt
  - **Problem:** Search filters cover item type, file type, broad size buckets, and simple date buckets, but lack file-manager-grade filters such as extension, hidden files, storage volume, path scope, exact date range, MIME, media dimensions/duration, duplicate candidates, and saved searches.
  - **Impact:** Users with large storage libraries cannot narrow results precisely.
  - **Fix:** Add advanced filter mode while keeping default mode simple. Include extension/type chips, hidden toggle, storage volume, date range, size range, folder scope, and saved search presets. Show active filter chips with clear labels and counts.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0023 - Bottom Toolbar / Safe Areas** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/FloatingSelectionToolbar.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Floating selection and operation toolbars use fixed 56dp height, fixed 16dp padding, snackbar offsets of 80dp, and list bottom padding of 100dp rather than measuring/insetting the actual overlay.
  - **Impact:** On large font, landscape, gesture nav, 3-button nav, and foldables, content and snackbars can sit awkwardly or leave excessive dead space.
  - **Fix:** Measure bottom toolbar height or expose a shared bottom overlay inset. Apply `navigationBarsPadding()` / safeDrawing bottom to the toolbar. Feed calculated bottom content padding into lists. Unify Browser, Recent, and Trash overlay behavior.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0025 - Gestures** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Category folder tabs add a `pointerInput` horizontal drag threshold of `96f` inside pull-to-refresh and within the app's Home/Browser pager ecosystem.
  - **Impact:** Users may accidentally switch folder tabs while trying to scroll, refresh, or navigate. Gesture meaning changes based on screen mode.
  - **Fix:** Prefer visible `FolderTabsRow` controls and optional pager/tab component for category folders. If horizontal swipe remains, use velocity/threshold tuned in dp, announce it, and disable when TalkBack is active. Add haptics at successful tab switch.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0026 - Selection UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** Selection has semantic selected state and range-selection logic, but it still relies mainly on background color and shape. Range selection remains hidden behind long-pressing a second item after previous interaction.
  - **Impact:** Power users may not discover range selection; accessibility and color-blind users may miss selected state.
  - **Fix:** Add explicit check badges and selected count feedback. Add overflow menu "Select range..." or drag/range mode for advanced users. Add semantic selected state and custom select/unselect actions.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0036 - File List Performance** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** Formatted dates are computed during item composition, and list/grid item models are raw domain models rather than preformatted stable UI rows.
  - **Impact:** Large directories can spend composition work on repeated formatting.
  - **Fix:** Introduce `FileRowUiModel` with formatted date/size/subtitle/icon type. Precompute visible row metadata when file list or locale/time config changes. Keep expensive formatting out of hot lazy item scopes.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0041 - State Restoration** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/TrashScreen.kt
  - **Problem:** Several transient UI states use `remember` instead of `rememberSaveable`, including FAB expansion and multiple dialogs/sheets. Process death or rotation can reset in-progress UI tasks.
  - **Impact:** Users can lose typed dialog input or mode context during rotation, fold/unfold, memory pressure, or process recreation.
  - **Fix:** Audit all `remember { mutableStateOf(...) }` UI state. Use `rememberSaveable` for typed inputs, open sheets/dialogs where safe. Hoist workflow-critical state to ViewModels. Add rotation/process-death tests for create/rename/archive/search/selection.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Archive Tasks

- [ ] **UI-0028 - Archive UX** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArchiveViewerScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Archive workflows exist and show summary metadata, but password fields are plain text, extraction progress is not deeply contextual, archive navigation lacks breadcrumbs, and the viewer remains visually bare compared with Browser.
  - **Impact:** Archive handling will not yet feel competitive with MiXplorer/Solid Explorer.
  - **Fix:** Use `PasswordVisualTransformation` and reveal toggle for archive passwords. Add archive breadcrumb/header, entry count, compression summary, and extract destination preview. Show extraction progress with current entry and cancel state. Support encrypted archive failure messaging.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0008 - Archive viewer strings** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArchiveViewerScreen.kt
  - **Problem:** Archive viewer strings and content descriptions are hardcoded: `"Back"`, `"Extract folder"`, `"Extract archive"`, `"Folder"`, `"Archive password"`, `"Password"`, `"Open"`, `"Cancel"`, and summary strings.
  - **Impact:** Archive handling feels less polished than the browser, and localization/accessibility are inconsistent.
  - **Fix:** Move all archive viewer strings to resources, including plural resources for entries. Use existing reusable file row/icon components where possible. Add archive-specific string tests to `checkProductionStrings`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Storage / Access Tasks

- [ ] **UI-0033 - Permission UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArcileAppShell.kt arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt
  - **Problem:** The all-files-access permission screen is a centered card with title/body/button, but lacks richer rationale, denied-state recovery, privacy reassurance, Android version nuance, and fallback paths.
  - **Impact:** Users may deny or abandon permission because the app does not clearly explain the benefit and boundaries.
  - **Fix:** Add a permission rationale screen with concrete benefits and privacy notes. Show fallback for limited access/SAF where available. Handle "permission denied" return with next-step copy. Move toast strings to resources.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0039 - Storage Visualization** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/StorageDashboardScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/home/StorageSummaryCards.kt
  - **Problem:** Storage insight is mainly a segmented bar and category list. It lacks a premium analyzer visualization, drill-down, cleanup candidates, and explanatory hierarchy.
  - **Impact:** Arcile misses a major differentiator against Files by Google and Samsung My Files.
  - **Fix:** Add folder/category treemap or sunburst/radial view for storage. Add large files, old downloads, duplicate candidates, APKs, videos, and cache-like cleanup groups. Allow tapping category to browse scoped files with sort preserved.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Operations / Reliability Tasks

- [ ] **UI-0022 - Destructive Actions / Safety** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/dialogs/DeleteConfirmationDialog.kt
  - **Problem:** Delete flows exist, but the broader UI does not consistently distinguish reversible trash, permanent delete, native scoped delete, and mixed delete flows at the interaction level.
  - **Impact:** High-stakes file actions can feel more frightening than necessary, especially with mixed selections and SAF/native delete flows.
  - **Fix:** Create a delete decision surface that clearly shows destination: Trash vs Permanent vs Android system confirmation. Include selected count, total size, folder count, and irreversible warning. Add undo for trash moves when feasible. Use consistent red only for irreversible final confirmation.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **OP-0007 - Foreground Operations / Reliability** `[Critical]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationCoordinator.kt`
  - **Problem:** Long-running operations are foreground-service based and have a cancel path, but they are not durable. Requests, progress, phase, cancellation state, and partial results are held in memory only.
  - **Impact:** If the process is killed, the user can lose operation visibility and may be left with staged temp files or partial copies.
  - **Fix:** Introduce an operation database/journal. Record source, destination, staging paths, phase, bytes, result, and rollback plan. On app/service startup, recover or clean incomplete operations. Update notifications with real progress and cancel action. Recommended Refactor: Build an `OperationEngine` independent of ViewModels and expose operation state via repository/Flow. Safer Alternative: For now, persist only active operation metadata and staged paths, then cleanup on next launch.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **REL-0024 - Error Handling / Reliability** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/**` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/**`
  - **Problem:** Error handling relies on generic `Exception` messages in many code paths, while some use typed exceptions. UI often shows raw messages.
  - **Impact:** Users get inconsistent, sometimes technical, sometimes vague errors.
  - **Fix:** Define sealed domain errors: access denied, storage unavailable, conflict, insufficient space, unsupported provider, partial success, cancelled, corrupted metadata, unsafe path. Include user-safe message id and recovery action. Stop surfacing arbitrary exception messages directly. Recommended Refactor: Add `ArcileError` and map platform exceptions at data boundaries. Safer Alternative: Wrap existing exceptions in typed errors incrementally for destructive operations first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Visual System / Interaction Tasks

- [ ] **UI-0002 - Edge-to-Edge / Insets** `[Critical]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArcileAppShell.kt
  - **Problem:** The root `Scaffold` sets `contentWindowInsets = WindowInsets(0, 0, 0, 0)` and downstream screens mostly rely on top padding while bottom gesture/nav/IME/cutout handling is inconsistent.
  - **Impact:** Bottom toolbars, snackbars, FABs, dialogs, and list content can crowd gesture navigation, 3-button nav, landscape cutouts, and desktop window caption areas. This lowers platform quality immediately on Android 15+ and targetSdk 36.
  - **Fix:** Define root inset policy: draw backgrounds edge-to-edge but inset interactive controls. Let Material 3 bars handle their default insets where appropriate. Replace magic bottom padding with `WindowInsets.safeDrawing`, `navigationBarsPadding()`, `imePadding()`, and `windowInsetsPadding()`. Audit all `Scaffold` instances for explicit `contentWindowInsets`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0011 - Haptics / Tactility** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/menus/ExpandableFabMenu.kt
  - **Problem:** Long-press selection, range selection, FAB expansion, destructive confirmation, transfer completion, and errors have no haptic feedback.
  - **Impact:** The app is visually modern but physically quiet. File managers benefit heavily from tactile confirmation because actions are high-stakes.
  - **Fix:** Add a small `ArcileHaptics` helper for selection start, selection changed, success, warning, destructive confirm, and error. Trigger haptics on long press, select all, destructive confirm, transfer completion, conflict resolution, and invalid operations. Respect system haptic settings.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0012 - Visual System / Spacing** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** Spacing uses a mix of `MaterialTheme.spacing` tokens and raw hardcoded values such as 2dp, 4dp, 6dp, 8dp, 12dp, 16dp, 20dp, 24dp, 32dp, 80dp, and 100dp.
  - **Impact:** The UI rhythm subtly changes between Browser, Home, Quick Access, Settings, Storage, Trash, and Archive surfaces.
  - **Fix:** Expand spacing tokens to include semantic values: screenGutter, listItemHorizontal, listItemVertical, sheetHorizontal, toolbarBottomGap, sectionGap, compactGap. Replace raw dp values where they express layout rhythm. Keep raw values only for icon sizes or truly component-specific geometry.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0013 - Visual System / Shape** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/ui/theme/Shape.kt
  - **Problem:** Shape usage is expressive but not disciplined: large cards, circular chips, extraLarge menus, 16dp quick access rows, 24dp menu item groups, and raw rounded shapes coexist without a clear hierarchy.
  - **Impact:** The app looks modern, but not yet iconic or fully cohesive. Some surfaces feel pill-heavy while others are plain.
  - **Fix:** Define semantic shapes: fileRow, fileGridCard, toolbarPill, menuGroupFirst/Middle/Last, sheet, dialog, storageCard. Replace raw `RoundedCornerShape(...)` where it represents a shared pattern. Reserve circles for icons/FABs/true chips.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0014 - Visual System / Typography** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/ui/theme/Type.kt
  - **Problem:** Typography mostly mirrors Material defaults but includes negative letter spacing on `displayLarge` and does not define file-manager-specific roles for filename, metadata, path, volume metric, and danger labels.
  - **Impact:** Filename and metadata hierarchy depends on local choices, so scan efficiency varies between list, grid, trash, recent, archive, and quick access.
  - **Fix:** Add semantic text extensions: `filename`, `fileMetadata`, `pathBreadcrumb`, `storageMetric`, `sectionHeader`, `dangerLabel`. Use zero letter spacing unless intentionally matching a Material token that has been visually verified. Ensure line heights survive 1.3x-2.0x font scale.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0017 - Motion System** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** Motion is present but not systematic: route transitions slide horizontally, file grid press scales, FAB icon rotates, toolbars slide vertically, storage bars spring, thumbnails crossfade, and empty states bounce. These motions do not share duration, easing, hierarchy, or interruption rules.
  - **Impact:** The UI can feel animated but not choreographed. Premium apps feel calm because motion explains hierarchy.
  - **Fix:** Create `ArcileMotion` tokens for quick, standard, emphasized, container transform, list item placement, and destructive emphasis. Audit all `animate*`, `AnimatedVisibility`, `AnimatedContent`, `animateContentSize`, and `spring` calls. Define interruption behavior for file operations, selection, search, and folder navigation.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0024 - Navigation Motion** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt
  - **Problem:** All route transitions use the same horizontal slide/fade pattern regardless of destination type.
  - **Impact:** Settings, trash, recent files, storage dashboard, quick access, and archive viewer all move like peer pages, even when they are detail/supporting/modal-like destinations.
  - **Fix:** Define destination classes: top-level, detail, modal-ish utility, archive viewer. Use fade-through for settings/about/licenses, shared bounds for archive/category/file navigation, and standard predictive back for route pops. Respect reduced motion.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0027 - Empty / Loading States** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/EmptyState.kt
  - **Problem:** Empty states include animated/particle-like visual decoration, but there is no reduce-motion policy and state illustration quality is not tied to specific file-manager contexts.
  - **Impact:** Some states feel playful but not premium, and motion-sensitive users cannot opt out.
  - **Fix:** Add reduce-motion composition local or system animator scale check. Create context-specific empty states: empty folder, no search results, empty trash, no storage access, archive empty. Keep animation subtle and purposeful.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0031 - Color System** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/ui/theme/Color.kt
  - **Problem:** Raw success/failure colors (`0xFF4CAF50`, `0xFFF44336`) are used in operation progress, while category/accent palettes use fixed values that may not harmonize with dynamic color or OLED modes.
  - **Impact:** Success/error states can look off-brand or too bright in custom/dynamic/OLED schemes.
  - **Fix:** Add semantic colors: success, onSuccess, successContainer, warning, operationProgress, hiddenBadge. Harmonize category colors with selected dynamic seed where possible. Verify contrast in light/dark/OLED/dynamic custom accents.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0042 - Dialog/Input UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/dialogs arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Dialogs mostly use basic `AlertDialog` + `OutlinedTextField` flows. File naming edge cases, IME actions, invalid characters, duplicate names, extension handling, password visibility, and live preview are not consistently handled.
  - **Impact:** Users can hit errors late, and high-stakes creation/rename/archive flows feel utilitarian rather than polished.
  - **Fix:** Add reusable `FileNameInput` with invalid character checks, duplicate hints, extension handling, and IME Done. Add live destination preview for create/archive. Use password visual transformation and reveal toggles. Make error text accessible and localized.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0043 - Premium Feel** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** Screen quality varies noticeably: Browser and Home are richer, while ArchiveViewer, Tools, Settings rows, About, Licenses, and some dialogs feel closer to template Material UI.
  - **Impact:** The app can feel like several design eras in one product.
  - **Fix:** Create reusable screen primitives: `ArcileScreenScaffold`, `ArcileSectionHeader`, `ArcileListSurface`, `ArcileActionSheet`, `ArcileStateView`. Convert lower-polish screens first: ArchiveViewer, Tools, Licenses, About, Settings. Add screenshot QA for compact/light/dark/OLED.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Home / Tools Tasks

- [ ] **UI-0038 - Home Screen Polish** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt
  - **Problem:** Home includes utility cards for unimplemented features like OnlyFiles, Large Files, and FTP Server. They may be labeled as placeholders visually but still reduce perceived production quality.
  - **Impact:** Users see a premium shell mixed with "coming later" content, which feels unfinished.
  - **Fix:** Hide unimplemented utilities from production builds or move them to a clearly labeled "Labs/Coming soon" area. Prioritize working tools: Trash, Storage Analyzer, Recent, Quick Access, Search. If placeholders remain, provide disable semantics and clear unavailable state.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **TOOL-0032 - Product Surface / Debug Utility** `[Low]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/TestToolbar.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt`
  - **Problem:** `TestToolbar` still exists in main source and production UI still exposes unimplemented or placeholder-like tool surfaces.
  - **Impact:** Users may see unavailable functionality or inconsistent polish.
  - **Fix:** Remove unused debug UI from main source or move it into a debug source set. Gate unimplemented tools behind feature flags or hide them. Recommended Refactor: Add `FeatureFlagRepository` and build-type aware flags. Safer Alternative: Remove `TestToolbar` from main source if unused.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Architecture / Maintainability Tasks

- [ ] **COMPOSE-0011 - Compose Performance** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/StorageDashboardScreen.kt`
  - **Problem:** Several expensive sorts/groupings/transforms happen in composables or on every state-level change. Some are remembered, but state objects are broad so unrelated changes can still invalidate calculations.
  - **Impact:** Scrolling/searching/selection can feel less responsive in large datasets.
  - **Fix:** Move stable display lists/groupings into ViewModel state or dedicated memoized selectors. Use narrower immutable UI models. Add Compose compiler stability reports and macrobenchmarks. Recommended Refactor: Add `BrowserDisplayState` with already-filtered/sorted file lists and separate transient UI state from data state. Safer Alternative: Use `derivedStateOf` for scroll/progress-triggered derived values and tighten `remember` keys.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0034 - Compose Stability** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/BrowserViewModel.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/home/HomeViewModel.kt
  - **Problem:** Screen state classes pass standard `List`, `Set`, and `Map` collections through high-level composables. Compose treats standard collections as unstable unless wrapped/annotated/using immutable collections.
  - **Impact:** Large file lists and frequent folder stats updates may recompose more UI than necessary.
  - **Fix:** Run Compose compiler stability reports. Convert hot UI state collections to `kotlinx.collections.immutable` persistent collections or stable UI wrappers. Split large state into smaller state holders for browser files, selection, operation, search, and overlays.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **ARCH-0002 - Architecture / Storage Abstraction** `[Critical]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/FileRepository.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/data/LocalFileRepository.kt`
  - **Problem:** `FileRepository` is a broad god interface covering browsing, mutation, trash, archive operations, analytics, search, volume discovery, cached stats, and properties.
  - **Impact:** New features can regress unrelated workflows because every ViewModel depends on the same large abstraction.
  - **Fix:** Split into `FileBrowserRepository`, `FileMutationRepository`, `SearchRepository`, `StorageAnalyticsRepository`, `TrashRepository`, `ArchiveRepository`, and `VolumeRepository`. Give each interface explicit capability and error contracts. Keep an app facade only if needed for composition, not as the primary domain API. Recommended Refactor: Move use cases to depend on narrow interfaces. ViewModels should depend on use cases or feature facades, not the monolithic repository. Safer Alternative: Create adapter interfaces incrementally around current implementation and migrate one feature at a time.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **ARCH-0009 - Compose Architecture** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt`
  - **Problem:** `BrowserScreen.kt` is roughly 1,500 lines and owns too many dialogs, toolbars, state effects, operation UI, browser content, archive flows, clipboard UI, and bottom sheets in one file.
  - **Impact:** UI regressions become more likely as new file-manager features are added.
  - **Fix:** Split into `BrowserContent`, `BrowserDialogs`, `BrowserOperationOverlay`, `BrowserTopBars`, `BrowserEmptyStates`, and archive dialog files. Define a stable `BrowserUiActions` holder. Keep ephemeral UI state close to owning component. Recommended Refactor: Move browser-specific dialogs and operation surfaces into dedicated composables with focused previews/tests. Safer Alternative: Extract only dialogs and operation progress first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **ARCH-0012 - Presentation State Ownership** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/BrowserViewModel.kt`
  - **Problem:** `BrowserState` contains persistent navigation data, list data, search data, selection, clipboard, dialog visibility, native pending actions, properties, progress overlay state, and presentation preferences in one wide state object.
  - **Impact:** Unrelated UI changes can trigger wide recompositions.
  - **Fix:** Split into `BrowserNavigationState`, `BrowserListingState`, `BrowserSelectionState`, `BrowserSearchState`, `BrowserDialogState`, and `OperationUiState`. Expose separate StateFlows or a composed immutable screen state. Recommended Refactor: Use reducers/events for browser state transitions and test them directly. Safer Alternative: At minimum, move dialog visibility and operation state out of `BrowserState`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **MAINT-0030 - Maintainability / Code Organization** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/**`
  - **Problem:** The code is package-layered (`data`, `domain`, `presentation`) but not feature-modular. Feature concerns cross directories and files are growing large.
  - **Impact:** Indirect: feature velocity and regression risk will worsen.
  - **Fix:** Move toward feature packages/modules. Define public APIs per feature. Add architecture rules for dependencies. Recommended Refactor: Start with `feature:browser`, `feature:trash`, `feature:archive`, and `core:storage`. Safer Alternative: Within single module, reorganize packages under `feature/*` and `core/*`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### API / Domain Tasks

- [ ] **API-0003 - API Design / Type Safety** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/FileRepository.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/FileModel.kt`
  - **Problem:** Most APIs accept raw `String` paths for identities, selections, clipboard contents, destinations, archive entries, trash restoration, and routing.
  - **Impact:** Edge cases around paths with unusual separators, volume boundaries, virtual documents, and stale selections are more likely.
  - **Fix:** Add value classes for path/id concepts. Canonicalize once at boundary creation. Make destructive APIs require validated handles, not arbitrary strings. Recommended Refactor: Introduce `StorageNodeRef` containing backend id, volume id, display path, canonical identity, and capability flags. Safer Alternative: Start with value classes around existing strings and migrate high-risk methods first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **API-0031 - Kotlin API Quality** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/domain/*.kt`
  - **Problem:** Several domain models use primitive strings/longs for semantically distinct values and lack stronger invariants.
  - **Impact:** Edge-case bugs around sizes, timestamps, paths, ids, and category names can leak into UI.
  - **Fix:** Add value classes for bytes, timestamps where useful, ids, paths, category ids. Use sealed capability models. Keep display names distinct from identifiers. Recommended Refactor: Introduce invariant constructors/factories for high-risk model types. Safer Alternative: Add value classes only for path/id/storage volume concepts first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Filesystem Performance / Memory Tasks

- [ ] **MEM-0010 - Memory / Large Directory Handling** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/FileSystemDataSource.kt`
  - **Problem:** Directory listing loads all children into memory, maps all to `FileModel`, then sorts the full list before returning.
  - **Impact:** Huge directories can load slowly, freeze visible progress, or consume excessive memory.
  - **Fix:** Introduce paged/incremental directory listing. Emit loading batches through Flow. Add configurable sorting that can operate on chunks or defer expensive metadata. Recommended Refactor: Create `DirectoryListingDataSource.list(path): Flow<ListingPage>`. Safer Alternative: Add a max initial batch and "load more" fallback for huge folders.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Build / Startup Tasks

- [ ] **BUILD-0023 - Build System / Dependency Freshness** `[Medium]`
  - **Location:** `arcile-app/gradle/libs.versions.toml` `arcile-app/app/build.gradle.kts`
  - **Problem:** The build uses a single app module with no convention plugins, no feature modules, no benchmark module, and no modular dependency boundaries.
  - **Impact:** Indirect: slower iteration and higher regression risk as the app grows.
  - **Fix:** Add convention plugins. Split modules by `core:domain`, `core:storage`, `core:ui`, `feature:browser`, `feature:home`, `feature:trash`, `feature:archive`, `benchmark`. Add dependency analysis and version update checks as ongoing tooling, not as a one-off dependency freshness task. Recommended Refactor: Start by extracting pure Kotlin domain and storage interfaces. Safer Alternative: Keep one APK module but add Gradle convention logic and package-level architecture checks.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Testing / QA Tasks

- [ ] **UI-0044 - Testing / Visual QA** `[Medium]`
  - **Location:** arcile-app/app/src/androidTest/java/dev/qtremors/arcile/ui arcile-app/app/src/test/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** There are UI tests, but the audit found no evidence of systematic screenshot/golden testing for theme modes, font scales, RTL, screen sizes, foldable layouts, or 1,000-file stress states.
  - **Impact:** Pixel-level regressions can ship unnoticed.
  - **Fix:** Add Paparazzi/Roborazzi or Compose screenshot testing. Cover compact phone, landscape, tablet, RTL, fontScale 1.5/2.0, light/dark/OLED/dynamic fallback. Add macrobenchmarks for browser scroll, thumbnail grid, search, and storage load.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **TEST-0025 - Testability / Production Verification** `[High]`
  - **Location:** `arcile-app/app/src/test/**` `arcile-app/app/src/androidTest/**`
  - **Problem:** The project has useful unit/UI tests, but lacks stress, benchmark, mutation recovery, SAF compatibility, process-death, and real large-directory tests.
  - **Impact:** Critical regressions may only appear on real devices with large storage.
  - **Fix:** Add contract tests for storage backends. Add large directory synthetic tests. Add transfer cancellation/recovery tests. Add archive safety tests. Add macrobenchmarks for startup, listing, scrolling, search, thumbnail grid. Recommended Refactor: Create test fixtures for in-memory, temp filesystem, SAF-like fake, and failure-injecting storage backends. Safer Alternative: Add stress tests for `FileTransferEngine`, `TrashManager`, and `FolderStatsCalculator` first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Thumbnail Tasks

- [ ] **THUMB-0027 - Thumbnail / Image Loading Performance** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/image/**`
  - **Problem:** Thumbnails are loaded directly from files while browsing, with custom fetchers but no global thumbnail budget, cancellation tuning, or memory policy tied to list/grid density.
  - **Impact:** Fast scrolling huge media folders can jank or trigger I/O pressure.
  - **Fix:** Add thumbnail request policy based on visible rows, density, and active operation state. Disable/pause expensive thumbnails during bulk operations. Add failure cache for corrupt files. Recommended Refactor: Introduce `ThumbnailPolicy` and `ThumbnailKey` independent from raw File path. Safer Alternative: Limit custom thumbnails to small files and known-safe types until richer policy exists.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Localization Tasks

- [ ] **UI-0032 - Localization / Production Polish** `[Medium]`
  - **Location:** arcile-app/app/build.gradle.kts arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** The project has a useful hardcoded-string guard, but current hardcoded strings remain across Settings, ArchiveViewer, SearchTopBar, ClipboardContentsDialog, PropertiesDialog, Browser snackbars, MainActivity toasts, and ActiveFiltersRow.
  - **Impact:** The app feels partially localized and partially prototype-like.
  - **Fix:** Expand the hardcoded string task to all production composables. Move all user-visible strings and content descriptions to resources. Add plural resources for counts and file/entry labels.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **I18N-0028 - Localization / UI Quality** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/**` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`
  - **Problem:** Several user-facing strings remain hardcoded in composables, helpers, and notifications.
  - **Impact:** Localization, accessibility, and consistency suffer.
  - **Fix:** Move all visible strings/content descriptions/notification text to resources. Expand `checkProductionStrings` or use lint/custom Detekt rules. Recommended Refactor: Add a UI text wrapper for domain errors and operation messages. Safer Alternative: Start with operation notifications, ArchiveViewer, Settings, dialogs, and ShareHelper chooser title.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

---
## Backlog / Future Ideas

> A parking lot for future ideas, enhancements, and unprioritized Android file-manager features.

### File Browsing & Navigation
- **Dual-Pane File Browser**: Add a tablet, foldable, and landscape mode with two live folder panes for drag/copy/move workflows.
- **Breadcrumb Path Editing**: Let users tap the current path and type or paste a filesystem path directly.
- **Folder Tabs**: Keep multiple folders open at once with tab restore after process death.
- **Starred / Favorited Files**: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Recent Locations**: Track recently opened folders separately from recently modified files.
- **Quick Jump Drawer**: Provide fast access to Download, DCIM, Documents, Android/media, SD cards, OTG drives, network locations, and pinned folders.

### File Operations & Reliability
- **Queued Operation Manager**: Queue copy, move, delete, rename, compress, extract, and trash actions with pause, resume, cancel, retry, and background notifications.
- **Conflict Resolution Dialogs**: Offer replace, skip, rename, apply-to-all, compare metadata, and keep-both choices during copy/move/extract operations.
- **Checksum Verification**: Add MD5, SHA-1, and SHA-256 calculation plus optional post-copy verification for large transfers.
- **Batch Rename Tool**: Support numbered patterns, date tokens, find/replace, case conversion, extension changes, and live preview.
- **Undo Recent Operations**: Provide a short-lived undo stack for rename, move, trash, and folder creation actions where safe.
- **Safe Large Transfer Mode**: Warn before moving large folders across storage volumes and fall back to copy-then-delete with verification.
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations for auditing and troubleshooting.

### Archives & File Formats
- **APK / AAB Inspector**: Show package name, version, signatures, permissions, icons, split APK details, and install/open actions.
- **Text / Code Previewer**: Add a lightweight viewer with encoding detection, syntax highlighting, line numbers, search, and share/copy actions.
- **PDF / Document Preview Hooks**: Provide better thumbnails and safe handoff to installed viewers for PDFs, Office files, and ebooks.

### Storage, Access & Android Integration
- **Storage Health Diagnostics**: Basic storage status checks, mount state, free-space trends, and repair/trim suggestions for mounted volumes.
- **Mount / Unmount Awareness**: Detect SD card and USB OTG changes immediately, refresh affected screens, and recover pending operations gracefully.
- **MediaStore Rescan Tools**: Manually rescan selected files or folders so gallery, music, and downloads apps see changes sooner.
- **MTP / USB Transfer Mode Notes**: Surface helpful guidance when Android's USB file transfer state blocks desktop access.

### Search, Filters & Organization
- **Indexed Search**: Build an optional local index for faster filename, extension, MIME type, size, and modified-date searches.
- **Advanced Search Filters**: Add filters for date range, file size range, media duration, image dimensions, duplicate candidates, hidden files, and storage volume.
- **Saved Searches**: Allow users to pin reusable searches such as "large videos", "old APKs", or "recent downloads".
- **Duplicate Finder**: Detect duplicates with size pre-filtering and optional content hashing before deletion.
- **Large / Old Files Cleanup**: Add focused views for files likely worth reviewing when freeing space.
- **Empty Folder Finder**: Scan and clean empty folders with preview and exclusions.
- **Smart Collections**: Auto-group screenshots, screen recordings, APKs, downloads, documents, and WhatsApp/Telegram media.
- **Storage Analyzer ("Filelight" View)**: A dedicated radial map or sunburst chart to visualize storage usage by folder and file type.

### Media & Preview Experience
- **Rich Media Previews**: Continue expanding custom Coil `Fetcher` components for rich file previews.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally scrollable carousel of the 10 most recently modified images and videos.
- **Image Detail Sheet**: Show resolution, EXIF, location presence, orientation, color profile, and quick rotate/share actions.
- **Video Detail Sheet**: Show duration, resolution, codec, bitrate, frame rate, subtitles, and thumbnail refresh actions.
- **Audio Detail Sheet**: Show album art, artist, album, duration, bitrate, and quick metadata cleanup hints.
- **Thumbnail Cache Controls**: Add settings to clear, size-limit, or rebuild thumbnail caches.
- **Hidden Media Controls**: Create or remove `.nomedia` files with clear warnings about gallery visibility.

### Automation & Power Tools
- **Automated Task Rules**: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.
- **Watched Folders**: Monitor selected folders for new files and offer rule actions such as rename, move, compress, or notify.
- **Quick Actions**: Let users configure a small set of folder-specific actions like "send to SD", "compress here", or "clean old files".
- **Scheduled Cleanup**: Periodically empty trash, remove temporary files, or prompt for old downloads review.

### Sharing, Transfers & Network
- **Nearby Share / Share Sheet Polish**: Improve multi-file sharing flow, MIME grouping, and error messages for unsupported targets.
- **Local HTTP File Drop**: Temporarily host selected files or a folder over LAN with a QR code and one-tap shutdown.
- **WebDAV / SMB Client**: Browse and transfer files from NAS, routers, and desktop shares.
- **FTP / SFTP Client**: Add optional remote location support for advanced users.
- **Wi-Fi Direct Transfer**: Explore device-to-device transfer without an external network.

### Security, Privacy & Safety
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
- **Secure Delete Option**: Provide best-effort overwrite/delete workflows where storage type makes it meaningful, with honest limitations.
- **Private Folder Bookmarks**: Hide selected pinned folders behind biometric confirmation.
- **Trash Privacy Audit**: Review shared `.trash` behavior, visibility to other apps, and options for app-private trash on supported storage.
- **Sensitive Metadata Warnings**: Warn before sharing images with location EXIF or documents with embedded author metadata.
- **App Lock**: Add optional biometric/PIN lock for opening Arcile.

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` where a main action regularly needs a secondary dropdown or overflow action.
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press, successful file operations, and error states.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations such as Lottie for empty folders, trash, and search results.
- **Selection Mode Polish**: Improve multi-select toolbar actions, selected-count affordances, drag selection, and range selection.
- **Adaptive Bottom Actions**: Keep high-frequency actions thumb-friendly on phones while preserving dense toolbars on tablets.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Settings, Personalization & Accessibility
- **File Icon Packs**: Allow optional icon style packs for file types, folders, archives, media, and app packages.
- **Compact / Comfortable Density**: Add a display-density setting for list rows, grid cells, and toolbars.
- **Large Text Audit**: Verify folder lists, dialogs, and bottom sheets at Android large-font accessibility settings.
- **High Contrast Theme**: Add a contrast-focused theme profile beyond light, dark, and OLED modes.
- **Gesture Customization**: Configure swipe actions, long-press behavior, and double-tap shortcuts.

### Multi-Window, Layout & Devices
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.
- **Foldable Layouts**: Add hinge-aware navigation and dual-pane behavior for large foldable devices.
- **Tablet Navigation Rail**: Use a rail or permanent navigation surface on wider screens.
- **Keyboard & Mouse Support**: Add keyboard shortcuts, hover states, context menus, and precise right-click behavior.
- **Chromebook Polish**: Verify resizable windows, external storage access, drag/drop, and system file picker interoperability.

### Developer, Build & Release
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.
- **Internal Diagnostics Export**: Export anonymized app state, settings, storage classifications, and recent operation errors for bug reports.
- **Performance Benchmarks**: Add repeatable benchmarks for large folder listing, recursive stats, thumbnail loading, and search.
- **StrictMode Debug Profile**: Enable stricter debug-only checks for disk I/O, leaked resources, and slow main-thread work.
