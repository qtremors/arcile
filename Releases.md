# Arcile - Releases

> **Project:** Arcile
> **Version:** 0.5.0
> **Last Updated:** 2026-03-23

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v0.5.0 Beta](#v050-beta) | 2026-03-23 | Security, Architecture & Target API Bump |
| [v0.4.5 Beta](#v045-beta) | 2026-03-19 | Stability, Performance & Visual Polish |
| [v0.4.0 Beta](#v040-beta) | 2026-03-16 | SD Card & OTG Support |
| [v0.3.0 Beta](#v030-beta) | 2026-03-11 | Trash Bin & Copy/Paste |
| [v0.2.0 Beta](#v020-beta) | 2026-03-06 | Material 3 Redesign |

---

# v0.5.0 Beta

**Release Date:** March 23, 2026  
**Previous release:** v0.4.5 Beta

Welcome to **Arcile v0.5.0 Beta**! This release heavily focuses on security enhancements, code quality, and structural robustness. I've completely overhauled the encryption layer for the Trash Bin, locked down external URI exposures, and officially raised the minimum API level to Android 11 to ensure a modern, stable scoped storage architecture.

## 🛡️ Security & Privacy
- **Trash Vault Encryption:** Replaced the plain-text `.arcile/.metadata` JSON storage with a secure AES256-GCM encryption layer. Keys are dynamically derived via hardware `ANDROID_ID`, ensuring that trash metadata stays perfectly hidden from other apps but fully survives app updates and reinstalls.
- **FileProvider Sandbox:** Closed a structural vulnerability by removing the root `external_root` path from `file_provider_paths.xml`. The `FileProvider` now correctly restricts external URIs strictly to standard public media folders (Downloads, Documents, Pictures, etc.).
- **Sensitive File Protection:** Explicitly blocked the ability to open or share internal `.arcile` metadata and application cache files via `MainActivity` and `ShareHelper`, preventing unintended system exposure.
- **Build Hardening & Credentials:** `build.gradle.kts` now securely loads signing properties, falling back to a clean debug configuration rather than failing when keys are missing. Added specific R8/ProGuard keep rules to ensure release build stability.

## 🚀 What's New & Changed
- **Target Platform:** Completely dropped support for Android 10 (API 29). `minSdk` officially raised to 30 (Android 11) to eliminate all scoped storage crash loops and align natively with `MANAGE_EXTERNAL_STORAGE` architecture.
- **Search Robustness:** Removed the arbitrary `.maxDepth(10)` limit on path-scoped searching. Deeply nested repository or archive folder structures can now be fully traversed, constrained solely by the 1000-item memory limit.
- **Accessibility & Localization:** Extracted massive swaths of hardcoded English texts, labels, and iconography `contentDescription` properties into `strings.xml`, instantly granting full TalkBack accessibility scaling for vision-impaired and non-English users.
- **UI/UX Polish:** Replaced hardcoded padding and margin components with strictly bounded `MaterialTheme.spacing` tokens to guarantee Material Design 3 scale compliance. Extracted shared Pull-to-Refresh logic into `ArcilePullRefreshIndicator.kt`.

## 🛠️ Fixes & Polish
- **Recent Files Affordance:** Added a fully wired `PullToRefreshBox` wrap to `RecentFilesScreen` to finally expose the previously inaccessible `onRefresh` manual action to users.
- **UI Performance & Formatting:** Hoisted `Calendar` and `SimpleDateFormat` logic out of recomposition scopes. Introduced reliable, locale-aware date string updates across the entire application interface.
- **Error Handling:** `TrashScreen` now properly surfaces persistent operational failures and errors via unified non-blocking `Snackbar` overlays. Introduced typed `FileOperationException` for granular error recovery.
- **Concurrency Safety:** Fixed unstructured concurrency leaks by properly re-throwing `CancellationException` inside Coroutines/Flows globally.
- **Testing:** Bootstrapped the `androidTest` layer with Robolectric Compose implementations and expanded the JVM test suite to handle high-risk branches, verifying edge cases including rename collisions and missing volume destinations.

---

# v0.4.5 Beta

**Release Date:** March 19, 2026  
**Previous release:** v0.4.0 Beta

Welcome to **Arcile v0.4.5 Beta**! This release focuses heavily on smoothing out the rough edges from the massive 0.4.0 update. I've eliminated UI flickers, drastically improved loading speeds for massive media folders, and redesigned the grid view to feel much more premium and uniform.

## 🚀 What's New & Improved

### ⚡ Lightning Fast Category Loading
- **Instant Media:** Opening categories with thousands of images or videos used to cause the app to freeze momentarily while checking the filesystem. I've optimized the query engine to trust the Android MediaStore index, making large category loading nearly instantaneous.

### 🎨 Visual Polish & Fluidity
- **Dynamic Colors:** I've integrated MaterialKolor to automatically generate beautiful, high-quality Material 3 color schemes based on your selected accent color.
- **Smooth Entry Animations:** Files and folders no longer pop onto the screen abruptly. I've added buttery-smooth staggered entry and reordering animations to all list and grid views.
- **Redesigned Image Grids:** The Grid view has been completely overhauled. Images now stretch edge-to-edge within their cards without awkward borders, and every card is locked to a perfect `1:1` aspect ratio so the grid looks perfectly uniform.
- **No More Startup Flicker:** I fixed a visual glitch where the app would briefly flash the default purple theme or the permission screen on launch. The app now seamlessly holds the launch screen until your saved themes are fully loaded.
- **Instant Dashboard:** Opening the Storage Dashboard from the Home screen is now instantaneous, reusing the data you've already loaded instead of calculating it from scratch twice.
- **Polished Permissions:** The initial permission request screen has been redesigned into a beautiful Material 3 Elevated Card, giving a much more professional first impression.
- **Glitch-Free Navigation:** I fixed subtle overlapping layout bugs and "double-padding" issues by cleaning up the root application scaffolding. Folder-to-folder crossfade animations are also now flawlessly typed, preventing random visual artifacts during fast navigation.

### 🌍 Global Support
- **Full Internationalization (i18n):** Arcile is officially ready for the world. I've extracted over 130 hardcoded text strings into a flexible translation system, laying the groundwork for full multi-language support in future updates.

### 🛠️ Stability & Under-the-Hood Fixes
- **Double Refreshing Fixed:** I eliminated a persistent glitch where the Home and Recent Files screens would load their data twice.
- **Silent Failures Patched:** I've fortified the core File Repository. If edge-case operations fail (like creating hidden `.nomedia` files for the Trash Bin, or dealing with locked media indices), they no longer crash silently in the background, making future debugging much easier.
- **Test Suite Enhancements:** I started bridging the gaps in my automated testing suite, adding new core logic checks to ensure critical file naming conflicts (like copying duplicate files) are resolved correctly and safely.

## 🐛 Known Issues (Beta)

As this is an active Beta, please be aware of the following tracked issues:

### 🚨 Critical
- **Splash Screen Hang:** In rare cases of data corruption, the app may hang on the launch screen. (Workaround: Clear app data).
- **Caching IOException:** On some devices, the new storage caching system may fail due to unsanitized volume identifiers.
- **Category Navigation:** Home screen category shortcuts may not function correctly if the app language is set to anything other than English.

### ⚠️ Medium
- **Unscrollable Themes:** On small screens or in landscape mode, the new Accent Color selector may not scroll, hiding some theme options.
- **Redundant Processing:** Opening Storage Management may trigger a redundant background calculation of storage statistics.

### 🔵 Low
- Minor missing translations in the Paste Conflict dialog.
- Incorrect labeling for "Unclassified" drives in the Trash list.
- Ongoing TalkBack accessibility refinements for the new theme swatches.
- Background log-swallowing during file sharing operations.

---


# v0.4.0 Beta

**Release Date:** March 16, 2026  
**Previous release:** v0.3.0 Beta

Welcome to **Arcile v0.4.0 Beta**! This massive update bridges the gap between v0.3.0 Beta and my current release, bringing a fundamental overhaul to how Arcile handles external storage, a brand-new smart paste system, and a ton of Material 3 visual polish.

Here is everything new, improved, and what you should watch out for in this release.

## 🚀 What's New

### 💾 Complete External Storage & SD Card Support
Arcile now fully understands the difference between a permanent SD card and a temporary USB OTG drive.
- **Smart Storage Classification:** When you insert a new drive, Arcile will prompt you to classify it. You can manage these at any time in the new **Settings > Storage Management** screen.
- **SD Cards as First-Class Citizens:** Drives classified as SD Cards now fully support the Trash Bin, file indexing, Home Screen Categories, the Storage Dashboard, and Recent Files.
- **Per-Volume Trash:** Each permanent drive (Internal and SD) now maintains its own dedicated and safe Trash Bin.
- **USB Tracking:** Inserting or removing an external drive can now be tracked by manually refreshing the app—no restarts required!

### 📋 Smart Paste & Conflict Resolution
Copying and moving files just got a lot safer and smarter.
- **No More Silent Overwrites:** I've introduced a robust conflict dialog when you paste files with overlapping names.
- **Smart Folder Merging:** When pasting folders, Arcile now uses a precise "Merge" paradigm instead of a generic replace action.
- **Batch Processing:** Moving a lot of files? Use the new "Do this for all remaining conflicts" checkbox to blast through large transfers.
- **Intelligent Auto-Renaming:** Choosing "Keep Both" will now cleanly append iterative numbers (e.g., `(1)`, `(2)`) instead of polluting your filenames.

### 🎨 Visual Polish & Material 3 Enhancements
Arcile looks and feels better than ever with deep Material 3 integration.
- **Expanded Themes & Accents:** Choose from 20 Material Design color presets, including a true **Monochrome** grayscale mode.
- **Dynamic Storage Bars:** The storage bar now features liquid fill animations, smooth transitions, and dynamic usage-based colors (Green/Orange/Red) for OTG drives to give you immediate capacity feedback.
- **Silky Smooth Animations:** Enjoy smooth folder-to-folder crossfades while browsing, expressive new "squircle" shapes across the app, and a refined fade overlay for the Expandable FAB menu.
- **Redesigned Utilities & Empty States:** The Utilities section on the Home screen has been upgraded to a modern horizontal carousel. I've also added beautiful, animated "Empty States" across the app when there are no files to show. *(Note: "Secure Vault" has been renamed to "OnlyFiles").*
- **Native SplashScreen:** The app launch screen now fully supports the Jetpack SplashScreen API with seamless Light/Dark mode transitions.

### 🗂️ Better Browsing & Organization
- **App State Memory:** Arcile now remembers exactly which folder you were in and your navigation history, even if your phone's memory manager closes the app in the background!
- **Persistent Sorting:** Your sorting preferences (like Date Newest or A-Z) are now saved per-directory or globally.
- **Subfolder Sorting:** You can now apply a sorting filter dynamically to a folder and *all* of its subfolders in one tap.
- **Pull-to-Refresh:** Manually refresh the Home screen, Storage Dashboard, and Recent Files by swiping down.
- **Smarter Defaults:** Home screen categories now default to sorting by "Date Newest" so your latest files are always at the top.
- **Accessibility Improvements:** We've added rich TalkBack screen reader support for file items and improved touch targets across the Home screen for a more accessible experience.
- **Refreshed About Screen:** Check out the updated About screen for a more complete overview of Arcile.

## ⚠️ Things to Keep in Mind

As I roll out these powerful new storage features, there are a few important details to watch out for:

- **Permanent Deletion on USB/OTG:** Drives classified as "OTG" or left "Unclassified" **do not support the Trash Bin**. Deleting files from these drives is **permanent**. Arcile will display an informational banner while you browse these drives to remind you.
- **Excluded from Global Views:** To keep your main library fast and uncluttered, files on OTG/Unclassified drives will **not** show up in your global Search, Recent Files, or Storage Dashboard totals. You can still search them locally by navigating to the drive first!
- **Trash Restore Fallback:** If you try to restore a file from the Trash Bin but the original drive (like an SD card) is no longer inserted, Arcile won't fail—it will simply prompt you to pick a new destination to restore the file to.
- **Dashboard Loading Speeds:** I am still working on a caching layer for the Storage Dashboard. If you have massive amounts of files, you might notice a slight loading time when Arcile calculates the category sizes on the Home screen or Dashboard.

## 🐛 Known Issues (Beta)

As this is a Beta release, there are a few non-critical bugs and UI quirks you might encounter:
- **UI Glitches:** Sometimes the "Empty Folder" illustration might momentarily appear when opening the app directly to a storage volume's root before files load. Additionally, some screens might exhibit minor layout padding quirks.
- **Light Theme Contrast:** A few "secondary" color pairs in the newly expanded Light Theme palettes may have low readability/contrast.
- **Accessibility & Translation:** The app currently lacks full localization (hardcoded English text) and several buttons/icons are missing proper TalkBack screen reader descriptions. Selection state is also not announced by TalkBack in lists.

## 🗺️ Roadmap & Next Steps

Please note that the next release will be slightly delayed. I am shifting my immediate focus toward strengthening the foundation of Arcile. This includes deep dives into:
* **Codebase Architecture:** Refactoring core modules for better scalability.
* **Code Quality:** Implementing more rigorous testing and linting standards.
* **Maintainability:** Simplifying complex logic to ensure the project remains easy to update and contribute to in the long run.

For a detailed list of all technical changes and commits, please see the [CHANGELOG.md](https://github.com/qtremors/arcile/blob/main/CHANGELOG.md).

*Thank you for testing Arcile Beta! If you encounter any issues, please create an issue.*


---


# v0.3.0 Beta

**Release Date:** March 11, 2026  
**Previous release:** v0.2.0 Beta

This is a large update. A lot has changed since v0.2.0 — new features, a full design overhaul, and many bug fixes. Here's what's new.

## ✨ New Features

### Trash Bin
Files you delete are now moved to a hidden Trash Bin — not permanently deleted. You can restore them any time, or empty the bin to permanently delete everything.

### Copy, Cut & Paste
Full clipboard-based file operations. Copy or cut files, then paste them anywhere. A persistent clipboard bar appears in the toolbar so you always know what's in your clipboard.

### File Search with Filters
Search is now scoped to your current folder. You can filter results by:
- Type (Images, Videos, Audio, Docs, Folders, Files)
- Size (e.g. < 10MB, 10–100MB)
- Date modified (Today, Last 7 Days, Last 30 Days)

### Storage Dashboard
Long-press the storage card on the Home screen to open a visual breakdown of your device storage by category. Tap any category to open it directly in the file browser.

### Recent Files — See All
Tap "See All" on the Recent Files section to see a full chronological view grouped by day — Today, Yesterday, and further back.

### Smart Selection
In selection mode, long-press a second file to automatically select everything between your first and second pick.

---

## 🎨 Design Overhaul
The app has been fully updated to **Material 3 Expressive** — new shapes, smoother animations, spring physics, and the Outfit font restored throughout.

---

## 🐛 Bug Fixes
- Fixed: Delete dialog incorrectly said "This action cannot be undone" — it now correctly says files are moved to Trash.
- Fixed: Recent Files screen sometimes showed stale data after file operations.
- Fixed: Some audio files caused the app to crash when loading album art.
- Fixed: Random directories were appearing as 0-byte files in Recent Files.
- Fixed: Double-launch bug when navigating back in the file browser.
- Fixed: OLED dark theme surfaces were not dark enough.

---

## ⚠️ Known Issues

- **Double-back navigation** — When deep inside folders, you may need to press back once extra to fully return to the Home screen. Being tracked for a fix.
- **Clipboard snackbar on resume** — Returning to the app after backgrounding may re-show the clipboard confirmation toast. Cosmetic only.
- **Search scope** — Search only covers your current directory and its subfolders. Global search across all storage is not yet supported.

---

## 🔐 Security
- File and folder names are now validated to block path traversal attacks.
- The app no longer requests legacy external storage permissions on Android 10.


---


# v0.2.0 Beta

**Release Date:** March 06, 2026  
**Previous release:** v0.1.0 Beta

This beta release marks a major step forward in making **Arcile** a premium, high-performance file manager. We've focused on bringing the UI up to modern Material 3 standards and optimizing core systems for native-level smoothness.

## 🌟 Key Enhancements
*   **Material 3 Redesign**: The Settings screen has been completely rebuilt using Elevated Tonal Cards and M3 `ListItem` components, supporting full "Material You" dynamic theming.
*   **120FPS Fluidity**: Added support for 120Hz/144Hz display peak refresh rates and implemented `Modifier.animateItem()` to ensure every interaction feels buttery-smooth.
*   **Visual Thumbnails**: Image and Video previews now load seamlessly across the app thanks to integrated Coil caching.
*   **MediaStore Integration**: Updated the search engine and category views to utilize the Android MediaStore, providing faster, device-wide indexing.
*   **Architecture & Stability**: Switched to **DataStore** for persistent settings and replaced legacy `Stack` structures with modern `ArrayDeque` for more reliable navigation.

## 📁 What's New
- **Search & Sort**: Improved search bar logic and added sort dialogs for better file organization.
- **Hardware Insights**: New interactive developer and hardware information tiles in Settings.
- **Security & Fixes**: Implemented path traversal protection, resolved directory refresh loops, and fixed critical compilation issues in the settings module.

*This beta sets the stage for our 1.0 milestone. Thanks for helping us test the future of Arcile!*

