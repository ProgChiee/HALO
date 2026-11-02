package com.ptc.halo.config;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;

/** Opt-in timing only: never logs queries, bean properties, exceptions or credentials. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "halo.startup-timing.enabled", havingValue = "true")
public class StartupTimingConfiguration {
    @Bean
    static StartupTiming startupTiming() {
        return new StartupTiming();
    }

    public static final class StartupTiming implements BeanPostProcessor, ApplicationListener<ApplicationEvent> {
        private static final Logger log = LoggerFactory.getLogger(StartupTiming.class);
        private final Map<Object, Long> starts = Collections.synchronizedMap(new IdentityHashMap<>());

        @Override
        public Object postProcessBeforeInitialization(Object bean, String name) {
            if (bean instanceof RepositoryFactoryBeanSupport || bean instanceof AbstractEntityManagerFactoryBean) {
                starts.put(bean, System.nanoTime());
                log.info("startup_timing phase=bean_init_start bean={}", name);
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
            Long start = starts.remove(bean);
            if (start != null) log.info("startup_timing phase=bean_init_end bean={} elapsed_ms={}", name,
                    (System.nanoTime() - start) / 1_000_000);
            return bean;
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof ApplicationStartedEvent started)
                log.info("startup_timing phase=context_started elapsed_ms={}", started.getTimeTaken().toMillis());
            else if (event instanceof ApplicationReadyEvent ready)
                log.info("startup_timing phase=ready elapsed_ms={}", ready.getTimeTaken().toMillis());
            else if (event instanceof ApplicationFailedEvent)
                log.info("startup_timing phase=failed");
        }
    }
}
