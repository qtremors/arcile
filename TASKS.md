# Arcile - Tasks

> **Project:** Arcile
>
> **Version:** 2.1.8
>
> **Last Updated:** 2026-10-04

---

## UI and Accessibility

- [ ] **A11Y-0001 - Resource the Shared File-Item Accessibility Labels** `[Low]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/lists/FileItemSemantics.kt` -> `fileItemSemantics`; `arcile-app/build-logic/src/main/kotlin/dev/qtremors/arcile/buildlogic/ArcileBuildVerificationPlugin.kt` -> `checkProductionStrings`.
  - **Problem:** Shared rows build spoken descriptions and actions from hardcoded English strings including Hidden, Folder, Modified, Toggle selection, and Open folder. These bypass resources. The production-string check passes because these labels use conditional expressions and `label` assignments outside its matching rules.
  - **Impact:** These core TalkBack announcements cannot follow localized resources, and the existing string gate does not detect regressions in this path.
  - **Fix:** Pass resource-resolved labels into the semantics helper or resolve them in its composable callers. Cover these specific semantics patterns in the existing string check without flagging non-user-facing animation labels.
  - **Verification:** Add a focused negative fixture for the production-string check.

## Maintainability

- [ ] **ARCH-0001 - Separate Cleaner Version Detection from Scan Orchestration** `[Medium]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageCleanerScanner.kt` -> `DefaultStorageCleanerScanner`, `parseFilenameVersion`, `findFilenameVersionFamilies`, `parseManifestPackageAndVersion`, `extractApkPackageInfo`.
  - **Problem:** This 1,118-line production file combines traversal/progress, snapshot caching, duplicate hashing, risk classification, filename-version ranking, and binary APK manifest parsing. Version heuristics and binary parsing are embedded in the scanner alongside deletion-candidate policy, making isolated rule changes and tests harder to maintain. It is explicitly allowlisted up to 1,150 lines, so length alone is not a rule violation.
  - **Impact:** Changes to version ranking or APK parsing require editing the same class that coordinates scan safety and resource limits.
  - **Fix:** Extract filename parsing/ranking and family formation into `CleanerVersionFamilyDetector.kt`, and binary manifest/package reading into `CleanerApkVersionReader.kt`. Preserve the injected APK resolver, keep implementation details internal, and leave traversal/cancellation/progress ownership in the scanner.
  - **Verification:** Run the existing cleaner scanner and filename-version tests, including camera/document-number exclusions, same-package APK grouping, duplicate suffixes, and retained-newest selection. Run the architecture boundary check after extraction; do not change candidate selection behavior.

## Requested Features and Workflow Improvements

These additions come from the user's idea list and were checked against the current source on 2026-09-26. They describe requested capabilities or extensions, not confirmed defects. Existing partial support is noted.

- [ ] **FEAT-0001 - Edit the Browser Path Bar** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/Breadcrumbs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserDirectoryNavigation.kt`.
  - **Verified current behavior:** Breadcrumbs renders clickable segments without text entry; directory navigation accepts paths.
  - **Requested change:** Allow typing/pasting accessible directory paths with validation, clear errors, and cancellation.

- [ ] **FEAT-0002 - Pin Individual Files in Quick Access** `[Requested feature]`
  - **Location:** `arcile-app/feature/quickaccess/src/main/java/dev/qtremors/arcile/feature/quickaccess/QuickAccessAddSurfaces.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessDestinationMapper.kt`.
  - **Verified current behavior:** Custom-path and SAF additions target folders; the mapper has no individual-file opening branch.
  - **Requested change:** Add file shortcuts routed through the normal opener, with missing/renamed-target handling.

- [ ] **FEAT-0003 - Add Local File and Folder Tags with Tag Search** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/db/ArcileDatabase.kt`; `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/QuickAccessItem.kt`.
  - **Verified current behavior:** The inspected database stores storage/cache records and Quick Access stores shortcuts. No user-defined file-tag model was found.
  - **Requested change:** Persist tags and assignments, expose assignment/removal and tag search, and define rename/move/delete/backup behavior.

- [ ] **FEAT-0004 - Add Virtual Albums and Groups of Folders** `[Requested feature]`
  - **Location:** `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/MediaGalleryFolderPresentation.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/MediaGalleryFoldersGrid.kt`.
  - **Verified current behavior:** Albums derive physical parent folders and a special Favorites album; arbitrary named memberships/groups are absent from the inspected presentation.
  - **Requested change:** Persist virtual albums and separately named folder groups. Membership changes must not move/delete physical files.

- [ ] **FEAT-0005 - Add Triggered File-Operation Rules** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`.
  - **Verified current behavior:** Cleaner rules describe sections/exclusions; Browser operations are interactive. No automation-rule/scheduler implementation was found.
  - **Requested change:** Add opt-in folder rules with new-file/scheduled/manual triggers, extension/size/age/name filters, and move/rename/compress/clean/notify actions. Include preview, exclusions, cancellation, collisions, and loop prevention. Resolve STORAGE-0001 through STORAGE-0004 before automating destructive operations.

- [ ] **FEAT-0006 - Extend Cleaner Rules with Combined File Predicates** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`; `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/CleanerSectionSettingsDialog.kt`.
  - **Verified current behavior:** Existing rules support exclusions, large-file thresholds, and old-download age, but not reusable combinations of positive folder/extension/size/age/name predicates.
  - **Requested change:** Extend the existing rule model/editor with combined predicates and match previews while preserving exclusions/defaults.

- [ ] **FEAT-0007 - Save Folder-Specific Action Presets** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/Breadcrumbs.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`; `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerRules.kt`.
  - **Verified current behavior:** Navigation and archive/cleaner actions exist; no saved per-folder action-preset model was found.
  - **Requested change:** Save actions such as send to SD, compress here, clean old files, or share recent files. Reuse operation/rule infrastructure with affected-file previews.

- [ ] **FEAT-0008 - Share Images with Metadata Removed from a Copy** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/utils/ShareHelper.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`.
  - **Verified current behavior:** ShareHelper prepares URI targets without a metadata-removal option; eraseMetadata is a separate editing action.
  - **Requested change:** Offer sanitized-copy sharing that preserves displayed orientation and original bytes, with managed temporary output. Do not fabricate EXIF.

- [ ] **FEAT-0009 - Modify Entries in Existing ZIP Archives** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ArchiveRepository.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/ZipArchiveHandler.kt`; `arcile-app/feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerViewModel.kt`.
  - **Verified current behavior:** The contract lists, extracts, and creates archives but has no add/remove-entry operation. Selected extraction already exists.
  - **Requested change:** Add selected-file insertion and entry removal for supported ZIPs using staged replacement; report format/password restrictions.

- [ ] **FEAT-0010 - Allow Explicit Names for Both Sides of a Paste Conflict** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ConflictResolution.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/PasteConflictDialog.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileConflictDetector.kt`.
  - **Verified current behavior:** Resolutions are KEEP_BOTH, REPLACE, and SKIP, without user-provided names.
  - **Requested change:** Allow validated names for incoming and existing items. Recheck collisions during execution and journal renames.

- [ ] **FEAT-0011 - Offer Content Comparison Alongside Name Conflicts** `[Requested feature]`
  - **Location:** `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileConflictDetector.kt`; `arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/StorageCleanerScanner.kt`.
  - **Verified current behavior:** Paste detects an existing same-named target; cleaner duplicate/version logic is not exposed as paste comparison.
  - **Requested change:** Keep collisions separate from optional duplicate/version suggestions. Use size prefilters and cancellable hashing for equality; label heuristic version matches.

- [ ] **FEAT-0012 - Add an Appearance Shape Preference** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/theme/UiPreferences.kt`; `arcile-app/feature/settings/src/main/java/dev/qtremors/arcile/feature/settings/ui/SettingsAppearanceSection.kt`.
  - **Verified current behavior:** UiPreferences and Appearance settings expose no user-selectable shape preference.
  - **Requested change:** Persist a shape option and apply it through shared shapes, including selected states, while preserving touch targets.

- [ ] **FEAT-0013 - Select the Filename Stem When Renaming** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/RenameDialog.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/FileNameInput.kt`.
  - **Verified current behavior:** RenameDialog uses a String field without selection state and does not override FileNameInput's false autoFocus default.
  - **Requested change:** Focus rename, show the keyboard, and initially select the stem. Handle folders/dotfiles/extensionless names without resetting user-adjusted selection.

- [ ] **FEAT-0014 - Offer a Numeric Keyboard for Vault Password Entry** `[Requested feature]`
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesDialogs.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesCreateDialog.kt`.
  - **Verified current behavior:** Password fields use KeyboardType.Password without a layout option.
  - **Requested change:** Offer a numeric keyboard with an alphanumeric fallback; keep this an input preference rather than changing encryption.

- [ ] **FEAT-0015 - Configure Vault Auto-Lock and Background Retention** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/ArcileApp.kt`; `arcile-app/core/vault/data/src/main/java/dev/qtremors/arcile/core/vault/data/DefaultVaultSecurityPreferences.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesDialogs.kt`.
  - **Verified current behavior:** Background handling invokes lockAll; persisted vault security settings currently contain screenshot protection only.
  - **Requested change:** Add an explicit lock policy retaining current defaults. Define background retention, screen-off, exit, process-death, and grant behavior without persisting unlocked keys.

- [ ] **FEAT-0016 - Send Browser Selections Directly into OnlyFiles** `[Requested feature]`
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesScreen.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesTransferController.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserClipboardController.kt`.
  - **Verified current behavior:** OnlyFiles already imports local files/folders and has its own clipboard; inspected Browser sources lack a vault-selection action.
  - **Requested change:** Choose/unlock a vault from Browser and reuse boundary transfers. Distinguish copy/move and use typed clipboard references. Resolve VAULT-0001 and VAULT-0002 before extending destructive paths.

- [ ] **FEAT-0017 - Schedule Settings Backups and Discover Restore Candidates** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/backup/PreferencesBackupManager.kt`; `arcile-app/feature/onboarding/src/main/java/dev/qtremors/arcile/feature/onboarding/OnboardingRoute.kt`.
  - **Verified current behavior:** Export/preview/restore and an onboarding picker exist; recurring export and backup-location discovery are absent from these flows.
  - **Requested change:** Add opt-in scheduled settings export to a selected destination and discover compatible backups in authorized locations. Retain preview/validation; do not imply file/vault-content backup.

- [ ] **FEAT-0018 - Save Image Rotation, Mirroring, and Freeform Crops** `[Requested feature]`
  - **Location:** `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerZoomable.kt`.
  - **Verified current behavior:** rotateViewerImage changes viewerRotationDegrees/saved state rather than image bytes. No mirror/freeform-crop operation was found in the inspected gallery flow.
  - **Requested change:** Add transform preview and explicit save-copy/replace. Distinguish persisted edits from viewer rotation and support crop transparency in suitable formats. Use safe publication as in STORAGE-0006.

- [ ] **FEAT-0019 - Export Rotated and Mirrored Videos** `[Requested feature]`
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerOverflowMenu.kt`; `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Verified current behavior:** Inspected controls have no persisted rotate/mirror export operation.
  - **Requested change:** Add transform preview and explicit export with progress/cancellation and safe publication. Distinguish playback orientation from saved edits; do not promise instant re-encoding.

- [ ] **FEAT-0020 - Add a Vertical Video-to-Video Browsing Mode** `[Requested feature]`
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Verified current behavior:** The existing VerticalPager switches video and metadata surfaces rather than successive videos.
  - **Requested change:** Add optional vertical media navigation, resolving gesture conflicts with metadata/seek/brightness/volume while preserving the existing mode.

- [ ] **FEAT-0021 - Expand Comparison to Synchronized Documents and Audio Metadata** `[Requested feature]`
  - **Location:** `arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/StorageCleanerDuplicateCompareSheet.kt`.
  - **Verified current behavior:** Cleaner comparison presents candidate previews/properties, not synchronized document/audio comparison.
  - **Requested change:** Add two independently usable panes with vertical split, optional synchronized document scrolling, and audio metadata comparison.

- [ ] **FEAT-0022 - List Installed User and System Apps Separately from APK Files** `[Requested feature]`
  - **Location:** `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryViewModel.kt`; `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryScreen.kt`.
  - **Verified current behavior:** The APK feature builds a file library via SearchRepository and does not enumerate installed packages in inspected sources.
  - **Requested change:** Add installed-user/system-app views alongside APK files, with visibility-aware icons/actions.

- [ ] **FEAT-0023 - Add HTML Editing and an Explicit Preview Mode** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/MarkdownRenderer.kt`.
  - **Verified current behavior:** The inspected editor handles text/Markdown; no HTML preview/highlighting integration was found.
  - **Requested change:** Add HTML highlighting/formatting and explicit preview. Define script/network/local-resource access; build on STORAGE-0006 and REL-0002.

- [ ] **FEAT-0024 - Add Book and Comic Reading to Documents** `[Requested feature]`
  - **Location:** `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryViewModel.kt`.
  - **Verified current behavior:** Documents has file browsing/PDF presentation but no dedicated EPUB/comic reading flow in the inspected feature.
  - **Requested change:** Define initial formats and add reading position, navigation, layout preferences, and bounded decoding. Keep external projects as implementation references.

- [ ] **FEAT-0025 - Create a PDF from Selected Images** `[Requested feature]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserArchiveController.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`.
  - **Verified current behavior:** No PDF-creation implementation was found in the inspected Browser/Documents sources.
  - **Requested change:** Add selection-based export with image ordering, page size/orientation, destination, and cancellation.

- [ ] **FEAT-0026 - Extend the Existing Plugin API for Editor Workflows** `[Requested feature]`
  - **Location:** `arcile-app/plugin-api/src/main/java/dev/qtremors/arcile/plugin/api/PluginContract.kt`; `arcile-app/core/plugin/android/src/main/java/dev/qtremors/arcile/core/plugin/android/PluginManager.kt`.
  - **Verified current behavior:** The existing public file action is VIEW_FILE; no editor result/save contract exists.
  - **Requested change:** Define editing capabilities, negotiation, writable-copy/output handling, results/cancellation, and access lifetime for reusable clients. Preserve viewer compatibility and document trust.

- [ ] **FEAT-0027 - Design an FTP Storage Plugin Contract** `[Requested feature]`
  - **Location:** `arcile-app/plugin-api/src/main/java/dev/qtremors/arcile/plugin/api/PluginContract.kt`; `arcile-app/core/plugin/android/src/main/java/dev/qtremors/arcile/core/plugin/android/PluginManager.kt`.
  - **Verified current behavior:** The current API is a viewer handoff; no FTP implementation was found.
  - **Requested change:** Define a storage-provider capability for listing/streaming/transfers/authentication/reconnect/cancellation before FTP implementation. Distinguish secure transport options and redact credentials.

- [ ] **FEAT-0028 - Add Optional Category Launcher Entries** `[Requested feature]`
  - **Location:** `arcile-app/app/src/main/AndroidManifest.xml`; `arcile-app/app/src/main/java/dev/qtremors/arcile/MainActivity.kt`.
  - **Verified current behavior:** The inspected app manifest exposes the main launcher and viewer/share entry points, without configurable category aliases.
  - **Requested change:** Allow separate launcher entries for supported categories, initially Gallery, with normal permissions/back navigation.

- [ ] **FEAT-0029 - Add Play Folder to Browser** `[Requested feature]`
  - **Location:** `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/controller/BrowserClipboardController.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlaybackController.kt`.
  - **Verified current behavior:** AudioPlaybackController.playQueue exists; no Browser play-folder entry point was found.
  - **Requested change:** Build a deterministic supported-media queue from a folder action; define subfolder inclusion and ordering and reuse playback.

- [ ] **FEAT-0030 - Document Features and Practical Workflows** `[Requested feature]`
  - **Location:** `README.md`.
  - **Verified current behavior:** README gives a feature overview; no detailed workflow guide/per-category capability matrix was found in the documentation inventory.
  - **Requested change:** Add a guide/table covering hidden actions, audio library/player/editor distinctions, vaults, archives, backup, and plugins. Link it without broad wording changes; describe shipped behavior only.

- [ ] **FEAT-0031 - Use Compact Paste-Status Text** `[Requested feature]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/category/CategoryLibraryContent.kt`.
  - **Verified current behavior:** The shared clipboard plural says '%d item(s) ready to paste'. The user reports clipping.
  - **Requested change:** Use compact pluralized item counts in constrained toolbars and retain clear paste/action semantics for accessibility.

- [ ] **FEAT-0032 - Use Tap-to-Trash and Hold-for-Options** `[Requested feature]`
  - **Location:** `arcile-app/core/presentation/src/main/java/dev/qtremors/arcile/core/presentation/delegate/DeleteFlowDelegate.kt`.
  - **Verified current behavior:** requestDeleteSelected evaluates storage policy then opens a trash/permanent/mixed confirmation; no distinct tap-versus-hold route exists in this delegate.
  - **Requested change:** For trash-capable selections, tap moves to trash with Undo and long-press opens the detailed deletion card. Preserve confirmation for irreversible/non-trash paths and provide an accessible way to open options. Resolve STORAGE-0001 and STORAGE-0005 first.

## User-Reported Issues

The reports below remain unresolved and include source paths and focused investigation plans. Manual testing is handled separately by the project owner.

- [ ] **REPORT-0002 - Investigate Audio Mini-Player Blocking Rename and Back** `[Medium]`
  - **Location:** `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioLibraryScreen.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioLibraryPlayerBars.kt`; `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/dialogs/FileNameInput.kt`.
  - **Evidence:** The user reports blocked keyboard/back gestures and a crash while the mini-player is present. AudioLibraryScreen owns predictive back and rename-dialog state; the mini-player owns pointer gestures. Source review alone does not establish the crash cause.
  - **Investigation:** Reproduce rename with collapsed/expanded playback and capture the stack trace, focus owner, IME state, and back-handler dispatch. Correct the responsible interaction before redesigning player placement; coordinate with FEAT-0013.

- [ ] **REPORT-0003 - Investigate Rainbow Artifacts During Video Playback** `[Medium]`
  - **Location:** `arcile-app/feature/videoplayer/src/main/java/dev/qtremors/arcile/feature/videoplayer/VideoViewerPlaybackSurface.kt`.
  - **Evidence:** The user reports intermittent rainbow regions. Playback owns player-surface attachment and loaded/rendered-path state; no affected media sample was available.
  - **Investigation:** Record device/codec/HDR format and reproduce with a nonprivate sample. Isolate decoder output versus surface reuse/composition and test the smallest correction; avoid blanket codec fallbacks without evidence.

- [ ] **REPORT-0004 - Investigate OnlyFiles Viewers Closing on Rotation** `[Medium]`
  - **Location:** `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesScreen.kt`; `arcile-app/feature/onlyfiles/src/main/java/dev/qtremors/arcile/feature/onlyfiles/OnlyFilesViewModel.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/ArcileApp.kt`.
  - **Evidence:** The user reports rotation closes viewers. Viewer state is ViewModel-owned and is cleared when the selected vault is no longer unlocked; application background handling also locks vaults. This does not prove rotation triggers that path.
  - **Investigation:** Trace configuration recreation, route identity, vault-lock events, and internal versus external viewer handoffs. Preserve the active item through recreation while retaining intentional locking behavior; coordinate with FEAT-0015.

- [ ] **REPORT-0005 - Investigate APK and Documents Grid-Size Controls** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/category/CategoryLibraryViewOptions.kt`; `arcile-app/feature/apk/src/main/java/dev/qtremors/arcile/feature/apk/ApkLibraryScreen.kt`; `arcile-app/feature/documents/src/main/java/dev/qtremors/arcile/feature/documents/DocumentLibraryScreen.kt`.
  - **Evidence:** The user reports a broken slider. Shared options translate a slider index to column count and gridMinCellSize, while category screens consume persisted presentation; no visual failure was reproduced.
  - **Investigation:** Trace available width, padding, index conversion, preference updates, and actual grid-cell sizing in both categories. Establish whether the control, persistence, or layout disagrees and fix that specific path.

- [ ] **REPORT-0006 - Investigate Browser Location Restoration by Entry Route** `[Medium]`
  - **Location:** `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/MainShellCoordinator.kt`; `arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/navigation/BrowserNavigationController.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessDestinationMapper.kt`.
  - **Evidence:** The user reports swipe and storage-bar entry behavior is reversed. Navigation has restorePersistentLocation, seedInitialPathHistory, explicit paths, and remembered folder preferences.
  - **Investigation:** Trace gestures versus explicit storage-root navigation. Require swipe entry to resume the last browser folder and storage-bar entry to open its selected root; keep explicit shortcut/deep-link paths authoritative.

- [ ] **REPORT-0007 - Investigate Playback Queue and Viewer Deletion Consistency** `[Medium]`
  - **Location:** `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioPlaybackController.kt`; `arcile-app/feature/audio/src/main/java/dev/qtremors/arcile/feature/audio/AudioQueueSheet.kt`; `arcile-app/feature/gallery/src/main/java/dev/qtremors/arcile/feature/gallery/ImageViewerViewModel.kt`.
  - **Evidence:** The user reports mismatched queue/file lists and failure to advance after deletion. Audio derives queue IDs from the player timeline. Image applyViewerDelete removes the item but falls back to the first remaining displayed item, so existing deletion handling must be examined rather than assumed absent.
  - **Investigation:** Reproduce sorted/filtered/shuffled playback and deletion of the current item in each affected viewer. Compare actual queue IDs, displayed order, and completion callbacks; define next-item behavior at the last item and on failed deletion.

- [ ] **REPORT-0008 - Investigate Markdown Overlay Opacity and Sizing** `[Medium]`
  - **Location:** `arcile-app/core/ui/src/main/java/dev/qtremors/arcile/core/ui/texteditor/StandaloneTextEditor.kt`.
  - **Evidence:** The user reports translucent/oversized overlays. The editor composes controls and a full-size animated information-sheet host; the responsible overlay and visual failure were not established.
  - **Investigation:** Reproduce with long Markdown, formatting controls, overflow menus, and document information at different IME/font/window sizes. Make the relevant card opaque and content-sized while retaining scrolling/insets for long content.

- [ ] **REPORT-0009 - Investigate Stale APK Confirmation Status** `[Medium]`
  - **Location:** `arcile-app/core/operation/android/src/main/java/dev/qtremors/arcile/core/operation/android/apk/PackageInstallerEngine.kt`; `arcile-app/app/src/main/java/dev/qtremors/arcile/apk/ApkInstallStatusReceiver.kt`.
  - **Evidence:** onUserConfirmationRequested sets 'Awaiting user confirmation...' at 95%; state changes next on a terminal result. There is no intermediate session reconciliation in the inspected engine, matching the user's stale-label report without proving installation itself is stuck.
  - **Investigation:** Reproduce accept/cancel and trace session callbacks/result broadcasts and activity return. Represent waiting versus active installation truthfully using observable state, reconcile recreated UI, and avoid claiming success before the terminal result.

## Audit Coverage

Audit completed 2026-09-26, with inspection and checks across 2026-09-23 through 2026-09-26, against `b11b150d5d4607768bee038b2fa67ba1d99386b1` (2.1.0). The working tree was clean at initial inspection and on continuation. Findings above are source-based unless a completed check is explicitly identified. Only this backlog is changed.

| Area | Inspected scope | Evidence/checks |
|---|---|---|
| User idea-list follow-up, 2026-09-26 | Checked current implementation at `05f37c25277428a2d4bf44c498efe7ce0acc0d19`: browser/navigation, clipboard/conflicts, archive contract, cleaner rules, Quick Access, gallery/albums/transforms, audio/video controls, OnlyFiles/session settings, APK install state, editor, shared grid options, backup/onboarding, database, plugins and docs | Added requested extensions with existing support identified and bounded investigations for user-reported behavior; source inspection and repository searches only. Working tree was clean before this follow-up. |
| Architecture/modularity | All 36 included modules inventoried; app composition, core runtime/navigation/presentation/operation/storage/vault/UI, plugin API/UI, testing helpers, and all 19 feature modules | Settings/build dependency declarations; representative ViewModels, controllers, DI, typed routes, repository contracts, and architecture-test rules |
| Maintainability/naming/comments/file size | First-party Kotlin line-count scan; cleaner, preferences, accent selector, playback surface; source-cleanup contracts and build-convention naming | Production files over 700 lines: cleaner 1,118, accent selector 784, playback surface 746, preferences 726; checked explicit allowlists |
| Startup/navigation/permissions | MainActivity, ArcileApp, shell/MainRoute, Browser initialization, typed routes, Onboarding state | Source tracing of splash timeout, permission refresh, restored state, browser ownership, back handlers |
| Browser, archives, trash, import | Browser controller wiring; archive load/extract/conflicts; local transfer and mutation journals; trash restore/Undo; incoming-share preflight and destination route | Detailed failure/recovery traces; path/symlink policy and archive safety/rollback inspection; focused storage tests below |
| Home, recent files, quick access | Dashboard/search/refresh state, recent paging and date boundaries, shortcut mutations and SAF handoff representation | Representative ViewModel and primary-screen source review |
| Settings, activity log, plugins | Preferences/backup state, log clear, plugin discovery/ranking/same-signer checks and explicit URI handoffs | Representative ViewModel/screen review, plugin manager, backup exclusions, build/catalog/docs comparison |
| Documents, APKs, images | Category-library state/actions, editor load/save/drafts, image viewer state, APK staging/install entry points | Detailed editor trace; representative gallery/library screens and bounded APK staging entry points |
| Audio/video | Playback service/session, audio edit plan/export/cancel, video playback-surface ownership and controls | Audio-focus/noisy handling, player release, export cleanup, primary-screen source triage |
| Vaults/feature-specific behavior | OnlyFiles controllers; domain contracts; crypto/KDF bounds; imports, transfer/merge, sessions/biometrics, transaction recovery, external grants | Detailed transaction and deletion traces; focused vault tests below |
| Lifecycle/background work | Bulk service, coordinator, journal/recovery, application initialization, viewer processes, biometric coroutine callers | Source review includes service cancellation, Android 15+ timeout override, and session disposal |
| UI/UX | Primary-screen source triage across all feature modules; deeper editor, trash, browser, OnlyFiles, category and playback flows | Loading/error/back/selection hooks, lazy-item keys, dialogs and shared scaffold reviewed selectively |
| Material/current APIs | Catalog: Compose BOM 2026.08.00, Material 3 1.5.0-alpha26, Adaptive 1.3.0; theme, motion, shared controls, adaptive MainRoute | [Official Material release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) checked again 2026-09-26: stable 1.4.0 and alpha29; Expressive APIs and API churn reviewed |
| Screens/windows/input | Supported API 30+; compact/medium/expanded branches, landscape and separating-hinge layout; keyboard field helper, insets and editor selections | Source-only compatibility matrix: phone portrait/landscape, tablet, folded/unfolded, split screen and desktop resize; [API 37 large-screen requirements](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored) checked during audit |
| Accessibility/languages | Shared file semantics, resource usage, selection actions, reduced-motion theme hooks, responsive/scrolling containers | Source checks; `checkProductionStrings` passed, with the specific semantics gap documented above |
| Database/persistence | Room v2, v1 destructive cache fallback, restore invalidation, storage-node/folder/thumbnail entities and DAOs, snapshot stores, DataStore ownership | Source review of cache-only schema, thumbnail foreign-key cascade, indexes, writes and invalidation paths |
| Storage/networking/security/privacy | PathSafety, exports/providers, staging, plugin signatures, manifests/backup rules, logger, vault grants and KDF limits | Reachability traced for accepted findings; no INTERNET permission in app manifest; bounded import/archive paths inspected |
| Resources/performance | Traversal/hash limits, cleaner workload, thumbnail cache setup, text history, biometric dispatchers | Static bounds and thread ownership only |
| Build/dependencies/native/upgrades | AGP 9.3.1, Kotlin 2.4.10, Gradle 9.5.0, KSP 2.3.11; compile/target 37, min 30, Java 21 daemon/JVM 11; debug suffix/coverage and release shrinking/signing references | Catalog/wrapper/build conventions inspected; offline targeted Gradle commands succeeded; app remains version 2.1.0/code 210 |
| Focused storage validation | `:core:storage:data:testDebugUnitTest --tests '*FileTransferEngineTest' --tests '*MutationJournalTest' --tests '*TrashManagerTest' --offline --max-workers=1 --console=plain` | Passed: 34 tests, zero failures/errors/skips (11 transfer, 8 journal, 15 trash) |
| Focused vault validation | `:core:vault:data:testDebugUnitTest --tests '*VaultTransactionManagerTest' --tests '*VaultTransferCoordinatorTest' --tests '*VaultImport*Test' --offline --max-workers=1 --console=plain -q` | Passed: 8 tests, zero failures/errors/skips (1 transaction, 3 transfer, 4 import) |
| Tests/docs/environment | README, relevant DEVELOPMENT sections, privacy policy, test fixtures and architecture gates; existing TASKS fully read | `checkProductionStrings :app:verifyArcileBuildConventions --offline --max-workers=1 --console=plain -q` passed |
