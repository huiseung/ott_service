SET 'execution.target' = 'local';
SET 'rest.bind-port' = '0';
SET 'rest.port' = '0';
SET 'execution.runtime-mode' = 'batch';
SET 'sql-client.execution.result-mode' = 'tableau';
SET 'parallelism.default' = '1';
CREATE TABLE verify_playback (
 event_id STRING, event_type STRING, raw_event BYTES, kafka_topic STRING,
 kafka_partition INT, kafka_offset BIGINT, kafka_timestamp TIMESTAMP(3), dt STRING, `hour` STRING
) PARTITIONED BY (dt, `hour`) WITH (
 'connector'='filesystem', 'path'='s3://event-lake/raw/playback', 'format'='parquet'
);
CREATE TABLE verify_behavior (
 event_id STRING, event_type STRING, raw_event BYTES, kafka_topic STRING,
 kafka_partition INT, kafka_offset BIGINT, kafka_timestamp TIMESTAMP(3), dt STRING, `hour` STRING
) PARTITIONED BY (dt, `hour`) WITH (
 'connector'='filesystem', 'path'='s3://event-lake/raw/behavior', 'format'='parquet'
);
CREATE TABLE verify_subscription (
 event_id STRING, event_type STRING, raw_event BYTES, kafka_topic STRING,
 kafka_partition INT, kafka_offset BIGINT, kafka_timestamp TIMESTAMP(3), dt STRING, `hour` STRING
) PARTITIONED BY (dt, `hour`) WITH (
 'connector'='filesystem', 'path'='s3://event-lake/raw/subscription', 'format'='parquet'
);
SELECT kafka_topic, COUNT(*) AS row_count,
 COUNT(DISTINCT CONCAT(CAST(kafka_partition AS STRING), ':', CAST(kafka_offset AS STRING))) AS unique_positions,
 COUNT(raw_event) AS raw_rows,
 MIN(dt) AS partition_date
FROM (SELECT * FROM verify_playback UNION ALL SELECT * FROM verify_behavior UNION ALL SELECT * FROM verify_subscription)
GROUP BY kafka_topic;
