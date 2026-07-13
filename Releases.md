# Arcile - Releases

> **Project:** Arcile
> **Version:** 1.5.0
> **Last Updated:** 2026-07-13

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v1.5.0](#v150) | 2026-07-13 | Independent feature ownership, safer file workflows, viewer and sharing reliability, plugin distribution, and navigation fixes |
| [v1.2.0](#v120) | 2026-06-21 | Activity history, backup/restore, refresh reliability, Save-to-Arcile durability, Gallery/Viewer polish, and navigation fixes |
| [v1.1.0](#v110) | 2026-06-14 | Storage Cleaner enhancements, Room-backed cache database, and immersive Media Viewer |
| [v1.0.0](#v100) | 2026-06-07 | First Stable Release - v0.8.0 through v0.9.9 plus final stable hardening |

---

# v1.5.0

**Release Date:** July 13, 2026

**Previous public release:** v1.2.0

**Unreleased development range included:** v1.2.1 through v1.5.0

**Skipped public versions:** v1.3.0 and v1.4.0

Arcile v1.5.0 consolidates the unreleased v1.2.x, v1.3.x, and v1.4.x development series into the first public release since v1.2.0. It focuses on independently owned features, safer storage and file handoffs, more dependable Gallery and viewer behavior, and startup, navigation, and feedback reliability.

## What's New Since v1.2.0

### Architecture & Startup Reliability
- **Independent feature ownership:** Browser, Home, Gallery, Recents, Trash, Settings, onboarding, archives, storage tools, plugins, and shared-file imports now own their state and workflows through focused feature and core modules.
- **Focused storage capabilities:** Browsing, mutations, media, volumes, Trash, preferences, authorization, and archive destinations use narrower storage contracts so unrelated workflows no longer share mutable state.
- **Reliable startup and restoration:** Cold launches open Home predictably, Browser restoration starts only when Browser is entered, and loading or recovery failures provide dedicated retry states instead of crashing the initial screen.
- **Lifecycle-safe authorization:** System file confirmations, settings pickers, and route-owned access survive lifecycle changes, reject stale results, and recover cleanly when access is denied or unavailable.

### File Workflows, Search & Storage Safety
- **Independent operations and feedback:** Concurrent Browser operations keep separate progress and results, cancelled work cleans staged data, and in-app feedback remains on the screen where its action originated.
- **Safer destinations and imports:** Save-to-Arcile and archive workflows validate storage-owned paths, preserve readable-folder navigation, prevent writes to unavailable or read-only locations, and reject duplicate or stale import actions.
- **Search and cache reliability:** Browser, Recents, and Trash prevent older searches from replacing newer results, while thumbnail, staging, and cleaner caches expose isolated loading, clearing, and failure states.
- **Path and metadata correctness:** File names display consistently for Android and Windows-style paths, while image metadata editing is limited to supported, writable local files.

### Gallery, Viewer & File Actions
- **Complete viewer context:** Images open anchored to the selected item with the full ordered neighboring-image carousel, including images opened from Gallery, Browser, and Trash.
- **Selection-safe viewer actions:** Gallery selection remains available in the viewer and is preserved correctly when an image is deleted or an operation fails.
- **Trash-aware viewing:** Trash images open in Arcile's read-only viewer with applicable share and open-with actions, while unavailable edit or destructive actions remain hidden.
- **Reliable multi-file sharing:** Android shares the complete selected batch with the required URI grants, and safely sends nothing if every selected file cannot be prepared.

### Navigation, Quick Access & Interface Reliability
- **Predictable back behavior:** Home now allows the system back gesture to close Arcile normally, while Browser and viewer returns retain the correct route, location, selection, and scroll context.
- **Quick Access correctness:** Custom locations are normalized before saving, restricted shortcuts use persisted Android access, and each shortcut appears in one appropriate management section.
- **Consistent file presentation:** Shared grids, lists, filters, range selection, thumbnails, fast scrolling, workflow dialogs, theming, and accessibility behavior now come from bounded reusable UI components.
- **State-accurate storage tools:** Storage Cleaner, storage usage, Activity Log, Trash, archive, and cache controls keep their loading, empty, error, and action states independent.

### Plugins & Distribution
- **Optional plugin system:** Arcile retains signed plugin discovery, compatibility checks, management, and secure file handoff without embedding optional viewers in the main app.
- **Standalone model viewer:** The bundled Arcile GLB Viewer and its install-catalog entry were removed because model viewing is now distributed as a separate standalone application.

### Documentation & Verification
- **Current project guidance:** README, website, development guidance, and release documentation now describe Arcile 1.5.0, the current modular ownership model, supported workflows, and focused module verification.
- **Regression coverage:** Focused tests cover navigation, viewer datasets and deletion, sharing, imports, storage authorization, preferences, search ordering, cancellation cleanup, architecture boundaries, and startup behavior.

---

# v1.2.0

**Release Date:** June 21, 2026

**Previous public release:** v1.1.0

**Candidate range included:** v1.1.1 through v1.2.0

Arcile v1.2.0 consolidates the v1.1.x follow-up series into a public release focused on reliability, recovery, refresh behavior, Gallery and image viewer polish, Quick Access improvements, and predictable back navigation. It does not change Arcile's local-first privacy model or add background-process lifetime behavior.

## What's New Since v1.1.0

### Activity, Backup & Recovery
- **Activity Log:** Added a local Activity tool for opened folders and foreground file-operation history, with clear controls and Home/Tools navigation.
- **Manual settings backup and restore:** Added file-based preference export and restore from Settings and onboarding, including previews, restore results, theme support, and safe error handling.
- **Durable Save to Arcile imports:** Shared-file imports now run through foreground operations with staged temp files, progress notifications, cancellation cleanup, recovery checkpoints, and mutation finalization.
- **Operation recovery checkpoints:** Interrupted copy/import work has stronger checkpoint data for staged files, finalized outputs, rollback hints, and Trash IDs.

### Refresh & Storage Reliability
- **Live media refresh:** Downloads, screenshots, gallery changes, external file updates, category data, open folders, Home carousel data, and Recent Files refresh more reliably after MediaStore and storage mutation events.
- **Recent Files refresh polish:** Pull-to-refresh now covers empty, loading, search, grid, and list states and avoids stale overlapping reloads.
- **Browser listing freshness:** Browser folders read live filesystem children where needed so Gallery image cache entries no longer hide folders in places such as Pictures.
- **Folder stats and cache stability:** Folder rows keep cached size/count visible while background refreshes run, and Room cache schema handling is documented and constrained to the intended migration path.
- **Video and thumbnail reliability:** Browser video thumbnails now fall back to direct metadata extraction when MediaStore thumbnail URIs are unavailable, with broader video extension recognition.

### Gallery & Image Viewer
- **Gallery copy/paste:** Real gallery albums support direct copy/cut paste, album-card paste actions, conflict resolution, and foreground copy/move feedback.
- **Gallery refresh and performance:** Album contents update after copy/move operations, gallery snapshots avoid stale persisted data, and album shaping/cover work moved off the main thread.
- **Image viewer details:** Viewer overlays now show position, filename, date/time, and resolution; the metadata sheet includes richer file and EXIF fields.
- **Viewer state restoration:** The image viewer preserves chrome, metadata sheet, current image, rotation, and erase-dialog state across activity recreation.
- **External image opens:** Arcile can handle external `image/*` opens with the standalone viewer, while reusing full image metadata in single-image Properties cards.
- **Deep gallery thumbnail performance:** Opening an image far down a large gallery now centers the selected bottom-strip thumbnail immediately, uses stable path keys and fixed item dimensions, and animates only nearby page changes.

### Navigation, Motion & Input
- **System predictive back:** Added Android 14+ predictive back support and expressive motion across main cards, buttons, lists, split actions, toolbar navigation, Browser, Gallery, Recents, Trash, and Archive Viewer.
- **Route-order Browser handoffs:** Browser opens from Cleaner, Recent Files, Quick Access, Storage Dashboard, Archive Viewer, and similar detail screens preserve the originating screen so back gestures unwind in the same order the user navigated.
- **Storage Cleaner local back:** Back dismisses ignored-items, delete confirmation, and details UI before leaving Cleaner.
- **Storage Dashboard handoff fix:** Dashboard and usage-map Browser handoffs return to Home instead of landing on an unintended Browser route.
- **Keyboard reliability:** Search fields, dialog name inputs, filters, and other text inputs now open the keyboard on focus/tap and stay visible above the IME.

### Onboarding & Quick Access Polish
- **Onboarding overhaul:** Refined onboarding with larger touch targets, clearer permission copy, pill-shaped status chips, reduced-motion-aware animation, and a redesigned expressive setup flow.
- **Home recent thumbnails:** Home recent-file carousel thumbnails now keep stable cache keys when scrolled offscreen and back.
- **Quick Access management:** Manage Quick Access now groups shortcuts into clearer sections, supports fullscreen drag reordering for enabled shortcuts, adds haptic switch feedback, and uses check/close symbols in home-toggle switches.
- **Home Arcile shortcut:** The Home Quick Access "All" shortcut is now labeled "Arcile" and loads the actual launcher icon for the active build.
- **Storage Cleaner layout polish:** Cleaner candidate rows keep Ignore actions readable while long risk reasons truncate cleanly.

### Security & Handoffs
- **External handoff hardening:** File open/share handoffs use carried content URI identity where available, avoid raw MediaStore path lookups, and preserve shared filenames through app-owned staged providers.
- **Backup privacy:** Local cache metadata is excluded from backup and transfer paths.
- **Secure delete reporting:** Secure delete failures are surfaced instead of being reported as successful.

### Archive Reliability
- **Extraction conflict isolation:** Archive extraction conflict decisions reset per extraction so skip and keep-both directory choices cannot leak into later archive operations.
- **Archive browser restoration:** Saved archive path and entry-prefix browsing state restore after process recreation without persisting archive passwords.
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
