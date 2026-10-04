package com.ptc.halo;

import com.ptc.halo.entity.ActivityLogEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.AdminDashboardService;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false"
}, showSql = false)
@Import(AdminDashboardService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminDashboardTransactionTest {
    @Autowired ActivityLogRepository logs;
    @Autowired UserRepository users;
    @Autowired AdminDashboardService service;

    @BeforeEach void clearFixtures() {
        logs.deleteAll();
        users.deleteAll();
    }

    @Test void existingLazyActorsMapOutsideAnyCallerTransaction() {
        var actor = new UserEntity();
        actor.setName("Admin Actor");
        actor.setEmail("actor@example.test");
        actor.setRole(Role.ADMIN);
        actor.setStatus(Status.ACTIVE);
        actor = users.saveAndFlush(actor);
        var start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 12; i++) {
            var entry = new ActivityLogEntity();
            entry.setUser(actor);
            entry.setActivityType(ActivityType.ACCOUNT);
            entry.setAction("Action " + i);
            entry.setCreatedAt(start.plusMinutes(i));
            logs.saveAndFlush(entry);
        }

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var detached = logs.findTop10ByOrderByCreatedAtDesc().get(0);
        assertFalse(Hibernate.isInitialized(detached.getUser()));
        assertThrows(LazyInitializationException.class, () -> detached.getUser().getName());

        var result = service.getRecentActivity();
        assertEquals(10, result.size());
        assertEquals("Action 11", result.get(0).getAction());
        assertEquals("Action 2", result.get(9).getAction());
        result.forEach(row -> {
            assertNotNull(row.getId());
            assertEquals("Admin Actor", row.getUserName());
            assertEquals("actor@example.test", row.getEmail());
            assertEquals(Role.ADMIN, row.getRole());
            assertEquals(ActivityType.ACCOUNT, row.getActivityType());
            assertNotNull(row.getCreatedAt());
        });
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test void emptyHistoryReturnsEmptyList() {
        assertTrue(service.getRecentActivity().isEmpty());
    }
}
