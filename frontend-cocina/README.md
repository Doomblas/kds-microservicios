# Frontend Cocina — KDS

Panel de órdenes de `ms-display-cocina`: tablero por estado, actualización por
REST y nuevas comandas en tiempo real por STOMP/WebSocket. Adopta el sistema
visual Culinary Operations de la referencia Stitch: fondo hueso, superficies
blancas, verde bosque y acentos lima, con Outfit y Plus Jakarta Sans.

La referencia `stitch*/` es material **local e ignorado por Git**. No se necesita
para compilar o ejecutar: estilos y tipografías se empaquetan en el frontend,
sin dependencias de Google Fonts, Tailwind CDN ni archivos de esa carpeta.

## Requisitos

- Node.js 22.12+ (la imagen Docker usa Node 22 LTS)
- `ms-display-cocina` disponible en `http://localhost:8083`
- PostgreSQL y RabbitMQ conectados al backend

## Ejecutar localmente

```bash
cd frontend-cocina
npm ci
npm run dev
```

Abrir `http://localhost:5173`. Vite hace proxy de `/ordenes` y `/ws` al backend para evitar configurar CORS durante el desarrollo.

## Ejecutar en Docker Compose

Desde la raíz del repositorio:

```bash
test -f .env || cp .env.example .env
docker compose -f compose.integration.yaml up --build -d frontend-cocina
```

Abrir `http://localhost:5175`. Nginx sirve el frontend y proxifica REST y
WebSocket a `ms-display-cocina` dentro de la red Docker.

Este comando levanta **sólo Cocina y sus dependencias**: `frontend-cocina`,
`ms-display-cocina`, `cocina-db` y RabbitMQ. No inicia ni modifica Menú o
Pedidos. Para la prueba integrada del equipo se puede levantar todo el Compose
sin indicar el nombre del servicio. Las bases de datos existentes se conservan.

Para actualizar únicamente la imagen web cuando el backend ya está levantado:

```bash
docker compose -f compose.integration.yaml up --build -d --no-deps frontend-cocina
```

### Backend local sin Docker

Para usar Vite con un backend en otro host, desde `frontend-cocina/`:

```bash
test -f .env || cp .env.example .env
# Edita KDS_API_URL dentro de .env
npm run dev
```

Para producción se puede configurar `VITE_API_URL` y `VITE_WS_URL` en el build. Sin esas variables, la app usa el mismo origen para REST y WebSocket.

## Funcionalidades

- Órdenes agrupadas en `PENDIENTE`, `PREPARANDO` y `LISTO`.
- Búsqueda por número de pedido, id o nombre de producto.
- Filtros por estado combinables con la búsqueda y opción de limpiar filtros.
- Resumen con conteos reales: no se presenta el histórico como «órdenes de hoy».
- Acción contextual para iniciar preparación y marcar como listo.
- Nuevas órdenes vía STOMP `/ws` → `/topic/cocina`.
- Indicador de conexión, reconexión automática y refresco de respaldo cada 20 segundos.
- Aviso opcional de sonido tras habilitarlo con interacción del usuario.
- Pantalla completa con vista operativa: oculta la cabecera editorial para dar
  más espacio al tablero; salir restaura la vista normal.
- Diseño adaptable desde 320 px, controles de al menos 44 px (48 px para avanzar
  comandas), foco visible y conservado tras mover una tarjeta, avisos accesibles
  y respeto a `prefers-reduced-motion`.
- Estados de carga, vacío, búsqueda sin resultados y error diferenciados;
  un error conserva los últimos datos sin aparentar que el servicio está vacío.
- Temporizadores actualizados en pantalla; los de comandas listas se detienen
  en `fechaFinalizacion`. Las fechas sin zona horaria siguen el contrato del backend.

## Contrato del backend

- `GET /ordenes`
- `PATCH /ordenes/{id}/estado` con `{"estado":"PREPARANDO"}` o `{"estado":"LISTO"}`
- STOMP `/ws`, suscripción `/topic/cocina`; se espera `tipo: PEDIDO_CREADO`.

El backend v1 no emite eventos WebSocket al actualizar estado; la UI actualiza el estado con la respuesta del PATCH y refresca periódicamente para reconciliar cambios externos.

## Build

```bash
npm run build
```

## Pruebas del frontend

```bash
npm ci
npx playwright install chromium
npm test
```

Las pruebas de navegador arrancan Vite en el puerto `4174` y simulan REST/STOMP
sin escribir en bases de datos reales. Cubren búsqueda/filtros, transiciones,
errores, notificaciones, teclado, tamaños táctiles, ausencia de desbordamiento
y comprobaciones automáticas WCAG AA con axe en 320, 390, 768, 1024 y 1440 px.
Estas comprobaciones no sustituyen una revisión manual de accesibilidad.

La validación en Docker debe comprobar por separado `GET /ordenes`, recepción
real por WebSocket y `PENDIENTE → PREPARANDO → LISTO` a través del proxy Nginx.
