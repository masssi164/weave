FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=weave-mcp-server/build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
