package com.domain.backend.analyticsjob;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.format.DateTimeParseException;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.metrics.Counter;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public final class AnalyticsJob {
    public static void main(String[] args) throws Exception {
        var env = StreamExecutionEnvironment.getExecutionEnvironment();
        String playback = setting("ANALYTICS_PLAYBACK_TOPIC", "playback-events");
        String behavior = setting("ANALYTICS_BEHAVIOR_TOPIC", "behavior-events");
        String subscription = setting("ANALYTICS_SUBSCRIPTION_TOPIC", "subscription-events");
        if (java.util.stream.Stream.of(playback, behavior, subscription).distinct().count() != 3)
            throw new IllegalArgumentException("Domain topics must differ");
        for (String domain : new String[]{"playback", "behavior", "subscription"}) {
            var source = KafkaSource.<String>builder()
                    .setBootstrapServers(setting("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"))
                    .setTopics(domain.equals("playback") ? playback : domain.equals("behavior") ? behavior : subscription)
                    .setGroupId(setting("ANALYTICS_CONSUMER_GROUP", "content-analytics") + "-" + domain)
                    .setStartingOffsets(OffsetsInitializer.earliest())
                    .setProperty("enable.auto.commit", "false")
                    .setProperty("partition.discovery.interval.ms", "30000")
                    .setValueOnlyDeserializer(new SimpleStringSchema()).build();
            var events = env.fromSource(source, WatermarkStrategy.noWatermarks(), domain + "-events")
                    .uid("analytics-source-" + domain);
            if (!domain.equals("subscription")) events
                    .flatMap(new ProjectContent(domain)).name(domain + "-projection").uid("content-projection-" + domain)
                    .addSink(new ClickHouseBatchSink(setting("CLICKHOUSE_URL", "http://clickhouse:8123"),
                            Integer.parseInt(setting("ANALYTICS_BATCH_ROWS", "1000"))))
                    .name(domain + "-clickhouse").uid("clickhouse-content-" + domain);
            if (domain.equals("playback")) {
                events.flatMap(new ProjectContent("qoe")).name("playback-qoe").uid("playback-qoe-v1")
                        .addSink(new ClickHouseBatchSink(setting("CLICKHOUSE_URL", "http://clickhouse:8123"),
                                Integer.parseInt(setting("ANALYTICS_BATCH_ROWS", "1000")), "qoe_events"))
                        .name("qoe-clickhouse").uid("clickhouse-qoe-v1");
                events.flatMap(new ProjectContent("engagement")).name("playback-observations").uid("playback-observations-v1")
                        .addSink(new ClickHouseBatchSink(setting("CLICKHOUSE_URL", "http://clickhouse:8123"),
                                Integer.parseInt(setting("ANALYTICS_BATCH_ROWS", "1000")), "playback_observations"))
                        .name("engagement-clickhouse").uid("clickhouse-engagement-v1");
            }
            if (!domain.equals("playback")) {
                String projection = domain.equals("subscription") ? "subscription" : "cta";
                String table = domain.equals("subscription") ? "subscription_events" : "subscription_ctas";
                events.flatMap(new ProjectContent(projection)).name(projection + "-projection").uid(projection + "-projection-v1")
                        .addSink(new ClickHouseBatchSink(setting("CLICKHOUSE_URL", "http://clickhouse:8123"),
                                Integer.parseInt(setting("ANALYTICS_BATCH_ROWS", "1000")), table))
                        .name(projection + "-clickhouse").uid("clickhouse-" + projection + "-v1");
            }
        }
        env.execute("content-event-analytics");
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static final class ProjectContent extends RichFlatMapFunction<String, String> {
        private final String domain;
        private transient Counter rejected;
        private transient Counter ignored;
        public ProjectContent(String domain) { this.domain = domain; }
        @Override public void open(Configuration config) {
            rejected = getRuntimeContext().getMetricGroup().counter("rejectedEvents");
            ignored = getRuntimeContext().getMetricGroup().counter("ignoredEvents");
        }
        @Override public void flatMap(String value, Collector<String> out) throws Exception {
            String row;
            try { row = switch (domain) {
                case "engagement" -> PlaybackObservationProjection.project(value);
                case "qoe" -> QoeProjection.project(value);
                case "subscription", "cta" -> SubscriptionProjection.project(value, domain.equals("cta"));
                default -> ContentEventProjection.project(value, domain);
            }; }
            catch (JsonProcessingException | IllegalArgumentException | DateTimeParseException invalid) {
                rejected.inc();
                return; // The independent raw archive retains the original event for future reprocessing.
            }
            if (row == null) ignored.inc();
            else out.collect(row);
        }
    }
}
