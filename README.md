# kds-microservicios

Distributed Kitchen Display System built with Java, Spring Boot, RabbitMQ,
WebSockets, PostgreSQL and Docker.

## Entorno de integración disponible

El entorno compartido levanta `ms-menu`, `ms-pedidos`, `ms-display-cocina`,
sus bases de datos y RabbitMQ. La responsabilidad de este equipo sigue siendo
`ms-display-cocina` y su frontend; Menu y Pedidos son dependencias mantenidas
por sus respectivos responsables.

```bash
cp .env.example .env
docker compose -f compose.integration.yaml up --build -d
```

- Frontend Menu: <http://localhost:5174>
- Frontend Cocina: <http://localhost:5175>
- Frontend Pedidos (caja): <http://localhost:5176>
- API Gateway: <http://localhost:8080>
- API Menu (diagnóstico): <http://localhost:8081>
- API Cocina (diagnóstico): <http://localhost:8084/ordenes>
- API Pedidos (diagnóstico): <http://localhost:8085/pedidos>
- RabbitMQ Management: <http://localhost:15673>

Las llamadas REST y WebSocket de los frontends pasan por el API Gateway. Los
puertos directos de cada microservicio se mantienen temporalmente para
diagnóstico y comparación, pero el flujo integrado debe usar el Gateway.

Rutas principales del Gateway: `/productos/**` y `/categorias/**` hacia Menu;
`/pedidos/**` hacia Pedidos; `/ordenes/**` hacia Cocina; `/auth/**` y
`/usuarios/**` hacia Menu; y los WebSocket `/ws/pedidos` y `/ws/cocina` (los
backends conservan su endpoint interno `/ws`).

## Autenticación, roles y usuarios

El sistema incorpora autenticación con JWT (HS256) y control de acceso por rol
en el backend. **No se crea un cuarto microservicio**: las cuentas, el rol y el
estado viven en la propia base de `ms-menu` (`menu_db`, tabla `usuario`), que ya
era el servicio administrativo del catálogo. `ms-pedidos` y `ms-display-cocina`
validan el token de forma *stateless* con el secreto compartido `JWT_SECRET`
y nunca acceden a la base de usuarios.

- Roles: `ADMIN`, `GESTOR_MENU`, `CAJA`, `COCINA`.
- Estados de cuenta (independientes del rol): `PENDIENTE`, `ACTIVO`, `BLOQUEADO`.
- El registro público (`POST /auth/register`) solo acepta nombre, email y
  contraseña: la cuenta nace `PENDIENTE` y sin rol. Cualquier rol enviado por el
  navegador se ignora.
- Contraseñas con BCrypt; el hash nunca se expone en la API.

| Endpoint | Acceso |
| --- | --- |
| `POST /auth/register` | Público |
| `POST /auth/login` | Público |
| `GET /auth/me` | Autenticado (cualquier rol) |
| `GET /usuarios`, `GET /usuarios/{id}` | ADMIN |
| `PATCH /usuarios/{id}/rol` | ADMIN (no permite `ADMIN`) |
| `PATCH /usuarios/{id}/estado` | ADMIN (no permite bloquear a sí mismo ni al último ADMIN activo) |

Permisos por módulo:

| Recurso | ADMIN | GESTOR_MENU | CAJA | COCINA |
| --- | --- | --- | --- | --- |
| `GET /productos`, `GET /categorias` | Sí | Sí | Sí | No |
| `POST/PUT/PATCH /productos`, `/categorias` | Sí | Sí | No | No |
| `/pedidos/**` | Sí | No | Sí | No |
| `/ordenes/**` | Sí | No | No | Sí |

Variables de entorno (ver `.env.example`): `JWT_SECRET`,
`JWT_EXPIRATION_MINUTES`, `CORS_ALLOWED_ORIGIN_PATTERNS` y, solo para
`ms-menu`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` y `ADMIN_NAME`, que crean el primer
ADMIN cuando todavía no existe ninguno.

Los tres frontends incorporate inicio de sesión, registro, cierre de sesión,
restauración de sesión contra `/auth/me`, pantalla de **espera de asignación de
rol** para las cuentas pendientes y pantalla de **acceso denegado** para los
roles sin permiso sobre el módulo. La gestión de usuarios y roles (buscar,
asignar rol, bloquear o activar) solo existe en el frontend de menú y solo es
visible para `ADMIN`.

Limitaciones conocidas y deliberadas:

- El handshake de los WebSocket STOMP (`/ws/pedidos`, `/ws/cocina`) se mantiene
  abierto para no romper el flujo en vivo existente; la protección real se
  aplica sobre toda la API REST operativa.
- Si un ADMIN cambia el rol o bloquea una cuenta, el token ya emitido sigue
  siendo criptográficamente válido hasta expirar. `/auth/me` refleja el estado
  real y los frontends cierran la sesión cuando detectan un cambio de
  permisos; por seguridad, tras un cambio de rol se debe iniciar sesión de nuevo.

La guía completa está en
[`docs/GUIA_AUTENTICACION_KDS.md`](docs/GUIA_AUTENTICACION_KDS.md).

La guía de despliegue, contratos y pruebas entre responsables está en
[`docs/GUIA_INTEGRACION_DISPLAY_COCINA.md`](docs/GUIA_INTEGRACION_DISPLAY_COCINA.md).
Para la siguiente etapa, consultar la
[`guía del Gateway`](docs/GUIA_GATEWAY_KDS.md) y la
[`prueba distribuida en tres PC`](docs/GUIA_PRUEBA_KDS_TRES_PC.md).
El Compose anterior es para **una sola máquina**: no configura automáticamente
las conexiones de red entre tres equipos.
Las claves de `.env.example` son únicamente para desarrollo local/exposición.

## Herramienta de métricas de propagación (herramienta de pruebas, no microservicio)

`tools/metricas-kds/` es una utilidad de línea de comandos en Java 21 que mide de
forma automática la latencia de propagación de eventos del KDS (pedido nuevo,
cambio a PREPARANDO y cambio a LISTO) a través del API Gateway, y genera los
artefactos del informe (CSV, JSON, gráficas PNG y textos). **No es un
microservicio**: no se despliega con Docker Compose ni se añade al Gateway.

```bash
cd tools/metricas-kds
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
KDS_ADMIN_EMAIL=... KDS_ADMIN_PASSWORD=... KDS_NUM_PRUEBAS=20 \
./run.sh
```

Los resultados se escriben en `tools/metricas-kds/resultados/`. Detalles en
[`tools/metricas-kds/README.md`](tools/metricas-kds/README.md).
