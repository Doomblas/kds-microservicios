# KDS · Administración de Menú

Aplicación independiente en **React + Vite + TypeScript** para administrar el
catálogo real de `ms-menu`: categorías, productos y disponibilidad.

## Requisitos

- Node.js 18+
- npm 9+
- `ms-menu` en ejecución (por defecto en `http://localhost:8080`)

## Instalación y desarrollo

```bash
cd frontend-menu
npm install
npm run dev
```

La app queda disponible en `http://localhost:5174`.

## Conexión con el backend

`ms-menu` **no tiene CORS habilitado**, por lo que en desarrollo el navegador no
puede llamarlo directamente en otro origen. Vite expone un proxy:

- El cliente usa `/api` como URL base (`VITE_MENU_API_URL=/api` por defecto).
- El proxy reescribe `/api/*` y lo envía a `VITE_MENU_API_TARGET`.
- Valor por defecto del destino: `http://localhost:8080`.

Variables disponibles (ver `.env.example`):

| Variable | Descripción | Por defecto |
| --- | --- | --- |
| `VITE_MENU_API_URL` | URL base que usa el navegador | `/api` |
| `VITE_MENU_API_TARGET` | Destino del proxy de Vite | `http://localhost:8080` |

Ejemplo para un backend en otro puerto:

```bash
VITE_MENU_API_TARGET=http://localhost:8090 npm run dev
```

Si se despliega en un entorno con CORS resuelto (o detrás del mismo origen), se
puede apuntar directamente:

```bash
VITE_MENU_API_URL=https://menu.example.com npm run build
```

Toda la configuración y el acceso a datos viven en
`src/services/menuApi.ts`; los componentes no hacen `fetch()` directo.

## Scripts

```bash
npm run dev        # servidor de desarrollo en el puerto 5174
npm run typecheck  # verificación de tipos
npm run build      # typecheck + build de producción en dist/
npm run preview    # servir el build en el puerto 4174 (con el mismo proxy /api)
```

`preview` también aplica el proxy `/api`, de modo que el build puede probarse
localmente contra `ms-menu` sin CORS.

## Endpoints utilizados

Solo los existentes en `ms-menu`:

- `GET /categorias`, `GET /categorias/{id}`, `POST /categorias`
- `GET /productos`, `GET /productos/{id}`, `POST /productos`,
  `PUT /productos/{id}`, `PATCH /productos/{id}/disponibilidad`

No se inventaron endpoints. En particular:

- **No hay edición de categorías** en el backend actual, por eso la app solo
  permite crearlas.
- No hay endpoints de borrado.
- Los productos incluyen la categoría anidada en la respuesta; `categoriaId` se
  usa únicamente en los cuerpos de escritura.

## Funcionalidades

- Carga inicial de categorías y productos con skeleton.
- Métricas reales: total de productos, disponibles, no disponibles, categorías
  y porcentaje disponible.
- Menu Pulse con anillo de progreso calculado desde los datos reales.
- Búsqueda por nombre en tiempo real, insensible a mayúsculas y acentos.
- Filtro por disponibilidad y pills dinámicas por categoría con contadores.
- Grid de productos con nombre, descripción, precio, categoría, disponibilidad,
  edición y toggle.
- Creación y edición de producto en un mismo drawer lateral, con validación en
  cliente y errores `ApiError` del backend.
- Actualización optimista de disponibilidad con rollback y toast de error.
- Creación de categoría con manejo específico de `409`.
- Estados de carga, error de conexión, menú vacío, sin resultados, categoría
  vacía, guardado, éxito y errores de validación/backend.
- Drawer accesible con foco atrapado y cierre con `Escape`; toasts y controles
  con nombres accesibles y foco visible.

## Estructura

```text
frontend-menu/
├── index.html
├── vite.config.ts
├── .env.example
└── src/
    ├── App.tsx
    ├── main.tsx
    ├── components/     # UI reutilizable: header, hero, cards, drawers, toasts…
    ├── hooks/          # datos, filtros y toasts
    ├── services/       # menuApi: base URL, requests y parseo de errores
    ├── styles/         # tokens, base, layout, componentes y drawers
    ├── types/          # contratos de ms-menu
    └── utils/          # formato de precios y porcentajes
```

## Accesibilidad

- Formularios con `label` asociado y `aria-describedby` para errores.
- Focus visible en todos los controles.
- Drawer con `role="dialog"`, `aria-modal`, foco inicial, trampa de tabulación y
  restauración del foco al cerrar.
- Toggles con `role="switch"` y `aria-checked`.
- Indicador de conexión con `role="status"` y `aria-live`.
- Animaciones desactivadas con `prefers-reduced-motion`.
