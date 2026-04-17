# ============================================================
#  PCH MSA — Multi-stage Dockerfile (all services)
#  Usage: docker build --build-arg SERVICE=pch-auth-service -t pch-auth-service .
# ============================================================

# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY pch-common/ pch-common/

ARG SERVICE
COPY ${SERVICE}/ ${SERVICE}/

RUN chmod +x gradlew \
    && ./gradlew :${SERVICE}:bootJar -x test --no-daemon --parallel

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S pch && adduser -S pch -G pch

WORKDIR /app

ARG SERVICE
COPY --from=builder /app/${SERVICE}/build/libs/*.jar app.jar

RUN chown -R pch:pch /app
USER pch

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
