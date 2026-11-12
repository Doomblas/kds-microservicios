# --- Etapa 1: Compilación con el Maven ya incluido en la imagen ---
# La imagen oficial maven:3.9.9-eclipse-temurin-21 ya trae Maven 3.9.9 y
# JDK 21, por lo que se invoca `mvn` directamente en lugar de `./mvnw`.
# El Maven Wrapper descargaba una distribución de Maven adicional y, al no
# existir `unzip` en la imagen, caía en la variante .tar.gz; ese binario
# descargado era un punto de fallo y una dependencia de red innecesaria.
# El proyecto no exige una versión de Maven superior a la incluida
# (spring-boot-starter-parent 4.1.2 no impone requireMavenVersion).
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar solo el POM mantiene la capa de caché de dependencias.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src src
RUN mvn -B -q package -DskipTests

# --- Etapa 2: Entorno de ejecución seguro y ligero de producción ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 menu
COPY --from=build --chown=menu:menu /app/target/ms-menu-*.jar app.jar
USER menu
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
