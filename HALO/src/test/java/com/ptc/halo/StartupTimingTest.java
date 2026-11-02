package com.ptc.halo;

import com.ptc.halo.config.StartupTimingConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class StartupTimingTest {
    @Test void diagnosticsAreOptIn() {
        var runner = new ApplicationContextRunner().withUserConfiguration(StartupTimingConfiguration.class);
        runner.run(context -> assertThat(context).doesNotHaveBean(StartupTimingConfiguration.StartupTiming.class));
        runner.withPropertyValues("halo.startup-timing.enabled=true").run(context ->
                assertThat(context).hasSingleBean(StartupTimingConfiguration.StartupTiming.class));
    }

    @Test void timingsPreserveBeansAndNeverInspectSensitiveProperties(CapturedOutput output) {
        var timings = new StartupTimingConfiguration.StartupTiming();
        var repository = mock(RepositoryFactoryBeanSupport.class);
        var emf = mock(LocalContainerEntityManagerFactoryBean.class);
        for (Object bean : new Object[]{repository, emf}) {
            assertThat(timings.postProcessBeforeInitialization(bean, "safeBean")).isSameAs(bean);
            assertThat(timings.postProcessAfterInitialization(bean, "safeBean")).isSameAs(bean);
        }
        verifyNoInteractions(repository, emf);
        assertThat(output.getOut()).contains("phase=bean_init_start bean=safeBean", "phase=bean_init_end bean=safeBean elapsed_ms=");
    }

    @Test void ordinaryBeansDoNotProduceTimingNoise(CapturedOutput output) {
        var timings = new StartupTimingConfiguration.StartupTiming();
        Object ordinary = new Object();
        timings.postProcessBeforeInitialization(ordinary, "ordinary");
        timings.postProcessAfterInitialization(ordinary, "ordinary");
        assertThat(output.getOut()).doesNotContain("startup_timing");
    }
}
