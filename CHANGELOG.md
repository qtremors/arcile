# Arcile Changelog

> **Project:** Arcile
> **Version:** 1.0.4
> **Last Updated:** 2026-06-10

---

## [1.0.4] - 2026-06-10

- **Native Expressive Image Viewer**: Introduced an immersive full-screen photo viewer with pinch-to-zoom spring mechanics, elastic vertical swipe-to-dismiss gestures, bouncy visual 90-degree rotations, and integrated deletion workflows.
- **Material 3 Expressive Gallery Revamp:** Redesigned the gallery screen layout to prioritize modern, high-end design aesthetics and immersive scrolling behavior.
- **Scroll-Linked Floating Controls:** Hidden and revealed the floating gallery action controls on vertical scroll gestures, freeing up screen real estate for photos.
- **Compact Floating Pill Bars:** Split the full-width top bar into individual circular back buttons and compact right-aligned action pills (Search, Sort, and Menu) to minimize space usage.
- **Floating Dynamic Bottom Navigation:** Added a centered floating glassmorphic bottom navigation bar displaying "Photos" and "Albums" tabs with smooth active tab transitions.
- **Albums List View:** Introduced a structured albums folder grid featuring cover photo previews and metadata indicators.
- **Vertical 3D Flip Selection Bar:** Integrated a spring-animated vertical 3D card flip transition (X-axis rotation) on selection mode change that expands dynamically to fill the available width.
- **Split Selection Toolbar:** Aligned the selection actions toolbar with the browser, utilizing a `SplitButtonGroup` for primary actions (Copy, Cut, Delete, Edit) and a detached circular dropdown menu button.
- **Draggable Fast Scrollbar:** Added a custom draggable fast scrollbar that coordinates with staggered grids, uniform grids, and lists, showcasing a floating date tooltip bubble (month/year) when scrolling.
- **Fluid Album Transitions:** Integrated horizontal spring slide/fade animations for folder opening and closing (connecting cleanly with the system back press and predictive back gestures).
- **Detached Action Button Group:** Removed the full-width background container overlay behind selection action buttons, allowing the split buttons and overflow option FAB to float freely on the screen, matching the browser style.
- **Collapsible Bottom Navigation Labels:** Refined bottom navigation items to collapse labels for inactive tabs while expanding the active tab, presenting a cleaner icon-only view for inactive options.
- **Predictive Back Gesture Animations:** Replaced old back handling with `PredictiveBackHandler` to support progressive gesture progress tracking, scaling/sliding gallery sub-pages and floating toolbars dynamically on back swipe.

## [1.0.3] - 2026-06-09

- **Room Cache Foundation:** Added the first shared Room-backed cache database for storage metadata, with tables for folder stats, storage nodes, and category summaries to move Arcile away from scattered JSON cache files.
- **Persistent Folder Stats:** Reworked folder statistics caching to persist through Room while preserving the existing low-priority queueing, cancellation, invalidation, and update flow.
- **Image Catalog Backend:** Introduced a storage-domain image catalog repository and MediaStore-backed implementation that indexes Images category rows into the storage node cache.
- **Gallery Metadata Pipeline:** Extended MediaStore image queries to capture width and height, allowing the Mini Gallery to receive aspect ratios from backend metadata instead of decoding every bitmap in the ViewModel.
- **Persistent Thumbnail State:** Added Room-backed thumbnail identity and variant tracking so loaded variants and failed identities survive app restarts while Coil continues to own bitmap disk/memory caching.
- **Thumbnail Invalidation:** Connected file mutations, Settings cache clearing, browser thumbnails, and Mini Gallery thumbnail requests to the persistent thumbnail state layer so stale thumbnails are ignored when files change.
- **Cached Directory Listings:** Persisted filesystem directory rows in Room so repeat folder opens can render from the storage node cache, with mutation invalidation for parent listings and changed paths.
- **Cached Category Summaries:** Moved category size summaries from JSON sidecar files into Room and kept the existing scoped TTL refresh behavior.
- **External Storage Invalidation:** Added a MediaStore observer that clears cached listing/category metadata and notifies storage screens after external file changes.
- **Gallery Cache Hits:** Stopped cached gallery snapshots from immediately force-refreshing, switched gallery tiles to cell-sized cached image requests, and replaced heavier subcomposed image loading in the gallery with plain cached image rendering.
- **Storage Usage Cache:** Added scanner-level storage usage caching with mutation invalidation so reopening the dashboard can reuse the previous scan instead of walking storage from zero.
- **Storage Bar Stability:** Prevented home and dashboard storage bars from drawing a temporary single-color used segment before real category data is available, and clamped segment totals so they cannot exceed actual used storage.
- **Backend Wiring:** Added Hilt providers and Room dependencies so the new cache layer can be reused by category, listing, gallery, and thumbnail refresh work.

## [1.0.2] - 2026-06-09

- **Mini Gallery Transition:** Converted the Images category into a fully immersive Mini Gallery featuring edge-to-edge full-screen scrolling, borderless grid rendering, and floating controls that dynamically adapt to the selection and search state. Restored original card-based details layout showing filename, size, and modified date/time.
- **Staggered Aspect Ratio Layout:** Added a staggered grid view option (Microsoft Photos style) that preserves natural image aspect ratios, decoded asynchronously in the background to prevent layout shifts.
- **Chronological Grouping:** Introduced timeline grouping options to section images by time ("Today", "This Week", "This Month", and "Older").
- **Unified Selection & Clipboard:** Integrated the gallery with global clipboard state and browser-aligned selection actions (Copy, Cut, Rename, Delete, Share, Properties, Compress ZIP), enabling items to be copied/cut from the gallery and pasted anywhere in the main file browser.
- **Material 3 Expressive Polish:** Added spring-scale animations on selection, custom floating pill bars, and unified haptic feedback (using `rememberArcileHaptics`) to provide a highly tactile and premium Material 3 experience.

## [1.0.1] - 2026-06-08

### Images Mini-App & Thumbnails
- **Independent Images Category:** Detached the Images category from the browser category screen into a dedicated MediaStore-backed Images mini-app while keeping the existing Arcile list/grid presentation, search, sort/view bottom sheet, selection, share, delete, and properties flows.
- **Thumbnail Reliability:** Added shared thumbnail load-state tracking with identity and size-bucket keys, density-aware request sizing, and safer in-flight behavior so thumbnails do not disappear after recomposition or scrolling.
- **Album Scroll Isolation:** Prevented album/filter tabs in the Images screen from sharing the same scroll offset when switching between them.
- **Cache Controls:** Added Settings controls to inspect and clear thumbnail cache state.

---

## [1.0.0] - 2026-06-07

Arcile v1.0.0 is the first stable release and the first release outside the beta channel. It consolidates the unreleased v0.8.x-v0.9.9 work, final stable hardening, and the beta-era feature set into a production-ready Android 11+ file manager.

### Stable Release Hardening
- **Stable Release Promotion:** Updated app metadata to `versionName` `1.0.0` and `versionCode` `100`, synchronized release-facing docs, and preserved the public release artifact name as `Arcile-1.0.0.apk`.
- **Release Build Readiness:** Verified the stable build path with project checks, release lint, and release APK assembly.
- **Android Platform Alignment:** Targeted the current Android SDK used by the project while keeping the app scoped around Android 11+ storage behavior.
- **Production String Guardrails:** Expanded production string checks through build logic so release builds avoid raw internal/debug text on user-facing surfaces.

### Storage, Volumes & Home Dashboard
- **Instant USB/SD Detection:** Improved volume observation so inserted and ejected storage volumes update the Home screen immediately instead of waiting for a delayed refresh.
- **Storage Volume Callback Support:** Added platform storage-volume callbacks on Android versions that support them, while keeping media mount/eject broadcasts as a compatibility path.
- **Indexed vs Temporary Storage Rules:** Kept SD cards as indexed permanent storage and USB/OTG drives as temporary read/write storage excluded from global category indexing and Trash.
- **Category-Only Segmented Bars:** Preserved segmented storage colors for indexed category breakdowns while keeping USB/OTG capacity bars plain, so inserting USB storage no longer flattens the internal storage category bar.
- **Home Recent Carousel Preference:** Connected the Settings carousel limit to the Home screen, made `0` hide the carousel, restored it when the value is raised again, and changed the default Home carousel count to `20`.
- **Home Tools & Quick Access Polish:** Kept Home utilities focused on implemented tools, improved quick-access behavior, and tightened dashboard refresh behavior around mounted volumes.

### Archive Workflows & Safety
- **Browser-Native Archive Views:** ZIP and 7z archives open as read-only virtual folders with navigation, search, sorting, selection, extraction, and file details without polluting normal filesystem indexes.
- **Expanded Archive Formats:** Added TAR-family and single-stream compression handling for supported create/list/extract flows, alongside ZIP and 7z support.
- **Safer Extraction:** Hardened extraction with destination presets, password retries, conflict handling, bounds checks, rollback for failed replacements, and safe path validation.
- **Archive Thumbnail Safety:** Bounded archive-entry thumbnail decoding with byte caps, bounds-first image checks, pixel limits, sampled decoding, and safe icon fallback for unsafe or unsupported entries.
- **Sequential Archive Operations:** Processed multi-extraction and multi-archive work in controlled queues, with deferred deletion only after successful completion.

### Share, Open & Privacy
- **Save to Arcile Imports:** Added Android share-sheet support for saving incoming single or multiple files into a selected Arcile destination.
- **Hostile Share Preflight:** Validated incoming `content://` and app-owned `file://` sources, enforced item and byte limits, normalized filenames, checked destination space, counted streaming writes, and reported partial failures clearly.
- **Direct Content Grants:** Opened user files through direct `content://` read grants where possible instead of staging large documents unnecessarily.
- **Sandboxed Sharing:** Restricted FileProvider exposure to controlled staging areas and maintained the app's no-internet, local-first privacy posture.

### Operations, Recovery & File Safety
- **Foreground Operation Pipeline:** Copy, move, trash, delete, archive, and extract operations run through foreground operation plumbing with progress, cancellation, and notification context.
- **Interrupted Operation Recovery:** Preserved interrupted queued, running, and cancelling work as recovery records with retry, cleanup, and dismiss paths.
- **Partial Delete Reporting:** Permanent delete and shred batches now track succeeded, skipped, failed, and cleanup-required paths, surfacing partial destructive failures instead of hiding them.
- **Transactional Mutation Safety:** Strengthened copy/move, archive replacement, extraction, and Trash flows with validation, staging, rollback, and cleanup behavior.
- **Smart Paste Conflicts:** Added user-facing conflict handling for replace, merge, keep both, and skip, with safer folder merging and clean numbered renames.

### UI, Theming & User Experience
- **Material 3 Expressive System:** Stabilized dynamic themes, custom palettes, Tokyo Night and Dracula presets, expressive shapes, tactile press feedback, and spring-based navigation transitions.
- **Recent Files Experience:** Added expressive carousel previews, chronological Recent Files lists, date grouping, audio/video/PDF/APK thumbnail improvements, and bounded thumbnail sizing.
- **Storage Cleaner:** Added scan flows for large files, old downloads, obsolete APKs, videos, duplicate candidates, and conservative junk/cache groups, with Trash-safe cleanup for indexed storage.
- **Storage Usage Map:** Added a bounded Filelight-style radial usage map with breadcrumb drill-in, selected item details, and direct browser navigation.
- **Accessibility & Haptics:** Improved TalkBack semantics, selection announcements, content descriptions, touch targets, haptic feedback, reduced-motion handling, double-line filenames, and marquee controls.
- **Loading & Layout Polish:** Added shimmer states, smoother category/grid movement, stable directory paging, better snackbar placement, and reduced stale UI feedback across navigation.

### Architecture, Performance & Testing
- **Multi-Module Gradle Architecture:** Split the app into core runtime, UI, storage, operation, navigation, testing, and feature modules with stricter Hilt composition and boundary checks.
- **Storage API Hardening:** Introduced stronger domain contracts for volumes, storage scopes, file models, listing pages, mutation notifications, and typed storage node identifiers.
- **Paged & Stable Listings:** Sorted full directory listings before paging and moved reusable row models out of hot Compose paths to reduce recomposition and scrolling work.
- **Bounded Scanner Work:** Capped folder stats, storage usage, archive prescans, thumbnails, and cleanup scans to avoid runaway traversal on very large trees.
- **Regression Coverage:** Expanded tests across browser navigation, archives, share imports, storage classification, recent files, carousel limits, operation recovery, delete decisions, thumbnails, architecture boundaries, preferences, and release build guards.

---

## Beta Recap: v0.1.0 - v0.9.9

The beta channel turned Arcile from a basic local file browser into a full Android storage manager.

### v0.1.x - Prototype Foundation
- Introduced file browsing, breadcrumbs, multi-select, create/delete basics, Home dashboard, category shortcuts, recent files, settings, Material You theming, and Android storage permission handling.
- Added early safety and polish including delete confirmation, FileProvider open support, rename UI, path traversal checks, theme persistence, search/sort controls, grid view, and category storage bars.

### v0.2.x - Material 3 & Core Operations
- Moved the app toward Material 3 Expressive with updated list items, animated UI components, bottom navigation, category storage visualization, and 120Hz display support.
- Added core copy/cut/paste/share flows, Trash routing, Recent Files "See All", MediaStore-backed search/categories, file thumbnails, filename validation, and tighter FileProvider exposure.

### v0.3.x - Trash, Search & Multi-Volume Direction
- Expanded Trash behavior, scoped search filters, storage dashboard entry points, smart selection, persistent sorting, process-death state handling, and smart paste conflict resolution.
- Began serious external storage work with reactive volume discovery, storage classification policies, SD/USB handling, and refreshed About/storage-management surfaces.

### v0.4.x - SD/USB Beta, Polish & Architecture Cleanup
- Delivered the first major external storage beta: SD cards as permanent storage, USB/OTG as temporary storage, per-volume Trash, classification prompts, and storage management.
- Added dynamic color generation, smoother grids and empty states, improved dashboard caching, type-safe routes, localization groundwork, cache cleanup, and early clean-architecture refactors.

### v0.5.x - Security, API Floor & Maintainability
- Raised the platform baseline to Android 11, hardened FileProvider and open/share paths, blocked sensitive metadata exposure, improved release signing behavior, and expanded R8/ProGuard readiness.
- Added typed errors, broader string extraction, shared UI spacing, pull-to-refresh infrastructure, folder properties, cached folder stats, dynamic quick access, and a stronger JVM/Compose test base.

### v0.6.x - Properties, Quick Access, Archives & Onboarding
- Added richer file/folder properties, Quick Access improvements, operation feedback, premium progress UI, randomized fake-file tooling, archive UX foundations, Recent Files refinements, onboarding, and permission setup.
- Continued reliability work across trash, bulk operations, media previews, swipe navigation, folder tabs, and test stability.

### v0.7.x - Public Beta Polish, Storage Tools & Safety
- Added onboarding, ZIP/7z archive tools, richer Recent Files, radial storage usage, Storage Cleaner, safer destructive dialogs, foreground operations, path safety, transfer verification, backup privacy, and accessibility/haptic improvements.
- Improved thumbnails, localization, storage utilities, search filters, back navigation, list performance, and cleaner/dashboards ahead of the v0.8.0 public beta.

### v0.8.x - Modularization & Production Hardening
- Rebuilt major architecture into feature and core modules, added stronger boundaries, shared UI modules, focused test fixtures, paged listings, immutable UI models, and stricter storage contracts.
- Continued storage, browser, cleaner, archive, Trash, and UI hardening that formed the base of the stable v1.0.0 release.

### v0.9.x - Unreleased Stable Candidate Work
- Added browser-native archive browsing, selected archive extraction, expanded archive formats, sequential archive workflows, operation recovery, safer open/share handling, custom themes, progress details, utility preferences, configurable Home recents, and thumbnail policy centralization.
- Completed major release hardening for imports, archive thumbnails, stale navigation cancellation, partial destructive operation reporting, MediaStore content URI resilience, transactional extraction replacement, stable directory paging, and release metadata.

Detailed beta changelog history is archived in [beta/CHANGELOG-BETA.md](beta/CHANGELOG-BETA.md).
