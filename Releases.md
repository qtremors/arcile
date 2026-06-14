# Arcile - Releases

> **Project:** Arcile
> **Version:** 1.1.0
> **Last Updated:** 2026-06-14

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v1.1.0](#v110) | 2026-06-14 | Storage Cleaner enhancements, Room-backed cache database, and immersive Media Viewer |
| [v1.0.0](#v100) | 2026-06-07 | First Stable Release - v0.8.0 through v0.9.9 plus final stable hardening |

---

# v1.1.0

**Release Date:** June 14, 2026

**Previous public release:** v1.0.0

**Candidate range included:** v1.0.1 through v1.1.0

Arcile v1.1.0 is the first major post-v1.0 stable release, consolidating the v1.0.x series of updates. It brings key enhancements to the Storage Cleaner, a new Room-backed cache database for improved storage performance, and a completely revamped, gesture-driven Gallery and Media Viewer experience.

## What's New Since v1.0.0

### Storage Cleaner & Verification
- **Exact Duplicate Detection**: Replaced filename-only matching with SHA-256 validation, size narrowing, and sampled-byte verification to ensure duplicate groups represent identical file contents even if names differ.
- **Custom Cleaner Rules**: Added persisted storage cleaner rules with per-section control, name/path ignore lists, large file/old download thresholds, and restore management for ignored items.
- **Vertical Duplicate Comparison**: Redesigned duplicate comparison to stack files vertically inside a scrollable column with header details, location boxes, selection footers, and a sticky "Delete selected" action button.
- **Cleaner UI & Badges**: Aligned preview thumbnails and metadata, removed redundant buttons, and made thumbnail clicks open the file while path clicks open the folder. Offset app badges slightly with background container borders.
- **Cleaner Hub & Categories**: Moved thumbnail cache controls into Storage Cleaner, and added dedicated cleanup categories for "Marker files" and "Empty folders" alongside the renamed "Duplicate files" section.

### Gallery & Immersive Media Viewer
- **Immersive Media Viewer**: Adapted a clean media viewer layout featuring a circular Back button, a date/time info pill, a bottom scrolling thumbnail strip, and a 3-dot overflow options menu. Increased touch target sizes.
- **Gesture-Driven Interaction**: Consolidated touch listeners to support zoom-responsive panning with boundary resistance, double-tap zoom targeting, and elastic drag-to-dismiss transitions with backdrop fading.
- **EXIF Metadata Display & Scrubbing**: Integrated a full-screen EXIF bottom sheet showing camera parameters, paths, and dates, with a secure metadata scrubbing action to strip location and camera attributes.
- **Pinch-to-Resize Grids**: Enabled real-time interactive multi-touch pinch-to-resize column scaling directly on Photos and Albums grids.
- **Favorites & Custom Album Covers**: Bookmarked items are automatically grouped in a virtual "Favorites" album using the latest favorited item as the cover. Allowed designating any image in an album as its cover.
- **Timeline Date Grouping**: Formatted date headers as wrapped chips and supported timeline grouping by Day, Week, Month, or None.
- **Gallery Navigation & Swipe**: Enabled horizontal swipe gestures between Photos and Albums, preserved scroll positions, and passed surrounding context so users can swipe nearby images from any source screen.

### Storage Performance & Room Cache
- **Room Cache Database**: Replaced legacy JSON cache files with a shared Room-backed database for storage metadata, folder stats, storage nodes, and category summaries.
- **Persistent Stats & Listings**: Cached filesystem directory listings and folder statistics in Room for instant folder loading, with mutation invalidation to ensure freshness.
- **Persistent Thumbnail State**: Room-backed tracking for thumbnail identity and variant states to survive app restarts.
- **Storage Usage Cache**: Cached storage usage scans with mutation invalidation to reuse the previous scan when reopening the dashboard.
- **Decimal Storage Units**: Switched all displayed file sizes to decimal units (KB/MB/GB/TB) to align with Android Settings.

### UI & Animations
- **Expressive Spring Animations**: Customized standard spring animations to use under-damped bouncy physics and integrated volumetric slide-and-scale navigation transitions with tactile haptic clicks.
- **Predictive Back Navigation**: Fully integrated progressive predictive back gestures across core screens (Browser, Gallery, Recents, Trash, Archive Viewer), dynamically scaling layouts in real-time response to back swipes.
- **Shimmering Storage Bar**: Added a dynamic shimmering used-space loading bar that populates automatically on app start and morphs smoothly when storage details resolve.

---

# v1.0.0

**Release Date:** June 7, 2026

**Previous public release:** v0.8.0 Beta

**Beta candidate range included:** v0.8.1 through v0.9.9

Arcile v1.0.0 is the first stable release and the first release outside the beta channel. Because v0.9.0 through v0.9.9 were not released as separate public builds, this stable release includes the full post-v0.8.0 beta candidate cycle plus the final stable fixes and release verification.

## What's New Since v0.8.0 Beta

### Stable Storage Behavior
- Internal storage and SD cards are treated as indexed permanent storage, while USB/OTG drives remain temporary read/write storage that is excluded from global categories and Trash.
- USB and SD insertion/ejection now updates the Home screen immediately through faster volume observation and platform storage callbacks where available.
- Category-segmented storage bars remain limited to indexed category data. USB/OTG capacity bars stay plain, and inserting USB storage no longer collapses internal storage categories into a single-color bar.

### Home, Recents & Utilities
- The Home recent-files carousel is configurable from Settings, defaults to `20` items, hides completely at `0`, and reappears when the limit is raised again.
- Home utilities now focus on implemented tools, with cleaner preference handling and fewer duplicate or placeholder entries.
- Recent Files received expressive carousel previews, better thumbnail sizing, audio album-art support, list/grid refinements, and chronological grouping improvements.

### Archives
- Archives now open directly in the Browser as read-only virtual folders with navigation, search, sorting, selection, details, and targeted extraction.
- ZIP, 7z, TAR-family archives, and supported single-stream compression formats have safer create/list/extract flows.
- Extraction gained password retries, conflict handling, rollback for failed replacements, safer path validation, bounded prescans, and deferred source deletion only after successful completion.
- Archive-entry thumbnails are bounded and decoded defensively to avoid oversized or corrupt image failures.

### Share, Open & Privacy
- "Save to Arcile" supports Android share-sheet imports for one or many files, with destination picking and keep-both handling.
- Incoming shares are preflighted for allowed schemes, item count, total bytes, filename safety, destination space, counted streaming, and partial-failure reporting.
- Open/share handoffs prefer direct `content://` grants for user files and keep FileProvider exposure restricted to controlled staging paths.
- Arcile remains local-first and does not request internet permission.

### Operations & Recovery
- Copy, move, trash, delete, archive, and extract operations use foreground operation infrastructure with progress, cancellation, and notification context.
- Interrupted work is preserved as recovery records with retry, cleanup, and dismiss actions.
- Permanent delete and shred flows now report partial destructive failures clearly, including succeeded, skipped, failed, and cleanup-required paths.
- Smart paste conflict dialogs support replace, merge, keep both, and skip, with safer folder merging and clean numbered renames.

### Architecture & Performance
- The codebase was split into core and feature Gradle modules for UI, storage, operations, navigation, runtime, testing, browser, trash, archives, recent files, quick access, onboarding, storage cleaner, and storage usage.
- Storage APIs now use stronger domain contracts for volumes, scopes, files, listing pages, storage nodes, mutation notifications, and preferences.
- Large directories, folder stats, storage usage scans, thumbnails, archive scans, and browser listings are bounded or paged to reduce stalls.
- UI row models, immutable state, and thumbnail policies were centralized to reduce recomposition and repeated work.

### UI, Accessibility & Theming
- Material 3 Expressive theming was stabilized with dynamic colors, custom HEX palettes, Tokyo Night and Dracula presets, expressive shapes, and spring-based transitions.
- TalkBack semantics, touch targets, haptics, reduced-motion behavior, double-line filenames, marquee filenames, loading shimmer states, and snackbar behavior were polished.
- Storage Cleaner, Storage Usage Map, Trash, Archive, Settings, Home, Browser, and Recent Files all received visual and interaction refinements.

### Release Verification
- Release metadata was synchronized to `versionName` `1.0.0` and `versionCode` `100`.
- Stable APK output is named `Arcile-1.0.0.apk`.
- The release was verified with project checks, release lint, and release assembly.

---

## Beta Recap: v0.2.0 - v0.8.0

The stable release builds on the public beta milestones below. Full beta release notes remain archived in [beta/RELEASES-BETA.md](beta/RELEASES-BETA.md).

### v0.2.0 Beta - Material 3 Redesign
- Introduced a major Material 3 refresh, faster MediaStore-backed search/categories, thumbnail support, settings improvements, DataStore preferences, and high-refresh-rate scrolling.

### v0.3.0 Beta - Trash, Copy/Paste & Search
- Added Trash Bin, copy/cut/paste, scoped search filters, Storage Dashboard entry points, Recent Files "See All", smart selection, and broader Material 3 Expressive polish.

### v0.4.0 Beta - External Storage & Smart Paste
- Added SD card and USB/OTG classification, per-volume storage behavior, per-volume Trash for permanent storage, smart paste conflict resolution, persistent sorting, pull-to-refresh, expanded themes, and better state restoration.

### v0.4.5 Beta - Stability & Visual Polish
- Improved category loading speed, dynamic colors, grid rendering, startup theme loading, dashboard reuse, permission UI, localization groundwork, and repository failure handling.

### v0.5.0 Beta - Security & Platform Hardening
- Raised the baseline to Android 11, hardened Trash metadata and FileProvider exposure, blocked sensitive internal files from open/share flows, improved signing/R8 readiness, and expanded accessibility/localization extraction.

### v0.6.0 Beta - Properties, Quick Access & Operations
- Added richer metadata/properties, Quick Access improvements, operation feedback, folder stats, UI controls, archive foundations, and broader operation reliability work.

### v0.7.0 Beta - Onboarding, Archives & Reliability
- Added onboarding, ZIP/7z archive workflows, Recent Files improvements, safer destructive operations, foreground progress, better thumbnails, navigation fixes, and stronger test coverage.

### v0.8.0 Beta - Storage Tools, Trash Rebuild & Safety
- Delivered rebuilt Trash metadata/recovery, Storage Cleaner, radial Storage Usage Map, advanced search filters, accessibility/haptics, safer file operations, archive safety policies, thumbnail policies, backup privacy, and comprehensive regression coverage.

Detailed beta release notes are archived in [beta/RELEASES-BETA.md](beta/RELEASES-BETA.md).
