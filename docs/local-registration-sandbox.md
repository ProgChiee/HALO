# Disposable local registration and email verification

This is a Java 17 test harness, not a deployment profile. It does not use local `halo_db`, Aiven, production SMTP, Supabase, or the normal backend on port 8081. Existing accounts and imported records are untouched.

## Start and stop

From the frontend repository directory in PowerShell:

```powershell
./scripts/start-registration-sandbox.ps1
$sandbox = (Get-Content "$env:TEMP/halo-registration-sandbox-path.txt").Trim()
Test-Path "$sandbox/ready"
```

Wait until the last command returns `True`. Compilation uses existing cached Maven dependencies offline; it installs no project dependencies. The launcher rejects occupied ports and requires Java 17. If startup fails, inspect the sandbox's logs; do not point it at a different database. Do not share logs or the sandbox directory without reviewing their contents.

Run the launcher from your own PowerShell window for browser testing. Agent tool sessions may terminate background children at a session boundary; a stale `ready` file alone is not proof of a live server. Confirm `/healthz` responds on port 8182 and that the separate frontend and catcher open. If a prior run stopped unexpectedly, run the stop script before starting again.

| Component | Address |
|---|---|
| Separate frontend | http://127.0.0.1:5179 |
| Separate backend | http://127.0.0.1:8182 |
| In-memory email catcher | http://127.0.0.1:18025 |
| SMTP sink | 127.0.0.1:11025 |

Stop only these test processes:

```powershell
./scripts/stop-registration-sandbox.ps1
```

Stopping discards the in-memory H2 database and captured messages. A fresh start creates new fixtures and keys. The stop script verifies process start times before terminating recorded PIDs. It never stops the ordinary HALO backend or database. Temporary build files remain for inspection; temporary Admin access and readiness files are removed by the stop script.

## Browser walkthrough

Use **only port 5179**, preferably a separate private browser window. It is hardwired to the sandbox backend, not the usual Vite proxy. Never use real students' details or a real password.

1. Open http://127.0.0.1:5179/register.
2. Use the unused manual-test registry fixture:
   - Student ID: `29AHRT-9001`
   - Full name: `Sandbox Student One`
   - Email: `halo.sandbox.9001@paterostechnologicalcollege.edu.ph`
   - Section: `AHRT 1A`
   - Year: First Year
   - Choose a new disposable password that meets the unchanged HALO requirements, then confirm it.
3. Request the verification code. Before verification, no Student account should exist and the registry remains `UNREGISTERED`.
4. Open http://127.0.0.1:18025 and reload. Read the code in the captured registration message. Do not copy it into logs, screenshots, or this documentation.
5. Enter it in the registration form and submit. Registration should succeed. The official fixture name is authoritative.
6. Log in at http://127.0.0.1:5179/login with the new Student email and disposable password. The Student Dashboard should load with no enrolled subjects.
7. Log out. Open `$sandbox/admin-access.txt` locally in an editor. It contains randomly generated credentials for **this disposable Admin only**. Do not paste them into logs or chat. Log in on port 5179 with those credentials, then open Student Registry. `29AHRT-9001` should be `ELIGIBLE` and `REGISTERED`.
8. Log out and attempt registration again with the same ID/email. It must be rejected; requesting another code gives generic guidance and must not create another account.
9. To repeat from a clean state, stop and restart the sandbox. Do not delete or unlink any record in your normal HALO instance.

## Repeatable HTTP verification

```powershell
node scripts/verify-registration-sandbox.mjs $sandbox
```

This consumes a separate fixture, `29AHRT-9002`, and checks unverified rejection, actual local SMTP delivery, code verification, account creation, Student login, duplicate rejection, and the Admin registry status. It prints only a success summary. Run once per fresh sandbox; it intentionally does not erase registered accounts to make reruns pass. Fixture 9001 remains unused for the browser walkthrough.

## Isolation and limitations

- H2 is in-memory in MariaDB compatibility mode; Hibernate creates/drops only this disposable schema. The registry SQL runs only against that H2 connection. This does not prove native MariaDB/MySQL locking or collation behavior.
- Normal application properties, `.env` files, environment property sources and JVM property sources are not used by the backend's Spring environment. JWT/verification keys are generated per run; no production credentials are copied. Storage directories are under the disposable directory.
- The test-only Java network guard allows only loopback connections to the reserved local SMTP/test ports and checks that remote-host connections are rejected before starting Spring. No actual production connection is attempted. This guard requires Java 17's deprecated SecurityManager API and a test-only Tomcat compatibility flag; do not use it in deployment or upgrade this harness to a newer Java version without revisiting isolation.
- AI/external storage operations are outside this test's scope and are blocked. Authentication, passwords, eligibility, verification, throttling, and registry claiming use the existing application implementation.
- Mailpit's official portable binary was checksum verified but Windows Application Control blocked execution. No policy was changed. The fallback Node SMTP sink has no outbound/relay implementation. It holds at most 16 messages of 64 KiB each, binds to loopback, escapes displayed content, and writes no messages to disk or console. It is a narrow test tool, not a general mail server.
- Anyone with access to your local desktop can view this unauthenticated test inbox while it runs. Use synthetic data only. Close it when finished.
- The supplied setup verifies HTTP and SMTP behavior. Browser clicks and actual MariaDB concurrency require separate verification; no real institutional email should be sent for this test.

## Verification performed (2026-11-16)

- Real loopback HTTP/SMTP check passed: unverified registration rejected, verification message captured locally, verified registration succeeded, Student login succeeded, duplicate registration rejected, and authenticated Admin registry showed REGISTERED. No code, password, or bearer token was printed.
- 45 backend regression tests passed (registry integration, Student login greetings, password recovery HTTP security, and JWT account identity).
- 11 frontend registration/authentication tests passed. Frontend lint passed. The test-only Java launcher compiled successfully; its setup also ran three Excel compatibility tests successfully.
- Mailpit execution was blocked by Windows Application Control; that policy was left unchanged. The Node catcher was used for the actual SMTP verification.
- No browser click-through or native MariaDB test was performed. All account writes were in the disposable in-memory database. The four imported records and existing accounts in normal HALO were not accessed or changed.
