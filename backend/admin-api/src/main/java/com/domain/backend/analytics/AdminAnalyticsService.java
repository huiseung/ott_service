package com.domain.backend.analytics;

import static com.domain.backend.analytics.AdminAnalyticsDtos.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnalyticsService {
    private final AnalyticsReader reader;
    private final Semaphore permits = new Semaphore(2);
    public AdminAnalyticsService(AnalyticsReader reader) { this.reader = reader; }

    @Transactional(propagation = Propagation.NEVER)
    public Dashboard dashboard(long contentId, LocalDate from, LocalDate to) {
        if (contentId <= 0 || contentId > 9_007_199_254_740_991L || from == null || to == null
                || from.isBefore(LocalDate.of(1970,1,1)) || to.isAfter(LocalDate.of(2149,6,6))
                || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 31)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid content and 1..31 days");
        if (!permits.tryAcquire()) throw AnalyticsReader.unavailable();
        try {
            var reach = reader.query(AnalyticsQueries.REACH, contentId, from, to, Reach.class).getFirst();
            var engagement = reader.query(AnalyticsQueries.ENGAGEMENT, contentId, from, to, Engagement.class).getFirst();
            var acquisition = reader.query(AnalyticsQueries.ACQUISITION, contentId, from, to, Acquisition.class).getFirst();
            var quality = reader.query(AnalyticsQueries.QUALITY, contentId, from, to, Quality.class).getFirst();
            var retention = reader.query(AnalyticsQueries.RETENTION, contentId, from, to, Retention.class);
            var episodes = reader.query(AnalyticsQueries.EPISODES, contentId, from, to, Episode.class);
            var daily = reader.query(AnalyticsQueries.DAILY, contentId, from, to, Daily.class);
            return new Dashboard(contentId, from, to, Instant.now(), reach, engagement, acquisition, quality,
                    retention, episodes.stream().limit(100).toList(), episodes.size()>100, daily);
        } catch (java.util.NoSuchElementException invalidResult) {
            throw AnalyticsReader.unavailable();
        } finally { permits.release(); }
    }
}
