package com.domain.backend.archive;

import java.time.ZoneId;
import java.util.Map;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;

/** One independent streaming job for all raw domains. No deduplication or business aggregation. */
public final class RawArchiveJob {
    public static void main(String[] args) {
        var tables = TableEnvironment.create(EnvironmentSettings.inStreamingMode());
        tables.getConfig().setLocalTimeZone(ZoneId.of("UTC"));
        tables.getConfig().getConfiguration().setString("pipeline.name", "raw-event-archive");
        var inserts = tables.createStatementSet();
        var topics = Map.of(
                "playback", env("ANALYTICS_PLAYBACK_TOPIC", "playback-events"),
                "behavior", env("ANALYTICS_BEHAVIOR_TOPIC", "behavior-events"),
                "subscription", env("ANALYTICS_SUBSCRIPTION_TOPIC", "subscription-events"));
        if (topics.values().stream().distinct().count() != topics.size())
            throw new IllegalArgumentException("Archive domain topics must be distinct");
        for (String domain : new String[]{"playback", "behavior", "subscription"}) {
            tables.executeSql("""
                    CREATE TABLE source_%s (
                        raw_event BYTES,
                        kafka_topic STRING METADATA FROM 'topic' VIRTUAL,
                        kafka_partition INT METADATA FROM 'partition' VIRTUAL,
                        kafka_offset BIGINT METADATA FROM 'offset' VIRTUAL,
                        kafka_timestamp TIMESTAMP_LTZ(3) METADATA FROM 'timestamp' VIRTUAL
                    ) WITH (
                        'connector' = 'kafka', 'topic' = '%s',
                        'properties.bootstrap.servers' = '%s',
                        'properties.group.id' = '%s',
                        'scan.startup.mode' = 'earliest-offset',
                        'properties.enable.auto.commit' = 'false',
                        'properties.partition.discovery.interval.ms' = '30000',
                        'format' = 'raw'
                    )
                    """.formatted(domain, sql(topics.get(domain)), sql(env("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092")),
                    sql(env("ARCHIVE_CONSUMER_GROUP", "raw-event-archive") + "-" + domain)));
            tables.executeSql("""
                    CREATE TABLE archive_%s (
                        event_id STRING, event_type STRING, raw_event BYTES,
                        kafka_topic STRING, kafka_partition INT, kafka_offset BIGINT,
                        kafka_timestamp TIMESTAMP(3), dt STRING, `hour` STRING
                    ) PARTITIONED BY (dt, `hour`) WITH (
                        'connector' = 'filesystem', 'path' = '%s/raw/%s',
                        'format' = 'parquet', 'parquet.compression' = 'SNAPPY',
                        'sink.rolling-policy.file-size' = '%s',
                        'sink.rolling-policy.rollover-interval' = '%s',
                        'sink.rolling-policy.check-interval' = '10 s'
                    )
                    """.formatted(domain, sql(env("ARCHIVE_ROOT", "s3://event-lake")), domain,
                    sql(env("ARCHIVE_FILE_SIZE", "128 MB")), sql(env("ARCHIVE_ROLLOVER_INTERVAL", "60 s"))));
            inserts.addInsertSql("""
                    INSERT INTO archive_%s
                    SELECT JSON_VALUE(CAST(raw_event AS STRING), '$.eventId' NULL ON ERROR),
                           JSON_VALUE(CAST(raw_event AS STRING), '$.eventType' NULL ON ERROR),
                           raw_event, kafka_topic, kafka_partition, kafka_offset,
                           CAST(kafka_timestamp AS TIMESTAMP(3)),
                           DATE_FORMAT(kafka_timestamp, 'yyyy-MM-dd'), DATE_FORMAT(kafka_timestamp, 'HH')
                    FROM source_%s
                    """.formatted(domain, domain));
        }
        inserts.execute();
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sql(String value) { return value.replace("'", "''"); }
}
