FROM maven:3.9-eclipse-temurin-8 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:8-jre

WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/audit-log-service-*.jar /app/app.jar
RUN chown appuser:appuser /app/app.jar
USER 10001

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]