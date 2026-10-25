package com.ptc.halo.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** All instances share durable admission counters. A rejected/rolled-back reset cannot refund admission. */
@Service
public class PasswordResetThrottle {
    private final JdbcTemplate jdbc;
    private final PasswordResetProtection policy;
    private final TransactionTemplate transaction;
    public PasswordResetThrottle(JdbcTemplate jdbc, PasswordResetProtection policy, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.policy=policy; transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void admit(String email, String remoteAddress, boolean verification) {
        long retry=transaction.execute(status -> {
            String operation=verification?"verify":"request";
            LocalDateTime now=databaseNow();
            // IP first bounds database growth from arbitrary identifiers; never trust forwarded request headers.
            long ipRetry=consume(policy.bucket(operation+":ip",remoteAddress),now,
                    verification?policy.verificationIpLimit:policy.requestIpLimit,0);
            if (ipRetry>0) return ipRetry;
            return consume(policy.bucket(operation+":account",email),now,
                    verification?policy.verificationAccountLimit:policy.requestAccountLimit,
                    verification?0:policy.cooldownSeconds);
        });
        // Throw AFTER commit so denied requests never roll back counters already consumed.
        if (retry>0) throw new PasswordResetException(429,retry);
    }
    public LocalDateTime databaseNow() { return jdbc.queryForObject("select current_timestamp",Timestamp.class).toLocalDateTime(); }
    private record Bucket(LocalDateTime start, int count, LocalDateTime next) {}
    private long consume(String key, LocalDateTime now, int limit, int cooldown) {
        jdbc.update("insert into password_reset_rate_limits(bucket_key,window_started_at,event_count) values(?,?,0) on duplicate key update bucket_key=bucket_key",key,now);
        // getObject(DATETIME) can return LocalDateTime on Connector/J and Timestamp on H2.
        // Typed JDBC getters perform the conversion without relying on driver-specific map values.
        var row=jdbc.queryForObject("select window_started_at,event_count,next_allowed_at from password_reset_rate_limits where bucket_key=? for update",
                (rs,index) -> {
                    Timestamp next=rs.getTimestamp("next_allowed_at");
                    return new Bucket(rs.getTimestamp("window_started_at").toLocalDateTime(),
                            rs.getInt("event_count"),next==null?null:next.toLocalDateTime());
                },key);
        LocalDateTime start=row.start();
        int count=row.count();
        LocalDateTime next=row.next();
        if (!now.isBefore(start.plusSeconds(policy.windowSeconds))) { start=now; count=0; next=null; }
        LocalDateTime until=count>=limit?start.plusSeconds(policy.windowSeconds):null;
        if (next!=null && next.isAfter(now) && (until==null || next.isAfter(until))) until=next;
        if (until!=null && until.isAfter(now)) return Math.max(1,Duration.between(now,until).toSeconds()+1);
        jdbc.update("update password_reset_rate_limits set window_started_at=?,event_count=?,next_allowed_at=? where bucket_key=?",
                start,count+1,cooldown==0?null:now.plusSeconds(cooldown),key);
        return 0;
    }
}
