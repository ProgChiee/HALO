# Lesson revision history migration plan

Status: prepared, NOT applied to the live database. Approval is required before applying.

## Schema and behavior

`lesson_revisions` remains the existing working slot; its concurrency token and draft
workflow are unchanged. `lesson_revision_history` adds immutable published snapshots,
uniquely identified by `(module_id, revision_number)`. Each snapshot retains the exact
serialized materials/generated content, attachment paths and original filenames,
revision concurrency/base versions, author/publisher IDs, and timestamps. Publication
also records the resulting live module version. Unknown legacy metadata stays null.

The application only inserts snapshots and reads them; no history update/delete route
or repository operation exists. Hibernate marks the entity immutable, all content
columns are non-updatable, and entity deletion is rejected. The module foreign key
does not cascade. Account IDs are historical values, not cascading relationships.

For a module starting its first revision after deployment, revision 0 captures the
known initial live content as `INITIAL_PUBLISHED`. Its recorded timestamp is a snapshot
time, not an invented publication date. Every newly published revision is appended in
the same transaction as live activation. A discarded draft may leave a numbering gap.

## Safe deployment sequence

1. Obtain approval. Schedule a maintenance window and stop revision writers. Back up
   the database and configured durable lesson-upload directory together.
2. Record the current revision rows, states, numbers and payload checksums. Check
   backups for missing prior content separately; do not infer it from audit text.
3. Run `lesson-revision-history.sql` against the intended database. DDL implicitly
   commits in MariaDB; it creates a new table only. The backfill copies only intact
   `PUBLISHED` rows, with their payload and metadata unchanged. It never updates,
   deletes or reconstructs existing rows. Run CREATE once; any retry must inspect the
   existing table schema first. The INSERT can safely be retried without overwriting.
4. The verification SELECT must return zero rows. Compare row counts/checksums and
   verify preserved file paths exist. Keep the backup; absent files cannot be recreated
   by this migration. An empty backfill is legitimate if all working rows are drafts.
5. Deploy the code, then verify history reads and an isolated test-environment
   publication before reopening writes. Existing configuration uses `ddl-auto=update`;
   do not start the new build against live MariaDB before approving/applying this
   migration. Prefer a deployment-time `ddl-auto=validate` override after migration.
   No configuration was changed by this fix.
6. Rollback: stop writes and revert the application if necessary, retaining this table
   and uploaded files. Do not DROP snapshots created after deployment.

## Existing loss identified (read-only check, October 22, 2026)

Module 37 has one working row: revision 2, DRAFT, revision token 8, base version 5.
Audit entries show revision publication on October 18 followed by a new revision on
October 20. Revision 1 no longer has its own payload/metadata row. The working draft
still carries the prior publication timestamp due to the old implementation; this
does not make its current payload an immutable revision-1 snapshot.

The current published module remains available, but complete revision-1 metadata and
any earlier overwritten content cannot be certified from that working row. No
automatic reconstruction/backfill of missing numbered revisions from drafts, audit
descriptions or current module content is performed. On the next authorized revision
start or publication, an exact capture of still-live content is retained if no history
exists. It is explicitly labeled `LEGACY_LIVE_BASELINE` (reserved baseline key 0),
with the known module version and capture time; unknown author/publication metadata
stays null. This prevents further loss without pretending to recover revision 1.
Existing drafts can continue through the current workflow; their eventual publication
creates a new exact numbered snapshot, leaving lost prior revisions absent. Recovery,
if required, needs separately approved backup investigation.

## Boundaries

History is available through authorized GET `/revision/history` (paged metadata) and
GET `/revision/history/{revisionNumber}` (content). No new history editor or restore
operation is provided. Student reads continue using the active module. AI review uses
the draft candidate and `buildRevision` without replacing the live cache. Publication
keeps the existing after-commit cache invalidation/rebuild and Mentor revalidation.
Quiz versions, attempts, progress and conversations are not changed by history storage.
The existing stale-draft behavior in Professor audit issue #3 is deliberately unchanged.
