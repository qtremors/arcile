# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.6.8
> **Last Updated:** 2026-05-14

---

## Consolidated Tasks

### UI / UX Tasks

- [ ] **UI-0001 - Adaptive Layout / Information Architecture** `[Critical]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt
  - **Problem:** The primary app structure is a two-page `HorizontalPager` between Home and Browser, with no adaptive navigation rail, list-detail layout, dual-pane file browser, or tablet/foldable workspace.
  - **Impact:** Large screens feel stretched rather than upgraded. Power users lose the most valuable file-manager workflows: two-pane copy/move, persistent folder panes, drag/drop, and side-by-side properties or preview.
  - **Fix:** Add `androidx.compose.material3.adaptive` and derive layout from `currentWindowAdaptiveInfo()`. Use bottom navigation or the current pager only for compact width. Use navigation rail plus list-detail or supporting pane for medium/expanded width. Add a dual-browser-pane mode on expanded width with independent path, sort, selection, and clipboard scopes. Preserve current single-pane phone behavior behind the adaptive shell.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0002 - Edge-to-Edge / Insets** `[Critical]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArcileAppShell.kt
  - **Problem:** The root `Scaffold` sets `contentWindowInsets = WindowInsets(0, 0, 0, 0)` and downstream screens mostly rely on top padding while bottom gesture/nav/IME/cutout handling is inconsistent.
  - **Impact:** Bottom toolbars, snackbars, FABs, dialogs, and list content can crowd gesture navigation, 3-button nav, landscape cutouts, and desktop window caption areas. This lowers platform quality immediately on Android 15+ and targetSdk 36.
  - **Fix:** Define root inset policy: draw backgrounds edge-to-edge but inset interactive controls. Let Material 3 bars handle their default insets where appropriate. Replace magic bottom padding with `WindowInsets.safeDrawing`, `navigationBarsPadding()`, `imePadding()`, and `windowInsetsPadding()`. Audit all `Scaffold` instances for explicit `contentWindowInsets`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0003 - Predictive Back / Platform Consistency** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt
  - **Problem:** Back behavior is handled through several `BackHandler` branches, but there is no predictive back progress, shared transition integration, or system-preview-aware hierarchy.
  - **Impact:** Back gestures feel less modern and less trustworthy. Users cannot preview whether back will close search, clear selection, move up a folder, return to Home, or leave the app.
  - **Fix:** Define a back priority stack: modal, sheet, search, selection, folder up, route pop, app exit. Use Navigation Compose predictive back support where route transitions apply. Use `PredictiveBackHandler` for browser folder-up progress and shared element/bounds opportunities. Add tests for back priority ordering.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0004 - Dependency Freshness / Material 3 Expressive** `[High]`
  - **Location:** arcile-app/gradle/libs.versions.toml
  - **Problem:** The project pins `composeBom = "2025.02.00"` and `composeMaterial3 = "1.4.0-alpha08"`, while the current Material 3 and animation/adaptive libraries have moved forward substantially.
  - **Impact:** Arcile misses newer expressive components, animation fixes, shared transition stability, adaptive APIs, and Material defaults.
  - **Fix:** Update Compose BOM and Material 3 to a current compatible set after checking release notes. Add `compose-material3-adaptive` for wide/foldable layouts. Audit experimental imports after upgrade. Run visual regression and instrumentation tests on core screens.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0005 - Recent Files sort action** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt
  - **Problem:** The sort action is visible but no-op (`onClick = { /* show sort dialog if needed */ }`).
  - **Impact:** This is a premium-feel breaker: users tap a polished control and nothing happens.
  - **Fix:** Implement a Recent Files sort/filter sheet or remove the sort icon until it works. Include sort by date, name, size, type, and group mode. Persist recent sort preference.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0006 - Trash sort action** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/TrashScreen.kt
  - **Problem:** The Trash sort action is visible but no-op (`onClick = { /* show sort dialog */ }`).
  - **Impact:** Trash recovery workflows become slower as trash grows, and a dead control makes the app feel unfinished.
  - **Fix:** Add Trash sort by deletion date, original path, name, size, and type. Add filter by recoverability/destination if that metadata is available. Hide the action until implemented.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0007 - Settings thumbnail strings** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/SettingsScreen.kt
  - **Problem:** The thumbnails setting section uses hardcoded production strings: `"Appearance"`, `"Show Thumbnails"`, and `"Display image and video thumbnails instead of file icons."`.
  - **Impact:** Localization breaks and the settings screen looks inconsistent with the rest of the app.
  - **Fix:** Move all strings to `strings.xml`. Add the affected file to the hardcoded-string verification target if missing. Consider merging this Appearance section with the existing Appearance section to avoid duplicate headings.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0008 - Archive viewer strings** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArchiveViewerScreen.kt
  - **Problem:** Archive viewer strings and content descriptions are hardcoded: `"Back"`, `"Extract folder"`, `"Extract archive"`, `"Folder"`, `"Archive password"`, `"Password"`, `"Open"`, `"Cancel"`, and summary strings.
  - **Impact:** Archive handling feels less polished than the browser, and localization/accessibility are inconsistent.
  - **Fix:** Move all archive viewer strings to resources, including plural resources for entries. Use existing reusable file row/icon components where possible. Add archive-specific string tests to `checkProductionStrings`.
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

- [ ] **UI-0015 - Large Font / Responsive Layout** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** File metadata and date are forced into one horizontal row with a weighted spacer; grid card text uses fixed one-line labels. Extremely long filenames, large font scales, RTL, and narrow split-screen will truncate important data aggressively.
  - **Impact:** Users browsing serious file collections lose context and may misidentify files.
  - **Fix:** At compact/large-font thresholds, move date under size/count instead of right-aligning in the same row. Allow two filename lines in grid when cell width permits. Add `LocalConfiguration.fontScale` aware row variants or use `TextMeasurer`/constraints. Add screenshot tests at fontScale 1.5 and 2.0.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0016 - Search UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/SearchTopBar.kt
  - **Problem:** Search actions use hardcoded content descriptions `"Clear"` and `"Filters"`, and the default placeholder is hardcoded `"Search files..."`.
  - **Impact:** Search is one of the highest-frequency file-manager workflows; small accessibility/localization inconsistencies are highly visible.
  - **Fix:** Require callers to provide localized labels or use string resources inside the component. Add `searchSemantics` with clear query and open filters actions. Consider active filter count in the filter button state.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0017 - Motion System** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** Motion is present but not systematic: route transitions slide horizontally, file grid press scales, FAB icon rotates, toolbars slide vertically, storage bars spring, thumbnails crossfade, and empty states bounce. These motions do not share duration, easing, hierarchy, or interruption rules.
  - **Impact:** The UI can feel animated but not choreographed. Premium apps feel calm because motion explains hierarchy.
  - **Fix:** Create `ArcileMotion` tokens for quick, standard, emphasized, container transform, list item placement, and destructive emphasis. Audit all `animate*`, `AnimatedVisibility`, `AnimatedContent`, `animateContentSize`, and `spring` calls. Define interruption behavior for file operations, selection, search, and folder navigation.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0018 - Compose Performance / Lazy Lists** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** `animateContentSize()` is applied to every grid item container, and thumbnails use `SubcomposeAsyncImage` with crossfade in lazy grid cells.
  - **Impact:** Huge directories with media thumbnails can feel less responsive, especially on low-end devices or during fast fling.
  - **Fix:** Remove or strictly bound `animateContentSize()` from grid cells. Prefer `AsyncImage` with a stable placeholder for common cases; reserve subcomposition for complex states. Use thumbnail size requests based on cell dimensions. Add macrobenchmark for 1,000 mixed files with thumbnails.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0019 - Power User Workflow** `[Critical]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** There is no dual-pane copy/move workflow, drag/drop, side-by-side source/destination browsing, or persistent multi-folder workspace.
  - **Impact:** Arcile cannot yet rival Solid Explorer, MiXplorer, FX, or Samsung My Files for serious file management.
  - **Fix:** Add expanded-width dual-pane mode. Add "send to other pane" and drag/drop between panes. Show operation preview with conflict count before paste. Preserve pane paths after process death.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0020 - Search / Filtering** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/SearchFiltersBottomSheet.kt
  - **Problem:** Search filters cover item type, file type, broad size buckets, and simple date buckets, but lack file-manager-grade filters such as extension, hidden files, storage volume, path scope, exact date range, MIME, media dimensions/duration, duplicate candidates, and saved searches.
  - **Impact:** Users with large storage libraries cannot narrow results precisely.
  - **Fix:** Add advanced filter mode while keeping default mode simple. Include extension/type chips, hidden toggle, storage volume, date range, size range, folder scope, and saved search presets. Show active filter chips with clear labels and counts.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0021 - Trash UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/TrashScreen.kt
  - **Problem:** Trash has recovery and empty actions, but lacks visual grouping by deletion time/original location, retention policy, recovery warnings, and completed properties action.
  - **Impact:** Users may hesitate before restore/delete because context is thin.
  - **Fix:** Group trash by "Today", "Yesterday", "Older" or original folder. Show original path, deletion date, and restore destination confidence. Complete the overflow Properties action. Add undo/snackbar for safe restore where possible.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0022 - Destructive Actions / Safety** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/dialogs/DeleteConfirmationDialog.kt
  - **Problem:** Delete flows exist, but the broader UI does not consistently distinguish reversible trash, permanent delete, native scoped delete, and mixed delete flows at the interaction level.
  - **Impact:** High-stakes file actions can feel more frightening than necessary, especially with mixed selections and SAF/native delete flows.
  - **Fix:** Create a delete decision surface that clearly shows destination: Trash vs Permanent vs Android system confirmation. Include selected count, total size, folder count, and irreversible warning. Add undo for trash moves when feasible. Use consistent red only for irreversible final confirmation.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0023 - Bottom Toolbar / Safe Areas** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/FloatingSelectionToolbar.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Floating selection and operation toolbars use fixed 56dp height, fixed 16dp padding, snackbar offsets of 80dp, and list bottom padding of 100dp rather than measuring/insetting the actual overlay.
  - **Impact:** On large font, landscape, gesture nav, 3-button nav, and foldables, content and snackbars can sit awkwardly or leave excessive dead space.
  - **Fix:** Measure bottom toolbar height or expose a shared bottom overlay inset. Apply `navigationBarsPadding()` / safeDrawing bottom to the toolbar. Feed calculated bottom content padding into lists. Unify Browser, Recent, and Trash overlay behavior.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0024 - Navigation Motion** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt
  - **Problem:** All route transitions use the same horizontal slide/fade pattern regardless of destination type.
  - **Impact:** Settings, trash, recent files, storage dashboard, quick access, and archive viewer all move like peer pages, even when they are detail/supporting/modal-like destinations.
  - **Fix:** Define destination classes: top-level, detail, modal-ish utility, archive viewer. Use fade-through for settings/about/licenses, shared bounds for archive/category/file navigation, and standard predictive back for route pops. Respect reduced motion.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0025 - Gestures** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Category folder tabs add a `pointerInput` horizontal drag threshold of `96f` inside pull-to-refresh and within the app's Home/Browser pager ecosystem.
  - **Impact:** Users may accidentally switch folder tabs while trying to scroll, refresh, or navigate. Gesture meaning changes based on screen mode.
  - **Fix:** Prefer visible `FolderTabsRow` controls and optional pager/tab component for category folders. If horizontal swipe remains, use velocity/threshold tuned in dp, announce it, and disable when TalkBack is active. Add haptics at successful tab switch.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0026 - Selection UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** Selection relies mainly on background color and shape. Range selection is hidden behind long-pressing a second item after previous interaction.
  - **Impact:** Power users may not discover range selection; accessibility and color-blind users may miss selected state.
  - **Fix:** Add explicit check badges and selected count feedback. Add overflow menu "Select range..." or drag/range mode for advanced users. Add semantic selected state and custom select/unselect actions.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0027 - Empty / Loading States** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/EmptyState.kt
  - **Problem:** Empty states include animated/particle-like visual decoration, but there is no reduce-motion policy and state illustration quality is not tied to specific file-manager contexts.
  - **Impact:** Some states feel playful but not premium, and motion-sensitive users cannot opt out.
  - **Fix:** Add reduce-motion composition local or system animator scale check. Create context-specific empty states: empty folder, no search results, empty trash, no storage access, archive empty. Keep animation subtle and purposeful.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0028 - Archive UX** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArchiveViewerScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Archive workflows exist but lack premium UX: password fields are plain text, extraction progress is not deeply contextual, archive viewer is visually bare, and archive navigation lacks breadcrumbs.
  - **Impact:** Archive handling will not yet feel competitive with MiXplorer/Solid Explorer.
  - **Fix:** Use `PasswordVisualTransformation` and reveal toggle for archive passwords. Add archive breadcrumb/header, entry count, compression summary, and extract destination preview. Show extraction progress with current entry and cancel state. Support encrypted archive failure messaging.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0029 - Quick Access / SAF UX** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessScreen.kt
  - **Problem:** Quick Access allows manual custom paths and SAF folders, but there is no validation preview, permission state, broken-link state, or graceful explanation for Android/data and Android/obb handoff limitations.
  - **Impact:** Users can create dead shortcuts and may not understand why restricted folders open externally.
  - **Fix:** Validate custom path before adding or show "unverified" state. Show SAF grant status and revoke/re-request actions. Explain external handoff folders with a compact warning/helper row. Add "test/open" after adding.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0030 - Hidden Files UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** Hidden files are only represented by `.alpha(0.5f)` when the filename starts with `"."`.
  - **Impact:** Hidden state is easy to miss, color/contrast suffers, and users lack a clear toggle or explanation.
  - **Fix:** Add a hidden-files visibility setting and per-folder preference. Use a small hidden/visibility-off badge plus metadata label. Include hidden state in semantics. Avoid reducing text contrast below accessibility thresholds.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0031 - Color System** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/ui/theme/Color.kt
  - **Problem:** Raw success/failure colors (`0xFF4CAF50`, `0xFFF44336`) are used in operation progress, while category/accent palettes use fixed values that may not harmonize with dynamic color or OLED modes.
  - **Impact:** Success/error states can look off-brand or too bright in custom/dynamic/OLED schemes.
  - **Fix:** Add semantic colors: success, onSuccess, successContainer, warning, operationProgress, hiddenBadge. Harmonize category colors with selected dynamic seed where possible. Verify contrast in light/dark/OLED/dynamic custom accents.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0032 - Localization / Production Polish** `[Medium]`
  - **Location:** arcile-app/app/build.gradle.kts arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** The project has a useful hardcoded-string guard, but current hardcoded strings remain across Settings, ArchiveViewer, SearchTopBar, ClipboardContentsDialog, PropertiesDialog, Browser snackbars, MainActivity toasts, and ActiveFiltersRow.
  - **Impact:** The app feels partially localized and partially prototype-like.
  - **Fix:** Expand the hardcoded string task to all production composables. Move all user-visible strings and content descriptions to resources. Add plural resources for counts and file/entry labels.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0033 - Permission UX** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/ArcileAppShell.kt arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt
  - **Problem:** The all-files-access permission screen is a centered card with title/body/button, but lacks richer rationale, denied-state recovery, privacy reassurance, Android version nuance, and fallback paths.
  - **Impact:** Users may deny or abandon permission because the app does not clearly explain the benefit and boundaries.
  - **Fix:** Add a permission rationale screen with concrete benefits and privacy notes. Show fallback for limited access/SAF where available. Handle "permission denied" return with next-step copy. Move toast strings to resources.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0034 - Compose Stability** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/BrowserViewModel.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/home/HomeViewModel.kt
  - **Problem:** Screen state classes pass standard `List`, `Set`, and `Map` collections through high-level composables. Compose treats standard collections as unstable unless wrapped/annotated/using immutable collections.
  - **Impact:** Large file lists and frequent folder stats updates may recompose more UI than necessary.
  - **Fix:** Run Compose compiler stability reports. Convert hot UI state collections to `kotlinx.collections.immutable` persistent collections or stable UI wrappers. Split large state into smaller state holders for browser files, selection, operation, search, and overlays.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0035 - RTL / Layout Direction** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Volume root content padding explicitly uses `LayoutDirection.Ltr` for left/right calculations.
  - **Impact:** RTL layouts may receive incorrect start/end padding and feel less platform-native.
  - **Fix:** Use `LocalLayoutDirection.current` or start/end values directly. Prefer inset-aware layout APIs rather than manual LTR calculations. Add RTL screenshot test for browser root and file list.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0036 - File List Performance** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt
  - **Problem:** Formatted dates are computed during item composition, and list/grid item models are raw domain models rather than preformatted stable UI rows.
  - **Impact:** Large directories can spend composition work on repeated formatting.
  - **Fix:** Introduce `FileRowUiModel` with formatted date/size/subtitle/icon type. Precompute visible row metadata when file list or locale/time config changes. Keep expensive formatting out of hot lazy item scopes.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0037 - Desktop / Keyboard / Mouse** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** There is no visible support for keyboard shortcuts, hover states, right-click/context menus, mouse drag/drop, focus rings, or desktop window resizing patterns.
  - **Impact:** On tablets, ChromeOS, desktop mode, and external keyboards, Arcile falls behind premium file managers.
  - **Fix:** Add keyboard shortcut map: search, back/up, select all, copy/cut/paste, rename, delete, new folder, properties, toggle view. Add hover/focus styling for rows, cards, and toolbar actions. Add right-click/context menu using platform pointer handling. Add drag/drop with keyboard alternatives.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0038 - Home Screen Polish** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt
  - **Problem:** Home includes utility cards for unimplemented features like OnlyFiles, Large Files, and FTP Server. They may be labeled as placeholders visually but still reduce perceived production quality.
  - **Impact:** Users see a premium shell mixed with "coming later" content, which feels unfinished.
  - **Fix:** Hide unimplemented utilities from production builds or move them to a clearly labeled "Labs/Coming soon" area. Prioritize working tools: Trash, Storage Analyzer, Recent, Quick Access, Search. If placeholders remain, provide disable semantics and clear unavailable state.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0039 - Storage Visualization** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/StorageDashboardScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/home/StorageSummaryCards.kt
  - **Problem:** Storage insight is mainly a segmented bar and category list. It lacks a premium analyzer visualization, drill-down, cleanup candidates, and explanatory hierarchy.
  - **Impact:** Arcile misses a major differentiator against Files by Google and Samsung My Files.
  - **Fix:** Add folder/category treemap or sunburst/radial view for storage. Add large files, old downloads, duplicate candidates, APKs, videos, and cache-like cleanup groups. Allow tapping category to browse scoped files with sort preserved.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0040 - File Manager Feature Depth** `[High]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt
  - **Problem:** Core file-manager advanced workflows are missing or not surfaced: batch rename, duplicate finder, saved searches, file preview, APK inspector, text/code viewer, metadata cleanup, network/cloud locations, operation history, and undo stack.
  - **Impact:** Arcile is not yet competitive with the best file managers for expert users.
  - **Fix:** Create a command system and tool registry. Prioritize: operation history, undo for safe operations, batch rename, storage analyzer, text preview, APK inspector, saved searches. Design tools as focused workflows, not placeholder cards.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **UI-0041 - State Restoration** `[Medium]`
  - **Location:** arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessScreen.kt arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/TrashScreen.kt
  - **Problem:** Several transient UI states use `remember` instead of `rememberSaveable`, including FAB expansion and multiple dialogs/sheets. Process death or rotation can reset in-progress UI tasks.
  - **Impact:** Users can lose typed dialog input or mode context during rotation, fold/unfold, memory pressure, or process recreation.
  - **Fix:** Audit all `remember { mutableStateOf(...) }` UI state. Use `rememberSaveable` for typed inputs, open sheets/dialogs where safe. Hoist workflow-critical state to ViewModels. Add rotation/process-death tests for create/rename/archive/search/selection.
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

- [ ] **UI-0044 - Testing / Visual QA** `[Medium]`
  - **Location:** arcile-app/app/src/androidTest/java/dev/qtremors/arcile/ui arcile-app/app/src/test/java/dev/qtremors/arcile/presentation/ui
  - **Problem:** There are UI tests, but the audit found no evidence of systematic screenshot/golden testing for theme modes, font scales, RTL, screen sizes, foldable layouts, or 1,000-file stress states.
  - **Impact:** Pixel-level regressions can ship unnoticed.
  - **Fix:** Add Paparazzi/Roborazzi or Compose screenshot testing. Cover compact phone, landscape, tablet, RTL, fontScale 1.5/2.0, light/dark/OLED/dynamic fallback. Add macrobenchmarks for browser scroll, thumbnail grid, search, and storage load.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Storage Platform Tasks

- [ ] **STORAGE-0001 - Android Storage / Platform Compatibility** `[Critical]`
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml`
  - **Problem:** The app depends on `MANAGE_EXTERNAL_STORAGE` as the central storage permission, with no mature SAF/tree-grant abstraction for restricted folders, SD cards, OTG, cloud document providers, or Android/data/obb handoff.
  - **Impact:** Users on devices or stores that restrict all-files access can lose core browsing and mutation functionality.
  - **Fix:** Introduce a `StorageBackend` abstraction with raw-file, MediaStore, and SAF implementations. Model capabilities: readable, writable, trashable, renameable, supports atomic rename, supports random access. Add persisted URI grant management and tree permission recovery flows. Keep `MANAGE_EXTERNAL_STORAGE` as one backend, not the architecture. Recommended Refactor: Move path-centric operations behind a `FileHandle`/`StorageNode` domain API instead of passing raw `String` paths everywhere. Safer Alternative: For restricted directories, route users through SAF tree grants and expose read/write operations through `ContentResolver`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **STORAGE-0006 - MediaStore / Scoped Storage** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/MediaStoreClient.kt`
  - **Problem:** Media queries depend heavily on `MediaStore.Files.FileColumns.DATA` and `LIKE` path filtering.
  - **Impact:** Search/category/recent results can miss files, include stale paths, or behave differently across Android versions/OEMs.
  - **Fix:** Prefer MediaStore ids, volume names, `RELATIVE_PATH`, `DISPLAY_NAME`, `MIME_TYPE`, and provider URIs. Validate existence/capability lazily at action time. Add stale row cleanup/rescan strategy. Recommended Refactor: Represent MediaStore results as `MediaStoreNodeRef` and only convert to raw file nodes when capability exists. Safer Alternative: Keep DATA fallback behind an Android-version-specific compatibility adapter.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Architecture Tasks

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

### Performance Tasks

- [ ] **PERF-0004 - Filesystem performance** `[Critical]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/FileTransferEngine.kt`
  - **Problem:** Every file copy/move verifies integrity by hashing the complete source and target with SHA-256. Directories are materialized into full file lists with `walkTopDown().toList()` before verification.
  - **Impact:** Large media copies can take roughly 2x or more I/O time, drain battery, and appear stalled.
  - **Fix:** Make verification policy explicit: metadata-only default, optional checksum verification. Stream directory verification without `toList()`. Emit progress during verification. Persist operation phases so UI does not look frozen. Recommended Refactor: Move verification into `TransferVerifier` with strategies: size+mtime, sampled hash, full hash, no verification. Safer Alternative: Use full SHA-256 only for cross-volume move before deleting source, and only when user enables safe transfer mode.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **PERF-0005 - Filesystem Performance / Memory** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/manager/TrashManager.kt`
  - **Problem:** Trash move/restore duplicates the same full SHA-256 verification and directory `walkTopDown().toList()` behavior as transfer operations.
  - **Impact:** Moving large folders to trash or restoring them can be slow, battery-heavy, and fragile.
  - **Fix:** Reuse `FileTransferEngine` or extract a shared `StorageTransferEngine`. Stream verification. Add operation progress for trash/restore/delete. Recommended Refactor: Make trash a specialized move destination implemented over the same transfer primitives. Safer Alternative: For same-volume trash, require atomic rename; for cross-volume fallback, use configurable verification and journaled cleanup.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **PERF-0013 - Storage Analytics / Query Efficiency** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/MediaStoreClient.kt`
  - **Problem:** Category storage calculation performs separate MediaStore scans per category and then validates matches again in Kotlin.
  - **Impact:** Storage dashboard can be slow or stale on large media libraries.
  - **Fix:** Prefer single query with projection and aggregate in one cursor pass. Cache per volume with invalidation based on media generation/version where available. Add background refresh policy. Recommended Refactor: Create `StorageAnalyticsIndex` that can be backed by MediaStore snapshots and later app-owned indexing. Safer Alternative: Group categories into MIME-prefix queries first, then extension fallbacks.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Operations Tasks

- [ ] **OP-0007 - Foreground Operations / Reliability** `[Critical]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationCoordinator.kt`
  - **Problem:** Long-running operations are foreground-service based but not durable. Requests, progress, phase, cancellation state, and partial results are held in memory only.
  - **Impact:** If the process is killed, the user can lose operation visibility and may be left with staged temp files or partial copies.
  - **Fix:** Introduce an operation database/journal. Record source, destination, staging paths, phase, bytes, result, and rollback plan. On app/service startup, recover or clean incomplete operations. Update notifications with real progress and cancel action. Recommended Refactor: Build an `OperationEngine` independent of ViewModels and expose operation state via repository/Flow. Safer Alternative: For now, persist only active operation metadata and staged paths, then cleanup on next launch.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Concurrency Tasks

- [ ] **CONC-0008 - Coroutine Dispatcher Injection** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/**` `arcile-app/app/src/main/java/dev/qtremors/arcile/image/**` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`
  - **Problem:** Dispatchers are hardcoded across repositories, managers, data sources, image fetchers, the service, and stores.
  - **Impact:** No direct user-facing bug, but performance tuning and deterministic tests are harder.
  - **Fix:** Add `CoroutineDispatchers(io, default, main, computation)` and inject it. Use limited parallelism intentionally for transfer, stats, thumbnails, and archive work. Update tests to inject `TestDispatcher`. Recommended Refactor: Define dispatchers in DI and pass them into data/manager/service classes. Safer Alternative: Start with data layer and operation service.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **CONC-0014 - Coroutine Cancellation / Backpressure** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/FolderStatsStore.kt`
  - **Problem:** Folder stats workers are application-scoped and can continue scanning after the original screen no longer needs them. Update flow drops old updates under pressure.
  - **Impact:** Battery and I/O may be spent on folders no longer visible.
  - **Fix:** Add cancellable request tokens per screen/listing. Prioritize visible rows and cancel old path queues on navigation. Expose stats as keyed StateFlow/cache with explicit loading state. Recommended Refactor: Make folder stats a demand-driven service with `observeStats(paths)` rather than fire-and-forget queueing. Safer Alternative: Clear queue/rerun state on directory navigation and increase visible-update reliability.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Memory Tasks

- [ ] **MEM-0010 - Memory / Large Directory Handling** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/FileSystemDataSource.kt`
  - **Problem:** Directory listing loads all children into memory, maps all to `FileModel`, then sorts the full list before returning.
  - **Impact:** Huge directories can load slowly, freeze visible progress, or consume excessive memory.
  - **Fix:** Introduce paged/incremental directory listing. Emit loading batches through Flow. Add configurable sorting that can operate on chunks or defer expensive metadata. Recommended Refactor: Create `DirectoryListingDataSource.list(path): Flow<ListingPage>`. Safer Alternative: Add a max initial batch and "load more" fallback for huge folders.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Compose Engineering Tasks

- [ ] **COMPOSE-0011 - Compose Performance** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/BrowserScreen.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/StorageDashboardScreen.kt`
  - **Problem:** Several expensive sorts/groupings/transforms happen in composables or on every state-level change. Some are remembered, but state objects are broad so unrelated changes can still invalidate calculations.
  - **Impact:** Scrolling/searching/selection can feel less responsive in large datasets.
  - **Fix:** Move stable display lists/groupings into ViewModel state or dedicated memoized selectors. Use narrower immutable UI models. Add Compose compiler stability reports and macrobenchmarks. Recommended Refactor: Add `BrowserDisplayState` with already-filtered/sorted file lists and separate transient UI state from data state. Safer Alternative: Use `derivedStateOf` for scroll/progress-triggered derived values and tighten `remember` keys.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Reliability Tasks

- [ ] **REL-0015 - Mutation Atomicity / Recovery** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/FileTransferEngine.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/data/manager/TrashManager.kt`
  - **Problem:** Operations use temp and backup files but do not persist a recovery journal for cleanup or rollback across process death.
  - **Impact:** Crashes or device shutdowns can leave `.arcile-transfer-*`, `.arcile-replace-*`, archive temp files, or inconsistent trash metadata.
  - **Fix:** Persist operation stages before creating temp/backup files. Add startup recovery cleaner. Record whether source deletion has occurred. Recommended Refactor: Build a transaction-like `MutationJournal` used by transfer, trash, archive extraction, archive creation, and fake-file creation. Safer Alternative: At minimum, scan known Arcile temp filename patterns on launch and offer cleanup.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **REL-0024 - Error Handling / Reliability** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/**` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/**`
  - **Problem:** Error handling relies on generic `Exception` messages in many code paths, while some use typed exceptions. UI often shows raw messages.
  - **Impact:** Users get inconsistent, sometimes technical, sometimes vague errors.
  - **Fix:** Define sealed domain errors: access denied, storage unavailable, conflict, insufficient space, unsupported provider, partial success, cancelled, corrupted metadata, unsafe path. Include user-safe message id and recovery action. Stop surfacing arbitrary exception messages directly. Recommended Refactor: Add `ArcileError` and map platform exceptions at data boundaries. Safer Alternative: Wrap existing exceptions in typed errors incrementally for destructive operations first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Security Tasks

- [ ] **SEC-0016 - Security / Path Safety** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/util/PathSafety.kt`
  - **Problem:** Non-destructive reads allow symlinks by default, and destructive path validation only rejects symlinks along the target path before operation start.
  - **Impact:** Rare but dangerous edge cases can expose or modify files outside intended storage roots on devices/filesystems that allow symlinks.
  - **Fix:** Centralize path policies by operation type. Reject symlink traversal for all mutation and recursive traversal unless explicitly allowed. Revalidate parent and target immediately before delete/rename/promote. Recommended Refactor: Return a validated `SafePath` value from `PathSafety` and require it for destructive APIs. Safer Alternative: Set `rejectSymlinks = true` for recursive read/stat operations and all mutation paths.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **SEC-0017 - Privacy / Backup** `[High]`
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml` `arcile-app/app/src/main/res/xml/backup_rules.xml` `arcile-app/app/src/main/res/xml/data_extraction_rules.xml`
  - **Problem:** The manifest has `android:allowBackup="true"` while the app stores storage classifications, quick access entries, preferences, and encrypted trash metadata references.
  - **Impact:** Private filesystem paths or app usage metadata may be backed up or restored unexpectedly.
  - **Fix:** Audit and restrict backup rules. Exclude trash metadata, operation journals, staging caches, quick-access private paths unless explicitly intended. Add restore validation/migration. Recommended Refactor: Classify persisted data as portable settings vs local-device metadata. Safer Alternative: Disable backup until rules are verified.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **SEC-0018 - Security / File Sharing** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ExternalFileAccessHelper.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ShareHelper.kt`
  - **Problem:** Opening and sharing copies files into cache staging with a 500 MB cleanup cap, but there is no explicit user-facing cleanup control, metadata stripping, MIME grouping, or per-share size guard.
  - **Impact:** Large shares can consume cache and sensitive files can remain staged for up to 24 hours.
  - **Fix:** Add explicit cleanup action and post-share cleanup scheduling. Warn or stream for large files when possible. Preserve MIME grouping and consider metadata warnings for media/docs. Recommended Refactor: Create `ExternalHandoffManager` with size limits, cleanup policy, and audit logging. Safer Alternative: Reduce cache age and expose "clear shared-file cache" in settings.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Archive Tasks

- [ ] **ARCHIVE-0019 - Archive Safety / Scalability** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/manager/ArchiveManager.kt`
  - **Problem:** Archive creation and extraction materialize entry/file lists and do not enforce archive-bomb limits, output byte caps, entry count caps, or compression ratio guards.
  - **Impact:** Malicious or huge archives can fill storage, run for excessive time, or exhaust memory.
  - **Fix:** Add maximum entries, maximum uncompressed bytes, maximum path length, maximum nested depth, and compression ratio thresholds. Stream creation/extraction where possible. Journal extraction outputs for cleanup on failure/cancel. Recommended Refactor: Create `ArchiveSafetyPolicy` and `ArchiveOperationEngine`. Safer Alternative: Warn and require confirmation when archive metadata exceeds conservative thresholds.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### User Experience Tasks

- [ ] **UX-0020 - Operation UX / Notifications** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`
  - **Problem:** Foreground operation notification is indeterminate and static. It does not expose bytes, current file, verification phase, cancel action, or completion/failure summary.
  - **Impact:** Users cannot judge duration, verify progress in background, or cancel from notification shade.
  - **Fix:** Update notification on throttled progress. Add cancel action. Use privacy-safe current filename, bytes, and phase. Show terminal notifications only when useful. Recommended Refactor: Add `OperationNotificationController`. Safer Alternative: At least add cancel action and percent when total bytes are known.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Battery Tasks

- [ ] **BAT-0021 - Battery Efficiency** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/FileTransferEngine.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/data/FolderStatsStore.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/data/source/MediaStoreClient.kt`
  - **Problem:** The app can run simultaneous expensive I/O: transfer hashing, folder stats traversal, analytics scans, and thumbnail loading.
  - **Impact:** Battery drain and device heat during heavy browsing or transfers.
  - **Fix:** Add centralized I/O scheduler with priorities: active transfer > visible listing > visible thumbnails > stats > analytics. Pause nonessential scans during foreground mutations. Add battery/thermal-aware throttling. Recommended Refactor: Inject `StorageWorkScheduler` into managers/data sources. Safer Alternative: Suspend folder stats and analytics while `BulkFileOperationCoordinator.activeRequest != null`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### ANR / Startup Tasks

- [ ] **ANR-0022 - ANR Risk** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt`
  - **Problem:** The app has no explicit debug StrictMode profile, startup benchmark, or trace markers around first storage/permission/navigation setup.
  - **Impact:** Cold start regressions may ship unnoticed.
  - **Fix:** Add StrictMode in debug builds. Add baseline profile and macrobenchmark startup test. Add trace sections for permission check, app shell composition, initial home load, and operation recovery. Recommended Refactor: Move startup storage work behind lazy flows and measured warmup. Safer Alternative: Add debug-only StrictMode first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Build / Release Tasks

- [ ] **BUILD-0023 - Build System / Dependency Freshness** `[Medium]`
  - **Location:** `arcile-app/gradle/libs.versions.toml` `arcile-app/app/build.gradle.kts`
  - **Problem:** The build uses a single app module with no convention plugins, no feature modules, no benchmark module, and no modular dependency boundaries.
  - **Impact:** Indirect: slower iteration and higher regression risk as the app grows.
  - **Fix:** Add convention plugins. Split modules by `core:domain`, `core:storage`, `core:ui`, `feature:browser`, `feature:home`, `feature:trash`, `feature:archive`, `benchmark`. Add dependency analysis and version update checks. Recommended Refactor: Start by extracting pure Kotlin domain and storage interfaces. Safer Alternative: Keep one APK module but add Gradle convention logic and package-level architecture checks.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Testing Tasks

- [ ] **TEST-0025 - Testability / Production Verification** `[High]`
  - **Location:** `arcile-app/app/src/test/**` `arcile-app/app/src/androidTest/**`
  - **Problem:** The project has useful unit/UI tests, but lacks stress, benchmark, mutation recovery, SAF compatibility, process-death, and real large-directory tests.
  - **Impact:** Critical regressions may only appear on real devices with large storage.
  - **Fix:** Add contract tests for storage backends. Add large directory synthetic tests. Add transfer cancellation/recovery tests. Add archive safety tests. Add macrobenchmarks for startup, listing, scrolling, search, thumbnail grid. Recommended Refactor: Create test fixtures for in-memory, temp filesystem, SAF-like fake, and failure-injecting storage backends. Safer Alternative: Add stress tests for `FileTransferEngine`, `TrashManager`, and `FolderStatsCalculator` first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Compatibility Tasks

- [ ] **COMPAT-0026 - Future Android Compatibility** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/data/provider/VolumeProvider.kt`
  - **Problem:** Volume discovery mixes `StorageManager.storageVolumes`, `Environment.getExternalStorageDirectory()`, and `getExternalFilesDirs()` heuristics.
  - **Impact:** Some OEMs, work profiles, removable volumes, and USB devices may show duplicate, missing, or misclassified volumes.
  - **Fix:** Make volume discovery synchronous during provider initialization or block path validation until roots are loaded. Add volume identity normalization and profile awareness. Add SAF roots as first-class `StorageVolume` alternatives. Recommended Refactor: Separate physical volumes from logical storage locations. Safer Alternative: Ensure `activeStorageRoots` is populated from `currentVolumes()` before any path validation.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

- [ ] **COMPAT-0029 - Android Platform Compatibility** `[High]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt`
  - **Problem:** Predictive back, multi-window, foldable layout state, and permission recovery are not modeled as first-class architecture concerns.
  - **Impact:** Back behavior, split-screen, and tablet/foldable workflows may feel inconsistent as app grows.
  - **Fix:** Define navigation state and back behavior per screen. Add predictive back testing. Add adaptive layouts for compact/medium/expanded widths. Treat permission revoked/unavailable as state, not one-time startup branch. Recommended Refactor: Create feature navigation contracts and adaptive app shell. Safer Alternative: Add explicit tests for current BackHandler behavior and process-death restoration first.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Thumbnail Tasks

- [ ] **THUMB-0027 - Thumbnail / Image Loading Performance** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileList.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/lists/FileGrid.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/image/**`
  - **Problem:** Thumbnails are loaded directly from files while browsing, with custom fetchers but no global thumbnail budget, cancellation tuning, or memory policy tied to list/grid density.
  - **Impact:** Fast scrolling huge media folders can jank or trigger I/O pressure.
  - **Fix:** Add thumbnail request policy based on visible rows, density, and active operation state. Disable/pause expensive thumbnails during bulk operations. Add failure cache for corrupt files. Recommended Refactor: Introduce `ThumbnailPolicy` and `ThumbnailKey` independent from raw File path. Safer Alternative: Limit custom thumbnails to small files and known-safe types until richer policy exists.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Localization Tasks

- [ ] **I18N-0028 - Localization / UI Quality** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/**` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/operations/BulkFileOperationService.kt`
  - **Problem:** Several user-facing strings remain hardcoded in composables, helpers, and notifications.
  - **Impact:** Localization, accessibility, and consistency suffer.
  - **Fix:** Move all visible strings/content descriptions/notification text to resources. Expand `checkProductionStrings` or use lint/custom Detekt rules. Recommended Refactor: Add a UI text wrapper for domain errors and operation messages. Safer Alternative: Start with operation notifications, ArchiveViewer, Settings, dialogs, and ShareHelper chooser title.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Maintenance Tasks

- [ ] **MAINT-0030 - Maintainability / Code Organization** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/**`
  - **Problem:** The code is package-layered (`data`, `domain`, `presentation`) but not feature-modular. Feature concerns cross directories and files are growing large.
  - **Impact:** Indirect: feature velocity and regression risk will worsen.
  - **Fix:** Move toward feature packages/modules. Define public APIs per feature. Add architecture rules for dependencies. Recommended Refactor: Start with `feature:browser`, `feature:trash`, `feature:archive`, and `core:storage`. Safer Alternative: Within single module, reorganize packages under `feature/*` and `core/*`.
  - **Verification:** Run targeted implementation tests plus manual QA for the affected flow.

### Tooling Tasks

- [ ] **TOOL-0032 - Product Surface / Debug Utility** `[Low]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/components/TestToolbar.kt` `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt`
  - **Problem:** There are debug/test or unimplemented tool surfaces present in main source.
  - **Impact:** Users may see unavailable functionality or inconsistent polish.
  - **Fix:** Move debug UI into debug source set. Gate unimplemented tools behind feature flags or hide them. Recommended Refactor: Add `FeatureFlagRepository` and build-type aware flags. Safer Alternative: Remove `TestToolbar` from main source if unused.
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
- **Root Access Mode**: Offer an opt-in root shell for power users, enabling access to system directories, permission changes, and operations beyond standard Android file APIs.
- **SAF Tree Manager**: Manage persisted Storage Access Framework grants for Android/data, Android/obb, SD cards, and cloud-backed providers.
- **Android/Data Access Assistant**: Guide users through granting access to restricted app folders, with clear fallback behavior on newer Android versions.
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
- **Shell Command Shortcuts**: For root or developer mode only, allow saved shell snippets with confirmation and clear risk labeling.
- **Scheduled Cleanup**: Periodically empty trash, remove temporary files, or prompt for old downloads review.

### Sharing, Transfers & Network
- **Nearby Share / Share Sheet Polish**: Improve multi-file sharing flow, MIME grouping, and error messages for unsupported targets.
- **Local HTTP File Drop**: Temporarily host selected files or a folder over LAN with a QR code and one-tap shutdown.
- **WebDAV / SMB Client**: Browse and transfer files from NAS, routers, and desktop shares.
- **FTP / SFTP Client**: Add optional remote location support for advanced users.
- **Cloud Provider Shortcuts**: Integrate SAF-backed Google Drive, OneDrive, Dropbox, and other document providers as pinned locations.
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
- **Interactive Petal Accent Picker**: Redesign the accent color picker into an interactive flower petal UI.
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
