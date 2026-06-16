# Storage Cleaner Expansion Plan

## Goal

Expand Arcile's Storage Cleaner from a compact grouped scanner into a safer, more configurable cleanup system that can identify common disposable files, app-related leftovers, duplicate files, stale downloads, install packages, and user-defined cleanup targets while preserving Arcile's trash-first deletion model.

This plan avoids version changes and focuses on implementation direction, risk controls, testing, and staged delivery.

The plan is intentionally Arcile-specific.

## Current Baseline

Arcile already has the following cleaner foundations:

- Dedicated `:feature:storagecleaner` UI with category cards, details sheets, duplicate comparison, confirmation, success/error feedback, and undo.
- Domain models for cleaner candidates, groups, risk levels, risk reasons, scan limits, and persisted rules.
- Recursive scanner with bounded depth/file count limits.
- Exact duplicate detection using size grouping, sampled hashing, and full SHA-256 verification.
- Trash-safe cleanup through `TrashRepository.moveToTrash()`, plus restore-based undo.
- Per-section enablement, ignore patterns, thresholds for large files and old downloads, and exact ignored paths.
- Cached cleaner snapshots to avoid repeated expensive scans.
- Risk labeling for Arcile internals, Android-owned paths, package-like folders, user folders, media folders, backups, logs, dumps, and cache/temp paths.
- Thumbnail cache controls surfaced inside the cleaner.

## Design Principles

- Keep cleanup recoverable by default. Permanent deletion should stay outside the normal cleaner path unless the target volume cannot support trash.
- Prefer explicit review for anything in user media, app-like, Android-owned, backup, or package-like paths.
- Make every detected result explainable: category, matching rule, risk reason, size, age, path, and expected cleanup behavior.
- Keep scanning bounded and cancellable. Large volumes should yield partial but useful results instead of blocking the UI.
- Reuse existing storage abstractions, volume classification, trash routing, mutation finalization, and preferences infrastructure.
- Add cleaner capabilities as small independent scanner rules instead of growing one large conditional block.
- Do not require root for the primary cleaner experience. Features that need elevated access should degrade cleanly or be hidden.

## Target Architecture

### Cleaner Rule Engine

Introduce a rule-driven scanner layer under `core/storage`:

- `CleanerRule`: describes a cleanup rule with stable ID, category, label key, description key, default enabled state, target file types, matching logic, and risk policy.
- `CleanerRuleMatch`: includes the matched file snapshot, rule ID, group type, explanation, confidence, and risk metadata.
- `CleanerRuleRegistry`: provides stock rules and user-defined rules.
- `CleanerScanContext`: contains current time, volume metadata, installed package snapshot, rules, scan limits, and path helpers.
- `CleanerScanProgress`: reports current root, current path, scanned count, matched count, skipped count, partial reason, and active phase.

Keep `CleanerGroupType` for UI grouping, but allow multiple rules to feed the same group.

### File Snapshot Model

Expand the scanner's internal snapshot so rules do not repeatedly touch the filesystem:

- Name, extension, absolute path, parent path, normalized lowercase path, path segments.
- Size, last modified, file/directory flag.
- Volume ID, volume kind, and whether the volume is trash-capable.
- Derived flags: in downloads, in media folder, in app-like folder, in Android data/obb/media, hidden file, package-like segment.
- Optional APK metadata and media metadata loaded lazily only for matching candidates.
- Optional package ownership metadata loaded only when app-related rules are enabled.

### Scan Pipeline

Recommended pipeline:

1. Resolve indexed volumes.
2. Load persisted cleaner rules.
3. Load installed package metadata if APK or leftover rules are enabled.
4. Walk volume roots with cancellation and scan limits.
5. Convert filesystem entries to snapshots.
6. Apply global skips and ignored paths.
7. Apply enabled stock rules and custom rules.
8. Run duplicate detection as a separate phase over eligible files.
9. Merge matches into candidates.
10. Classify risk, sort, cap per group, store snapshot, and publish progress.

## Stock Cleaner Categories To Add

### Platform Leftovers

Add rules for files commonly created when storage is connected to desktop operating systems:

- Windows files: `desktop.ini`, `thumbs.db`.
- macOS files: `.DS_Store`, `._*`, `.Trashes`, `.Spotlight-V100`, `.fseventsd`, `.TemporaryItems`.
- Linux trash folders: `.Trash`, `.Trash-*`.

Default risk:

- Low for exact marker files.
- Review for recursive trash folders because they can contain user content.
- High if inside Android-owned or Arcile-owned paths.

### Android Trash Markers

Add a rule for Android-style `.trashed-*` files.

Default risk:

- Review by default because these may already be user-deleted files.
- Low only when in known cache/temp folders.

### LOST.DIR

Add a `LOST.DIR` rule for removable-transfer leftovers.

Default risk:

- Review. These may contain recovered user files with missing names.
- Show clear explanation and avoid auto-selecting by default.

### Thumbnail Caches

Add rules for accessible thumbnail cache paths:

- `.thumbnails`
- `thumbs`
- `thumbnail`
- app-specific thumbnail/cache folders where confidence is high

Default risk:

- Low for cache-only folders.
- Review for media folders with mixed content.

### Screenshots By Age

Add an optional old screenshots rule:

- Match files under common screenshot directories.
- Default age threshold: 90 days.
- User-configurable age threshold.
- Disabled by default or set to Review, because screenshots are user-created content.

### Logs, Dumps, And Temporary Files

Broaden the current simple extension matcher:

- Extensions: `.log`, `.tmp`, `.temp`, `.dmp`, `.trace`, `.stacktrace`, `.crash`, `.bak`, `.old`.
- Folder names: `tmp`, `temp`, `cache`, `caches`, `logs`, `crash`, `crashes`.
- Include age thresholds for temp/log files to avoid touching files from active work.

Default risk:

- Low for old files in cache/temp folders.
- Review for backups and logs outside cache/temp folders.

### Old Downloads

Improve existing old downloads detection:

- Separate old downloads into file-type subfilters: APKs, archives, documents, media, unknown.
- Add user thresholds for age and minimum size.
- Add sorting by age, size, and extension.
- Avoid auto-selecting documents/media by default.

### APK Cleanup

Upgrade APK category from "all APKs" to APK status detection:

- Installed same version.
- Installed newer version.
- Installed older version.
- Not installed.
- Unreadable/corrupt APK.
- Split package archive where metadata can be read.

Default cleanup suggestions:

- Low or Review for same/older APKs when not in backup folders.
- Review for not-installed APKs.
- Review or High for files under backup/recovery folders.
- Never assume backup APKs are disposable.

### App-Related Public Caches

Add accessible app cache grouping without root:

- Detect package-like folders on public storage.
- Resolve installed app labels/icons where possible.
- Identify common cache folders below app-associated public paths.
- Group by app when package ownership can be inferred.

Default risk:

- Low for cache/temp folders under known installed apps.
- Review for media, backup, export, downloads, or database-like folders.
- High for Android/data or Android/obb paths on modern Android unless the operation route is clearly supported.

### Uninstalled App Leftovers

Add a leftovers category:

- Find package-like folders.
- Compare against installed packages.
- Mark folders whose owner package is no longer installed.
- Include known public locations such as root-level app folders and accessible app media/data folders.
- Exclude common false positives and generic folder names.

Default risk:

- Review for normal leftovers.
- High when ownership is ambiguous or folder contains media/documents/backups.

### Duplicate Files

Keep exact duplicates as the default. Add better decision support:

- Suggested keeper per duplicate group.
- Reasons for keeper choice: newest modified date, oldest modified date, shortest path, outside cache, outside Downloads, user-pinned folder, highest-resolution media when metadata is available.
- Per-group "select all except keeper".
- Option to exclude a folder from duplicate detection.
- Minimum duplicate size setting.

Future optional work:

- Similar image detection.
- Similar audio detection.
- Media fingerprinting for videos/audio.

These should be treated as separate features because they are heavier and need more CPU, memory, and battery controls.

### Safe Quick Clean

Add a conservative one-tap cleanup profile that only selects low-risk matches:

- Old temp files in cache/temp folders.
- Exact desktop marker files.
- Same-version or older APK installers outside backup/recovery folders.
- Empty folders outside media, Android-owned, Arcile-owned, and app-like paths.
- Exact duplicates only when a keeper is selected and all removed files are low-risk.

Behavior:

- Never include Review or High risk items by default.
- Always show the confirmation surface before cleanup.
- Keep undo support when trash metadata is available.

### Cleaner History

Add local cleanup history:

- Scan start/completion timestamps.
- Cleanup operation timestamp.
- Cleaned count, failed count, skipped count, and reclaimed bytes.
- Target categories and selected rule IDs.
- Trash IDs for undo-capable operations where available.
- Partial failure details with recoverable messages.

Use this for user trust, debugging, and future automation summaries.

### Uninstall Watcher

Add an optional watcher that reacts after package removal:

- Detect recently removed packages.
- Scan only likely leftover locations for that package.
- Notify the user when leftovers are found.
- Never auto-clean by default.
- Offer a direct review flow into the leftovers category.

This should be implemented only after the app-leftover ownership model is reliable.

### App-Private And Inaccessible Cache

Keep this separate from the baseline cleaner because platform restrictions vary:

- Detect when app-private cache cleanup is unavailable.
- Explain what setup or platform capability would be required.
- Consider user-guided system cleanup flows only if they are reliable and transparent.
- Avoid background automation for destructive cleanup unless the user explicitly opts in.
- Keep inaccessible-cache estimates separate from directly selectable files.

This should not block the public-storage cleaner roadmap.

## Custom Cleaner Rules

Build a user-defined rule editor on top of existing cleaner preferences.

Rule fields:

- Label.
- Enabled state.
- Target type: files, folders, or both.
- Category destination.
- Name match mode: contains, starts with, ends with, exact, wildcard.
- Path match mode: contains, starts with, ends with, exact, wildcard.
- Exclusions with the same match modes.
- Min/max size.
- Min/max age.
- Optional extension allowlist.
- Optional volume scope.
- Risk override: low, review, high.

Safety behavior:

- Custom rules default to Review risk.
- Custom rules require a preview before cleanup.
- Custom rules should be exportable/importable only after the schema stabilizes.
- Invalid or underdefined custom rules should be disabled with an explanatory UI state.

## Exclusions And Ignore System

Expand current ignored paths into a general cleaner exclusion model:

- Ignore exact path.
- Ignore folder subtree.
- Ignore file name pattern.
- Ignore path pattern.
- Ignore package/app.
- Ignore rule ID.
- Ignore category.
- Temporary hide result until next scan.

Persist exclusions with schema versioning and migration from current ignored paths.

UI actions:

- Ignore this item.
- Ignore containing folder.
- Ignore this rule for this folder.
- Manage ignored items.
- Restore ignored item.
- Reset category rules.

## UI Plan

### Cleaner Home

Keep the current category card layout, but add:

- Scan progress row with current phase and scanned count.
- Partial scan warning with reason.
- Last scan timestamp and refresh action.
- Total selected size when selections exist.
- Summary chips for low/review/high risk counts.

### Category Details

Add:

- Filter by risk.
- Filter by rule/source.
- Sort by size, age, name, path, risk.
- Group by app, folder, rule, or duplicate cluster where relevant.
- Explanation panel for why an item matched.
- Per-item ignore actions.

### Confirmation

Before cleanup, show:

- File count and folder count.
- Total bytes.
- Trash destination summary.
- High-risk count.
- Items that cannot be moved to trash and why.
- Warning if operation is partial or mixed-volume.

### Duplicate Compare

Improve duplicate flow:

- Show suggested keeper.
- One-tap select all except keeper.
- Explain why the keeper was chosen.
- Allow user to change keeper.
- Show file previews, path, size, modified time, app/folder context, and risk.

### Custom Rules

Add a separate "Cleaner rules" screen:

- Stock rules list.
- User rules list.
- Per-rule enable toggle.
- Per-rule thresholds.
- Import/export controls later.

### Cleanup History

Add a history view or Activity integration:

- Recent cleaner scans.
- Recent cleaner cleanup operations.
- Reclaimed space.
- Failed/skipped items.
- Links to Trash when undo/restore is available.

### Storage Dashboard Integration

Connect cleaner opportunities to storage analysis:

- Surface top cleanup opportunities from the dashboard without starting destructive actions.
- Link category storage rows to relevant cleaner filters where appropriate.
- Show cleaner reclaimed-space history in storage summaries once cleanup history exists.

## Data And Persistence

DataStore:

- Continue storing cleaner preferences in DataStore.
- Add schema version for cleaner rules/exclusions.
- Keep forward-compatible JSON with `ignoreUnknownKeys`.

Room:

- Keep cached scan snapshots.
- Consider storing per-candidate rule IDs and explanations if snapshots need richer UI recovery.
- Store scan metadata: roots, limits, rules hash, started time, completed time, partial reason.

Cache invalidation:

- Invalidate cleaner snapshots after trash, restore, permanent delete, copy/move into scanned roots, MediaStore broad changes, volume classification changes, and cleaner rule changes.
- Prefer scoped invalidation when possible, but full invalidation is acceptable for rule/schema changes.

## Permissions And Platform Constraints

Baseline cleaner must work with Arcile's existing storage access model.

Expected constraints:

- Android/data and Android/obb may be inaccessible or restricted.
- App private caches generally require platform APIs, usage access, root, ADB, or user-guided system flows.
- Some removable storage operations may bypass trash depending on volume classification.
- Package visibility declarations may be needed for reliable app labels, icons, ownership checks, and APK status detection.

UI should clearly distinguish:

- Accessible and trash-safe.
- Accessible but permanent-delete-only.
- Inaccessible without extra setup.
- Detected but skipped due to platform limits.

## Safety Rules

Hard skips:

- Arcile internal folders unless specifically surfaced by an Arcile-owned maintenance card.
- Active trash metadata and payload directories.
- Volume roots.
- Android system-owned directories when path handling is not explicitly supported.
- Symlinks or suspicious canonical path mismatches.

Pre-clean validation:

- Canonicalize all selected paths.
- Re-check existence and type before cleanup.
- Collapse nested selections to distinct roots.
- Recalculate high-risk status before cleanup.
- Route through existing trash/delete decision infrastructure.
- Record mutation operations so interrupted cleanup can be recovered.
- Require explicit opt-in before any background or scheduled cleanup can run.

Post-clean validation:

- Remove cleaned candidates optimistically only after successful trash move.
- Invalidate relevant caches.
- Surface partial failures with path-level detail.
- Preserve undo IDs when trash metadata is available.

## Performance Plan

Scanning:

- Keep file and depth limits configurable.
- Add progress throttling.
- Avoid loading thumbnails during scan.
- Avoid APK metadata reads until candidates pass cheap filters.
- Avoid full hashing unless duplicate candidates share size and sample hash.
- Run duplicate hashing as a separate cancellable phase.

Memory:

- Stream scan snapshots where possible.
- Cap candidates per group.
- Keep duplicate candidate sets bounded by minimum size.
- Avoid storing large path lists in Compose state.

Battery:

- Do not auto-run heavy scans repeatedly.
- Reuse cached scan snapshots.
- Provide manual refresh.
- Defer heavy duplicate/media analysis behind explicit user action if needed.

Background work:

- Prefer user-initiated scans.
- Allow future scheduled scans only for low-cost checks or notifications.
- Do not run cleanup in the background unless a dedicated opt-in automation profile exists.
- Persist enough operation state to summarize interrupted scans or cleanup attempts.

## Testing Plan

Unit tests:

- Rule matching for every stock rule.
- Risk classification for sensitive/user/cache/media/app-like paths.
- Pattern matching modes.
- Custom rule serialization and normalization.
- Exclusion migration and matching.
- APK status classification with fake package metadata.
- Duplicate keeper selection.
- Nested selection collapse.
- Partial scan reasons.
- Safe quick-clean eligibility.
- Cleanup history serialization.
- Package removal watcher targeting.

Scanner tests:

- Bounded max files.
- Bounded max depth.
- Inaccessible folder handling.
- Snapshot cache reuse and invalidation.
- Rule enable/disable behavior.
- Ignored path behavior.

ViewModel tests:

- Progress state updates.
- Scan cancellation.
- Cached result followed by fresh result.
- High-risk cleanup acknowledgement.
- Undo cleanup.
- Rule updates trigger rescan.
- Ignore/unignore triggers rescan.
- Quick-clean selection excludes Review and High risk items.
- Cleanup history is updated after success and partial failure.

UI tests:

- Category details render matched rule explanations.
- Confirmation displays high-risk warnings.
- Duplicate suggested keeper flow.
- Custom rule editor validation.
- Ignore management.
- Partial scan banner.
- Cleanup history rendering.
- Safe quick-clean confirmation.

Device/manual tests:

- Primary storage.
- SD card/removable storage.
- OTG/temporary storage classification.
- Large Downloads folder.
- Duplicate media folder.
- APK archives.
- Desktop leftover files.
- Android 13+ restricted paths.
- Trash/restore after cleaner cleanup.
- Package uninstall leftover scan.
- App label/icon/package visibility behavior.

## Delivery Phases

### Phase 1: Rule Engine Foundation

- Add `CleanerRule`, `CleanerRuleMatch`, `CleanerRuleRegistry`, and `CleanerScanContext`.
- Move existing group heuristics into stock rules.
- Preserve current UI behavior and result groups.
- Add rule IDs and explanations to candidates.
- Add scan progress metadata.

Acceptance:

- Existing cleaner tests pass.
- Existing categories behave the same or better.
- No UI regression in category cards, details, cleanup, or undo.

### Phase 2: Low-Risk Stock Rules

- Add platform leftovers.
- Add expanded temp/log/dump detection.
- Add Android `.trashed-*` rule.
- Add thumbnail cache folder detection.
- Add `LOST.DIR` as Review risk.

Acceptance:

- Each rule has tests.
- High-risk paths are not auto-cleaned.
- Matching explanations are visible in details.

### Phase 3: APK Intelligence

- Add APK metadata reader abstraction.
- Add installed package snapshot provider.
- Classify APK candidates by install/version state.
- Add backup/recovery path exclusions and higher risk.

Acceptance:

- APK status appears in details.
- Unreadable APKs do not crash scans.
- APK metadata reads are lazy and cancellable.

### Phase 4: Exclusions And Rule Management

- Add generalized cleaner exclusions.
- Migrate existing ignored paths.
- Add ignore actions for item, folder, rule, and category.
- Add rule management screen for stock rule toggles and thresholds.

Acceptance:

- Existing ignored paths continue to work.
- Users can restore exclusions.
- Rule changes invalidate snapshots and rescan.

### Phase 5: Custom Rules

- Add custom rule schema.
- Add rule editor.
- Add validation and preview.
- Add import/export after schema stabilization.

Acceptance:

- Underdefined rules cannot silently match broad storage.
- Custom matches default to Review risk.
- Rules survive app restart.

### Phase 6: App-Related Cache And Leftovers

- Add package-like folder ownership inference.
- Add installed/missing package checks.
- Add app grouping with labels/icons.
- Add leftovers category.

Acceptance:

- False positives are Review or High risk.
- Ambiguous ownership is explained.
- Installed app folders are not labeled as uninstalled leftovers.

### Phase 7: Duplicate Decision Support

- Add keeper strategy model.
- Add suggested keeper in duplicate groups.
- Add select-all-except-keeper.
- Add duplicate exclusions.

Acceptance:

- Exact duplicate detection remains unchanged.
- Keeper suggestion is explainable and overrideable.
- Cleanup still routes through trash and undo.

### Phase 8: Safe Quick Clean And History

- Add conservative quick-clean selection.
- Add cleanup history persistence.
- Add Activity/history UI integration.
- Add dashboard cleanup opportunity links.

Acceptance:

- Quick clean never includes Review or High risk candidates by default.
- History records successful, failed, skipped, and partially completed cleanup operations.
- Dashboard links do not trigger cleanup directly.

### Phase 9: Optional Watchers And Inaccessible Cache Guidance

- Add package-removal leftover notifications.
- Add inaccessible-cache status/explanation UI.
- Add future automation settings only behind explicit opt-in.

Acceptance:

- Watchers only notify and route to review.
- Inaccessible cache is never mixed with directly selectable files.
- No background destructive action runs without explicit setup.

### Phase 10: Advanced Media Similarity

Optional future phase:

- Similar photo detection.
- Similar audio detection.
- Media fingerprinting.
- Separate heavy-scan controls and battery warnings.

Acceptance:

- Disabled by default unless performance is proven.
- Heavy scans are cancellable and scoped.
- Similarity results are never treated as exact duplicates.

## Suggested Priority

Highest value with low risk:

1. Rule engine foundation.
2. Platform leftovers and expanded junk rules.
3. APK intelligence.
4. Generalized exclusions.
5. Duplicate keeper suggestions.

Higher complexity:

1. Custom rule editor.
2. App-related cache grouping.
3. Uninstalled app leftovers.
4. Safe quick clean and cleaner history.
5. Package-removal watcher.
6. Inaccessible-cache guidance.
7. Similar media detection.

## Open Questions

- Should old screenshots be enabled by default, or only available as an opt-in rule?
- Should custom rules support regex initially, or should the first version stay with wildcard and match modes?
- Should app-related cleanup live under Storage Cleaner, or become a separate App Cleaner tool later?
- Should duplicate scans continue to run automatically, or move behind an explicit "scan duplicates" action for very large storage?
- Should cleaner snapshots store full candidate metadata or only enough to recreate the current UI?
- Should quick clean be a prominent action or stay behind a review-first workflow until user trust is established?
- Should package-removal scanning notify immediately, batch notifications, or only run when the cleaner is opened?
- Should inaccessible app cache be displayed as an estimate, a guided action, or omitted until reliable platform support exists?

## Definition Of Done

A cleaner expansion is complete when:

- Every new rule has unit coverage and clear UI explanations.
- Cleanup remains trash-first and undo-capable where the volume supports it.
- High-risk results require explicit acknowledgement.
- Scan progress and partial results are visible.
- Rule/exclusion changes persist and invalidate cached scans correctly.
- Large-volume scans remain cancellable and bounded.
- Quick-clean and background-related features never bypass review, confirmation, or trash routing.
- Cleaner history accurately records successes, skips, failures, and partial operations.
- Changelog and development documentation are updated for shipped behavior.
