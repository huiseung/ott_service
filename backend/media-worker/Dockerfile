FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 appuser

WORKDIR /app
COPY media-worker/build/libs/media-worker-0.0.1-SNAPSHOT.jar /app/media-worker.jar
RUN mkdir -p /work/jobs && chown -R appuser:appuser /app /work

USER appuser

ENTRYPOINT ["java", "-jar", "/app/media-worker.jar"]
