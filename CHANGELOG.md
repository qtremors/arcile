# Arcile Changelog

> **Project:** Arcile
> **Version:** 2.4.9
> **Last Updated:** 2026-11-08

---

## [2.4.9] - 2026-11-08

### Changed
- Long-press a vault for its options instead of tapping a menu button. Pinned vaults now sit in their own Pinned section above the rest, and unlocked vaults offer Vault settings from the same menu.
- The open vault's menu is shorter: actions for the current folder and items that need attention are listed first, and a row of icons along the bottom holds Lock, Show hidden files, Pinned folders, and Workspace tabs. Search stays in the app bar.
- Each vault has a full-screen Vault settings page, opened from its menu: password, fingerprint unlock, backups, apps with access, storage use, folder views, health checks, and removing or deleting the vault. These were previously all crowded into the menu, which also showed both turning fingerprint unlock on and off at once.
- OnlyFiles settings open as a full screen instead of a bottom sheet, grouped into Locking, Password entry, Privacy, and Storage.
- Choosing files to import, a folder to export to, or a backup location happens on a full-screen picker. Moving between folders keeps the list on screen with a thin progress bar instead of flashing a spinner and resizing.

### Fixed
- Opening OnlyFiles no longer flashes the "no vaults" screen before your vaults appear. If the vault list is slow to load, a loading indicator shows instead.
- When a vault locked during audio editing, the editor reported itself closed slightly before it had released the decrypted audio from memory. It now releases the audio first.

## [2.4.8] - 2026-11-08

### Changed
- The OnlyFiles audio player updates its position only while playing and on screen, instead of waking four times a second for as long as it is open.
- The icon on empty screens breathes for 15 seconds and then eases to rest, matching the background shapes, so an empty screen left open stops redrawing every frame.
- Folder size scans wait for running file operations to finish without checking in a loop.
- Home and the shared storage bar use one loading indicator instead of two identical copies.
- Older changelog entries are condensed to their main points. Every version is still listed.

### Fixed
- When one file operation ended just as another began, the app could treat storage as idle and start folder size scans during the second operation. The busy flag and its counter now change together.
- A file import test failed occasionally when every module's tests ran at once.

## [2.4.7] - 2026-11-08

### Changed
- Folder sizes read each item with one file-system call instead of three, stop scanning when you leave a folder, and keep a running scan when you come back instead of restarting it.
- While Arcile is in the background, changes from other apps (camera, downloads, messaging) are only noted. Caches, folder sizes, and Home totals refresh once when you return, and Home skips refreshes while it is off screen.
- Image, PDF, text, and video viewer processes no longer repeat the main app's storage watching and thumbnail bookkeeping. Each viewer process has its own image cache, and the database stays in sync across processes.
- Thumbnails already shown are not recorded again, and new ones are saved in one batch instead of several database writes per image.
- The music player saves its position separately from the queue, so the regular position save no longer rewrites every track. Position updates pause while no player screen is visible.
- The gallery finds images through MediaStore's indexed media type instead of matching every file name, and swaps its saved list in one step.
- Paused videos stop polling their position. Empty-screen animations drift for 15 seconds and then hold still.
- File, folder, and other storage models are marked stable for Compose through a stability configuration file. The previous homemade annotation was ignored by the compiler, so lists rebuilt unchanged rows.
- Saving in the text editor no longer reads the file back. Local files are staged, synced, checked by length, and swapped in atomically, with the original guarded by its size and timestamps instead of a full copy in memory. The recovery draft is written once per save instead of twice. Documents from other apps' providers are still read back once, since a provider can silently keep old content.
- Storage, vault, audio, backup, and operation classes take their background threads from one shared, injectable dispatcher set instead of creating their own. Storage scans share one two-thread pool everywhere, and an architecture check keeps hardcoded IO threads out of the data layers.
- Hardcoded UI text is now checked by an architecture test instead of a separate Gradle plugin with its own Kotlin tokenizer. The new check also catches icon descriptions and named text arguments that the old one missed.
- Release builds shrink and rename more code. The rules that kept every serializable class and every image loader whole are gone; the libraries' own rules cover what is actually needed, and crash traces now include line numbers. The one screen check that compared routes by class name now matches by route type, so renaming is safe.
- The manifest drops its package query list: the all-packages permission a file manager needs for APK versions and folder app icons already makes every app visible. The reason is now written next to the permission.
- Backup rules list each excluded path once and explain what is excluded and why. A test checks that both backup rule files stay identical.
- Gradle builds run modules in parallel and reuse the build and configuration caches. Compose compiler reports are written only when asked for.
- The website loads a prebuilt stylesheet instead of compiling Tailwind in every visitor's browser. The navigation bar changes style through an observer instead of a scroll listener, the phone mockup's float and the other animations stop when Reduce Motion is on, and two unused images are removed.
- The developer guide is shorter and matches the current code, including the plugin contract, permissions, size limits, and release steps.

### Fixed
- Cancelling an action or leaving a screen no longer shows an error in 77 places across audio, viewers, vaults, backups, APK tools, and plugins.
- Two onboarding tests expected outdated capitalization for the theme labels.
- Tapping the notification of a video opened from another app reopens that video instead of Arcile's home screen. Videos played inside Arcile still return to where you were.
- The APK installer, batch rename, accent color preview, storage cards, and video viewer showed English text in every language. These 23 labels now come from string resources.

### Removed
- Unused code and resources: a video overflow menu, audio track sheet, mixed-delete dialog, tab indicator, viewer session registry, progress smoother, three onboarding pages, two settings sections, archive option types, a vault streaming writer, two pass-through use cases, and 192 strings.
- IDE settings files tracked by mistake, a duplicate inner .gitignore, and the rule that ignored every .txt file in the repository.

## [2.4.6] - 2026-11-07

### Added
- Select several vault tracks and choose Edit audio to combine them in the shared audio editor, using in-memory copies.

### Changed
- Choose folder icons with Plain, Special, and App buttons. Plain uses one icon for every folder, Special marks folders such as DCIM, Pictures, Download, and Recordings, and App uses the related app's icon; Special and App can be combined. The previous single switch carries over to both.
- Choose which item details appear under file and folder names: Count, Size, Date, and Time. Rows adjust to what is shown: a time or date alone sits beside the name, and with nothing shown the name is centered. Grid cards keep equal heights.
- Architecture checks pass again: the class graph is imported once per run, vault grant and file reads moved out of ViewModel and composable code, Trash parsing preserves cancellation, and remaining oversized files are capped at their current size.

### Fixed
- Folders inside archives opened in Browser show their file count and size instead of calculating forever.

## [2.4.5] - 2026-11-07

### Added
- Undo vault pastes, cross-vault copies and moves, and imports when every copied item is unchanged.
- Undo Move into vault, Export, Move out, and Move to main Trash after a confirmation that explains which plaintext is written or removed.
- Print vault PDFs after confirming that the system print service receives decrypted pages.
- Edit MP3 and FLAC music details and artwork in vaults. Edits run in memory and save as a new encrypted revision.
- Timed lyrics for vault tracks, stored in an encrypted LRC file beside the track, with the shared lyrics view.
- Trim, extract, and combine vault audio in the shared audio editor using in-memory copies; results save as new encrypted files.

### Changed
- The music details editor, lyrics view, and lyrics parser moved into shared UI so Browser and vaults use one implementation.
- Archive, audio editor, and vault text editor errors now use localized messages instead of English exception text.

## [2.4.4] - 2026-11-07

### Changed
- Extract vault archives into a named folder, the current folder, or any existing vault folder using Browser's extraction dialog.
- Vault extraction reviews name conflicts like other vault transfers and publishes all changes at once; failures and cancellation leave the destination unchanged.
- An existing extraction folder is reused and merged instead of creating a numbered copy, and selected entries keep their archive folders, matching Browser.
- Partial extractions report extracted, skipped, and failed counts, with Show destination when anything was written.

## [2.4.3] - 2026-11-06

### Changed
- Create password-protected 7z archives inside vaults without plaintext temporary files.
- Browser and vault ZIP and 7z creation share compression options, cancellation, and source-size checks.
- Vault PDF menus now offer rename, copy, cut, archive creation, and deletion through vault actions.

## [2.4.2] - 2026-11-06

### Changed
- Undo file and folder creation, renames, batch renames, and reversible moves inside a vault. Changed items and name collisions prevent unsafe reversals.
- Create TAR, TAR.GZ, TAR.BZ2, and TAR.XZ archives inside vaults using Browser's shared streaming writer and compression options.
- Create archives from vault image and video menus with private dialogs and operation progress.

## [2.4.1] - 2026-11-06

### Changed
- Interrupted folder retries remember each published child across restarts, including retries started in the current session.
- Uncertain folder publications stay available for review while safe queued selections can resume.
- Recovered imports offer Show destination. Locking expires earlier Move retry handles.

## [2.4.0] - 2026-11-06

### Changed
- Recover interrupted vault imports, pastes, and exports with verified publication records. Recovered moves keep remaining originals.
- Save partial-folder retry checkpoints across restarts and safely clean up owned export stages or restore known backups.
- Show destinations after recovered vault pastes and exports.

## [2.3.9] - 2026-11-05

### Changed
- Choose when vaults lock after backgrounding and whether screen-off locks immediately.
- Prefer a numeric password keyboard with a text-keyboard fallback.
- Improved vault operation and health messages, including feedback from the video viewer.

## [2.3.8] - 2026-11-05

### Changed
- Choose Copy or Move when sending Browser selections to OnlyFiles.
- Vault transfers use Browser clipboard messages and snackbar presentation, with destination actions and clearer partial-result and error messages.

## [2.3.7] - 2026-11-05

### Changed
- Opening vault audio starts playback in Arcile's full player with cover art, music details, shuffle, repeat, speed/pitch, sleep timer, and visualizer.
- Vault audio shares playback preferences with Audio.

## [2.3.6] - 2026-11-05

### Changed
- OnlyFiles uses Browser's archive screen and creation dialog, including in-archive search, selection, and filename encoding.
- Fixed archive Root navigation, selection Back handling, and file-action menu placement.
- Archive password fields use password keyboards with autocorrect disabled.

## [2.3.5] - 2026-11-05

### Changed
- Rename, copy, cut, and delete vault videos from the shared viewer.
- Vault audio uses the shared queue sheet with reordering, removal, and continuous playback between tracks.
- Portable vault search reuses ranked pages and detects changed folders.

## [2.3.4] - 2026-11-04

### Changed
- Browser and OnlyFiles share archive readers and extraction checks.
- Browse and extract TAR, GZIP, BZIP2, and XZ archives inside OnlyFiles, including compressed TAR variants.
- Open password-protected 7z archives and choose ZIP filename encoding inside OnlyFiles.

## [2.3.3] - 2026-11-04

### Added
- Resume queued items from interrupted imports, exports, and vault pastes after unlocking OnlyFiles.
- Recovery skips the item that was in progress.

## [2.3.2] - 2026-11-04

### Changed
- Resume partial OnlyFiles pastes and exports from the files that were skipped.
- Resuming Move Out verifies earlier exports before removing the encrypted original.

## [2.3.1] - 2026-11-04

### Changed
- OnlyFiles and Browser share built-in file routing.
- App-private vault search reuses ranked results across pages while the vault is unlocked.
- Create and extract password-protected ZIPs, and browse and extract unencrypted 7z archives inside vaults.
- Browse more images while swiping and carry viewer selection back to the listing.

## [2.3.0] - 2026-11-04

### Changed
- Move vault items to main Trash after confirming decryption.
- Create verified encrypted backups and restore them as separate vaults.
- Open APKs and split packages from OnlyFiles in Arcile’s shared installer after granting temporary access.
- Recover committed transactions from the vault health report.

## [2.2.9] - 2026-11-04

### Changed
- Pin vaults to keep them at the top of the OnlyFiles list.
- Send local Browser selections to an unlocked vault folder, or move local files from OnlyFiles after verifying their encrypted copies.
- See vault storage use and revoke individual external access grants from the vault menu.
- Reset a vault’s folder views together.

## [2.2.8] - 2026-11-04

### Changed
- Enable Workspace tabs in OnlyFiles to keep separate folders, searches, and scroll positions open in a vault.
- OnlyFiles uses Browser's tab controls.

## [2.2.7] - 2026-11-04

### Changed
- OnlyFiles paste and export use Browser's comparison dialog to review known conflicts before transferring.
- Import, export, and portable vault setup share storage picker rows.

### Fixed
- Move Out keeps the source folder in the vault when any child is skipped.

## [2.2.6] - 2026-11-04

### Changed
- OnlyFiles Select All and Invert include results across pages.
- Open As and saved open preferences use the Android chooser after vault consent.
- Properties shows recursive selection totals and private image metadata.

## [2.2.5] - 2026-11-04

### Changed
- OnlyFiles creates ZIPs from selected files and folders directly in the vault.
- Extract an entire ZIP, its current folder, or selected entries into an encrypted folder.

## [2.2.4] - 2026-11-04

### Changed
- OnlyFiles restores the last folder after unlock, supports pinned folders, and lets you choose a search folder and reveal results in their parent.
- Added the shared scrollbar, folder view reset, random file creation, and ZIP folder browsing.

### Fixed
- Copy and cut survive switching between unlocked vaults.
- Open viewers refresh after rename or text save.

## [2.2.3] - 2026-11-04

### Changed
- OnlyFiles shares Browser's navigation, selection, file dialogs, clipboard, and transfer details.
- Vault folders remember sort and view settings, with optional inheritance.

### Fixed
- Vault folders show file counts and sizes when available.
- Paste retries skip completed items, and paste queue names stay within the initiating vault session.

## [2.2.2] - 2026-11-04

### Changed
- OnlyFiles uses Gallery's swipeable image viewer and Arcile's video and PDF viewers.
- OnlyFiles lists ZIP contents and asks before handing an APK to Android's installer.

### Fixed
- Vault previews and private dialogs clear on lock.
- Vault video strips load encrypted thumbnails and show a play icon when a frame is unavailable.

## [2.2.1] - 2026-11-02

### Changed
- **Dependency Updates:** Upgraded Android build tooling and app libraries, including Coil 3, Compose, Navigation, and Media3, with the required image-loading.
- **License:** Updated the project license to Tremors Source License (TSL) Version 2.0.

## [2.2.0] - 2026-10-04

- **Audio Library:** Remove song overflow buttons and Genres navigation.
- **Albums:** Show track lists, song counts, and other albums by the same artist.
- **Player:** Align the mini player with five-tab navigation and hide both on scroll.
- **Lyrics:** Add inline and full screen lyrics with line seeking, playback following, LRC offsets, and a dedicated editor.
- **Music Details:** Use spacious rounded editor fields, aligned actions, and metadata, listening history, and file information cards.
- **Sound Settings:** Add media volume controls, finer speed and pitch adjustments, and speed presets.
- **File Replacement:** Keep copied replacements intact when cleanup of the old backup fails.

## [2.1.9] - 2026-10-04

- **Browser Entry:** Resume the remembered folder when swiping into Browser, while storage shortcuts open their selected root.
- **Audio Queue Removal:** Advance in playback order, including shuffle, preserve paused playback, and clear an empty player.
- **Image Deletion:** Advance to the next image, or the previous at the end, and retain the position after rotation.
- **Vault Video Rotation:** Keep protected videos open during rotation and close them when backgrounded.
- **APK Install Status:** Clear stale confirmation messages, retain progress through rotation, and report final installation results accurately.
- **Library Grids:** Make APK and Documents grid-size controls match the displayed layout.
- **Text Editor Panels:** Use opaque controls and a compact, scrollable information card with an accessible close button.
- **Easier Renaming:** Focus and select the name while preserving file extensions and user-adjusted selections.
- **Paste Status:** Show compact item counts with full accessibility announcements.
- **Accessible File Labels:** Make file details and opening and selection labels translatable.

## [2.1.8] - 2026-10-04

- **Browser Responsiveness:** Show folder contents before logging, indexing, and saved folder-size lookups finish.
- **Responsive Biometrics:** Prepare and complete biometric vault access in the background, preserve cancellation, and reject pending challenges after locking.
- **Bounded Text Editing:** Limit documents and recovery drafts to 4 MiB, cap combined Undo/Redo history at 8 MiB of text, and keep oversized recovery drafts with clear feedback.
- **Markdown Selection:** Format text selected in either direction without crashing, with working Undo and Redo.
- **Safe Viewer Startup:** Keep standalone viewers from cleaning shared operations, and preserve temporary files and compatibility copies owned by live processes.
- **Accurate Restore Undo:** Undo the actual restored files, including renamed conflicts and partial restores.
- **Recoverable Text Saves:** Verify staged local saves before replacing originals, preserve durable drafts before provider writes.
- **Reliable Text Editor State:** Keep document contents and editing history out of Android saved state while retaining edits across screen recreation.
- **Vault Transaction Recovery:** Reject oversized operations before committing and finish authenticated older transactions that exceeded collection limits.

## [2.1.7] - 2026-10-03

- **Safe Trash And Restore:** Preserve complete verified copies and trash recovery information when source cleanup fails or is cancelled.
- **Safe Vault Folder Moves:** Keep skipped files and their folders at the source during merged moves within or between vaults.
- **Safe File Replacement:** Keep existing destinations safe when moves fail, verify replacement contents with checksums.
- **Verified Move Recovery:** Recheck recorded file identities and contents before retrying source cleanup.
- **Clearer Labels:** Call gallery groups Folders and generated random files Synthetic Files, while preserving saved settings and pending operations.
- **Maintenance:** Update internal naming and organization while preserving existing features and saved preferences.

## [2.1.6] - 2026-09-28

- **Music Library Layouts:** Show artwork, artist, album, duration, and song counts across Audio pages, with alphabetical songs by default and direct actions for favorites.
- **Listening Library:** Save favorites, recent plays, and play counts in a dedicated Audio database.
- **Player and Lyrics:** Place the mini player above Audio navigation, keep its expand and collapse motion, and give timed lyrics a full screen view with seek.
- **Library Stability:** Update visible page content together with its filters and keep artwork tied to the correct song while lists change.

## [2.1.5] - 2026-09-28

- **Integrated Audio Player:** Keep the mini player inside Arcile's layout so navigation, text fields, and Android gestures remain available.
- **Playback and Queue Controls:** Restore the paused queue and position after relaunch, return to the player from its notification, show the real shuffle order, and add queue editing.
- **Audio Library Updates:** Refresh on MediaStore changes, offer a saved Music only filter, smooth search updates.

## [2.1.4] - 2026-09-28

- **Music Library Pages:** Browse Songs, Folders, Albums, Artists, Genres, and Playlists inside Audio.
- **Playlists and Collections:** Create, rename, delete, and reorder playlists; add selected songs and open collection details with artwork, song counts, and playback controls.
- **Music Details and Lyrics:** Edit song tags and artwork in supported audio files, with verification and a recovery copy during writes.

## [2.1.3] - 2026-09-28

- **New Audio Player Layouts:** Give the mini player circular artwork, a progress ring, and grouped previous and next buttons.
- **Player Motion and Themes:** Animate track changes, playback controls, and mini player expansion on tap or swipe.
- **Playback Visualizer and Layout:** Show audio-responsive bars in the play/pause button with an on/off switch.
- **Storage Settings:** Show connected storage volumes and classification controls directly in Settings alongside temporary file cleanup.

## [2.1.2] - 2026-09-27

- **Settings Pages:** Organize controls into focused pages with clearer section names and descriptions.
- **Compact Choices:** Keep the theme mode tiles and show start page, theme preset, filename, and extension choices in compact rows that scroll when needed.
- **File Opening:** Choose which supported or installed plugin extensions open in Arcile, and add or remove custom extension exclusions.
- **Settings Stability:** Fix the Appearance crash when choices overflow on narrow screens.

## [2.1.1] - 2026-09-26

- **Stable Home Dashboard:** Show saved recent files and category totals immediately, then quietly check live storage on a cold launch.
- **Recent File Cache:** Remove obsolete saved preview lists after successful refreshes while keeping the latest fallback for the next launch.
- **Persistent Folder Details:** Keep saved folder sizes visible while recalculating folders shown after a new launch.
- **Consistent Collapsed App Bars:** Keep locked app bars in their natural collapsed position with the compact title visible, and prevent dragging them open until expansion is enabled.
- **Home Cache and Scrolling Corrections:** Show saved category totals before quiet background updates, restore the multicolor loading bar with a smoother segment reveal.

## [2.1.0] - 2026-08-28

- **Adaptive Primary Workspace:** Added persistent navigation and automatic dual-pane browsing for larger displays and foldables, adapting smoothly to live resizing and fold postures.
- **Refined Conflict Resolution:** Compacted file conflict dialogs with circular thumbnails, single-row metadata, full-width path labels, and side-by-side action buttons.
- **Consistent Viewer Actions:** Added capability-aware file actions (rename, copy, move, delete, share, open-with, archive, properties) across all media viewers, PDFs.
- **Predictable Storage Cleaner:** Replaced candidate checkboxes with long-press selection, added filename-version grouping, expanded duplicate comparisons.
- **App-Aware Folder Icons & Ordering:** Added an opt-in toggle for app-matched folder icons and a persisted setting to group folders above files across all sort modes.
- **Improved Browsing & Presentation:** Aligned grid sizing to discrete density steps, preserved cached folder statistics during background refreshes.
- **Signed Update Discovery:** Added on-device update checks for Arcile in About and compatible plugin updates in Plugins with rate-limited notifications.
- **Smoother Startup Performance:** Deferred disk reads and preference storage initialization during composition.

## [2.0.9] - 2026-08-26

- **Predictable App Gestures:** Added shared touch-slop, direction-lock, velocity, cancellation, and system-edge rules so Browser swipes, Gallery viewer gestures.

## [2.0.8] - 2026-08-25

- **Precision Audio Editing:** Added zoomable waveforms, millisecond boundary controls, scrubbing, looped selection previews, section extraction, middle removal.
- **Reliable Audio Selection:** Kept waveform handles attached throughout a drag, centered high zoom levels on the playhead, and added a Classic slider option without the waveform.
- **Smarter Audio Editor:** Added a compact theme-aware layout with artwork and file details, side-by-side boundary controls, default-on Loop and Waveform switches.

## [2.0.7] - 2026-08-24

- **Protected Vault Viewing:** Kept screenshot protection active while moving from OnlyFiles into vault video viewers, including overlapping screen transitions.
- **Accurate Vault Unlock State:** Prevented stale unlock prompts after returning from an already unlocked vault file or completing biometric unlock.
- **Reliable Background Operations:** Stopped timed-out file operations and vault imports cleanly with visible failure feedback, and removed their notifications after completion, failure.
- **Safe Settings Restore:** Bounded settings backup imports, verified their contents before changing live preferences.
- **Reliable Shared Imports:** Reserved reclaimable storage before shared-file imports, staged each batch before exposing files.
- **Predictable Thumbnail Scrubbing:** Made held thumbnail strips follow the finger directly without disappearing or jumping outside the available queue.
- **Complete Gallery Batch Rename:** Added Rename to Gallery multi-selection and connected it to the transactional batch-rename flow.
- **Reliable Recents:** Repaired grouped list and grid selection, kept range selection anchored to stable files, enforced newest-first ordering.
- **Dependable File Renames and Browser Preferences:** Fixed case-only file and folder renames and kept the selected apply-to-subfolders sort scope when reopening Browser options.
- **Non-Blocking Mini-Player:** Let back gestures, text fields, and the keyboard work outside the collapsed player.
- **Selectable Search Results:** Restored long-press and range selection in Browser search results and replaced search-header back arrows with clear close icons.

## [2.0.6] - 2026-08-22

- **Clean Modern Android Build:** Updated Android and Compose integrations, removed obsolete packaged resources, and cleared compiler and static-analysis warnings across the app.

## [2.0.5] - 2026-08-21

- **Responsive Text And App Bars:** Prevented selection summaries, page titles, browser tabs, segmented controls, and sort labels from clipping on compact or large-text layouts.
- **Tab-First Browser Gestures:** Made content swipes move through enabled browser tabs before crossing pane boundaries, while app-bar swipes continue to switch panes directly.
- **Focused Root Access:** Kept Root Storage exclusive to Home Quick Access by removing its swipeable storage-summary page and Add Tab shortcut.
- **Vector Splash Branding:** Switched the startup splash foreground from a raster mipmap to Arcile's VectorDrawable for crisp rendering at every display density.
- **Flexible Opening And Folder Sorting:** Added browser selection options to open files explicitly as images, videos, audio, documents, archives, text, or other files.
- **Surrounding-Aware Media Navigation:** Preserved the visible audio-category order in playback queues and added hold-and-drag fast scrolling to image and video thumbnail strips.

## [2.0.4] - 2026-08-21

- **Chronological Activity Timeline:** Reworked Activity into a Recents-style history grouped under Today, Yesterday, and calendar dates, covering visited pages, opened files and folders.
- **Universal Collapsed App Bars:** Added a preference to keep supported app bars compact while preserving their normal expanded-to-collapsed scrolling when disabled.
- **Centered Landscape Viewer Strips:** Kept the active image or video thumbnail centered, inset the vertical strip from the safe display edge.

## [2.0.3] - 2026-08-21

- **Browser Preferences:** Added dedicated Browsing settings for remembering the last folder and keeping browser app bars compact, with a shared app-bar state across tabs.
- **Complete Activity History:** Expanded Activity to include opened files alongside folders and file operations.
- **Responsive Browsing And Viewers:** Added pinch-to-resize browser grids and moved image and video thumbnail strips to a vertical layout in landscape orientation.
- **Clearer Feedback And Properties:** Made feedback messages swipe-to-dismiss with distinct icon and action containers, removed the close button, and consolidated duplicate file.
- **Cleaner Startup:** Updated the splash screen to show only Arcile's foreground logo without the launcher icon background.

## [2.0.2] - 2026-08-15

- **Simplified Storage Architecture:** Removed optional Root and Shizuku access and their provider-specific workflows.
- **Long-Press New Tab Storage Selection:** Added a storage volume and root chooser dropdown menu when long-pressing the `+` button in the browser tab bar.
- **Settings Page Controls:** Added the Browser tabs toggle and Start page selector (Home vs Browse) directly to the Appearance settings page.

## [2.0.1] - 2026-08-15

- **Simpler Storage Access:** Detects and uses Root automatically, restores the v1.9.0 Root icon, replaces provider selection with one optional Shizuku switch.
- **Shizuku Status Badge:** Shows the installed Shizuku app icon beside app-bar actions while Shizuku is active, with an Appearance setting to hide it.
- **Reliable APK Updates:** Opens the unknown-app-sources permission screen when an update needs it.
- **Video Playback Fixes:** Added a visible back button, prevented notification-shade swipes from triggering player gestures, and fixed black video playback in OnlyFiles.
- **Browser And Cleaner Accuracy:** Fixed newest and oldest sorting across files and folders, labels empty-folder results as folders.
- **Browser Location Accuracy:** Keeps Root Storage paths distinct from normal volumes, shows the current folder name in the app bar, and preserves correct Root Storage names.

## [2.0.0] - 2026-08-13

- **Video Playback:** Kept titles clear of display cutouts, preserved gallery order and the selected thumbnail position.
- **Interface Clarity:** Added an easy-to-tap total/remaining video timer and active resize-mode feedback, explained OnlyFiles health checks, improved image-decoding errors.
- **Runtime Compatibility:** Kept Android 15-only PDF text search behind an explicit platform guard and made viewer and editor feedback follow configuration changes safely.
- **Compatible Dependency Refresh:** Updated the Android, Kotlin, Compose, Hilt, KSP, Media3, security, and test libraries that remain compatible with the Gradle 9.5.0 toolchain.

## [1.9.10] - 2026-08-11

- **Storage Provider Setup:** Added Automatic, Root, Shizuku, and Normal Android selection to onboarding and Settings, with live connection state, effective privilege, reconnect.
- **Protected Write Controls:** Added a Root-only protected app-data write preference with an explicit safety warning while keeping system partitions.
- **Provider-Aware App Access:** Allowed Arcile to open through a ready Root or Shizuku connection without requiring Normal Android all-files permission.
- **Context-Preserving Recovery:** Kept the current browser location and in-progress interface state visible when Root, Shizuku, or Normal access disappears, with clear reconnect.
- **Explicit Normal Fallback:** Added a user-controlled switch from protected storage to Normal access that waits for permission.
- **Reliable Normal Recovery:** Fixed the explicit Normal fallback after Root or Shizuku access is lost so it leaves the protected location.
- **Scoped Protected Folder Tools:** Limited folder analysis and cleanup shortcuts to Root and Shizuku locations, with labels that clearly identify the current folder as their scope.
- **Interruption-Safe Storage Recovery:** Preserved coroutine cancellation during archive rollback, cross-backend streams, protected content access.

---

## [1.9.9] - 2026-08-11

- **Protected File Viewing:** Added secure Root and Shizuku previews, playback, thumbnails, PDF viewing and printing, APK inspection.
- **Read-Only External Handoffs:** Added fresh, time-limited grants for opening and sharing protected files with other apps without exposing storage paths or write access.
- **Safe Protected Text Editing:** Added atomic saves for editable protected text files, preserving the original when writing, syncing, or replacement fails.

---

## [1.9.8] - 2026-08-11

- **Explicit Protected Storage Analysis:** Added folder-level Root and Shizuku usage maps and cleaner entry points while keeping protected areas opt-in instead of scanning them automatically.
- **Descriptor-Based Cleaner Verification:** Added bounded protected traversal, large-file detection, duplicate sampling, full-content verification, cycle handling.
- **Backend-Safe Cleaner Recovery:** Preserved protected file identity in cached cleaner results, Trash operations, undo refreshes, rule changes.

---

## [1.9.7] - 2026-08-11

- **Protected Archive Workflows:** Added Root and Shizuku browsing, metadata, conflict detection, extraction, and creation for ZIP, TAR-family, and 7z archives.
- **Safe Cross-Backend Publication:** Added private compatibility workspaces, recognizable partial outputs, final renames, conflict-safe folder aliases, replacement rollback.
- **Persistent Archive Identity:** Kept archive and destination backends through browser history, restored sessions, properties, durable foreground requests.

---

## [1.9.6] - 2026-08-11

- **Backend-Aware Privileged Trash:** Added same-volume Trash support for Root and Shizuku user-storage files and folders.
- **Safe Move And Restore Recovery:** Stored app-private recovery metadata before each move, reconciled interrupted outcomes, avoided identifier collisions.
- **Reconnect-Aware Trash:** Kept cached privileged Trash items visible while their backend is unavailable, blocked unsafe restore attempts with clear guidance.

---

## [1.9.5] - 2026-08-11

- **Protected Folder Search:** Added recursive search with existing filters inside the current Root or Shizuku directory, while preserving each result's original backend.
- **Bounded Privileged Scans:** Added depth, entry, result, and duration limits with cycle detection, inaccessible-branch handling, and cancellation-safe traversal.
- **Stable Cached Identity:** Preserved canonical backend identity and capabilities in cached file and usage results, including Unicode, newline, and delimiter-containing paths.

---

## [1.9.4] - 2026-08-11

- **Privileged File Details:** Added backend-aware properties and folder totals for Root and Shizuku files, including partial-access reporting, cycle protection, bounded scans.
- **Reliable Batch Rename:** Kept Root and Shizuku identity through batch staging, rollback, completion, and undo instead of retrying protected files through local storage.
- **Consistent Folder Insights:** Loaded cached folder sizes and background totals while browsing privileged directories.

---

## [1.9.3] - 2026-08-11

- **Safe Cross-Backend Transfers:** Added staged descriptor streaming between Normal, Root, and Shizuku storage, including nested folders, conflict choices, partial-output cleanup.
- **Persistent Privileged Browsing:** Kept Root and Shizuku directory identity through folder navigation, history.
- **Backend-Aware File Actions:** Preserved storage identity through foreground copy, move, delete, shred.

---

## [1.9.2] - 2026-08-11

- **Reliable Backend Selection:** Added passive Root-to-Shizuku-to-Normal selection, explicit-mode failure handling, backend death recovery.
- **Backend-Aware Storage Routing:** Preserved Root and Shizuku identity in listed files, bounded remote directory pages, and routed retained references.
- **Protected Privileged Paths:** Kept system and virtual filesystems read-only, protected storage roots and Arcile private areas.

---

## [1.9.1] - 2026-08-11

- **Secure Privileged Access Core:** Added capability-verified Root and Shizuku service connections with effective-UID checks, bounded directory pages, descriptor-based file access.
- **Safe Remote File Operations:** Kept recursive deletion from following symbolic links, rejected traversal and special-file misuse, bounded Binder payloads.

## [1.9.0] - 2026-08-09

- **Safe Cross-Storage Moves:** Kept fully verified destination copies when source cleanup only partially succeeds, recorded the remaining cleanup for safe retry.
- **Responsive External Viewers:** Opened shared images, PDFs, videos, and audio without blocking the interface on storage providers, with readability checks, timeouts.
- **Reliable Large Operations:** Stored bounded bulk-operation requests privately and handed services only a durable ID.
- **Safe Deep Folder Handling:** Made storage analysis and directory copies cancellable and cycle-aware without recursive stack growth.
- **Temporary Share Access:** Released only the document permissions acquired for Save to Arcile after rejection, completion, failure, cancellation.

## [1.8.9] - 2026-08-09

- **Consistent Customization Ordering:** Added item-specific move controls across Utilities, Quick Access, and Home layout, with immediate Quick Access saves, position feedback.
- **Responsive Date Filters:** Kept date-range fields and actions reachable in short, landscape, large-text, and keyboard-constrained windows.
- **Localized Audio Favorites:** Made the Audio favorites folder title and search follow the current app language, including live locale changes while the library remains open.
- **Correct Count Grammar:** Fixed singular and plural wording across file, media, storage, backup, cleaner, and progress surfaces, with locale-aware quantity handling.
- **Faster Secure Startup:** Removed recursive plaintext-share cleanup from app startup while still making old compatibility copies inaccessible immediately.
- **Private Recovery Records:** Moved operation and mutation recovery data out of backups, removed archive passwords from persisted records, bounded retained data.
- **Safer Compressed Extraction:** Enforced real expanded-size, compression-ratio, cancellation, and free-space limits for GZIP, BZIP2, and XZ files without leaving partial output.
- **Safer Split-APK Installs:** Bounded split-package staging, extracted only device-compatible APKs into unique temporary folders.
- **Lighter Background Transfers:** Coalesced high-frequency file-operation progress so notifications and durable recovery checkpoints stay useful.

## [1.8.8] - 2026-08-09

- **Complete Search Overhaul:** Extended the redesigned search pill to Audio, Images, Video Gallery, APKs, Documents, shared category pages, and archive browsing.
- **Reliable Filter-Only Results:** Restored filter-only search state, applied filter-sheet changes only after confirmation, included requested hidden MediaStore results.
- **Polished Viewer Behavior:** Kept the notification shade and navigation bar hidden during video playback, locked every vault when Arcile is backgrounded or OnlyFiles is exited.
- **Smoother, Consistent Feedback:** Animated Browser tab space while collapsing and standardized the remaining category snackbar presentation.
- **Timely File Operation Feedback:** Showed copy and cut confirmation on the initiating screen, prevented completed operations from resurfacing elsewhere.

## [1.8.7] - 2026-08-09

- **Complete PDF Controls:** Added pinch zoom, highlighted native text search on supported Android versions, a keep-screen-on toggle, and system printing.
- **Reliable Video Playback:** Kept immersive video playback awake, restored hidden system bars, and safely closed encrypted OnlyFiles videos when the device locks.
- **Roomier Browser and Search:** Made Browser tabs slide away while scrolling and return on downward gestures, redesigned every search bar, and enabled filters without typed text.
- **Consistent Feedback:** Routed all 22 activity-level toast fallbacks through one shared presentation.

## [1.8.6] - 2026-08-09

- **Natural Cleaner Refresh:** Added pull-to-refresh to the cleaner overview and every cleaner page, retained detailed scan progress, and removed refresh buttons.
- **Full Cleaner Pages:** Replaced cleaner bottom sheets with full navigation pages that preserve selections, duplicate comparisons, settings, and return state.
- **Reliable Cleaner State:** Kept refresh feedback continuous, enabled refresh and retry on empty pages, safely revalidated selections while results reload.

## [1.8.5] - 2026-08-08

- **Faster Cleaner Scans:** Added cached, per-category scans with live wavy progress and ETA, optimized for large storage.
- **Reliable Cleaner Results:** Removed deleted and stale files immediately, skipped Android-protected folders quietly, and fixed thumbnail-cache size reporting.
- **Preserved Cleaner Navigation:** Kept the active cleaner, duplicate comparison, selections, and dialogs when returning from previews or folders.

## [1.8.4] - 2026-08-08

- **Configurable Browser Tabs:** Made tabs disabled by default with a Browser overflow toggle; hiding them preserves each Browser page's tab state.
- **Landscape Dual Pane:** Added an optional Appearance setting that shows Browser 1 and Browser 2 side by side at equal width in landscape.
- **Refined Browser Tabs:** Added left/right ordering actions, reduced the tab strip's vertical footprint, and added leading spacing before the first tab.

## [1.8.3] - 2026-08-08

- **Independent Browser Tabs:** Added click-controlled tabs inside each existing Browser page, with independent locations, real folder, category, and archive names.

## [1.8.2] - 2026-08-08

- **Custom Home Layout:** Added an Edit Home overflow action with a card dialog for showing, hiding, resetting, and drag-reordering Storage, Categories, Quick Access.
- **Consistent Home Spacing:** Standardized section gutters and vertical spacing, omitted the top title when no header action is needed, and retained clear titles beside Manage.

## [1.8.1] - 2026-08-08

- **Root Storage Browsing:** Added an opt-in Root Storage shortcut for browsing system paths available through standard Android permissions, without root or Shizuku.
- **Root Storage Usage:** Added a continuously swipeable Home card with matching layout and a System legend, loaded only when shown.
- **Protected Location Handling:** Added back navigation outside detected storage volumes, reported denied folders instead of showing misleading empty results.

## [1.8.0] - 2026-08-02

- **Release Readiness:** Synchronized 1.8.0 versioning, build metadata, task tracking, and user-facing release notes for the v1.7.1 through v1.8.0 development cycle.

## [1.7.9] - 2026-08-02

- **Two Browser Workspaces:** Added a second independently stateful Browser page after the existing Browser, so another swipe from Home provides a separate location, history.
- **Preferred Start Page:** Added Home and Browser launch choices to the main overflow menus, using the same selected-default treatment as category pages.
- **Preserved Browser Context:** Kept the active Browser workspace when returning from a file viewer, preserved normal parent-folder back navigation in both workspaces.
- **Per-Folder Scroll Memory:** Restored each folder, category tab, archive location, and large paged listing to its previous list or grid position after navigating away.

## [1.7.8] - 2026-08-02

- **Markdown Preview and Editing Tools:** Added formatted Markdown preview, source formatting actions, undo and redo, and document statistics.
- **Arcile Editor Chrome:** Matched Arcile's viewer controls and segmented overflow menu, honored OLED backgrounds, let content scroll beneath the transparent chrome.
- **Consistent Selection Chrome:** Centered selection content and aligned Docs and APK split actions, overflow controls, and screen-edge spacing with the browser and other categories.

## [1.7.7] - 2026-08-02

- **Native Text and Markdown Editing:** Added a standalone source editor activity for Arcile and other apps, with read-only handling for view-only grants.
- **Document Saving and Draft Recovery:** Verified saved content before reporting success, preserved newer edits made during a save, restored matching unsaved drafts after interruption.

## [1.7.6] - 2026-07-30

- **Reliable Player Motion:** Fixed mini-to-full and full-to-mini transitions for taps and swipes, added swipe-up expansion and swipe-down dismissal.
- **Stable Playback Surfaces:** Kept mini-player artwork stable while tracks change and added a close action to playback notifications.
- **Finished File Operations:** Removed progress notifications after file and vault operations complete or are cancelled, including concurrent operations and stale progress updates.

## [1.7.5] - 2026-07-29

- **Completed Seven-Category Parity:** Finished Gallery-shell integration for APKs and the remaining category routes, removed startup and folder-content flashes, matched selection.
- **Clean Stable Thumbnails:** Kept circular thumbnails exclusive to lists, removed APK icon background layers, made PDF previews cover document folder tiles.
- **One Global Audio Player:** Unified mini and full playback in one exported player that opens as a touch-through floating mini-player, expands.
- **Focused Category UI:** Removed the redundant containing-folder audio action and non-selection folder overflow controls, kept folders grid-only.

## [1.7.4] - 2026-07-29

- **Documents And Native PDF Viewing:** Added a dedicated Documents library with Gallery-style files and folder navigation, stable document thumbnails.
- **Independent Category State:** Persisted category presentation, grouping, item and folder preferences independently, introduced stable category IDs and names.

## [1.7.3] - 2026-07-29

- **Reusable Category Browser:** Added the common file-and-folder category surface with Gallery-matched list and grid layouts, date grouping, fast scrolling, search, refresh.
- **Consistent Category Actions:** Unified copy, cut, paste, rename, delete or shred, ZIP, share, properties, and Open With workflows while keeping circular previews list-only.

## [1.7.2] - 2026-07-29

- **Gallery-Parity Audio Library:** Aligned Audio list and grid spacing, date sections, floating chrome, selection actions, folder navigation.
- **Audio-Specific Presentation:** Kept circular artwork in lists, rectangular artwork cards in grids, rich artist, album, duration, and size metadata, grid-only folder browsing.

## [1.7.1] - 2026-07-28

- **Shared Gallery Shell:** Extracted the Image Gallery 1.6.1 floating chrome, list and grid spacing, section headers, selection motion.
- **Gallery Reference Migration:** Moved Images and Videos onto the shared implementation without changing their established behavior.

## [1.7.0] - 2026-07-26

- **Refined Audio Player:** Rebuilt the mini and full player as one continuous animated surface with shared artwork motion, a persistent mini progress rail.
- **Release Readiness:** Synchronized 1.7.0 metadata and release-facing documentation, published user-focused notes for the complete 1.6.1–1.7.0 development range.

## [1.6.9] - 2026-07-26

- **Consistent Media Categories:** Aligned Audio with the Image and Video galleries through loading and error feedback, fast scrolling, complete selection actions, delete.

## [1.6.8] - 2026-07-26

- **Audio Player and App Integration:** Added the Audio category route, background playback, Arcile-branded system media controls, animated mini and full-player transitions, wavy seeking.

## [1.6.7] - 2026-07-26

- **Audio Library Workflows:** Added selection-aware Audio navigation with copy, cut, paste, rename, share, Open With, containing-folder access, and surfaced mini-player.

## [1.6.6] - 2026-07-26

- **Audio Browsing Interface:** Added adaptive Audio and Folders pages with compact lists, artwork-first grids, latest folder art, search, sorting, grouping, sizing.

## [1.6.5] - 2026-07-26

- **Audio Library Foundation:** Added device-audio discovery, independent Audio preferences, artwork and grouping models, saved list and grid presentation options.

## [1.6.4] - 2026-07-26

- **Independent Categories and Browser Restore:** Kept category screens separate from the swipeable Browser and restored the Browser's last opened folder across launches.
- **Complete File Actions:** Made thumbnails open files and filenames reveal their containing folders, prompt with Open With for unsupported formats.
- **Reliable Batch Deletion:** Added live item counts, accurate current-file details, determinate progress from the first item, and durable completion feedback to Browser.
- **Expressive, Adaptive UI:** Standardized modal cards for compact phones, rotation, and larger displays; rebuilt onboarding controls as compact Material 3 Expressive groups.
- **Cleaner Reliability:** Refreshed cleaner detail lists with aligned actions, immediate single-tap ignores, clearer ignored-item management and scoped exclusion rules.
- **Safer Archives and Complete Backups:** Continued extraction while securely skipping unsafe entries, retained archive-wide safety limits, verified Power Rename history backup.
- **Project Website:** Added compact, icon-led repository and download cards with total and latest release counts, animated live counters, and reduced-motion support.

## [1.6.3] - 2026-07-25

- **PowerRename:** Added batch search and replace, Regex, case changes, numbering, live conflict previews, Undo, safe rollback.
- **Interface Polish:** Improved dropdown contrast and elevation across dialogs and cards.

## [1.6.2] - 2026-07-25

- **Interactive Video Viewer:** Added gesture controls, metadata, auto-hiding playback controls, drag-to-dismiss, resize modes, configuration-safe playback.
- **APK and Split Install Support:** Added secure installation for APKs and complete compatible split sets from `.apks`, `.xapk`, `.apkm`, or multi-selection, with metadata previews.
- **Fast Scrollbar Enhancements:** Synchronized real-time scrollbar drag positions across browser and gallery views.
- **OnlyFiles In-Module Settings:** Relocated vault security options, thumbnail cache management, grant revocations.
- **Official System Notifications:** Standardized background file operations, vault imports, and active external grants with Arcile's official monochrome brand icon.
- **Interface Polish:** Updated plugins supporting description in Settings.


## [1.6.1] - 2026-07-23

- **Expressive Controls:** Unified dropdown styling, standardized split actions and standalone overflow controls at `48.dp`, brought Settings, Quick Access, About.
- **Expressive Accent Color Picker:** Redesigned the Accent Color Selector and Bottom Sheet with Material Design 3 Expressive UI/UX features.
- **Plugins Empty State:** Replaced unavailable “Coming soon” plugin rows with a focused empty state until plugins are installed or available.

## [1.6.0] - 2026-07-23

- **Storage Usage Map:** Added capacity-aware rings, complete folder accounting, instant cached drill-down, theme-adaptive colors, and clearer touch interactions.
- **Trash Experience:** Improved previews and item actions, added confirmed one-item restore, and retained safe recovery when original metadata is unavailable.
- **Browser Reliability:** Prevented category and breadcrumb crashes with very large folder sets, polished the folder selector.
- **Interface Polish:** Unified expressive loading states and aligned video thumbnail navigation with the image viewer.


## [1.5.9] - 2026-07-22

- **Immersive Video Viewer:** Matched the Image Viewer with sibling paging, thumbnail navigation, swipe-up properties, predictive back, favorites, selection, sharing.
- **Video Gallery Category:** Replaced the generic Videos category list with Arcile's gallery experience, including Videos/Albums tabs, timeline grouping, search, view.
- **Large Video Gallery Launch Fix:** Prevented video opens from freezing on large galleries by indexing playback sources once, preparing only the selected video.
- **Video Viewer Edge-Case Reliability:** Preserved gallery selection and return position, kept paused videos paused and the progress bar stable while paging, restored replay.
- **Gallery Resource Hardening:** Reduced image gesture and video progress-update overhead, bounded viewer and gallery caches, reused image metadata.
- **OnlyFiles Responsiveness:** Moved biometric enrollment checks out of composable UI filesystem work.

## [1.5.8] - 2026-07-19

- **Direct Biometric Unlock on Cards:** Locked vault list cards enrolled with biometrics now display an interactive primary-colored fingerprint icon.
- **Biometric Prompt Context Visibility:** Modified vault unlock dialogs to dynamically query local enrollment status.
- **Expressive Loading and Progress Controls:** Replaced legacy flat linear and circular progress loaders with premium Material 3 Expressive shape-morphing `LoadingIndicator` components.
- **Precise Back Gesture Handling:** Refined the `BackHandler` logic to capture back navigation at the root folder level inside vaults, cleanly deselecting.

## [1.5.7] - 2026-07-15

- **Native Vault Transfers:** File/folder import and export or move-out now use Arcile's own storage browsers end to end.
- **Arcile-Aligned OnlyFiles UI:** The vault library and mounted browser now use Arcile scaffolding, large/search/selection app bars, file layouts, spacing, shapes.
- **Useful Utilities Only:** Utilities now lists only implemented destinations—Trash, Cleaner, Activity Log, and OnlyFiles—with placeholder FTP, App Manager.
- **Custom Home Utilities:** Users can choose which implemented utilities appear on Home and reorder them, with ordered preference migration and stale-entry cleanup.
- **Deep Transfer Reliability:** Native export, merge, cancellation, and move-out handle deeply nested folders without recursive stack growth, clean private staging data on failure.
- **Large Vault Confidence:** Authenticated directory paging is now stress-verified with 10,000 siblings in one folder and 100,000 entries across a vault.

## [1.5.6] - 2026-07-14

- **Protected Vault Presentation:** OnlyFiles blocks capture by default, supports encrypted revision-keyed image and video thumbnails, clears decrypted previews on lock.
- **Complete Vault Administration:** Per-vault actions now cover password changes, strong-biometric enrollment or removal with password fallback, quick and full health checks.
- **Verified Plaintext Boundaries:** Guarded export and move-out stage complete document-tree results, verify file contents, publish conflict-safe replacements.
- **Compatibility and Disclosure:** Apps that cannot stream can receive a separately confirmed, space-checked private plaintext copy with UID binding and automatic cleanup; Settings.

## [1.5.5] - 2026-07-14

- **Daily Vault Browsing:** OnlyFiles now uses stable opaque item identities with paged list/grid browsing, sorting, current-folder or recursive search, range.
- **Vault Clipboard:** Memory-only copy and move supports same-vault and cross-vault destinations, cancellation, progress, and keep-both, replace, skip.
- **Global Video Player:** Browser, Recents, Trash, external opening, and OnlyFiles now share an immersive queue player with lifecycle-safe position retention, seeking, tracks.
- **Controlled Media Access:** Vault images use bounds-first sampled decoding, while single-consumer external grants provide seekable encrypted streams.

## [1.5.4] - 2026-07-14

- **Reliable Portable Vaults:** Registrations now follow stable storage-volume identities across remount paths, preserve missing removable vaults, and detect missing, damaged.
- **Complete Vault Transfers:** Same-vault and cross-vault copy and move operations are serialized safely, commit each selected item atomically, support replace, keep-both, skip.
- **Stronger Session Security:** Password strength confirmation, atomic password rotation, Android Keystore-backed biometric challenges, and independent transfer.
- **Private External Access:** Share and Open With grants use opaque expiring read-only streams directly from encrypted objects, survive an interactive lock, support revocation.

## [1.5.3] - 2026-07-14

- **Atomic Vault Changes:** OnlyFiles mutations and imports now commit through recoverable generation transactions.
- **Scalable Encrypted Browsing:** Vault folders now use stable opaque IDs and paged per-directory metadata instead of a global path index, with normalized case-insensitive names.
- **Independent Transfer Leases:** Started imports keep a bounded operation key lease while foreground vault access locks immediately and closes interactive readers.
- **Vault Health Checks:** Quick and full verification now detect damaged headers, manifests, references, file chunks, pending transactions, missing objects.

## [1.5.2] - 2026-07-14

- **Hardened Vault Format:** OnlyFiles now uses bounded Argon2id password protection, separated encryption domains, redundant authenticated headers.
- **Portable Vault Foundations:** Vault locations now use opaque volume-relative identities.
- **Unified Video Opening:** Browser, Recents, Trash, and external video intents now enter the same Arcile video-viewing flow.
- **Stable Recent Previews:** Cached Home carousel thumbnails remain visible when cards leave and re-enter the viewport.

## [1.5.1] - 2026-07-14

- **OnlyFiles Vaults:** Create and independently unlock multiple encrypted vaults in private app storage, with visible vault names.
- **Encrypted Browser:** Browse, create, rename, and delete folders and files entirely inside a vault, with authenticated encrypted names, hierarchy, metadata.
- **Private Imports:** Copy files or complete folders into a vault through a cancellable foreground import that removes incomplete data.
- **Secure Media:** View encrypted images and seek through large encrypted videos without decrypting whole files to disk or memory.

## [1.5.0] - 2026-07-13

- **Metadata Editing Reliability:** Image metadata editing is now offered only for supported, writable local images and safely remains unavailable for missing, read-only.
- **Operation Path Reliability:** Archive and Browser progress surfaces now display file names consistently for both Unix- and Windows-style storage paths.
- **Developer Guidance:** Development documentation now reflects the current modular architecture, feature ownership, release metadata, and focused verification workflow.
- **Plugin Distribution:** Removed the bundled Arcile GLB Viewer application and its install catalog entry while retaining generic plugin discovery, compatibility checks.
- **Project Documentation:** README, website, development guidance, and release notes now reflect Arcile 1.5.0, its current feature set, modular structure.
- **Navigation & Feedback Reliability:** Feedback stays on its originating screen, and the system back gesture exits normally from Home instead of being intercepted by the hidden Browser.
- **Home Thumbnail Quality:** Recent image cards now use Gallery-aligned content sources, rendered-size requests.
- **Gallery Viewer:** Opening an image now keeps that image anchored while neighboring images load, preserves Gallery selection through viewer actions and deletion.
- **File Actions & Sharing:** Trash images now open with neighboring items in Arcile's read-only viewer, Trash files expose only applicable actions.

## [1.4.9] - 2026-07-13

- **Preference Reliability:** Browser, Recents, Gallery, and save-destination settings now update through independent stores while retaining all existing saved values.
- **Search Reliability:** Browser, Recents, and Trash now prevent stale searches from replacing newer results and keep search failures separate from unrelated loading errors.
- **File Operation Reliability:** Concurrent Browser workflows keep independent progress and feedback, while cancelled storage work cleans up staged data before stopping.
- **Destination Reliability:** Save-to-Arcile and archive workflows now validate storage-owned paths throughout navigation, preserve access to readable folders.
- **Cache Control Reliability:** Settings staging and Storage Cleaner thumbnail caches now load and clear through independent owners, reject duplicate actions.

## [1.4.8] - 2026-07-12

- **Upgrade Reliability:** Shared thumbnail loading and feature test wiring now remain owned by their correct modules.

## [1.4.7] - 2026-07-12

- **File Interface Reliability:** Grid previews, range selection, Trash loading/empty states, and Activity Log labels now remain independently consistent.
- **Quick Access Organization:** Custom paths are normalized before saving and every shortcut appears in exactly one appropriate management section.

## [1.4.6] - 2026-07-12

- **Model Viewer Reliability:** GLB loading, cancellation, back navigation, viewer controls, and top/bottom overlays now retain consistent state through viewer changes.
- **Storage Cleaner Reliability:** Duplicate groups handle empty and directory entries safely, while risk summaries and displayed storage paths remain accurate.

## [1.4.5] - 2026-07-11

- **Startup Stability:** Restored the generated authorization bridge required by Browser so the app no longer crashes while composing its initial screen.
- **Shared Import Reliability:** Save-to-Arcile now validates incoming shares and destinations through isolated workflows, safely restores its selected folder.
- **Quick Access Reliability:** Folder selection and restricted Files shortcuts now use route-owned Android access with persisted permissions and normalized destinations.

## [1.4.4] - 2026-07-11

- **File Authorization Reliability:** System file confirmations now survive lifecycle changes, reject stale results, and recover cleanly when denied or unavailable.
- **Settings Reliability:** Backup pickers and external-cache cleanup now use route-owned workflows with duplicate-action protection and accurate remaining-cache status.

## [1.4.3] - 2026-07-11

- **File Workflow Reliability:** Isolated file-list rendering and focused archive, cleaner, storage-usage, activity-log, conflict.

## [1.4.2] - 2026-07-11

- **Navigation Reliability:** Unified file opening, sharing, plugin prompts, Browser entry, and viewer-return handling across app destinations.

## [1.4.1] - 2026-07-11

- **Startup Reliability:** Browser restoration now begins only after entering Browser, with dedicated loading and retry UI instead of restoring behind Home.

## [1.4.0] - 2026-07-10

- **Architecture Reliability:** Completed feature ownership for browsing, Home, galleries, storage tools, settings, recents, trash, onboarding, archives, and plugin viewers.
- **Storage Reliability:** Split storage browsing, mutations, media, volumes, and trash into focused repositories with clearer runtime boundaries.
- **Interface Consistency:** Consolidated shared workflow feedback, viewer navigation, theming, and reusable file-management presentation.

## [1.3.9] - 2026-07-05

- **File Workflow Reliability:** Consolidated creation, rename, deletion, properties, clipboard, and conflict dialogs into shared UI ownership.

## [1.3.8] - 2026-07-05

- **Search Filter Reliability:** Consolidated search filters, date-range selection, localized date formatting, presets, and expressive filter controls into shared UI ownership.

## [1.3.7] - 2026-07-05

- **File List Reliability:** Consolidated list, grid, volume, filter, row presentation, and accessibility behavior into bounded shared file-browsing components.

## [1.3.6] - 2026-07-05

- **Media Browsing Reliability:** Consolidated thumbnail sizing, loading and failure state, image metadata presentation, and fast-scroll behavior into shared UI components.

## [1.3.5] - 2026-07-05

- **Theme Reliability:** Consolidated theme state, colors, typography, shapes, spacing, motion, and category styling under the shared UI layer with direct preference coverage.

## [1.3.4] - 2026-07-05

- **Shared Workflow Reliability:** Consolidated selection properties, clipboard state, debounced search, file formatting, folder tabs.

## [1.3.3] - 2026-07-05

- **Storage Path Reliability:** Isolated filesystem listing, file transfer, secure deletion, synthetic-file creation, MediaStore queries, volume selection.

## [1.3.2] - 2026-07-05

- **Storage Reliability:** Added focused storage capabilities for browsing, file changes, archives, media, trash, volumes, preferences.

## [1.3.1] - 2026-07-02

- **Browser Navigation Reliability:** Browser now owns its navigation state, scroll restoration, file actions, and back handling while Home-to-Browser requests use explicit folder.
- **Onboarding and Browser Search Reliability:** Onboarding now owns permission and backup restoration state, while Browser search uses an independently owned, debounced state controller.

## [1.3.0] - 2026-07-02

- **Settings and Import Reliability:** Isolated settings, backup, activity history, plugins, external file access.

## [1.2.9] - 2026-07-01

- **Home and Storage Reliability:** Isolated Home, storage dashboard, and storage management state so loading, refreshes, file opening.

## [1.2.8] - 2026-07-01

- **Background Operation Reliability:** Isolated background file operations, shared-file imports, and interrupted-operation recovery with dedicated coverage.
- **File Opening Reliability:** Centralized plugin, archive, image, and external file resolution with direct coverage for viewer context and unsupported archives.

## [1.2.7] - 2026-06-29

- **Reliable App Relaunch:** Cold launcher starts now open Home instead of briefly restoring a previous utility screen.
- **Architecture Groundwork:** Renamed shared file-listing presentation contracts away from Browser-specific terms and added ratcheting size guardrails.

## [1.2.6] - 2026-06-28

- **Optional Plugin System:** Moved GLB rendering into an independently versioned Arcile GLB Viewer APK, added signed intent-based discovery, compatibility checks.

## [1.2.5] - 2026-06-28

- **Image Metadata and Viewer Controls:** Added editing and clearing for descriptive, camera, date, time, and GPS metadata; improved metadata refreshes, date/time dialogs, sheet transitions.
- **Date and Size Filtering:** Added date-range and file-size presets, custom date selection, MB-based minimum/maximum size inputs, and reliable application.
- **Recents and Fast Scrolling:** Improved calendar-day grouping and year-aware labels, corrected staggered-grid fast scrolling, refined scrollbar tooltips.
- **Home Performance:** Displayed recent files and storage volumes sooner, handled overlapping refreshes reliably, and reduced repeated storage animation work.
- **File Format Documentation:** Documented recognized formats, built-in capabilities, metadata-writing support, and features requiring Android codecs or external apps.

## [1.2.4] - 2026-06-23

- **Image Viewer Navigation and Selection Fixes:** Preserved Browser breadcrumbs, active selection, and far-scroll position when returning from Browser-opened images by routing viewer back gestures.
- **Browser Viewer Back Stack Hardening:** Prevented Browser page restores after returning from the image viewer from clearing active folder history.
- **Browser Folder Scroll Restoration:** Preserved Browser list and grid scroll positions by folder/category/archive location after opening images or other files.
- **Settings Toggle Shape Simplification:** Replaced the custom lobed Settings toggle thumb with a simple circular thumb while keeping the existing animated check and close indicators.

## [1.2.3] - 2026-06-23

- **Model Viewer Implementation:** Added GLB/model file recognition, in-app and standalone model viewer routing, SceneView/Filament rendering support, viewer metadata.

## [1.2.2] - 2026-06-22

- **Quick Access Default Fix:** Kept WhatsApp available in Manage Quick Access while no longer pinning it to Home by default for fresh preference state.
- **Thumbnail Cache Cleaner Accuracy:** Measured thumbnail disk usage from Coil's active disk cache directory.
- **Gallery Viewer Return Position:** Returned Gallery to the last image viewed in the image viewer, including grouped gallery layouts with section headers.
- **Browser and Gallery Scrollbar Controls:** Added separate Settings toggles to disable Browser and Gallery scrollbar overlays entirely.
- **Fast Scrollbar Expressive Polish:** Shared the scrollbar overlay between Browser and Gallery, showing a normal rounded thumb during regular scrolling.
- **View Switcher Expressive Design:** Updated the view switchers in bottom sheets.
- **Settings Expressive Toggles:** Replaced all standard preference switches in the Settings screen with a custom spring-animated `ExpressiveSwitch` containing a morphing 7-sided.
- **Settings Elastic Inputs:** Refreshed Custom Theme hex input fields in the Settings screen to utilize focus-driven elastic spring scaling.
- **Release String Gate Cleanup:** Localized search filter date labels and clear actions for production string validation.

## [1.2.1] - 2026-06-21

- **Material 3 Expressive UI Pass:** Updated dialogs, sheets, toolbar actions, chips, list rows, and cleaner/gallery controls with rounded expressive styling, tactile feedback.
- **Search and File Dialog Polish:** Improved search filter controls and refreshed file/archive creation dialogs with clearer expressive inputs and actions.
- **Gallery and Viewer Polish:** Converted gallery Search, Sort, and More into split actions, refined image viewer bottom chrome, and improved thumbnail strip behavior.
- **Gallery Tab Touch Polish:** Clipped Photos and Albums bottom tab hover states to their pill shapes.
- **Browser Top Bar Touch Polish:** Clipped Browser top-bar hover and touch states to their circular and split-button shapes.
- **Dynamic Toast Polish:** Made in-app feedback toasts/snackbars content-sized so short messages no longer stretch across the screen.
- **Storage Touch Polish:** Replaced storage breadcrumbs and classification chips with expressive rounded surfaces and shaped storage usage actions.
- **Archive Viewer Touch Polish:** Clipped archive viewer toolbar and list-row touch states to their circular and rounded row shapes.
- **Trash Touch Polish:** Shaped Trash top-bar actions, empty-trash controls, restore destination rows, and dialog actions to match their rounded UI surfaces.
- **Home and App Toolbar Touch Polish:** Rounded Home header actions and clipped About, Licenses, and Activity toolbar buttons to circular touch states.
- **Shared Dialog Touch Polish:** Shaped shared sort, search filter, and clipboard dialog actions with expressive sheets, rounded buttons, and clipped icon targets.
- **Gallery Surface Shape Polish:** Aligned gallery photo and album touch surfaces with shared expressive shape tokens.
- **Archive and Dialog Touch Polish:** Replaced archive chips and shared create/rename dismiss actions with expressive rounded touch surfaces.
- **Onboarding Chip Polish:** Replaced remaining standard onboarding chips with expressive rounded chip surfaces.
- **Quick Access and Cleaner Polish:** Refined Quick Access management controls and Storage Cleaner duplicate/detail interactions with consistent expressive surfaces.
- **Top Bar and Import Touch Polish:** Clipped Quick Access, Recent Files, Storage Cleaner, gallery, standalone image viewer.
- **Remaining Component Shape Polish:** Removed stale standard chip references and rounded additional archive, browser transfer, gallery options, storage dashboard.
- **Dropdown Menu Touch Polish:** Routed app overflow and picker menu rows through a rounded dropdown item wrapper so hover and ripple states match expressive menu shapes.
- **List and Breadcrumb Touch Polish:** Clipped volume root rows, archive encoding choices, Tools shortcuts, Settings navigation, and breadcrumb segments to rounded touch-state bounds.

## [1.2.0] - 2026-06-21

- **Image Viewer Thumbnail Performance:** Made the bottom thumbnail strip jump directly to far-opened gallery items, animate only nearby page changes, use stable path keys.
- **Navigation Back Stack Fixes:** Preserved route origins for Browser handoffs from dashboards, recent files, cleaner, quick access.
- **Storage Cleaner Back Handling:** Added local back priority so Cleaner dismisses ignored-items, delete confirmation, and details UI before leaving the Cleaner route.

## [1.1.9] - 2026-06-21

- **Segmented Quick Access Groups & Toggle Symbols:** Reorganized the Manage Quick Access screen to display folders in sequentially ordered, visually segmented stacked cards.
- **Image Viewer Selected Thumbnail Polish:** Enhanced the bottom thumbnail strip in the image viewer to animate the selected thumbnail's width (28dp to 36dp), height (42dp to 54dp).
- **Home "Arcile" Shortcut:** Updated the Home screen Quick Access "All" shortcut's label text from "All" to "Arcile", and made it load.
- **Quick Access Reordering & Switch Haptics:** Added a fullscreen drag-to-reorder dialog for enabled quick access shortcuts (including the "Arcile" shortcut) with viewport auto-scrolling.
- **Storage Cleaner Layout Polish:** Fixed a UI issue where the "Ignore" text button next to risk reasons in the storage cleaner candidate rows.

## [1.1.8] - 2026-06-21

- **Standalone Image Viewer & Metadata Reuse:** Added an exported image viewer activity running in a separate process for external `image/*` opens.
- **Image Viewer Layout Polish:** Expanded the viewer top metadata pill to use the space beside Back.
- **Gallery Viewer Selection Fix:** Scoped restored viewer position to the active opened image so tapping a gallery item no longer reuses a stale/random previous page.
- **Image Viewer Space & Strip Polish:** Removed the top viewer back arrow to give metadata the full overlay width.

## [1.1.7] - 2026-06-20

- **System Predictive Back & Expressive Motion:** Enabled Android 14+ system predictive back gestures, custom spring horizontal page scroll animations.
- **Refresh Coverage Fixes:** Extended pull-to-refresh across Browser search/loading/empty states, Gallery albums, and Trash lists and empty states.

## [1.1.6] - 2026-06-19

- **Image Viewer Info Details:** Added dynamic viewer position, filename, date/time, and resolution details to the image viewer overlay, and expanded the metadata sheet with title.
- **Image Viewer Metadata Freshness:** Kept viewer metadata fresh after metadata erase/refresh events and restored EXIF date-taken display in the details sheet.
- **Browser Folder Listing Freshness:** Made Browser directory listings read live filesystem children instead of trusting partial media cache rows.
- **Browser Folder Stats Cache Display:** Kept cached folder stats visible while a background refresh is queued so folder rows can show the previous size/count immediately.
- **Browser Video Thumbnails:** Restored video thumbnails for raw browser file paths by falling back to `MediaMetadataRetriever` when a MediaStore thumbnail URI is unavailable.
- **Image Viewer State Restoration:** Made the image viewer collect gallery state with lifecycle awareness and persist viewer chrome, metadata sheet, current image, rotation.
- **Gallery Clipboard UX:** Added Browser-aligned clipboard management in Gallery with a persistent clipboard/progress pill, queued item inspection and removal.
- **Gallery Cache Completeness:** Prevented Browser directory-listing cache rows from seeding partial Gallery image snapshots.
- **Release Regression Gates:** Added focused coverage for viewer saved-state restoration, Save-to-Arcile import checkpoints, and repeated archive extraction state isolation.
- **Storage Documentation Sync:** Reconciled release docs with Room cache schema version 2, cache-backed external handoff providers, and v1.1.6 release metadata.

## [1.1.5] - 2026-06-18

- **Live Media Refresh:** Wired MediaStore multi-URI change callbacks into shared storage invalidation, refresh storage caches on app startup, and made Recent Files.
- **Recent Files Pull Refresh:** Moved pull-to-refresh to cover the whole Recent Files screen body and positioned it below the top app bar so the indicator.
- **Refresh Consistency:** Extended startup and external MediaStore invalidation to storage-usage, cleaner, and folder stats caches.
- **Gallery Album Paste:** Added direct copy/cut paste support for real gallery albums, including paste actions inside an open album and on album cards.
- **Gallery Refresh Reliability:** Refreshed gallery snapshots after copy and move operations using both source.
- **Gallery Performance:** Moved image snapshot shaping off the main thread and precomputed album cover lookups to reduce gallery loading and album-grid recomposition churn.

## [1.1.4] - 2026-06-17

- **Save to Arcile Defaults and Recents Refresh:** Added a persistent default destination for Save to Arcile imports, opened valid defaults first while keeping folder navigation available.
- **Durable Save-to-Arcile Imports:** Routed shared-file imports through the foreground operation pipeline with staged temp files, progress notifications, cancellation cleanup.
- **Operation Recovery Checkpoints:** Persisted operation checkpoints for staged files, finalized outputs, rollback hints, and Trash IDs so interrupted foreground work can recover.
- **Archive Extraction Reliability:** Reset archive extraction conflict state for every extraction so skip and keep-both directory decisions cannot leak into later archive operations.
- **Archive Browser Restoration:** Restored saved archive path and entry-prefix browsing state after process recreation without persisting archive passwords.
- **Storage Dashboard Back Navigation:** Fixed dashboard and usage-map Browser handoffs so Back returns to Home instead of landing on an unintended Browser route.
- **Storage Identity and Handoffs:** Routed open/share and media thumbnail fallbacks through carried content URI identity where available.
- **Shared Filename Preservation:** Staged shared files with collision-safe original display names.
- **Room Cache Schema Discipline:** Enabled Room schema export, committed the version 2 schema, limited destructive cache reset to the explicit version 1 upgrade path.

## [1.1.3] - 2026-06-16

- **Onboarding UI/UX Polish:** Refined all onboarding chips and status elements to be pill-shaped (`CircleShape`).
- **Onboarding Accessibility Polish:** Made the expressive onboarding icon respect reduced-motion settings, moved new onboarding labels into string resources.
- **Onboarding M3 Expressive Overhaul:** Overhauled onboarding pages with a custom counter-rotating morphing shape background behind the app icon.
- **Keyboard Input Reliability:** Made search fields, dialog name inputs, filter fields, and other typed text inputs explicitly open the keyboard on focus/tap.
- **Home Recent Thumbnail Stability:** Unified Home recent-file carousel preload and display thumbnail cache keys on a shared bucketed variant so carousel thumbnails stay cached when.
- **Fresh Recent Files and Gallery:** Stopped serving stale persisted recent-file snapshots to UI refreshes, tightened gallery cached-image validation.

## [1.1.2] - 2026-06-15

- **Security and Privacy Hardening:** Tightened external file handoffs, excluded local cache metadata from backup and transfer.
- **Manual Settings Backup and Restore:** Added file-based export and restore for Arcile preferences from Settings and onboarding, with clear previews, restore results, theme support.

## [1.1.1] - 2026-06-15

- **Activity Log:** Added a local Activity tool for opened folders and foreground file-operation history, including clear controls, backup exclusions.
- **Activity Log Reliability:** Serialized foreground operation activity writes so completed, failed, or cancelled operations cannot be overwritten by a delayed running-status entry.

## [1.1.0] - 2026-06-14

- **Storage Bar Progress:** Replaced the default loading shimmer with an indeterminate linear moving progress bar showing all category colors.
- **Release Documentation and Verification:** Updated release-facing README, website, release notes, and development docs for v1.1.0.
- **Cache and Thumbnail Stability:** Stabilized Browser list/grid thumbnail requests with remembered no-crossfade Coil requests, increased thumbnail prefetch buffering.
- **Storage Cache Freshness:** Cleared persisted Recent Files snapshots when MediaStore reports file changes.
- **Gallery Timeline Dates:** Formatted dates as wrapped, content-fitting chips in daily/weekly/monthly grouping views, styled like the recents screen.
- **Gallery Overflow Menu:** Removed the redundant "Show file details" toggle from the 3-dot overflow menu (already accessible via the view options sheet).
- **Image Viewer EXIF Sheet:** Refactored the metadata sheet to be a full-screen layout and wrapped each pager item in a VerticalPager for smooth scroll-snapping.
- **Image Viewer Bottom Actions:** Realigned bottom actions to place the action buttons (Favorite, Info, Rotate) on the left in a SplitButtonGroup.
- **Bouncy Card Press Actions:** Converted all cleaner category cards to use `bounceClickable` for bouncy, tactile Spring interactions and haptic feedback.
- **Animated Manual Refresh:** Added infinite, fluid rotation to the manual refresh icon during active storage scans.
- **Persistent Page Memory:** Preserved back stack structures when opening containing folders or archives from sub-pages (Recent Files, Quick Access, Archive Viewer).
- **Cleaner UI/UX and Alignment:** Aligned the checkbox, thumbnail, and path/metadata.
- **Vertical Duplicate Comparison:** Redesigned the "Compare duplicates" sheet to stack files vertically inside a scrollable column with the "Delete selected" action button sticky at.
- **Smart App Badge Layout:** Offset application badges slightly outwards on file previews.
- **Cleaner Header Alignment:** Simplified the `DuplicateGroupCard` header to place the name and a single subtitle containing the duplicate count and size.
- **Cleaner Exact File Opening:** Kept Storage Cleaner file preview taps routed through the exact platform file opener, while folder candidates open their containing Browser location.
- **Cleaner Scroll Performance:** Moved thumbnail cache size reads and app icon/label resolution off the UI thread to reduce duplicate-file list scroll jank.
- **Cleaner Exact Duplicate Detection:** Replaced duplicate-name matching with size narrowing, sampled-byte checks.
- **Cleaner Custom Rules and Ignores:** Added persisted Storage Cleaner rules with per-section enablement, name/path ignore patterns, large-file and old-download thresholds.
- **Cleaner Metadata Precision:** Updated duplicate timestamp metadata to include seconds and removed redundant app-name risk wording when the app icon already provides context.

## [1.0.9] - 2026-06-14

- **Home Storage Loading Reliability:** Cancelled overdue storage, category, volume, and Trash analytics work when the Home refresh timeout is reached so skeleton loaders can clear instead.
- **Storage Dashboard Cache Reuse:** Seeded the dashboard's single-volume category breakdown from the already-loaded Home storage categories.
- **Storage Dashboard First Paint:** Reused Home's single-volume category and Trash totals.
- **Home Force Refresh:** Made Home pull-to-refresh clear cached category analytics before reloading so manual refresh performs a true fresh storage calculation.
- **Storage Cleaner Hub:** Moved thumbnail cache controls from Settings into Storage Cleaner, added shared file previews.
- **Cleaner Cleanup Categories:** Added dedicated Marker files and Empty folders sections while keeping duplicate detection available for all duplicate files.
- **Cleaner Duplicate Files:** Renamed the duplicate cleanup section to "Duplicate files" and added a scrollable vertical compare sheet with per-file.
- **Cleaner File Actions:** Added exact Open file handling plus Open folder navigation that focuses the containing Browser location.

## [1.0.8] - 2026-06-13

- **Image Viewer Revamp:** Adapted a clean media viewer layout to Arcile's design system, adding a circular Back button in the top-left.
- **Quick Access App Icons:** Declared WhatsApp package visibility and resolved the Android Files/DocumentsUI package so Home.
- **Whole-Device Storage Usage:** Used Android platform storage statistics for primary internal storage and surfaced the non-browsable remainder as system.
- **Decimal Storage Units:** Switched displayed file sizes to decimal KB/MB/GB/TB units so storage totals align with Android Settings and device capacity labels.
- **Gallery Album Defaults:** Removed the album aspect-ratio layout option, kept album covers square, and added a Photos/Albums default opening preference for the gallery.
- **Architecture Budget Tuning:** Raised the large-file and ViewModel line-count guardrails to 1000 lines to reduce churn from overly tight test limits.
- **Storage Dashboard Reuse:** Reused already-loaded home storage breakdowns when opening the dashboard summary.
- **Gallery Swipe Navigation:** Added horizontal swiping between Photos and Albums while preserving the existing floating tab controls.
- **Gallery Scroll Polish:** Preserved gallery scroll position when returning from the image viewer, moved the fast scrollbar to the far-right edge.
- **Gallery Chrome Behavior:** Hid and revealed the bottom Photos/Albums navigation with scroll, matching the existing floating control behavior.
- **Gallery Delete Freshness:** Removed deleted images from gallery state, favorites, albums, and viewer paging immediately while the backing catalog refreshes.
- **Transparent Image Rendering:** Stopped drawing the generic image icon behind successfully loaded transparent images and switched thumbnails to a neutral background.
- **Viewer Context Awareness:** Preserved gallery ordering in the image viewer and opened the pager on the selected image instead of rebuilding the list around page zero.
- **Cross-Screen Image Swiping:** Passed surrounding image context from Home recents, Recent Files, Browser folders, Browser categories.
- **Browser Viewer Return:** Returned users to the active Browser folder after swiping through folder images in the viewer instead of landing back on Home.
- **Browser Scroll Preservation:** Kept the Browser folder scroll position intact when returning from the image viewer instead of jumping back to the top of the folder.

## [1.0.7] - 2026-06-12

- **Material 3 Expressive Animations:** Customized standard spring animations to use under-damped bouncy physics and integrated volumetric slide-and-scale navigation transitions.
- **Progressive Predictive Back Navigation:** Integrated progressive predictive back gestures across core screens (Browser, Gallery, Recents, Trash, Archive Viewer), dynamically scaling.
- **Bouncy Spring Padding Safeguards:** Coerced animated horizontal and vertical item list paddings to be non-negative.
- **Home Storage Bar Shimmering Loading:** Implemented a dynamic shimmering used-space loading bar that populates automatically on app start, morphs smoothly when storage details resolve.
- **Gallery Module Refactor:** Split the oversized gallery screen and image viewer implementations into focused chrome, options, content, album, timeline, scrolling, zoom.
- **Architecture Boundary Cleanup:** Refactored the remaining temporary large-file whitelist entries across archive viewer, storage usage map.
- **Gallery Boundary Coverage:** Added the image gallery feature module to production class imports for architecture boundary checks.

## [1.0.6] - 2026-06-11

- **Pinch-to-Resize Columns:** Implemented real-time interactive multi-touch pinch-to-resize column scaling directly on Photos and Albums grids.
- **Custom Album Covers:** Added the ability to designate any image inside an album as its customized cover thumbnail.
- **Favorites virtual album:** Added a Favorite (heart) action in the image viewer to bookmark media.
- **Gallery state reliability:** Prevented restored or direct-entry image viewers from closing before images finish loading, clamped the viewer pager after Favorites/delete changes.
- **Gallery startup and navigation smoothness:** Applied saved gallery preferences before the initial image load and removed heavyweight full-grid content animations between Photos, Albums.
- **Viewer external handoff reliability:** Preserved MediaStore `content://` grants for gallery Open With and Share actions instead of incorrectly treating them as filesystem paths.
- **On-demand EXIF details:** Kept EXIF metadata loading scoped to the image viewer details sheet so gallery browsing stays lightweight.
- **Chronological folder sorting:** Added full chronological sorting options (Newest/Oldest) to the Albums grid using updated folder modification dates.

## [1.0.5] - 2026-06-11

- **Interactive Gesture-Driven Image Viewer:** Consolidated competing touch listeners in the image viewer to support zoom-responsive panning with boundary resistance, double-tap zoom targeting.
- **Privacy EXIF Metadata Scrubbing:** Added a detailed EXIF bottom sheet utilizing `ExifInterface` to display rich camera parameters, paths, sizes, dates, and locations.
- **Photos Timeline Dynamic Grouping:** Supported user-selectable chronological photos grouping (None, Day, Week, Month) on the Photos screen.
- **Multi-Select Drag Selection:** Integrated a long-press-and-drag range selection helper in standard/staggered grids and list layouts.
- **Customizable Album Settings:** Implemented a dynamic grid column sizing slider, customizable sorting criteria (Name, Size), and aspect ratio previews on the Albums tab.

## [1.0.4] - 2026-06-10

- **Native Expressive Image Viewer:** Introduced an immersive full-screen photo viewer with pinch-to-zoom spring mechanics, elastic vertical swipe-to-dismiss gestures.
- **Material 3 Expressive Gallery Revamp:** Redesigned the gallery screen layout to prioritize modern, high-end design aesthetics and immersive scrolling behavior.
- **Scroll-Linked Floating Controls:** Hidden and revealed the floating gallery action controls on vertical scroll gestures, freeing up screen real estate for photos.
- **Compact Floating Pill Bars:** Split the full-width top bar into individual circular back buttons and compact right-aligned action pills.
- **Floating Dynamic Bottom Navigation:** Added a centered floating glassmorphic bottom navigation bar displaying "Photos" and "Albums" tabs with smooth active tab transitions.
- **Albums List View:** Introduced a structured albums folder grid featuring cover photo previews and metadata indicators.
- **Vertical 3D Flip Selection Bar:** Integrated a spring-animated vertical 3D card flip transition (X-axis rotation) on selection mode change that expands dynamically to fill.
- **Split Selection Toolbar:** Aligned the selection actions toolbar with the browser, utilizing a `SplitButtonGroup` for primary actions (Copy, Cut, Delete, Edit).
- **Draggable Fast Scrollbar:** Added a custom draggable fast scrollbar that coordinates with staggered grids, uniform grids, and lists.
- **Fluid Album Transitions:** Integrated horizontal spring slide/fade animations for folder opening and closing.
- **Detached Action Button Group:** Removed the full-width background container overlay behind selection action buttons, allowing the split buttons.
- **Collapsible Bottom Navigation Labels:** Refined bottom navigation items to collapse labels for inactive tabs while expanding the active tab.
- **Predictive Back Gesture Animations:** Replaced old back handling with `PredictiveBackHandler` to support progressive gesture progress tracking, scaling/sliding gallery sub-pages.

## [1.0.3] - 2026-06-09

- **Room Cache Foundation:** Added the first shared Room-backed cache database for storage metadata, with tables for folder stats, storage nodes.
- **Persistent Folder Stats:** Reworked folder statistics caching to persist through Room while preserving the existing low-priority queueing, cancellation, invalidation.
- **Image Catalog Backend:** Introduced a storage-domain image catalog repository.
- **Gallery Metadata Pipeline:** Extended MediaStore image queries to capture width and height.
- **Persistent Thumbnail State:** Added Room-backed thumbnail identity and variant tracking so loaded variants and failed identities survive app restarts.
- **Thumbnail Invalidation:** Connected file mutations, Settings cache clearing, browser thumbnails.
- **Cached Directory Listings:** Persisted filesystem directory rows in Room so repeat folder opens can render from the storage node cache.
- **Cached Category Summaries:** Moved category size summaries from JSON sidecar files into Room and kept the existing scoped TTL refresh behavior.
- **External Storage Invalidation:** Added a MediaStore observer that clears cached listing/category metadata and notifies storage screens after external file changes.
- **Gallery Cache Hits:** Stopped cached gallery snapshots from immediately force-refreshing, switched gallery tiles to cell-sized cached image requests.
- **Storage Usage Cache:** Added scanner-level storage usage caching with mutation invalidation so reopening the dashboard can reuse the previous scan instead of walking.
- **Storage Bar Stability:** Prevented home and dashboard storage bars from drawing a temporary single-color used segment before real category data is available.
- **Backend Wiring:** Added Hilt providers and Room dependencies so the new cache layer can be reused by category, listing, gallery, and thumbnail refresh work.

## [1.0.2] - 2026-06-09

- **Mini Gallery Transition:** Converted the Images category into a fully immersive Mini Gallery featuring edge-to-edge full-screen scrolling, borderless grid rendering.
- **Staggered Aspect Ratio Layout:** Added a staggered grid view option (Microsoft Photos style) that preserves natural image aspect ratios.
- **Chronological Grouping:** Introduced timeline grouping options to section images by time ("Today", "This Week", "This Month", and "Older").
- **Unified Selection & Clipboard:** Integrated the gallery with global clipboard state and browser-aligned selection actions.
- **Material 3 Expressive Polish:** Added spring-scale animations on selection, custom floating pill bars.

## [1.0.1] - 2026-06-08

### Images Mini-App & Thumbnails
- **Independent Images Category:** Detached the Images category from the browser category screen into a dedicated MediaStore-backed Images mini-app.
- **Thumbnail Reliability:** Added shared thumbnail load-state tracking with identity and size-bucket keys, density-aware request sizing.
- **Album Scroll Isolation:** Prevented album/filter tabs in the Images screen from sharing the same scroll offset when switching between them.
- **Cache Controls:** Added Settings controls to inspect and clear thumbnail cache state.

---

## [1.0.0] - 2026-06-07

Arcile v1.0.0 is the first stable release and the first release outside the beta channel.

### Stable Release Hardening
- **Stable Release Promotion:** Updated app metadata to `versionName` `1.0.0` and `versionCode` `100`, synchronized release-facing docs.
- **Release Build Readiness:** Verified the stable build path with project checks, release lint, and release APK assembly.
- **Android Platform Alignment:** Targeted the current Android SDK used by the project while keeping the app scoped around Android 11+ storage behavior.
- **Production String Guardrails:** Expanded production string checks through build logic so release builds avoid raw internal/debug text on user-facing surfaces.

### Storage, Volumes & Home Dashboard
- **Instant USB/SD Detection:** Improved volume observation so inserted and ejected storage volumes update the Home screen immediately instead of waiting for a delayed refresh.
- **Storage Volume Callback Support:** Added platform storage-volume callbacks on Android versions that support them, while keeping media mount/eject broadcasts as a compatibility path.
- **Indexed vs Temporary Storage Rules:** Kept SD cards as indexed permanent storage and USB/OTG drives as temporary read/write storage excluded from global category indexing and Trash.
- **Category-Only Segmented Bars:** Preserved segmented storage colors for indexed category breakdowns while keeping USB/OTG capacity bars plain.
- **Home Recent Carousel Preference:** Connected the Settings carousel limit to the Home screen, made `0` hide the carousel, restored it when the value is raised again.
- **Home Tools & Quick Access Polish:** Kept Home utilities focused on implemented tools, improved quick-access behavior, and tightened dashboard refresh behavior around mounted volumes.

### Archive Workflows & Safety
- **Browser-Native Archive Views:** ZIP and 7z archives open as read-only virtual folders with navigation, search, sorting, selection, extraction.
- **Expanded Archive Formats:** Added TAR-family and single-stream compression handling for supported create/list/extract flows, alongside ZIP and 7z support.
- **Safer Extraction:** Hardened extraction with destination presets, password retries, conflict handling, bounds checks, rollback for failed replacements.
- **Archive Thumbnail Safety:** Bounded archive-entry thumbnail decoding with byte caps, bounds-first image checks, pixel limits, sampled decoding.
- **Sequential Archive Operations:** Processed multi-extraction and multi-archive work in controlled queues, with deferred deletion only after successful completion.

### Share, Open & Privacy
- **Save to Arcile Imports:** Added Android share-sheet support for saving incoming single or multiple files into a selected Arcile destination.
- **Hostile Share Preflight:** Validated incoming `content://` and app-owned `file://` sources, enforced item and byte limits, normalized filenames, checked destination space.
- **Direct Content Grants:** Opened user files through direct `content://` read grants where possible instead of staging large documents unnecessarily.
- **Sandboxed Sharing:** Restricted FileProvider exposure to controlled staging areas and maintained the app's no-internet, local-first privacy posture.

### Operations, Recovery & File Safety
- **Foreground Operation Pipeline:** Copy, move, trash, delete, archive, and extract operations run through foreground operation plumbing with progress, cancellation.
- **Interrupted Operation Recovery:** Preserved interrupted queued, running, and cancelling work as recovery records with retry, cleanup, and dismiss paths.
- **Partial Delete Reporting:** Permanent delete and shred batches now track succeeded, skipped, failed, and cleanup-required paths.
- **Transactional Mutation Safety:** Strengthened copy/move, archive replacement, extraction, and Trash flows with validation, staging, rollback, and cleanup behavior.
- **Smart Paste Conflicts:** Added user-facing conflict handling for replace, merge, keep both, and skip, with safer folder merging and clean numbered renames.

### UI, Theming & User Experience
- **Material 3 Expressive System:** Stabilized dynamic themes, custom palettes, Tokyo Night and Dracula presets, expressive shapes, tactile press feedback.
- **Recent Files Experience:** Added expressive carousel previews, chronological Recent Files lists, date grouping, audio/video/PDF/APK thumbnail improvements.
- **Storage Cleaner:** Added scan flows for large files, old downloads, obsolete APKs, videos, duplicate candidates, and conservative junk/cache groups.
- **Storage Usage Map:** Added a bounded radial usage map with path navigation, selected item details, and direct browser navigation.
- **Accessibility & Haptics:** Improved TalkBack semantics, selection announcements, content descriptions, touch targets, haptic feedback, reduced-motion handling.
- **Loading & Layout Polish:** Added shimmer states, smoother category/grid movement, stable directory paging, better snackbar placement.

### Architecture, Performance & Testing
- **Multi-Module Gradle Architecture:** Split the app into core runtime, UI, storage, operation, navigation, testing, and feature modules with stricter Hilt composition and boundary checks.
- **Storage API Hardening:** Introduced stronger domain contracts for volumes, storage scopes, file models, listing pages, mutation notifications.
- **Paged & Stable Listings:** Sorted full directory listings before paging and moved reusable row models out of hot Compose paths to reduce recomposition and scrolling work.
- **Bounded Scanner Work:** Capped folder stats, storage usage, archive prescans, thumbnails, and cleanup scans to avoid runaway traversal on very large trees.
- **Regression Coverage:** Expanded tests across browser navigation, archives, share imports, storage classification, recent files, carousel limits, operation recovery.

---

## Beta Recap: v0.1.0 - v0.9.9

The beta channel turned Arcile from a basic local file browser into a full Android storage manager.

### v0.1.x - Prototype Foundation
- Introduced file browsing, breadcrumbs, multi-select, create/delete basics, Home dashboard, category shortcuts, recent files, settings.
- Added early safety and polish including delete confirmation, FileProvider open support, rename UI, path traversal checks, theme persistence.

### v0.2.x - Material 3 & Core Operations
- Moved the app toward Material 3 Expressive with updated list items, animated UI components, bottom navigation, category storage visualization.
- Added core copy/cut/paste/share flows, Trash routing, Recent Files "See All", MediaStore-backed search/categories, file thumbnails.

### v0.3.x - Trash, Search & Multi-Volume Direction
- Expanded Trash behavior, scoped search filters, storage dashboard entry points, smart selection, persistent sorting, process-death state handling.
- Began serious external storage work with reactive volume discovery, storage classification policies, SD/USB handling.

### v0.4.x - SD/USB Beta, Polish & Architecture Cleanup
- Delivered the first major external storage beta: SD cards as permanent storage, USB/OTG as temporary storage, per-volume Trash.
- Added dynamic color generation, smoother grids and empty states, improved dashboard caching, type-safe routes, localization groundwork.

### v0.5.x - Security, API Floor & Maintainability
- Raised the platform baseline to Android 11, hardened FileProvider and open/share paths, blocked sensitive metadata exposure.
- Added typed errors, broader string extraction, shared UI spacing, pull-to-refresh infrastructure, folder properties, cached folder stats.

### v0.6.x - Properties, Quick Access, Archives & Onboarding
- Added richer file/folder properties, Quick Access improvements, operation feedback, premium progress UI, randomized fake-file tooling.
- Continued reliability work across trash, bulk operations, media previews, swipe navigation, folder tabs, and test stability.

### v0.7.x - Public Beta Polish, Storage Tools & Safety
- Added onboarding, ZIP/7z archive tools, richer Recent Files, radial storage usage, Storage Cleaner, safer destructive dialogs, foreground operations.
- Improved thumbnails, localization, storage utilities, search filters, back navigation, list performance.

### v0.8.x - Modularization & Production Hardening
- Rebuilt major architecture into feature and core modules, added stronger boundaries, shared UI modules, focused test fixtures, paged listings.
- Continued storage, browser, cleaner, archive, Trash, and UI hardening that formed the base of the stable v1.0.0 release.

### v0.9.x - Unreleased Stable Candidate Work
- Added browser-native archive browsing, selected archive extraction, expanded archive formats, sequential archive workflows, operation recovery.
- Completed major release hardening for imports, archive thumbnails, stale navigation cancellation, partial destructive operation reporting.

Detailed beta changelog history is archived.
