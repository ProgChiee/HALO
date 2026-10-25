# Password-reset security deployment

This migration has NOT been applied to live MariaDB. It was designed against the existing
`password_reset_otps` schema: unique `user_id`, `otp VARCHAR(255)`, and creation/expiry timestamps.
It adds one challenge counter and one shared InnoDB rate-limit table. No account/history data is removed.

IMPORTANT: The current checkout defaults Hibernate DDL to `update`. Do not start the modified backend
against live MariaDB before approval. Set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` for deployment;
otherwise startup could apply additive schema changes automatically, before the migration is reviewed.

## Deployment order (requires operator approval)

1. Back up the database; record the current application version and security configuration.
2. Stop ALL HALO backend instances / disable public recovery during the maintenance window.
   Do not do a rolling deployment: old instances accept plaintext codes and do not enforce these limits.
3. Review and run `password-reset-security.sql` ONCE against the intended database. DDL implicitly commits;
   inspect both tables if any step fails before resuming. Legacy plaintext OTPs are intentionally invalidated.
4. Generate at least 32 cryptographically random bytes, base64 encode them, and store them as
   `HALO_PASSWORD_RESET_PEPPER` in the deployment secret manager. Use the SAME stable value on ALL instances.
   Do not put it in Git, logs, frontend configuration, or the database. It must be independent of the JWT key.
   The application fails startup if this secret is absent/invalid. Changing it invalidates codes and changes
   bucket identifiers, so rotate only as a coordinated recovery maintenance operation, not on every restart.
5. Deploy the backend and frontend together with `spring.jpa.hibernate.ddl-auto=validate`;
   do not let Hibernate `update` substitute for the reviewed migration and legacy-code invalidation.
6. Confirm readiness and use an explicitly authorized test account for recovery acceptance testing.
   This implementation task did not send live mail or alter live accounts/challenges.

## Defaults / configuration

Properties use the `halo.password-reset.` prefix:

| Property | Default |
|---|---:|
| `max-attempts` | 5 failed guesses per challenge |
| `ttl-seconds` | 600 |
| `cooldown-seconds` | 60 between account code requests |
| `window-seconds` | 3600 |
| `request-account-limit` | 3 per window, initial and resend combined |
| `request-ip-limit` | 20 per window |
| `verification-account-limit` | 10 per window |
| `verification-ip-limit` | 100 per window |

Use identical limits on every instance. Rate admission commits separately from reset transactions,
including unsuccessful requests and email/provider failures. Both nonexistent and existing identifiers
consume the same limits. Resend keeps the current failure count and original expiry; locked challenges
cannot be replaced before expiry. Expired/new challenges do not reset account/IP buckets.
Successful reset deletes the challenge and increments the existing JWT token version transactionally.

Identifiers/IPs are stored only as purpose-separated HMAC keys. OTP storage is `v1:<random salt>:<HMAC>`
bound to the user ID. Legacy/plaintext values are never accepted. Codes exist in memory only for delivery.
Keep database/session timezones consistent across instances; database time drives challenge expiry/rate windows.

IP admission uses servlet `getRemoteAddr()`; this code does NOT trust X-Forwarded-For or other supplied headers.
Behind a proxy this may be the proxy IP, sharing its allowance. Configure trusted-proxy handling at the
deployment boundary only when direct origin access is restricted and client-supplied forwarding headers are
removed/replaced. Never enable arbitrary forwarded-header trust. Tune shared-network limits deliberately.

Request/resend responses are identical for unknown accounts and SMTP failures; 429 includes Retry-After
and `retryAfterSeconds`. Invalid, expired, reused and exhausted challenges use the same public 400 message.
SMTP delivery timing can still differ from an unknown account; this is not a constant-time recovery system.
Failed SMTP delivery still consumes admission, preventing repeated retries from bypassing email controls.

## Retention / rollback

Rate rows contain no raw addresses but require bounded maintenance. An operator may schedule the following
in small batches; NEVER remove rows within the configured window or with a future cooldown:

```sql
DELETE FROM password_reset_rate_limits
WHERE window_started_at < CURRENT_TIMESTAMP - INTERVAL 2 DAY
  AND (next_allowed_at IS NULL OR next_allowed_at < CURRENT_TIMESTAMP)
LIMIT 1000;
```

The maximum supported window is one day, so this retains every effective limit. Do not clear this table on
application restart/deployment. This maintenance SQL has NOT been executed against live data.

For rollback, first disable recovery endpoints and stop all instances. Restore the previous application
only with recovery disabled until a secure forward fix is available; old code would reintroduce the issue.
Keep the additive column/table and verifier values; do not restore plaintext OTP backups or active old codes.
No destructive down migration is provided. JWT/login/logout and historical account records are unchanged.
