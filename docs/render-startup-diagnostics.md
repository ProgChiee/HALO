# Render startup diagnostics

The deployed JVM flags (`-XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC`), MySQLDialect and successful Aiven connection are confirmed by the owner. They are not outstanding configuration errors.

## Safe changes

- Disable `spring.jpa.show-sql` by default. Production SQL printing is unnecessary overhead; validation and query verification remain enabled.
- Bind to `${SERVER_ADDRESS:0.0.0.0}` using the unchanged `${PORT:8081}` port setting. Render provides PORT=10000. Docker EXPOSE now documents 10000; it does not select the application's port.
- Optional `HALO_STARTUP_TIMING=true` logs start/end and elapsed milliseconds for repository factory and EntityManagerFactory bean initialization. Logs contain application bean names and durations only, never query text, parameters, credentials or exception details. Default is off. Set false/remove after investigation.
- Log context-started and application-ready elapsed times when diagnostics are enabled. A start without a matching end helps locate a slow initialization, but is not proof of deadlock. Timing excludes bean construction/dependency resolution and does not break down individual queries. Failures before this diagnostic bean exists are only covered by normal Spring logs.
- `/healthz` returns 503 with `{"status":"not_ready"}` until Spring's readiness state accepts traffic; then it returns 200 with `{"status":"ok"}`. Shutdown refusal also yields 503. No database or provider calls are made by this endpoint. Existing authentication rules remain unchanged.

## Interpretation and expected impact

HALO has 24 Spring Data repository interfaces and 45 explicit query declarations, including complex reporting queries. Eager repository validation remains enabled. No global lazy initialization, deferred validation, background health listener, or weakened schema/security checks were introduced. There is no demonstrated safe code change that eliminates this work.

Logging reduction may offer a small improvement, particularly after startup; binding removes ambiguity. Diagnostics and readiness gating are correctness/observability changes, not a claimed speedup. No Render runtime benchmark was performed. Enable the timing option for a separately approved deployment and compare timestamp gaps, CPU usage, RSS, and restart/OOM events from that same deployment. Avoid dumping environment variables or credentials. Do not enable SQL parameter TRACE logging.

At 512 MiB, a 60% heap cap is approximately 307 MiB when the container limit is recognized. Metaspace, code cache, stacks, native libraries and network buffers must fit in the remainder. PDF/image processing, indexing and concurrent AI requests also consume memory after startup. A 0.1 CPU allocation can substantially slow Hibernate/query verification. This tier is not a reliable production target for this workload based solely on successful isolated tests. If CPU is saturated or memory is exhausted, use more resources rather than delaying essential checks. A sensible staging measurement target is at least 1 GiB RAM and materially more CPU (preferably one vCPU), then size from measured peak usage; this is not a guarantee or a claim about available Render plan names.

## Verification before rollout

Use an explicitly disposable MySQL 8.4 database and synthetic accounts for cold-start tests; the SuperAdmin bootstrap runner may create an account in an empty database. Keep local password-reset profiles disabled. Preserve ddl-auto=validate and the established private Supabase configuration. No production database, bucket, credential, or platform setting was changed by these code changes.

Measure repeated cold starts at the actual resource limits and a larger comparison size. Check schema validation failures remain fatal, repositories validate before readiness, `/healthz` stays unavailable before readiness and public afterward, JWT/role restrictions remain intact, and shutdown refuses readiness. Exercise enrollment, reporting queries, private attachment access, AI generation/indexing and concurrent material requests on isolated services. H2/mocked tests do not establish native MySQL query performance, Render port detection or production memory sufficiency.
