# ---- Build ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Exécution (image légère, utilisateur non root) ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S revia && adduser -S revia -G revia
WORKDIR /app
COPY --from=build /app/target/revia.jar app.jar
USER revia
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD wget -qO- http://localhost:${PORT:-8085}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
