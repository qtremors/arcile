# Arcile - Tasks

> **Project:** Arcile
>
> **Version:** 2.0.9
>
> **Last Updated:** 2026-08-26

## Status

| Status | Count |
| :--- | ---: |
| Remaining | 15 |

The open queue contains one high-priority fix, 11 medium-priority improvements, and three low-priority polish items.

## Recommended Execution Order

Work in the following waves. Finish each wave's focused tests and manual checks before starting the next so fixes remain attributable and recoverable.

1. **Performance and quality foundations:** `PERF-0004`, `I18N-0003`, `COMPOSE-0001`
   - Preserve cached folder information during refreshes, centralize locale-aware file-size formatting, and remove lint-confirmed Compose hot-path churn.
2. **Stable presentation and cross-screen consistency:** `UI-0008`, `UI-0012`, `UI-0013`, `UI-0015`, `UI-0016`, `UI-0017`
   - Standardize feedback and viewer actions, then complete the smaller layout corrections.
3. **New capabilities:** `UI-0001`, `FEAT-0001`, `FEAT-0002`, `FEAT-0003`, `FEAT-0004`, `FEAT-0005`
   - Add preferences and cleaner classification first. Build on-device update discovery as one signed-APK scanning pipeline with separate Arcile and plugin policies. Take on adaptive layouts after the interaction and shared-UI contracts are stable.

## Incomplete Tasks

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
