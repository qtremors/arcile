# Arcile Changelog

> **Project:** Arcile
> **Version:** 2.1.3
> **Last Updated:** 2026-09-28

---

## [2.1.3] - 2026-09-28

- **New Audio Player Layouts**: Give the mini player circular artwork, a progress ring, and grouped previous and next buttons. Add a favorite button, straight seek slider, pill shaped transport controls, paired lower actions sized to match file actions, and a More menu anchored to its button in the full player.
- **Player Motion and Themes**: Animate track changes, playback controls, and mini player expansion on tap or swipe. Open the queue with an upward swipe, and use the selected theme's background and accent colors throughout the full player.
- **Playback Visualizer and Layout**: Show audio-responsive bars in the play/pause button with an on/off switch. Distribute player content across available height, resize artwork to fit, and scroll on short screens.
- **Storage Settings**: Show connected storage volumes and classification controls directly in Settings alongside temporary file cleanup.

## [2.1.2] - 2026-09-27

- **Settings Pages**: Organize controls into focused pages with clearer section names and descriptions.
- **Compact Choices**: Keep the theme mode tiles and show start page, theme preset, filename, and extension choices in compact rows that scroll when needed. Put the Custom theme preset on its own row.
- **File Opening**: Choose which supported or installed plugin extensions open in Arcile, and add or remove custom extension exclusions.
- **Settings Stability**: Fix the Appearance crash when choices overflow on narrow screens.

## [2.1.1] - 2026-09-26

- **Stable Home Dashboard**: Show saved recent files and category totals immediately, then quietly check live storage on a cold launch. Avoid duplicate resume refreshes and keep the multicolor storage animation visible when the category breakdown is unavailable.
- **Recent File Cache**: Remove obsolete saved preview lists after successful refreshes while keeping the latest fallback for the next launch.
- **Persistent Folder Details**: Keep saved folder sizes visible while recalculating folders shown after a new launch. Retain counts through failed scans and show explicit pending or unavailable sizes when no saved value exists.
- **Consistent Collapsed App Bars**: Keep locked app bars in their natural collapsed position with the compact title visible, and prevent dragging them open until expansion is enabled.
- **Home Cache and Scrolling Corrections**: Show saved category totals before quiet background updates, restore the multicolor loading bar with a smoother segment reveal, keep categories ranked by size, and allow scrolling with collapsed app bars.

## [2.1.0] - 2026-08-28

- **Adaptive Primary Workspace**: Added persistent navigation and automatic dual-pane browsing for larger displays and foldables, adapting smoothly to live resizing and fold postures.
- **Refined Conflict Resolution**: Compacted file conflict dialogs with circular thumbnails, single-row metadata, full-width path labels, and side-by-side action buttons.
- **Consistent Viewer Actions**: Added capability-aware file actions (rename, copy, move, delete, share, open-with, archive, properties) across all media viewers, PDFs, and OnlyFiles vaults.
- **Predictable Storage Cleaner**: Replaced candidate checkboxes with long-press selection, added filename-version grouping, expanded duplicate comparisons, and excluded OnlyFiles vaults and ignored folders from cleanup scans.
- **App-Aware Folder Icons & Ordering**: Added an opt-in toggle for app-matched folder icons and a persisted setting to group folders above files across all sort modes.
- **Improved Browsing & Presentation**: Aligned grid sizing to discrete density steps, preserved cached folder statistics during background refreshes, and standardized locale-aware file size formatting.
- **Signed Update Discovery**: Added on-device update checks for Arcile in About and compatible plugin updates in Plugins with rate-limited notifications.
- **Smoother Startup Performance**: Deferred disk reads and preference storage initialization during composition and dependency injection to eliminate main-thread StrictMode violations and startup frame drops.

## [2.0.9] - 2026-08-26

- **Predictable App Gestures**: Added shared touch-slop, direction-lock, velocity, cancellation, and system-edge rules so Browser swipes, Gallery viewer gestures, audio-player gestures, and feedback dismissal distinguish taps from intentional swipes and settle once at release.

## [2.0.8] - 2026-08-25

- **Precision Audio Editing**: Added zoomable waveforms, millisecond boundary controls, scrubbing, looped selection previews, section extraction, middle removal, and ordered combining for compatible M4A/AAC, WAV, Opus/Ogg, and WebM audio. Exports prefer stream-copy, fall back to re-encoding with a visible warning, copy readable tags and artwork, inherit the first item’s metadata when combining, and keep completed audio when some metadata cannot be copied.
- **Reliable Audio Selection**: Kept waveform handles attached throughout a drag, centered high zoom levels on the playhead, and added a Classic slider option without the waveform.
- **Smarter Audio Editor**: Added a compact theme-aware layout with artwork and file details, side-by-side boundary controls, default-on Loop and Waveform switches, export notes behind an info button, multiple trim ranges, and in-editor audio adding, ordering, and removal.

## [2.0.7] - 2026-08-24

- **Protected Vault Viewing**: Kept screenshot protection active while moving from OnlyFiles into vault video viewers, including overlapping screen transitions.
- **Accurate Vault Unlock State**: Prevented stale unlock prompts after returning from an already unlocked vault file or completing biometric unlock.
- **Reliable Background Operations**: Stopped timed-out file operations and vault imports cleanly with visible failure feedback, and removed their notifications after completion, failure, cancellation, rejected starts, or stale-process recovery.
- **Safe Settings Restore**: Bounded settings backup imports, verified their contents before changing live preferences, and rolled back every applied store when a restore could not finish.
- **Reliable Shared Imports**: Reserved reclaimable storage before shared-file imports, staged each batch before exposing files, and removed staged data when space changed or an import could not finish.
- **Predictable Thumbnail Scrubbing**: Made held thumbnail strips follow the finger directly without disappearing or jumping outside the available queue.
- **Complete Gallery Batch Rename**: Added Rename to Gallery multi-selection and connected it to the transactional batch-rename flow.
- **Reliable Recents**: Repaired grouped list and grid selection, kept range selection anchored to stable files, enforced newest-first ordering, and removed Recents sort controls while preserving view options.
- **Dependable File Renames and Browser Preferences**: Fixed case-only file and folder renames and kept the selected apply-to-subfolders sort scope when reopening Browser options.
- **Non-Blocking Mini-Player**: Let back gestures, text fields, and the keyboard work outside the collapsed player, and made swipe-down dismissal settle consistently from distance or velocity.
- **Selectable Search Results**: Restored long-press and range selection in Browser search results and replaced search-header back arrows with clear close icons.

## [2.0.6] - 2026-08-22

- **Clean Modern Android Build**: Updated Android and Compose integrations, removed obsolete packaged resources, and cleared compiler and static-analysis warnings across the app and tests.

## [2.0.5] - 2026-08-21

- **Responsive Text And App Bars**: Prevented selection summaries, page titles, browser tabs, segmented controls, and sort labels from clipping on compact or large-text layouts, and added scroll-to-collapse app bars across Settings and compatible secondary pages.
- **Tab-First Browser Gestures**: Made content swipes move through enabled browser tabs before crossing pane boundaries, while app-bar swipes continue to switch panes directly.
- **Focused Root Access**: Kept Root Storage exclusive to Home Quick Access by removing its swipeable storage-summary page and Add Tab shortcut.
- **Vector Splash Branding**: Switched the startup splash foreground from a raster mipmap to Arcile's VectorDrawable for crisp rendering at every display density.
- **Flexible Opening And Folder Sorting**: Added browser selection options to open files explicitly as images, videos, audio, documents, archives, text, or other files, plus Most Files First and Fewest Files First sorting for folder-aware views.
- **Surrounding-Aware Media Navigation**: Preserved the visible audio-category order in playback queues and added hold-and-drag fast scrolling to image and video thumbnail strips.

## [2.0.4] - 2026-08-21

- **Chronological Activity Timeline**: Reworked Activity into a Recents-style history grouped under Today, Yesterday, and calendar dates, covering visited pages, opened files and folders, and file operations including create, rename, restore, and Trash actions.
- **Universal Collapsed App Bars**: Added a preference to keep supported app bars compact while preserving their normal expanded-to-collapsed scrolling when disabled and sharing Browser state across tabs and panes.
- **Centered Landscape Viewer Strips**: Kept the active image or video thumbnail centered, inset the vertical strip from the safe display edge, and reserved space so it no longer overlaps viewer controls.

## [2.0.3] - 2026-08-21

- **Browser Preferences**: Added dedicated Browsing settings for remembering the last folder and keeping browser app bars compact, with a shared app-bar state across tabs and dual panes.
- **Complete Activity History**: Expanded Activity to include opened files alongside folders and file operations, with a master recording switch that retains existing history when disabled.
- **Responsive Browsing And Viewers**: Added pinch-to-resize browser grids and moved image and video thumbnail strips to a vertical layout in landscape orientation.
- **Clearer Feedback And Properties**: Made feedback messages swipe-to-dismiss with distinct icon and action containers, removed the close button, and consolidated duplicate file and folder property details.
- **Cleaner Startup**: Updated the splash screen to show only Arcile's foreground logo without the launcher icon background.

## [2.0.2] - 2026-08-15

- **Simplified Storage Architecture**: Removed optional Root and Shizuku access and their provider-specific workflows. Arcile now uses Android storage access throughout, with less service, routing, and operation overhead.
- **Long-Press New Tab Storage Selection**: Added a storage volume and root chooser dropdown menu when long-pressing the `+` button in the browser tab bar, allowing users to open a new tab directly in Internal Storage, Root Storage, SD cards, or USB OTG drives.
- **Settings Page Controls**: Added the Browser tabs toggle and Start page selector (Home vs Browse) directly to the Appearance settings page while keeping the 3-dot dropdown menu toggles intact.

## [2.0.1] - 2026-08-15

- **Simpler Storage Access**: Detects and uses Root automatically, restores the v1.9.0 Root icon, replaces provider selection with one optional Shizuku switch, falls back to normal Android access when a privileged service stops, and offers Shizuku when opening supported restricted folders.
- **Shizuku Status Badge**: Shows the installed Shizuku app icon beside app-bar actions while Shizuku is active, with an Appearance setting to hide it.
- **Reliable APK Updates**: Opens the unknown-app-sources permission screen when an update needs it and supports installing an Arcile APK update from inside Arcile with clear self-update guidance.
- **Video Playback Fixes**: Added a visible back button, prevented notification-shade swipes from triggering player gestures, and fixed black video playback in OnlyFiles.
- **Browser And Cleaner Accuracy**: Fixed newest and oldest sorting across files and folders, labels empty-folder results as folders, and shows when recursive folder details are still being calculated.
- **Browser Location Accuracy**: Keeps Root Storage paths distinct from normal volumes, shows the current folder name in the app bar, and preserves correct Root Storage names and locations across browser tabs, pinning, history, and restore.

## [2.0.0] - 2026-08-13

- **Video Playback**: Kept titles clear of display cutouts, preserved gallery order and the selected thumbnail position, kept the cached thumbnail visible throughout each video and surface handoff until the matching first frame, limited background playback to app backgrounding with an Arcile media notification and Close player action, renewed the control timeout after every touch interaction, protected gestures from Android navigation edges, added pinch zoom and pan, and made fast or slow forward and reverse progress scrubbing pause, track the finger directly, and resume without hiding controls.
- **Interface Clarity**: Added an easy-to-tap total/remaining video timer and active resize-mode feedback, explained OnlyFiles health checks, improved image-decoding errors, standardized Root icons, and consistently named primary storage “Internal Storage.”
- **Runtime Compatibility**: Kept Android 15-only PDF text search behind an explicit platform guard and made viewer and editor feedback follow configuration changes safely.
- **Compatible Dependency Refresh**: Updated the Android, Kotlin, Compose, Hilt, KSP, Media3, security, and test libraries that remain compatible with the Gradle 9.5.0 toolchain, while retaining Coil 2 and stable MaterialKolor 4.

## [1.9.10] - 2026-08-11

- **Storage Provider Setup**: Added Automatic, Root, Shizuku, and Normal Android selection to onboarding and Settings, with live connection state, effective privilege, reconnect, permission, manager, and explicit Normal fallback actions.
- **Protected Write Controls**: Added a Root-only protected app-data write preference with an explicit safety warning while keeping system partitions and virtual filesystems read-only.
- **Provider-Aware App Access**: Allowed Arcile to open through a ready Root or Shizuku connection without requiring Normal Android all-files permission, with passive access refreshes whenever the app resumes.
- **Context-Preserving Recovery**: Kept the current browser location and in-progress interface state visible when Root, Shizuku, or Normal access disappears, with clear reconnect and permission recovery actions.
- **Explicit Normal Fallback**: Added a user-controlled switch from protected storage to Normal access that waits for permission and changes location only after the fallback is explicitly completed.
- **Reliable Normal Recovery**: Fixed the explicit Normal fallback after Root or Shizuku access is lost so it leaves the protected location and opens local primary storage immediately.
- **Scoped Protected Folder Tools**: Limited folder analysis and cleanup shortcuts to Root and Shizuku locations, with labels that clearly identify the current folder as their scope.
- **Interruption-Safe Storage Recovery**: Preserved coroutine cancellation during archive rollback, cross-backend streams, protected content access, and temporary-file cleanup instead of converting interruptions into ordinary failures.

---

## [1.9.9] - 2026-08-11

- **Protected File Viewing**: Added secure Root and Shizuku previews, playback, thumbnails, PDF viewing and printing, APK inspection, and internal metadata access through expiring path-free content grants.
- **Read-Only External Handoffs**: Added fresh, time-limited grants for opening and sharing protected files with other apps without exposing storage paths or write access.
- **Safe Protected Text Editing**: Added atomic saves for editable protected text files, preserving the original when writing, syncing, or replacement fails.

---

## [1.9.8] - 2026-08-11

- **Explicit Protected Storage Analysis**: Added folder-level Root and Shizuku usage maps and cleaner entry points while keeping protected areas opt-in instead of scanning them automatically.
- **Descriptor-Based Cleaner Verification**: Added bounded protected traversal, large-file detection, duplicate sampling, full-content verification, cycle handling, and partial-result reporting without reopening protected paths locally.
- **Backend-Safe Cleaner Recovery**: Preserved protected file identity in cached cleaner results, Trash operations, undo refreshes, rule changes, and mutation invalidation so identical paths from different backends remain separate.

---

## [1.9.7] - 2026-08-11

- **Protected Archive Workflows**: Added Root and Shizuku browsing, metadata, conflict detection, extraction, and creation for ZIP, TAR-family, and 7z archives while keeping protected paths behind authorized descriptors.
- **Safe Cross-Backend Publication**: Added private compatibility workspaces, recognizable partial outputs, final renames, conflict-safe folder aliases, replacement rollback, and cleanup after failures or interruptions.
- **Persistent Archive Identity**: Kept archive and destination backends through browser history, restored sessions, properties, durable foreground requests, and service restarts instead of retrying protected files as local paths.

---

## [1.9.6] - 2026-08-11

- **Backend-Aware Privileged Trash**: Added same-volume Trash support for Root and Shizuku user-storage files and folders while preserving their backend identity through foreground operations.
- **Safe Move And Restore Recovery**: Stored app-private recovery metadata before each move, reconciled interrupted outcomes, avoided identifier collisions, and restored with conflict-safe renaming or destination selection.
- **Reconnect-Aware Trash**: Kept cached privileged Trash items visible while their backend is unavailable, blocked unsafe restore attempts with clear guidance, and resumed live status after reconnection.

---

## [1.9.5] - 2026-08-11

- **Protected Folder Search**: Added recursive search with existing filters inside the current Root or Shizuku directory, while preserving each result's original backend.
- **Bounded Privileged Scans**: Added depth, entry, result, and duration limits with cycle detection, inaccessible-branch handling, and cancellation-safe traversal.
- **Stable Cached Identity**: Preserved canonical backend identity and capabilities in cached file and usage results, including Unicode, newline, and delimiter-containing paths.

---

## [1.9.4] - 2026-08-11

- **Privileged File Details**: Added backend-aware properties and folder totals for Root and Shizuku files, including partial-access reporting, cycle protection, bounded scans, and identity-safe caching.
- **Reliable Batch Rename**: Kept Root and Shizuku identity through batch staging, rollback, completion, and undo instead of retrying protected files through local storage.
- **Consistent Folder Insights**: Loaded cached folder sizes and background totals while browsing privileged directories, without colliding with identical paths from another access method.

---

## [1.9.3] - 2026-08-11

- **Safe Cross-Backend Transfers**: Added staged descriptor streaming between Normal, Root, and Shizuku storage, including nested folders, conflict choices, partial-output cleanup, and source retention when move cleanup fails.
- **Persistent Privileged Browsing**: Kept Root and Shizuku directory identity through folder navigation, history, and restored browser sessions instead of falling back to local-only access.
- **Backend-Aware File Actions**: Preserved storage identity through foreground copy, move, delete, shred, and fake-file operations so protected locations continue using their selected backend.

---

## [1.9.2] - 2026-08-11

- **Reliable Backend Selection**: Added passive Root-to-Shizuku-to-Normal selection, explicit-mode failure handling, backend death recovery, and generation isolation so an operation cannot switch providers midway.
- **Backend-Aware Storage Routing**: Preserved Root and Shizuku identity in listed files, bounded remote directory pages, and routed retained references and same-backend core operations without treating protected paths as missing local files.
- **Protected Privileged Paths**: Kept system and virtual filesystems read-only, protected storage roots and Arcile private areas, required Root plus an advanced opt-in for other apps' private-data writes, and rejected traversal and unsupported special files.

---

## [1.9.1] - 2026-08-11

- **Secure Privileged Access Core**: Added capability-verified Root and Shizuku service connections with effective-UID checks, bounded directory pages, descriptor-based file access, cancellation, and typed failures.
- **Safe Remote File Operations**: Kept recursive deletion from following symbolic links, rejected traversal and special-file misuse, bounded Binder payloads, and separated selected access preference from the active backend.

## [1.9.0] - 2026-08-09

- **Safe Cross-Storage Moves**: Kept fully verified destination copies when source cleanup only partially succeeds, recorded the remaining cleanup for safe retry, and retained rollback before source deletion begins.
- **Responsive External Viewers**: Opened shared images, PDFs, videos, and audio without blocking the interface on storage providers, with readability checks, timeouts, and retryable failure states.
- **Reliable Large Operations**: Stored bounded bulk-operation requests privately and handed services only a durable ID, preventing oversized selections from exceeding Android transaction limits or running twice.
- **Safe Deep Folder Handling**: Made storage analysis and directory copies cancellable and cycle-aware without recursive stack growth, with bounded partial analysis for unusually large trees.
- **Temporary Share Access**: Released only the document permissions acquired for Save to Arcile after rejection, completion, failure, cancellation, or interrupted-operation cleanup.

## [1.8.9] - 2026-08-09

- **Consistent Customization Ordering**: Added item-specific move controls across Utilities, Quick Access, and Home layout, with immediate Quick Access saves, position feedback, keyboard focus continuity, and Home draft apply/cancel behavior.
- **Responsive Date Filters**: Kept date-range fields and actions reachable in short, landscape, large-text, and keyboard-constrained windows.
- **Localized Audio Favorites**: Made the Audio favorites folder title and search follow the current app language, including live locale changes while the library remains open.
- **Correct Count Grammar**: Fixed singular and plural wording across file, media, storage, backup, cleaner, and progress surfaces, with locale-aware quantity handling.
- **Faster Secure Startup**: Removed recursive plaintext-share cleanup from app startup while still making old compatibility copies inaccessible immediately and deleting them safely in the background.
- **Private Recovery Records**: Moved operation and mutation recovery data out of backups, removed archive passwords from persisted records, bounded retained data, and excluded vault locations from cloud backup and device transfer.
- **Safer Compressed Extraction**: Enforced real expanded-size, compression-ratio, cancellation, and free-space limits for GZIP, BZIP2, and XZ files without leaving partial output.
- **Safer Split-APK Installs**: Bounded split-package staging, extracted only device-compatible APKs into unique temporary folders, and cleaned them after every install or interruption path.
- **Lighter Background Transfers**: Coalesced high-frequency file-operation progress so notifications and durable recovery checkpoints stay useful and exact without repeatedly writing every buffer update.

## [1.8.8] - 2026-08-09

- **Complete Search Overhaul**: Extended the redesigned search pill to Audio, Images, Video Gallery, APKs, Documents, shared category pages, and archive browsing, with separate app-bar-aligned back controls and clearer PDF match navigation.
- **Reliable Filter-Only Results**: Restored filter-only search state, applied filter-sheet changes only after confirmation, included requested hidden MediaStore results, and added accurate empty-state feedback without typed text.
- **Polished Viewer Behavior**: Kept the notification shade and navigation bar hidden during video playback, locked every vault when Arcile is backgrounded or OnlyFiles is exited, safely closed encrypted playback before locking, and reported PDF search failures clearly.
- **Smoother, Consistent Feedback**: Animated Browser tab space while collapsing and standardized the remaining category snackbar presentation.
- **Timely File Operation Feedback**: Showed copy and cut confirmation on the initiating screen, prevented completed operations from resurfacing elsewhere, kept cross-screen file lists current, distinguished queued clipboard items from live progress, and cleared only the clipboard session used by each paste.

## [1.8.7] - 2026-08-09

- **Complete PDF Controls**: Added pinch zoom, highlighted native text search on supported Android versions, a keep-screen-on toggle, and system printing.
- **Reliable Video Playback**: Kept immersive video playback awake, restored hidden system bars, and safely closed encrypted OnlyFiles videos when the device locks.
- **Roomier Browser and Search**: Made Browser tabs slide away while scrolling and return on downward gestures, redesigned every search bar, and enabled filters without typed text.
- **Consistent Feedback**: Routed all 22 activity-level toast fallbacks through one shared presentation and retained the app-wide expressive snackbar treatment for in-app feedback.

## [1.8.6] - 2026-08-09

- **Natural Cleaner Refresh**: Added pull-to-refresh to the cleaner overview and every cleaner page, retained detailed scan progress, and removed refresh buttons and hidden hold gestures.
- **Full Cleaner Pages**: Replaced cleaner bottom sheets with full navigation pages that preserve selections, duplicate comparisons, settings, and return state.
- **Reliable Cleaner State**: Kept refresh feedback continuous, enabled refresh and retry on empty pages, safely revalidated selections while results reload, and updated cache totals after every return path.

## [1.8.5] - 2026-08-08

- **Faster Cleaner Scans**: Added cached, per-category scans with live wavy progress and ETA, optimized for large storage.
- **Reliable Cleaner Results**: Removed deleted and stale files immediately, skipped Android-protected folders quietly, and fixed thumbnail-cache size reporting.
- **Preserved Cleaner Navigation**: Kept the active cleaner, duplicate comparison, selections, and dialogs when returning from previews or folders.

## [1.8.4] - 2026-08-08

- **Configurable Browser Tabs**: Made tabs disabled by default with a Browser overflow toggle; hiding them preserves each Browser page's tab state.
- **Landscape Dual Pane**: Added an optional Appearance setting that shows Browser 1 and Browser 2 side by side at equal width in landscape and returns to the last-focused Browser page in portrait.
- **Refined Browser Tabs**: Added left/right ordering actions, reduced the tab strip's vertical footprint, and added leading spacing before the first tab.

## [1.8.3] - 2026-08-08

- **Independent Browser Tabs**: Added click-controlled tabs inside each existing Browser page, with independent locations, real folder, category, and archive names, protected pinned tabs, and page-specific persistence across launches.

## [1.8.2] - 2026-08-08

- **Custom Home Layout**: Added an Edit Home overflow action with a card dialog for showing, hiding, resetting, and drag-reordering Storage, Categories, Quick Access, Utilities, and Recent Files.
- **Consistent Home Spacing**: Standardized section gutters and vertical spacing, omitted the top title when no header action is needed, and retained clear titles beside Manage, Show All, and See All actions.

## [1.8.1] - 2026-08-08

- **Root Storage Browsing**: Added an opt-in Root Storage shortcut for browsing system paths available through standard Android permissions, without root or Shizuku. This is limited access, not full privileged root storage support.
- **Root Storage Usage**: Added a continuously swipeable Home card with matching layout and a System legend, loaded only when shown. Dashboard reporting remains separate from mounted-storage totals.
- **Protected Location Handling**: Added back navigation outside detected storage volumes, reported denied folders instead of showing misleading empty results, and cached root discovery to avoid repeated permission probes.

## [1.8.0] - 2026-08-02

- **Release Readiness**: Synchronized 1.8.0 versioning, build metadata, task tracking, and user-facing release notes for the v1.7.1 through v1.8.0 development cycle.

## [1.7.9] - 2026-08-02

- **Two Browser Workspaces**: Added a second independently stateful Browser page after the existing Browser, so another swipe from Home provides a separate location, history, selection, search, and scroll position.
- **Preferred Start Page**: Added Home and Browser launch choices to the main overflow menus, using the same selected-default treatment as category pages and applying the choice on the next cold launch without resetting the current session.
- **Preserved Browser Context**: Kept the active Browser workspace when returning from a file viewer, preserved normal parent-folder back navigation in both workspaces, and moved from the second Browser to the first before Home.
- **Per-Folder Scroll Memory**: Restored each folder, category tab, archive location, and large paged listing to its previous list or grid position after navigating away, going back, switching Browser workspaces, or returning to the app.

## [1.7.8] - 2026-08-02

- **Markdown Preview and Editing Tools**: Added formatted Markdown preview, source formatting actions, undo and redo, and document statistics.
- **Arcile Editor Chrome**: Matched Arcile's viewer controls and segmented overflow menu, honored OLED backgrounds, let content scroll beneath the transparent chrome, and separated editor tools from file actions without containing the editing canvas.
- **Consistent Selection Chrome**: Centered selection content and aligned Docs and APK split actions, overflow controls, and screen-edge spacing with the browser and other categories.

## [1.7.7] - 2026-08-02

- **Native Text and Markdown Editing**: Added a standalone source editor activity for Arcile and other apps, with read-only handling for view-only grants.
- **Document Saving and Draft Recovery**: Verified saved content before reporting success, preserved newer edits made during a save, restored matching unsaved drafts after interruption, and saved pending changes before sharing or opening elsewhere.

## [1.7.6] - 2026-07-30

- **Reliable Player Motion**: Fixed mini-to-full and full-to-mini transitions for taps and swipes, added swipe-up expansion and swipe-down dismissal, and kept the mini player open during back gestures.
- **Stable Playback Surfaces**: Kept mini-player artwork stable while tracks change and added a close action to playback notifications.
- **Finished File Operations**: Removed progress notifications after file and vault operations complete or are cancelled, including concurrent operations and stale progress updates.

## [1.7.5] - 2026-07-29

- **Completed Seven-Category Parity**: Finished Gallery-shell integration for APKs and the remaining category routes, removed startup and folder-content flashes, matched selection and folder predictive-back motion, and kept Archives unchanged.
- **Clean Stable Thumbnails**: Kept circular thumbnails exclusive to lists, removed APK icon background layers, made PDF previews cover document folder tiles, and added size-aware thumbnail and metadata caching to prevent reloads.
- **One Global Audio Player**: Unified mini and full playback in one exported player that opens as a touch-through floating mini-player, expands and collapses from its real bounds without refreshing, and works for audio opened by other apps.
- **Focused Category UI**: Removed the redundant containing-folder audio action and non-selection folder overflow controls, kept folders grid-only, and prevented Settings toggles from changing segmented-row shapes.

## [1.7.4] - 2026-07-29

- **Documents And Native PDF Viewing**: Added a dedicated Documents library with Gallery-style files and folder navigation, stable document thumbnails, and native multi-page PDF rendering with navigation and bounded zoom.
- **Independent Category State**: Persisted category presentation, grouping, item and folder preferences independently, introduced stable category IDs and names, and removed the hidden Models category while leaving Archives unchanged.

## [1.7.3] - 2026-07-29

- **Reusable Category Browser**: Added the common file-and-folder category surface with Gallery-matched list and grid layouts, date grouping, fast scrolling, search, refresh, selection, predictive back, and transfer progress.
- **Consistent Category Actions**: Unified copy, cut, paste, rename, delete or shred, ZIP, share, properties, and Open With workflows while keeping circular previews list-only and folders as Gallery-style grids.

## [1.7.2] - 2026-07-29

- **Gallery-Parity Audio Library**: Aligned Audio list and grid spacing, date sections, floating chrome, selection actions, folder navigation, and predictive back behavior with the shared Gallery shell.
- **Audio-Specific Presentation**: Kept circular artwork in lists, rectangular artwork cards in grids, rich artist, album, duration, and size metadata, grid-only folder browsing, and independent Favorites, pinned folders, and custom covers.

## [1.7.1] - 2026-07-28

- **Shared Gallery Shell**: Extracted the Image Gallery 1.6.1 floating chrome, list and grid spacing, section headers, selection motion, and bottom navigation into reusable category UI.
- **Gallery Reference Migration**: Moved Images and Videos onto the shared implementation without changing their established behavior, making Gallery the parity reference for the remaining categories.

## [1.7.0] - 2026-07-26

- **Refined Audio Player**: Rebuilt the mini and full player as one continuous animated surface with shared artwork motion, a persistent mini progress rail, smoother wavy seeking, balanced transport controls, adaptive spacing, a centered now-playing header, and direct queue access.
- **Release Readiness**: Synchronized 1.7.0 metadata and release-facing documentation, published user-focused notes for the complete 1.6.1–1.7.0 development range, and refreshed production build guidance.

## [1.6.9] - 2026-07-26

- **Consistent Media Categories**: Aligned Audio with the Image and Video galleries through loading and error feedback, fast scrolling, complete selection actions, delete and properties dialogs, ZIP creation, clipboard inspection, and direct paste controls on folder tiles.

## [1.6.8] - 2026-07-26

- **Audio Player and App Integration**: Added the Audio category route, background playback, Arcile-branded system media controls, animated mini and full-player transitions, wavy seeking, repeat and shuffle controls, swipe navigation, and an editable playback queue.

## [1.6.7] - 2026-07-26

- **Audio Library Workflows**: Added selection-aware Audio navigation with copy, cut, paste, rename, share, Open With, containing-folder access, and surfaced mini-player and selection controls.

## [1.6.6] - 2026-07-26

- **Audio Browsing Interface**: Added adaptive Audio and Folders pages with compact lists, artwork-first grids, latest folder art, search, sorting, grouping, sizing, and clipboard progress controls.

## [1.6.5] - 2026-07-26

- **Audio Library Foundation**: Added device-audio discovery, independent Audio preferences, artwork and grouping models, saved list and grid presentation options, and focused preference and presentation coverage.

## [1.6.4] - 2026-07-26

- **Independent Categories and Browser Restore**: Kept category screens separate from the swipeable Browser and restored the Browser's last opened folder across launches.
- **Complete File Actions**: Made thumbnails open files and filenames reveal their containing folders, prompt with Open With for unsupported formats, keep external-only preferences from reopening Arcile, preserve per-type behavior, and repaired folder sharing with ZIP packaging.
- **Reliable Batch Deletion**: Added live item counts, accurate current-file details, determinate progress from the first item, and durable completion feedback to Browser and OnlyFiles batch deletes.
- **Expressive, Adaptive UI**: Standardized modal cards for compact phones, rotation, and larger displays; rebuilt onboarding controls as compact Material 3 Expressive groups; and refined OnlyFiles feedback and predictive back.
- **Cleaner Reliability**: Refreshed cleaner detail lists with aligned actions, immediate single-tap ignores, clearer ignored-item management and scoped exclusion rules, accurate thumbnail-cache size, and improved storage summaries without changing the cleaner landing page.
- **Safer Archives and Complete Backups**: Continued extraction while securely skipping unsafe entries, retained archive-wide safety limits, verified Power Rename history backup, and added OnlyFiles preferences while excluding transient caches and operation state.
- **Project Website**: Added compact, icon-led repository and download cards with total and latest release counts, animated live counters, and reduced-motion support.

## [1.6.3] - 2026-07-25

- **PowerRename**: Added batch search and replace, Regex, case changes, numbering, live conflict previews, Undo, safe rollback, and Arcile-styled history with per-item removal and preset menus.
- **Interface Polish**: Improved dropdown contrast and elevation across dialogs and cards.

## [1.6.2] - 2026-07-25

- **Interactive Video Viewer**: Added gesture controls, metadata, auto-hiding playback controls, drag-to-dismiss, resize modes, configuration-safe playback, and a stable cached thumbnail strip.
- **APK and Split Install Support**: Added secure installation for APKs and complete compatible split sets from `.apks`, `.xapk`, `.apkm`, or multi-selection, with metadata previews, downgrade guidance, progress, cancellation cleanup, and reliable status handling.
- **Fast Scrollbar Enhancements**: Synchronized real-time scrollbar drag positions across browser and gallery views, expanded thumb reachability region to 48dp with intentional drag-to-wake activation to eliminate accidental triggers, and added responsive touch feedback.
- **OnlyFiles In-Module Settings**: Relocated vault security options, thumbnail cache management, grant revocations, and security disclosures directly into the OnlyFiles module app bar and vault headers.
- **Official System Notifications**: Standardized background file operations, vault imports, and active external grants with Arcile's official monochrome brand icon, tap-to-open content intents, progress notification categories, and dedicated action drawables.
- **Interface Polish**: Updated plugins supporting description in Settings.


## [1.6.1] - 2026-07-23

- **Expressive Controls**: Unified dropdown styling, standardized split actions and standalone overflow controls at `48.dp`, brought Settings, Quick Access, About, and Licenses lists onto Material 3 Expressive segmented groups, and updated Browser overflow toggle items to use active container highlights and dynamic leading state icons instead of redundant trailing checkmarks.
- **Expressive Accent Color Picker**: Redesigned the Accent Color Selector and Bottom Sheet with Material Design 3 Expressive UI/UX features, including interactive live component theme previews, spring-animated shape morphing swatches, dynamic halo borders, and categorized color palettes.
- **Plugins Empty State**: Replaced unavailable “Coming soon” plugin rows with a focused empty state until plugins are installed or available.

## [1.6.0] - 2026-07-23

- **Storage Usage Map**: Added capacity-aware rings, complete folder accounting, instant cached drill-down, theme-adaptive colors, and clearer touch interactions.
- **Trash Experience**: Improved previews and item actions, added confirmed one-item restore, and retained safe recovery when original metadata is unavailable.
- **Browser Reliability**: Prevented category and breadcrumb crashes with very large folder sets, polished the folder selector, and added a hidden-files toggle to the Browser menu.
- **Interface Polish**: Unified expressive loading states and aligned video thumbnail navigation with the image viewer.


## [1.5.9] - 2026-07-22

- **Immersive Video Viewer**: Matched the Image Viewer with sibling paging, thumbnail navigation, swipe-up properties, predictive back, favorites, selection, sharing, and deletion controls; stabilized thumbnail playback with one lifecycle-aware player, reliable page/seek state, and conflict-free gestures.
- **Video Gallery Category**: Replaced the generic Videos category list with Arcile's gallery experience, including Videos/Albums tabs, timeline grouping, search, view and sort controls, selection actions, video thumbnails, and play affordances.
- **Large Video Gallery Launch Fix**: Prevented video opens from freezing on large galleries by indexing playback sources once, preparing only the selected video, and loading sibling media lazily as the viewer page changes.
- **Video Viewer Edge-Case Reliability**: Preserved gallery selection and return position, kept paused videos paused and the progress bar stable while paging, restored replay and fit/zoom/fill controls, removed deleted pages immediately, and made Trash/vault playback safely read-only with source-aware share and open actions.
- **Gallery Resource Hardening**: Reduced image gesture and video progress-update overhead, bounded viewer and gallery caches, reused image metadata, and lowered retained thumbnail memory for smoother large-library browsing.
- **OnlyFiles Responsiveness**: Moved biometric enrollment checks out of composable UI filesystem work and split the vault screen into focused components without changing its workflow.

## [1.5.8] - 2026-07-19

- **Direct Biometric Unlock on Cards**: Locked vault list cards enrolled with biometrics now display an interactive primary-colored fingerprint icon. Tapping the fingerprint directly invokes the biometric authentication dialog to unlock the vault, skipping the password prompt.
- **Biometric Prompt Context Visibility**: Modified vault unlock dialogs to dynamically query local enrollment status, completely hiding the "Use Biometrics" trigger option if biometrics have not been configured for the vault.
- **Expressive Loading and Progress Controls**: Replaced legacy flat linear and circular progress loaders with premium Material 3 Expressive shape-morphing `LoadingIndicator` components. Cleaned up empty bottom bar layout slots in `OnlyFilesScreen` to prevent reserved spaces and divider lines when clipboard or transfer states are null.
- **Precise Back Gesture Handling**: Refined the `BackHandler` logic to capture back navigation at the root folder level inside vaults, cleanly deselecting and closing the vault session to return to the library screen instead of popping the entire OnlyFiles navigation stack and returning to the home screen.

## [1.5.7] - 2026-07-15

- **Native Vault Transfers**: File/folder import and export or move-out now use Arcile's own storage browsers end to end; the obsolete parallel document-tree exporter has been removed.
- **Arcile-Aligned OnlyFiles UI**: The vault library and mounted browser now use Arcile scaffolding, large/search/selection app bars, file layouts, spacing, shapes, and clearer locked/open actions instead of a separate visual language. This includes deep integration of Material 3 Expressive UI/UX elements, such as bouncy circular action wrappers, position-aware grouped dropdown items, bottom sheet sort controls, custom segmented rows, expressive switches, and transparent overlay media bars. A consolidated conditional back-gestures handler resolves back-stack popping issues to prevent premature exits to the home screen.
- **Useful Utilities Only**: Utilities now lists only implemented destinations—Trash, Cleaner, Activity Log, and OnlyFiles—with placeholder FTP, App Manager, and Network Share entries removed.
- **Custom Home Utilities**: Users can choose which implemented utilities appear on Home and reorder them, with ordered preference migration and stale-entry cleanup.
- **Deep Transfer Reliability**: Native export, merge, cancellation, and move-out handle deeply nested folders without recursive stack growth, clean private staging data on failure, and remove encrypted sources only after publication.
- **Large Vault Confidence**: Authenticated directory paging is now stress-verified with 10,000 siblings in one folder and 100,000 entries across a vault while retaining the 256-entry page bound.

## [1.5.6] - 2026-07-14

- **Protected Vault Presentation**: OnlyFiles blocks capture by default, supports encrypted revision-keyed image and video thumbnails, clears decrypted previews on lock, and provides global controls for screen protection and thumbnail-cache clearing.
- **Complete Vault Administration**: Per-vault actions now cover password changes, strong-biometric enrollment or removal with password fallback, quick and full health checks, portable registration removal, locking, and explicitly confirmed permanent deletion.
- **Verified Plaintext Boundaries**: Guarded export and move-out stage complete document-tree results, verify file contents, publish conflict-safe replacements, and delete encrypted sources only after success.
- **Compatibility and Disclosure**: Apps that cannot stream can receive a separately confirmed, space-checked private plaintext copy with UID binding and automatic cleanup; Settings and Arcile documentation now explain the unaudited security model and recovery limits.

## [1.5.5] - 2026-07-14

- **Daily Vault Browsing**: OnlyFiles now uses stable opaque item identities with paged list/grid browsing, sorting, current-folder or recursive search, range and bulk selection, properties, empty-file creation, and permanent multi-delete.
- **Vault Clipboard**: Memory-only copy and move supports same-vault and cross-vault destinations, cancellation, progress, and keep-both, replace, skip, or directory-merge conflict decisions.
- **Global Video Player**: Browser, Recents, Trash, external opening, and OnlyFiles now share an immersive queue player with lifecycle-safe position retention, seeking, tracks, subtitles, speed, repeat, resize, buffering, completion, and retry states without path-bearing routes.
- **Controlled Media Access**: Vault images use bounds-first sampled decoding, while single-consumer external grants provide seekable encrypted streams, persistent revocation controls, 30-second post-close revocation, and a 12-hour maximum.

## [1.5.4] - 2026-07-14

- **Reliable Portable Vaults**: Registrations now follow stable storage-volume identities across remount paths, preserve missing removable vaults, and detect missing, damaged, or replaced folders without recreating them.
- **Complete Vault Transfers**: Same-vault and cross-vault copy and move operations are serialized safely, commit each selected item atomically, support replace, keep-both, skip, and directory-merge conflicts, and roll back cancelled work.
- **Stronger Session Security**: Password strength confirmation, atomic password rotation, Android Keystore-backed biometric challenges, and independent transfer and external-access sessions keep foreground locking immediate.
- **Private External Access**: Share and Open With grants use opaque expiring read-only streams directly from encrypted objects, survive an interactive lock, support revocation, and create no plaintext staging files.

## [1.5.3] - 2026-07-14

- **Atomic Vault Changes**: OnlyFiles mutations and imports now commit through recoverable generation transactions, so interruption exposes either the complete previous state or the complete replacement.
- **Scalable Encrypted Browsing**: Vault folders now use stable opaque IDs and paged per-directory metadata instead of a global path index, with normalized case-insensitive names, sorting, recursive search, and empty-file creation.
- **Independent Transfer Leases**: Started imports keep a bounded operation key lease while foreground vault access locks immediately and closes interactive readers.
- **Vault Health Checks**: Quick and full verification now detect damaged headers, manifests, references, file chunks, pending transactions, missing objects, and safely cleanable encrypted orphans.

## [1.5.2] - 2026-07-14

- **Hardened Vault Format**: OnlyFiles now uses bounded Argon2id password protection, separated encryption domains, redundant authenticated headers, crash-safe password-envelope replacement, immutable 256 KiB chunked objects, and paged encrypted directory metadata.
- **Portable Vault Foundations**: Vault locations now use opaque volume-relative identities and encrypted media can be read through bounded random access without whole-file plaintext copies.
- **Unified Video Opening**: Browser, Recents, Trash, and external video intents now enter the same Arcile video-viewing flow.
- **Stable Recent Previews**: Cached Home carousel thumbnails remain visible when cards leave and re-enter the viewport.

## [1.5.1] - 2026-07-14

- **OnlyFiles Vaults**: Create and independently unlock multiple encrypted vaults in private app storage, with visible vault names and automatic locking whenever Arcile leaves the foreground.
- **Encrypted Browser**: Browse, create, rename, and delete folders and files entirely inside a vault, with authenticated encrypted names, hierarchy, metadata, and file content.
- **Private Imports**: Copy files or complete folders into a vault through a cancellable foreground import that removes incomplete data and locks its operation-only session when finished.
- **Secure Media**: View encrypted images and seek through large encrypted videos without decrypting whole files to disk or memory.

## [1.5.0] - 2026-07-13

- **Metadata Editing Reliability**: Image metadata editing is now offered only for supported, writable local images and safely remains unavailable for missing, read-only, or external content.
- **Operation Path Reliability**: Archive and Browser progress surfaces now display file names consistently for both Unix- and Windows-style storage paths.
- **Developer Guidance**: Development documentation now reflects the current modular architecture, feature ownership, release metadata, and focused verification workflow.
- **Plugin Distribution**: Removed the bundled Arcile GLB Viewer application and its install catalog entry while retaining generic plugin discovery, compatibility checks, and file handoff support.
- **Project Documentation**: README, website, development guidance, and release notes now reflect Arcile 1.5.0, its current feature set, modular structure, and focused verification workflow.
- **Navigation & Feedback Reliability**: Feedback stays on its originating screen, and the system back gesture exits normally from Home instead of being intercepted by the hidden Browser page.
- **Home Thumbnail Quality**: Recent image cards now use Gallery-aligned content sources, rendered-size requests, and cache variants so their previews remain sharp on high-density screens.
- **Gallery Viewer**: Opening an image now keeps that image anchored while neighboring images load, preserves Gallery selection through viewer actions and deletion, supplies the complete thumbnail carousel, and uses the standard viewer route transition without image morphing.
- **File Actions & Sharing**: Trash images now open with neighboring items in Arcile's read-only viewer, Trash files expose only applicable actions, and Android multi-file sharing either delivers the complete selection with correct grants or safely sends nothing.

## [1.4.9] - 2026-07-13

- **Preference Reliability**: Browser, Recents, Gallery, and save-destination settings now update through independent stores while retaining all existing saved values and backup compatibility.
- **Search Reliability**: Browser, Recents, and Trash now prevent stale searches from replacing newer results and keep search failures separate from unrelated loading errors.
- **File Operation Reliability**: Concurrent Browser workflows keep independent progress and feedback, while cancelled storage work cleans up staged data before stopping.
- **Destination Reliability**: Save-to-Arcile and archive workflows now validate storage-owned paths throughout navigation, preserve access to readable folders, and prevent writes to unavailable or read-only destinations.
- **Cache Control Reliability**: Settings staging and Storage Cleaner thumbnail caches now load and clear through independent owners, reject duplicate actions, and report failures without blocking unrelated state.

## [1.4.8] - 2026-07-12

- **Upgrade Reliability**: Shared thumbnail loading and feature test wiring now remain owned by their correct modules, with automated package-location checks preventing release-only source drift.

## [1.4.7] - 2026-07-12

- **File Interface Reliability**: Grid previews, range selection, Trash loading/empty states, and Activity Log labels now remain independently consistent.
- **Quick Access Organization**: Custom paths are normalized before saving and every shortcut appears in exactly one appropriate management section.

## [1.4.6] - 2026-07-12

- **Model Viewer Reliability**: GLB loading, cancellation, back navigation, viewer controls, and top/bottom overlays now retain consistent state through viewer changes.
- **Storage Cleaner Reliability**: Duplicate groups handle empty and directory entries safely, while risk summaries and displayed storage paths remain accurate.

## [1.4.5] - 2026-07-11

- **Startup Stability**: Restored the generated authorization bridge required by Browser so the app no longer crashes while composing its initial screen.
- **Shared Import Reliability**: Save-to-Arcile now validates incoming shares and destinations through isolated workflows, safely restores its selected folder, and prevents duplicate saves.
- **Quick Access Reliability**: Folder selection and restricted Files shortcuts now use route-owned Android access with persisted permissions and normalized destinations.

## [1.4.4] - 2026-07-11

- **File Authorization Reliability**: System file confirmations now survive lifecycle changes, reject stale results, and recover cleanly when denied or unavailable.
- **Settings Reliability**: Backup pickers and external-cache cleanup now use route-owned workflows with duplicate-action protection and accurate remaining-cache status.

## [1.4.3] - 2026-07-11

- **File Workflow Reliability**: Isolated file-list rendering and focused archive, cleaner, storage-usage, activity-log, conflict, and filesystem responsibilities for safer independent updates.

## [1.4.2] - 2026-07-11

- **Navigation Reliability**: Unified file opening, sharing, plugin prompts, Browser entry, and viewer-return handling across app destinations.

## [1.4.1] - 2026-07-11

- **Startup Reliability**: Browser restoration now begins only after entering Browser, with dedicated loading and retry UI instead of restoring behind Home.

## [1.4.0] - 2026-07-10

- **Architecture Reliability**: Completed feature ownership for browsing, Home, galleries, storage tools, settings, recents, trash, onboarding, archives, and plugin viewers.
- **Storage Reliability**: Split storage browsing, mutations, media, volumes, and trash into focused repositories with clearer runtime boundaries.
- **Interface Consistency**: Consolidated shared workflow feedback, viewer navigation, theming, and reusable file-management presentation.

## [1.3.9] - 2026-07-05

- **File Workflow Reliability**: Consolidated creation, rename, deletion, properties, clipboard, and conflict dialogs into shared UI ownership.

## [1.3.8] - 2026-07-05

- **Search Filter Reliability**: Consolidated search filters, date-range selection, localized date formatting, presets, and expressive filter controls into shared UI ownership.

## [1.3.7] - 2026-07-05

- **File List Reliability**: Consolidated list, grid, volume, filter, row presentation, and accessibility behavior into bounded shared file-browsing components.

## [1.3.6] - 2026-07-05

- **Media Browsing Reliability**: Consolidated thumbnail sizing, loading and failure state, image metadata presentation, and fast-scroll behavior into shared UI components.

## [1.3.5] - 2026-07-05

- **Theme Reliability**: Consolidated theme state, colors, typography, shapes, spacing, motion, and category styling under the shared UI layer with direct preference coverage.

## [1.3.4] - 2026-07-05

- **Shared Workflow Reliability**: Consolidated selection properties, clipboard state, debounced search, file formatting, folder tabs, and operation feedback into focused shared presentation components.

## [1.3.3] - 2026-07-05

- **Storage Path Reliability**: Isolated filesystem listing, file transfer, secure deletion, synthetic-file creation, MediaStore queries, volume selection, and archive destination handling for more predictable storage operations.

## [1.3.2] - 2026-07-05

- **Storage Reliability**: Added focused storage capabilities for browsing, file changes, archives, media, trash, volumes, preferences, and authorization so each workflow can depend only on the operations it needs.

## [1.3.1] - 2026-07-02

- **Browser Navigation Reliability**: Browser now owns its navigation state, scroll restoration, file actions, and back handling while Home-to-Browser requests use explicit folder, category, archive, and restore targets.
- **Onboarding and Browser Search Reliability**: Onboarding now owns permission and backup restoration state, while Browser search uses an independently owned, debounced state controller.

## [1.3.0] - 2026-07-02

- **Settings and Import Reliability**: Isolated settings, backup, activity history, plugins, external file access, and shared-file imports so these workflows no longer depend on app-shell state ownership.

## [1.2.9] - 2026-07-01

- **Home and Storage Reliability**: Isolated Home, storage dashboard, and storage management state so loading, refreshes, file opening, and volume classification no longer depend on app-shell state ownership.

## [1.2.8] - 2026-07-01

- **Background Operation Reliability**: Isolated background file operations, shared-file imports, and interrupted-operation recovery with dedicated coverage while preserving import safeguards.
- **File Opening Reliability**: Centralized plugin, archive, image, and external file resolution with direct coverage for viewer context and unsupported archives.

## [1.2.7] - 2026-06-29

- **Reliable App Relaunch**: Cold launcher starts now open Home instead of briefly restoring a previous utility screen, while Browser still restores its last location when opened and configuration changes retain the active screen.
- **Architecture Groundwork**: Renamed shared file-listing presentation contracts away from Browser-specific terms and added ratcheting size guardrails while preserving existing preferences.

## [1.2.6] - 2026-06-28

- **Optional Plugin System**: Moved GLB rendering into an independently versioned Arcile GLB Viewer APK, added signed intent-based discovery, compatibility checks, consistent file routing, shared viewer UI, missing/update guidance, and plugin management while removing SceneView and Filament from the main APK.

## [1.2.5] - 2026-06-28

- **Image Metadata and Viewer Controls**: Added editing and clearing for descriptive, camera, date, time, and GPS metadata; improved metadata refreshes, date/time dialogs, sheet transitions, and touch-anchored pinch and double-tap zoom.
- **Date and Size Filtering**: Added date-range and file-size presets, custom date selection, MB-based minimum/maximum size inputs, and reliable application and clearing of active filters.
- **Recents and Fast Scrolling**: Improved calendar-day grouping and year-aware labels, corrected staggered-grid fast scrolling, refined scrollbar tooltips, and prevented hidden overlays from intercepting touches.
- **Home Performance**: Displayed recent files and storage volumes sooner, handled overlapping refreshes reliably, and reduced repeated storage animation work.
- **File Format Documentation**: Documented recognized formats, built-in capabilities, metadata-writing support, and features requiring Android codecs or external apps.

## [1.2.4] - 2026-06-23

- **Image Viewer Navigation and Selection Fixes**: Preserved Browser breadcrumbs, active selection, and far-scroll position when returning from Browser-opened images by routing viewer back gestures through the Browser-return path, suppressing Browser auto-restore reloads after viewer pops, and avoiding same-folder list clearing during refreshes; removed user-visible image viewer zoom-in and zoom-out caps with improved double-tap targeting; allowed Browser image thumbnails to open the viewer during selection mode while the rest of the row or card keeps toggling selection; and added visible Gallery photo open icons during selection mode.
- **Browser Viewer Back Stack Hardening**: Prevented Browser page restores after returning from the image viewer from clearing active folder history, and skipped redundant persistent-location restores when the Browser is already showing a real folder/category/archive/root location so Back from a viewed image returns to the containing folder before Home.
- **Browser Folder Scroll Restoration**: Preserved Browser list and grid scroll positions by folder/category/archive location after opening images or other files, kept the Main pager from recreating on Home during viewer returns, and explicitly reveals the opened file when returning.
- **Settings Toggle Shape Simplification**: Replaced the custom lobed Settings toggle thumb with a simple circular thumb while keeping the existing animated check and close indicators.

## [1.2.3] - 2026-06-23

- **Model Viewer Implementation**: Added GLB/model file recognition, in-app and standalone model viewer routing, SceneView/Filament rendering support, viewer metadata and share/open-with actions, image-viewer-aligned chrome, zoom and brightness controls, and Theme/White/Black background choices.

## [1.2.2] - 2026-06-22

- **Quick Access Default Fix**: Kept WhatsApp available in Manage Quick Access while no longer pinning it to Home by default for fresh preference state.
- **Thumbnail Cache Cleaner Accuracy**: Measured thumbnail disk usage from Coil's active disk cache directory and recalculated stats after clearing so the cleaner reliably reports `0B` when cache data is gone.
- **Gallery Viewer Return Position**: Returned Gallery to the last image viewed in the image viewer, including grouped gallery layouts with section headers.
- **Browser and Gallery Scrollbar Controls**: Added separate Settings toggles to disable Browser and Gallery scrollbar overlays entirely.
- **Fast Scrollbar Expressive Polish**: Shared the scrollbar overlay between Browser and Gallery, showing a normal rounded thumb during regular scrolling and an animated 10-pointed Material Expressive "Lobate" (wavy) shape thumb (rotating dynamically as a function of scroll progress) during direct scrollbar fast-scrolling.
- **View Switcher Expressive Design**: Updated the view switchers in bottom sheets (such as Search Filters, Gallery Options, and Sort Options) to a custom morphing expressive segmented row design, animating selected segment corners to a capsule shape (20dp radius) while retaining rounded rectangle corners (12dp radius) on unselected segments, and animating a checkmark icon to slide in upon selection.
- **Settings Expressive Toggles**: Replaced all standard preference switches in the Settings screen with a custom spring-animated `ExpressiveSwitch` containing a morphing 7-sided cookie thumb and scaling state icons.
- **Settings Elastic Inputs**: Refreshed Custom Theme hex input fields in the Settings screen to utilize focus-driven elastic spring scaling (1.03x pop-out animation upon receiving input focus).
- **Release String Gate Cleanup**: Localized search filter date labels and clear actions for production string validation.

## [1.2.1] - 2026-06-21

- **Material 3 Expressive UI Pass**: Updated dialogs, sheets, toolbar actions, chips, list rows, and cleaner/gallery controls with rounded expressive styling, tactile feedback, and clipped touch states.
- **Search and File Dialog Polish**: Improved search filter controls and refreshed file/archive creation dialogs with clearer expressive inputs and actions.
- **Gallery and Viewer Polish**: Converted gallery Search, Sort, and More into split actions, refined image viewer bottom chrome, and improved thumbnail strip behavior.
- **Gallery Tab Touch Polish**: Clipped Photos and Albums bottom tab hover states to their pill shapes.
- **Browser Top Bar Touch Polish**: Clipped Browser top-bar hover and touch states to their circular and split-button shapes.
- **Dynamic Toast Polish**: Made in-app feedback toasts/snackbars content-sized so short messages no longer stretch across the screen.
- **Storage Touch Polish**: Replaced storage breadcrumbs and classification chips with expressive rounded surfaces and shaped storage usage actions.
- **Archive Viewer Touch Polish**: Clipped archive viewer toolbar and list-row touch states to their circular and rounded row shapes.
- **Trash Touch Polish**: Shaped Trash top-bar actions, empty-trash controls, restore destination rows, and dialog actions to match their rounded UI surfaces.
- **Home and App Toolbar Touch Polish**: Rounded Home header actions and clipped About, Licenses, and Activity toolbar buttons to circular touch states.
- **Shared Dialog Touch Polish**: Shaped shared sort, search filter, and clipboard dialog actions with expressive sheets, rounded buttons, and clipped icon targets.
- **Gallery Surface Shape Polish**: Aligned gallery photo and album touch surfaces with shared expressive shape tokens.
- **Archive and Dialog Touch Polish**: Replaced archive chips and shared create/rename dismiss actions with expressive rounded touch surfaces.
- **Onboarding Chip Polish**: Replaced remaining standard onboarding chips with expressive rounded chip surfaces.
- **Quick Access and Cleaner Polish**: Refined Quick Access management controls and Storage Cleaner duplicate/detail interactions with consistent expressive surfaces.
- **Top Bar and Import Touch Polish**: Clipped Quick Access, Recent Files, Storage Cleaner, gallery, standalone image viewer, and Save to Arcile action states to their visible rounded shapes.
- **Remaining Component Shape Polish**: Removed stale standard chip references and rounded additional archive, browser transfer, gallery options, storage dashboard, and cleaner dialog controls.
- **Dropdown Menu Touch Polish**: Routed app overflow and picker menu rows through a rounded dropdown item wrapper so hover and ripple states match expressive menu shapes.
- **List and Breadcrumb Touch Polish**: Clipped volume root rows, archive encoding choices, Tools shortcuts, Settings navigation, and breadcrumb segments to rounded touch-state bounds.

## [1.2.0] - 2026-06-21

- **Image Viewer Thumbnail Performance**: Made the bottom thumbnail strip jump directly to far-opened gallery items, animate only nearby page changes, use stable path keys, keep thumbnail layout dimensions stable, and disable strip crossfade so deep gallery opens no longer visibly rearrange before settling.
- **Navigation Back Stack Fixes**: Preserved route origins for Browser handoffs from dashboards, recent files, cleaner, quick access, and archive viewer so Back returns to the screen the user came from after Browser consumes its own folder/archive back stack.
- **Storage Cleaner Back Handling**: Added local back priority so Cleaner dismisses ignored-items, delete confirmation, and details UI before leaving the Cleaner route.

## [1.1.9] - 2026-06-21

- **Segmented Quick Access Groups & Toggle Symbols**: Reorganized the Manage Quick Access screen to display folders in sequentially ordered, visually segmented stacked cards (System Folders, App Folders, Files App Section, and Custom Folders) with thin dividers between items, and added checkmark and close symbols inside the Show on Home toggle switches.
- **Image Viewer Selected Thumbnail Polish**: Enhanced the bottom thumbnail strip in the image viewer to animate the selected thumbnail's width (28dp to 36dp), height (42dp to 54dp), and shadow elevation (0dp to 6dp) with spring physics and z-indexing to draw it on top of neighboring thumbnails.
- **Home "Arcile" Shortcut**: Updated the Home screen Quick Access "All" shortcut's label text from "All" to "Arcile", and made it load and display the actual launcher icon of the Arcile app dynamically, ensuring compatibility with all build variants (including debug builds).
- **Quick Access Reordering & Switch Haptics**: Added a fullscreen drag-to-reorder dialog for enabled quick access shortcuts (including the "Arcile" shortcut) with viewport auto-scrolling, root-coordinate gesture tracking to prevent touch sticking, mechanical tactile haptic ticks, and a prominent filled Apply button in the TopAppBar. Enabled keyboard-tap haptics on the show-on-home switch toggles.
- **Storage Cleaner Layout Polish**: Fixed a UI issue where the "Ignore" text button next to risk reasons in the storage cleaner candidate rows and duplicate rows would wrap vertically when risk reasons labels were long. Weighted the RiskSummary component and reasons text so that long reasons truncate with an ellipsis, allowing the Ignore button to retain its full preferred width.

## [1.1.8] - 2026-06-21

- **Standalone Image Viewer & Metadata Reuse**: Added an exported image viewer activity running in a separate process for external `image/*` opens, moved viewer Delete into the bottom split actions, moved Info into the overflow menu, and reused full image metadata in single-image Properties cards.
- **Image Viewer Layout Polish**: Expanded the viewer top metadata pill to use the space beside Back and compacted image Properties into one combined information layout with tighter dialog padding and no duplicate file metadata rows.
- **Gallery Viewer Selection Fix**: Scoped restored viewer position to the active opened image so tapping a gallery item no longer reuses a stale/random previous page, reset viewer chrome for new image opens, and ordered viewer metadata as resolution, size, then date/time.
- **Image Viewer Space & Strip Polish**: Removed the top viewer back arrow to give metadata the full overlay width, centered the active thumbnail in the viewer strip instead of pinning it to the start, and applied the marquee filename setting to viewer metadata pills.

## [1.1.7] - 2026-06-20

- **System Predictive Back & Expressive Motion**: Enabled Android 14+ system predictive back gestures, custom spring horizontal page scroll animations, and bouncy touch scaling animations with haptic feedback across all main clickable cards, buttons, lists, split button groups, and toolbar navigation actions.
- **Refresh Coverage Fixes**: Extended pull-to-refresh across Browser search/loading/empty states, Gallery albums, and Trash lists and empty states, kept the shared refresh indicator above page content, and added regression coverage so completed paste operations refresh initially empty Browser folders immediately.

## [1.1.6] - 2026-06-19

- **Image Viewer Info Details**: Added dynamic viewer position, filename, date/time, and resolution details to the image viewer overlay, and expanded the metadata sheet with title, date, resolution, size, URI, path, MIME type, and extension rows.
- **Image Viewer Metadata Freshness**: Kept viewer metadata fresh after metadata erase/refresh events and restored EXIF date-taken display in the details sheet.
- **Browser Folder Listing Freshness**: Made Browser directory listings read live filesystem children instead of trusting partial media cache rows, preventing Gallery image cache entries from hiding folders in locations such as Pictures.
- **Browser Folder Stats Cache Display**: Kept cached folder stats visible while a background refresh is queued so folder rows can show the previous size/count immediately and then update when fresh stats arrive.
- **Browser Video Thumbnails**: Restored video thumbnails for raw browser file paths by falling back to `MediaMetadataRetriever` when a MediaStore thumbnail URI is unavailable, and expanded common video extension recognition across browser icons, categories, cleaner scans, and thumbnail eligibility.
- **Image Viewer State Restoration**: Made the image viewer collect gallery state with lifecycle awareness and persist viewer chrome, metadata sheet, current image, rotation, and erase-dialog state through activity recreation.
- **Gallery Clipboard UX**: Added Browser-aligned clipboard management in Gallery with a persistent clipboard/progress pill, queued item inspection and removal, paste/cancel controls for real albums, and foreground operation status feedback.
- **Gallery Cache Completeness**: Prevented Browser directory-listing cache rows from seeding partial Gallery image snapshots, so Gallery waits for the MediaStore-backed catalog instead of briefly showing only direct Pictures-folder images.
- **Release Regression Gates**: Added focused coverage for viewer saved-state restoration, Save-to-Arcile import checkpoints, and repeated archive extraction state isolation.
- **Storage Documentation Sync**: Reconciled release docs with Room cache schema version 2, cache-backed external handoff providers, and v1.1.6 release metadata.

## [1.1.5] - 2026-06-18

- **Live Media Refresh**: Wired MediaStore multi-URI change callbacks into shared storage invalidation, refresh storage caches on app startup, and made Recent Files and Browser subscribe to storage mutation events so downloads and external file changes refresh recents, Home carousel data, Gallery, albums, categories, and open folders without per-page manual pulls.
- **Recent Files Pull Refresh**: Moved pull-to-refresh to cover the whole Recent Files screen body and positioned it below the top app bar so the indicator and pull motion are visible from empty, loading, search, grid, and list states.
- **Refresh Consistency**: Extended startup and external MediaStore invalidation to storage-usage, cleaner, and folder stats caches, made Storage Cleaner listen for storage mutations, prevented unrelated Browser mutations from clearing the current folder state, and guarded Recent Files against stale overlapping reloads.
- **Gallery Album Paste**: Added direct copy/cut paste support for real gallery albums, including paste actions inside an open album and on album cards, with shared conflict resolution and foreground COPY/MOVE operations.
- **Gallery Refresh Reliability**: Refreshed gallery snapshots after copy and move operations using both source and destination invalidation so album contents update without detouring through Browser.
- **Gallery Performance**: Moved image snapshot shaping off the main thread and precomputed album cover lookups to reduce gallery loading and album-grid recomposition churn.

## [1.1.4] - 2026-06-17

- **Save to Arcile Defaults and Recents Refresh**: Added a persistent default destination for Save to Arcile imports, opened valid defaults first while keeping folder navigation available, aligned the destination picker closer to Arcile list styling, and fixed Recent Files pull-to-refresh to use the manual refresh path.
- **Durable Save-to-Arcile Imports**: Routed shared-file imports through the foreground operation pipeline with staged temp files, progress notifications, cancellation cleanup, operation journal recovery, and mutation finalization.
- **Operation Recovery Checkpoints**: Persisted operation checkpoints for staged files, finalized outputs, rollback hints, and Trash IDs so interrupted foreground work can recover and clean up more precisely.
- **Archive Extraction Reliability**: Reset archive extraction conflict state for every extraction so skip and keep-both directory decisions cannot leak into later archive operations.
- **Archive Browser Restoration**: Restored saved archive path and entry-prefix browsing state after process recreation without persisting archive passwords.
- **Storage Dashboard Back Navigation**: Fixed dashboard and usage-map Browser handoffs so Back returns to Home instead of landing on an unintended Browser route.
- **Storage Identity and Handoffs**: Routed open/share and media thumbnail fallbacks through carried content URI identity where available, removed raw MediaStore `DATA = ?` lookups from those paths, and made Trash cleanup use supplied MediaStore content URIs.
- **Shared Filename Preservation**: Staged shared files with collision-safe original display names and served staged handoffs through an app-owned provider that reports `OpenableColumns.DISPLAY_NAME` and size metadata.
- **Room Cache Schema Discipline**: Enabled Room schema export, committed the version 2 schema, limited destructive cache reset to the explicit version 1 upgrade path, and documented schema bump review requirements.

## [1.1.3] - 2026-06-16

- **Onboarding UI/UX Polish**: Refined all onboarding chips and status elements to be pill-shaped (`CircleShape`), increased page bottom scroll padding to prevent content from cutting off, made permission row touch targets larger and more tactile, animated soft background warning highlights on pending permissions, simplified permissions copy, and added a back arrow icon to header navigation.
- **Onboarding Accessibility Polish**: Made the expressive onboarding icon respect reduced-motion settings, moved new onboarding labels into string resources, and changed granted permission indicators from inert buttons to static status chips.
- **Onboarding M3 Expressive Overhaul**: Overhauled onboarding pages with a custom counter-rotating morphing shape background behind the app icon, compact features showcase utilizing a FlowRow of interactive chips with dynamic detail crossfading, and direct inline personalization and permission configuration.
- **Keyboard Input Reliability**: Made search fields, dialog name inputs, filter fields, and other typed text inputs explicitly open the keyboard on focus/tap and bring themselves into view above the IME.
- **Home Recent Thumbnail Stability**: Unified Home recent-file carousel preload and display thumbnail cache keys on a shared bucketed variant so carousel thumbnails stay cached when scrolled offscreen and back.
- **Fresh Recent Files and Gallery**: Stopped serving stale persisted recent-file snapshots to UI refreshes, tightened gallery cached-image validation, and ensured external MediaStore changes clear gallery snapshots so new screenshots and deleted images are reflected without manual refresh.

## [1.1.2] - 2026-06-15

- **Security and Privacy Hardening**: Tightened external file handoffs, excluded local cache metadata from backup and transfer, and made secure delete failures visible instead of silently reporting success.
- **Manual Settings Backup and Restore**: Added file-based export and restore for Arcile preferences from Settings and onboarding, with clear previews, restore results, theme support, and safe error handling.

## [1.1.1] - 2026-06-15

- **Activity Log**: Added a local Activity tool for opened folders and foreground file-operation history, including clear controls, backup exclusions, and Home/Tools navigation.
- **Activity Log Reliability**: Serialized foreground operation activity writes so completed, failed, or cancelled operations cannot be overwritten by a delayed running-status entry.

## [1.1.0] - 2026-06-14

- **Storage Bar Progress**: Replaced the default loading shimmer with an indeterminate linear moving progress bar showing all category colors, and added a bouncy spring animation for segments when loading completes.
- **Release Documentation and Verification**: Updated release-facing README, website, release notes, and development docs for v1.1.0, clarified full local versus device-backed test suite commands, documented per-module test commands, corrected multi-module release verification commands, refreshed module/test counts, and cleared release-prep test, production-string, architecture-budget, and lint issues.
- **Cache and Thumbnail Stability**: Stabilized Browser list/grid thumbnail requests with remembered no-crossfade Coil requests, increased thumbnail prefetch buffering, scoped MediaStore invalidation instead of clearing all storage nodes, and added cached-first snapshots for Recent Files, Storage Usage, and Storage Cleaner.
- **Storage Cache Freshness**: Cleared persisted Recent Files snapshots when MediaStore reports file changes and made broad MediaStore change events force a full storage cache invalidation.
- **Gallery Timeline Dates**: Formatted dates as wrapped, content-fitting chips in daily/weekly/monthly grouping views, styled like the recents screen.
- **Gallery Overflow Menu**: Removed the redundant "Show file details" toggle from the 3-dot overflow menu (already accessible via the view options sheet).
- **Image Viewer EXIF Sheet**: Refactored the metadata sheet to be a full-screen layout and wrapped each pager item in a VerticalPager for smooth scroll-snapping, allowing users to swipe up to slide the EXIF sheet up (pushing the image above) and swipe down/tap close to slide it back down.
- **Image Viewer Bottom Actions**: Realigned bottom actions to place the action buttons (Favorite, Info, Rotate) on the left in a SplitButtonGroup and the 3-dot overflow button on the far right, matching the select actions layout.
- **Bouncy Card Press Actions**: Converted all cleaner category cards to use `bounceClickable` for bouncy, tactile Spring interactions and haptic feedback.
- **Animated Manual Refresh**: Added infinite, fluid rotation to the manual refresh icon during active storage scans.
- **Persistent Page Memory**: Preserved back stack structures when opening containing folders or archives from sub-pages (Recent Files, Quick Access, Archive Viewer), allowing system back gestures to return users cleanly to their caller pages.
- **Cleaner UI/UX and Alignment**: Aligned the checkbox, thumbnail, and path/metadata. Removed the redundant icon buttons on the right side; instead, clicking the file preview thumbnail opens the file, and clicking the path text opens the containing folder. Positioned the risk badges and dynamically resolved app icons below in an indented row to optimize space.
- **Vertical Duplicate Comparison**: Redesigned the "Compare duplicates" sheet to stack files vertically inside a scrollable column with the "Delete selected" action button sticky at the bottom. Organized each file inside a premium card layout featuring a header row (with 56.dp thumbnail, bold filename, size/date, dynamic app label/icon, and risk badge), a structured location box (clickable to open the containing folder), and a selection footer. Moved the per-file delete trash icon button from the header to the selection footer next to the keep checkbox. Removed the redundant `/storage/emulated/0` prefix from displayed paths to improve readability.
- **Smart App Badge Layout**: Offset application badges slightly outwards on file previews and styled them with matching card/container background borders to avoid nested/overlapping visual clutter. Resolved app risk package names into recognizable app icon context without repeating app-specific labels.
- **Cleaner Header Alignment**: Simplified the `DuplicateGroupCard` header to place the name and a single subtitle containing the duplicate count and size (e.g. `2 files • 116.7 KB`) on the left, centering the Compare text button on the right to fix vertical and horizontal alignment.
- **Cleaner Exact File Opening**: Kept Storage Cleaner file preview taps routed through the exact platform file opener, while folder candidates open their containing Browser location.
- **Cleaner Scroll Performance**: Moved thumbnail cache size reads and app icon/label resolution off the UI thread to reduce duplicate-file list scroll jank.
- **Cleaner Exact Duplicate Detection**: Replaced duplicate-name matching with size narrowing, sampled-byte checks, and SHA-256 verification so duplicate groups represent identical file contents even when names differ.
- **Cleaner Custom Rules and Ignores**: Added persisted Storage Cleaner rules with per-section enablement, name/path ignore patterns, large-file and old-download thresholds, exact-path ignore actions, and ignored-item restore management.
- **Cleaner Metadata Precision**: Updated duplicate timestamp metadata to include seconds and removed redundant app-name risk wording when the app icon already provides context.

## [1.0.9] - 2026-06-14

- **Home Storage Loading Reliability**: Cancelled overdue storage, category, volume, and Trash analytics work when the Home refresh timeout is reached so skeleton loaders can clear instead of waiting on a stuck background calculation.
- **Storage Dashboard Cache Reuse**: Seeded the dashboard's single-volume category breakdown from the already-loaded Home storage categories, avoiding a duplicate per-volume recalculation when the global cached data is equivalent.
- **Storage Dashboard First Paint**: Reused Home's single-volume category and Trash totals while opening a selected-volume dashboard so category rows appear immediately instead of briefly showing only Trash and system data.
- **Home Force Refresh**: Made Home pull-to-refresh clear cached category analytics before reloading so manual refresh performs a true fresh storage calculation while normal app opens can still reuse cache.
- **Storage Cleaner Hub**: Moved thumbnail cache controls from Settings into Storage Cleaner, added shared file previews, and added app-related badges for files Cleaner flags as app-associated.
- **Cleaner Cleanup Categories**: Added dedicated Marker files and Empty folders sections while keeping duplicate detection available for all duplicate files.
- **Cleaner Duplicate Files**: Renamed the duplicate cleanup section to "Duplicate files" and added a scrollable vertical compare sheet with per-file and selected-file delete actions that use the shared confirmation flow.
- **Cleaner File Actions**: Added exact Open file handling plus Open folder navigation that focuses the containing Browser location, and clarified risk labels with non-clickable badges and an info dialog.

## [1.0.8] - 2026-06-13

- **Image Viewer Revamp**: Adapted a clean media viewer layout to Arcile's design system, adding a circular Back button in the top-left, a date/time info pill in the top-center, a horizontal scrolling thumbnail strip at the bottom, and a 3-dot overflow options menu in the bottom-right corner for secondary actions. Increased the size of viewer overlay and action buttons to 56.dp with 28.dp icons to improve touch targets.
- **Quick Access App Icons**: Declared WhatsApp package visibility and resolved the Android Files/DocumentsUI package so Home and the Quick Access screen can show sharper installed app icons for WhatsApp, Files, Android/data, and Android/obb instead of always falling back to Material icons.
- **Whole-Device Storage Usage**: Used Android platform storage statistics for primary internal storage and surfaced the non-browsable remainder as system and inaccessible data so Arcile usage totals better match Android Settings.
- **Decimal Storage Units**: Switched displayed file sizes to decimal KB/MB/GB/TB units so storage totals align with Android Settings and device capacity labels.
- **Gallery Album Defaults**: Removed the album aspect-ratio layout option, kept album covers square, and added a Photos/Albums default opening preference for the gallery. Polished the gallery and viewer dropdown overflow menus to set a consistent width, stretch option containers to full width, prevent text wrapping using ellipsis, and optimize content padding.
- **Architecture Budget Tuning**: Raised the large-file and ViewModel line-count guardrails to 1000 lines to reduce churn from overly tight test limits.
- **Storage Dashboard Reuse**: Reused already-loaded home storage breakdowns when opening the dashboard summary and delayed the heavier usage-map scan until the Usage Map tab is opened.
- **Gallery Swipe Navigation**: Added horizontal swiping between Photos and Albums while preserving the existing floating tab controls.
- **Gallery Scroll Polish**: Preserved gallery scroll position when returning from the image viewer, moved the fast scrollbar to the far-right edge, and made it appear only during scroll, press, or drag interactions.
- **Gallery Chrome Behavior**: Hid and revealed the bottom Photos/Albums navigation with scroll, matching the existing floating control behavior while keeping selection actions visible.
- **Gallery Delete Freshness**: Removed deleted images from gallery state, favorites, albums, and viewer paging immediately while the backing catalog refreshes.
- **Transparent Image Rendering**: Stopped drawing the generic image icon behind successfully loaded transparent images and switched thumbnails to a neutral background.
- **Viewer Context Awareness**: Preserved gallery ordering in the image viewer and opened the pager on the selected image instead of rebuilding the list around page zero.
- **Cross-Screen Image Swiping**: Passed surrounding image context from Home recents, Recent Files, Browser folders, Browser categories, and Browser search results into the image viewer so users can swipe nearby images without returning to the source list.
- **Browser Viewer Return**: Returned users to the active Browser folder after swiping through folder images in the viewer instead of landing back on Home.
- **Browser Scroll Preservation**: Kept the Browser folder scroll position intact when returning from the image viewer instead of jumping back to the top of the folder.

## [1.0.7] - 2026-06-12

- **Material 3 Expressive Animations**: Customized standard spring animations to use under-damped bouncy physics and integrated volumetric slide-and-scale navigation transitions. Added responsive press-scaling effects with tactile haptic clicks to interactive list items, cards, and tab buttons.
- **Progressive Predictive Back Navigation**: Integrated progressive predictive back gestures across core screens (Browser, Gallery, Recents, Trash, Archive Viewer), dynamically scaling, translating, and fading layouts in real-time response to back swipes.
- **Bouncy Spring Padding Safeguards**: Coerced animated horizontal and vertical item list paddings to be non-negative, preventing runtime crashes caused by spring animation overshoots below zero during selection changes.
- **Home Storage Bar Shimmering Loading**: Implemented a dynamic shimmering used-space loading bar that populates automatically on app start, morphs smoothly when storage details resolve, and crossfades seamlessly to full-category segmented colors once loaded.
- **Gallery Module Refactor**: Split the oversized gallery screen and image viewer implementations into focused chrome, options, content, album, timeline, scrolling, zoom, metadata, and helper files while preserving existing public navigation entry points.
- **Architecture Boundary Cleanup**: Refactored the remaining temporary large-file whitelist entries across archive viewer, storage usage map, and filesystem data source code so the architecture budget only exempts the website `docs/index.html`.
- **Gallery Boundary Coverage**: Added the image gallery feature module to production class imports for architecture boundary checks.

## [1.0.6] - 2026-06-11

- **Pinch-to-Resize Columns**: Implemented real-time interactive multi-touch pinch-to-resize column scaling directly on Photos and Albums grids.
- **Custom Album Covers**: Added the ability to designate any image inside an album as its customized cover thumbnail.
- **Favorites virtual album**: Added a Favorite (heart) action in the image viewer to bookmark media, and injected a virtual "Favorites" album at the top of the Albums tab using the latest favorited item as the cover.
- **Gallery state reliability**: Prevented restored or direct-entry image viewers from closing before images finish loading, clamped the viewer pager after Favorites/delete changes, and kept Favorites album counts and custom album covers aligned with currently available files.
- **Gallery startup and navigation smoothness**: Applied saved gallery preferences before the initial image load and removed heavyweight full-grid content animations between Photos, Albums, and album pages.
- **Viewer external handoff reliability**: Preserved MediaStore `content://` grants for gallery Open With and Share actions instead of incorrectly treating them as filesystem paths.
- **On-demand EXIF details**: Kept EXIF metadata loading scoped to the image viewer details sheet so gallery browsing stays lightweight.
- **Chronological folder sorting**: Added full chronological sorting options (Newest/Oldest) to the Albums grid using updated folder modification dates.

## [1.0.5] - 2026-06-11

- **Interactive Gesture-Driven Image Viewer**: Consolidated competing touch listeners in the image viewer to support zoom-responsive panning with boundary resistance, double-tap zoom targeting, and elastic drag-to-dismiss transitions with backdrop fading.
- **Privacy EXIF Metadata Scrubbing**: Added a detailed EXIF bottom sheet utilizing `ExifInterface` to display rich camera parameters, paths, sizes, dates, and locations. Introduced a secure metadata scrubbing action to strip all geolocation and camera attributes.
- **Photos Timeline Dynamic Grouping**: Supported user-selectable chronological photos grouping (None, Day, Week, Month) on the Photos screen.
- **Multi-Select Drag Selection**: Integrated a long-press-and-drag range selection helper in standard/staggered grids and list layouts. Handled layout headers correctly using visible items' keys, added auto-scrolling near the viewport boundaries, and generated tactile haptic clicks on selection change.
- **Customizable Album Settings**: Implemented a dynamic grid column sizing slider, customizable sorting criteria (Name, Size), and aspect ratio previews on the Albums tab.

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
- **Storage Usage Map:** Added a bounded radial usage map with path navigation, selected item details, and direct browser navigation.
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
