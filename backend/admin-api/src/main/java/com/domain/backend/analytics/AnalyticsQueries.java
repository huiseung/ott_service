package com.domain.backend.analytics;

/** Fixed, parameterized SELECTs only. Rates are recomputed across the selected cohort range. */
final class AnalyticsQueries {
    static final String REACH = """
        SELECT countIf(event_type='PLAYBACK_SESSION_STARTED') AS playStarts,
          uniqExactIf(viewer_id,event_type='PLAYBACK_SESSION_STARTED') AS uniqueViewers,
          countIf(event_type='CONTENT_IMPRESSION') AS impressions,
          countIf(event_type='CONTENT_CLICK') AS clicks,
          countIf(event_type='CONTENT_DETAIL_VIEW') AS detailViews,
          countIf(event_type='SUBSCRIPTION_CTA_CLICK') AS ctaClicks,
          toString(maxOrNull(occurred_at)) AS latestEventAt
        FROM analytics.content_events_current
        WHERE content_id={content:Int64} AND toDate32(occurred_at) BETWEEN {from:Date} AND {to:Date}
        """;
    static final String ENGAGEMENT = """
        SELECT count() AS sessions, sum(watch_ms)/3600000.0 AS watchHours,
          avgOrNull(watch_ms)/1000.0 AS averageWatchSeconds,
          countIf(has_start=1 AND duration_ms>0) AS eligibleSessions,
          countIf(has_start=1 AND duration_ms>0 AND covered_ms/nullIf(duration_ms,0)>=0.9) AS completedSessions,
          completedSessions/nullIf(eligibleSessions,0) AS completionRate
        FROM analytics.playback_sessions
        WHERE content_id={content:Int64}
          AND toDate32(if(has_start=1,started_at,first_seen_at)) BETWEEN {from:Date} AND {to:Date}
        """;
    static final String ACQUISITION = """
        SELECT f.*, a.* FROM
          (SELECT sum(cta_clicks) AS ctaClicks, sum(checkout_ctas) AS checkoutCtas,
            sum(converted_ctas) AS convertedCtas, convertedCtas/nullIf(ctaClicks,0) AS conversionRate
           FROM analytics.content_subscription_funnel
           WHERE content_id={content:Int64} AND cta_date BETWEEN {from:Date} AND {to:Date}) AS f
        CROSS JOIN
          (SELECT uniqExact(subscription_id) AS attributedSubscriptions,
            uniqExactIf(subscription_id,activation_source='LOCAL_TEST') AS testSubscriptions
           FROM analytics.subscription_acquisition_attribution
           WHERE attributed_content_id={content:Int64} AND toDate32(activated_at) BETWEEN {from:Date} AND {to:Date}) AS a
        """;
    static final String QUALITY = """
        SELECT count() AS sessions, countIf(startup_ms IS NOT NULL) AS startupSamples,
          avgOrNull(startup_ms) AS averageStartupMs, quantileOrNull(0.95)(startup_ms) AS p95StartupMs,
          sum(buffer_count) AS bufferCount,
          countIf(buffer_count>closed_buffer_count) AS openBufferSessions,
          sum(buffer_ms)/nullIf(sum(buffer_ms)+sum(watch_ms),0) AS rebufferRatio,
          countIf(error_count>0) AS errorSessions, errorSessions/nullIf(sessions,0) AS errorRate,
          countIf(has_fatal_error=1) AS fatalErrorSessions, fatalErrorSessions/nullIf(sessions,0) AS fatalErrorRate
        FROM analytics.qoe_sessions
        WHERE content_id={content:Int64} AND has_start=1 AND toDate32(started_at) BETWEEN {from:Date} AND {to:Date}
        """;
    static final String RETENTION = """
        SELECT bucket_start_percent AS bucket, sum(eligible_sessions) AS eligibleSessions,
          sum(watched_sessions) AS watchedSessions, watchedSessions/nullIf(eligibleSessions,0) AS rate
        FROM analytics.content_retention
        WHERE content_id={content:Int64} AND cohort_date BETWEEN {from:Date} AND {to:Date}
        GROUP BY bucket ORDER BY bucket
        """;
    static final String EPISODES = """
        SELECT episode_id AS episodeId, next_episode_id AS nextEpisodeId,
          sum(eligible_viewers) AS eligibleViewers, sum(converted_viewers) AS convertedViewers,
          convertedViewers/nullIf(eligibleViewers,0) AS rate, min(cohort_mature) AS mature
        FROM analytics.episode_conversion
        WHERE content_id={content:Int64} AND cohort_date BETWEEN {from:Date} AND {to:Date}
        GROUP BY episodeId,nextEpisodeId ORDER BY episodeId,nextEpisodeId LIMIT 101
        """;
    static final String DAILY = """
        SELECT toString(event_date) AS date, play_starts AS playStarts, unique_viewers AS uniqueViewers,
          impressions, clicks
        FROM analytics.content_daily_reach
        WHERE content_id={content:Int64} AND event_date BETWEEN {from:Date} AND {to:Date}
        ORDER BY event_date
        """;
    private AnalyticsQueries() {}
}
