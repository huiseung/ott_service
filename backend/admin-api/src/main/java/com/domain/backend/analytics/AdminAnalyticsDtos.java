package com.domain.backend.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminAnalyticsDtos {
    public record Reach(long playStarts, long uniqueViewers, long impressions, long clicks,
                        long detailViews, long ctaClicks, String latestEventAt) {}
    public record Engagement(long sessions, double watchHours, Double averageWatchSeconds,
                             long eligibleSessions, long completedSessions, Double completionRate) {}
    public record Acquisition(long ctaClicks, long checkoutCtas, long convertedCtas, Double conversionRate,
                              long attributedSubscriptions, long testSubscriptions) {}
    public record Quality(long sessions, long startupSamples, Double averageStartupMs, Double p95StartupMs,
                          long bufferCount, long openBufferSessions, Double rebufferRatio,
                          long errorSessions, Double errorRate, long fatalErrorSessions, Double fatalErrorRate) {}
    public record Retention(int bucket, long eligibleSessions, long watchedSessions, Double rate) {}
    public record Episode(long episodeId, long nextEpisodeId, long eligibleViewers, long convertedViewers,
                          Double rate, int mature) {}
    public record Daily(String date, long playStarts, long uniqueViewers, long impressions, long clicks) {}
    public record Dashboard(long contentId, LocalDate from, LocalDate to, Instant queriedAt,
                            Reach reach, Engagement engagement, Acquisition acquisition, Quality quality,
                            List<Retention> retention, List<Episode> episodes, boolean episodesTruncated, List<Daily> daily) {}
}
