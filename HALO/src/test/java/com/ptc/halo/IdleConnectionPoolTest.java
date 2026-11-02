package com.ptc.halo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class IdleConnectionPoolTest {
    @Test void reducedIdleFloorStillAllowsNestedHistoryRetentionTransactions() {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:idle" + java.util.UUID.randomUUID());
        config.setUsername("sa");
        config.setPassword("");
        config.setMinimumIdle(1);
        try (var pool = new HikariDataSource(config)) {
            assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
            var jdbc = new JdbcTemplate(pool);
            var manager = new DataSourceTransactionManager(pool);
            var outer = new TransactionTemplate(manager);
            var inner = new TransactionTemplate(manager);
            inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            outer.executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
                inner.executeWithoutResult(nested -> {
                    assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
                    assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(2);
                });
            });
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }
}
