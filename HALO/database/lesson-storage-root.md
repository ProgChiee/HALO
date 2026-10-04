# PR-16: stable lesson storage

Set IntelliJ's `HALO_LESSON_STORAGE_ROOT` environment variable to the **absolute
directory containing the existing original lesson files**, before restarting.
The application property is `halo.lesson-storage.root`. Its default is
`${user.home}/.halo/lessons`, independent of the process working directory.
A relative configured root is rejected at startup.

Example (verify this directory contains your files first):
`HALO_LESSON_STORAGE_ROOT=C:/Users/User/Desktop/halo-frontend/halo-frontend/HALO/uploads/lessons`

All upload, generation, material-index and deletion paths use LessonStoragePaths.
New records store relative paths, generally just the stored filename. Original
filename metadata is unchanged. The derived index cache is `.module-index` inside
the configured root; old caches need not be moved and rebuild from original files.

## Existing records: no automatic migration

- `uploads/lessons/<filename>` (including Windows backslashes) maps to
  `<configured root>/<filename>` without changing the database.
- Absolute records still work if inside the configured root.
- Absolute records outside the root, or files spread across multiple historical
  run directories, require manual reconciliation. Back up files and database,
  inventory each record against its actual file, review collisions, and only then
  copy files and update each verified record to its root-relative path. Do not
  strip arbitrary absolute paths or assign filenames without checking contents.
- Missing originals are not replaced by cached text. Recover the originals first.

This change does not move/delete existing files or modify database records.
Stop all backend instances while changing the root; use the same absolute root
for every instance/run configuration. Ensure the service account can read/write it.
Paths outside the root (including symlink escapes) are rejected. Keep this storage
directory writable only by trusted service/operators; validation is not protection
against a local attacker concurrently replacing filesystem links.

PR-15 remains in force: rolled-back uploads are cleaned up and physical deletion
is deferred until DB commit. Filesystem cleanup failures are logged for recovery;
process-crash cleanup is not a durable distributed transaction.
