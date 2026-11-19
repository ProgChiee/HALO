# Student Registry: local validation and future rollout

## Safety and schema

This implementation does not apply migrations automatically. No production connection or existing account modification is required. The existing local server was verified with metadata SELECTs as MariaDB at 127.0.0.1:3307, schema halo_db. Do not reuse application production datasource settings for tests.

The versioned migration is `HALO/database/V20261115_01__student_registry.sql`. It adds:

- `student_registry_import`: importing Admin, timestamp, total/imported/skipped counts.
- `student_registry`: string Student ID, official name/email/course, eligibility/registration status, nullable unique linked user, import provenance, timestamps, and a short-lived keyed verification digest/expiry.
- Unique Student ID, normalized email, and linked-user constraints; foreign keys to users and import history; a status/ID index.

No existing tables or records are changed. Application-created registry emails are trimmed and lowercase; IDs remain strings and must match `^[0-9]{2}AHRT-[0-9]{4}$`. Existing normalized account-email and Student-profile uniqueness constraints remain prerequisites. The supplied SQL targets MariaDB/MySQL and is also exercised against isolated H2 fixtures; native MariaDB/MySQL migration/concurrency still requires local staging verification.

## Import contract

Admin-only `/api/admin/student-registry` provides paginated GET search (ID/name/email), eligibility/registration filters, GET `/history` (latest 50 summaries), GET `/template`, POST `/preview`, and POST `/confirm`.

Preview performs no database writes. Confirmation requires `confirmed=true` and the original file, reparses it, rechecks database conflicts, and atomically inserts eligible records plus history/audit. A concurrent unique conflict rolls back the whole confirmation; preview again. Never overwrite existing registry or user records.

Only valid AHRT rows are imported. Duplicate, invalid, and other-course rows are displayed with row numbers and skipped. Counts are mutually exclusive: duplicates take precedence, then ineligible course, then invalid, then eligible. Ineligible rows are not retained as registry identities; the eligibility filter is available but a normal import creates ELIGIBLE records only. Import history retains summaries, not uploaded workbooks or confidential rejected rows. Failed imports do not create history records.

The template contains deliberately invalid sample guidance: replace its second row with official data. Keep exactly Student_ID, Full_Name, Institutional_Email, Course in columns A-D. The current institutional domain is the existing `paterostechnologicalcollege.edu.ph` policy, not the example school.edu.ph.

The JDK ZIP/XML reader accepts a deliberately restricted plain-data `.xlsx` subset (shared or inline strings): one worksheet, at most 1,000 nonempty student rows (blank formatted rows do not count), 2 MiB compressed, 4 MiB per entry, 8 MiB total expansion, 100 entries. Headers are exact. Formulas, merged cells, external relationships (except ignored worksheet mailto hyperlink metadata), DTDs/entities, macro/binary objects, external-link/connection/query-table structures, and malformed files are rejected. No macros, formulas, external content or hyperlinks are executed/fetched. No new parser dependency is installed. Complex workbooks must be exported as a plain single-sheet workbook first.

Duplicate checks are batched rather than issuing queries per row. Registry APIs expose explicit identity columns, never verification digests or passwords. Acting-mode requests are rejected; the new navigation item is omitted from Super Admin's Admin acting shell.

## Registration and security

The previous registration flow had no email ownership verification; it immediately created ACTIVE accounts. This implementation adds it without changing existing login accounts or password-recovery OTPs.

1. Applicant enters the existing registration fields and requests a code via public POST `/api/auth/register/student/code` with Student ID/email only.
2. The response is generic regardless of registry membership. Only an eligible, unclaimed matching registry record receives a code at its official email.
3. The applicant sends the six-digit `verificationCode` with the existing POST `/api/auth/register/student` body.
4. The backend validates existing fields, locks the registry row, verifies its eligibility, email, unclaimed status and unexpired keyed digest, then creates the user/profile and claims the row in one transaction. A concurrent request cannot create a second account. The official registry name is authoritative; the entered name is validated but cannot rewrite the registry.
5. No account or permanent reservation exists before successful verification. Expiry, mail failure and abandoned attempts leave the record unclaimed. Resend replaces the code after cooldown; older codes cease working. Successful creation clears the digest.

Registration uses the existing secure random/keyed OTP primitives with negative registry IDs to separate them from positive password-reset user IDs. It uses separately namespaced durable account/IP throttle buckets, with the existing configured limits (defaults: 60-second resend cooldown, 3 requests/account/hour, 20/IP/hour, 10 verifications/account/hour, 100/IP/hour). Failed registrations do not refund throttle admission. Do not trust forwarded headers; reverse-proxy IP behavior must be assessed before a future rollout. There are no public registry lookup endpoints.

The existing backend-only password-reset pepper and SMTP configuration are reused. No additional secret or frontend environment variable is introduced. Keep secrets outside version control. Raw OTPs, passwords, registry lists and provider errors are not logged. SMTP tests are mocked.

## Testing on localhost

1. Keep a backup of local halo_db and uploads. Prefer a separate disposable local schema and synthetic users/mailbox for initial UI testing. Explicitly verify host, port and schema before any DDL. Do not use Aiven credentials.
2. Apply the new SQL only to that verified local schema after confirming neither new table already exists and existing user/profile unique constraints are present. MariaDB DDL auto-commits; if interrupted, inspect partial schema rather than blindly rerunning. No migration has been applied to existing halo_db by this implementation.
3. Run the backend with an explicitly local datasource and a local mail catcher. Existing password-reset pepper must be configured. Do not start with production environment files. Run the Vite frontend against that backend.
4. Sign in as a synthetic active Admin. Download/replace the template, preview mixed synthetic rows, cancel, then confirm eligible rows. Search/filter and review import counts.
5. Register the synthetic eligible Student, retrieve the code from the local mail catcher, and submit. Verify login, duplicate rejection, unknown/mismatched identities, expired/resend codes, and absence of accounts before verification.
6. Confirm an existing synthetic Student can still log in without registry membership. Do not test account creation using real students' email addresses.

## Existing accounts, rollback, and future production

Existing accounts are never disabled, claimed, linked, or altered by import. Their IDs/emails are reported as duplicates and skipped. Reconciliation of existing accounts is a separate reviewed operation requiring identity proof; it is not implemented here.

Before production rollout: independently review this security change, verify the migration on disposable native MariaDB/MySQL, verify actual Excel exports and SMTP delivery, check proxy/IP rate limiting, test browser/mobile accessibility, and confirm the existing uniqueness constraints. Populate the official registry before opening new registration. No production migration/deployment is authorized or performed here.

Rollback should first close new registration, then restore the reviewed application version while retaining registry/history for recovery. Do not simply restore unrestricted old sign-up. Do not delete registry or user records as rollback; use approved backups and a separately reviewed data-retention plan. Existing registered accounts retain their original login semantics.

## Executed verification

- 97 focused backend tests: zero failures, errors, or skips. Real registry flows use isolated H2 in MariaDB compatibility mode; existing authentication tests use a mocked registry dependency where registry behavior is outside their original scope.
- Complete frontend suite: 470 passed, zero failures/skips (sequential execution).
- ESLint and Vite production build passed.
- Backend compilation and Spring Boot executable JAR packaging passed in a temporary project without live application resources. The generated JAR is verification evidence, not a deployment artifact.
- No live browser, real SMTP, native MariaDB/Aiven concurrency, or real institutional workbook verification is claimed.

## Files changed for this feature

- `HALO/src/main/java/com/ptc/halo/config/SecurityConfig.java`
- `HALO/src/main/java/com/ptc/halo/controller/AuthController.java`
- `HALO/src/main/java/com/ptc/halo/dtoRequest/StudentRequest.java`
- `HALO/src/main/java/com/ptc/halo/service/AuthService.java`
- `HALO/src/main/java/com/ptc/halo/service/EmailService.java`
- `HALO/src/test/java/com/ptc/halo/AuthLogoutHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/AuthenticationAuditTest.java`
- `HALO/src/test/java/com/ptc/halo/JwtAccountIdentityHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/LoginIssuanceConcurrencyTest.java`
- `HALO/src/test/java/com/ptc/halo/LogoutRevocationTest.java`
- `HALO/src/test/java/com/ptc/halo/PasswordChangeValidationHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/PasswordResetPublicHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/ProfessorAccountConcurrencyTest.java`
- `HALO/src/test/java/com/ptc/halo/RegistrationHttpValidationTest.java`
- `HALO/src/test/java/com/ptc/halo/StudentLoginGreetingTest.java`
- `HALO/src/test/java/com/ptc/halo/StudentRegistrationAtomicityTest.java`
- `HALO/src/test/java/com/ptc/halo/SuperAdminConcurrencyTest.java`
- `src/components/shared/PageShell.jsx`
- `src/data/navigationData.js`
- `src/pages/login/components/Register.jsx`
- `src/routes/AppRoutes.jsx`
- `src/services/authService.js`
- `tests/admin-navigation.test.mjs`
- `tests/auth-presentation.test.mjs`
- `tests/fixtures/backend-contract.json`
- `tests/registration-form.test.mjs`
- `HALO/database/V20261115_01__student_registry.sql`
- `HALO/src/main/java/com/ptc/halo/controller/StudentRegistryController.java`
- `HALO/src/main/java/com/ptc/halo/service/StudentRegistryService.java`
- `HALO/src/main/java/com/ptc/halo/service/StudentRegistryWorkbook.java`
- `HALO/src/test/java/com/ptc/halo/StudentRegistryHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/StudentRegistryIntegrationTest.java`
- `docs/student-registry.md`
- `src/pages/admin/components/StudentRegistry.jsx`
- `src/pages/admin/styles/StudentRegistry.module.css`
- `tests/student-registry.test.mjs`

## Excel compatibility correction (2026-11-15)

A native Excel save can add style-only cells outside A-D and many blank physical worksheet rows. The original parser treated these as invalid columns/excess rows. It now ignores only empty formatting cells and counts nonempty student rows. Nonempty extra columns and formulas remain rejected. Standard scalar cell types are read as text and validated by the existing row rules; fields over 254 characters are no longer rejected at workbook level (the Excel 32,767-character cell ceiling and all archive limits still apply). Headers remain exactly Student_ID, Full_Name, Institutional_Email, Course.

Regression fixtures saved by installed Microsoft Excel reproduce the original rejection and verify the fix, including a generated HALO template filled and saved in Excel. The untouched generated template already parsed correctly in isolation; its guidance row remains deliberately invalid until replaced. Isolated preview tests return HTTP 200 with total=1, eligible=1, invalid=0 for both filled fixtures, with no registry/import/user/audit writes. Final focused verification: 18 backend tests and 10 frontend tests passed; lint, Vite build and isolated executable JAR packaging passed. Existing localhost records and production were not accessed or changed for these tests.
