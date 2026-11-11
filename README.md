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
- API Menu: <http://localhost:8081>
- API Cocina: <http://localhost:8084/ordenes>
- API Pedidos: <http://localhost:8085/pedidos>
- RabbitMQ Management: <http://localhost:15673>

La guía de despliegue, contratos y pruebas entre responsables está en
[`docs/GUIA_INTEGRACION_DISPLAY_COCINA.md`](docs/GUIA_INTEGRACION_DISPLAY_COCINA.md).
Para la siguiente etapa, consultar la
[`guía del Gateway`](docs/GUIA_GATEWAY_KDS.md) y la
[`prueba distribuida en tres PC`](docs/GUIA_PRUEBA_KDS_TRES_PC.md).
El Compose anterior es para **una sola máquina**: no configura automáticamente
las conexiones de red entre tres equipos.
Las claves de `.env.example` son únicamente para desarrollo local/exposición.
