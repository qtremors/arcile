# Arcile Changelog

> **Project:** Arcile
> **Version:** 1.4.5
> **Last Updated:** 2026-07-11

---

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
