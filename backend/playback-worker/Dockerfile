FROM eclipse-temurin:21-jre
WORKDIR /app
COPY playback-worker/build/libs/playback-worker-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
