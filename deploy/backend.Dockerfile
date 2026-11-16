FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
# Tests were run before deployment; an image build does not connect to a database.
RUN mvn -B -ntp -DskipTests package && \
    cp target/family-tree-backend-0.0.1-SNAPSHOT.jar /workspace/app.jar

# A glibc runtime with java.desktop/font support is needed for portrait ImageIO/Graphics2D.
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl fontconfig fonts-dejavu-core && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
RUN mkdir -p /app/uploads && chown app:app /app/uploads
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
