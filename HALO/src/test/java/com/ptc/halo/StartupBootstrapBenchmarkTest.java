package com.ptc.halo;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import com.ptc.halo.controller.HealthController;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in, fresh JVM per sample. Never load production application.properties. */
@EnabledIfSystemProperty(named = "halo.startup.benchmark", matches = "true")
class StartupBootstrapBenchmarkTest {
    @Test void measureValidatedStartup() throws Exception {
        String mode = System.getProperty("halo.startup.benchmark.mode", "default");
        assertThat(mode).isIn("default", "deferred");
        Path config = Path.of(System.getProperty("halo.startup.benchmark.config")).toAbsolutePath();
        Properties fixture = new Properties();
        try (var input = Files.newInputStream(config)) { fixture.load(input); }
        assertThat(fixture.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
        assertThat(fixture.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
        assertThat(fixture.getProperty("spring.sql.init.schema-locations")).startsWith("file:");

        var args = new ArrayList<String>();
        // These explicit arguments override environment variables, including real service credentials.
        fixture.forEach((key, value) -> args.add("--" + key + "=" + value));
        args.add("--spring.config.location=" + config.toUri());
        args.add("--spring.profiles.active=isolated-startup-benchmark");
        args.add("--spring.datasource.username=sa");
        args.add("--spring.datasource.password=");
        args.add("--spring.jpa.hibernate.ddl-auto=validate");
        args.add("--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect");
        args.add("--spring.jpa.show-sql=false");
        boolean lowIdle = Boolean.getBoolean("halo.startup.benchmark.low-idle");
        args.add("--spring.datasource.hikari.minimum-idle=" + (lowIdle ? 1 : 10));
        args.add("--spring.datasource.hikari.maximum-pool-size=10");
        args.add("--server.tomcat.threads.min-spare=" + (lowIdle ? 2 : 10));
        args.add("--server.tomcat.threads.max=200");
        args.add("--spring.main.lazy-initialization=false");
        args.add("--spring.data.jpa.repositories.bootstrap-mode=" + mode);
        args.add("--halo.lesson-storage.provider=local");
        args.add("--spring.ai.google.genai.api-key=synthetic-benchmark-only");
        args.add("--spring.ai.google.genai.embedding.api-key=synthetic-benchmark-only");
        args.add("--spring.mail.host=127.0.0.1");
        args.add("--spring.mail.port=1");
        args.add("--jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        args.add("--SUPER_ADMIN_EMAIL=benchmark@example.test");
        args.add("--SUPER_ADMIN_PASSWORD=SyntheticBenchmark123!");
        args.add("--server.address=127.0.0.1");
        args.add("--server.port=0");
        args.add("--logging.level.root=WARN");
        args.add("--debug=false");
        args.add("--logging.level.org.springframework=WARN");
        args.add("--logging.level.org.hibernate=WARN");
        args.add("--halo.startup-timing.enabled=false");
        var application = new SpringApplication(HaloApplication.class);
        var bound = new AtomicLong();
        application.addListeners((ApplicationListener<WebServerInitializedEvent>) event -> bound.compareAndSet(0, System.nanoTime()));
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long cpu = os.getProcessCpuTime(), start = System.nanoTime();
        var overrides = new java.util.LinkedHashMap<String, String>();
        for (String argument : args) {
            int separator = argument.indexOf('=');
            overrides.put(argument.substring(0, separator), argument.substring(separator + 1));
        }
        String[] uniqueArgs = overrides.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
        try (var context = application.run(uniqueArgs)) {
            long ready = System.nanoTime(), usedCpu = os.getProcessCpuTime() - cpu;
            assertThat(context.getBean(ApplicationAvailability.class).getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
            assertThat(context.getBean(HealthController.class).health().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(bound.get()).isGreaterThan(start);
            var memory = ManagementFactory.getMemoryMXBean();
            var pool = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
            assertThat(pool.getMinimumIdle()).isEqualTo(lowIdle ? 1 : 10);
            var web = (org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context;
            var tomcat = (org.springframework.boot.web.embedded.tomcat.TomcatWebServer) web.getWebServer();
            var protocol = (org.apache.coyote.AbstractProtocol<?>) tomcat.getTomcat().getConnector().getProtocolHandler();
            assertThat(protocol.getMinSpareThreads()).isEqualTo(lowIdle ? 2 : 10);
            assertThat(protocol.getMaxThreads()).isEqualTo(200);
            System.out.printf("IDLE_SAMPLE low_idle=%s db_connections=%d live_threads=%d http_workers=%d%n",
                    lowIdle, pool.getHikariPoolMXBean().getTotalConnections(),
                    ManagementFactory.getThreadMXBean().getThreadCount(),
                    ((org.apache.tomcat.util.threads.ThreadPoolExecutor) protocol.getExecutor()).getPoolSize());
            System.out.printf("BOOTSTRAP_SAMPLE mode=%s ready_ms=%d bound_ms=%d cpu_ms=%d heap_used_bytes=%d nonheap_used_bytes=%d%n",
                    mode, (ready-start)/1_000_000, (bound.get()-start)/1_000_000, usedCpu/1_000_000,
                    memory.getHeapMemoryUsage().getUsed(), memory.getNonHeapMemoryUsage().getUsed());
        }
    }
}
