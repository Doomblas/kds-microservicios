# Guía de autenticación, roles y usuarios del KDS

## 1. Objetivo y alcance

Incorporar identidad y permisos reales al KDS sin modificar la arquitectura
existente: se mantienen **exactamente tres microservicios de negocio**
(`ms-menu`, `ms-pedidos`, `ms-display-cocina`), el `api-gateway` como
infraestructura de enrutado, RabbitMQ para los eventos de pedidos y los
WebSocket STOMP para las notificaciones en vivo.

## 2. Dónde viven las cuentas y por qué

Las cuentas se guardan en **la propia base de datos de `ms-menu`** (`menu_db`,
tabla `usuario`). Motivos:

- `ms-menu` ya es el servicio administrativo del catálogo; el frontend de menú
  es el que necesita administration de usuarios.
- No se crea un cuarto microservicio ni una cuarta base de datos.
- `ms-pedidos` y `ms-display-cocina` **no acceden** a esa base: validan el JWT
  de forma *stateless* con el secreto compartido `JWT_SECRET`, por lo que no
  hay acceso cruzado a datos ni dependencias nuevas entre servicios.

El modelo es:

| Columna | Contenido |
| --- | --- |
| `id`, `nombre`, `email` | Identidad (el email es único) |
| `password_hash` | Hash BCrypt; nunca se expone en la API |
| `rol` | `ADMIN`, `GESTOR_MENU`, `CAJA`, `COCINA` o `null` |
| `estado` | `PENDIENTE`, `ACTIVO`, `BLOQUEADO` |
| `fecha_creacion`, `fecha_actualizacion` | Auditoría |

El **rol** y el **estado** son columnas distintas: una cuenta puede estar
`BLOQUEADO` con rol `CAJA`, y viceversa.

## 3. Flujo de una cuenta

1. La persona se registra desde cualquiera de los tres frontends con nombre,
   email y contraseña.
2. `POST /auth/register` crea la cuenta con `estado = PENDIENTE` y `rol = null`.
   El contrato `RegistroRequest` **no tiene campo de rol**: aunque el navegador
   envíe `rol` o `estado`, se ignoran.
3. La persona puede iniciar sesión (`POST /auth/login`) y recibe un JWT, pero
   el frontend muestra la pantalla **“Esperando asignación de rol”**: no puede
   abrir los módulos operativos.
4. Un `ADMIN` entra al frontend de menú → **Usuarios** → asigna el rol. Si la
   cuenta estaba `PENDIENTE`, la asignación la activa automáticamente.
5. La persona vuelve a iniciar sesión (o actualiza el estado) y ya accede al
   módulo que le corresponde.

## 4. Contrato HTTP

### Público

```http
POST /auth/register
{ "nombre": "Ana Pérez", "email": "ana@empresa.com", "password": "ClaveSegura123" }
→ 201 { "id": 1, "nombre": "...", "email": "...", "rol": null, "estado": "PENDIENTE", "fechaCreacion": "..." }
```

```http
POST /auth/login
{ "email": "ana@empresa.com", "password": "ClaveSegura123" }
→ 200 { "token": "eyJ...", "tipo": "Bearer", "expiraEnSegundos": 1800, "usuario": { ... } }
```

Errores: `401` credenciales inválidas, `403` cuenta bloqueada, `409` email
duplicado, `400` validación por campo (`fieldErrors`).

### Autenticado

```http
GET /auth/me
Authorization: Bearer <token>
→ 200 { "id": 1, "nombre": "...", "email": "...", "rol": "CAJA", "estado": "ACTIVO", "fechaCreacion": "..." }
```

### Solo ADMIN

```http
GET   /usuarios[?q=texto]
GET   /usuarios/{id}
PATCH /usuarios/{id}/rol      { "rol": "COCINA" }
PATCH /usuarios/{id}/estado   { "estado": "BLOQUEADO" }
```

Reglas aplicadas en `UsuarioService`:

- No se puede asignar `ADMIN` por API (`400`). El rol `ADMIN` solo existe por
  el bootstrap seguro del arranque.
- No se puede bloquear la propia cuenta (`400`).
- No se puede bloquear al último `ADMIN` activo (`409`).
- No se puede activar una cuenta sin rol (`400`).
- No se puede volver a `PENDIENTE` desde la API (`400`).

## 5. Token

- Algoritmo: **HS256** con `JWT_SECRET` (mínimo 32 bytes), compartido por los
  tres servicios.
- Vida útil: `JWT_EXPIRATION_MINUTES` (30 por defecto).
- Claims: `userId`, `email`, `role` (ausente si la cuenta no tiene rol),
  `estado`, `iss = kds-auth`, `iat`, `exp`.
- El claim `role` se omite cuando la cuenta está pendiente, de modo que un
  token de cuenta pendiente no puede abrir ningún módulo.

## 6. Matriz de permisos en el backend

| Recurso | ADMIN | GESTOR_MENU | CAJA | COCINA | Sin rol |
| --- | --- | --- | --- | --- | --- |
| `GET /productos`, `GET /categorias` (ms-menu) | Sí | Sí | Sí | No | No |
| `POST/PUT/PATCH /productos`, `/categorias` (ms-menu) | Sí | Sí | No | No | No |
| `/usuarios/**` (ms-menu) | Sí | No | No | No | No |
| `/pedidos/**` (ms-pedidos) | Sí | No | Sí | No | No |
| `/ordenes/**` (ms-display-cocina) | Sí | No | No | Sí | No |

La autorización se aplica en el backend: la interfaz oculta acciones, pero la
respuesta real es `403` cuando el permiso falta.

## 7. Frontends

Los tres frontends (`frontend-menu`, `frontend-pedidos`, `frontend-cocina`)
comparten el mismo comportamiento, sin introducir librerías de UI nuevas:

- `Iniciar sesión` y `Registrarme` en una pantalla de acceso con el lenguaje
  visual existente (verde bosque, lima, tipografías y radios actuales).
- La sesión se guarda en `localStorage` (token + copia no sensible del
  usuario) y se restaura contra `GET /auth/me`.
- Pantalla **“Esperando asignación de rol”** para cuentas `PENDIENTE` o sin rol,
  con opción de actualizar el estado y cerrar sesión.
- Pantalla de **acceso denegado** para roles sin permiso sobre el módulo, y
  aviso de sesión expirada cuando el backend responde `401`.
- Solo `frontend-menu` muestra **Usuarios y roles**, y únicamente a `ADMIN`.

## 8. Bootstrap del primer ADMIN

```bash
ADMIN_EMAIL=admin@empresa.com
ADMIN_PASSWORD=ClaveSegura123
ADMIN_NAME=Administrador KDS
```

Si `ADMIN_PASSWORD` está vacío o tiene menos de 8 caracteres, no se crea ninguna
cuenta. Si ya existe un `ADMIN`, el bootstrap se omite. Los valores viajan por
variables de entorno y nunca se escriben en el repositorio ni en los logs.

## 9. Limitaciones conocidas

1. **WebSocket**: el handshake STOMP (`/ws/pedidos`, `/ws/cocina`) queda
   abierto (`permitAll`) para no romper el flujo en vivo ya implementado. La
   protección efectiva está en toda la API REST; las operaciones de negocio
   (crear pedidos, cambiar estados, administrar el menú) siempre exigen token.
2. **Ventana de token válido**: si se bloquea una cuenta o se cambia su rol, el
   token ya emitido sigue siendo criptográficamente válido hasta expirar
   (30 minutos por defecto). `/auth/me` consulta el estado real y los
   frontends cierran la sesión al detectar un cambio de permisos; tras un
   cambio de rol el usuario debe iniciar sesión de nuevo para que el token
   incluya el nuevo claim `role`.

## 10. Pruebas

| Suite | Ubicación | Cobertura principal |
| --- | --- | --- |
| `AuthServiceTest` | `ms-menu/src/test/.../auth/` | BCrypt, normalización de email, claims del JWT, cuenta bloqueada |
| `SeguridadMenuIntegrationTest` | `ms-menu/src/test/.../auth/` | Registro pendiente, login, `/auth/me`, matriz de roles, gestión de usuarios (contexto Spring completo y H2) |
| `PedidosSecurityTest` | `ms-pedidos/src/test/.../security/` | 401 sin token, token inválido/expirado, 403 por rol |
| `OrdenesCocinaSecurityTest` | `ms-display-cocina/src/test/.../security/` | 401/403 por rol sobre `/ordenes` |
| `kitchen.spec.ts` | `frontend-cocina/tests/` | Pantalla de acceso, login real, espera de rol, acceso denegado, regresión del tablero |

Ejecución:

```bash
cd ms-menu && ./mvnw test
cd ms-pedidos && ./mvnw test
cd ms-display-cocina && ./mvnw test
cd frontend-cocina && npm test
```
