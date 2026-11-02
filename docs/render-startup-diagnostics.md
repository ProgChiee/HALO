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

## DEFAULT versus DEFERRED experiment (November 2, 2026)

No Docker or Podman CLI was available, including the standard Docker Desktop CLI path. Instead, six sequential fresh JVMs started the full HALO web application against disposable H2 fixtures. Each used `-Xmx307m -XX:+UseSerialGC -XX:ActiveProcessorCount=1`, schema validation, synthetic JWT/AI/bootstrap values, local storage, and a loopback ephemeral HTTP port. Three alternating samples per mode were recorded. These are desktop Java measurements, not Render/container results; ActiveProcessorCount does not impose a 0.1 CPU quota, and Xmx does not cap total process memory. Maven/JVM launch overhead is excluded from the timing window. Shared-host scheduling, file caches and background activity introduce noise.

| Mode/sample | Readiness ms | Listener event ms | Process CPU ms | Heap used bytes | Nonheap used bytes |
|---|---:|---:|---:|---:|---:|
| DEFAULT 1 | 12742 | 12503 | 24984 | 188618256 | 136566872 |
| DEFAULT 2 | 15809 | 15510 | 30421 | 195959600 | 136815704 |
| DEFAULT 3 | 20273 | 19971 | 38578 | 149305512 | 136946888 |
| DEFERRED 1 | 12409 | 6079 | 26312 | 165193504 | 135804696 |
| DEFERRED 2 | 14168 | 6529 | 29843 | 209156592 | 136121880 |
| DEFERRED 3 | 14472 | 7276 | 30859 | 119969584 | 135965944 |

Readiness medians: DEFAULT 15809 ms, DEFERRED 14168 ms. CPU medians: 30421 versus 29843 ms (about 2% less). DEFERRED moved listener initialization substantially earlier, but repository validation still consumed work afterward. The readiness ranges overlap, heap snapshots vary with GC, and nonheap usage is almost unchanged. These results do not establish a reliable CPU/memory benefit at Render's limits. **DEFAULT remains the production behavior; DEFERRED was not adopted.** No health-success-before-readiness or blanket lazy initialization was introduced.

The benchmark captures readiness after SpringApplication.run returns and checks the actual HealthController reports OK. A separate regression deliberately declares an invalid repository query outside HALO's production scan; both DEFAULT and DEFERRED must fail context refresh with schema validation enabled. Existing health tests cover refusal before readiness and unauthenticated endpoint boundaries.

## Targeted dependency cleanup

Startup emitted a warning about duplicate Commons Logging. Dependency inspection identified paths through PDFBox and Google GenAI's Apache HTTP client. The POM excludes `commons-logging:commons-logging` on both paths, retaining Spring Framework 6's `spring-jcl` API/bridge and the existing logging backend. No PDF, Gemini or authentication implementation is removed. A regression checks exactly one LogFactory class resource and verifies PDFBox can acquire a logger. See [Spring Framework logging guidance](https://docs.spring.io/spring/reference/6.2/core/spring-jcl.html).

This removes redundant logging classes and discovery ambiguity. It is a small cleanup, not a demonstrated solution to the port-scan timeout. No percentage speedup is claimed for it.

## Reproducing the benchmark safely

`StartupBootstrapBenchmarkTest` is opt-in and skipped by the ordinary suite. Use an isolated checkout/test copy with H2 test dependencies and a reviewed synthetic properties file; do not supply production properties. The fixture must contain an in-memory H2 URL, H2 driver, and file-based schema initialization scripts for a synthetic database matching HALO's mappings. Use temporary local lesson/cache paths. The harness overrides service credentials with synthetic values and retains `ddl-auto=validate`.

Run each sample in a new Maven/Surefire process, changing only the mode:

```text
mvn -Dtest=StartupBootstrapBenchmarkTest -Dhalo.startup.benchmark=true -Dhalo.startup.benchmark.mode=default -Dhalo.startup.benchmark.config=/absolute/path/to/synthetic.properties "-DargLine=-Xmx307m -XX:+UseSerialGC -XX:ActiveProcessorCount=1" test
```

Repeat with `mode=deferred`. Never use this harness to bootstrap live Aiven or Supabase. Do not treat these results as production load tests: native MySQL queries, cloud latency, real AI, PDF processing concurrency, process peak RSS and exact container throttling remain unverified. More CPU and memory remain the appropriate next step if the deployed service is resource-bound; avoiding essential validation is not an acceptable substitute.
