FROM flink:1.20.3-scala_2.12-java17

# The S3 plugin is isolated from the SQL connector classpath, as required by Flink.
RUN mkdir -p /opt/flink/plugins/s3-fs-hadoop \
    && cp /opt/flink/opt/flink-s3-fs-hadoop-1.20.3.jar /opt/flink/plugins/s3-fs-hadoop/
COPY event-archive/build/distribution/lib/ /opt/flink/lib/
COPY event-archive/build/distribution/event-archive.jar /opt/flink/usrlib/event-archive.jar
COPY event-archive/start-archive.sh /opt/flink/bin/start-archive.sh
# Keep the script usable with Windows checkouts (CRLF).
RUN sed -i 's/\r$//' /opt/flink/bin/start-archive.sh
