package com.domain.backend.analytics;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

class AnalyticsReaderTest {
    @Test void parameterizesReadsAndPreservesMissingSamples() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var query = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"data\":[{\"playStarts\":2,\"uniqueViewers\":1,\"impressions\":0,\"clicks\":0,\"detailViews\":0,\"ctaClicks\":0,\"latestEventAt\":null}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var reader = new AnalyticsReader("http://127.0.0.1:"+server.getAddress().getPort(), "test", "test-only");
            var day = LocalDate.of(2026,9,25);
            var result = reader.query(AnalyticsQueries.REACH, 42, day, day, AdminAnalyticsDtos.Reach.class).getFirst();
            assertThat(result.uniqueViewers()).isEqualTo(1);
            assertThat(result.latestEventAt()).isNull();
            assertThat(query.get()).contains("readonly=1", "param_content=42", "param_from=2026-09-25", "max_execution_time=2");
        } finally { server.stop(0); }
    }
    @Test void missingCredentialsFailOnlyAnalyticsRequest() {
        var reader = new AnalyticsReader("http://localhost:8123", "", "");
        assertThatThrownBy(() -> reader.query("SELECT 1", 1, LocalDate.now(), LocalDate.now(), Object.class))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("password");
    }
}
