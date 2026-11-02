package com.ptc.halo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoggingBridgeTest {
    @Test void oneSpringCommonsLoggingBridgeSuppliesPdfBoxLogging() throws Exception {
        var implementations = java.util.Collections.list(getClass().getClassLoader()
                .getResources("org/apache/commons/logging/LogFactory.class"));
        assertThat(implementations).hasSize(1);
        assertThat(implementations.get(0).toString()).contains("spring-jcl");
        assertThat(org.apache.commons.logging.LogFactory.getLog(org.apache.pdfbox.Loader.class)).isNotNull();
    }
}
