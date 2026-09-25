CREATE TABLE IF NOT EXISTS analytics.playback_observations
(
    event_id UUID, session_key FixedString(64), sequence UInt64,
    occurred_at DateTime64(3, 'UTC'), content_id Int64, video_id Int64, viewer_id String,
    duration_ms UInt64, watch_ms UInt64, interval_start_ms UInt64, interval_end_ms UInt64,
    is_start UInt8, episode_id Int64, next_episode_id Int64, catalog_enriched UInt8
)
ENGINE = ReplacingMergeTree ORDER BY event_id;

-- Remove transport retries first, then duplicate session sequence numbers.
-- Conflicting facts for the same identity are unsupported; lowest UUID is the deterministic winner.
CREATE VIEW IF NOT EXISTS analytics.playback_observations_current AS
SELECT * FROM analytics.playback_observations FINAL
ORDER BY event_id LIMIT 1 BY session_key, sequence;

CREATE VIEW IF NOT EXISTS analytics.playback_sessions AS
SELECT session_key, content_id, video_id, viewer_id,
    min(occurred_at) AS first_seen_at,
    minIf(occurred_at, is_start = 1) AS started_at,
    max(is_start) AS has_start,
    max(duration_ms) AS duration_ms,
    sum(watch_ms) AS watch_ms,
    intervalLengthSum(interval_start_ms, interval_end_ms) AS covered_ms,
    groupArray((interval_start_ms, interval_end_ms)) AS intervals,
    argMax(episode_id, sequence) AS episode_id,
    argMax(next_episode_id, sequence) AS next_episode_id
FROM analytics.playback_observations_current
GROUP BY session_key, content_id, video_id, viewer_id;

-- UTC session-start cohort. Entire session watch time belongs to its cohort, including midnight crossings.
CREATE VIEW IF NOT EXISTS analytics.content_daily_engagement AS
SELECT toDate32(if(has_start = 1, started_at, first_seen_at)) AS cohort_date, content_id,
    count() AS observed_sessions,
    countIf(has_start = 1) AS started_sessions,
    uniqExact(viewer_id) AS observed_viewers,
    sum(watch_ms) / 3600000.0 AS watch_hours,
    avg(watch_ms) / 1000.0 AS average_watch_seconds,
    countIf(has_start = 1 AND duration_ms > 0) AS completion_eligible_sessions,
    countIf(has_start = 1 AND duration_ms > 0 AND covered_ms / nullIf(duration_ms, 0) >= 0.9) AS completed_sessions,
    completed_sessions / nullIf(completion_eligible_sessions, 0) AS completion_rate
FROM analytics.playback_sessions
GROUP BY cohort_date, content_id;

CREATE VIEW IF NOT EXISTS analytics.viewer_video_watch AS
SELECT viewer_id, content_id, video_id, sum(watch_ms) AS watch_ms,
    max(duration_ms) AS duration_ms,
    intervalLengthSum(interval_start_ms, interval_end_ms) AS covered_ms,
    least(1.0, covered_ms / nullIf(duration_ms, 0)) AS watched_fraction
FROM analytics.playback_observations_current
GROUP BY viewer_id, content_id, video_id;

-- Fixed 10% buckets. A session counts once in a bucket if any watched interval overlaps it.
-- A zero-duration/unstarted session is excluded, not treated as 100% completed.
CREATE VIEW IF NOT EXISTS analytics.content_retention AS
SELECT toDate32(started_at) AS cohort_date, content_id, bucket * 10 AS bucket_start_percent,
    count() AS eligible_sessions,
    countIf(arrayExists(span -> span.2 > duration_ms * bucket / 10.0
        AND span.1 < duration_ms * (bucket + 1) / 10.0 AND span.2 > span.1, intervals)) AS watched_sessions,
    watched_sessions / nullIf(eligible_sessions, 0) AS retention_rate
FROM analytics.playback_sessions
ARRAY JOIN range(10) AS bucket
WHERE has_start = 1 AND duration_ms > 0
GROUP BY cohort_date, content_id, bucket;

-- User/episode cohort starts at the user's first observed started session for that episode.
CREATE VIEW IF NOT EXISTS analytics.episode_viewers AS
SELECT content_id, episode_id, viewer_id, min(started_at) AS first_started_at,
    argMin(next_episode_id, started_at) AS next_episode_id
FROM analytics.playback_sessions
WHERE has_start = 1 AND episode_id > 0 AND watch_ms > 0
GROUP BY content_id, episode_id, viewer_id;

CREATE VIEW IF NOT EXISTS analytics.episode_conversion AS
SELECT toDate32(a.first_started_at) AS cohort_date, a.content_id AS content_id,
    a.episode_id AS episode_id, a.next_episode_id AS next_episode_id,
    uniqExact(a.viewer_id) AS eligible_viewers,
    uniqExactIf(a.viewer_id, b.has_start = 1 AND b.watch_ms > 0
        AND b.started_at > a.first_started_at
        AND b.started_at <= a.first_started_at + INTERVAL 7 DAY) AS converted_viewers,
    converted_viewers / nullIf(eligible_viewers, 0) AS conversion_rate,
    max(a.first_started_at) + INTERVAL 7 DAY <= now64(3, 'UTC') AS cohort_mature
FROM analytics.episode_viewers AS a
LEFT JOIN analytics.playback_sessions AS b
    ON a.content_id = b.content_id AND a.viewer_id = b.viewer_id AND a.next_episode_id = b.episode_id
WHERE a.next_episode_id > 0
GROUP BY cohort_date, a.content_id, a.episode_id, a.next_episode_id;
