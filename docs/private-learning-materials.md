# Private learning materials deployment and recovery

No live migration, database change, deployment, or cloud verification has been performed by this implementation. Obtain separate approval before running the apply command against production.

## Configuration

Backend only:

- `HALO_LESSON_STORAGE_PROVIDER=local` (default) or `supabase`.
- `HALO_LESSON_STORAGE_ROOT`: absolute local material root; in Supabase mode this holds disposable module-index caches, not original files. An ephemeral Render cache can be rebuilt from private originals.
- `SUPABASE_URL`: project HTTPS origin, with no storage path.
- `SUPABASE_SECRET_KEY`: server secret (never a Vite variable). New `sb_secret_` keys use the apikey header; legacy service-role JWTs also use Authorization. Restrict access and rotate according to deployment policy.
- `HALO_LESSON_LEGACY_ROOTS`: semicolon-separated exact old absolute lesson roots, e.g. `C:/old-halo/uploads/lessons`. Use the same roots for migration. Do not use drive roots or broad filesystem roots.
- `HALO_CORS_ALLOWED_ORIGINS`: comma-separated explicit Vercel frontend origins. Avoid a wildcard production origin. Defaults remain localhost for development.
- Preserve existing database/JWT/AI provider settings and `ddl-auto=validate`; this feature requires no schema migration.

Frontend: `VITE_API_URL=https://YOUR-BACKEND/api` at Vite build time. No Supabase secret or direct storage URL belongs in the frontend. Configure the Vercel SPA fallback to index.html for client-side routes, without intercepting static assets. Use Render's assigned PORT via the existing server-port configuration when deploying.

The `halo-learning-materials` bucket must already exist and be private. The adapter checks privacy and fails closed; it never creates or changes a bucket. Do not add public object-read policies. All student requests are authenticated backend blob requests. Professor UI is unchanged.

## API

GET `/api/student/ai-learning-modules/files/{fileId}/content` and the Professor equivalent return authenticated bytes. `?download=true` selects attachment disposition. Mode equivalents are `/api/admin/{acting|preview}/{student|professor}/ai-learning-modules/files/{fileId}/content` and require the existing validated X-Acting-Session context.

Student lesson file metadata contains only id, originalFileName, and fileType. Student access reuses enrollment, profile/year, publication, and progression checks for the actual file's module, rechecking after storage retrieval. Professor access uses subject ownership. Existing mode wrappers validate original actor and target. Responses are no-store, nosniff, and carry safe disposition names. Already downloaded bytes cannot be recalled after access revocation.

## Offline inventory and dry run

1. Freeze uploads/revisions for the final cutover and obtain matching database and uploads backups using the approved backup process. Keep immutable originals. Restore the database backup into an explicitly disposable environment, not live halo_db/Aiven. No migration script here connects to a database.
2. Export ALL current file references, draft revision payloads, and immutable revision-history payloads from that restored backup using SELECT only. Use a trusted JSON exporter with this exact shape:

   ```json
   {"attachments":[{"filePath":"UUID.pdf","fileType":"application/pdf"}],"revisions":[{"payload":{"files":[]}}],"history":[{"payload":{"files":[]}}]}
   ```

   Sources: `SELECT file_path AS filePath, file_type AS fileType FROM ai_learning_files`; `SELECT payload FROM lesson_revisions`; `SELECT payload FROM lesson_revision_history`. Payload may be a JSON string or parsed object. Include every row, including empty file arrays. Do not include user credentials or other account records. Independently compare exported row counts to these three tables; an empty/incomplete export cannot prove historical preservation.
3. Arrange a working copy of the uploads backup with paths relative to the original lesson root, preserving every filename. The tool rejects symlinks, traversal, missing files, unknown absolute roots, mismatched type signatures, and files outside the existing 3 MB contract. Missing historical files are a recovery blocker, not permission to discard references.
4. Dry run (no network and no remote writes):

   ```powershell
   node scripts/migrate-learning-materials.mjs --backup "D:/safe-copy/lessons" --inventory "D:/audit/inventory.json" --legacy-roots "C:/old-halo/uploads/lessons" --plan "D:/audit/new-plan.json"
   ```

   Review reference/revision/history counts, object keys, sizes, and SHA-256 checksums. Plan output refuses to overwrite an existing file. Keep plans/inventories private: they contain internal paths. Signature checking during migration is a consistency check, not a replacement for upload parser validation or a malware scan.

## Approved apply and verification (NOT executed)

After separate production approval, set backend-only storage credentials securely in the operator environment and run the same command with `--apply --confirm-bucket halo-learning-materials --journal "D:/audit/verified.jsonl"` (omit --plan or choose a new plan filename).

The tool checks bucket privacy, uploads with overwrite disabled, reads every remote object back, and compares SHA-256. Existing objects must already match. It never overwrites or deletes remote objects, rewrites database records, or deletes local backups. Resume with the same backup/inventory: every source and remote object is reverified; journal entries are evidence, not authority. Partial successful copies can remain safely while a failed run is investigated. Review failures before retrying; do not skip missing historical files.

Preserved database filePath values resolve deterministically to object keys. Configure the exact same legacy roots on the backend. Historical revisions remain unchanged. Test all historical path shapes against a disposable restored database before production cutover.

## Deployment order and rollback

1. Test the full application against disposable database/storage infrastructure, including private-bucket rejection, authorization, generation/index rebuilding, PDF/image preview, download, and revision replacement/removal.
2. Freeze material writes, capture consistent backups/inventory, approve and complete the verified copy, then switch backend provider and deploy. Deploy the frontend API URL/CORS configuration together. No schema migration is required.
3. Verify authenticated reads and generation on approved staging identities before reopening writes. Native Aiven/Supabase behavior, Render startup, and Vercel browser flows remain required external checks.
4. Before any new writes, rollback can restore the previous application/local provider using the unchanged backup and database. After new remote uploads/revisions, do NOT simply switch back to an old local backup: first obtain an approved, checksum-verified reverse copy of all new referenced objects. This tool intentionally does not implement deletion or reverse migration. Keep both stores until recovery verification and retention approval are complete.

Deletion is transaction-aware and retains objects referenced by current file rows, drafts, or immutable history. Unreadable retention data fails closed. The conservative reference scan can cost more as history grows. A process/network failure around a remote upload may leave an unreferenced object; it is not automatically deleted because an ambiguous response must not destroy a valid object. Inventory-based garbage collection requires a separate reviewed operation.

This change covers learning materials only. Other local storage (for example subject covers) needs its own deployment review. Real cloud connectivity, private-bucket configuration, migration completeness, browser PDF support, AI provider behavior, and native database concurrency have not been established by mocked HTTP/H2 tests.
