package com.domain.backend.analyticsjob;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

/** At-least-once HTTP batches. A checkpoint cannot complete before all prior rows are acknowledged. */
public final class ClickHouseBatchSink extends RichSinkFunction<String> implements CheckpointedFunction {
    private final String endpoint;
    private final int maxRows;
    private final String table;
    private transient HttpClient client;
    private transient StringBuilder pending;
    private transient int rows;
    private transient int bytes;
    private transient String user;
    private transient String password;

    public ClickHouseBatchSink(String endpoint, int maxRows) {
        this(endpoint, maxRows, "content_events");
    }

    public ClickHouseBatchSink(String endpoint, int maxRows, String table) {
        if (maxRows < 1 || maxRows > 10_000) throw new IllegalArgumentException("Batch rows must be 1..10000");
        if (!java.util.Set.of("content_events", "playback_observations", "subscription_events", "subscription_ctas", "qoe_events").contains(table))
            throw new IllegalArgumentException("Unknown serving table");
        this.endpoint = endpoint;
        this.maxRows = maxRows;
        this.table = table;
    }

    @Override
    public void open(Configuration parameters) {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        pending = new StringBuilder();
        user = System.getenv("CLICKHOUSE_USER");
        password = System.getenv("CLICKHOUSE_PASSWORD");
        if (user == null || user.isBlank() || password == null || password.isBlank())
            throw new IllegalArgumentException("ClickHouse credentials are required");
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        pending.append(value).append('\n');
        rows++;
        bytes += value.getBytes(StandardCharsets.UTF_8).length + 1;
        if (rows >= maxRows || bytes >= 512 * 1024) flush();
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception { flush(); }

    @Override
    public void initializeState(FunctionInitializationContext context) {
        // Successful snapshots have no pending rows. Failed/uncheckpointed writes are replayed by Kafka.
    }

    private void flush() throws IOException, InterruptedException {
        if (rows == 0) return;
        var request = HttpRequest.newBuilder(URI.create(endpoint +
                        "/?wait_end_of_query=1&async_insert=0&date_time_input_format=best_effort"))
                .timeout(Duration.ofSeconds(20))
                .header("X-ClickHouse-User", user).header("X-ClickHouse-Key", password)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "INSERT INTO analytics." + table + " FORMAT JSONEachRow\n" + pending))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // INSERT has an empty successful body. Do not log exception bodies containing user data.
        if (response.statusCode() != 200 || !response.body().isBlank())
            throw new IOException("ClickHouse insert failed (HTTP " + response.statusCode() + ")");
        pending.setLength(0);
        rows = 0;
        bytes = 0;
    }
}
