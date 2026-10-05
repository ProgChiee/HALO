# ST-09 badge catalog deployment

Stop the application and back up the database before running st09-badge-catalog.sql.
The deployed database could not be inspected because localhost:3307 was unavailable.
The script checks for legacy HALFWAY_THERE rows and aborts if any exist: those awards
must be reviewed manually before deploying the renamed Java enum. It never converts
historical awards into MODULE_FINISHER awards without completion evidence.
For native MySQL/MariaDB ENUM columns it adds MODULE_FINISHER while preserving other
values. VARCHAR columns need no change. DDL implicitly commits. If the guarded call
aborts, the helper procedure may remain; inspect the failure, then drop only
st09_badge_catalog_migration before retrying.

MODULE_FINISHER uses completed=true on an existing StudentModuleProgress record.
SUBJECT_MASTER requires a nonempty set of APPROVED / AI COMPLETED modules in an
eligible year-level Subject, with completed=true for every module. Progression-locked
published modules remain in the denominator; otherwise the first completed module
could prematurely count as an entire subject. Draft/declined/failed modules are excluded.
Awards are checked on the existing quiz-submission award path. No automatic historical
backfill or removal of already-earned badges is introduced.
HALO_ACHIEVER requires ten distinct ordinary badges, excluding itself. FIRST_STEP
remains unchanged; MODULE_FINISHER deliberately overlaps its completion milestone.
The catalog remains twelve entries (eleven ordinary plus HALO_ACHIEVER), matching
Badges.jsx. Names/descriptions of earned badges continue to come from the backend.
