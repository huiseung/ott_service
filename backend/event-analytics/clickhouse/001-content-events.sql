CREATE DATABASE IF NOT EXISTS analytics;

-- Immutable eventId is the retry identity. No date partition: a retry must always share the same key.
CREATE TABLE IF NOT EXISTS analytics.content_events
(
    domain LowCardinality(String),
    event_id UUID,
    event_type LowCardinality(String),
    occurred_at DateTime64(3, 'UTC'),
    content_id Int64,
    viewer_id String
)
ENGINE = ReplacingMergeTree
ORDER BY (domain, event_id);

-- All consumers use this view (or FINAL), never aggregate the physical retry rows directly.
CREATE VIEW IF NOT EXISTS analytics.content_events_current AS
SELECT * FROM analytics.content_events FINAL;

CREATE VIEW IF NOT EXISTS analytics.content_daily_reach AS
SELECT toDate32(occurred_at) AS event_date, content_id,
    countIf(event_type = 'PLAYBACK_SESSION_STARTED') AS play_starts,
    uniqExactIf(viewer_id, event_type = 'PLAYBACK_SESSION_STARTED') AS unique_viewers,
    countIf(event_type = 'CONTENT_IMPRESSION') AS impressions,
    countIf(event_type = 'CONTENT_CLICK') AS clicks,
    countIf(event_type = 'CONTENT_DETAIL_VIEW') AS detail_views,
    countIf(event_type = 'SUBSCRIPTION_CTA_CLICK') AS subscription_cta_clicks
FROM analytics.content_events_current
GROUP BY event_date, content_id;
