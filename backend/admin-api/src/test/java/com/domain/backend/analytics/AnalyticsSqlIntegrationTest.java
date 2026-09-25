package com.domain.backend.analytics;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/** Run only against a fresh disposable ClickHouse initialized with event-analytics/clickhouse/*.sql. */
@EnabledIfEnvironmentVariable(named="ANALYTICS_SQL_TEST_URL", matches=".+")
class AnalyticsSqlIntegrationTest {
    final String url = System.getenv("ANALYTICS_SQL_TEST_URL");
    final String user = "stage8-test";
    final String password = "stage8-test-only";
    void execute(String sql) throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url))
                .header("X-ClickHouse-User", user).header("X-ClickHouse-Key", password)
                .POST(HttpRequest.BodyPublishers.ofString(sql)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
    @Test void allDashboardQueriesUseCorrectPeriodDenominatorsAndNullEmptyMetrics() throws Exception {
        execute("""
            INSERT INTO analytics.content_events VALUES
            ('playback','80000000-0000-0000-0000-000000000001','PLAYBACK_SESSION_STARTED','2026-09-24 12:00:00',10,'user:1'),
            ('playback','80000000-0000-0000-0000-000000000001','PLAYBACK_SESSION_STARTED','2026-09-24 12:00:00',10,'user:1'),
            ('playback','80000000-0000-0000-0000-000000000002','PLAYBACK_SESSION_STARTED','2026-09-25 12:00:00',10,'user:1')
            """);
        execute("""
            INSERT INTO analytics.playback_observations
            (event_id,session_key,sequence,occurred_at,content_id,video_id,viewer_id,duration_ms,watch_ms,interval_start_ms,interval_end_ms,is_start,episode_id,next_episode_id,catalog_enriched) VALUES
            ('80000000-0000-0000-0000-000000000011','A',1,'2026-09-24 12:00:00',10,20,'user:1',100000,0,0,0,1,101,102,1),
            ('80000000-0000-0000-0000-000000000012','A',2,'2026-09-24 12:01:30',10,20,'user:1',100000,90000,0,90000,0,101,102,1),
            ('80000000-0000-0000-0000-000000000013','B',1,'2026-09-25 12:00:00',10,21,'user:1',100000,0,0,0,1,102,0,1),
            ('80000000-0000-0000-0000-000000000014','B',2,'2026-09-25 12:00:10',10,21,'user:1',100000,10000,0,10000,0,102,0,1)
            """);
        execute("""
            INSERT INTO analytics.subscription_ctas VALUES
            ('80000000-0000-0000-0000-000000000021','user:1',10,'2026-09-24 11:00:00')
            """);
        execute("""
            INSERT INTO analytics.subscription_events VALUES
            ('80000000-0000-0000-0000-000000000022','CHECKOUT_STARTED','80000000-0000-0000-0000-000000000020','checkout','80000000-0000-0000-0000-000000000021','user:1',10,'2026-09-24 11:00:01',0,'LOCAL_TEST'),
            ('80000000-0000-0000-0000-000000000023','SUBSCRIPTION_ACTIVATED','80000000-0000-0000-0000-000000000020','checkout','80000000-0000-0000-0000-000000000021','user:1',10,'2026-09-24 11:00:02',1,'LOCAL_TEST')
            """);
        execute("""
            INSERT INTO analytics.qoe_events
            (event_id,session_key,sequence,occurred_at,content_id,video_id,event_type,watch_ms,startup_ms,buffer_ms,fatal) VALUES
            ('80000000-0000-0000-0000-000000000031','A',1,'2026-09-24 12:00:00',10,20,'PLAYBACK_SESSION_STARTED',0,NULL,0,0),
            ('80000000-0000-0000-0000-000000000032','A',2,'2026-09-24 12:00:01',10,20,'PLAY',0,1000,0,0),
            ('80000000-0000-0000-0000-000000000033','A',3,'2026-09-24 12:01:31',10,20,'BUFFER_STARTED',90000,NULL,0,0),
            ('80000000-0000-0000-0000-000000000034','A',4,'2026-09-24 12:01:41',10,20,'BUFFER_ENDED',0,NULL,10000,0),
            ('80000000-0000-0000-0000-000000000035','B',1,'2026-09-25 12:00:00',10,21,'PLAYBACK_SESSION_STARTED',0,NULL,0,0),
            ('80000000-0000-0000-0000-000000000036','B',2,'2026-09-25 12:00:03',10,21,'PLAY',0,3000,0,0),
            ('80000000-0000-0000-0000-000000000037','B',3,'2026-09-25 12:00:13',10,21,'PLAYBACK_ERROR',10000,NULL,0,1)
            """);
        var service = new AdminAnalyticsService(new AnalyticsReader(url, user, password));
        var from = LocalDate.of(2026,9,24); var to = LocalDate.of(2026,9,25);
        var data = service.dashboard(10,from,to);
        assertThat(data.reach().playStarts()).isEqualTo(2);
        assertThat(data.reach().uniqueViewers()).isEqualTo(1);
        assertThat(data.daily()).hasSize(2);
        assertThat(data.engagement().completionRate()).isEqualTo(0.5);
        assertThat(data.engagement().averageWatchSeconds()).isEqualTo(50);
        assertThat(data.retention()).hasSize(10);
        assertThat(data.episodes().getFirst().rate()).isEqualTo(1);
        assertThat(data.acquisition().conversionRate()).isEqualTo(1);
        assertThat(data.acquisition().testSubscriptions()).isEqualTo(1);
        assertThat(data.quality().averageStartupMs()).isEqualTo(2000);
        assertThat(data.quality().rebufferRatio()).isCloseTo(1.0/11, within(0.000001));
        assertThat(data.quality().errorRate()).isEqualTo(0.5);
        var empty = service.dashboard(11,from,to);
        assertThat(empty.reach().playStarts()).isZero();
        assertThat(empty.reach().latestEventAt()).isNull();
        assertThat(empty.engagement().completionRate()).isNull();
        assertThat(empty.acquisition().conversionRate()).isNull();
        assertThat(empty.quality().averageStartupMs()).isNull();
        assertThat(empty.quality().errorRate()).isNull();
        assertThat(empty.daily()).isEmpty();
        // Reusable synthetic fixtures for browser smoke tests; no production records.
        var mapper = JsonMapper.builder().build();
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/analytics-preview.json"), mapper.writeValueAsString(data));
        Files.writeString(Path.of("build/analytics-empty.json"), mapper.writeValueAsString(empty));
    }
}
