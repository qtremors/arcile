# Arcile Task Backlog

> **Version:** 2.1.9 | **Last Updated:** 2026-10-04

30 open feature and workflow tasks. No open bug or maintenance tasks.

Only unfinished work is listed. Task IDs are retained for reference. Source locations and current-behavior notes come from the 2026-09-26 review; recheck them before implementation.

## Browser and File Organization

- [ ] **FEAT-0001 - Edit the Browser Path Bar**
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/Breadcrumbs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserDirectoryNavigation.kt`.
  - **Current:** Breadcrumbs renders clickable segments without text entry; directory navigation accepts paths.
  - **Change:** Allow typing/pasting accessible directory paths with validation, clear errors, and cancellation.

- [ ] **FEAT-0002 - Pin Individual Files in Quick Access**
  - **Location:** `arcile-app/feature/quickaccess/src/main/java/dev/qtremors/arcile/feature/quickaccess/QuickAccessAddSurfaces.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessDestinationMapper.kt`.
  - **Current:** Custom-path and SAF additions target folders; the mapper has no individual-file opening branch.
  - **Change:** Add file shortcuts routed through the normal opener, with missing/renamed-target handling.

- [ ] **FEAT-0003 - Add Local File and Folder Tags with Tag Search**
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/db/ArcileDatabase.kt`; `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/QuickAccessItem.kt`.
  - **Current:** The inspected database stores storage/cache records and Quick Access stores shortcuts. No user-defined file-tag model was found.
  - **Change:** Persist tags and assignments, expose assignment/removal and tag search, and define rename/move/delete/backup behavior.

- [ ] **FEAT-0004 - Add Virtual Albums and Groups of Folders**
  - **Location:** `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/MediaGalleryFolderPresentation.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/MediaGalleryFoldersGrid.kt`.
  - **Current:** Albums derive physical parent folders and a special Favorites album; arbitrary named memberships/groups are absent from the inspected presentation.
  - **Change:** Persist virtual albums and separately named folder groups. Membership changes must not move/delete physical files.

- [ ] **FEAT-0007 - Save Folder-Specific Action Presets**
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/Breadcrumbs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`; `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`.
  - **Current:** Navigation and archive/cleaner actions exist; no saved per-folder action-preset model was found.
  - **Change:** Save actions such as send to SD, compress here, clean old files, or share recent files. Reuse operation/rule infrastructure with affected-file previews.

- [ ] **FEAT-0009 - Modify Entries in Existing ZIP Archives**
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ArchiveRepository.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ZipArchiveHandler.kt`; `arcile-app/feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerViewModel.kt`.
  - **Current:** The contract lists, extracts, and creates archives but has no add/remove-entry operation. Selected extraction already exists.
  - **Change:** Add selected-file insertion and entry removal for supported ZIPs using staged replacement; report format/password restrictions.

- [ ] **FEAT-0010 - Allow Explicit Names for Both Sides of a Paste Conflict**
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ConflictResolution.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/PasteConflictDialog.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileConflictDetector.kt`.
  - **Current:** Resolutions are KEEP_BOTH, REPLACE, and SKIP, without user-provided names.
  - **Change:** Allow validated names for incoming and existing items. Recheck collisions during execution and journal renames.

- [ ] **FEAT-0011 - Offer Content Comparison Alongside Name Conflicts**
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileConflictDetector.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageCleanerScanner.kt`.
  - **Current:** Paste detects an existing same-named target; cleaner duplicate/version logic is not exposed as paste comparison.
  - **Change:** Keep collisions separate from optional duplicate/version suggestions. Use size prefilters and cancellable hashing for equality; label heuristic version matches.

- [ ] **FEAT-0029 - Add Play Folder to Browser**
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserClipboardController.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlaybackController.kt`.
  - **Current:** AudioPlaybackController.playQueue exists; no Browser play-folder entry point was found.
  - **Change:** Build a deterministic supported-media queue from a folder action; define subfolder inclusion and ordering and reuse playback.

- [ ] **FEAT-0032 - Use Tap-to-Trash and Hold-for-Options**
  - **Location:** `arcile-app/core/presentation/src/main/java/dev/qtremors/arcile/core/presentation/delegate/DeleteFlowDelegate.kt`.
  - **Current:** requestDeleteSelected evaluates storage policy then opens a trash/permanent/mixed confirmation; no distinct tap-versus-hold route exists in this delegate.
  - **Change:** For trash-capable selections, tap moves to trash with Undo and long-press opens the detailed deletion card. Preserve confirmation for irreversible/non-trash paths and provide an accessible way to open options. Preserve verified trash recovery and restore Undo.

## Cleaner and Automation

- [ ] **FEAT-0005 - Add Triggered File-Operation Rules**
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`.
  - **Current:** Cleaner rules describe sections/exclusions; Browser operations are interactive. No automation-rule/scheduler implementation was found.
  - **Change:** Add opt-in folder rules with new-file/scheduled/manual triggers, extension/size/age/name filters, and move/rename/compress/clean/notify actions. Include preview, exclusions, cancellation, collisions, and loop prevention. Preserve verified transfer and recovery safeguards for destructive operations.

- [ ] **FEAT-0006 - Extend Cleaner Rules with Combined File Predicates**
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`; `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/CleanerSectionSettingsDialog.kt`.
  - **Current:** Existing rules support exclusions, large-file thresholds, and old-download age, but not reusable combinations of positive folder/extension/size/age/name predicates.
  - **Change:** Extend the existing rule model/editor with combined predicates and match previews while preserving exclusions/defaults.

## Vaults and Settings Backups

- [ ] **FEAT-0014 - Offer a Numeric Keyboard for Vault Password Entry**
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesDialogs.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesCreateDialog.kt`.
  - **Current:** Password fields use KeyboardType.Password without a layout option.
  - **Change:** Offer a numeric keyboard with an alphanumeric fallback; keep this an input preference rather than changing encryption.

- [ ] **FEAT-0015 - Configure Vault Auto-Lock and Background Retention**
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/ArcileApp.kt`; `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/DefaultVaultSecurityPreferences.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesDialogs.kt`.
  - **Current:** Background handling invokes lockAll; persisted vault security settings currently contain screenshot protection only.
  - **Change:** Add an explicit lock policy retaining current defaults. Define background retention, screen-off, exit, process-death, and grant behavior without persisting unlocked keys.

- [ ] **FEAT-0016 - Send Browser Selections Directly into OnlyFiles**
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesScreen.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesTransferController.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserClipboardController.kt`.
  - **Current:** OnlyFiles already imports local files/folders and has its own clipboard; inspected Browser sources lack a vault-selection action.
  - **Change:** Choose/unlock a vault from Browser and reuse boundary transfers. Distinguish copy/move and use typed clipboard references. Preserve verified vault transaction and transfer recovery safeguards.

- [ ] **FEAT-0017 - Schedule Settings Backups and Discover Restore Candidates**
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/backup/PreferencesBackupManager.kt`; `arcile-app/feature/onboarding/src/main/java/dev/qtremors/arcile/feature/onboarding/OnboardingRoute.kt`.
  - **Current:** Export/preview/restore and an onboarding picker exist; recurring export and backup-location discovery are absent from these flows.
  - **Change:** Add opt-in scheduled settings export to a selected destination and discover compatible backups in authorized locations. Retain preview/validation; do not imply file/vault-content backup.

## Media and Documents

- [ ] **FEAT-0008 - Share Images with Metadata Removed from a Copy**
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ShareHelper.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`.
  - **Current:** ShareHelper prepares URI targets without a metadata-removal option; eraseMetadata is a separate editing action.
  - **Change:** Offer sanitized-copy sharing that preserves displayed orientation and original bytes, with managed temporary output. Do not fabricate EXIF.

- [ ] **FEAT-0018 - Save Image Rotation, Mirroring, and Freeform Crops**
  - **Location:** `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerZoomable.kt`.
  - **Current:** rotateViewerImage changes viewerRotationDegrees/saved state rather than image bytes. No mirror/freeform-crop operation was found in the inspected gallery flow.
  - **Change:** Add transform preview and explicit save-copy/replace. Distinguish persisted edits from viewer rotation and support crop transparency in suitable formats. Stage and verify output before replacing originals.

- [ ] **FEAT-0019 - Export Rotated and Mirrored Videos**
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerOverflowMenu.kt`; `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Current:** Inspected controls have no persisted rotate/mirror export operation.
  - **Change:** Add transform preview and explicit export with progress/cancellation and safe publication. Distinguish playback orientation from saved edits; do not promise instant re-encoding.

- [ ] **FEAT-0020 - Add a Vertical Video-to-Video Browsing Mode**
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Current:** The existing VerticalPager switches video and metadata surfaces rather than successive videos.
  - **Change:** Add optional vertical media navigation, resolving gesture conflicts with metadata/seek/brightness/volume while preserving the existing mode.

- [ ] **FEAT-0021 - Expand Comparison to Synchronized Documents and Audio Metadata**
  - **Location:** `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/StorageCleanerDuplicateCompareSheet.kt`.
  - **Current:** Cleaner comparison presents candidate previews/properties, not synchronized document/audio comparison.
  - **Change:** Add two independently usable panes with vertical split, optional synchronized document scrolling, and audio metadata comparison.

- [ ] **FEAT-0023 - Add HTML Editing and an Explicit Preview Mode**
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/MarkdownRenderer.kt`.
  - **Current:** The inspected editor handles text/Markdown; no HTML preview/highlighting integration was found.
  - **Change:** Add HTML highlighting/formatting and explicit preview. Define script/network/local-resource access; preserve verified save recovery and bounded editor state.

- [ ] **FEAT-0024 - Add Book and Comic Reading to Documents**
  - **Location:** `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryViewModel.kt`.
  - **Current:** Documents has file browsing/PDF presentation but no dedicated EPUB/comic reading flow in the inspected feature.
  - **Change:** Define initial formats and add reading position, navigation, layout preferences, and bounded decoding. Keep external projects as implementation references.

- [ ] **FEAT-0025 - Create a PDF from Selected Images**
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`.
  - **Current:** No PDF-creation implementation was found in the inspected Browser/Documents sources.
  - **Change:** Add selection-based export with image ordering, page size/orientation, destination, and cancellation.

## Apps and Plugins

- [ ] **FEAT-0022 - List Installed User and System Apps Separately from APK Files**
  - **Location:** `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryViewModel.kt`; `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryScreen.kt`.
  - **Current:** The APK feature builds a file library via SearchRepository and does not enumerate installed packages in inspected sources.
  - **Change:** Add installed-user/system-app views alongside APK files, with visibility-aware icons/actions.

- [ ] **FEAT-0026 - Extend the Existing Plugin API for Editor Workflows**
  - **Location:** `arcile-app/plugin-api/src/main/java/dev/qtremors/arcile/plugin/api/PluginContract.kt`; `arcile-app/core/plugin/android/src/main/java/dev/qtremors/arcile/core/plugin/android/PluginManager.kt`.
  - **Current:** The existing public file action is VIEW_FILE; no editor result/save contract exists.
  - **Change:** Define editing capabilities, negotiation, writable-copy/output handling, results/cancellation, and access lifetime for reusable clients. Preserve viewer compatibility and document trust.

- [ ] **FEAT-0027 - Design an FTP Storage Plugin Contract**
  - **Location:** `arcile-app/plugin-api/src/main/java/dev/qtremors/arcile/plugin/api/PluginContract.kt`; `arcile-app/core/plugin/android/src/main/java/dev/qtremors/arcile/core/plugin/android/PluginManager.kt`.
  - **Current:** The current API is a viewer handoff; no FTP implementation was found.
  - **Change:** Define a storage-provider capability for listing/streaming/transfers/authentication/reconnect/cancellation before FTP implementation. Distinguish secure transport options and redact credentials.

- [ ] **FEAT-0028 - Add Optional Category Launcher Entries**
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml`; `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt`.
  - **Current:** The inspected app manifest exposes the main launcher and viewer/share entry points, without configurable category aliases.
  - **Change:** Allow separate launcher entries for supported categories, initially Gallery, with normal permissions/back navigation.

## Appearance and Documentation

- [ ] **FEAT-0012 - Add an Appearance Shape Preference**
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/theme/UiPreferences.kt`; `arcile-app/feature/settings/src/main/java/dev/qtremors/arcile/feature/settings/ui/SettingsAppearanceSection.kt`.
  - **Current:** UiPreferences and Appearance settings expose no user-selectable shape preference.
  - **Change:** Persist a shape option and apply it through shared shapes, including selected states, while preserving touch targets.

- [ ] **FEAT-0030 - Document Features and Practical Workflows**
  - **Location:** `README.md`.
  - **Current:** README gives a feature overview; no detailed workflow guide/per-category capability matrix was found in the documentation inventory.
  - **Change:** Add a guide/table covering hidden actions, audio library/player/editor distinctions, vaults, archives, backup, and plugins. Link it without broad wording changes; describe shipped behavior only.
