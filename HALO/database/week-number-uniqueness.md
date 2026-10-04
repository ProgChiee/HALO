# Week number uniqueness (PR-09)

Local halo_db preflight: no duplicate (subject_id, week_number) pairs; no equivalent unique index found.

Back up the database and pause academic writes before deployment. Run the SELECT and SHOW INDEX in week-number-uniqueness.sql first. If duplicates exist, stop and obtain an explicit correction plan; never delete or renumber automatically. If an equivalent unique index already exists (including one Hibernate created), skip ALTER. Otherwise apply the ALTER and verify SHOW INDEX. MySQL/MariaDB DDL implicitly commits. The ALTER changes no academic records and fails if conflicting records exist.

The entity also declares uk_week_subject_number. Do not rely solely on ddl-auto to deploy the constraint; verify it in each target database. Existing null week numbers are not repaired here; request validation already requires positive integers for writes.
