FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ARG JAR_FILE=weave-mcp-server/build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
