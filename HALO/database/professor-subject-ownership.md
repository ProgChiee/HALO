# PR-01 deployment and legacy assignment

No live database changes or assignments were performed during implementation.

The inspected database contains five subjects (IDs 2, 3, 4, 5, 6). There is no
existing assignment relationship. Two name-based creation-log matches are hints,
not reliable evidence: subject names are editable and logs do not identify the
created subject by ID. All five need an approved mapping.

1. Back up the database and stop backend instances before schema deployment.
2. Inspect `SHOW CREATE TABLE subjects`. Apply the adjacent SQL migration once
   if `professor_id` is absent. Existing `ddl-auto=update` can create the nullable
   mapping on startup; inspect before running DDL to avoid duplicate objects.
3. Review these read-only queries and supply a subject ID -> professor entity ID
   mapping. Do not assign by first login or automatically infer from log text.

```sql
SELECT id, subject_code, subject_name, professor_id FROM subjects ORDER BY id;
SELECT p.id AS professor_entity_id, p.professor_id AS staff_identifier,
       u.id AS user_id, u.name, u.email, u.role, u.status
FROM professor_entity p JOIN user_entity u ON u.id = p.user_id
WHERE u.role = 'PROFESSOR';
```

4. For each reviewed mapping, use a transaction with explicit IDs. Replace the
   two named parameters below in your database client. Verify exactly one row
   changed; otherwise roll back and investigate. Commit only the reviewed result.

```sql
START TRANSACTION;
UPDATE subjects s
JOIN professor_entity p ON p.id = :approved_professor_entity_id
JOIN user_entity u ON u.id = p.user_id AND u.role = 'PROFESSOR'
SET s.professor_id = p.id
WHERE s.id = :approved_subject_id AND s.professor_id IS NULL;
SELECT ROW_COUNT();
-- COMMIT only after verifying; otherwise ROLLBACK.
```

5. Start/restart the backend. Test with two separate Professor accounts.

Unassigned subjects and their weeks return no entries/404 through Professor
academic APIs. They are preserved, and new subjects receive the authenticated
Professor's entity as owner. DTOs and frontend request bodies are unchanged.

This fixes academic Subject/Week endpoints only. Module/file endpoints and
Student Progress ownership remain separate PR-02/PR-03 findings; this change
does not claim those access paths are secured. Student year-level access is
unchanged. No database records are deleted, and ownership is not cascade-deleted.
