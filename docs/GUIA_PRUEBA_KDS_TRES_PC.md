# Prueba del KDS distribuido: una PC por microservicio

Esta guía describe un **ensayo de LAN**, distinto de
`compose.integration.yaml` ejecutado completo en una sola PC. Cada integrante
arranca sólo su backend, su base PostgreSQL local y su interfaz. Jorge ejecuta
además el Gateway propuesto y **un único RabbitMQ** para Pedidos y Cocina. No
hay bases compartidas ni lectura cruzada de PostgreSQL.

El PDF técnico de referencia está en `docs/` **sólo como material local**: no
debe añadirse al repositorio. Consultar también
[`GUIA_GATEWAY_KDS.md`](GUIA_GATEWAY_KDS.md) antes de anunciar que las UIs
están probadas a través del Gateway.

## 1. Topología que acordará el equipo

Sustituir las IP de ejemplo por las IP LAN **reales**; registrarlas en el grupo
y comprobar que no cambien durante la prueba (reserva DHCP o IP fija).

| Integrante | Ejemplo de IP | Levanta en su PC | Puertos de host usados aquí |
|---|---|---|---|
| Douglas | `192.168.1.10` | `ms-menu`, `menu-db`, frontend Menú | API `8080`, DB `5434`, UI `5174` |
| Jorge | `192.168.1.11` | `ms-pedidos`, `pedidos-db`, RabbitMQ, Gateway, frontend Pedidos | API `8082`, DB `5436`, AMQP `5673`, Gateway `8090`, UI `5176` |
| Marco | `192.168.1.12` | `ms-display-cocina`, `cocina-db`, frontend Cocina | API `8083`, DB `5435`, UI `5173` |

**Diferencia importante:** dentro del Compose de una sola máquina las apps
usan `ms-menu:8080`, `rabbitmq:5672`, etc. En tres PC **esos nombres no
existen** entre máquinas. Los ejemplos de abajo ejecutan los backends en el
**host** (Maven), PostgreSQL/RabbitMQ en contenedores con puertos publicados,
las UIs con Vite y el Gateway de Jorge en el host. Por eso RabbitMQ se conecta
desde el host de Jorge como `localhost:5673` y desde el host de Marco como
`192.168.1.11:5673`. Si alguien elige contenerizar también los backends, debe
reemplazar `localhost` por DNS de la red Docker **local** o por
`host.docker.internal`/`host-gateway` según el sistema, y usar las IP LAN para
los servicios de otras PC. No arrancar el Compose completo en las tres PC:
eso crearía tres brokers y servicios duplicados.

## 2. Preparación (todos)

1. Conectarse a la misma red de confianza, sin VPN/aislamiento de clientes
   Wi-Fi; tener Java 21, Maven wrapper, Node/npm y Docker Compose disponibles.
2. Sincronizar la rama `develop`; comprobar que cada servicio y sus tres
   frontends están presentes. Mantener las contraseñas **fuera de Git**.
3. Conservar el `.env` local si ya existe. Para los contenedores de base y
   RabbitMQ, usar en cada PC las mismas credenciales de desarrollo del archivo
   `.env.example` como punto de partida, o definir una contraseña compartida
   para el broker en variables locales. No publicar `.env` ni claves reales.
4. Configurar el cortafuegos para permitir **sólo a las otras dos IP** los
   puertos necesarios: Douglas `8080` (Menú); Jorge `8090` (Gateway) y `5673`
   (AMQP para Marco); las UIs `5174`, `5176` y `5173` si se mostrarán desde
   otra PC. Si Jorge quiere probar el backend Cocina directamente, Marco
   habilita temporalmente `8083` sólo para él (y el Gateway debe poder llegar
   a ese puerto). No abrir DB `5434`/`5435`/`5436`, ni Management `15673`, a
   toda la LAN o Internet. El port mapping del Compose actual escucha en todas
   las interfaces: usar firewall o un Compose específico para LAN que publique
   las DB sólo en `127.0.0.1`.
5. Probar conectividad antes de arrancar las UIs. `ping` puede estar bloqueado;
   preferir comprobaciones HTTP/TCP. Comprobar también que las PC se alcanzan
   por sus **IP**, no sólo por `localhost`.

### Arranque de contenedores de infraestructura, por PC

Ejecutar desde la raíz del repositorio en **cada PC** sólo la línea que le
corresponde. Si no existe `.env`, copiar `.env.example` sin sobrescribirlo:

```bash
test -f .env || cp .env.example .env
# Douglas: docker compose -f compose.integration.yaml up -d menu-db
# Jorge:   docker compose -f compose.integration.yaml up -d pedidos-db rabbitmq
# Marco:   docker compose -f compose.integration.yaml up -d cocina-db
```

Estas líneas **no** levantan los microservicios ni los frontends del Compose;
los pasos siguientes los arrancan en el host, sin depender de DNS de Docker.
`docker compose -f compose.integration.yaml ps` permite revisar salud y
puertos. Las bases conservan sus datos en volúmenes; no ejecutar `down -v` si
se quieren conservar los pedidos.

## 3. Arrancar cada backend en el host

**Douglas (PC Menú):** su PostgreSQL está expuesto sólo para su JVM en `5434`.

```bash
cd ms-menu
DB_PASSWORD='<CLAVE_POSTGRES_LOCAL>' \
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5434/menu_db' \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD='<CLAVE_POSTGRES_LOCAL>' \
./mvnw spring-boot:run
```

`ms-menu` escucha por defecto en `8080`. Usar la contraseña configurada para
`menu-db` en su `.env`. **Jorge (PC Pedidos):** su PostgreSQL usa `5436`, el
RabbitMQ compartido está en **su propio host** en `5673` y Menú está en la PC
de Douglas:

```bash
cd ms-pedidos
DB_URL='jdbc:postgresql://localhost:5436/pedidos_db' \
DB_USERNAME=postgres DB_PASSWORD='<CLAVE_POSTGRES_LOCAL>' \
RABBITMQ_HOST=localhost RABBITMQ_PORT=5673 \
RABBITMQ_USERNAME='<USUARIO_RABBIT>' RABBITMQ_PASSWORD='<CLAVE_RABBIT>' \
MENU_BASE_URL='http://192.168.1.10:8080' \
./mvnw spring-boot:run
```

**Marco (PC Cocina):** su PostgreSQL usa `5435` y RabbitMQ está en la PC de
Jorge; no se conecta a la base de Pedidos ni de Menú:

```bash
cd ms-display-cocina
DB_URL='jdbc:postgresql://localhost:5435/cocina_db' \
DB_USERNAME=postgres DB_PASSWORD='<CLAVE_POSTGRES_LOCAL>' \
RABBITMQ_HOST='192.168.1.11' RABBITMQ_PORT=5673 \
RABBITMQ_USERNAME='<USUARIO_RABBIT>' RABBITMQ_PASSWORD='<CLAVE_RABBIT>' \
./mvnw spring-boot:run
```

Si se usa configuración de orígenes WebSocket más restrictiva que el `*` de
desarrollo, añadir `WS_ALLOWED_ORIGINS` con los orígenes **reales** de las UIs
(esquema, IP y puerto), y coordinarlo con Jorge. Estas variables son para las
JVM del **host**; si se copia el ejemplo al Compose original sin adaptarlo,
`localhost` apuntará al propio contenedor.

## 4. Gateway y frontends en la LAN

Jorge inicia su Gateway en `0.0.0.0:8090` (no `127.0.0.1` solamente), con
destinos accesibles **desde su PC**:

```text
MENU_URL=http://192.168.1.10:8080
PEDIDOS_URL=http://localhost:8082
COCINA_URL=http://192.168.1.12:8083
```

Para WebSocket, el destino de Cocina debe ser `ws://192.168.1.12:8083/ws` y
el de Pedidos `ws://localhost:8082/ws`; usar `/ws/cocina` y `/ws/pedidos`
como rutas **públicas** distintas. Ver el contrato completo en la guía de
Gateway. Verificar con `curl` **desde una PC distinta de la de Jorge**:

```bash
curl -i http://192.168.1.11:8090/api/productos
curl -i http://192.168.1.11:8090/pedidos
curl -i http://192.168.1.11:8090/ordenes
```

**Etapa A: conectividad básica sin Gateway.** Cada integrante puede levantar
su propia UI con `npm ci` y `npm run dev`. Defaults actuales: Menú
`http://<IP_DOUGLAS>:5174` → `localhost:8080`, Pedidos
`http://<IP_JORGE>:5176` → `localhost:8082` para Pedidos/WebSocket, y
Cocina `http://<IP_MARCO>:5173` → `localhost:8083` para Cocina/WebSocket.
Ejecutar **cada bloque en la PC indicada** (desde la raíz del repo), en una
terminal distinta de la de su backend:

```bash
# Douglas
cd frontend-menu
npm ci
npm run dev
```

```bash
# Jorge: su catálogo consulta al servicio de Douglas por LAN.
cd frontend-pedidos
npm ci
VITE_MENU_API_TARGET=http://192.168.1.10:8080 npm run dev
```

```bash
# Marco
cd frontend-cocina
npm ci
npm run dev
```

Si Jorge no define `VITE_MENU_API_TARGET`, su frontend intentará usar el
`localhost:8080` de **Jorge** y fallará. Los tres Vite escuchan en `0.0.0.0`.
Esta etapa valida microservicios y eventos, **pero todavía no prueba el
Gateway**.

**Etapa B: prueba real a través del Gateway (obligatoria para aceptarlo).**
Antes de la exposición aplicar y validar los ajustes de proxy detallados en
[`GUIA_GATEWAY_KDS.md`](GUIA_GATEWAY_KDS.md#4-adaptación-necesaria-de-los-frontends).
Configurar los proxies Vite/Nginx para que REST de Menú/Pedidos/Cocina pase
por `http://192.168.1.11:8090`. Las rutas `/ws` del navegador requieren
reescritura a `/ws/pedidos` o `/ws/cocina` **según la UI**. El `vite.config.ts`
de Menú quita `/api`; evitar quitarlo dos veces. Las UIs de Pedidos y Cocina
usan WebSocket del mismo origen por defecto. No basta cambiar una variable
REST y dar por validado STOMP: verificar la conexión de cada pantalla en la
pestaña Network/WS del navegador. También pueden definir direcciones
absolutas `VITE_WS_URL` para una prueba concreta, pero entonces ajustar
`WS_ALLOWED_ORIGINS` y CORS según los orígenes reales.

La imagen Nginx de cada frontend tiene upstreams `ms-*` de **Compose local**.
En esta topología el camino más sencillo es Vite en cada PC; si quieren servir
imágenes Docker de las UIs, primero preparen sus upstreams de LAN/Gateway y
prueben que Nginx inicia y que el navegador llega a los destinos. **El Compose
actual completo no es un despliegue distribuido listo para usar.**

## 5. Ensayo funcional y evidencia

Probar primero por API **vía Gateway** para diagnosticar, después repetir las
acciones con las **tres pantallas** para la exposición:

1. Douglas crea una categoría activa y un producto disponible en su UI;
   registrar el `id` real del producto. Una prueba técnica equivalente:

   ```bash
   GATEWAY=http://192.168.1.11:8090
   curl -sS -X POST "$GATEWAY/api/categorias" -H 'Content-Type: application/json' \
     -d '{"nombre":"Prueba LAN KDS","activo":true}'
   # Usar en categoriaId el id devuelto, no asumir que es 1.
   curl -sS -X POST "$GATEWAY/api/productos" -H 'Content-Type: application/json' \
     -d '{"nombre":"Hamburguesa LAN","descripcion":"Prueba de red","precio":4.50,"disponible":true,"categoriaId":<ID_CATEGORIA>}'
   ```

2. Jorge selecciona ese producto en la UI de Pedidos, envía el pedido y
   anota `id` y `numeroPedido`. La prueba API equivalente es `POST
   /pedidos` con `{"items":[{"productoId":<ID_PRODUCTO>,"cantidad":2}]}`;
   debe responder `201`. Si da `404`/error de catálogo, comprobar
   `MENU_BASE_URL` **desde Pedidos**, no desde el navegador.
3. Marco observa en Cocina la comanda `PENDIENTE` **sin recargar** (llegada de
   `pedido.creado` por RabbitMQ y `PEDIDO_CREADO` por STOMP). Si no aparece,
   comprobar que ambas apps usan **el mismo broker** y las colas correctas.
4. Marco pulsa «Iniciar preparación»; el tablero muestra `PREPARANDO` y la
   UI de Jorge refleja el estado. Repetir «Marcar como listo» y comprobar
   `LISTO` en ambos. Cocina publica `pedido.estado.actualizado` y Pedidos
   consume en **su propia cola**; no usar la cola de Cocina para Pedidos.
5. Repetir con otro pedido y mirar Network en cada UI: REST por el Gateway o
   su proxy local; WebSocket de Pedidos en `/ws/pedidos` y el de Cocina en
   `/ws/cocina` en el Gateway. Capturar pantallas y anotar tiempos de llegada,
   errores y versiones/commit usados por las tres PC.

Los comandos son apoyo técnico, **no reemplazan la demostración visual**.
RabbitMQ Management es diagnóstico, no interfaz para crear pedidos.

## 6. Problemas frecuentes

| Síntoma | Revisar |
|---|---|
| `localhost` funciona en una PC, desde otra no | IP real, firewall, servicio escuchando en `0.0.0.0`, puerto correcto |
| Gateway devuelve `404` para `/api/productos` | `Path` y `StripPrefix`/`RewritePath` de Menú; evitar quitar `/api` dos veces |
| Gateway devuelve `502/503` | Desde la PC de Jorge: conectividad con Douglas `:8080` y Marco `:8083`; URLs destino, no nombres DNS Compose |
| Pedidos no puede crear con un producto | `MENU_BASE_URL` en la JVM de Jorge, disponibilidad/id del producto |
| La comanda no llega a Cocina | Host/puerto/credenciales del **mismo** RabbitMQ; exchange `kds.exchange`, routing key `pedido.creado` y conexión del consumidor |
| Pedidos no refleja estado | Su cola propia enlazada a `pedido.estado.actualizado`; logs de Pedidos |
| REST funciona pero no hay tiempo real | Distinguir `/ws/pedidos` de `/ws/cocina`, rewrite a `/ws` y upgrade WebSocket; revisar STOMP y `WS_ALLOWED_ORIGINS` |
| Nginx no encuentra `ms-menu` o `ms-pedidos` | Esos nombres sólo existen en la red Docker local; usar Vite o configurar upstreams LAN |
| Funciona desde `curl`, pero no desde UI | Mirar URL real, CORS/origin, proxy Vite/Nginx y errores de Network/Console |

### Cierre

Cada integrante detiene **sólo sus procesos** de host al terminar. Los
contenedores locales se paran con `docker compose -f compose.integration.yaml
stop <nombre-del-servicio>`; conservar volúmenes. No ejecutar `down -v`, no
resetear datos de compañeros y no publicar contraseñas ni el PDF en Git.
