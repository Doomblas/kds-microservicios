# metricas-kds — Herramienta de medición de propagación de eventos del KDS

Utilidad de pruebas para obtener, de forma automática y reproducible, los datos del
apartado 7.2 del informe académico del proyecto KDS.

**No es un microservicio.** Es una aplicación Java de línea de comandos (Java 21) que se
conecta al stack KDS ya desplegado, ejecuta las pruebas de propagación y genera los
artefactos del informe (CSV, JSON, PNG y textos).

---

## 1. Qué mide

| Escenario | Flujo medido | t0 | t1 |
|---|---|---|---|
| Pedido nuevo | ms-pedidos → RabbitMQ → ms-display-cocina | justo antes del `POST /pedidos` | primer instante en que `GET /ordenes` devuelve la orden del pedido |
| Cambio a PREPARANDO | ms-display-cocina → RabbitMQ → ms-pedidos | justo antes del `PATCH /ordenes/{id}/estado` | primer instante en que `GET /pedidos/{id}/estado` devuelve `PREPARANDO` |
| Cambio a LISTO | ms-display-cocina → RabbitMQ → ms-pedidos | justo antes del `PATCH /ordenes/{id}/estado` | primer instante en que `GET /pedidos/{id}/estado` devuelve `LISTO` |

* La latencia se mide con `System.nanoTime()` (reloj monotónico) y se expresa en ms con tres decimales.
* No se mide el tiempo de respuesta HTTP de la petición original, sino el tiempo hasta que el
  estado es observable en el servicio consumidor.
* La correlación entre el pedido y la orden de cocina se hace por `pedidoId`, que es el
  identificador que viaja en los eventos `pedido.creado` y `pedido.estado.actualizado`.
* La propagación se detecta con sondeo corto (10 ms por defecto) que se detiene en cuanto se
  observa el estado esperado.

## 2. Requisitos

* JDK 21.
* El stack KDS levantado (`docker compose -f compose.integration.yaml up -d`) con el API Gateway
  accesible en `http://localhost:8080`.
* Un usuario con rol `ADMIN` (el rol ADMIN tiene acceso a `/pedidos`, `/ordenes` y `/productos`).

## 3. Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `KDS_BASE_URL` | `http://localhost:8080` | URL base del API Gateway |
| `KDS_ADMIN_EMAIL` | *(obligatoria)* | Correo del usuario ADMIN de prueba |
| `KDS_ADMIN_PASSWORD` | *(obligatoria)* | Contraseña del usuario ADMIN de prueba |
| `KDS_NUM_PRUEBAS` | `20` | Iteraciones completas (cada una: pedido nuevo → PREPARANDO → LISTO) |
| `KDS_TIMEOUT_MS` | `10000` | Tiempo de espera máximo por evento |
| `KDS_POLL_INTERVAL_MS` | `10` | Intervalo de sondeo |
| `KDS_CALENTAMIENTO` | `0` | Iteraciones previas no contabilizadas |
| `KDS_PRODUCTO_ID` | *(automático)* | Producto a usar; por defecto, el primero disponible |
| `KDS_CANTIDAD` | `1` | Unidades por producto en cada pedido |
| `KDS_DIR_RESULTADOS` | `resultados` | Directorio de salida |
| `KDS_EJECUTAR_HUMO` | `true` | Prueba de humo del flujo de eventos antes de medir |

**No hay ninguna credencial almacenada en el repositorio.**

## 4. Ejecución

```bash
cd tools/metricas-kds

KDS_ADMIN_EMAIL=admin@kds.local \
KDS_ADMIN_PASSWORD='****' \
KDS_NUM_PRUEBAS=20 \
./run.sh
```

Alternativa sin el script:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
KDS_ADMIN_EMAIL=... KDS_ADMIN_PASSWORD=... KDS_NUM_PRUEBAS=20 \
./mvnw compile exec:java
```

O empaquetando el jar:

```bash
./mvnw -DskipTests package
java -jar target/metricas-kds.jar
```

## 5. Validaciones previas

Antes de medir, la herramienta aborta con un mensaje claro si falla alguna de estas comprobaciones:

1. El API Gateway responde (`GET /actuator/health`).
2. El login funciona (`POST /auth/login`).
3. El catálogo de `ms-menu` contiene al menos un producto disponible.
4. `ms-pedidos` responde (`GET /pedidos`).
5. `ms-display-cocina` responde (`GET /ordenes`).
6. El flujo de eventos RabbitMQ funciona en los dos sentidos: se crea un pedido de comprobación y se
   verifica que llega a cocina y que el cambio de estado vuelve a `ms-pedidos`.

## 6. Artefactos generados

Todos en `resultados/` (configurable con `KDS_DIR_RESULTADOS`):

| Fichero | Contenido |
|---|---|
| `latencias_detalle.csv` | Una fila por prueba: escenario, prueba, pedido_id, numero_pedido, exito, latencia_ms, timestamp, detalle |
| `latencias_resumen.csv` | Una fila por escenario con las métricas agregadas |
| `latencias_resumen.json` | Resumen con las mismas métricas en JSON, más los metadatos de la corrida |
| `latencias_promedio.png` | **Gráfica obligatoria**: barras con la latencia media de cada escenario (1600×900) |
| `latencias_min_media_max.png` | Gráfica secundaria: mínima, media y máxima por escenario |
| `tabla_informe.txt` | Tabla lista para pegar en el informe |
| `resumen_informe.txt` | Resumen académico redactado a partir de los datos reales |
| `metodologia_medicion.txt` | Metodología de medición lista para la sección de Metodología |

Las estadísticas de latencia se calculan **únicamente sobre las pruebas exitosas**. Las pruebas
fallidas se contabilizan en el porcentaje de éxito y se registran en el CSV de detalle; no se les
asigna ninguna latencia estimada.

## 7. Pruebas unitarias

```bash
./mvnw test
```

Cubren el cálculo de media, mediana, desviación estándar, porcentaje de éxito, el tratamiento de
listas vacías, la generación de los CSV/JSON y la redacción de los textos del informe.

## 8. Datos generados

Los pedidos creados durante la medición permanecen en las bases de datos del prototipo, porque el
contrato REST no expone ningún endpoint de borrado de pedidos. Cada pedido generado queda
identificado por su `id` y su `numeroPedido`, y ambos aparecen en `latencias_detalle.csv`.

## 9. Notas

* La resolución de la medida está acotada por el intervalo de sondeo: la latencia reportada puede
  incluir hasta un ciclo de sondeo adicional por encima del valor real.
* El token JWT caduca a los 30 minutos por defecto; si expira durante la campaña, la herramienta
  renueva la sesión y reintenta la petición una vez.
