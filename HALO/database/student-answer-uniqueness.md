# ST-01 deployment

Read-only inspection of `halo_db` on 2026-10-05 found no duplicate
`(attempt_id, question_id)` pairs and no equivalent unique index. Only the primary
key and separate non-unique foreign-key indexes were present. No live data or DDL
was changed by this task.

Before restarting with the new mapping, back up the database and stop submission
writers. Select the intended database, rerun the preflight queries in
`student-answer-uniqueness.sql`, then run its guarded migration. It skips an
equivalent unique pair index and raises an error if duplicates exist. Review any
duplicates manually; do not delete or rewrite answers automatically. DDL commits
implicitly. If the procedure aborts, its final DROP might not execute; remove the
helper procedure after investigating before rerunning the script.

Verify the unique index afterward, then restart the backend. Do not rely on
Hibernate schema update to resolve old duplicates. Existing valid rows remain
unchanged. This constraint prevents duplicate answer pairs; it does not implement
the separate ST-06 attempt/concurrency redesign.
