# Guía de integración — ms-display-cocina

> Documento de contexto para el equipo KDS.
> Microservicio: `ms-display-cocina` · Rama: `marco` · Contrato: KDS v1.0
> Última actualización: 2026-11-09

---

## 1. Propósito de este documento

Servir como punto de entrada para cualquier integrante que necesite:

- Entender qué hace `ms-display-cocina`.
- Saber qué contratos expone y consume.
- Ver qué está terminado y qué falta para la integración completa.
- Levantar el servicio y probarlo sin depender de los demás módulos.

La rama `marco` ya incluye `origin/develop`, por lo que un Pull Request hacia
`develop` **no elimina** el trabajo de `ms-menu`.

---

## 2. Rol del microservicio

`ms-display-cocina` es la pantalla de cocina. Su responsabilidad es:

1. Recibir los pedidos creados por `ms-pedidos` mediante `pedido.creado`.
2. Registrarlos en su propia base `cocina_db` con estado `PENDIENTE`.
3. Notificar en tiempo real al frontend de cocina por WebSocket.
4. Permitir avanzar el estado: `PENDIENTE -> PREPARANDO -> LISTO`.
5. Publicar `pedido.estado.actualizado` para que `ms-pedidos` (y caja)
   conozcan el avance.

No administra productos, precios, clientes ni pagos. `productoId` es una
referencia lógica; no hay foreign key hacia `menu_db`.

### Flujo general

```text
ms-pedidos ──pedido.creado──> RabbitMQ ──> ms-display-cocina
                                              │
                                              ├──> cocina_db
                                              └──> WebSocket /topic/cocina
                                                      │
                                                      v
                                               Frontend cocina
                                              │
                        PATCH /ordenes/{id}/estado
                                              │
                                              v
ms-pedidos <──pedido.estado.actualizado── RabbitMQ
```

---

## 3. Contratos

### 3.1 API REST

Base local: `http://localhost:8083`

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/ordenes` | Lista las órdenes ordenadas por fecha de recepción |
| `GET` | `/ordenes/{id}` | Consulta una orden por su id interno |
| `PATCH` | `/ordenes/{id}/estado` | Avanza el estado de la orden |

Respuesta de una orden:

```json
{
  "id": 1,
  "pedidoId": 1002,
  "numeroPedido": "ORD-1002",
  "estado": "PENDIENTE",
  "fechaRecepcion": "2026-11-09T14:25:00",
  "fechaInicio": null,
  "fechaFinalizacion": null,
  "items": [
    { "productoId": 1, "nombreProducto": "Hamburguesa Clasica", "cantidad": 2 }
  ]
}
```

Cambio de estado:

```bash
curl -X PATCH http://localhost:8083/ordenes/1/estado \
  -H "Content-Type: application/json" \
  -d '{"estado":"PREPARANDO"}'
```

Errores:

| Código | Caso |
|---|---|
| `400` | Estado inválido, falta el estado o se intenta asignar `PENDIENTE` |
| `404` | La orden o la ruta no existen |
| `409` | Transición no permitida (por ejemplo `LISTO -> PREPARANDO`) |
| `500` | Error inesperado |

Formato de error:

```json
{
  "timestamp": "2026-11-09T14:21:52.32",
  "status": 409,
  "error": "Conflict",
  "message": "Transicion de estado no permitida: LISTO -> PREPARANDO",
  "path": "/ordenes/1/estado"
}
```

Documentación interactiva:

- Swagger UI: `http://localhost:8083/swagger-ui.html`
- OpenAPI: `http://localhost:8083/v3/api-docs`
- Health: `http://localhost:8083/actuator/health`

> El frontend incluido en `frontend-cocina/` usa el proxy de Vite en desarrollo,
> por lo que no requiere habilitar CORS REST para esa configuración. Si se
> despliega en otro origen, configurar CORS en Gateway/backend o mantener un
> proxy reverso.

### 3.2 RabbitMQ

| Elemento | Valor |
|---|---|
| Exchange | `kds.exchange` (topic, durable) |
| Cola | `cocina.pedidos.queue` (durable) |
| Routing key consumida | `pedido.creado` |
| Routing key publicada | `pedido.estado.actualizado` |

#### Evento consumido: `pedido.creado`

```json
{
  "eventoId": "uuid",
  "evento": "pedido.creado",
  "pedidoId": 1002,
  "numeroPedido": "ORD-1002",
  "fechaCreacion": "2026-11-09T14:25:00",
  "items": [
    { "productoId": 1, "nombreProducto": "Hamburguesa Clasica", "cantidad": 2 }
  ]
}
```

Reglas:

- `eventoId`, `pedidoId` y `numeroPedido` son obligatorios.
- Debe incluir al menos un ítem.
- Cada ítem requiere `productoId`, `nombreProducto` y `cantidad > 0`.
- El mensaje debe publicarse con `contentType=application/json`.
- Es idempotente por `pedidoId`: un duplicado no crea otra orden ni vuelve a
  notificar por WebSocket.

#### Evento publicado: `pedido.estado.actualizado`

```json
{
  "eventoId": "uuid",
  "evento": "pedido.estado.actualizado",
  "pedidoId": 1002,
  "numeroPedido": "ORD-1002",
  "estado": "PREPARANDO",
  "fechaActualizacion": "2026-11-09T14:21:29.428971887"
}
```

Se publica después del commit de la transacción. Si RabbitMQ no está
disponible, el cambio en base de datos **no se revierte**; solo se registra el
error en el log. Actualmente no hay patrón outbox.

### 3.3 WebSocket

| Elemento | Valor |
|---|---|
| Endpoint | `/ws` (STOMP nativo, sin SockJS) |
| Tópico | `/topic/cocina` |
| Orígenes | `WS_ALLOWED_ORIGINS` (por defecto `*`) |

Mensaje `PEDIDO_CREADO`:

```json
{
  "tipo": "PEDIDO_CREADO",
  "pedidoId": 1002,
  "numeroPedido": "ORD-1002",
  "estado": "PENDIENTE",
  "items": [
    { "nombreProducto": "Hamburguesa Clasica", "cantidad": 2 }
  ]
}
```

Solo se emite al crear una orden nueva. Los cambios de estado no generan un
mensaje WebSocket en v1: el frontend debe actualizar su estado local o volver a
consultar el REST.

---

## 4. Base de datos

| Elemento | Valor |
|---|---|
| Base | `cocina_db` |
| Tablas | `orden_cocina`, `item_cocina` |
| Esquema | Generado por Hibernate (`ddl-auto=update`) |
| Restricción | `pedido_id` único |

Variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/cocina_db
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

---

## 5. Reglas de negocio implementadas

- [x] Crear la orden con estado `PENDIENTE` al recibir `pedido.creado`.
- [x] Copiar `productoId`, `nombreProducto` y `cantidad`.
- [x] Registrar `fechaRecepcion` desde el evento o con la hora actual.
- [x] Registrar `fechaInicio` al pasar a `PREPARANDO`.
- [x] Registrar `fechaFinalizacion` al pasar a `LISTO`.
- [x] Permitir solo `PENDIENTE -> PREPARANDO -> LISTO`.
- [x] Rechazar saltos y retrocesos con `409`.
- [x] Rechazar la asignación manual de `PENDIENTE` con `400`.
- [x] Hacer idempotente un `PATCH` al mismo estado.
- [x] Ignorar eventos duplicados por `pedidoId`.
- [x] Notificar por WebSocket después del commit.
- [x] No revertir la base si falla la publicación a RabbitMQ.

---

## 6. Cómo levantar el servicio

### 6.1 Con Docker (recomendado para el equipo)

```bash
docker run --name cocina-db \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=cocina_db \
  -p 5432:5432 \
  -d postgres:16

docker run --name kds-rabbit \
  -p 5672:5672 \
  -p 15672:15672 \
  -d rabbitmq:3-management
```

Instalar el plugin de administración si se usa la imagen básica:

```bash
docker exec kds-rabbit rabbitmq-plugins enable rabbitmq_management
```

Consola: `http://localhost:15672` (`guest` / `guest`).

### 6.2 Ejecutar el microservicio

```bash
cd ms-display-cocina

export DB_PASSWORD=postgres
export RABBITMQ_PASSWORD=guest

./mvnw spring-boot:run
```

O con el jar:

```bash
./mvnw -B clean package
java -jar target/ms-display-cocina-0.0.1-SNAPSHOT.jar
```

### 6.3 Variables de entorno

| Variable | Por defecto | Obligatoria |
|---|---|---|
| `SERVER_PORT` | `8083` | No |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cocina_db` | No |
| `DB_USERNAME` | `postgres` | No |
| `DB_PASSWORD` | — | Sí |
| `RABBITMQ_HOST` | `localhost` | No |
| `RABBITMQ_PORT` | `5672` | No |
| `RABBITMQ_USERNAME` | `guest` | No |
| `RABBITMQ_PASSWORD` | — | Sí |
| `RABBITMQ_VHOST` | `/` | No |
| `WS_ALLOWED_ORIGINS` | `*` | No |

---

## 7. Pruebas

```bash
cd ms-display-cocina
./mvnw -B clean test
```

Resultado actual:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Cobertura funcional:

- Servicio: creación, duplicados, transiciones, fechas e idempotencia.
- Controller: 200, 400, 404 y 409.
- Publicador de eventos y conversión JSON.
- Listener de `pedido.creado`.
- Rutas inexistentes devuelven 404.

### Publicar un `pedido.creado` de prueba

Con `RabbitTemplate` de Spring, o con cualquier cliente AMQP asegurando:

```text
exchange:     kds.exchange
routing key:  pedido.creado
contentType:  application/json
```

Sin `contentType=application/json`, el listener rechaza el mensaje.

---

## 8. Estado del trabajo

### Terminado y validado

- API REST completa.
- Persistencia en PostgreSQL.
- Consumo de `pedido.creado`.
- Publicación de `pedido.estado.actualizado`.
- WebSocket STOMP en `/ws` y `/topic/cocina`.
- Idempotencia, validaciones, transiciones y manejo de errores.
- Swagger/OpenAPI y Actuator.
- Dockerfiles multi-stage para `ms-menu`, `ms-display-cocina` y frontend cocina.
- Compose funcional para Menu + Cocina + PostgreSQLs + RabbitMQ + frontend.
- 22 tests.
- Validación de punta a punta con PostgreSQL y RabbitMQ reales:
  creación, duplicados, transiciones, eventos, WebSocket y errores.
- Build de las tres imágenes y prueba REST/WebSocket mediante el proxy Nginx.

### Pendiente para la integración completa

| Tarea | Depende de | Notas |
|---|---|---|
| Implementar/integrar `ms-pedidos` y publicar `pedido.creado` | Equipo de Pedidos | No está en la copia de trabajo que acompaña este Compose |
| Consumir `pedido.estado.actualizado` en Pedidos | Equipo de Pedidos | Actualizar estado y notificar a caja |
| Agregar rutas al API Gateway | Equipo de Gateway | `/ordenes/**` y `/ws` hacia `:8083` |
| Integrar `ms-pedidos` al Compose | Equipo de Pedidos | Agregar su DB, Dockerfile, healthcheck y variables |
| Desplegar frontend Cocina detrás del Gateway | Equipo de Gateway/Frontend | Ya tiene Nginx proxy para pruebas locales; falta validar Gateway |
| CORS/proxy para producción | Equipo de Gateway/Frontend | Configurar orígenes y URL pública según despliegue |
| Colección Postman | Equipo | `GET`, `GET/{id}`, `PATCH` |
| Medir propagación | Equipo | Pedido creado → visible; estado → Pedidos |
| Validar imágenes y stack Compose | Equipo | Build y arranque de Menu+Cocina+frontend ya comprobados |
| Integrar `ms-pedidos` al Compose compartido | Equipo de Pedidos | Falta su código/Dockerfile en esta copia; seguir el contrato y pasos de §9.1 |
| Considerar patrón outbox | Futuro | Evitar pérdida de eventos si RabbitMQ cae |

---

## 9. Guía de integración con los microservicios de los compañeros

### 9.0 Matriz de responsabilidades

| Componente | Publica | Consume | Base de datos |
|---|---|---|---|
| `ms-menu` | — | — | `menu_db` |
| `ms-pedidos` | `pedido.creado` | `pedido.estado.actualizado` | `pedidos_db` |
| `ms-display-cocina` | `pedido.estado.actualizado` | `pedido.creado` | `cocina_db` |
| API Gateway | — | — | — |
| Frontend cocina (`frontend-cocina/`) | — | REST + WebSocket | — |
| Caja | — | Notificación desde Pedidos | `caja_db` |

Regla de oro: **ningún servicio lee ni escribe la base de otro**. La
comunicación es por eventos o por REST a través del Gateway.

---

### 9.1 Integración con `ms-pedidos` (prioridad alta)

`ms-pedidos` es el único servicio con integración directa obligatoria.

#### A. Lo que Pedidos debe publicar: `pedido.creado`

```text
exchange:    kds.exchange (topic, durable)
routing key: pedido.creado
contentType: application/json
```

Payload esperado:

```json
{
  "eventoId": "uuid",
  "evento": "pedido.creado",
  "pedidoId": 1002,
  "numeroPedido": "ORD-1002",
  "fechaCreacion": "2026-11-09T14:25:00",
  "items": [
    { "productoId": 1, "nombreProducto": "Hamburguesa Clasica", "cantidad": 2 }
  ]
}
```

DTO sugerido en Pedidos:

```java
public record PedidoCreadoEvent(
        String eventoId,
        String evento,
        Long pedidoId,
        String numeroPedido,
        LocalDateTime fechaCreacion,
        List<Item> items) {

    public record Item(Long productoId, String nombreProducto, Integer cantidad) {}
}
```

Publicación con `RabbitTemplate`:

```java
rabbitTemplate.convertAndSend(
        "kds.exchange",
        "pedido.creado",
        evento);
```

#### B. Conversor JSON obligatorio en Pedidos

Si Pedidos no configura un conversor JSON, Spring AMQP usa
`SimpleMessageConverter`, que envía el objeto como Java serializado
(`application/x-java-serialized-object`). **Cocina rechaza ese mensaje.**

Con Spring Boot 4 / Jackson 3:

```java
@Bean
MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
}
```

Con Spring Boot 3 / Jackson 2:

```java
@Bean
MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
}
```

Spring Boot toma ese bean para `RabbitTemplate` y para el listener container.
Si se publica con un cliente distinto, hay que fijar explícitamente:

```java
MessageProperties props = new MessageProperties();
props.setContentType("application/json");
props.setContentEncoding("UTF-8");
```

No es necesario agregar el header `__TypeId__` para que Cocina lo reciba:
el listener usa el tipo concreto del método. Cocina sí lo agrega al publicar
sus eventos, pero un consumidor con tipo inferido puede ignorarlo.

#### C. Lo que Pedidos debe consumir: `pedido.estado.actualizado`

Pedidos debe declarar **su propia cola**. No debe consumir de
`cocina.pedidos.queue`, porque esa cola pertenece a Cocina.

```java
@Configuration
class RabbitConfig {

    @Bean
    TopicExchange kdsExchange() {
        return ExchangeBuilder.topicExchange("kds.exchange").durable(true).build();
    }

    @Bean
    Queue pedidosCocinaQueue() {
        return QueueBuilder.durable("pedidos.cocina.queue").build();
    }

    @Bean
    Binding pedidosCocinaBinding(Queue pedidosCocinaQueue, TopicExchange kdsExchange) {
        return BindingBuilder.bind(pedidosCocinaQueue)
                .to(kdsExchange)
                .with("pedido.estado.actualizado");
    }
}
```

Listener:

```java
@RabbitListener(queues = "pedidos.cocina.queue")
public void onEstadoActualizado(PedidoEstadoActualizadoEvent evento) {
    // 1. Buscar el pedido por evento.pedidoId()
    // 2. Actualizar su estado
    // 3. Notificar a caja si corresponde
}
```

DTO sugerido en Pedidos:

```java
public record PedidoEstadoActualizadoEvent(
        String eventoId,
        String evento,
        Long pedidoId,
        String numeroPedido,
        EstadoCocina estado,
        LocalDateTime fechaActualizacion) {
}
```

Headers reales que publica Cocina:

```text
contentType=application/json
contentEncoding=UTF-8
__TypeId__=com.kds.cocina.dto.PedidoEstadoActualizadoEvent
```

Si el conversor de Pedidos intenta usar el `__TypeId__` y la clase no existe
en su classpath, debe usar tipo inferido:

```java
converter.getJavaTypeMapper().setTypePrecedence(TypePrecedence.INFERRED);
```

O definir el DTO en su propio paquete y recibirlo tipado en el método. En ese
caso, el header se ignora y se usa el tipo del parámetro.

#### D. Reglas que Pedidos debe respetar

- `eventoId` debe ser único por emisión; sirve para idempotencia.
- `pedidoId` es el identificador de negocio y es único en Cocina.
- `items` no puede venir vacío.
- `cantidad` debe ser mayor que cero.
- `productoId` debe ser estable; no se reutilizan ids de productos eliminados.
- Si Pedidos reenvía el mismo `pedido.creado`, Cocina no duplica la orden.
- Los estados que recibirá son: `PREPARANDO` y `LISTO`. `PENDIENTE` nunca se
  publica porque es el estado inicial.

#### E. Verificación conjunta Pedidos ↔ Cocina

1. Levantar RabbitMQ, `ms-pedidos` y `ms-display-cocina`.
2. Crear un pedido en Pedidos.
3. Verificar en Cocina: `GET /ordenes` debe mostrar la orden en `PENDIENTE`.
4. Verificar el WebSocket: debe llegar `PEDIDO_CREADO`.
5. En Cocina, hacer `PATCH` a `PREPARANDO`.
6. Verificar en Pedidos que recibió `pedido.estado.actualizado`.
7. Repetir con `LISTO`.
8. Reenviar el mismo `pedido.creado` y confirmar que no se duplica.

---

### 9.2 Integración con `ms-menu`

No hay integración en tiempo de ejecución. Cocina **no llama** a `ms-menu` ni
lee `menu_db`.

- `productoId` es una referencia lógica.
- `nombreProducto` viaja como copia (snapshot) dentro de `pedido.creado`.
- Si el producto cambia de nombre después, las órdenes existentes conservan el
  nombre con el que se crearon.
- Es responsabilidad de `ms-pedidos` obtener el nombre desde `ms-menu` al crear
  el pedido.
- No se debe agregar una foreign key entre `cocina_db` y `menu_db`.

Único acuerdo necesario: **los `productoId` deben ser estables y no
reutilizarse**.

---

### 9.3 Integración con el API Gateway

Rutas requeridas:

| Ruta pública | Destino | Tipo |
|---|---|---|
| `/ordenes/**` | `ms-display-cocina:8083` | HTTP |
| `/ws` o `/ws/**` | `ms-display-cocina:8083` | WebSocket |
| `/swagger-ui/**`, `/v3/api-docs` | Opcional | HTTP |

Ejemplo con Spring Cloud Gateway:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: display-cocina
          uri: http://ms-display-cocina:8083
          predicates:
            - Path=/ordenes/**
        - id: display-cocina-ws
          uri: ws://ms-display-cocina:8083
          predicates:
            - Path=/ws/**
```

Si se usa descubrimiento de servicios, el esquema suele ser
`lb:ws://ms-display-cocina`. Verificar la versión del Gateway del equipo.

Si el Gateway es Nginx:

```nginx
location /ws {
    proxy_pass http://ms-display-cocina:8083;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

Consideraciones:

- El WebSocket debe conservar los headers `Upgrade` y `Connection`.
- Definir timeouts mayores que el intervalo de ping del cliente STOMP.
- El Gateway puede centralizar CORS; si no, hay que configurarlo en Cocina.
- Probar tanto `GET /ordenes` como la conexión `ws://gateway/ws`.

Verificación:

```bash
curl http://localhost:8080/ordenes
# y desde el frontend o Postman:
# ws://localhost:8080/ws  -> SUBSCRIBE /topic/cocina
```

---

### 9.4 Integración con el frontend de cocina

El frontend debe usar **STOMP nativo**, no SockJS.

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8083/ws',
  reconnectDelay: 5000,
});

client.onConnect = () => {
  client.subscribe('/topic/cocina', (frame) => {
    const evento = JSON.parse(frame.body);
    if (evento.tipo === 'PEDIDO_CREADO') {
      // agregar la orden a la pantalla
    }
  });
};

client.activate();
```

REST:

```js
const res = await fetch('http://localhost:8083/ordenes');
const ordenes = await res.json();

await fetch(`http://localhost:8083/ordenes/${id}/estado`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ estado: 'PREPARANDO' }),
});
```

Notas:

- Hoy **no hay CORS REST** en Cocina. Opciones:
  - Proxy de Vite: `/ordenes` y `/ws` hacia `http://localhost:8083`.
  - O agregar CORS en el backend antes de consumir desde otro origen.
- El WebSocket ya permite orígenes configurables con `WS_ALLOWED_ORIGINS`.
- `PEDIDO_CREADO` es el único mensaje WebSocket de v1.
- Ante `409`, mostrar que la orden ya cambió de estado y refrescar la lista.
- Ante `404`, quitar la orden de la vista.

---

### 9.5 Integración con Caja

En el diseño v1, Cocina **no publica hacia Caja**. Cocina publica
`pedido.estado.actualizado` y es `ms-pedidos` quien actualiza su base y
notifica a Caja si el alcance lo requiere.

Si Caja necesitara consumir el evento directamente:

- Debe declarar su propia cola durable.
- Debe enlazarla a `kds.exchange` con la routing key
  `pedido.estado.actualizado`.
- No debe consumir de `cocina.pedidos.queue` ni de `pedidos.cocina.queue`.
- Debe ser idempotente por `eventoId`.

Coordinar con Pedidos para no duplicar notificaciones.

---

### 9.6 Docker Compose, interfaz para exposición y pruebas de integración

Ya existe `compose.integration.yaml`, que levanta lo disponible en esta rama:

- `ms-menu` + su `menu-db`.
- `ms-display-cocina` + su `cocina-db`.
- RabbitMQ con Management.
- `frontend-cocina`, servida por Nginx y con proxy HTTP/WebSocket al servicio
  de Cocina.

Las bases y RabbitMQ son contenedores separados y tienen volúmenes persistentes.
Dentro de la red Compose, las aplicaciones usan DNS de servicio (`menu-db`,
`cocina-db`, `rabbitmq`), nunca `localhost`. Las contraseñas `root` son solo
para desarrollo/exposición local.

Arranque reproducible desde la raíz:

```bash
cp .env.example .env
docker compose -f compose.integration.yaml config
docker compose -f compose.integration.yaml up --build -d
docker compose -f compose.integration.yaml ps
```

URLs en esta configuración (se eligieron puertos alternativos para no chocar
con servicios locales ya instalados):

| Componente | URL |
|---|---|
| Frontend Cocina | `http://localhost:5175` |
| API display-cocina (directa) | `http://localhost:8084/ordenes` |
| Swagger display-cocina | `http://localhost:8084/swagger-ui.html` |
| API `ms-menu` | `http://localhost:8081` |
| RabbitMQ Management | `http://localhost:15673` |
| PostgreSQL menu (host) | `localhost:5434`, DB `menu_db` |
| PostgreSQL cocina (host) | `localhost:5435`, DB `cocina_db` |

Para la consola RabbitMQ use el usuario/contraseña local de `.env`. Las
aplicaciones se conectan entre sí dentro de Compose por `rabbitmq:5672`.

Verificación operativa (no reemplaza la demostración visual):

```bash
docker compose -f compose.integration.yaml ps
docker compose -f compose.integration.yaml logs --tail=100 ms-menu ms-display-cocina rabbitmq
curl -i http://localhost:8081/categorias
curl -i http://localhost:8081/productos
curl -i http://localhost:8084/ordenes
```

Validación ejecutada en desarrollo: build de las tres imágenes correcto; todos
los servicios Compose quedaron `Up` (bases y RabbitMQ `healthy`); GET de Menu,
GET Cocina directo y GET Cocina a través de Nginx devolvieron `200`; una
notificación `PEDIDO_CREADO` atravesó RabbitMQ → listener → WebSocket proxificado
por Nginx; PATCH a `PREPARANDO` por el proxy devolvió `200` y registró
`fechaInicio`. Tests: Cocina 22/22; Menu 1/1 al apuntarlo al `menu-db` de Compose.

Ese smoke test técnico publicó un evento de validación directamente al broker.
Para la exposición, el flujo debe originarse en la interfaz de Pedidos, como se
describe abajo; no se deben mostrar publicaciones manuales como interacción.

Parar conservando los datos:

```bash
docker compose -f compose.integration.yaml down
```

`docker compose down -v` borra las bases y colas persistidas; **no usarlo** si
se quieren conservar los datos de prueba.

#### Incorporación obligatoria de `ms-pedidos`

`ms-pedidos` no está en esta copia del repositorio; por eso el Compose actual
no finge ejecutar el flujo completo. Cuando su dueño integre el servicio debe:

1. Añadir su Dockerfile y servicio al `compose.integration.yaml`.
2. Añadir una base aislada `pedidos-db` y variables de conexión propias.
3. Añadir dependencia `rabbitmq` usando la red de Compose, usuario compartido
   y `contentType=application/json`.
4. Declarar su cola propia para `pedido.estado.actualizado`.
5. Publicar `pedido.creado` tras crear el pedido correctamente.
6. Publicar el puerto de su API y documentar su URL.
7. Agregar su frontend (por ejemplo `frontend-pedidos/`) para crear pedidos
   desde una pantalla, sin tener que publicar eventos manualmente.

No usar la cola `cocina.pedidos.queue` como cola de Pedidos. Las colas son
independientes y se enlazan al exchange compartido por sus routing keys.

#### Prueba integrada para la exposición (flujo de usuario)

La exposición debe demostrar acciones desde las interfaces, no depender de
comandos de consola para crear/avanzar el pedido:

1. Abrir el frontend de Menú y crear/ver un producto disponible.
2. Abrir el frontend de Pedidos y crear una orden con ese producto.
3. Observar que la orden aparece en el frontend de Cocina como `PENDIENTE`.
4. Desde Cocina, pulsar **Iniciar preparación** y comprobar el cambio a
   `PREPARANDO`.
5. Comprobar visualmente en Pedidos que recibió el estado actualizado.
6. Desde Cocina, pulsar **Marcar como listo** y confirmar `LISTO` en Pedidos.
7. Repetir con otro pedido para comprobar que el tablero sigue actualizándose.

RabbitMQ Management, Swagger, logs y SQL quedan como herramientas de diagnóstico
para el equipo, no como sustituto de las pantallas durante la presentación.

#### Frontends asignados para la presentación

Para cumplir el objetivo de una demostración de extremo a extremo, cada equipo
de microservicio debe entregar la interfaz de usuario correspondiente a su
responsabilidad, integrada al menos con su API y, cuando corresponda, al
Gateway. En este trabajo:

| Responsable | Interfaz esperada | Acción visible |
|---|---|---|
| Menú | Frontend de Menú (lo entrega su responsable) | Consultar/administrar categorías y productos |
| Pedidos | Frontend de Pedidos (lo entrega su responsable) | Crear pedido con productos del menú y ver estado |
| Display Cocina | `frontend-cocina/` (incluido aquí) | Ver comandas y avanzar su preparación |

No es requisito técnico que cada UI sea un despliegue web separado: pueden
integrarse en un portal común si cada módulo conserva su navegación y permite
mostrar su flujo sin usar Postman o comandos como interacción principal.

---

### 9.7 Orden sugerido para integrar

1. **Sincronizar ramas.** Partir de `develop` actualizado.
2. **ms-pedidos.** Publicar `pedido.creado` con `contentType=application/json`
   y consumir `pedido.estado.actualizado` en su propia cola.
3. **Gateway.** Enrutar REST y WebSocket.
4. **Compose.** Levantar `compose.integration.yaml` y comprobar Menu, Cocina,
   bases, RabbitMQ y frontend.
5. **Pedidos.** Incorporar `ms-pedidos`, su base y su frontend al Compose; validar
   publicación/consumo real de los eventos.
6. **Gateway.** Enrutar APIs y WebSockets; cambiar los frontends para usar el
   Gateway en el entorno integrado.
7. **Ensayo visual.** Ejecutar el recorrido Menú → Pedido → Cocina → estado
   actualizado en Pedidos desde las interfaces.
8. **Pruebas técnicas complementarias.** Duplicados, errores, reinicios y caída
   temporal de RabbitMQ; registrar métricas de propagación.

---

### 9.8 Criterios de aceptación de la integración

- [ ] Un pedido creado en `ms-pedidos` aparece en Cocina como `PENDIENTE`.
- [ ] El frontend recibe `PEDIDO_CREADO` por `/topic/cocina`.
- [ ] `PATCH` a `PREPARANDO` actualiza la base y publica el evento.
- [ ] `PATCH` a `LISTO` registra `fechaFinalizacion` y publica el evento.
- [ ] `ms-pedidos` recibe y aplica `pedido.estado.actualizado`.
- [ ] Un evento duplicado no crea otra orden ni otra notificación.
- [ ] Una transición inválida devuelve `409` y no altera la base.
- [ ] Las rutas REST y WebSocket funcionan a través del Gateway.
- [ ] Todo el conjunto levanta con `docker compose up`.
- [ ] No se accede a bases de datos de otros servicios.
- [ ] Durante la exposición, Menu, Pedidos y Cocina se operan desde sus UIs; no
      se usan comandos o Postman como sustituto de la experiencia visual.

---

## 10. Puntos de atención

- **Content-Type:** los eventos deben viajar como JSON con
  `contentType=application/json`. El listener no acepta `text/plain`.
- **Frontend existente:** `frontend-cocina/` contiene una primera pantalla
  funcional con tablero, búsqueda, avance de estados, STOMP y reconexión.
- **Paquete confiable:** el conversor solo deserializa DTO de
  `com.kds.cocina.dto`.
- **WebSocket sin SockJS:** el cliente debe usar STOMP nativo sobre
  `ws://host:8083/ws`.
- **Un solo mensaje WebSocket:** `PEDIDO_CREADO`. No se emiten mensajes de
  cambio de estado en v1.
- **Eventos best-effort:** si RabbitMQ está caído, el estado se guarda igual y
  el evento se pierde. No hay outbox todavía.
- **Base separada:** `cocina_db` es exclusiva del servicio; no se comparten
  tablas con `menu_db`.
- **Carpeta `referencia-st/`:** es material local de referencias visuales y
  está ignorada por Git.

---

## 11. Contacto

Responsable de `ms-display-cocina`: Marco.
Cualquier cambio en los contratos de eventos debe coordinarse con Pedidos y
Caja antes de modificarse.
