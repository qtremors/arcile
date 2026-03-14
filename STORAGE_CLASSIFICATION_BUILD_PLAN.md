# Storage Classification Build Plan

## Status Summary

This document now reflects the current implementation state in the repo.

### Done

- Added stable `storageKey` support to `StorageVolume` in `app/src/main/java/dev/qtremors/arcile/domain/StorageInfo.kt`.
- Added explicit `StoragePolicy` mapping via `StorageKind.policy` and kept convenience accessors like `isIndexed` and `supportsTrash`.
- Added a classification store interface in `app/src/main/java/dev/qtremors/arcile/data/StorageClassificationRepository.kt` and injected it into `HomeViewModel`.
- Refactored `LocalFileRepository` volume discovery to compute `storageKey` from UUID first and canonical path second.
- Updated classification merging to prefer `storageKey` rather than transient `volume.id`.
- Centralized repository helpers for:
  - `currentVolumes()`
  - `browsableVolumes()`
  - `indexedVolumes()`
  - `indexedVolumesForScope(scope)`
  - `trashEnabledVolumes()`
- Refactored indexed repository methods to use indexed-volume filtering:
  - `getRecentFiles`
  - `getStorageInfo`
  - `getCategoryStorageSizes`
  - `getFilesByCategory`
  - global `searchFiles`
- Kept path-scoped browser search browsable for all mounted volumes, including OTG and unclassified storage.
- Made `deleteFile(path)` policy-aware in `LocalFileRepository`.
- Guarded `moveToTrash()` so temporary storage does not use trash.
- Kept mixed delete blocked in both browser and recent-files viewmodels.
- Added temporary-storage messaging in the browser screen.
- Added a temporary-volume exclusion note to the global storage dashboard.
- Updated the home classification prompt to use `storageKey`.
- Added resettable storage classification actions in `HomeViewModel`.
- Added a dedicated `StorageManagementScreen`.
- Wired storage management from Settings through navigation.
- Updated tests to match the new default behavior that removable storage is unclassified and non-indexed until classified.
- Verified the current unit test suite passes with `./gradlew testDebugUnitTest`.

### Partially Done

- Home/dashboard multi-volume cards now show temporary badges, but the browser roots UI still does not expose all requested badge copy variants in a polished way.
- Trash metadata already carries `sourceVolumeId` and `sourceStorageKind`, and trash is aggregated across trash-enabled volumes, but trash UI still does not clearly surface source volume labeling for each item.
- Classification prompt queueing exists in Home, but the UX is still a lightweight inline card rather than a fuller dedicated prompt flow.
- Reclassification refresh works through the storage-volume observer and refreshed queries, but explicit indexed cache invalidation and MediaStore refresh hooks are not implemented yet.

### Not Done Yet

- No dedicated restore-destination picker exists for SD trash restore when the original SD volume is unavailable.
- No richer restore contract exists yet beyond `restoreFromTrash(trashIds)`.
- No explicit MediaStore refresh calls are triggered after indexed file operations.
- No lightweight caching layer for indexed analytics has been added.
- No dedicated repository/unit test coverage yet exists for:
  - storage-key resolution
  - classification merge precedence
  - indexed filtering helpers
  - trash routing by storage kind
  - reclassification refresh behavior

## Current Repo Baseline

The repo now contains the following storage-classification foundation:

- `StorageKind`, `StoragePolicy`, and `StorageVolume.storageKey` in `app/src/main/java/dev/qtremors/arcile/domain/StorageInfo.kt`.
- DataStore-backed classification persistence plus `StorageClassificationStore` in `app/src/main/java/dev/qtremors/arcile/data/StorageClassificationRepository.kt`.
- `LocalFileRepository` classification merge and indexed/browsable/trash-enabled filtering helpers in `app/src/main/java/dev/qtremors/arcile/data/LocalFileRepository.kt`.
- Home prompt state and resettable classification actions in `app/src/main/java/dev/qtremors/arcile/presentation/home/HomeViewModel.kt`.
- A dedicated management screen in `app/src/main/java/dev/qtremors/arcile/presentation/ui/StorageManagementScreen.kt`.

## Goal

Treat storage by effective policy instead of `isRemovable`:

- `INTERNAL` and `SD_CARD`: indexed, shown in permanent surfaces, trash-enabled
- `OTG` and `EXTERNAL_UNCLASSIFIED`: browsable only, excluded from indexed/global surfaces, permanent delete only

## Phase 1: Finish the Domain Model

### Status

Mostly done.

### Done

1. Added explicit `StoragePolicy`.
2. Added `StorageKind.policy`.
3. Extended `StorageVolume` with `storageKey`.
4. Promoted classification APIs into `StorageClassificationStore`.

### Remaining

1. Consider moving `StoragePolicy` into its own file if the domain package grows further.
2. If restore-destination picking is added later, add an explicit domain model for restore outcomes.

## Phase 2: Make Storage Identity Correct

### Status

Mostly done.

### Done

1. `LocalFileRepository` now computes `storageKey`.
2. Classification merge now resolves with `storageKey` first.
3. Primary storage is forced to `INTERNAL`.
4. Removable storage defaults to `EXTERNAL_UNCLASSIFIED`.
5. Home classification actions now persist by `storageKey`.

### Remaining

1. Add focused tests for UUID-first, canonical-path-second identity behavior.
2. Verify real-device behavior when a removable volume path changes but UUID remains stable.

## Phase 3: Centralize Indexed vs Browsable Behavior

### Status

Substantially done.

### Done

Refactored these methods to use indexed/browsable helpers:

- `getRecentFiles`
- `getCategoryStorageSizes`
- `getFilesByCategory`
- global `searchFiles`
- `getStorageInfo`
- `getVolumeForPath`

### Remaining

1. Audit for any remaining UI/business decisions that still implicitly rely on `isRemovable`.
2. Add repository-level tests specifically for `indexedVolumesForScope`.

## Phase 4: Fix Refresh and Analytics Staleness

### Status

Only partially done.

### Done

1. `HomeViewModel` now recomputes per-volume category storage from indexed volumes on refresh.
2. Reclassification is reflected through refreshed storage-volume observation and screen reloads.

### Remaining

1. Add lightweight invalidation or caching for indexed analytics.
2. Add explicit MediaStore refresh after indexed file operations.
3. Add stronger refresh guarantees for:
   - create
   - rename
   - move
   - copy
   - permanent delete
   - move to trash
   - restore

## Phase 5: Complete the Trash Split by Volume

### Status

Partially done.

### Done

1. Trash remains per-volume under:
   - `<volumeRoot>/.arcile/.trash`
   - `<volumeRoot>/.arcile/.metadata`
2. `moveToTrash()` rejects non-trash-enabled storage.
3. `deleteFile(path)` now routes temporary storage to permanent delete.
4. Trash aggregation already scans all mounted trash-enabled volumes.

### Remaining

1. Show source volume details clearly in trash UI.
2. Add restore fallback flow when an SD source volume is missing.
3. Extend restore APIs for explicit destination selection.

## Phase 6: Finish Delete UX Across Browser and Recents

### Status

Mostly done.

### Done

1. Browser and recent-files both branch on trash-enabled vs permanent-delete-only volumes.
2. Mixed delete remains blocked.
3. OTG/unclassified delete does not route through trash.
4. Browser now shows a temporary-storage banner when relevant.

### Remaining

1. Extract shared delete-policy evaluation into one reusable helper instead of duplicating the logic across two viewmodels.
2. Consider adding a more explicit user-facing mixed-delete explanation in both screens.

## Phase 7: Browser, Dashboard, Search, and Categories UX

### Status

Partially done.

### Done

1. Home storage cards show temporary badges.
2. Browser shows a temporary-storage informational banner.
3. Global dashboard shows a note when temporary mounted volumes are excluded.
4. Categories, recents, dashboard totals, and global search now rely on indexed volumes only.

### Remaining

1. Add fuller root-level browser badges and labels for:
   - `SD card`
   - `Temporary USB`
   - `Unclassified external`
2. Review browser root list copy and visuals so SD and temporary states are clearer from first glance.
3. Add explicit per-volume category/dashboard affordance restrictions in the UI where helpful.

## Phase 8: Add Real Classification Management

### Status

Done for the first complete version.

### Done

1. Home prompt uses `storageKey`.
2. Session suppression for `Decide later` is preserved.
3. Added a dedicated `StorageManagementScreen`.
4. Added Settings entry point and navigation route.
5. Added classify-as-SD, classify-as-OTG, and reset actions.

### Remaining

1. Optionally surface a shortcut from Home in addition to Settings.
2. Optionally persist richer prompt queue state if future UX needs it.

## Phase 9: Testing

### Status

Partially done.

### Done

1. Updated existing `StorageScopeViewModelTest` to match indexed-volume semantics.
2. Verified `./gradlew testDebugUnitTest` passes.

### Remaining

Add targeted tests for:

- storage-key resolution and merge precedence
- repository indexed filtering helpers
- trash routing by storage kind
- restore behavior when SD source is unavailable
- reclassification refresh behavior

## Recommended Next Work

1. Add explicit MediaStore refresh/invalidation after indexed file operations.
2. Add restore-to-destination flow for unavailable SD trash items.
3. Improve trash UI to show source volume information clearly.
4. Add focused repository tests for storage identity and indexed filtering.
5. Extract shared delete-policy logic into a reusable helper.

## Biggest Remaining Risks

- Trash restore for unavailable SD media still has no destination-picker path.
- MediaStore-backed freshness may lag after indexed file operations.
- Some storage-state UX is still informational rather than fully explicit, especially in trash and browser roots.
- Storage identity behavior still needs dedicated tests around UUID/path edge cases.

## Definition of Done

The change will be fully done when:

- SD cards behave like internal storage across browser, recents, categories, dashboard, search, and trash.
- OTG/unclassified volumes stay fully browsable but never appear in indexed/global surfaces.
- Every delete flow is policy-aware and never mixes trash and permanent-delete semantics in one action.
- Reclassification reliably adds or removes a volume from indexed surfaces after refresh.
- Trash is aggregated across all mounted permanent volumes and clearly identifies source storage.
- SD-trash restore supports explicit destination selection when the original source volume is unavailable.
