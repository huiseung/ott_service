FROM eclipse-temurin:21-jre-jammy

RUN useradd --create-home --uid 10001 appuser

WORKDIR /app
COPY admin-api/build/libs/admin-api-0.0.1-SNAPSHOT.jar /app/admin-api.jar
RUN chown -R appuser:appuser /app

USER appuser

ENTRYPOINT ["java", "-jar", "/app/admin-api.jar"]
