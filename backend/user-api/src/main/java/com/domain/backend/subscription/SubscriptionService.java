package com.domain.backend.subscription;

import com.domain.backend.user.application.ContentEligibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionService {
    private final JdbcTemplate jdbc;
    private final SubscriptionOutbox outbox;
    private final ContentEligibility eligibility;
    public SubscriptionService(JdbcTemplate jdbc, SubscriptionOutbox outbox, ContentEligibility eligibility) {
        this.jdbc = jdbc; this.outbox = outbox; this.eligibility = eligibility;
    }
    public record Checkout(String checkoutId, String subscriptionId, Long contentId, String ctaEventId,
                           String status, Instant createdAt, Instant expiresAt) {}
    public record Membership(String subscriptionId, String status) {}

    @Transactional
    public Checkout checkout(long userId, UUID requestId, Long contentId, UUID ctaEventId) {
        lockUser(userId);
        String cta = ctaEventId == null ? null : ctaEventId.toString();
        var previous = jdbc.query("select * from subscription_checkouts where user_id=? and request_id=?",
                this::checkoutRow, userId, requestId.toString());
        if (!previous.isEmpty()) {
            var existing = previous.getFirst();
            if (!Objects.equals(existing.contentId(), contentId) || !Objects.equals(existing.ctaEventId(), cta))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request id reused with different checkout facts");
            return existing;
        }
        if (cta != null && contentId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CTA requires content");
        if (contentId != null) eligibility.requireContentAvailable(contentId);
        Instant now = Instant.now();
        var membership = membership(userId);
        if (membership.status().equals("ACTIVE")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription already active");
        String subscriptionId = membership.subscriptionId();
        if (subscriptionId == null) {
            subscriptionId = UUID.randomUUID().toString();
            jdbc.update("insert into user_subscriptions(user_id,subscription_id,status,created_at) values (?,?,'PENDING',?)",
                    userId, subscriptionId, Timestamp.from(now));
            outbox.record("SUBSCRIPTION_CREATED", subscriptionId, null, userId, null, null, false, now);
        }
        var checkout = new Checkout(UUID.randomUUID().toString(), subscriptionId, contentId, cta, "CREATED", now, now.plus(Duration.ofMinutes(30)));
        jdbc.update("""
                insert into subscription_checkouts(checkout_id,request_id,user_id,subscription_id,content_id,cta_event_id,status,created_at,expires_at)
                values (?,?,?,?,?,?,'CREATED',?,?)
                """, checkout.checkoutId(), requestId.toString(), userId, subscriptionId, contentId, cta,
                Timestamp.from(now), Timestamp.from(checkout.expiresAt()));
        outbox.record("CHECKOUT_STARTED", subscriptionId, checkout.checkoutId(), userId, contentId, cta, false, now);
        return checkout;
    }

    /** Called only by the explicitly enabled local test adapter, never by analytics collection. */
    @Transactional
    public Membership activateLocal(long userId, UUID checkoutId) {
        lockUser(userId);
        var rows = jdbc.query("select * from subscription_checkouts where checkout_id=? and user_id=?", this::checkoutRow, checkoutId.toString(), userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout not found");
        var checkout = rows.getFirst();
        if (checkout.status().equals("ACTIVATED")) return membership(userId);
        Instant now = Instant.now();
        if (!checkout.expiresAt().isAfter(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout expired");
        if (membership(userId).status().equals("ACTIVE")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription already active");
        boolean first = Boolean.TRUE.equals(jdbc.queryForObject("select first_activated_at is null from user_subscriptions where user_id=?", Boolean.class, userId));
        jdbc.update("update user_subscriptions set status='ACTIVE',first_activated_at=coalesce(first_activated_at,?),activated_at=?,cancelled_at=null where user_id=?",
                Timestamp.from(now), Timestamp.from(now), userId);
        jdbc.update("update subscription_checkouts set status='ACTIVATED',activated_at=? where checkout_id=?", Timestamp.from(now), checkout.checkoutId());
        outbox.record("SUBSCRIPTION_ACTIVATED", checkout.subscriptionId(), checkout.checkoutId(), userId,
                checkout.contentId(), checkout.ctaEventId(), first, now);
        return membership(userId);
    }

    @Transactional
    public Membership cancel(long userId) {
        lockUser(userId);
        var membership = membership(userId);
        if (!membership.status().equals("ACTIVE")) return membership;
        Instant now = Instant.now();
        jdbc.update("update user_subscriptions set status='CANCELLED',cancelled_at=? where user_id=?", Timestamp.from(now), userId);
        outbox.record("SUBSCRIPTION_CANCELLED", membership.subscriptionId(), null, userId, null, null, false, now);
        return membership(userId);
    }

    @Transactional(readOnly = true)
    public Membership membership(long userId) {
        var rows = jdbc.query("select subscription_id,status from user_subscriptions where user_id=?",
                (rs, n) -> new Membership(rs.getString(1), rs.getString(2)), userId);
        return rows.isEmpty() ? new Membership(null, "NONE") : rows.getFirst();
    }
    private void lockUser(long userId) {
        if (jdbc.queryForList("select id from users where id=? and enabled=true for update", Long.class, userId).isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not available");
    }
    private Checkout checkoutRow(ResultSet rs, int row) throws SQLException {
        return new Checkout(rs.getString("checkout_id"), rs.getString("subscription_id"), rs.getObject("content_id", Long.class),
                rs.getString("cta_event_id"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant());
    }
}
