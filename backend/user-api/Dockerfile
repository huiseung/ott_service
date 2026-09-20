FROM eclipse-temurin:21-jre-jammy

RUN useradd --create-home --uid 10001 appuser

WORKDIR /app
COPY user-api/build/libs/user-api-0.0.1-SNAPSHOT.jar /app/user-api.jar
RUN chown -R appuser:appuser /app

USER appuser

ENTRYPOINT ["java", "-jar", "/app/user-api.jar"]
