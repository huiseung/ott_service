package com.domain.backend.subscription;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubscriptionOutboxStore {
    private final JdbcTemplate jdbc;
    public SubscriptionOutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Delivery(long id, String aggregateId, String payload, int attempts, String claimToken) {}

    @Transactional
    public List<Delivery> claim() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                update subscription_outbox set status='FAILED',claim_token=null,last_error_code='LEASE_EXHAUSTED'
                where status='PROCESSING' and attempts>=8 and available_at<=?
                """, now);
        String token = UUID.randomUUID().toString();
        // One outstanding event per subscription: later changes cannot overtake a failed predecessor.
        var rows = jdbc.query("""
                select o.id,o.aggregate_id,o.payload,o.attempts from subscription_outbox o
                where o.status in ('PENDING','PROCESSING') and o.available_at<=? and o.attempts<8
                  and not exists(select 1 from subscription_outbox older
                    where older.aggregate_id=o.aggregate_id and older.id<o.id and older.status<>'SENT')
                order by o.id limit 20 for update skip locked
                """, (rs, n) -> new Delivery(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4) + 1, token), now);
        for (var row : rows) jdbc.update("""
                update subscription_outbox set status='PROCESSING',attempts=attempts+1,claim_token=?,available_at=? where id=?
                """, token, Timestamp.from(Instant.now().plusSeconds(120)), row.id());
        return rows;
    }

    @Transactional
    public void delivered(Delivery row) {
        jdbc.update("""
                update subscription_outbox set status='SENT',sent_at=?,claim_token=null,last_error_code=null
                where id=? and status='PROCESSING' and claim_token=?
                """, Timestamp.from(Instant.now()), row.id(), row.claimToken());
    }

    @Transactional
    public void failed(Delivery row, String errorCode) {
        jdbc.update("""
                update subscription_outbox set status=?,available_at=?,claim_token=null,last_error_code=?
                where id=? and status='PROCESSING' and claim_token=?
                """, row.attempts() >= 8 ? "FAILED" : "PENDING",
                Timestamp.from(Instant.now().plusSeconds(Math.min(300, 1L << row.attempts()))),
                errorCode, row.id(), row.claimToken());
    }
}
