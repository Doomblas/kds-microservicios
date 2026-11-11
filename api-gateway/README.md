# API Gateway

Infraestructura de entrada del KDS basada en Spring Boot 4.1.2, Java 21 y
Spring Cloud Gateway 2025.1.4 (Gateway Server WebFlux 5.0.4).

## Rutas

| Ruta pública | Destino | Tipo |
|---|---|---|
| `/productos`, `/productos/**` | `ms-menu:8080` | REST |
| `/categorias`, `/categorias/**` | `ms-menu:8080` | REST |
| `/pedidos`, `/pedidos/**` | `ms-pedidos:8082` | REST |
| `/ordenes`, `/ordenes/**` | `ms-display-cocina:8083` | REST |
| `/ws/pedidos`, `/ws/pedidos/**` | `ws://ms-pedidos:8082/ws` | STOMP nativo |
| `/ws/cocina`, `/ws/cocina/**` | `ws://ms-display-cocina:8083/ws` | STOMP nativo |

`ms-pedidos` y `ms-display-cocina` usan ambos `/ws`, por lo que el Gateway
publica prefijos distintos y reescribe la ruta antes de reenviar. Los tópicos
STOMP no cambian: `/topic/pedidos` y `/topic/cocina`.

Los frontends conservan la URL relativa `/ws`; su Nginx la reescribe a
`/ws/pedidos` o `/ws/cocina` antes de llegar al Gateway.

## Variables

| Variable | Default local | Uso |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto del Gateway |
| `MS_MENU_URI` | `http://localhost:8081` | Backend Menu |
| `MS_PEDIDOS_URI` | `http://localhost:8085` | Backend Pedidos REST |
| `MS_DISPLAY_COCINA_URI` | `http://localhost:8084` | Backend Cocina REST |
| `MS_PEDIDOS_WS_URI` | `ws://localhost:8085` | WebSocket Pedidos |
| `MS_DISPLAY_COCINA_WS_URI` | `ws://localhost:8084` | WebSocket Cocina |
| `GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost:5174,...` | Orígenes CORS |

En Docker Compose las URIs se sobrescriben con los nombres internos de cada
servicio. Los puertos directos siguen expuestos para diagnóstico.

## Build y ejecución

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw clean package
docker compose -f ../compose.integration.yaml build api-gateway
```

Healthcheck: `GET /actuator/health`.
