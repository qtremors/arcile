# Arcile Changelog

> **Project:** Arcile
> **Version:** 0.1.0
> **Last Updated:** 2026-03-04

---

## [Unreleased]

<!-- Accumulate changes here prior to the next formal release. -->

### Added
- Project documentation: `README.md`, `DEVELOPMENT.md`, `CHANGELOG.md`, `LICENSE.md`
- Comprehensive project audit in `TASKS.md` (40 findings across 8 categories)

### Changed
- Reformatted `TASKS.md` to use prioritized task template

---

## [0.1.0] - 2026-03-04

### Added
- File browsing with sorted directory listings (folders first, then alphabetical)
- Breadcrumb navigation bar with auto-scroll and segment tap navigation
- Multi-file selection via long-press with contextual top bar actions
- Create folder via FAB and overflow menu dialog
- Delete selected files and directories (recursive)
- Home screen dashboard with storage summary card, category shortcuts, folder quick-access, and recent files list
- Material You dynamic theming (wallpaper-based colors on Android 12+)
- Custom accent color selection (Blue, Cyan, Green, Red, Purple, Monochrome, Dynamic)
- Theme modes: System, Light, Dark, OLED
- Settings screen with theme mode and accent color bottom sheet pickers
- Tools screen with placeholder cards (FTP Server, Analyze Storage, Clean Junk, Duplicates, Large Files, App Manager, Secure Vault, Network Share)
- Storage permission handling for Android 11+ (MANAGE_EXTERNAL_STORAGE) and pre-11 (READ/WRITE_EXTERNAL_STORAGE)
- Composable top bar component (`ArcileTopBar`) with selection mode, overflow menu, search/sort icons
- Error dialogs for file operation failures
- Bottom navigation bar (Home, Storage, Tools)

### Known Issues
- Search and sort buttons are non-functional (UI stubs only)
- All category/folder shortcuts navigate to storage root instead of specific folders
- Recent file clicks open the file browser instead of the actual file
- Tools screen items are non-functional placeholders
- Theme preferences are not persisted across app restarts
- No file-open capability exists
- No rename UI despite backend support
- Delete has no confirmation dialog
- Settings screen has no back navigation button

---
