FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/ ./
RUN mvn -B -DskipTests package && \
    JAR="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*sources.jar' | head -n 1)" && \
    test -n "$JAR" && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget && addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
