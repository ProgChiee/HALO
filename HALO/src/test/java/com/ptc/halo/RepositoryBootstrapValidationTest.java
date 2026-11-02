package com.ptc.halo;

import com.ptc.startupprobe.StartupProbe;
import com.ptc.startupprobe.BrokenStartupRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.config.BootstrapMode;
import static org.assertj.core.api.Assertions.assertThat;

class RepositoryBootstrapValidationTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = StartupProbe.class)
    @EnableJpaRepositories(basePackageClasses = BrokenStartupRepository.class, bootstrapMode = BootstrapMode.DEFAULT)
    static class DefaultConfiguration {}

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = StartupProbe.class)
    @EnableJpaRepositories(basePackageClasses = BrokenStartupRepository.class, bootstrapMode = BootstrapMode.DEFERRED)
    static class DeferredConfiguration {}

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidQueriesFailContextRefreshInBothModes(boolean deferred) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                .withUserConfiguration(deferred ? DeferredConfiguration.class : DefaultConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:bootstrap" + java.util.UUID.randomUUID() + ";INIT=CREATE TABLE IF NOT EXISTS startup_probe (id BIGINT PRIMARY KEY)",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa", "spring.datasource.password=",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("nonexistentProperty");
                });
    }
}
