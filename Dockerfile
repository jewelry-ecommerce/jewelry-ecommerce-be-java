# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pk-core/ ./pk-core/
# Mirror + retries: Maven Central often returns 429 on Render free builders.
RUN --mount=type=cache,target=/root/.m2/repository \
    set -eux; \
    for attempt in 1 2 3 4 5; do \
      if mvn -B -ntp -s /root/.m2/settings.xml \
          -Daether.connector.http.retryHandler.count=5 \
          -Daether.connector.http.retryHandler.requestSentEnabled=true \
          -f pk-core/pom.xml -pl pk-service -am package -DskipTests; then \
        exit 0; \
      fi; \
      echo "Maven build failed (attempt ${attempt}/5); backing off..."; \
      sleep $((attempt * 20)); \
    done; \
    echo "Maven build failed after 5 attempts"; \
    exit 1

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=app:app /build/pk-core/pk-service/target/*-exec.jar /app/app.jar
ENV PORT=8080 \
    UPLOAD_DIR=/tmp/jewelry-uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError"
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
