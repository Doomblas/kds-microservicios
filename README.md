# kds-microservicios

Distributed Kitchen Display System built with Java, Spring Boot, RabbitMQ,
WebSockets, PostgreSQL and Docker.

## Entorno de integración disponible

En la rama actual están `ms-menu`, `ms-display-cocina` y su frontend de Cocina.
El microservicio `ms-pedidos` debe incorporarse por su responsable para poder
ejecutar el flujo completo pedido → cocina.

```bash
cp .env.example .env
docker compose -f compose.integration.yaml up --build -d
```

- Frontend Cocina: <http://localhost:5175>
- API Menu: <http://localhost:8081>
- API Cocina: <http://localhost:8084/ordenes>
- RabbitMQ Management: <http://localhost:15673>

La guía de despliegue, contratos y pruebas entre responsables está en
[`docs/GUIA_INTEGRACION_DISPLAY_COCINA.md`](docs/GUIA_INTEGRACION_DISPLAY_COCINA.md).
Las claves de `.env.example` son únicamente para desarrollo local/exposición.
