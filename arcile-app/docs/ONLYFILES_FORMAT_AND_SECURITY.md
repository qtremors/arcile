# Arcile OnlyFiles format and security

This document describes Arcile's OnlyFiles vault format version 1 and its security boundaries. It applies only to Arcile and OnlyFiles.

## Security status

OnlyFiles uses standard authenticated-encryption and password-derivation primitives, but this implementation has not received an independent security audit. Treat it as unaudited software. A strong, unique password and a separate locked backup remain important.

OnlyFiles protects file contents, logical names, folder hierarchy, MIME hints, logical modification times, and thumbnail bytes. It does not hide that a vault exists. An observer of the storage can still see encrypted object counts, approximate ciphertext sizes, sharding layout, and physical filesystem timestamps.

## Format version 1

Every vault is self-contained. App-private and portable vaults use the same format. Version 1 has:

- two recoverable public-header copies;
- authenticated, paged directory manifests;
- opaque, randomly named and sharded encrypted objects;
- generation transaction markers and staging data; and
- no dependency on Arcile's convenience registry for decryption.

The public header exposes only the format version, vault UUID, visible label, creation time, password-derivation parameters, and protected master-secret envelopes. The complete public identity is authenticated so a header cannot be exchanged with another vault. Header updates use a commit record and two copies so password changes do not rewrite file content.

The format is Arcile-native. Unreleased prototype vaults are not compatible with version 1.

## Keys and cryptography

Arcile generates a random 256-bit master secret for each vault. The password envelope uses Argon2id version 1.3 with a random 16-byte salt. New vaults use 64 MiB of memory, three iterations, and parallelism one.

Before processing an attached vault, Arcile validates imported Argon2 parameters. Version 1 accepts memory costs from 32 MiB through 128 MiB, one through ten iterations, parallelism one through four, and bounded salt sizes. Values outside those limits are rejected before derivation.

HKDF-SHA-256 derives separate 256-bit keys for password wrapping, public-header authentication, root-directory metadata, transactions, and encrypted thumbnails. AES-256-GCM provides authenticated encryption. Associated data binds encrypted structures to their vault and role.

Each file has its own random content key. That key exists only in authenticated parent-directory metadata. Child directory keys are protected through their parent manifest; the root directory key is protected by the master secret.

## Files and directories

Files are split into independently authenticated 256 KiB chunks. Authentication binds the vault UUID, object ID, revision, chunk index, and declared plaintext length. The layout supports bounded-memory streaming, random seeking, and logical sizes greater than 4 GiB. Committed ciphertext is never overwritten in place.

Directory manifests contain at most 256 entries per encrypted page. Stable node and directory IDs, rather than decrypted paths, identify items internally. Names are normalized to Unicode NFC, compared case-insensitively within a directory, limited to 255 UTF-8 bytes, and may not contain separators, NUL, or dot segments.

Encrypted thumbnails are stored under Arcile's non-backed-up private storage. Their key is derived in a separate thumbnail domain, and each entry is authenticated with its vault ID, node ID, revision, and size bucket. Decoded thumbnail bitmaps remain in memory and are cleared when vault access locks.

## Transactions and recovery

Each top-level mutation is a generation transaction:

1. Arcile prepares staged metadata.
2. It writes and synchronizes new encrypted objects.
3. It authenticates the complete staged result.
4. It writes and synchronizes a commit marker.
5. It publishes stable manifests.
6. It removes the marker and obsolete objects.

Readers see either the previous complete generation or the replacement. On the next unlock, Arcile ignores incomplete uncommitted staging and completes or cleans up committed work. A failed or cancelled top-level selection is rolled back while earlier completed selections remain reported as complete.

Recovery cannot reconstruct logical names or hierarchy when all authenticated metadata copies are lost. OnlyFiles does not merge concurrent writers or synchronize independently modified copies. Lock a vault before copying or backing up its folder.

Quick health checks authenticate headers, transaction state, manifests, references, declared sizes, and orphan detection. Full checks additionally authenticate every encrypted file chunk. A successful check reports what Arcile could read at that time; it is not a substitute for backups and cannot repair lost authenticated metadata. Orphans are deleted only after an explicit confirmed cleanup.

## App-private and portable vaults

App-private vaults live in Arcile's non-backed-up private application storage. Android permanently removes them when Arcile is uninstalled or its app data is cleared.

Portable vaults live directly in an empty Arcile-managed folder on internal, SD-card, or USB storage. Arcile records only convenience metadata: volume identity, relative path, vault UUID, cached label, and header fingerprint. Absolute paths are not treated as identity.

A portable vault can be attached after reinstall using Add Existing and its password. If removable storage disappears, Arcile keeps the registration unavailable instead of creating a replacement. Removing a portable registration does not delete encrypted files; permanent deletion is a separate, explicitly confirmed action.

For a portable backup, lock the vault first, close Arcile access, and then copy the entire vault folder as one unit. Do not copy an unlocked or actively changing vault.

## Passwords and biometrics

OnlyFiles accepts any non-empty password. Weak passwords receive a warning and require explicit confirmation because encryption cannot compensate for an easily guessed password. Changing a password atomically replaces only the protected master-secret envelope.

Optional biometric unlock requires Android's `BIOMETRIC_STRONG` class. Password unlock is always available as a fallback. Biometric convenience material is protected by Android Keystore and excluded from backup. Enrollment changes, device-security changes, key invalidation, or restored app state can invalidate that material; use the vault password and enroll again.

Interactive keys are zeroed when Arcile leaves the foreground. Vault viewers close, navigation and in-memory clipboard state reset, and decrypted thumbnail memory is cleared. Operation-specific leases let an already confirmed import, export, transfer, or external grant finish without keeping interactive access unlocked.

Unlocked OnlyFiles screens block screenshots and screen recording with `FLAG_SECURE` by default. The global setting can disable that protection.

## Plaintext boundaries

Import, export, boundary move, Share, and Open With require explicit guarded actions. Export writes plaintext to the selected document tree. A boundary move verifies and publishes the destination before permanently deleting the encrypted source. Destination storage is outside OnlyFiles protection.

Share and Open With normally use an unguessable, single-purpose, seekable content grant. The first reader binds the grant to its application UID. A persistent Arcile notification remains while grants are active. Grants can be revoked immediately, are automatically revoked 30 seconds after the final descriptor closes, and have a hard maximum lifetime of 12 hours.

Some receiving apps cannot read streamed content. Only after a second confirmation can Arcile create a compatibility copy. It checks available space, writes with a bounded buffer, verifies the exact length, and stores plaintext only in Arcile's private cache. The first reader is UID-bound. Arcile deletes the copy after use, revocation, expiration, cancellation, process restart, or startup recovery. It never places compatibility plaintext in shared storage.

Once another application reads plaintext, Arcile cannot control copies that application creates. Revoking an Arcile grant does not erase data already copied by the receiver.

## Operational limitations

OnlyFiles is offline and adds no network access. Archives inside a vault are ordinary encrypted files; OnlyFiles does not browse, create, or extract them in place. Imports preserve bytes, names, MIME hints, logical modification times, hidden regular files, and empty folders, but reject symlinks and do not preserve permissions or extended attributes.

Keep the password and locked backups outside the device. Losing both the password/usable envelope and all authenticated metadata can make a vault unrecoverable.
