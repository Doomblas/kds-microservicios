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

> **Pendiente:** todavía no hay configuración CORS para REST. Si el frontend
> corre en otro origen (por ejemplo Vite en `localhost:5173`), debe usarse un
> proxy del bundler o agregar CORS en el backend antes de consumirlo directo.

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
- Dockerfile multi-stage.
- 22 tests.
- Validación de punta a punta con PostgreSQL y RabbitMQ reales:
  creación, duplicados, transiciones, eventos, WebSocket y errores.

### Pendiente para la integración completa

| Tarea | Depende de | Notas |
|---|---|---|
| Implementar `ms-pedidos` y publicar `pedido.creado` | Equipo de Pedidos | Aún no existe en `develop` |
| Consumir `pedido.estado.actualizado` en Pedidos | Equipo de Pedidos | Actualizar estado y notificar a caja |
| Agregar rutas al API Gateway | Equipo de Gateway | `/ordenes/**` y `/ws` hacia `:8083` |
| Agregar el servicio a `docker-compose.yml` raíz | Equipo | Incluir `cocina-db`, RabbitMQ y variables |
| Agregar CORS REST o proxy en el frontend | Frontend | Necesario si se consume desde otro origen |
| Implementar pantalla de cocina | Frontend | Consumir REST + STOMP |
| Colección Postman | Equipo | `GET`, `GET/{id}`, `PATCH` |
| Medir propagación | Equipo | Pedido creado → visible; estado → Pedidos |
| Validar la imagen Docker | Equipo | No fue posible en el entorno de validación |
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
| Frontend cocina | — | REST + WebSocket | — |
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

### 9.6 Integración en `docker-compose.yml`

Bloque de referencia para el compose raíz:

```yaml
services:
  cocina-db:
    image: postgres:16
    environment:
      POSTGRES_DB: cocina_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5433:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d cocina_db"]
      interval: 5s
      timeout: 3s
      retries: 10

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  ms-display-cocina:
    build: ./ms-display-cocina
    environment:
      DB_URL: jdbc:postgresql://cocina-db:5432/cocina_db
      DB_USERNAME: postgres
      DB_PASSWORD: postgres
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PASSWORD: guest
      WS_ALLOWED_ORIGINS: "http://localhost:5173,http://localhost:3000"
    ports:
      - "8083:8083"
    depends_on:
      cocina-db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
```

Notas:

- `5433:5432` evita choque si ya hay un PostgreSQL local en `5432`.
- El `DB_URL` usa el nombre del servicio (`cocina-db`), no `localhost`.
- Si el compose ya tiene un RabbitMQ compartido, no crear otro: reutilizarlo y
  ajustar `RABBITMQ_HOST`.
- El Gateway y `ms-pedidos` deben apuntar a `ms-display-cocina:8083` dentro de
  la red de Docker.

---

### 9.7 Orden sugerido para integrar

1. **Sincronizar ramas.** Partir de `develop` actualizado.
2. **ms-pedidos.** Publicar `pedido.creado` con `contentType=application/json`
   y consumir `pedido.estado.actualizado` en su propia cola.
3. **Gateway.** Enrutar REST y WebSocket.
4. **docker-compose.** Agregar `ms-display-cocina`, `cocina-db`, RabbitMQ,
   healthchecks y `depends_on`.
5. **Frontend.** Conectar la pantalla de cocina al REST y al STOMP.
6. **Pruebas integradas.** Varios pedidos, duplicados, transiciones y caída de
   RabbitMQ.
7. **Métricas.** Tiempo de propagación y porcentaje de eventos procesados.

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

---

## 10. Puntos de atención

- **Content-Type:** los eventos deben viajar como JSON con
  `contentType=application/json`. El listener no acepta `text/plain`.
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
