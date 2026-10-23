# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pk-core/ ./pk-core/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f pk-core/pom.xml -pl pk-service -am package -DskipTests

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
