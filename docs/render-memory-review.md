# Render Free memory review — November 2, 2026

## Evidence and diagnosis

The owner reported a Render out-of-memory event above 512 MB at 1:18 PM Asia/Manila, a subsequent 502, and startup taking 306.095 seconds. Those are supplied production observations; this investigation did not access Render, Aiven or Supabase. The JVM options and MySQL dialect are confirmed correct. An OOM confirms insufficient memory at that moment, not a particular leak or a particular heap/native allocation cause.

`MaxRAMPercentage=60.0` limits heap sizing, not total RSS. If the JVM recognizes a 512 MiB container, its maximum heap is approximately 307 MiB. Prior isolated startup samples used roughly 130 MiB of nonheap memory (metaspace/class metadata and code cache together). Heap maximum plus that nonheap snapshot leaves only around 75 MiB for additional native memory, stacks, direct buffers and other process costs. These quantities are illustrative, not a production RSS measurement; reserved virtual memory is also not the same as resident/committed memory.

Contributors inspected:

- Spring/Hibernate/Security/Spring AI class loading and query plans consume heap and metaspace. DEFAULT repository initialization and MySQL schema validation remain eager. The previous DEFAULT/DEFERRED experiment did not establish a reliable CPU/memory reduction; DEFAULT is retained.
- Hikari's default idle floor equals its maximum (10), maintaining warm JDBC/TLS connections even when idle. Remote connection creation and schema metadata round trips can add startup time. Query verification also performs substantial local parsing; the last query-init log line alone does not identify a network wait.
- Tomcat's default 10 spare request threads hold stack/native resources. Its maximum is 200, not 200 eagerly created threads. Gemini HTTP clients, the Supabase HTTP client, Hikari and JVM internal threads add separate costs. No custom application-wide unbounded executor was found.
- Runtime indexing holds up to ten 3 MiB originals while computing its content fingerprint. PDFBox, decoded rasters, OCR images, generated text and AI request serialization add allocations. The current 40-million-pixel image validation limit permits a roughly 153 MiB four-byte-per-pixel raster, despite a compressed upload being at most 3 MiB. The limits and educational behavior are unchanged.
- Preview/session state and revision-history processing add runtime heap use. No production heap dump was obtained, so no leak or exact dominant allocation is claimed.
- The Docker runtime uses a JRE and does not run Maven. CA import precedes Java startup; the existing exec entrypoint, truststore, PORT and readiness behavior remain intact.

## Minimal implemented changes

1. `spring.datasource.hikari.minimum-idle=${HALO_DB_MIN_IDLE:1}` reduces the idle floor. Maximum connections remain at the existing default/configured value. This matters because nested `REQUIRES_NEW` history-retention transactions may require another connection while an outer transaction retains one. The new isolated regression verifies that expansion still works.
2. `server.tomcat.threads.min-spare=${HALO_HTTP_MIN_SPARE:2}` reduces eager idle request workers. Maximum workers, request behavior and authorization are unchanged.
3. Material indexing drops its list reference to each original as that file is extracted, instead of retaining already processed originals until all later PDF/OCR/AI work completes. Fingerprinting still covers all originals, and no extra storage fetch occurs. This permits earlier GC; it does not eliminate the initial up-to-30-MiB buffer peak or guarantee a particular RSS reduction.
4. Opt-in startup diagnostics now report heap used/committed/max, nonheap used and live-thread count at readiness. Enable with the existing `HALO_STARTUP_TIMING=true`; no credentials, environment values, documents or user data are logged. These counters are not a complete native-memory or RSS measurement.

Tradeoff: an idle service may need to open more DB connections/create workers on its next traffic burst. Default Hikari idle retirement timing is unchanged, so warmed excess connections need not disappear immediately after a burst. Existing maximum capacities are intentionally preserved, meaning these changes do not bound peak concurrency memory. Restore the former idle behavior with HALO_DB_MIN_IDLE=10 and HALO_HTTP_MIN_SPARE=10 if latency testing requires it.

No heap, metaspace, stack or direct-buffer cap was imposed. A lower heap can move a container OOM into Java heap exhaustion; arbitrary native/stack caps can cause new runtime failures. No feature, schema validation, security filter, CORS rule, AI integration, bucket setting, or academic rule was removed. `/healthz` still reports 503 until readiness, then 200. No migration or production configuration change was performed.

## Measurement method and limits

The opt-in `StartupBootstrapBenchmarkTest` compares the former idle settings with `-Dhalo.startup.benchmark.low-idle=true`, in separate JVMs using DEFAULT bootstrap, synthetic H2, local storage, a 307 MiB heap, Serial GC, and ActiveProcessorCount=1. It records actual pool connections, Tomcat workers, live JVM threads, CPU time, heap/nonheap snapshots and readiness time, while asserting maximum capacities remain 10 DB connections and 200 Tomcat workers. Docker remains unavailable: this is not a 512 MiB container or 0.1 CPU quota test, not a native MySQL latency test, and not real Gemini/Supabase traffic. Heap snapshots are GC-dependent; they are not peak or post-GC retained heap measurements.

Four alternating fresh-JVM samples on Windows/Java 17:

| Settings/sample | Ready ms | CPU ms | DB connections | Live JVM threads | HTTP workers | Heap used bytes | Nonheap used bytes |
|---|---:|---:|---:|---:|---:|---:|---:|
| Former idle minima 1 | 17382 | 32406 | 10 | 26 | 10 | 134334672 | 136875488 |
| Reduced idle minima 1 | 17497 | 33578 | 2 | 18 | 2 | 150794992 | 136293616 |
| Former idle minima 2 | 17806 | 34281 | 10 | 26 | 10 | 140506608 | 137395312 |
| Reduced idle minima 2 | 18024 | 35140 | 2 | 18 | 2 | 242563600 | 136858440 |

Measured improvement: eight fewer live threads, eight fewer HTTP workers, eight fewer open DB connections at readiness. Minimum idle=1 does not guarantee exactly one total connection; startup activity expanded the pool to two. No startup-time improvement was demonstrated: the two-sample averages were 17.594 versus 17.761 seconds. Heap snapshots were actually higher in the reduced-idle samples and cannot be used to claim heap savings; GC scheduling and allocation timing were not normalized. No RSS savings or OOM resolution is claimed. Buffer reference release is justified by code lifetimes and functional regression coverage, not a measured production retained-heap reduction.

## Production follow-up and capacity recommendation

512 MB cannot currently be represented as a reliable capacity for HALO. The reported OOM demonstrates a real failure, and these modest reductions do not establish that it is fixed. Start controlled capacity testing with at least 1 GiB and materially more CPU, then measure realistic concurrent uploads, OCR/indexing, Mentor traffic and reporting; larger capacity may be required. A five-minute startup and 0.1 CPU allocation also require CPU investigation, not just heap tuning.

For a separately approved staging run, correlate startup timing/heap counters with Render RSS, CPU and restart events. If available, use a temporary diagnostic JDK/container for thread dumps and Native Memory Tracking; the current JRE image may not include jcmd. NMT itself has overhead and does not account for every third-party/native allocation. Do not enable it blindly on an already exhausted production instance. Do not collect or publish heap dumps without treating them as sensitive academic/session data.

Test native MySQL schema validation and query behavior in a disposable database; measure TLS/metadata latency before changing JDBC timeout or validation settings. Keep production data untouched. Verify idle-to-burst latency, concurrent transactions, attachment authorization, all role workflows, and readiness/shutdown before any rollout. Existing JVM flags, authenticated routes, CORS and Supabase configuration should remain unchanged unless a separately measured adjustment is approved.

## Final verification and changed files

- Complete backend suite with the reduced idle settings, isolated H2: **866 total, 854 passed, zero failures/errors, 12 skipped**. Skips comprise existing optional tests and the opt-in benchmark; four final benchmark samples were run separately and passed.
- Nested transaction expansion test passed; ten-material extraction/order/cache regression passed; numeric memory diagnostics and existing readiness/security tests passed.
- Backend compilation and production Spring Boot JAR build passed. Source configuration and Git whitespace checks passed.
- Two early benchmark measurement attempts failed because of test-only Tomcat executor API assumptions. The measurement code was corrected before the four reported samples; neither failure was treated as a successful sample.
- No production process, Aiven/Supabase data, credentials, schema, or deployment settings were modified. No commit or push was performed. Native cloud, Docker/RSS-limited testing and connected browser/load testing were not performed.

Files changed by this memory follow-up:

- `HALO/src/main/resources/application.properties`
- `HALO/src/main/java/com/ptc/halo/component/ModuleMaterialIndex.java`
- `HALO/src/main/java/com/ptc/halo/config/StartupTimingConfiguration.java`
- `HALO/src/test/java/com/ptc/halo/IdleConnectionPoolTest.java`
- `HALO/src/test/java/com/ptc/halo/ModuleScopedMentorTest.java`
- `HALO/src/test/java/com/ptc/halo/StartupBootstrapBenchmarkTest.java`
- `HALO/src/test/java/com/ptc/halo/StartupTimingTest.java`
- `docs/render-memory-review.md`

Pre-existing changes to `docs/render-startup-diagnostics.md` and the unrelated `tatus --short` file were preserved.
