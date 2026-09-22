FROM eclipse-temurin:21-jre
WORKDIR /app
COPY playback-worker/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
