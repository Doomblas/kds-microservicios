# Guía para Jorge: API Gateway del KDS

> Asignación propuesta para el equipo: Jorge implementa el Gateway **aparte de
> `ms-pedidos`**. El PDF de referencia exige un Gateway, pero no designa a un
> responsable. Douglas valida Menú y Marco valida Cocina; los tres acuerdan
> rutas públicas y prueban juntos la integración antes del PR a `develop`.

## 1. Alcance y punto de partida

El proyecto tiene **tres microservicios de negocio**: `ms-menu` (Douglas),
`ms-pedidos` (Jorge) y `ms-display-cocina` (Marco). El Gateway es un **punto de
entrada**, no una cuarta base de datos ni un servicio dueño de pedidos: no
persiste información, no publica eventos, no sustituye RabbitMQ y no consulta
las bases de los demás servicios.

En el `develop` revisado hay frontends de Menú, Pedidos y Cocina, y un Compose
para ejecutarlo todo en **una sola red Docker**. **Todavía no hay un Gateway**.
El Nginx de cada frontend sólo hace proxy para *su* aplicación; no es el
Gateway central. Revisar el código actual antes de empezar porque `develop`
puede avanzar durante la implementación.

La guía técnica original (`Guia_Tecnica_Implementacion_KDS_v1.0-1.pdf`) propone
**Java 21 + Spring Boot + Spring Cloud Gateway**. Su tabla de trabajos
pendientes es histórica: para la integración, usar las rutas y contratos
reales del repositorio como fuente de verdad.

## 2. Entregable de Jorge

1. Crear un proyecto independiente (por ejemplo, `api-gateway/`) con Java 21,
   Spring Boot y el starter **Spring Cloud Gateway Server WebFlux** compatible
   con la versión de Spring Boot elegida. Consultar la matriz oficial de
   versiones Spring Boot/Spring Cloud; no copiar a ciegas propiedades de otras
   versiones ni añadir `spring-boot-starter-web` al Gateway reactivo.
2. Hacer configurables por variables las URL destino de Menú, Pedidos y
   Cocina. En producción/LAN deben ser direcciones **alcanzables desde la PC
   que ejecuta el Gateway**, no `localhost` de las otras PC ni DNS de Compose.
3. Implementar las rutas REST y dos rutas **WebSocket distintas**. Mantener
   paths de las APIs, sin que los clientes deban conocer las IP de cada backend.
4. Añadir pruebas automáticas para selección de rutas, reescritura y upgrade
   WebSocket; probar después contra los tres servicios en ejecución.
5. Dockerizar el Gateway y documentar arranque local, variables, healthcheck,
   logs y forma de integrarlo con Compose en la **misma PC** cuando corresponda.
6. Coordinar ajustes de proxy/CORS en los frontends; un Gateway que funciona
   por `curl` pero al que no llegan las pantallas no completa la demostración.
7. Abrir PR hacia `develop` sin cambios no acordados en los otros servicios.

## 3. Contrato público propuesto (acordar antes de codificar)

Ejemplo: Gateway en la PC de Jorge, `http://<IP_JORGE>:8090`.

| Ruta pública | Destino interno | Nota |
|---|---|---|
| `/api/categorias/**`, `/api/productos/**` | Menú `:8080` | Quitar `/api`; frontend Menú ya utiliza `/api` |
| `/categorias/**`, `/productos/**` | Menú `:8080` | Alias para frontend Pedidos, que solicita estas rutas sin `/api` |
| `/pedidos/**` | Pedidos `:8082` | REST, conservar método y cuerpo |
| `/ordenes/**` | Cocina `:8083` | REST, incluir `PATCH /ordenes/{id}/estado` |
| `/ws/pedidos` | Pedidos `:8082/ws` | WebSocket/STOMP; quitar `/pedidos` del path |
| `/ws/cocina` | Cocina `:8083/ws` | WebSocket/STOMP; quitar `/cocina` del path |

Ambos backends publican internamente **`/ws`**, por lo que no basta una sola
ruta pública `/ws`: se debe distinguir Pedidos de Cocina **antes** de quitar el
prefijo. El destino de la ruta WebSocket debe usar `ws://` (o `wss://` con TLS)
y permitir el upgrade, no tratarlo como HTTP normal. El canal STOMP de Cocina
es `/topic/cocina` y el de Pedidos `/topic/pedidos`; los canales no son rutas
HTTP del Gateway.

Probar cuidadosamente `/api/productos` frente a `/productos`: el primero
requiere quitar el prefijo una sola vez, el segundo **no**. Como alternativa
podrían unificar las rutas de los frontends, pero eso requiere acuerdo y
cambios explícitos en sus proxies. No publicar rutas `actuator`/Swagger de
cada servicio ni acceso a PostgreSQL/RabbitMQ a través del Gateway por defecto.

### Ejemplo de implementación de rutas

Tras generar el proyecto con el starter reactivo, configurar
`server.port=${SERVER_PORT:8090}` y definir un `RouteLocator` como punto de
partida. Ajustar imports/DSL si cambia la versión elegida, **compilar y
probar el upgrade**; este ejemplo no sustituye las pruebas:

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KdsRoutes {
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder,
            @Value("${MENU_URL:http://localhost:8080}") String menu,
            @Value("${PEDIDOS_URL:http://localhost:8082}") String pedidos,
            @Value("${COCINA_URL:http://localhost:8083}") String cocina,
            @Value("${PEDIDOS_WS_URL:ws://localhost:8082}") String pedidosWs,
            @Value("${COCINA_WS_URL:ws://localhost:8083}") String cocinaWs) {
        return builder.routes()
            .route("menu-api", r -> r.path("/api/categorias", "/api/categorias/**",
                    "/api/productos", "/api/productos/**")
                .filters(f -> f.stripPrefix(1)).uri(menu))
            .route("menu-directo", r -> r.path("/categorias", "/categorias/**",
                    "/productos", "/productos/**").uri(menu))
            .route("pedidos-rest", r -> r.path("/pedidos", "/pedidos/**").uri(pedidos))
            .route("cocina-rest", r -> r.path("/ordenes", "/ordenes/**").uri(cocina))
            .route("pedidos-ws", r -> r.path("/ws/pedidos")
                .filters(f -> f.rewritePath("/ws/pedidos", "/ws")).uri(pedidosWs))
            .route("cocina-ws", r -> r.path("/ws/cocina")
                .filters(f -> f.rewritePath("/ws/cocina", "/ws")).uri(cocinaWs))
            .build();
    }
}
```

En la prueba LAN del equipo, configurar en la PC de Jorge:

```text
MENU_URL=http://<IP_DOUGLAS>:8080
PEDIDOS_URL=http://localhost:8082
COCINA_URL=http://<IP_MARCO>:8083
PEDIDOS_WS_URL=ws://localhost:8082
COCINA_WS_URL=ws://<IP_MARCO>:8083
SERVER_PORT=8090
```

Las URL WebSocket de destino son **bases sin `/ws`**: el filtro reescribe el
path público a `/ws`. En la misma PC `localhost:8082` puede ser Pedidos; en el
contenedor del Gateway `localhost` sería **el propio contenedor**, por lo que
habrá que usar nombres de servicio Docker. Si se usa TLS, ajustar `wss://` y
certificados. No introducir descubrimiento de servicios sólo para este
prototipo si las tres IP/URL pueden configurarse explícitamente.

## 4. Adaptación necesaria de los frontends

El objetivo es que sus solicitudes **pasen por el Gateway** y que el navegador
no haga llamadas directas a otras PC. Para pruebas locales con Vite se puede
aprovechar el proxy del mismo origen (evita CORS):

- **Menú (`frontend-menu/vite.config.ts`):** `VITE_MENU_API_TARGET` apunta al
  Gateway. Vite quita `/api` actualmente, mientras el Gateway propone
  `/api/...`: acordar **un solo** punto para quitar el prefijo, o conservar la
  ruta alias `/productos`, `/categorias` en el Gateway.
- **Pedidos (`frontend-pedidos/vite.config.ts`):** `/pedidos`, `/productos` y
  `/categorias` pueden apuntar al Gateway. Para `/ws`, el proxy actual envía
  `/ws` tal cual; Jorge debe cambiarlo para enviarlo a `/ws/pedidos`, sin
  modificar la URL de STOMP del navegador (`/ws`). No basta cambiar sólo
  `VITE_PEDIDOS_API_TARGET` si el proxy WebSocket sigue apuntando al backend.
- **Cocina (`frontend-cocina/vite.config.ts`):** `/ordenes` puede apuntar al
  Gateway con `KDS_API_URL`; `/ws` también debe reescribirse a `/ws/cocina`
  si va a pasar por Gateway. Marco valida que `PEDIDO_CREADO` llegue a la UI.

Para frontends servidos por **Nginx en Docker**, los archivos
`frontend-{menu,pedidos,cocina}/nginx.conf` actuales contienen destinos como
`ms-menu:8080`, que **sólo existen en la red Compose de una PC**. La adaptación
del despliegue distribuido debe usar el Gateway accesible por LAN (o un proxy
configurable en cada PC). Vite como servidor de desarrollo puede servir para
el ensayo de red; documentar los cambios de Nginx antes de anunciar soporte
Docker distribuido. Si en lugar de proxy se usa una URL absoluta en el
navegador, configurar CORS y los orígenes permitidos del WebSocket; con HTTPS
usar `wss://`.

## 5. Comprobación mínima del Gateway

Desde **otra PC de la LAN**, no sólo desde Jorge:

```bash
GATEWAY=http://<IP_JORGE>:8090
curl -i "$GATEWAY/api/productos"
curl -i "$GATEWAY/productos"
curl -i "$GATEWAY/pedidos"
curl -i "$GATEWAY/ordenes"
```

Esperar respuestas de cada servicio. Comprobar además `POST /pedidos`, `PATCH
/ordenes/{id}/estado`, y los dos upgrades STOMP con un cliente apropiado:
`ws://<IP_JORGE>:8090/ws/pedidos` → `/topic/pedidos` y
`ws://<IP_JORGE>:8090/ws/cocina` → `/topic/cocina`. Un `curl` HTTP **no**
demuestra que funcione STOMP. Probar en las pantallas y observar en Network que
REST/WebSocket vayan realmente al Gateway o a su proxy, no directamente a los
servicios. Ver pasos completos en
[`GUIA_PRUEBA_KDS_TRES_PC.md`](GUIA_PRUEBA_KDS_TRES_PC.md).
