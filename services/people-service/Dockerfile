# syntax=docker/dockerfile:1
# Build context is the repo root (monorepo). Build just this service's bootJar
# out of the Gradle multi-project.
# ---------- build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY config config
COPY settings.gradle build.gradle ./
COPY services/people-service services/people-service
RUN chmod +x gradlew && ./gradlew :people-service:bootJar --no-daemon

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl && addgroup -S app && adduser -S -G app app
COPY --from=build /app/services/people-service/build/libs/*.jar app.jar
USER app
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
