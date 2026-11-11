# KDS · Caja de Pedidos

Aplicación independiente en **React + Vite + TypeScript** para la estación de
caja: arma pedidos con el catálogo de `ms-menu`, los envía a `ms-pedidos` y
sigue los estados en vivo por WebSocket.

## Requisitos

- Node.js 18+
- npm 9+
- `ms-menu` y `ms-pedidos` en ejecución

## Instalación y desarrollo

```bash
cd frontend-pedidos
npm install
npm run dev
```

La app queda en `http://localhost:5176`.

## Conexión con los backends

Ni `ms-pedidos` ni `ms-menu` exponen CORS. En desarrollo Vite proxyfica:

| Ruta del navegador | Destino por defecto |
| --- | --- |
| `/pedidos`, `/ws` | `VITE_PEDIDOS_API_TARGET` → `http://localhost:8082` |
| `/productos`, `/categorias` | `VITE_MENU_API_TARGET` → `http://localhost:8080` |

Con `compose.integration.yaml` (puertos publicados):

```bash
VITE_PEDIDOS_API_TARGET=http://localhost:8085 \
VITE_MENU_API_TARGET=http://localhost:8081 \
npm run dev
```

## Flujo

1. Carga productos disponibles desde `ms-menu`.
2. Arma la comanda y hace `POST /pedidos`.
3. Lista pedidos y escucha `/topic/pedidos` (`ESTADO_ACTUALIZADO`).

## Scripts

```bash
npm run dev        # puerto 5176
npm run typecheck
npm run build
npm run preview    # puerto 4176
```
