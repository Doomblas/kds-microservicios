# KDS · Caja de Pedidos

Aplicación independiente en **React + Vite + TypeScript** para la estación de
caja: inicia sesión (rol `CAJA`/`ADMIN`), arma pedidos con el catálogo de
`ms-menu`, los envía a `ms-pedidos` y sigue los estados en vivo por WebSocket.

## Requisitos

- Node.js 18+
- npm 9+
- Backends alcanzables: autenticación/catálogo (`ms-menu` o Gateway) y
  `ms-pedidos` (directo o vía Gateway)
- Usuario con rol `CAJA` o `ADMIN` (cuenta `ACTIVO`)

## Instalación y desarrollo

```bash
cd frontend-pedidos
npm install
npm run dev
```

La app queda en `http://localhost:5176`.

## Conexión con los backends

En desarrollo Vite proxyfica (sin CORS):

| Ruta del navegador | Destino |
| --- | --- |
| `/pedidos` | `VITE_PEDIDOS_API_TARGET` o `VITE_GATEWAY_URL` → por defecto `http://localhost:8082` |
| `/ws` | Mismo destino; si es Gateway, se reescribe a `/ws/pedidos` |
| `/productos`, `/categorias`, `/auth` | `VITE_MENU_API_TARGET` o `VITE_GATEWAY_URL` → por defecto `http://localhost:8080` |

### Compose en una sola PC

```bash
VITE_GATEWAY_URL=http://localhost:8080 npm run dev
```

### Prueba LAN (3 PC) — las IP cambian por red; no van en el código

En la PC de Jorge (ejemplo):

```bash
VITE_GATEWAY_URL=http://127.0.0.1:8080 \
VITE_MENU_API_TARGET=http://<IP_DOUGLAS>:8080 \
VITE_PEDIDOS_API_TARGET=http://127.0.0.1:8082 \
npm run dev
```

Si todo el tráfico REST/WS debe ir al Gateway de Jorge:

```bash
VITE_GATEWAY_URL=http://<IP_JORGE>:8080 \
VITE_WS_VIA_GATEWAY=true \
npm run dev
```

## Flujo

1. Login / registro → espera de rol si la cuenta está `PENDIENTE`.
2. Carga productos de menú (botón **actualizar catálogo** si Douglas agrega ítems).
3. `POST /pedidos` con Bearer JWT.
4. Lista pedidos y escucha `/topic/pedidos` (`ESTADO_ACTUALIZADO`).
5. Si el WebSocket se cae, la UI hace polling REST cada 8 s como respaldo.

## Scripts

```bash
npm run dev        # puerto 5176
npm run typecheck
npm run build
npm run preview    # puerto 4176
```
