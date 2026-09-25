FROM flink:1.20.3-scala_2.12-java17
RUN mkdir -p /opt/flink/plugins/s3-fs-hadoop \
    && cp /opt/flink/opt/flink-s3-fs-hadoop-1.20.3.jar /opt/flink/plugins/s3-fs-hadoop/
COPY event-analytics/build/distribution/lib/ /opt/flink/lib/
COPY event-analytics/build/distribution/event-analytics.jar /opt/flink/usrlib/event-analytics.jar
COPY event-analytics/start-analytics.sh /opt/flink/bin/start-analytics.sh
RUN sed -i 's/\r$//' /opt/flink/bin/start-analytics.sh
