# Arcile - Releases

> **Project:** Arcile
> **Version:** 1.0.0
> **Last Updated:** 2026-06-07

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v1.0.0](#v100) | 2026-06-07 | First Stable Release - v0.8.0 through v0.9.9 plus final stable hardening |

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
