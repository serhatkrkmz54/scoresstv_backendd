# ===================================================================
# Multi-stage build - scorestv-backend
# Stage 1: Gradle ile bootJar uretir
# Stage 2: Sadece JRE iceren kucuk calisma imaji
# ===================================================================

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Once sadece gradle dosyalari -> bagimlilik katmani cache'lenir
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Kaynak kod
COPY src src
RUN ./gradlew clean bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# root olmayan kullanici
RUN groupadd -r app && useradd -r -g app app

COPY --from=build /app/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
