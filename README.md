# KDS Microservices — Kitchen Display System

Sistema distribuido de **pantalla de cocina (Kitchen Display System)** para la gestión
de pedidos de un restaurante. El KDS sustituye al papel de comanda: el personal de caja
registra el pedido, la cocina lo visualiza en tiempo real y marca su avance
(`PENDIENTE → PREPARANDO → LISTO`), y la caja recibe esas actualizaciones de forma
inmediata sin recargar la página.

Construido con **Java 21 + Spring Boot**, **RabbitMQ**, **PostgreSQL**, **WebSockets/STOMP**
y **React**, desplegado íntegramente con **Docker Compose**.

---

## 1. Presentación del proyecto

El problema que resuelve el KDS es la **pérdida de información y los retardos en la
coordinación entre sala y cocina**. En un servicio real:

- La comanda escrita a mano llega tarde, se transcribe con errores y se pierde.
- Nadie sabe en qué estado está cada pedido sin preguntar o asomarse a la cocina.
- La caja no se entera de que un plato está listo hasta que alguien se lo dice.

Este proyecto automatiza ese circuito con eventos:

1. **Caja** (`frontend-pedidos`) registra un pedido compuesto por productos del catálogo.
2. **ms-pedidos** lo persiste y publica el evento `pedido.creado` en RabbitMQ.
3. **Cocina** (`frontend-cocina`) recibe el evento y muestra la orden en el panel.
4. La cocina marca la orden como *en preparación* o *lista*; **ms-display-cocina**
   publica `pedido.estado.actualizado`.
5. **ms-pedidos** actualiza el pedido y notifica a la caja por WebSocket, que refleja
   el nuevo estado sin recargar.

Todo el intercambio entre microservicios es **asíncrono y desacoplado**: un servicio
no necesita estar disponible en el mismo instante que otro, y el broker absorbe los
picos de carga.

---

## 2. Arquitectura

El sistema se compone de **tres microservicios de negocio**, tres bases de datos
independientes, un API Gateway, un broker de mensajes, tres frontends y una
infraestructura de soporte.

```
   Navegador
      │  HTTP / WebSocket
      ▼
┌──────────────────────────────────────────────────────────────┐
│  API Gateway (Spring Cloud Gateway)  :8080                   │
│  /productos /categorias /auth /usuarios → ms-menu            │
│  /pedidos                              → ms-pedidos          │
│  /ordenes                               → ms-display-cocina   │
│  /ws/pedidos  /ws/cocina                → backends (/ws)      │
└───┬───────────────┬──────────────────────┬───────────────────┘
    │               │                      │
    ▼               ▼                      ▼
┌──────────┐  ┌─────────────┐    ┌────────────────────┐
│ ms-menu  │  │ ms-pedidos  │    │ ms-display-cocina  │
│          │  │             │    │                    │
│ catálogo │  │ pedidos    │    │ ordenes de cocina  │
│ usuarios │  │ (caja)     │    │ (KDS)              │
└────┬─────┘  └──────┬──────┘    └─────────┬──────────┘
     │                │                     │
     │        RabbitMQ (exchange kds.exchange)                │
     │   pedido.creado ──────────▶ cocina.pedidos.queue       │
     │   pedido.estado.actualizado ◀── pedidos.cocina.queue  │
     │                                                      │
┌────┴─────┐      ┌─────────────┐      ┌─────────────┐      │
│ menu_db  │      │ pedidos_db  │      │ cocina_db   │      │
└──────────┘      └─────────────┘      └─────────────┘      │
   PostgreSQL 16    PostgreSQL 16       PostgreSQL 16

  Frontends React 18 + Vite 6 (servidos por Nginx):
    frontend-menu  :5174   frontend-cocina :5175   frontend-pedidos :5176
```

| Componente | Tecnología | Responsabilidad |
|---|---|---|
| `ms-menu` | Java 21 · Spring Boot 4.1 | Catálogo (categorías y productos), **cuentas de usuario**, roles y emisión de tokens JWT. Es el único emisor de tokens. |
| `ms-pedidos` | Java 21 · Spring Boot 4.1 | Pedidos y control de caja. Valida cada producto contra `ms-menu`, publica `pedido.creado` y consume `pedido.estado.actualizado`. |
| `ms-display-cocina` | Java 21 · Spring Boot 4.1 | Panel de cocina. Consume `pedido.creado`, guarda la orden y publica los cambios de estado. |
| `api-gateway` | Java 21 · Spring Cloud Gateway | Punto de entrada único. Enruta REST y WebSocket. No contiene lógica de negocio ni valida tokens. |
| `menu_db`, `pedidos_db`, `cocina_db` | PostgreSQL 16 | Una base de datos **independiente por microservicio**. Ningún servicio comparte esquema con otro. |
| `rabbitmq` | RabbitMQ 3 (management) | Bus de eventos. Exchange `kds.exchange` (tipo *topic*) con una cola propia por consumidor. |
| `frontend-menu` | React 18 + Vite 6 | Gestión del catálogo, **alta de usuarios y asignación de roles**. |
| `frontend-pedidos` | React 18 + Vite 6 | Caja: armado del pedido y seguimiento en tiempo real. |
| `frontend-cocina` | React 18 + Vite 6 | KDS: bandeja de órdenes y avance de estados. |

**Comunicaciones asíncronas (RabbitMQ).** El bus de eventos cubre la comunicación
**asíncrona entre Pedidos y Cocina**, es decir, el ciclo de vida del pedido: un único
exchange de tipo *topic* enruta por *routing key* y cada consumidor tiene su cola.

| Routing key | Productor | Cola | Consumidor |
|---|---|---|---|
| `pedido.creado` | `ms-pedidos` | `cocina.pedidos.queue` | `ms-display-cocina` |
| `pedido.estado.actualizado` | `ms-display-cocina` | `pedidos.cocina.queue` | `ms-pedidos` |

**Consulta del catálogo (REST síncrono).** La relación entre Pedidos y Menú es de
otro tipo: cuando se registra un pedido, `ms-pedidos` **consulta de forma síncrona**
el catálogo de `ms-menu` mediante REST (`GET /productos/{id}`) para obtener el nombre,
el precio y la disponibilidad de cada producto, y rechaza el pedido si algún producto
no está disponible. No hay eventos ni cola entre ambos servicios: la consulta es
inmediata y forma parte de la transacción de alta del pedido.

**Tiempo real (WebSockets/STOMP).** `ms-pedidos` y `ms-display-cocina` exponen un
endpoint STOMP en `/ws` y publican en los topics `/topic/pedidos` y `/topic/cocina`
respectivamente. El Gateway los publica como `/ws/pedidos` y `/ws/cocina`.

> Este despliegue ejecuta **todos los componentes en una sola máquina** mediante Docker
> Compose. La variante opcional de un microservicio por equipo físico está documentada
> aparte en [`docs/GUIA_PRUEBA_KDS_TRES_PC.md`](docs/GUIA_PRUEBA_KDS_TRES_PC.md).

---

## 3. Requisitos previos

| Requisito | Detalle |
|---|---|
| **Git** | Cualquier versión reciente, para clonar el repositorio. |
| **Docker Engine** o **Docker Desktop** | Docker Desktop incluye Engine. Linux: Engine + plugin `docker-compose`. |
| **Docker Compose v2** | Comando `docker compose` (no `docker-compose`). Verificar con `docker compose version`. |

**No es necesario instalar Java ni Node.js.** Las cuatro aplicaciones Java y los tres
frontends React se **compilan dentro de sus propias imágenes Docker** (imágenes
`maven:3.9.9-eclipse-temurin-21` y `node:22-alpine`), y el runtime usa imágenes
`eclipse-temurin:21-jre` y `nginx`. El único requisito adicional es que Docker tenga
acceso a Internet para descargar las imágenes base y las dependencias durante la
compilación.

> Java 21 y Node.js solo hacen falta si se desea compilar o ejecutar alguna pieza
> **fuera** de Docker (por ejemplo, la herramienta de métricas de la sección 11).

Recursos aproximados: 8 GB de RAM libres y 4 GB de disco para las imágenes y los volúmenes.

---

## 4. Instalación paso a paso

### 4.1 Clonar el repositorio

```bash
git clone https://github.com/Doomblas/kds-microservicios.git
cd kds-microservicios
```

### 4.2 Crear el fichero de configuración

```bash
cp .env.example .env
```

### 4.3 Configurar las variables necesarias

Edita `.env` y establece **como mínimo** `ADMIN_EMAIL` y `ADMIN_PASSWORD` (ver la
sección 5). Las demás variables tienen valores por defecto pensados para desarrollo
local y exposición.

```bash
nano .env      # o: code .env   /  notepad .env
```

### 4.4 Levantar el sistema

```bash
docker compose -f compose.integration.yaml up --build -d
```

La primera compilación descarga las imágenes base y todas las dependencias, y puede
tardar varios minutos. Las siguientes reutilizan la caché de Docker.

### 4.5 Verificar el arranque

```bash
docker compose -f compose.integration.yaml ps
```

Deben aparecer **11 contenedores** en estado `Up`:

| Servicio | Imagen / origen | Puerto en el host |
|---|---|---|
| `menu-db` | `postgres:16` | 5434 |
| `cocina-db` | `postgres:16` | 5435 |
| `pedidos-db` | `postgres:16` | 5436 |
| `rabbitmq` | `rabbitmq:3-management` | 5673 (AMQP), 15673 (consola) |
| `ms-menu` | `./ms-menu` | 8081 |
| `ms-pedidos` | `./ms-pedidos` | 8085 |
| `ms-display-cocina` | `./ms-display-cocina` | 8084 |
| `api-gateway` | `./api-gateway` | 8080 |
| `frontend-menu` | `./frontend-menu` | 5174 |
| `frontend-cocina` | `./frontend-cocina` | 5175 |
| `frontend-pedidos` | `./frontend-pedidos` | 5176 |

> **Sobre los healthchecks:** 8 de los 11 contenedores declaran comprobación de
> salud (las tres bases de datos y RabbitMQ mediante `healthcheck` en el Compose; el
> Gateway y los tres frontends mediante `HEALTHCHECK` en su Dockerfile) y por eso
> `docker compose ps` los muestra como `healthy`. Los tres microservicios de negocio
> **no declaran `HEALTHCHECK`** y aparecen simplemente como `Up`. Para comprobar su
> funcionamiento se usan sus endpoints REST (sección 9): `ms-pedidos` y
> `ms-display-cocina` exponen `/actuator/health`, pero **`ms-menu` no incluye
> `spring-boot-starter-actuator` y, por tanto, no expone `/actuator/health`**; para
> comprobarlo basta a pedir `/auth/me` y verificar que responde `401` (servidor vivo,
> petición sin autenticar).

### 4.6 Comprobar que el Gateway responde

```bash
curl -s http://localhost:8080/actuator/health
```

Respuesta esperada: `{"groups":["liveness","readiness"],"status":"UP"}`.

---

## 5. Configuración del administrador inicial

`ms-menu` es el único servicio que emite tokens JWT y guarda las cuentas (tabla
`usuario` de `menu_db`). Para que exista la **primera** cuenta de administrador se
utiliza un *bootstrap* que se ejecuta al arrancar el servicio.

En `.env` hay que definir:

| Variable | Obligatoria | Descripción |
|---|---|---|
| `ADMIN_EMAIL` | **Sí** | Correo electrónico de la cuenta de administrador inicial. |
| `ADMIN_PASSWORD` | **Sí** | Contraseña de esa cuenta. **Mínimo 8 caracteres.** |
| `ADMIN_NAME` | No | Nombre visible de la cuenta. Por defecto `Administrador KDS`. |

Reglas del bootstrap (`AdminBootstrap`):

- Si `ADMIN_EMAIL` o `ADMIN_PASSWORD` están vacíos, **no se crea ninguna cuenta** y
  el sistema quedará sin ningún administrador: nadie podrá iniciar sesión ni asignar
  roles. Esta es la causa más habitual de «no puedo entrar».
- Si la contraseña tiene menos de 8 caracteres, el bootstrap se omite.
- Solo se crea la cuenta **si todavía no existe ningún `ADMIN`** y si ese correo no
  está en uso. Es idempotente: reiniciar el stack no duplica el administrador.
- El rol `ADMIN` **no se puede asignar desde la API ni desde la interfaz**; existe
  únicamente por este bootstrap.

```bash
# Ejemplo de valores para .env (sustituye por los tuyos, no reutilices estos)
ADMIN_EMAIL=tu-correo@ejemplo.com
ADMIN_PASSWORD=una-clave-de-al-menos-8-caracteres
ADMIN_NAME=Administrador KDS
```

> No guardes credenciales reales en el repositorio: `.env` está en `.gitignore`.
> Las claves de `.env.example` son únicamente para desarrollo local y exposición.

El resto de variables disponibles (todas con valor por defecto razonable):

| Variable | Para qué sirve |
|---|---|
| `POSTGRES_PASSWORD` | Contraseña de las tres bases de datos PostgreSQL. |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Credenciales del broker. |
| `JWT_SECRET` | Secreto compartido por los tres backends para firmar y validar los JWT (HS256). **Debe tener al menos 32 bytes.** Cámbialo en cualquier entorno real. |
| `JWT_EXPIRATION_MINUTES` | Duración de la sesión en minutos (30 por defecto). |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | Orígenes permitidos por CORS. |

---

## 6. Acceso a las interfaces

| Interfaz | URL | Usuario |
|---|---|---|
| **Menú / Administración** | <http://localhost:5174> | `ADMIN`, `GESTOR_MENU` |
| **Cocina (KDS)** | <http://localhost:5175> | `COCINA`, `ADMIN` |
| **Pedidos (Caja)** | <http://localhost:5176> | `CAJA`, `ADMIN` |
| **API Gateway** | <http://localhost:8080> | — (enruta; no sirve interfaz) |
| **Consola de RabbitMQ** | <http://localhost:15673> | `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` de `.env` |

Puertos **directos de cada microservicio**, publicados solo para diagnóstico y
comparación. El flujo integrado de los frontends pasa siempre por el Gateway:

| Servicio | URL directa |
|---|---|
| `ms-menu` | <http://localhost:8081> (Swagger en `/swagger-ui.html`) |
| `ms-pedidos` | <http://localhost:8085/pedidos> |
| `ms-display-cocina` | <http://localhost:8084/ordenes> (Swagger en `/swagger-ui.html`) |

---

## 7. Uso inicial

### 7.1 Iniciar sesión como administrador

1. Abre <http://localhost:5174>.
2. En «Iniciar sesión», usa el `ADMIN_EMAIL` y el `ADMIN_PASSWORD` que definiste en
   `.env`.
3. El token JWT queda guardado en el navegador (`localStorage`) y se envía en la
   cabecera `Authorization` de cada petición. Al abrir la aplicación, el frontend
   valida la sesión contra `/auth/me`; esa llamada **solo comprueba** que el token
   sigue siendo válido y que el rol no ha cambiado: **no renueva el token ni amplía
   su vigencia**. Cuando este caduca (30 minutos por defecto) hay que iniciar sesión
   de nuevo.

### 7.2 Registrar usuarios

El registro es público desde la pantalla «Crear cuenta» del frontend de Menú
(`POST /auth/register`, accesible con el prefijo `/api` en ese frontend). Solo se
piden **nombre, correo y contraseña**.

La cuenta recién creada nace **sin rol** y en estado `PENDIENTE`. Mientras tanto, la
interfaz muestra una pantalla de «espera de asignación de rol» y el acceso a los
módulos queda denegado.

### 7.3 Asignar roles

Solo un `ADMIN` puede hacerlo, y solo desde el frontend de Menú:

1. Pulsa el botón **«Usuarios»** de la cabecera (visible únicamente para `ADMIN`).
2. Busca la cuenta pendiente.
3. Asígnale uno de los roles asignables: `GESTOR_MENU`, `CAJA` o `COCINA`.
   (El rol `ADMIN` no es asignable desde la interfaz, por diseño.)
4. Al asignar un rol, la cuenta pasa automáticamente de `PENDIENTE` a `ACTIVO`.
5. Opcionalmente, usa la gestión de estado para bloquear (`BLOQUEADO`) o reactivar una
   cuenta. No se puede bloquear la propia cuenta ni dejar el sistema sin ningún
   `ADMIN` activo.

Roles disponibles: `ADMIN`, `GESTOR_MENU`, `CAJA`, `COCINA`.
Estados de cuenta: `PENDIENTE`, `ACTIVO`, `BLOQUEADO`.

### 7.4 Acceder a cada módulo

| Módulo | Inicia sesión en | Rol necesario |
|---|---|---|
| Catálogo y usuarios | <http://localhost:5174> | `ADMIN` (gestión de usuarios) o `GESTOR_MENU` (catálogo) |
| Cocina | <http://localhost:5175> | `COCINA` (o `ADMIN`) |
| Caja | <http://localhost:5176> | `CAJA` (o `ADMIN`) |

Si una cuenta accede a un módulo sin permiso, la interfaz muestra una pantalla de
**acceso denegado**.

### 7.5 Permisos por módulo (resumen)

| Recurso | `ADMIN` | `GESTOR_MENU` | `CAJA` | `COCINA` |
|---|---|---|---|---|
| `GET /productos`, `GET /categorias` | Sí | Sí | Sí | No |
| `POST/PUT/PATCH /productos`, `/categorias` | Sí | Sí | No | No |
| `/pedidos/**` | Sí | No | Sí | No |
| `/ordenes/**` | Sí | No | No | Sí |
| `/usuarios/**` | Sí | No | No | No |

---

## 8. Prueba funcional de extremo a extremo

Verifica que la cadena completa funciona con el sistema recién desplegado. Necesitas
dos ventanas (o dos navegadores) y una cuenta `COCINA` y otra `CAJA`.

1. **Caja** (<http://localhost:5176>): inicia sesión con la cuenta `CAJA`.
   En el panel de catálogo, elige uno o varios productos, ajusta las cantidades y
   envíalo. Aparecerá una confirmación con el número de pedido (`ORD-…`).
2. **Cocina** (<http://localhost:5175>): inicia sesión con la cuenta `COCINA`. La
   orden aparece en la bandeja en estado *pendiente* en cuanto `ms-pedidos` publica
   `pedido.creado` en RabbitMQ. No hace falta recargar la página.
3. En Cocina, pulsa **«Iniciar preparación»** en la tarjeta del pedido. El estado pasa
   a *en preparación* y `ms-display-cocina` publica `pedido.estado.actualizado`.
4. Vuelve a la ventana de **Caja**: el pedido cambia a `PREPARANDO` de forma
   automática, gracias al WebSocket `/ws/pedidos`. Si no lo ves, comprueba que la
   ventana de Caja siga abierta y conectada (el indicador de la cabecera).
5. En Cocina, pulsa **«Marcar como listo»**. En Caja el pedido pasa a `LISTO` de
   nuevo sin recargar.

El recorrido de estados completo es `PENDIENTE → PREPARANDO → LISTO`. La consola de
RabbitMQ (<http://localhost:15673>) permite ver las colas y los mensajes en
tránsito para comprobar la parte asíncrona.

**Comprobación rápida por API** (opcional), sin navegador:

```bash
# 1. Autenticarse y guardar el token en una variable
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"<ADMIN_EMAIL>","password":"<ADMIN_PASSWORD>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 2. Crear un pedido y quedarse con su id
PEDIDO=$(curl -s -X POST http://localhost:8080/pedidos \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"items":[{"productoId":1,"cantidad":1}]}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# 3. Ver la orden correspondiente en cocina
curl -s http://localhost:8080/ordenes -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; [print(o['id'], o['estado']) for o in json.load(sys.stdin) if o['pedidoId']==$PEDIDO]"

# 4. Ver el estado del pedido en caja
curl -s http://localhost:8080/pedidos/$PEDIDO/estado -H "Authorization: Bearer $TOKEN"
```

---

## 9. Comandos de mantenimiento

Todos se ejecutan desde la raíz del repositorio.

```bash
# Ver el estado de los 11 contenedores
docker compose -f compose.integration.yaml ps

# Seguir los logs en tiempo real de todos los servicios
docker compose -f compose.integration.yaml logs -f

# Logs de un servicio concreto (últimas 100 líneas)
docker compose -f compose.integration.yaml logs --tail=100 ms-pedidos

# Reconstruir una imagen y recrear su contenedor
docker compose -f compose.integration.yaml up --build -d ms-pedidos

# Reconstruir todo desde cero
docker compose -f compose.integration.yaml up --build -d

# Detener el sistema CONSERVANDO los datos
docker compose -f compose.integration.yaml down

# Detener y volver a levantar
docker compose -f compose.integration.yaml down
docker compose -f compose.integration.yaml up -d

# Reiniciar un servicio concreto
docker compose -f compose.integration.yaml restart ms-menu

# Eliminar imágenes construidas (no afecta a los datos)
docker compose -f compose.integration.yaml down --rmi local

# Estado de salud por API
curl -s http://localhost:8080/actuator/health    # Gateway
curl -s http://localhost:8085/actuator/health    # ms-pedidos
curl -s http://localhost:8084/actuator/health    # ms-display-cocina

# ms-menu no expone /actuator/health (no incluye actuator):
# un 401 en /auth/me confirma que el servicio está vivo y rechaza peticiones sin token
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/auth/me
```

> **Importante:** `docker compose down` **conserva** los volúmenes con las bases de
> datos y RabbitMQ, de modo que los pedidos y el catálogo sobreviven a un reinicio.
> El comando `docker compose down -v` **elimina los volúmenes y todos los datos**:
> no lo uses salvo que quieras empezar de cero.

---

## 10. Solución de problemas

### 10.1 «Ports are already allocated» / puertos ocupados

Otro proceso está usando uno de los puertos del host. Identifícalo y libera el que
necesites, o cambia el puerto en el `ports:` del servicio en
`compose.integration.yaml` (por ejemplo `"5176:80"` → `"8087:80"`).

```bash
# Ver qué ocupa los puertos del KDS
sudo ss -ltnp | grep -E ':(5174|5175|5176|8080|8081|8084|8085|5434|5435|5436|5673|15673)\b'
```

### 10.2 Algún servicio no arranca

```bash
# Ver qué contenedores están realmente arriba
docker compose -f compose.integration.yaml ps -a

# Leer el error de arranque
docker compose -f compose.integration.yaml logs --tail=200 ms-menu
```

Causas frecuentes:

- **El Gateway no está listo**: los frontends dependen de él (`condition:
  service_healthy`) y esperan su arranque. Es normal que tarden unos segundos más.
- **Falta el administrador**: si `ms-menu` arrancó pero no puedes iniciar sesión,
  revisa `ADMIN_EMAIL` y `ADMIN_PASSWORD` en `.env` (sección 5). El bootstrap solo
  actúa al arrancar el servicio: corrige `.env` y reinicia `ms-menu`.
- **Base de datos no conectada**: los tres backends dependen de su PostgreSQL con
  `condition: service_healthy`; espera a que la base esté `healthy`.

### 10.3 Credenciales incorrectas o «sesión expirada»

- El login es `POST /auth/login` con **correo y contraseña**. El rol no se elige en el
  formulario: se asigna después, desde el frontend de Menú.
- `CredencialesInvalidasException` → revisa correo y contraseña.
- `CuentaBloqueadaException` (403) → la cuenta está `BLOQUEADO`; reactívala desde la
  gestión de usuarios.
- Una cuenta recién registrada sin rol entra en sesión pero ve la pantalla de
  «espera de asignación de rol».
- Si un `ADMIN` cambia el rol de una cuenta, el token emitido sigue siendo válido
  hasta expirar: **vuelve a iniciar sesión** para aplicar el nuevo rol.

### 10.4 Error de DNS durante la compilación

Síntoma: la compilación falla con `Unknown host repo.maven.apache.org`, `Temporary
failure in name resolution` o `wget: unable to resolve host address`.

Ocurre en algunas máquinas Linux con `systemd-resolved`: el *bridge* de Docker no
hereda el stub resolver del host y no resuelve los repositorios externos, por lo que
no se pueden descargar ni las dependencias de Maven ni los paquetes de npm.

Solución: usar el fichero Compose **opcional** que compila en la red del host.

```bash
docker compose -f compose.integration.yaml \
               -f compose.integration.dns-fallback.yaml up --build -d
```

Ese override **solo afecta a la fase de compilación**: no cambia puertos, variables
de entorno, bases de datos, volúmenes ni la red de ejecución. En Docker Desktop
(macOS y Windows) no hace falta, porque la red de las máquinas virtuales ya resuelve
correctamente. Detalle en
[`compose.integration.dns-fallback.yaml`](compose.integration.dns-fallback.yaml).

Si el daemon no permite red de host en las compilaciones (instalación rootless con
el driver `docker-container`), la alternativa es configurar el DNS **del daemon** en
`/etc/docker/daemon.json`:

```json
{ "dns": ["8.8.8.8", "1.1.1.1"] }
```

y reiniciar Docker (`sudo systemctl restart docker`). Ese ajuste es del equipo, no
del repositorio.

### 10.5 La cocina no recibe los pedidos

- Comprueba que `ms-pedidos` y `ms-display-cocina` están levantados y que
  `rabbitmq` está `healthy`.
- Revisa la cola en <http://localhost:15673> → *Queues and Streams*.
- Mira los logs de ambos servicios: `docker compose -f compose.integration.yaml logs -f ms-pedidos ms-display-cocina`.

### 10.6 La caja no se actualiza sola

- El WebSocket necesita estar conectado: el indicador de la cabecera de Caja debe
  indicar «En vivo». Si indica «Reconectando», la conexión STOMP falló; recarga la
  página.
- El estado se refleja igualmente al recargar, aunque tarde unos segundos.

---

## 11. Pruebas y métricas de propagación

El repositorio incluye una **herramienta de medición automática** de la latencia de
propagación de eventos, usada para los resultados del informe académico.

`tools/metricas-kds/` es una **utilidad de línea de comandos**, no un microservicio:
no se despliega con Docker Compose, no se añade al Gateway y no forma parte del
sistema en ejecución. Mide, a través del API Gateway, cuánto tarda un pedido en
aparecer en cocina y cuánto tardan los cambios de estado en volver a la caja;
genera CSV, JSON, gráficas PNG y los textos del informe.

```bash
cd tools/metricas-kds
KDS_ADMIN_EMAIL=<correo-admin> \
KDS_ADMIN_PASSWORD=<contraseña-admin> \
KDS_NUM_PRUEBAS=20 \
./run.sh
```

Requiere **JDK 21** en el equipo (esta es la única parte del proyecto que no se
ejecuta dentro de Docker). Los resultados se escriben en
`tools/metricas-kds/resultados/`.

Documentación completa, variables de entorno y pruebas:
[`tools/metricas-kds/README.md`](tools/metricas-kds/README.md).

Las pruebas automatizadas de cada microservicio se ejecutan desde su propio módulo:

```bash
(cd ms-menu           && ./mvnw test)   # 54 tests
(cd ms-pedidos        && ./mvnw test)   # 12 tests
(cd ms-display-cocina && ./mvnw test)   # 31 tests
```

Cada módulo se compila y se prueba en una subshell para que los comandos puedan
copiarse y ejecutarse uno tras otro desde la raíz del repositorio.

---

## 12. Documentación adicional

| Guía | Contenido |
|---|---|
| [`docs/GUIA_INTEGRACION_DISPLAY_COCINA.md`](docs/GUIA_INTEGRACION_DISPLAY_COCINA.md) | Guía de integración de `ms-display-cocina`: arquitectura, contratos, despliegue y pruebas entre módulos. |
| [`docs/GUIA_AUTENTICACION_KDS.md`](docs/GUIA_AUTENTICACION_KDS.md) | Autenticación JWT, roles, estados de cuenta y gestión de usuarios. |
| [`docs/GUIA_GATEWAY_KDS.md`](docs/GUIA_GATEWAY_KDS.md) | Rutas del API Gateway, CORS, WebSockets y diagnóstico. |
| [`docs/GUIA_PRUEBA_KDS_TRES_PC.md`](docs/GUIA_PRUEBA_KDS_TRES_PC.md) | Variante **opcional** de la prueba con un microservicio por equipo físico. No es necesaria para la instalación normal. |
| [`tools/metricas-kds/README.md`](tools/metricas-kds/README.md) | Herramienta de medición de latencia de propagación. |
| [`compose.integration.dns-fallback.yaml`](compose.integration.dns-fallback.yaml) | Override opcional para máquinas Linux con problemas de DNS en la compilación. |

---

## Licencia y uso

Proyecto académico. Las claves incluidas en `.env.example` son **únicamente para
desarrollo local y exposición**: sustitúyelas antes de cualquier despliegue real.
