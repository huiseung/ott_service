CREATE TABLE IF NOT EXISTS analytics.qoe_events
(
    event_id UUID, session_key FixedString(64), sequence UInt64,
    occurred_at DateTime64(3, 'UTC'), content_id Int64, video_id Int64,
    event_type LowCardinality(String), watch_ms UInt64,
    startup_ms Nullable(UInt64), buffer_ms UInt64, fatal UInt8,
    error_source LowCardinality(String), error_code LowCardinality(String)
)
ENGINE = ReplacingMergeTree ORDER BY event_id;

CREATE VIEW IF NOT EXISTS analytics.qoe_events_current AS
SELECT * FROM analytics.qoe_events FINAL
ORDER BY event_id LIMIT 1 BY session_key, sequence;

CREATE VIEW IF NOT EXISTS analytics.qoe_sessions AS
SELECT session_key, content_id, video_id,
    min(occurred_at) AS first_seen_at,
    minIf(occurred_at, event_type = 'PLAYBACK_SESSION_STARTED') AS started_at,
    countIf(event_type = 'PLAYBACK_SESSION_STARTED') > 0 AS has_start,
    countIf(event_type = 'PLAY') > 0 AS has_play,
    argMin(startup_ms, sequence) AS startup_ms,
    sum(watch_ms) AS watch_ms,
    countIf(event_type = 'BUFFER_STARTED') AS buffer_count,
    countIf(event_type = 'BUFFER_ENDED') AS closed_buffer_count,
    sum(buffer_ms) AS buffer_ms,
    countIf(event_type = 'PLAYBACK_ERROR') AS error_count,
    max(fatal) AS has_fatal_error,
    countIf(event_type = 'PLAYBACK_SESSION_ENDED') > 0 AS has_end
FROM analytics.qoe_events_current
GROUP BY session_key, content_id, video_id;

-- UTC session-start cohorts; orphan events are visible in observed_sessions only.
CREATE VIEW IF NOT EXISTS analytics.content_daily_qoe AS
SELECT toDate32(if(has_start, started_at, first_seen_at)) AS cohort_date, content_id,
    count() AS observed_sessions,
    countIf(has_start) AS started_sessions,
    countIf(has_start AND has_play) AS playing_sessions,
    countIf(has_start AND startup_ms IS NOT NULL) AS startup_samples,
    avgOrNullIf(startup_ms, has_start) AS average_startup_ms,
    quantileOrNullIf(0.95)(startup_ms, has_start) AS p95_startup_ms,
    sumIf(q.watch_ms, has_start) AS watch_ms,
    sumIf(q.buffer_ms, has_start) AS buffer_ms,
    sumIf(q.buffer_count, has_start) AS buffer_count,
    sumIf(q.closed_buffer_count, has_start) AS closed_buffer_count,
    countIf(has_start AND q.buffer_count > q.closed_buffer_count) AS open_buffer_sessions,
    buffer_ms / nullIf(buffer_ms + watch_ms, 0) AS rebuffer_ratio,
    countIf(has_start AND has_play AND q.buffer_count > 0) / nullIf(playing_sessions, 0) AS rebuffer_session_rate,
    countIf(has_start AND error_count > 0) AS error_sessions,
    error_sessions / nullIf(started_sessions, 0) AS error_session_rate,
    countIf(has_start AND has_fatal_error) AS fatal_error_sessions,
    fatal_error_sessions / nullIf(started_sessions, 0) AS fatal_error_session_rate,
    countIf(has_start AND NOT has_play AND has_fatal_error) AS failed_before_play_sessions
FROM analytics.qoe_sessions AS q
GROUP BY cohort_date, content_id;

CREATE VIEW IF NOT EXISTS analytics.qoe_error_breakdown AS
SELECT toDate32(occurred_at) AS event_date, content_id, error_source, error_code, fatal,
    count() AS error_events, uniqExact(session_key) AS affected_sessions
FROM analytics.qoe_events_current
WHERE event_type = 'PLAYBACK_ERROR'
GROUP BY event_date, content_id, error_source, error_code, fatal;
