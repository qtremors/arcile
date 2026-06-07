# Arcile - Tasks

> **Project:** Arcile
> **Version:** 1.0.0
> **Last Updated:** 2026-06-05

---

## Upcoming Version Roadmap

### 1.1 - Categories, Recents, And Gallery Experience

- [ ] **FEAT-1101 - Recent files calendar filter**
  - Add date/calendar filtering for recent files, with quick chips for today, yesterday, this week, and custom date range.

- [ ] **FEAT-1102 - Category tab-based search**
  - Make category search aware of active category tabs/sections so users can search within Images, Videos, Audio, Documents, APKs, and Archives.

- [ ] **FEAT-1103 - Category date chips and sections**
  - Add date chips and grouped sections to category views, especially media-heavy categories.

- [ ] **FEAT-1104 - Gallery view for image and video categories**
  - Add a pure gallery view mode for Images and Videos with large visual tiles and minimal file-manager chrome.

- [ ] **FEAT-1105 - Customizable category view modes**
  - Let categories choose list, grid, gallery, tabbed, or river-style views independently from normal folders.

- [ ] **FEAT-1106 - Gallery side rail / left-side media navigation**
  - Explore a left-side gallery/category rail for larger screens after the base gallery mode works.

### 1.2 - Settings, Personalization, And Home

- [ ] **FEAT-1201 - Separate theme page** `[Expand Existing]`
  - **Audit:** Theme modes, theme presets, accent selection, custom colors, harmonization, OLED, and persistence already exist inside Settings.
  - Move theme customization into a dedicated settings page with clearer previews and grouped controls.

- [ ] **FEAT-1202 - Home and screen-specific settings**
  - Add settings for Home sections, Browser defaults, category defaults, thumbnails, hidden files, and utility visibility.

- [ ] **FEAT-1203 - File icon packs**
  - Support optional icon style packs for file types, folders, archives, media, and APKs.

- [ ] **FEAT-1204 - Show app icons for app-related folders**
  - Detect common app-owned folders and show recognizable app/package icons where safe and useful.

- [ ] **FEAT-1205 - Material 3 expressive motion pass**
  - Add bouncy physics and expressive animations only where they improve clarity, not to core file-operation reliability paths.

### 1.3 - Cleaner, Trash, And Storage Intelligence

- [ ] **FEAT-1301 - Investigate SD Maid SE patterns**
  - Study cleaner UX, risk labeling, exclusions, app-cache handling, and user confirmation patterns.
  - Document what Arcile should adopt, avoid, or simplify.

- [ ] **FEAT-1302 - Storage-based vs app-based trash strategy**
  - Decide whether Arcile should keep storage-root trash, app-private trash, or a hybrid based on volume type and Android restrictions.
  - Include privacy, restore reliability, removable storage, and uninstall behavior.

- [ ] **FEAT-1303 - Dedicated duplicate finder** `[Expand Existing]`
  - **Audit:** Cleaner already detects duplicate candidates by name and size.
  - Build this into a dedicated duplicate finder with preview, exclusions, optional hashing, and safe trash/delete flow.

- [ ] **FEAT-1304 - Cleaner recommendation model polish** `[Expand Existing]`
  - **Audit:** Cleaner risk levels and risk reasons already exist, including high-risk acknowledgement behavior.
  - Refine labels, copy, exclusions, and SD Maid-style recommendations for app folders, thumbnails, unknown cache-like directories, and user media.

### 1.4+ - Preview, Cache, And Power Tools

- [ ] **FEAT-1401 - Native text and Markdown viewer**
  - Add in-app viewing for Markdown, TXT, JSON, XML, logs, and code-like files with encoding detection, search, copy, and share.

- [ ] **FEAT-1402 - SVG thumbnails**
  - Add safe SVG thumbnail generation with size, parser, and timeout limits.

- [ ] **FEAT-1403 - Thumbnail cache architecture research**
  - Research how Aves and similar gallery apps manage thumbnail cache keys, invalidation, failure caching, and disk budgets.

- [ ] **FEAT-1404 - Rich media detail sheets**
  - Add image EXIF, video metadata, audio metadata, and document metadata sheets.

---

## Tracked Technical Debt

> These IDs are referenced from source comments. Keep the IDs stable until the comments are removed or updated.

- [ ] **C1 - Bound home recent-file queries**
  - **Reference:** `StorageAnalyticsRepository.getRecentFiles`.
  - **Concern:** Home currently requests a bounded preview limit, but this should remain guarded if the Home carousel or Recent Files UI grows.
  - **Next step:** Keep Home preview limits bounded and add paging/offset UI only where needed.

- [ ] **C2 - Avoid repeated full MediaStore category-size scans**
  - **Reference:** `StorageAnalyticsRepository.getCategoryStorageSizes`.
  - **Concern:** Category-size caching exists for global and volume-wide stats; invalidation, TTL behavior, and scoped refresh still need periodic audit.
  - **Next step:** Verify cache invalidation after mutations, volume changes, and MediaStore-only updates.

- [ ] **A4 - Trash metadata orphan correctness**
  - **Reference:** `TrashRepository.getTrashFiles`.
  - **Concern:** Orphaned metadata cleanup is silent and should be audited for failure reporting and recovery visibility.
  - **Next step:** Add explicit cleanup result tracking or diagnostics for missing blobs and invalid metadata sidecars.

- [ ] **F4 - Non-interactive utility cards clarity**
  - **Reference:** `ToolsScreen`.
  - **Concern:** Coming-soon utility cards can look too similar to actionable tools.
  - **Next step:** Either hide unavailable tools or make disabled state visually and semantically obvious.

---

## Future Backlog

> This is the long-range parking lot. Items already shipped in 1.0.0, such as MediaStore content URI hardening, archive replacement rollback, release APK naming, expanded production string checks, and Android 17 target SDK work, have been removed from the active backlog.

### Architecture & Storage Foundation

- [ ] **BACKLOG-ARCH-001 - Storage Provider / VFS abstraction**
  - Introduce a provider interface for local files, SAF trees, MediaStore, removable volumes, network locations, and future cloud/network backends.

- [ ] **BACKLOG-ARCH-002 - Queued operation manager**
  - Extend the existing operation coordinator, foreground service, and journal into a visible queue with pause, resume, retry, cancellation, and completed-operation history.

- [ ] **BACKLOG-ARCH-003 - Operation logs page**
  - Add a diagnostics/history page for completed, failed, cancelled, cleanup-required, undo, and cleaner events.

- [ ] **BACKLOG-ARCH-004 - Performance benchmarks**
  - Add repeatable benchmarks for large folder listing, recursive stats, thumbnail loading, cleaner scanning, usage-map building, and search.

### File Browsing & Navigation

- [ ] **BACKLOG-BROWSE-001 - Dual-pane file browser**
  - Add tablet, foldable, and landscape browsing with two live panes for copy/move workflows.

- [ ] **BACKLOG-BROWSE-002 - Editable breadcrumb path**
  - Let users type or paste a filesystem path directly from the current path bar.

- [ ] **BACKLOG-BROWSE-003 - User-managed folder tabs**
  - Add open folder tabs with add, close, reorder, and process-death restore.

- [ ] **BACKLOG-BROWSE-004 - Starred files and folders**
  - Add favorites to Home, Browser rows, search, and quick access.

- [ ] **BACKLOG-BROWSE-005 - Recent locations**
  - Track recently opened folders separately from recently modified files.

- [ ] **BACKLOG-BROWSE-006 - Quick Access drawer expansion**
  - Expand Quick Access into a drawer or sheet with Downloads, DCIM, Documents, Android/media, Android/data, Android/obb, SD cards, OTG drives, network locations, recent locations, and pinned folders.

### File Operations

- [ ] **BACKLOG-OPS-001 - Batch rename**
  - Support patterns, date tokens, find/replace, regex, case conversion, extension changes, conflict detection, and live preview.

- [ ] **BACKLOG-OPS-002 - Checksum tools**
  - Add MD5, SHA-1, and SHA-256 calculation plus optional post-copy verification.

- [ ] **BACKLOG-OPS-003 - Broader safe undo**
  - Extend undo beyond trash moves to rename, move, restore, create folder/file, and cleaner actions where rollback is reliable.

- [ ] **BACKLOG-OPS-004 - Large transfer safety mode**
  - Add warnings and copy-then-delete verification for risky cross-volume moves.

- [ ] **BACKLOG-OPS-005 - Rich conflict resolution dialogs**
  - Conflict preflight exists for paste and archive flows.
  - Remaining scope: replace, skip, rename, apply-to-all, compare metadata, and keep-both consistently across copy, move, and archive extraction.

### Archives & File Formats

- [ ] **BACKLOG-ARCHIVE-001 - APK / AAB inspector**
  - Add package name, version, signatures, permissions, split APK details, and install/open actions.

- [ ] **BACKLOG-ARCHIVE-002 - Archive modification**
  - Support adding/removing entries from ZIP archives without full extraction.

- [ ] **BACKLOG-ARCHIVE-003 - In-memory archive previews**
  - Stream archive entries directly into image or text previewers without extracting to disk.

- [ ] **BACKLOG-ARCHIVE-004 - Advanced archive creation**
  - Compression levels, no-compression default, format selection, and password controls already exist.
  - Remaining scope: archive splitting, multi-volume creation, explicit encryption algorithm controls, and richer format-specific options.

- [ ] **BACKLOG-ARCHIVE-005 - Archive integrations**
  - Register share intents for creating archives from external apps and add smart extraction rules such as optional archive deletion after successful extraction.

- [ ] **BACKLOG-ARCHIVE-006 - Read-only RAR support**
  - Archive format detection recognizes RAR, but this build does not browse or extract it.
  - Add read-only RAR browsing/extraction if a suitable dependency and license path are acceptable.

### Storage, Access & Android Integration

- [ ] **BACKLOG-STORAGE-001 - Storage health diagnostics**
  - Storage volumes, classification, usage summaries, category sizes, and usage map already exist.
  - Add mount state details, free-space trends, health checks, and repair/trim suggestions for mounted volumes.

- [ ] **BACKLOG-STORAGE-002 - Mount and unmount awareness**
  - Home observes storage volumes and refreshes summaries.
  - Extend this to immediate Browser, Trash, operation recovery, and cleaner behavior when SD cards or USB OTG devices mount/unmount.

- [ ] **BACKLOG-STORAGE-003 - MediaStore rescan tools**
  - Add manual rescan actions for selected files or folders so gallery, music, and downloads apps see changes sooner.

- [ ] **BACKLOG-STORAGE-004 - MTP and USB transfer guidance**
  - Surface helpful guidance when Android USB file transfer state blocks desktop access.

### Search, Filters & Organization

- [ ] **BACKLOG-SEARCH-001 - Indexed search**
  - Add an optional local index for faster filename, extension, MIME, size, date, tag, and folder searches.

- [ ] **BACKLOG-SEARCH-002 - Saved search presets**
  - `SearchFilters.savedPresetName` metadata and active filter chips exist.
  - Remaining scope: persistence, management UI, and pinning reusable searches such as large videos, old APKs, recent downloads, or hidden media.

- [ ] **BACKLOG-SEARCH-003 - Tags**
  - Let users assign Arcile-owned tags to files and folders for local organization.

- [ ] **BACKLOG-SEARCH-004 - Smart collections**
  - Add collections for screenshots, screen recordings, messaging-app media, APKs, downloads, documents, and app-specific folders.

- [ ] **BACKLOG-SEARCH-005 - Advanced search filter expansion**
  - Current filters cover type, item type, size, date, extension, hidden, volume, folder scope, MIME, and saved-preset metadata.
  - Add media duration, image dimensions, duplicate candidates, and persisted preset management.

- [ ] **BACKLOG-SEARCH-006 - Large and old files cleanup presets**
  - Cleaner already has large files and old downloads groups.
  - Add richer filters, folder exclusions, risk labels, and saved cleaner presets.

- [ ] **BACKLOG-SEARCH-007 - Empty folder finder**
  - Scan and clean empty folders with preview and exclusions.

- [ ] **BACKLOG-SEARCH-008 - Storage analyzer polish**
  - StorageUsageMap already provides a radial usage-map view.
  - Improve drill-down, labels, selection actions, legends, filtering, and large-folder performance.

### Media, Preview & Cache

- [ ] **BACKLOG-MEDIA-001 - Thumbnail cache controls**
  - Staging cache controls and folder/category caches exist, but dedicated thumbnail cache controls were not found.
  - Add thumbnail cache clear, size limit, rebuild, and failure-cache controls.

- [ ] **BACKLOG-MEDIA-002 - Hidden media controls**
  - Create or remove `.nomedia` files with clear warnings about gallery visibility.

- [ ] **BACKLOG-MEDIA-003 - Document and ebook preview hooks**
  - Add richer metadata, thumbnails, and safer handoff hints for PDFs, Office files, and ebooks.

- [ ] **BACKLOG-MEDIA-004 - Rich media preview expansion**
  - Custom fetchers already cover APK icons, audio album art, PDFs, and videos.
  - Continue adding richer thumbnails and metadata for images, documents, ebooks, archives, and developer files.

- [ ] **BACKLOG-MEDIA-005 - Image detail sheet**
  - Show resolution, EXIF, location presence, orientation, color profile, and quick rotate/share actions.

- [ ] **BACKLOG-MEDIA-006 - Video detail sheet**
  - Show duration, resolution, codec, bitrate, frame rate, subtitles, and thumbnail refresh actions.

- [ ] **BACKLOG-MEDIA-007 - Audio detail sheet**
  - Album art fetching exists.
  - Add artist, album, duration, bitrate, embedded-art status, and metadata cleanup hints.

- [ ] **BACKLOG-MEDIA-008 - Thumbnail cleaner**
  - Folder stats and cleaner scanning already skip `.thumbnails`.
  - Add explicit thumbnail/cache detection with preview, exclusions, and risk labels for locations that are not recommended to remove.

### Sharing, Transfers & Network

- [ ] **BACKLOG-NET-001 - Local HTTP file drop**
  - Temporarily host selected files or folders over LAN with QR code and one-tap shutdown.

- [ ] **BACKLOG-NET-002 - WebDAV / SMB client**
  - Browse and transfer files from NAS, routers, and desktop shares.

- [ ] **BACKLOG-NET-003 - FTP / SFTP client**
  - Add optional remote location support for advanced users.

- [ ] **BACKLOG-NET-004 - Wi-Fi Direct transfer**
  - Explore direct device-to-device transfer without an external network.

- [ ] **BACKLOG-NET-005 - Nearby Share and share sheet polish**
  - Share/open handoff is centralized through `ExternalFileAccessHelper` and `ShareHelper`.
  - Improve multi-file target grouping, Nearby Share behavior, unsupported-target messaging, and post-share cleanup visibility.

### Security, Privacy & Safety

- [ ] **BACKLOG-SEC-001 - App lock**
  - Add optional biometric/PIN lock for opening Arcile.

- [ ] **BACKLOG-SEC-002 - Encrypted vault**
  - Explore an Arcile-owned encrypted vault for sensitive files and folders.

- [ ] **BACKLOG-SEC-003 - Sensitive metadata warnings**
  - Warn before sharing images with location EXIF or documents with embedded author metadata.

- [ ] **BACKLOG-SEC-004 - Secure delete option**
  - Provide honest best-effort overwrite/delete workflows where the storage type makes it meaningful.

- [ ] **BACKLOG-SEC-005 - Private folder bookmarks**
  - Hide selected pinned folders behind biometric confirmation.

- [ ] **BACKLOG-SEC-006 - Trash privacy audit**
  - Trash already uses `.arcile/.trash`, metadata sidecars, `.nomedia`, restore, filters, sorting, properties, and permanent routing for temporary volumes.
  - Review visibility to other apps and options for app-private trash on supported storage.

### Layout, Accessibility & Devices

- [ ] **BACKLOG-UI-001 - Adaptive layout system**
  - Define shared window-size classes and adaptive scaffolds for phones, tablets, foldables, landscape, desktop mode, and large displays.

- [ ] **BACKLOG-UI-002 - Tablet navigation rail**
  - Use a rail or permanent navigation surface on wider screens.

- [ ] **BACKLOG-UI-003 - Large text audit**
  - Verify folder lists, dialogs, bottom sheets, and toolbars under Android large-font accessibility settings.

- [ ] **BACKLOG-UI-004 - Keyboard, mouse, and right-click support**
  - Add desktop-grade shortcuts, hover states, context menus, and predictable focus movement.

- [ ] **BACKLOG-UI-005 - Foldable and multi-window polish**
  - Add hinge-aware behavior, split-screen reflow, and Chromebook/resizable-window checks.

- [ ] **BACKLOG-UI-006 - Material 3 expressive SplitButton rollout**
  - `SplitButtonGroup` exists and is used in Trash.
  - Expand usage to Browser, cleaner, archive, and selection actions where a main action needs secondary options.

- [ ] **BACKLOG-UI-007 - Material 3 expressive WavyProgressIndicator**
  - Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.

- [ ] **BACKLOG-UI-008 - Progress FAB**
  - Let the floating action button transform into a circular percentage/progress control during scans and long-running operations, with tap behavior for details or cancellation where appropriate.

- [ ] **BACKLOG-UI-009 - Haptics and interaction quality audit**
  - `rememberArcileHaptics` and global vibration settings already exist.
  - Audit long-press, successful operations, errors, selection changes, and destructive confirmations.

- [ ] **BACKLOG-UI-010 - Animated empty states**
  - Reusable `EmptyState` composables exist.
  - Add motion, Lottie, or lightweight animation for empty folders, trash, and search results.

- [ ] **BACKLOG-UI-011 - Selection mode polish**
  - Multi-select, select-all, invert-selection, and floating/toolbar actions exist.
  - Improve selected-count affordances, drag selection, range selection, and action grouping.

- [ ] **BACKLOG-UI-012 - One UI-style scroll action chips**
  - Collapse or transform prominent actions into compact icon chips while scrolling so Browser and Home surfaces keep primary actions reachable without crowding content.

- [ ] **BACKLOG-UI-013 - Customizable Home screen**
  - Let users reorder, hide, and restore Home sections such as storage cards, categories, quick access, recent files, starred files, and cleaner shortcuts.

- [ ] **BACKLOG-UI-014 - Adaptive bottom actions**
  - Keep high-frequency actions thumb-friendly on phones while preserving dense toolbars on tablets.

- [ ] **BACKLOG-UI-015 - Shape customization toggle**
  - Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

- [ ] **BACKLOG-UI-016 - Compact and comfortable density presets**
  - Browser presentation already stores list zoom and grid min cell size.
  - Add a user-facing density preset that applies across rows, grid cells, toolbars, dialogs, and dashboard sections.

- [ ] **BACKLOG-UI-017 - High contrast theme**
  - Add a contrast-focused theme profile beyond light, dark, dynamic, accent, and OLED modes.

- [ ] **BACKLOG-UI-018 - Gesture customization**
  - Configure swipe actions, long-press behavior, and double-tap shortcuts.

- [ ] **BACKLOG-UI-019 - Gamepad and remote support**
  - Add predictable D-pad focus order, remote-friendly actions, and gamepad navigation for large-screen and accessibility use cases.

- [ ] **BACKLOG-UI-020 - Chromebook polish**
  - Verify resizable windows, external storage access, drag/drop, and system file picker interoperability.

### Automation & Power Tools

- [ ] **BACKLOG-AUTO-001 - Watched folders**
  - Monitor selected folders and offer actions for new files.

- [ ] **BACKLOG-AUTO-002 - Automated task rules**
  - Add trigger-based move, rename, compress, clean, or notify rules.

- [ ] **BACKLOG-AUTO-003 - Scheduled cleanup**
  - Periodically empty trash, remove temporary files, or prompt for old downloads review.

- [ ] **BACKLOG-AUTO-004 - Custom cleaner rules**
  - Let users define cleaner rules by folder, extension, size, age, filename pattern, and exclusions.

- [ ] **BACKLOG-AUTO-005 - Quick actions**
  - Let users configure folder-specific actions such as send to SD, compress here, clean old files, or share recent.

### Developer, Build & Release

- [ ] **BACKLOG-DEV-001 - FOSS / Libre build flavor**
  - Ensure a fully free-and-open-source build suitable for F-Droid-style distribution.

- [ ] **BACKLOG-DEV-002 - Internal diagnostics export**
  - Export anonymized app state, settings, quick access, operation journal state, cache stats, storage classifications, and recent errors.

- [ ] **BACKLOG-DEV-003 - StrictMode debug profile**
  - Enable stricter debug-only checks for disk I/O, leaked resources, and slow main-thread work.

- [ ] **BACKLOG-DEV-004 - Android 11+ compatibility audit**
  - Current `minSdk` is 30 and `targetSdk` is 37.
  - Verify storage permissions, DocumentsUI handoff, MediaStore queries, notifications, and background operation behavior across Android 11 through Android 17.

- [ ] **BACKLOG-DEV-005 - Developer mode toggle**
  - Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
