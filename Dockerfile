# ── build ─────────────────────────────────────────────
FROM gradle:7.6-jdk11 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ── runtime ───────────────────────────────────────────
FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
# 한국 사용자 대상 — JVM 타임존 KST
ENV TZ=Asia/Seoul
# 컨테이너 메모리의 75% 까지 힙으로 사용 (Railway 기본 메모리 512MB~)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
# Spring Boot 가 server.port 를 ${PORT} 로 읽으므로 Railway 가 동적 할당
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
