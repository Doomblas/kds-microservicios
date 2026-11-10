# Frontend Cocina — KDS

Panel de órdenes para cocina: tablero por estado, actualización de estado por REST y nuevas comandas en tiempo real por STOMP/WebSocket. Diseño oscuro con paneles glass inspirado en `referencia-st/liquid-glass`, ajustado para mantener legibilidad y contraste en una pantalla operativa.

## Requisitos

- Node.js 20+
- `ms-display-cocina` disponible en `http://localhost:8083`
- PostgreSQL y RabbitMQ conectados al backend

## Ejecutar localmente

```bash
cd frontend-cocina
npm install
npm run dev
```

Abrir `http://localhost:5173`. Vite hace proxy de `/ordenes` y `/ws` al backend para evitar configurar CORS durante el desarrollo.

Si el backend está en otro host:

```bash
cp .env.example .env
# Edita KDS_API_URL dentro de .env
npm run dev
```

Para producción se puede configurar `VITE_API_URL` y `VITE_WS_URL` en el build. Sin esas variables, la app usa el mismo origen para REST y WebSocket.

## Funcionalidades

- Órdenes agrupadas en `PENDIENTE`, `PREPARANDO` y `LISTO`.
- Búsqueda por número de pedido, id o nombre de producto.
- Acción contextual para iniciar preparación y marcar como listo.
- Nuevas órdenes vía STOMP `/ws` → `/topic/cocina`.
- Indicador de conexión, reconexión automática y refresco de respaldo cada 20 segundos.
- Aviso opcional de sonido tras habilitarlo con interacción del usuario.
- Diseño adaptable, foco visible, `aria-live`, estados de carga/error/vacío y respeto a `prefers-reduced-motion`.

## Contrato del backend

- `GET /ordenes`
- `PATCH /ordenes/{id}/estado` con `{"estado":"PREPARANDO"}` o `{"estado":"LISTO"}`
- STOMP `/ws`, suscripción `/topic/cocina`; se espera `tipo: PEDIDO_CREADO`.

El backend v1 no emite eventos WebSocket al actualizar estado; la UI actualiza el estado con la respuesta del PATCH y refresca periódicamente para reconciliar cambios externos.

## Build

```bash
npm run build
```
