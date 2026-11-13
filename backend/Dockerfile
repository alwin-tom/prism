FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S prism \
    && adduser -S prism -G prism \
    && mkdir -p /data/media \
    && chown -R prism:prism /data
WORKDIR /app
COPY --from=build /src/target/prism-backend.jar app.jar
USER prism
ENV PRISM_MEDIA_STORAGE_PATH=/data/media
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=18 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
