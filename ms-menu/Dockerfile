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
RUN useradd --system --uid 1001 menu
COPY --from=build --chown=menu:menu /app/target/ms-menu-*.jar app.jar
USER menu
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
