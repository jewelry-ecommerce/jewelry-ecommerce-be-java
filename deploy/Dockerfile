# syntax=docker/dockerfile:1
# Build + chạy pk-core trong Docker. Build context = thư mục pk-core (xem docker-compose.yml).
# Chạy được cả máy x86 và ARM (Oracle Ampere A1) vì dùng image đa kiến trúc.

# ---------- Giai đoạn 1: build bằng Maven (không cần cài Maven/JDK trên server) ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
# Cache thư mục ~/.m2 giữa các lần build để lần sau nhanh hơn.
# -pl pk-service -am: chỉ build pk-service và các module nó cần (bỏ pk-unit-test); test đã chạy ở máy dev.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests -pl pk-service -am package \
 && cp pk-service/target/pk-service-*-exec.jar /app.jar

# ---------- Giai đoạn 2: image chạy (chỉ JRE, nhỏ gọn) ----------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
 && mkdir -p /data/uploads && chown -R app:app /data
COPY --from=build /app.jar /app/app.jar
USER app
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Ho_Chi_Minh"
EXPOSE 8080
VOLUME /data/uploads
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
