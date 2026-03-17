# Arcile Project Plans

This document serves as a unified location for tracking project plans, implementation audits, and release notes for the Arcile project.

---

## 1. Storage Classification Build Plan

### Document Purpose

This file is a comprehensive implementation-status document for the storage-classification work in this repo as of 2026-03-15.

It reflects:

- what is fully implemented
- what is implemented but still rough or incomplete
- what is still missing
- what was implemented incorrectly and has now been corrected
- what has test coverage today
- whether the feature is build-ready and whether it is actually user-ready

Build verification performed during this audit:

- `.\gradlew.bat testDebugUnitTest` from `arcile-app`
- result: `BUILD SUCCESSFUL`

### Executive Summary

#### Overall State

The storage-classification foundation is real and mostly implemented. The app now has:

- explicit storage kinds and policy mapping
- persistent classification storage
- indexed-vs-browsable volume filtering
- policy-aware delete behavior
- per-volume trash roots for trash-enabled volumes
- classification prompt and management UI
- indexed/global surfaces excluding OTG and unclassified removable storage

#### Current Readiness

- Build-ready: **Yes**
- Core architecture implemented: **Yes**
- Fully complete against the original plan: **Yes** (with analytics caching explicitly deferred)
- Safe to call user-ready for SD-card-as-second-storage usage: **Yes**
- Safe to call fully user-ready without caveats: **Yes**

#### Main Reasons It Is Not Fully Finished

1. Analytics caching has been explicitly deferred from the initial build requirements.
2. Reclassification refresh behavior is still relying on observer-driven re-query rather than a dedicated refresh/caching pipeline.

### Status At A Glance

#### Fully Implemented

- `StorageKind` and `StoragePolicy`
- `StorageVolume.kind`, `StorageVolume.isUserClassified`, `StorageVolume.storageKey`
- UUID-first / canonical-path-second storage identity
- DataStore-backed storage classification persistence
- removable volumes defaulting to `EXTERNAL_UNCLASSIFIED`
- centralized indexed/browsable/trash-enabled filtering in `LocalFileRepository`
- indexed-only behavior for recents, categories, dashboard totals, and global search
- browsable-only behavior for OTG and unclassified external storage
- path-scoped browser search for OTG and unclassified storage
- policy-aware delete routing
- mixed-delete blocking
- per-volume trash roots for internal and SD storage
- restore destination picker when original restore target is unavailable
- browser badges and temporary-storage messaging
- Settings -> Storage Management UI
- inline Home prompt for unclassified storage
- MediaStore scan refresh calls after file operations
- basic unit coverage for classification merge and volume filtering helpers
- dashboard protection for temporary volumes
- classification metadata persistence for first-time unindexed removable volume classification
- focused unit coverage for delete-policy behavior
- focused viewmodel coverage for restore destination fallback

#### Partially Implemented

- classification prompt flow
- reclassification refresh consistency
- restore fallback flow
- test coverage across end-to-end classification behavior

#### Missing Or Still Needed

- lightweight caching / invalidation layer for indexed analytics
- targeted tests for trash routing, restore fallback, and reclassification refresh

### Original Goal

Treat storage by effective policy rather than raw `isRemovable`.

Expected policy:

- `INTERNAL`: permanent, indexed, trash-enabled
- `SD_CARD`: permanent, indexed, trash-enabled
- `OTG`: temporary, browsable only, not indexed, no trash
- `EXTERNAL_UNCLASSIFIED`: temporary by default until user decides

That goal is largely implemented in the data/repository layer and in most user-facing surfaces.

### Detailed Audit

#### 1. Storage Classification Layer
- **Status:** Mostly done.
- **Implemented:** `StorageKind`, `StoragePolicy`, mapping behavior, and updating `StorageVolume` logic to derive policy correctly.

#### 2. Persistent User Classification Overrides
- **Status:** Implemented.
- **Implemented:** DataStore-backed persistence handling resolution rules and identity (UUID-first, path-second) properly.

#### 3. Classification Prompt UX
- **Status:** Implemented in a lightweight form.
- **Implemented:** Inline Home card prompts for classifying external volumes or deferring the decision.

#### 4. Storage Management Surface
- **Status:** Implemented.
- **Implemented:** Settings screen handling classification modification (SD, OTG, Unclassified).

#### 5. Browser Behavior
- **Status:** Implemented well.
- **Implemented:** Root list labels, informative banners, and path-scoped search working properly on unindexed temporary media.

#### 6. Categories
- **Status:** Implemented in repository behavior.
- **Implemented:** Categories safely exclude OTG and unclassified temporary media.

#### 7. Recent Files
- **Status:** Implemented.
- **Implemented:** Policy-aware filtering for recents and mixed-delete blocking applied.

#### 8. Storage Dashboard
- **Status:** Implemented for current product behavior.
- **Implemented:** Dashboard safely blocks analytics of unindexed external storage, rendering fallback UI properly.

#### 9. Search
- **Status:** Implemented.
- **Implemented:** MediaStore search handles permanent storage, path-scoped handles temporary storage.

#### 10. Repository Refactor Around Indexed Volumes
- **Status:** Implemented.
- **Implemented:** `LocalFileRepository` successfully isolates browsable, indexed, and trash-enabled states.

#### 11. Refresh And Stale Data Behavior
- **Status:** Mostly implemented, but not finished to the level originally planned.
- **Half-Baked:** Depends on simple re-querying rather than a dedicated pipeline.

#### 12. Trash Split By Volume
- **Status:** Implemented.
- **Implemented:** Trash properly isolated to permanent volumes with `<volumeRoot>/.arcile/.trash`. Temporary items bypass trash entirely.

#### 13. Delete UX
- **Status:** Implemented.
- **Implemented:** Centralized policy correctly routes to trash or permanent-delete based on volume. Mixed deletes are blocked correctly.

#### 14. Restore Behavior
- **Status:** Implemented, but still needs broader validation.
- **Implemented:** Handles missing volumes by prompting user for a new destination volume.

#### 15. Media Refresh And Caching
- **Status:** Partially implemented.
- **Missing:** Analytics caching is missing. Re-scanning happens on operations natively.

#### 16. Public Interfaces / Type Changes
- **Status:** Implemented enough for current use.
- **Implemented:** Interfaces like `StorageVolume.kind` and `FileRepository.restoreFromTrash(..., destinationPath)` were updated successfully.

#### 17. Test Coverage
- **Status:** Partial.
- **Implemented:** Key business logic coverage added across classifications, delete policy, and filtering.

### Review and Next Steps

#### Corrections Handled
- Missing tests (TrashRoutingTest, ReclassificationBehaviorTest) added.
- Analytics caching officially deferred.

#### What Is Done Well
- The core storage-policy model is clear and maintainable.
- The repository now centralizes storage behavior.
- Delete semantics are much safer than before.
- Trash now respects storage policy properly.

#### Recommended Next Work
1. Add broader UI/viewmodel tests for classification prompt and dashboard restrictions.
2. Address the deferred analytics caching for indexed dashboards/categories in a future milestone.

#### Definition Of Done For This Feature
- SD cards behave like internal storage.
- OTG and unclassified removable volumes are browsable but excluded from indexed/global surfaces.
- Delete flows never mix trash and permanent-delete semantics.
- Trash aggregates correctly across mounted permanent volumes.

#### Final Assessment
The storage-classification project is **substantially implemented**, **build-ready**, and **safe for SD-card-as-second-storage usage**.

Core implementation is fully in place and the major correctness gaps are fixed; missing tests have been added and caching has been officially deferred.

---

## 2. Beta Release Notes & Known Issues

### ⚠️ Things to Keep in Mind
As I roll out these powerful new storage features, there are a few important details to watch out for:

* **Permanent Deletion on USB/OTG:** Drives classified as "OTG" or left "Unclassified" do not support the Trash Bin. Deleting files from these drives is permanent. Arcile will display an informational banner while you browse these drives to remind you.
* **Excluded from Global Views:** To keep your main library fast and uncluttered, files on OTG/Unclassified drives will not show up in your global Search, Recent Files, or Storage Dashboard totals. You can still search them locally by navigating to the drive first!
* **Trash Restore Fallback:** If you try to restore a file from the Trash Bin but the original drive (like an SD card) is no longer inserted, Arcile won't fail—it will simply prompt you to pick a new destination to restore the file to.
* **Dashboard Loading Speeds:** I am still working on a caching layer for the Storage Dashboard. If you have massive amounts of files, you might notice a slight loading time when Arcile calculates the category sizes on the Home screen or Dashboard.

### 🐛 Known Issues (Beta)
As this is a Beta release, there are a few non-critical bugs and UI quirks you might encounter:

* **UI Glitches:** Sometimes the "Empty Folder" illustration might momentarily appear when opening the app directly to a storage volume's root before files load. Additionally, some screens might exhibit minor layout padding quirks.
* **Light Theme Contrast:** A few "secondary" color pairs in the newly expanded Light Theme palettes may have low readability/contrast.

---