FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 kds
COPY --from=build /app/target/ms-display-cocina-*.jar app.jar
USER kds
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
