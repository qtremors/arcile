# Arcile - Tasks

> **Project:** Arcile
>
> **Version:** 2.0.7
>
> **Last Updated:** 2026-08-24

## Status

| Status | Count |
| :--- | ---: |
| Remaining | 17 |
| Completed | 32 |
| Total | 49 |

The open queue contains three high-priority fixes, 11 medium-priority improvements, and three low-priority polish items.

## Recommended Execution Order

Work in the following waves. Finish each wave's focused tests and manual checks before starting the next so fixes remain attributable and recoverable.

1. **Release safety and validation:** Complete
   - Vault privacy, live unlock-state handling, foreground timeout handling, notification cleanup, transactional preference restore, deterministic app tests, and shared-import space reservation are complete.
2. **App-wide gesture arbitration:** `UI-0011`
   - Extend the repaired fast-scroll, selection, search, and mini-player gesture behavior into one shared touch-slop, direction-lock, velocity, and cancellation policy across representative app surfaces.
3. **Shared architecture and performance foundations:** `ARCH-0001`, `I18N-0003`, `COMPOSE-0001`
   - Split the oversized shared category and viewer surfaces before adding more cross-screen action plumbing. Keep the architecture and lint gates green throughout the refactor.
4. **Stable presentation and cross-screen consistency:** `PERF-0004`, `UI-0008`, `UI-0012`, `UI-0013`, `UI-0015`, `UI-0016`, `UI-0017`
   - Preserve cached information during refreshes, standardize feedback and viewer actions, then complete the smaller layout corrections.
5. **New capabilities:** `UI-0001`, `FEAT-0001`, `FEAT-0002`, `FEAT-0003`, `FEAT-0004`, `FEAT-0005`
   - Add preferences and cleaner classification first. Build on-device update discovery as one signed-APK scanning pipeline with separate Arcile and plugin policies. Take on adaptive layouts after the interaction and shared-UI contracts are stable.

## Completed in 2.0.7

- [x] **SEC-0004 - Restore Vault Screen Protection** `[Critical]` `[Security / Privacy]`
  - Screenshot protection now has shared ownership across the vault browser and vault-launched video viewers, so navigation cannot clear the secure flag while sensitive content remains visible. Focused ownership, playback-session, OnlyFiles, and video-player tests pass.

- [x] **UI-0005 - Suppress Stale OnlyFiles Unlock Toasts** `[Medium]` `[UI / UX]`
  - Vault opening and biometric unlock now resolve against live session IDs, while stale pending prompts are dismissed when the vault is already selected or unlocked. Focused OnlyFiles tests pass.

- [x] **REL-0008 - Clear Completed Operation Notifications** `[High]` `[Reliability / Notifications]`
  - Bulk operations and vault imports now remove owned notifications from every terminal, rejected-start, timeout, destruction, and stale-process path. Focused service tests cover completion, cancellation, concurrent imports, timeout, and late progress rejection.

- [x] **REL-0005 - Handle `dataSync` Foreground-Service Timeouts** `[High]` `[Reliability]`
  - Both long-running services now implement the Android 15 timeout callback, cancel active work, publish one actionable failed state, and stop foreground execution within the callback. Arcile's operations are local filesystem and content-provider work, so they do not qualify as network-focused user-initiated data-transfer jobs. API 35 Robolectric service tests cover timeout behavior.

- [x] **REL-0006 - Make Preferences Import Bounded and Transactional** `[High]` `[Reliability]`
  - Settings backups now cap the envelope, each store, and total decoded data; reject duplicate, unknown, oversized, and corrupt inputs before changing settings; restore through live DataStores; reset missing stores; and roll back applied stores after a commit failure or cancellation. Focused tests cover live round trips, legacy resets, validation failures, and injected failures at every commit step.

- [x] **TEST-0001 - Stabilize the Robolectric and Compose App Test Runtime** `[High]` `[Testing / Release]`
  - JVM tests now use a startup-free test application and Robolectric's non-native SQLite backend, while the one Hilt application integration test cancels its application scope explicitly. Runtime, preference, Compose, share-helper, and Hilt integration tests pass together, followed by a clean 131-test app suite with no native-loader, SQLite, leaked-coroutine, or before-test cascade.

- [x] **STORAGE-0002 - Reserve Space for Shared-File Imports** `[Medium]` `[Data / Storage / Platform]`
  - Shared imports now check allocatable bytes for the destination volume, request cache reclamation through `StorageManager`, retain a bounded allowance for unknown sizes, stage the full batch, and recheck safety headroom before committing. Allocation, ENOSPC, full-volume, and concurrent-consumption failures return the insufficient-space recovery path while deleting staged data and preserving existing files. Focused importer and foreground-service tests pass.

- [x] **UI-0003 - Repair Thumbnail-Strip Fast Scrolling** `[High]` `[UI / UX]`
  - Thumbnail scrubbing now maps the held pointer directly to a bounded list position, keeps control when the starting item leaves composition, and preserves ordinary taps and scrolling before the hold activates. Focused horizontal, vertical, bounds, and empty-state tests pass.

- [x] **UI-0004 - Restore Gallery Multi-Selection Actions** `[High]` `[UI / UX]`
  - Gallery selection now exposes Rename for multiple files and routes it through the existing transactional batch-rename operation while preserving the selection action set. Focused controller tests cover the batch request, successful selection clearing, and one refresh.

- [x] **UI-0006 - Repair Selection on Recents** `[High]` `[UI / UX]`
  - Grouped list and grid layouts now share a stable path-based selection anchor, bidirectional range selection, and selection haptics. Focused Recents state, screen, and range tests pass.

- [x] **UI-0007 - Keep Recents Newest-First Without Sort Controls** `[Medium]` `[UI / UX]`
  - Recents now always uses newest-first ordering, migrates older saved sort choices, and exposes layout, density, and thumbnail options without a sort control. Focused migration, ViewModel, and screen tests pass.

- [x] **REL-0007 - Support Case-Only Renames** `[High]` `[Reliability / Files]`
  - Case-only file and folder renames now use a verified temporary-name transition with rollback instead of trusting a direct filesystem rename. Existing rename, extension, and conflict tests pass.

- [x] **UI-0009 - Stop the Mini-Player Blocking Navigation and Text Input** `[High]` `[UI / UX]`
  - The collapsed player window is now non-focusable and non-touch-modal, yields focus and system navigation outside its bounds, and stays below the keyboard. Focused window-policy tests pass.

- [x] **UI-0010 - Make Mini-Player Swipe-Down Dismissal Reliable** `[High]` `[UI / UX]`
  - Mini-player dismissal now settles once from drag distance or velocity, waits for the gesture terminal event, and recovers cleanly from cancellation. Focused presentation tests cover slow drags, fast flings, cancellation, and window state.

- [x] **REL-0009 - Persist the Sort-Subfolders Setting Correctly** `[High]` `[Reliability / Settings]`
  - Browser sort scope now initializes from the exact stored folder-tree preference, updates live after applying, and does not misrepresent inherited parent settings as direct scope. Focused preference and navigation tests pass.

- [x] **UI-0014 - Show a Close Icon in Search Bars** `[Low]` `[UI / UX]`
  - Shared and PDF search headers now use a close icon with the existing close-search accessibility action instead of a back arrow.

- [x] **REL-0010 - Make Smart Select Work on Search Results** `[High]` `[Reliability / Search]`
  - Browser search results now use the shared selectable list and grid components, preserving long-press entry, tap toggles, visible-result range selection, layout choice, and fast scrolling. Focused Browser screen tests pass.

## Newly Triaged Work

- [ ] **PERF-0004 - Preserve Folder Subtitle Metadata While Refreshing** `[High]` `[Performance / UI]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/FolderStatsStore.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/delegate/BrowserDirectoryNavigation.kt`; shared file-row subtitle UI
  - **Reported behavior:** Folder subtitles repeatedly show a calculating state. Display the last known metadata immediately and refresh it without replacing stable UI with a conspicuous loading label.
  - **Fix:** Treat persisted stale stats as displayable stale-while-revalidate data, load them for every supported browser scope, retain them while refresh is queued, and show a loading label only when no prior value exists. Invalidate only paths affected by successful mutations.
  - **Verification:** Repository and ViewModel tests cover cold start from the database, expired cache refresh, navigation away and back, mutation invalidation, unavailable folders, and no subtitle flicker.

- [ ] **UI-0008 - Refine Toast Shape and Action Layout** `[Medium]` `[UI / UX]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/ArcileSnackbarHost.kt`; remaining `showArcileToast` call sites
  - **Reported behavior:** Toasts have oversized bezels; actions should use a separate attached container similar to the app's split-button treatment.
  - **Fix:** Tighten feedback padding and icon treatment, render the message and optional action as attached sibling surfaces, and migrate in-app actionable feedback to the shared snackbar host. Keep platform toasts only where no Compose host exists.
  - **Verification:** Screenshot tests cover short and long messages, all severities, actions, large font, narrow screens, RTL, and swipe dismissal.

- [ ] **UI-0011 - Disambiguate Swipes and Taps Across the App** `[High]` `[UI / UX]`
  - **Location:** shared click and gesture modifiers; file list and grid selection; fast scrollbars; Browser workspace swipes; Gallery and viewer gestures; mini-player gestures
  - **Reported behavior:** Across the app, input handling can confuse a swipe with a tap.
  - **Fix:** Define one touch-slop, direction-lock, velocity, and cancellation policy for shared gesture surfaces. Delay tap activation until drag intent is ruled out, avoid consuming pointer changes before ownership is established, and keep nested child controls independent.
  - **Verification:** Shared gesture tests distinguish taps, slight movement, vertical and horizontal swipes, diagonal movement, long press, nested controls, multi-touch cancellation, and system-edge gestures across representative app surfaces.

- [ ] **FEAT-0001 - Add a Browser Folders-First Toggle** `[Medium]` `[Feature / Browser]`
  - **Location:** browser presentation preferences; `arcile-app/core/presentation/src/main/java/dev/qtremors/arcile/core/presentation/FilePresentation.kt`; sort UI
  - **Requested behavior:** Add a browser option that groups folders before files.
  - **Fix:** Add a persisted `foldersFirst` presentation preference with global and path scope, apply it consistently to every sort mode, and expose it next to browser sort options. Preserve current behavior as the migration default.
  - **Verification:** Sorting tests cover every sort mode with the toggle on and off, inherited folder preferences, search, archives, and applicable category screens.

- [ ] **FEAT-0002 - Show App Icons for Matching App Folders** `[Low]` `[Feature / Browser]`
  - **Location:** shared file-row icon presentation; browser-visible folder metadata provider
  - **Requested behavior:** Show the app icon when a folder matches an installed package or app name.
  - **Fix:** Build a cached package-name and normalized app-label index, resolve only visible folders off the main thread, and fall back to the folder icon for ambiguous or unavailable matches. Do not scan folder contents to infer an app.
  - **Verification:** Tests cover exact package names, case-insensitive app labels, ambiguous labels, package changes, work profiles, missing query visibility, and scroll performance.

- [ ] **UI-0012 - Provide File-Viewer Action Parity** `[Medium]` `[UI / UX]`
  - **Location:** image, video, PDF, audio, archive, and OnlyFiles viewer chrome; shared category and browser file-action controllers
  - **Reported behavior:** Actions available for a selected file in the browser are not consistently available after opening the same file in a viewer.
  - **Fix:** Introduce a shared capability-based viewer action contract and pass the same file-operation handlers used by the source surface. Preserve read-only, trash, provider, archive-entry, and vault security restrictions.
  - **Verification:** Contract tests assert action parity for each capability set; viewer UI tests cover rename, copy, cut, delete, share, open with, archive, properties, and secure or read-only exclusions.

- [ ] **UI-0013 - Improve Conflict-Resolution File Cards** `[Medium]` `[UI / UX]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/ConflictCard.kt`; `PasteConflictDialog.kt`
  - **Reported behavior:** Conflict resolution lacks well-spaced, dynamically sized cards for complete file information and does not show thumbnails.
  - **Fix:** Use adaptive stacked or side-by-side layouts based on available width, show complete names and useful metadata without fixed-height clipping, and load both incoming and existing thumbnails through the shared thumbnail request pipeline.
  - **Verification:** Screenshot tests cover narrow and wide windows, long names, files, folders, images, video, PDF, missing thumbnails, large font, and RTL.

- [ ] **FEAT-0003 - Add a Filename-Version Cleaner** `[Medium]` `[Feature / Cleaner]`
  - **Location:** cleaner domain group types; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageCleanerScanner.kt`; cleaner group UI
  - **Requested behavior:** Detect and group version-like filename variants, such as `arcile-1.apk` and `arcile-2.apk`, so users can review and clean redundant versions.
  - **Fix:** Add a review-only version-family group based on conservative filename normalization, extension equality, and sibling or package metadata. Rank likely newest versions without preselecting deletion, and avoid treating ordinary numbered photos or documents as versions unless confidence is high.
  - **Verification:** Tests cover semantic versions, copy suffixes, dates, APK package metadata, unrelated numbered names, mixed extensions, localization, and manual keep or delete choices.

- [ ] **UI-0015 - Keep Grid Slider Labels and Steps Consistent** `[Medium]` `[UI / UX]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/SortOptionDialog.kt`; category presentation controls in `FileCategoryLibrary.kt`
  - **Reported behavior:** Some screens show a grid value of four even though the slider exposes only three positions.
  - **Fix:** Define one discrete column-count model per window width, derive cell size from the selected count, and use matching labels, tick counts, ranges, and persistence on browser and category screens.
  - **Verification:** UI tests cover every slider position at compact, medium, and expanded widths and assert that the rendered grid count matches the displayed value.

- [ ] **UI-0016 - Let PDF Thumbnails Fill Their Bounds** `[Low]` `[UI / UX]`
  - **Location:** PDF thumbnail presentation in shared file items, Documents, Recents, Home, Trash, and cleaner surfaces
  - **Reported behavior:** PDF thumbnails leave unnecessary empty space at the sides instead of filling the available thumbnail bounds.
  - **Fix:** Keep the renderer aspect-correct, but crop or clip the rendered page at the presentation layer for card thumbnails. Preserve `Fit` only in full-document previews where the whole page must remain visible.
  - **Verification:** Screenshot tests cover portrait, landscape, and square first pages in list, grid, and document cards without stretching.

- [ ] **UI-0017 - Remove Redundant Document-Type Labels** `[Low]` `[UI / UX]`
  - **Location:** `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`
  - **Reported behavior:** Document-category items show both a type badge and duplicate type text. Keep the badge and remove the redundant bottom text.
  - **Fix:** In grid mode, retain the type badge and show only size and modified time in supporting text. Keep a readable type indication in list mode if no badge is present.
  - **Verification:** Screenshot tests cover grid and list modes, unknown extensions, hidden details, long names, and accessibility descriptions.

- [ ] **FEAT-0004 - Discover and Install On-Device Plugin APK Updates** `[Medium]` `[Feature / Updates]`
  - **Location:** APK catalog and parser; plugin manager; package installer flow; Plugins screen
  - **Requested behavior:** Automatically discover plugin APKs already present on the device and offer a safe installation flow.
  - **Fix:** Scan indexed on-device APK metadata for known plugin package IDs, require the Arcile signing certificate and compatible plugin API, choose only a higher version code, deduplicate candidates, and ask for explicit confirmation before handing the APK to `PackageInstaller`.
  - **Verification:** Tests cover valid upgrades, wrong signatures, downgrades, incompatible API versions, duplicate candidates, inaccessible files, split APKs, and post-install refresh.

- [ ] **FEAT-0005 - Discover On-Device Arcile APK Updates** `[Medium]` `[Feature / Updates]`
  - **Location:** APK catalog and parser; app startup/update coordinator; existing package installer dialog and engine
  - **Requested behavior:** Automatically discover newer Arcile APKs already present on the device and prompt the user to update.
  - **Fix:** Reuse the signed on-device APK discovery pipeline, match the current application ID and signing certificate, require a higher version code, rate-limit prompts per candidate, and let users dismiss or install through the existing confirmation flow. Do not silently install or fetch over the network.
  - **Verification:** Tests cover release and debug application IDs, valid updates, equal versions, downgrades, signature mismatch, repeated launches, dismissed candidates, and successful installation handoff.

## Remaining Work

### High Priority

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
