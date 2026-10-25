package com.ptc.halo;

import com.ptc.halo.service.*;
import java.sql.*;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JdbcTemplate mapping with the DATETIME getObject behavior observed on Connector/J + MariaDB. */
class PasswordResetThrottleJdbcTest {
    private final LocalDateTime now=LocalDateTime.of(2026,10,23,12,0,0,123456000);
    private Connection connection;
    private PlatformTransactionManager manager;
    private PasswordResetThrottle throttle;
    private int count;
    private LocalDateTime start, next;
    private boolean legacyTimestampObjects;

    @BeforeEach void setup() throws Exception {
        start=now; next=null; count=0;
        var source=mock(DataSource.class); connection=mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var databaseMetadata=mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(databaseMetadata);
        when(databaseMetadata.getDriverName()).thenReturn("MySQL Connector/J");
        when(databaseMetadata.getDatabaseProductName()).thenReturn("MariaDB");
        var statement=mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select current_timestamp")).thenAnswer(call -> {
            var result=mock(ResultSet.class); var metadata=mock(ResultSetMetaData.class);
            when(result.next()).thenReturn(true,false); when(result.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(1); when(result.getTimestamp(1)).thenReturn(Timestamp.valueOf(now));
            return result;
        });
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            var prepared=mock(PreparedStatement.class);
            when(prepared.getConnection()).thenReturn(connection);
            var parameters=mock(ParameterMetaData.class);
            when(prepared.getParameterMetaData()).thenReturn(parameters);
            when(parameters.getParameterType(anyInt())).thenReturn(Types.TIMESTAMP);
            when(prepared.executeUpdate()).thenReturn(1);
            when(prepared.executeQuery()).thenAnswer(query -> bucketResult());
            return prepared;
        });
        manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var policy=new PasswordResetProtection("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",5,600,60,3600,3,20,10,100);
        throttle=new PasswordResetThrottle(new JdbcTemplate(source),policy,manager);
    }

    private ResultSet bucketResult() throws Exception {
        var result=mock(ResultSet.class); var metadata=mock(ResultSetMetaData.class);
        when(result.next()).thenReturn(true,false); when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(3);
        String[] names={"window_started_at","event_count","next_allowed_at"};
        Object[] values={legacyTimestampObjects?Timestamp.valueOf(start):start,Integer.valueOf(count),
                next==null?null:legacyTimestampObjects?Timestamp.valueOf(next):next};
        for(int i=1;i<=3;i++) {
            when(metadata.getColumnLabel(i)).thenReturn(names[i-1]);
            when(metadata.getColumnName(i)).thenReturn(names[i-1]);
            when(result.getObject(i)).thenReturn(values[i-1]);
        }
        // Typed JDBC access converts DATETIME even when getObject returns LocalDateTime.
        when(result.getTimestamp("window_started_at")).thenReturn(Timestamp.valueOf(start));
        when(result.getTimestamp("next_allowed_at")).thenReturn(next==null?null:Timestamp.valueOf(next));
        when(result.getInt("event_count")).thenReturn(count);
        return result;
    }

    @Test void mariaDbDateTimeAdmissionWithNullCooldownDoesNotCastGetObject() {
        assertDoesNotThrow(() -> throttle.admit("student@example.test","192.0.2.1",false));
        verify(manager).commit(any()); verify(manager,never()).rollback(any());
    }
    @Test void mariaDbDateTimeCooldownStillRejectsAndCommitsAdmission() {
        next=now.plusSeconds(60);
        var error=assertThrows(PasswordResetException.class,()->throttle.admit("student@example.test","192.0.2.1",false));
        assertEquals(429,error.getStatus()); assertEquals(61,error.getRetryAfterSeconds());
        verify(manager).commit(any()); verify(manager,never()).rollback(any());
    }
    @Test void mariaDbIntegerCounterStillEnforcesVerificationLimit() {
        count=100;
        var error=assertThrows(PasswordResetException.class,()->throttle.admit("student@example.test","192.0.2.1",true));
        assertEquals(429,error.getStatus()); assertEquals(3601,error.getRetryAfterSeconds());
    }
    @Test void expiredWindowStillPermitsAdmission() {
        count=100;start=now.minusHours(2);next=now.minusSeconds(1);
        assertDoesNotThrow(()->throttle.admit("student@example.test","192.0.2.1",true));
    }
    @Test void timestampReturningDriversRemainSupported() {
        legacyTimestampObjects=true;
        assertDoesNotThrow(()->throttle.admit("student@example.test","192.0.2.1",false));
    }
}
