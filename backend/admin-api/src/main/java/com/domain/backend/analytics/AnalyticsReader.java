package com.domain.backend.analytics;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AnalyticsReader {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String endpoint;
    private final String user;
    private final String password;

    public AnalyticsReader(@Value("${app.analytics.url:http://localhost:8123}") String endpoint,
                           @Value("${app.analytics.user:}") String user,
                           @Value("${app.analytics.password:}") String password) {
        this.endpoint = endpoint.replaceAll("/+$", ""); this.user = user; this.password = password;
    }

    public <T> List<T> query(String sql, long content, LocalDate from, LocalDate to, Class<T> type) {
        if (user.isBlank() || password.isBlank()) throw unavailable();
        String params = "?readonly=1&max_execution_time=2&max_result_rows=1000&max_result_bytes=131072"
                + "&result_overflow_mode=throw&max_memory_usage=268435456&max_threads=2&wait_end_of_query=1"
                + "&output_format_json_quote_64bit_integers=0"
                + "&param_content=" + content + "&param_from=" + encode(from.toString()) + "&param_to=" + encode(to.toString());
        var request = HttpRequest.newBuilder(URI.create(endpoint + params)).timeout(Duration.ofSeconds(4))
                .header("X-ClickHouse-User", user).header("X-ClickHouse-Key", password)
                .POST(HttpRequest.BodyPublishers.ofString(sql + " FORMAT JSON")).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 262144) throw unavailable();
            var data = json.readTree(response.body()).path("data");
            if (!data.isArray() || data.size() > 1000) throw unavailable();
            List<T> result = new ArrayList<>();
            for (var row : data) result.add(json.treeToValue(row, type));
            return List.copyOf(result);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (Exception failed) {
            // Never expose SQL, credentials or upstream response bodies to the browser.
            throw unavailable();
        }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analytics is temporarily unavailable");
    }
}
