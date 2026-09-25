CREATE TABLE IF NOT EXISTS analytics.subscription_events
(
    event_id UUID, event_type LowCardinality(String), subscription_id UUID,
    checkout_id String, cta_event_id String, viewer_id String, content_id Int64,
    occurred_at DateTime64(3, 'UTC'), first_activation UInt8, activation_source LowCardinality(String)
)
ENGINE = ReplacingMergeTree ORDER BY event_id;

CREATE TABLE IF NOT EXISTS analytics.subscription_ctas
(
    event_id UUID, viewer_id String, content_id Int64, occurred_at DateTime64(3, 'UTC')
)
ENGINE = ReplacingMergeTree ORDER BY event_id;

CREATE VIEW IF NOT EXISTS analytics.subscription_events_current AS
SELECT * FROM analytics.subscription_events FINAL;
CREATE VIEW IF NOT EXISTS analytics.subscription_ctas_current AS
SELECT * FROM analytics.subscription_ctas FINAL;
CREATE VIEW IF NOT EXISTS analytics.subscription_checkouts AS
SELECT * FROM analytics.subscription_events_current WHERE event_type='CHECKOUT_STARTED';

-- Explicit checkout CTA, same authenticated viewer/content, at most 7 days before checkout.
-- Late delivery is resolved at query time. Missing/mismatched CTA remains unattributed (content=0).
CREATE VIEW IF NOT EXISTS analytics.subscription_acquisition_attribution AS
SELECT a.event_id AS activation_event_id, a.subscription_id AS subscription_id,
    a.viewer_id AS viewer_id, a.occurred_at AS activated_at, a.activation_source AS activation_source,
    if(c.content_id > 0 AND t.content_id=c.content_id AND t.viewer_id=c.viewer_id
        AND t.occurred_at<=c.occurred_at AND t.occurred_at>=c.occurred_at-INTERVAL 7 DAY
        AND a.occurred_at>=c.occurred_at AND a.occurred_at<=c.occurred_at+INTERVAL 30 MINUTE,
        c.content_id, toInt64(0)) AS attributed_content_id,
    if(attributed_content_id>0, toString(t.event_id), '') AS matched_cta_id
FROM analytics.subscription_events_current AS a
LEFT JOIN analytics.subscription_checkouts AS c
    ON a.checkout_id=c.checkout_id AND a.subscription_id=c.subscription_id AND a.viewer_id=c.viewer_id
LEFT JOIN analytics.subscription_ctas_current AS t
    ON c.cta_event_id=toString(t.event_id) AND c.viewer_id=t.viewer_id AND c.content_id=t.content_id
WHERE a.event_type='SUBSCRIPTION_ACTIVATED' AND a.first_activation=1;

CREATE VIEW IF NOT EXISTS analytics.content_daily_acquisition AS
SELECT toDate32(activated_at) AS activation_date, attributed_content_id AS content_id, activation_source,
    uniqExact(subscription_id) AS new_subscriptions
FROM analytics.subscription_acquisition_attribution
GROUP BY activation_date, content_id, activation_source;

-- CTA cohort; repeated checkout attempts or outbox retries cannot inflate the numerator.
CREATE VIEW IF NOT EXISTS analytics.content_subscription_funnel AS
SELECT toDate32(t.occurred_at) AS cta_date, t.content_id AS content_id,
    uniqExact(t.event_id) AS cta_clicks,
    uniqExactIf(t.event_id, c.content_id=t.content_id AND c.occurred_at>=t.occurred_at
        AND c.occurred_at<=t.occurred_at+INTERVAL 7 DAY) AS checkout_ctas,
    uniqExactIf(t.event_id, a.attributed_content_id=t.content_id) AS converted_ctas,
    converted_ctas / nullIf(cta_clicks, 0) AS conversion_rate
FROM analytics.subscription_ctas_current AS t
LEFT JOIN analytics.subscription_checkouts AS c
    ON toString(t.event_id)=c.cta_event_id AND t.viewer_id=c.viewer_id AND t.content_id=c.content_id
LEFT JOIN analytics.subscription_acquisition_attribution AS a
    ON toString(t.event_id)=a.matched_cta_id AND t.viewer_id=a.viewer_id
GROUP BY cta_date, t.content_id;
