# Arcile - Releases

> **Project:** Arcile
> **Version:** 2.1.0
> **Last Updated:** 2026-08-28

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v2.1.0](#v210) | 2026-08-28 | Adaptive workspaces, unified viewer actions, precision audio editing, safer storage cleaning, and conflict dialog refinements |
| [v2.0.5](#v205) | 2026-08-21 | Responsive navigation, focused Root access, richer file opening and sorting, complete activity history, and surrounding-aware media controls |
| [v2.0.0](#v200) | 2026-08-13 | Provider-aware Root and Shizuku workflows, refined video playback, clearer documentation, and compatible platform updates |
| [v1.9.0](#v190) | 2026-08-09 | Customizable workspaces, complete search and viewer controls, safer file operations, faster storage tools, and release reliability |
| [v1.8.0](#v180) | 2026-08-02 | Category UI parity, native PDF and document tools, Markdown editor, dual browser workspaces, and unified audio playback |
| [v1.7.0](#v170) | 2026-07-26 | Complete audio library and background player, PowerRename, split APK installation, richer video controls, and consistent media workflows |
| [v1.6.0](#v160) | 2026-07-23 | Encrypted OnlyFiles vaults, native video browsing and playback, visual storage insights, and refined Trash interactions |
| [v1.5.0](#v150) | 2026-07-13 | Independent feature ownership, safer file workflows, viewer and sharing reliability, plugin distribution, and navigation fixes |
| [v1.2.0](#v120) | 2026-06-21 | Activity history, backup/restore, refresh reliability, Save-to-Arcile durability, Gallery/Viewer polish, and navigation fixes |
| [v1.1.0](#v110) | 2026-06-14 | Storage Cleaner enhancements, Room-backed cache database, and immersive Media Viewer |
| [v1.0.0](#v100) | 2026-06-07 | First Stable Release - v0.8.0 through v0.9.9 plus final stable hardening |

---

# v2.1.0

**Release Date:** August 28, 2026

**Previous public release:** v2.0.5

**Development range included:** v2.0.6 through v2.1.0

**Known issues & roadmap:** Track active issues and ongoing engineering tasks in [TASKS.md](TASKS.md).

Arcile v2.1.0 introduces adaptive workspaces for large screens and foldables, unified file actions across native viewers, precision audio editing, smarter storage cleaning, and on-device update discovery.

## Highlights

- **Adaptive Multi-Pane Browsing**: Automatic dual-pane navigation on tablets and foldables, with compact swipe navigation on phones.
- **Unified Viewer Actions**: Rename, copy, move, delete, share, open-with, archive, and view properties directly within media viewers and OnlyFiles vaults.
- **Precision Audio Editor**: Trim, extract, remove sections, and combine tracks with zoomable waveforms, millisecond controls, and looped previews.
- **Safer Storage Cleaner**: Long-press selection, filename-version cleanup, full duplicate comparisons, and strict OnlyFiles vault protection.
- **Streamlined Conflict Resolution**: Compact file comparison cards with circular thumbnails, single-row metadata, and side-by-side action buttons.
- **Signed Update Discovery**: Check for signed Arcile and plugin updates on-device with rate-limited notifications.

## What's New Since v2.0.5

### Adaptive Workspaces & Browsing

- **Large-Screen Layouts**: Automatic dual-pane browsing and persistent navigation rails on tablets and foldables that adjust dynamically to window resizing and fold postures.
- **Folders-First Sorting**: Persisted preference to keep folders grouped above files across all sort and search views, with optional subfolder inheritance.
- **App-Matched Folder Icons**: Opt-in toggle to display recognizable app and category icons on supported media and system folders.
- **Refined Grid Sizing**: Snapped grid columns to discrete window density steps and preserved cached folder statistics during background refreshes.

### Viewers & Conflict Resolution

- **Complete In-Viewer Actions**: Full set of file management actions across image, video, PDF, audio, and archive viewers respecting read-only and vault boundaries.
- **Redesigned Conflict Dialogs**: Streamlined layout with round thumbnails, single-line metadata, and side-by-side action buttons for quick resolutions.
- **Smooth Gesture Handling**: Consistent touch slop, direction locking, and velocity curves across swipes, viewers, and dismissible feedback cards.

### Storage Cleaner & Protection

- **Predictable Selection**: Browser-style long-press multi-selection replaces checkboxes, keeping your position on the current screen after deletion.
- **Filename-Version Families**: Smart grouping for semantic versioning, duplicate suffixes, and APK variants without automatic preselection.
- **Vault & System Safety**: Completely excludes OnlyFiles vaults and ignored folders from cleanup scans to prevent accidental data loss.

### Audio Editing & Performance

- **Waveform & Range Controls**: High-precision waveform editor supporting M4A, AAC, WAV, Opus, Ogg, and WebM audio formats.
- **Non-Destructive Workflows**: Stream-copy exports, multiple trim ranges, middle-cut, and track merging with metadata retention.
- **Reliability & Updates**: Secure on-device update verification, hardened vault transitions, and general performance optimizations.

---

# v2.0.5

**Release Date:** August 21, 2026

**Previous public release:** v2.0.0

**Development range included:** v2.0.1 through v2.0.5

**Known issues & roadmap:** Track active issues and ongoing engineering tasks in [TASKS.md](TASKS.md).

Arcile v2.0.5 simplifies storage access, makes navigation and text dependable across screen sizes, completes activity history, expands browser opening and sorting controls, and gives image, video, and audio navigation better awareness of surrounding files.

## Highlights

- Use Android storage access consistently without the removed Root and Shizuku provider workflows.
- Reach the limited standard-Android Root Storage browser only through Home Quick Access, keeping it separate from the Home storage summary and Add Tab menu.
- Move through enabled Browser tabs before crossing workspace panes with content swipes, or swipe the app bar to switch panes directly.
- Open selected files explicitly as images, videos, audio, documents, archives, text, or other files, and sort folder-aware views by most or fewest contained files.
- Hold and drag image or video thumbnail strips for fast navigation, and keep audio playback queues aligned with the visible category order and surrounding tracks.

## What's New Since v2.0.0

### Storage and Browser navigation

- Removed optional Root and Shizuku access and their provider-specific services, routing, and operation overhead; Arcile now uses Android storage access throughout.
- Kept Root Storage paths distinct from normal volumes across browser titles, tabs, pinning, history, and restoration while limiting its entry point to Home Quick Access.
- Added direct Add Tab choices for Internal Storage, SD cards, and USB drives, plus settings for Browser tabs, the preferred Home or Browse start page, last-folder restoration, and compact Browser app bars.
- Made content swipes traverse enabled tabs before moving between panes, retained direct pane switching from the app bar, and shared Browser app-bar state across tabs and dual panes.
- Added pinch-to-resize Browser grids, corrected newest and oldest ordering across files and folders, and introduced Most Files First and Fewest Files First folder sorting.

### Responsive interface and activity history

- Prevented selection summaries, page titles, Browser tabs, segmented controls, and sort labels from clipping on compact screens or with large text.
- Added expanded-to-collapsed app bars across Settings and compatible secondary pages, with a preference to keep supported app bars compact.
- Expanded Activity into a dated timeline for visited pages, opened files and folders, and file operations, with a master recording switch that preserves existing history when disabled.
- Made feedback messages swipe-dismissible with clearer icon and action containers, consolidated duplicate property details, and simplified the splash screen to Arcile's vector foreground mark.

### File opening, updates, and media

- Added explicit Open As choices for image, video, audio, document, archive, text, and other file handling.
- Improved in-app APK update installation with unknown-source permission recovery and clearer self-update guidance.
- Restored a visible video back action, prevented notification-shade swipes from triggering player gestures, and fixed black protected-video playback.
- Moved image and video thumbnail strips vertically in landscape, centered the active item, respected safe display edges, and added hold-and-drag fast scrolling.
- Preserved visible audio-category ordering in playback queues so previous and next actions remain aware of surrounding tracks.

---

# v2.0.0

**Release Date:** August 13, 2026

**Previous public release:** v1.9.0

**Development range included:** v1.9.1 through v2.0.0

**Known issues & roadmap:** Track active issues and ongoing engineering tasks in [TASKS.md](TASKS.md).

Arcile v2.0.0 completes provider-aware file management across Normal Android, Root, and Shizuku access, significantly refines video playback, and makes the project's user and developer documentation easier to navigate.

> [!CAUTION]
> **Root and Shizuku limitations:** Root support has not been tested on a rooted device and should be treated as unverified. Shizuku does not provide unrestricted root access, so it cannot access every filesystem location or perform every privileged operation; availability may vary by device and Android version.

## What's New Since v1.9.0

### Root, Shizuku, and Normal Android access

- Choose Automatic, Root, Shizuku, or Normal Android access during onboarding or later in Settings, with live readiness, identity, reconnect, permission, and fallback controls.
- Browse, search, inspect, rename, copy, move, delete, shred, archive, analyze, clean, preview, edit, open, and share files while preserving their selected storage-provider identity.
- Transfer files safely between access providers with staged publication, conflict handling, cancellation cleanup, partial-failure reporting, and interruption recovery.
- Keep system and virtual filesystems read-only, with a separate Root-only safety opt-in for protected app-data writes.
- Preserve the active folder and interface state when access disappears, then recover through an explicit reconnect or Normal-access action.

### Protected media, archives, Trash, and storage tools

- Preview images, videos, audio, PDFs, APK details, thumbnails, and metadata through expiring protected content grants without exposing storage paths.
- Keep gallery video order and thumbnail position intact, retain the cached thumbnail until the next video renders, and keep titles clear of display cutouts.
- Continue ordinary video playback only when Arcile moves to the background, with media notification controls and a Close player action; protected playback still closes on backgrounding.
- Pinch to zoom and pan video, scrub quickly or precisely in either direction without losing the controls, tap the duration to switch between total and remaining time, and see the active fit, zoom, or fill mode.
- Keep brightness, volume, and metadata gestures clear of Android navigation edges, and restart the control timeout after every interaction.
- Get a more useful image-decoding error when an image is damaged, unsupported, or temporarily inaccessible.
- Create, browse, and extract supported archives in protected locations while retaining backend identity through foreground work and restarts.
- Move supported protected files to same-volume Trash, keep disconnected items visible, and restore only after the original provider is ready.
- Run explicit folder-scoped usage maps and cleaner scans with bounded traversal, verified duplicate detection, partial-result warnings, and provider-safe cleanup.

### Documentation, community, and platform maintenance

- Replaced the README's developer sections with a comprehensive user-facing feature guide and moved build, structure, stack, signing, testing, and release details to `DEVELOPMENT.md`.
- Added the Discord community link and expanded credits across the README, website, and in-app license screen.
- Standardized Root actions on the Android icon, named primary storage “Internal Storage,” and explained the difference between OnlyFiles quick and full health checks.
- Updated compatible Android, Kotlin, Compose, Hilt, KSP, Media3, security, and test dependencies while keeping Gradle 9.5.0, Coil 2, and stable MaterialKolor 4.
- Synchronized app metadata to `versionName` `2.0.0` and `versionCode` `200`.

---

# v1.9.0

**Release Date:** August 9, 2026

**Previous public release:** v1.8.0

**Development range included:** v1.8.1 through v1.9.0

**Known issues & roadmap:** Track active issues and ongoing engineering tasks in [TASKS.md](TASKS.md).

Arcile v1.9.0 makes the main workspace more personal, expands search and native viewer controls, overhauls Storage Cleaner navigation, and hardens large or interrupted file operations.

## What's New Since v1.8.0

### Customizable Home and Browser

- **Custom Home layout:** Show, hide, reset, and reorder Home sections while preserving Apply and Cancel behavior.
- **Accessible ordering controls:** Reorder Utilities, Quick Access items, and Home sections with consistent item-specific controls, immediate feedback, and keyboard or assistive-technology support.
- **Independent Browser tabs:** Keep separate locations, history, selection, search, and scroll position in each Browser workspace, with configurable tab visibility and ordering.
- **Landscape dual pane:** Optionally show both Browser workspaces side by side in landscape and return to the last-focused workspace in portrait.
- **Root Storage shortcut:** Opt into browsing and measuring system paths available through normal Android permissions, with honest access limits and protected-location feedback.

### Search, Viewers, and Media

- **Complete search experience:** Use the redesigned search and filters across Browser, Audio, Images, Videos, APKs, Documents, and archives, including reliable filter-only results and accurate empty states.
- **Expanded PDF tools:** Pinch to zoom, search native PDF text on supported Android versions, keep the screen awake, and print through Android.
- **Reliable media viewing:** Keep video immersive and awake, preserve viewer context, and lock encrypted OnlyFiles playback safely when Arcile leaves the foreground.
- **Responsive external opens:** Shared images, PDFs, videos, and audio now validate storage access away from the interface, with bounded waits and a retryable error state.
- **Localized presentation:** Audio Favorites and user-visible item counts now follow the selected language and locale-aware grammar.

### Storage Cleaner and File Safety

- **Full Cleaner pages:** Review every cleaner category on a dedicated page with pull-to-refresh, live progress, retained selections, duplicate comparison, settings, and reliable return state.
- **Faster Cleaner scans:** Reuse cached per-category results, show progress and ETA, and remove deleted or stale items immediately.
- **Safe cross-storage moves:** Keep a fully verified destination if source cleanup only partially succeeds, and retain exact cleanup state for a safe retry.
- **Safe deep folders:** Analyze and copy unusually deep directory trees without recursive stack growth, stop cycles, honor cancellation, and return bounded partial analysis when necessary.
- **Reliable large operations:** Store bounded bulk requests privately and hand Android services only a durable ID, preventing oversized Binder transactions and duplicate execution.
- **Temporary share access:** Release only the document permissions acquired by Save to Arcile after every terminal or interrupted path.

### Security, Performance, and Reliability

- **Private recovery state:** Keep operation and mutation recovery data out of backups, omit archive passwords, bound retention, and exclude vault locations from cloud backup and device transfer.
- **Safer archive extraction:** Enforce expanded-size, compression-ratio, cancellation, and free-space limits for GZIP, BZIP2, and XZ streams without leaving partial output.
- **Safer split-APK installs:** Bound package staging, extract only compatible APKs into unique temporary folders, and clean staging after completion or interruption.
- **Lighter background work:** Coalesce high-frequency transfer progress and remove recursive plaintext cleanup from the startup path while preserving exact final state and secure cleanup.
- **Usable date filters:** Keep date-range fields and actions reachable in short, landscape, large-text, and keyboard-constrained windows.

# v1.8.0

**Release Date:** August 2, 2026

**Previous public release:** v1.7.0

**Development range included:** v1.7.1 through v1.8.0

**Known issues & roadmap:** Track active issues and ongoing engineering tasks in [TASKS.md](TASKS.md).

Arcile v1.8.0 delivers full design and navigation parity across all file categories, introduces native PDF viewing and Markdown editing, adds a second independent Browser workspace, and unifies audio playback with smoother gestures and interactions.

## What's New Since v1.7.0

### Category Design Parity
- **Unified Category Experience:** All seven file categories (Images, Videos, Audio, Documents, APKs, Archives, and Vaults) now share the same polished floating navigation, list/grid views, fast scrolling, date grouping, search, and predictive-back motion.
- **Documents Library:** Added a dedicated Documents space with category-matched file and folder navigation, quick filters, and document preview tiles.

### Native Reading & Editing
- **Built-in PDF Viewer:** View PDF documents natively with smooth multi-page scrolling, quick page navigation, and pinch-to-zoom controls without leaving Arcile.
- **Markdown & Text Editor:** Create, edit, and format Markdown and plain text files with live preview, formatting shortcuts, source tools, undo/redo history, document stats, and automatic draft recovery.

### Dual Workspaces & Navigation
- **Two Browser Workspaces:** Swipe to open a second independent Browser workspace with its own location history, folder position, search, and selection state.
- **Per-Folder Memory:** Returning to any folder, category tab, or workspace now instantly restores your exact scroll and grid position.
- **Preferred Launch Screen:** Choose whether Arcile starts directly on your Home dashboard or Browser view.

### Playback & Interface Polish
- **Unified Audio Experience:** Merged mini and full playback into one seamless floating player with smooth expansion gestures, responsive artwork transitions, wavy seek rails, and notification playback controls.
- **Finished Operations:** Improved batch file operation feedback, progress tracking, and background notification management across all operations.

---

# v1.7.0

**Release Date:** July 26, 2026

**Previous public release:** v1.6.0

**Development range included:** v1.6.1 through v1.7.0

Arcile v1.7.0 introduces a complete device-audio experience with background playback, expands native media and package workflows, adds powerful batch renaming, and brings category screens and common file actions into closer alignment.

## What's New Since v1.6.0

### Audio Library & Playback
- **Dedicated audio library:** Browse device music through Songs and Folders pages with album artwork, search, sorting, grouping, fast scrolling, and saved list or grid layouts.
- **Complete audio file workflows:** Select tracks and folders to copy, cut, paste, rename, share, open with another app, create ZIP archives, delete, inspect properties, or reveal the containing folder.
- **Background playback:** Keep listening outside Arcile with branded Android media controls, reliable playback state, previous/next actions, seeking, shuffle, repeat, and an editable queue.
- **Connected mini and full players:** Move smoothly between the compact and expanded player with coordinated artwork motion, a persistent mini progress rail, wavy seeking, adaptive spacing, balanced transport controls, and direct queue access.
- **Consistent media categories:** Audio now follows the same loading, error, selection, clipboard, deletion, properties, and folder interaction patterns used by the Image and Video galleries.

### File & Package Workflows
- **PowerRename:** Batch rename files using search and replace, regular expressions, case changes, numbering, reusable presets, conflict previews, safe rollback, and one-tap Undo.
- **APK and split-package installation:** Inspect and install standard APKs or compatible `.apks`, `.xapk`, and `.apkm` sets with package previews, progress, cancellation cleanup, and downgrade guidance.
- **Safer everyday operations:** Improved batch-deletion progress and completion feedback, restored the last Browser location, repaired folder sharing through ZIP packaging, and kept unsupported file handoffs predictable.
- **Resilient archives and backups:** Unsafe archive entries are skipped without stopping valid extraction, and settings backups now include OnlyFiles preferences while excluding temporary operation data.

### Media, Cleaner & Interface Polish
- **More capable video viewing:** Added gesture controls, metadata, auto-hiding controls, drag-to-dismiss, resize modes, stable thumbnail strips, and configuration-safe playback.
- **Cleaner review improvements:** Refined item actions, ignore rules, ignored-item management, thumbnail-cache reporting, and storage summaries.
- **Expressive controls:** Standardized action sizing, dropdown surfaces, grouped settings, dynamic state indicators, and an interactive accent-color picker across compact and larger layouts.
- **Faster gallery navigation:** Improved scrollbar drag synchronization, reachability, activation feedback, and large-library navigation.
- **Clearer OnlyFiles controls:** Moved vault security, thumbnail-cache, grant-revocation, and security-disclosure actions into the OnlyFiles screen where they are easier to find.
- **Consistent system feedback:** Standardized background operation and active-grant notifications with Arcile branding, progress states, and tap-to-open behavior.
- **Honest plugin presentation:** Replaced unavailable placeholder rows with a focused empty state until compatible plugins are installed or available.
- **Updated project website:** Added compact repository and download statistics with total and latest release counts.

## Looking Ahead to v1.8.0

The Audio category will receive another UI and UX refinement in v1.8.0, and the remaining file categories will begin receiving the same broader overhaul. This work needs additional design and verification time, so v1.8.0 will arrive a little later than the recent release cadence.

---

# v1.6.0

**Release Date:** July 23, 2026

**Previous public release:** v1.5.0

**Development range included:** v1.5.1 through v1.6.0

Arcile v1.6.0 adds encrypted OnlyFiles vaults and a complete native video experience, then refines storage analysis, Trash, loading states, and media navigation across the app.

## What's New Since v1.5.0

### OnlyFiles Vaults
- **Private and portable vaults:** Create multiple encrypted vaults in app-private storage or Arcile-managed folders on internal, SD-card, and USB storage.
- **Daily file workflows:** Browse, search, sort, select, copy, move, import, export, share, and open encrypted content through guarded, cancellable operations.
- **Protected access and recovery:** Use optional biometric unlock, automatic foreground locking, screen-capture protection, encrypted media previews, health checks, and recoverable atomic changes.
- **Documented boundaries:** Vault cryptography, plaintext handoffs, backup guidance, and recovery limits are described in the [OnlyFiles format and security guide](arcile-app/docs/ONLYFILES_FORMAT_AND_SECURITY.md). OnlyFiles remains unaudited software.

### Native Video Experience
- **Video gallery:** Browse videos and albums with search, timeline grouping, sorting, view controls, selection actions, and stable thumbnails.
- **Shared native viewer:** Browser, Recents, Trash, external opens, and OnlyFiles use one Media3 viewer with sibling paging, seeking, tracks, subtitles, speed, repeat, resize, favorites, sharing, and source-aware file actions.
- **Large-library reliability:** Playback loads the selected item first and resolves neighboring media lazily for responsive opening and navigation.

### Storage, Trash, and Interface
- **Capacity-aware storage map:** Visual rings distinguish free space, system data, and accessible files, include complete folder accounting, and reuse scanned sizes for instant drill-down.
- **Clearer map navigation:** The responsive, theme-adaptive map and grouped list share highlighting, touch-shaped feedback, accessible path navigation, and direct reset behavior.
- **Focused Trash actions:** Media previews open directly, filenames expose applicable actions, and single-item restore uses a clear confirmation while preserving safe recovery behavior.
- **Consistent motion:** Expressive loading states and video thumbnail transitions now align with the rest of Arcile.

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
