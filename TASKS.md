# Arcile - Tasks

> **Project:** Arcile
> **Version:** 0.8.3
> **Last Updated:** 2026-05-26

---

## Consolidated Tasks

### Architecture / Maintainability Tasks

- [ ] **ARCH-0012 - Presentation State Ownership** `[High]` `[Deferred]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/browser/BrowserViewModel.kt`
  - **Problem:** `BrowserState` contains persistent navigation data, list data, search data, selection, clipboard, dialog visibility, native pending actions, properties, progress overlay state, and presentation preferences in one wide state object.
  - **Impact:** Unrelated UI changes can trigger wide recompositions.
  - **Fix:** Split into `BrowserNavigationState`, `BrowserListingState`, `BrowserSelectionState`, `BrowserSearchState`, `BrowserDialogState`, and `OperationUiState`. Expose separate StateFlows or a composed immutable screen state. Recommended Refactor: Use reducers/events for browser state transitions and test them directly. Safer Alternative: At minimum, move dialog visibility and operation state out of `BrowserState`.
  - **Status:** Deferred beyond the completed display-state extraction. `BrowserState` still remains a compatibility screen state; full state-holder/reducer split is intentionally skipped because it would be wide.
  - **Verification:** Not run for this deferred refactor.

- [ ] **MAINT-0030 - Maintainability / Code Organization** `[Medium]` `[Deferred]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/**`
  - **Problem:** The code is package-layered (`data`, `domain`, `presentation`) but not feature-modular. Feature concerns cross directories and files are growing large.
  - **Impact:** Indirect: feature velocity and regression risk will worsen.
  - **Fix:** Move toward feature packages/modules. Define public APIs per feature. Add architecture rules for dependencies. Recommended Refactor: Start with `feature:browser`, `feature:trash`, `feature:archive`, and `core:storage`. Safer Alternative: Within single module, reorganize packages under `feature/*` and `core/*`.
  - **Status:** Deferred. Feature/package/module reorganization is intentionally skipped because it would be a broad refactor.
  - **Verification:** Not run for this deferred refactor.

---
## Backlog / Future Ideas

> A parking lot for future ideas, enhancements, and unprioritized Android file-manager features.

### Architecture & Foundation (Comparison Insights)
- **Persistent Operation Manager**: Move away from fire-and-forget Coroutines for file operations (Copy/Move/Delete). Implement a persistent Service-backed queuing architecture (similar to WorkManager or Foreground Services) to ensure heavy I/O operations survive UI destruction and provide robust pause/resume/retry capabilities.
- **Storage Provider Interfaces (VFS)**: Decouple the data layer from hardcoded `java.io.File` and Android Scoped Storage APIs. Introduce a `StorageProvider` interface that returns standard domain `FileModel`s and handles `InputStream/OutputStream` streams. This lays the groundwork for seamless future integration of SMB, FTP, or Cloud storage without modifying the UI layer.
- **Native File Viewers**: Integrate native, in-app viewers for common developer and text formats (e.g., Markdown, JSON, XML, TXT). Explore integrating lightweight code editors (like Sora) with syntax highlighting to keep users engaged within Arcile rather than bouncing them to external apps via Intents.

### File Browsing & Navigation
- **Dual-Pane File Browser**: Add a tablet, foldable, and landscape mode with two live folder panes for drag/copy/move workflows.
- **Breadcrumb Path Editing**: Let users tap the current path and type or paste a filesystem path directly.
- **Folder Tabs**: Keep multiple folders open at once with tab restore after process death.
- **Starred / Favorited Files**: Add a starred section to the Home screen and a star toggle on individual files and folders.
- **Recent Locations**: Track recently opened folders separately from recently modified files.
- **Quick Jump Drawer**: Provide fast access to Download, DCIM, Documents, Android/media, SD cards, OTG drives, network locations, and pinned folders.

### File Operations & Reliability
- **Queued Operation Manager**: Queue copy, move, delete, rename, compress, extract, and trash actions with pause, resume, cancel, retry, and background notifications.
- **Conflict Resolution Dialogs**: Offer replace, skip, rename, apply-to-all, compare metadata, and keep-both choices during copy/move/extract operations.
- **Checksum Verification**: Add MD5, SHA-1, and SHA-256 calculation plus optional post-copy verification for large transfers.
- **Batch Rename Tool**: Support numbered patterns, date tokens, find/replace, case conversion, extension changes, and live preview.
- **Undo Recent Operations**: Provide a short-lived undo stack for rename, move, trash, and folder creation actions where safe.
- **Safe Large Transfer Mode**: Warn before moving large folders across storage volumes and fall back to copy-then-delete with verification.
- **Operation Logs Page**: A dedicated page tracking the history of all major file manipulations for auditing and troubleshooting.

### Archives & File Formats
- **APK / AAB Inspector**: Show package name, version, signatures, permissions, icons, split APK details, and install/open actions.
- **Text / Code Previewer**: Add a lightweight viewer with encoding detection, syntax highlighting, line numbers, search, and share/copy actions.
- **PDF / Document Preview Hooks**: Provide better thumbnails and safe handoff to installed viewers for PDFs, Office files, and ebooks.
- **Expanded Format Support**: Add read-only extraction support for RAR (e.g., via junrar), and add TAR, GZIP, BZIP2, and XZ using existing commons-compress dependencies.
- **Advanced Archive Creation Options**: Add compression levels, archive splitting (multi-volume), and AES-256 encryption options using Zip4j.
- **Archive Modification**: Allow in-place editing of ZIP archives (adding or removing files) without full extraction using Zip4j.
- **In-Memory Previews**: Stream entries directly into image or text previewers within the ArchiveViewerScreen without extracting to disk.
- **Archive Integrations**: Register Share intent for creating ZIPs from external apps, and add smart extraction rules (e.g., auto-delete archive after extraction).

### Storage, Access & Android Integration
- **Storage Health Diagnostics**: Basic storage status checks, mount state, free-space trends, and repair/trim suggestions for mounted volumes.
- **Mount / Unmount Awareness**: Detect SD card and USB OTG changes immediately, refresh affected screens, and recover pending operations gracefully.
- **MediaStore Rescan Tools**: Manually rescan selected files or folders so gallery, music, and downloads apps see changes sooner.
- **MTP / USB Transfer Mode Notes**: Surface helpful guidance when Android's USB file transfer state blocks desktop access.

### Search, Filters & Organization
- **Indexed Search**: Build an optional local index for faster filename, extension, MIME type, size, and modified-date searches.
- **Advanced Search Filters**: Add filters for date range, file size range, media duration, image dimensions, duplicate candidates, hidden files, and storage volume.
- **Saved Searches**: Allow users to pin reusable searches such as "large videos", "old APKs", or "recent downloads".
- **Duplicate Finder**: Detect duplicates with size pre-filtering and optional content hashing before deletion.
- **Large / Old Files Cleanup**: Add focused views for files likely worth reviewing when freeing space.
- **Empty Folder Finder**: Scan and clean empty folders with preview and exclusions.
- **Smart Collections**: Auto-group screenshots, screen recordings, APKs, downloads, documents, and WhatsApp/Telegram media.
- **Storage Analyzer ("Filelight" View)**: A dedicated radial map or sunburst chart to visualize storage usage by folder and file type.

### Media & Preview Experience
- **Rich Media Previews**: Continue expanding custom Coil `Fetcher` components for rich file previews.
- **Recent Media Carousel**: Replace the current recent files list on the Home screen with a horizontally scrollable carousel of the 10 most recently modified images and videos.
- **Image Detail Sheet**: Show resolution, EXIF, location presence, orientation, color profile, and quick rotate/share actions.
- **Video Detail Sheet**: Show duration, resolution, codec, bitrate, frame rate, subtitles, and thumbnail refresh actions.
- **Audio Detail Sheet**: Show album art, artist, album, duration, bitrate, and quick metadata cleanup hints.
- **Thumbnail Cache Controls**: Add settings to clear, size-limit, or rebuild thumbnail caches.
- **Hidden Media Controls**: Create or remove `.nomedia` files with clear warnings about gallery visibility.

### Automation & Power Tools
- **Automated Task Rules**: Implement trigger-based operations, for example auto-moving video files from Camera to Videos daily.
- **Watched Folders**: Monitor selected folders for new files and offer rule actions such as rename, move, compress, or notify.
- **Quick Actions**: Let users configure a small set of folder-specific actions like "send to SD", "compress here", or "clean old files".
- **Scheduled Cleanup**: Periodically empty trash, remove temporary files, or prompt for old downloads review.

### Sharing, Transfers & Network
- **Nearby Share / Share Sheet Polish**: Improve multi-file sharing flow, MIME grouping, and error messages for unsupported targets.
- **Local HTTP File Drop**: Temporarily host selected files or a folder over LAN with a QR code and one-tap shutdown.
- **WebDAV / SMB Client**: Browse and transfer files from NAS, routers, and desktop shares.
- **FTP / SFTP Client**: Add optional remote location support for advanced users.
- **Wi-Fi Direct Transfer**: Explore device-to-device transfer without an external network.

### Security, Privacy & Safety
- **"OnlyFiles" Encrypted Vault**: A secure, encrypted vault for storing sensitive files and folders using AES-256 encryption.
- **Secure Delete Option**: Provide best-effort overwrite/delete workflows where storage type makes it meaningful, with honest limitations.
- **Private Folder Bookmarks**: Hide selected pinned folders behind biometric confirmation.
- **Trash Privacy Audit**: Review shared `.trash` behavior, visibility to other apps, and options for app-private trash on supported storage.
- **Sensitive Metadata Warnings**: Warn before sharing images with location EXIF or documents with embedded author metadata.
- **App Lock**: Add optional biometric/PIN lock for opening Arcile.

### Home Screen & UI Polish
- **Material 3 Expressive - SplitButton Implementation**: Replace standard actions with `SplitButton` where a main action regularly needs a secondary dropdown or overflow action.
- **Material 3 Expressive - WavyProgressIndicator**: Investigate and implement `WavyProgressIndicator` for long-running non-blocking background tasks if appropriate.
- **Haptics & Interaction Quality**: Inject `HapticFeedback` via `LocalHapticFeedback`. Trigger subtle vibrations on long-press, successful file operations, and error states.
- **Animated Empty States**: Fix or replace the current static graphics with smooth animations such as Lottie for empty folders, trash, and search results.
- **Selection Mode Polish**: Improve multi-select toolbar actions, selected-count affordances, drag selection, and range selection.
- **Adaptive Bottom Actions**: Keep high-frequency actions thumb-friendly on phones while preserving dense toolbars on tablets.
- **Shape Customization Toggle**: Add a setting to toggle UI element shapes, for example squircle vs standard rounded corners.

### Settings, Personalization & Accessibility
- **File Icon Packs**: Allow optional icon style packs for file types, folders, archives, media, and app packages.
- **Compact / Comfortable Density**: Add a display-density setting for list rows, grid cells, and toolbars.
- **Large Text Audit**: Verify folder lists, dialogs, and bottom sheets at Android large-font accessibility settings.
- **High Contrast Theme**: Add a contrast-focused theme profile beyond light, dark, and OLED modes.
- **Gesture Customization**: Configure swipe actions, long-press behavior, and double-tap shortcuts.

### Multi-Window, Layout & Devices
- **Multi-Window / Split-Screen Support**: Ensure the app works correctly in Android multi-window mode, with proper layout reflow.
- **Foldable Layouts**: Add hinge-aware navigation and dual-pane behavior for large foldable devices.
- **Tablet Navigation Rail**: Use a rail or permanent navigation surface on wider screens.
- **Keyboard & Mouse Support**: Add keyboard shortcuts, hover states, context menus, and precise right-click behavior.
- **Chromebook Polish**: Verify resizable windows, external storage access, drag/drop, and system file picker interoperability.

### Developer, Build & Release
- **Developer Mode Toggle**: Unlock hidden developer options by rapidly holding or tapping the version number in the About screen.
- **FOSS / Libre Build**: Ensure a fully free-and-open-source build flavor suitable for F-Droid without proprietary blobs or dependencies.
- **Internal Diagnostics Export**: Export anonymized app state, settings, storage classifications, and recent operation errors for bug reports.
- **Performance Benchmarks**: Add repeatable benchmarks for large folder listing, recursive stats, thumbnail loading, and search.
- **StrictMode Debug Profile**: Enable stricter debug-only checks for disk I/O, leaked resources, and slow main-thread work.
