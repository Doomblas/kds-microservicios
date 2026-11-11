import {
  AlertTriangle,
  ArrowLeft,
  Ban,
  CheckCircle2,
  ChevronDown,
  Clock3,
  KeyRound,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCheck,
  Users,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { AuthApiError } from './authApi'
import { usuariosApi } from './usuariosApi'
import { estadoLabel, rolLabel } from './types'
import type { EstadoUsuario, Rol, Usuario } from './types'

interface UserManagementProps {
  currentUser: Usuario
  onBack: () => void
}

const assignableRoles: Rol[] = ['GESTOR_MENU', 'CAJA', 'COCINA']

const estadoClass: Record<EstadoUsuario, string> = {
  PENDIENTE: 'is-pending',
  ACTIVO: 'is-active',
  BLOQUEADO: 'is-blocked',
}

function initials(nombre: string) {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('es-EC', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date)
}

function errorMessage(error: unknown) {
  if (error instanceof AuthApiError || error instanceof Error) {
    return error.message
  }
  return 'Ocurrió un error inesperado.'
}

export function UserManagement({
  currentUser,
  onBack,
}: UserManagementProps) {
  const [usuarios, setUsuarios] = useState<Usuario[]>([])
  const [query, setQuery] = useState('')
  const [appliedQuery, setAppliedQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyKey, setBusyKey] = useState('')
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')

  const loadUsuarios = useCallback(async (busqueda: string, quiet = false) => {
    if (!quiet) setLoading(true)
    setError('')
    try {
      const data = await usuariosApi.listar(busqueda)
      setUsuarios(Array.isArray(data) ? data : [])
      setAppliedQuery(busqueda.trim())
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadUsuarios('')
  }, [loadUsuarios])

  const resumen = useMemo(
    () => ({
      total: usuarios.length,
      pendientes: usuarios.filter((usuario) => usuario.estado === 'PENDIENTE')
        .length,
      activos: usuarios.filter((usuario) => usuario.estado === 'ACTIVO').length,
      bloqueados: usuarios.filter((usuario) => usuario.estado === 'BLOQUEADO')
        .length,
    }),
    [usuarios],
  )

  const usuariosOrdenados = useMemo(() => {
    const prioridad: Record<EstadoUsuario, number> = {
      PENDIENTE: 0,
      ACTIVO: 1,
      BLOQUEADO: 2,
    }
    return [...usuarios].sort((a, b) => {
      const diferencia = prioridad[a.estado] - prioridad[b.estado]
      if (diferencia !== 0) return diferencia
      return a.nombre.localeCompare(b.nombre, 'es')
    })
  }, [usuarios])

  const activeAdmins = usuarios.filter(
    (usuario) => usuario.rol === 'ADMIN' && usuario.estado === 'ACTIVO',
  ).length

  const reemplazarUsuario = (actualizado: Usuario) => {
    setUsuarios((current) =>
      current.map((usuario) =>
        usuario.id === actualizado.id ? actualizado : usuario,
      ),
    )
    if (currentUser.id === actualizado.id) {
      setFeedback(
        'Actualizaste tu propia cuenta. Si el rol cambió, vuelve a iniciar sesión para aplicar los permisos.',
      )
    }
  }

  const handleRoleChange = async (usuario: Usuario, rol: Rol) => {
    if (usuario.rol === rol) return
    setBusyKey(`rol-${usuario.id}`)
    setError('')
    setFeedback('')

    try {
      const actualizado = await usuariosApi.asignarRol(usuario.id, rol)
      reemplazarUsuario(actualizado)
      setFeedback(
        `${actualizado.nombre} ahora tiene el rol ${rolLabel[rol]}.${
          usuario.estado === 'PENDIENTE'
            ? ' La cuenta quedó activa.'
            : ''
        }`,
      )
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setBusyKey('')
    }
  }

  const handleEstadoChange = async (
    usuario: Usuario,
    estado: EstadoUsuario,
  ) => {
    setBusyKey(`estado-${usuario.id}`)
    setError('')
    setFeedback('')

    try {
      const actualizado = await usuariosApi.cambiarEstado(usuario.id, estado)
      reemplazarUsuario(actualizado)
      setFeedback(
        estado === 'BLOQUEADO'
          ? `${actualizado.nombre} quedó bloqueado. Sus tokens vigentes expirarán pronto; el acceso REST se rechaza desde el próximo inicio de sesión.`
          : `${actualizado.nombre} volvió a estar activo.`,
      )
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setBusyKey('')
    }
  }

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void loadUsuarios(query)
  }

  const clearSearch = () => {
    setQuery('')
    void loadUsuarios('')
  }

  const renderRoleControl = (usuario: Usuario) => {
    const esAdmin = usuario.rol === 'ADMIN'
    const busy = busyKey === `rol-${usuario.id}`

    return (
      <div className="user-role-control">
        <label className="user-mobile-label" htmlFor={`rol-${usuario.id}`}>
          Rol
        </label>
        <div className={`select-wrap ${busy ? 'is-busy' : ''}`}>
          <select
            id={`rol-${usuario.id}`}
            value={usuario.rol ?? ''}
            onChange={(event) => {
              void handleRoleChange(usuario, event.target.value as Rol)
            }}
            disabled={esAdmin || busy}
            aria-label={`Rol de ${usuario.nombre}`}
          >
            {usuario.rol === null && (
              <option value="" disabled>
                Sin asignar
              </option>
            )}
            {esAdmin && <option value="ADMIN">Administrador</option>}
            {assignableRoles.map((rol) => (
              <option key={rol} value={rol}>
                {rolLabel[rol]}
              </option>
            ))}
          </select>
          <ChevronDown size={16} aria-hidden="true" />
        </div>
        {esAdmin && (
          <span className="user-role-hint">
            El rol ADMIN solo se crea con el bootstrap seguro.
          </span>
        )}
      </div>
    )
  }

  const renderStateControl = (usuario: Usuario) => {
    const busy = busyKey === `estado-${usuario.id}`
    const esAdminActivo =
      usuario.rol === 'ADMIN' && usuario.estado === 'ACTIVO'
    const esUltimoAdmin = esAdminActivo && activeAdmins <= 1
    const esMiCuenta = usuario.id === currentUser.id
    const bloquearDeshabilitado = busy || esUltimoAdmin || esMiCuenta
    const activarDeshabilitado = busy || usuario.rol === null

    if (usuario.estado === 'BLOQUEADO') {
      return (
        <button
          type="button"
          className="btn btn-ghost user-action"
          onClick={() => {
            void handleEstadoChange(usuario, 'ACTIVO')
          }}
          disabled={activarDeshabilitado}
          title={
            usuario.rol === null
              ? 'Asigna un rol antes de activar la cuenta.'
              : 'Habilitar nuevamente esta cuenta'
          }
        >
          <UserCheck size={16} aria-hidden="true" />
          {busy ? 'Activando…' : 'Activar'}
        </button>
      )
    }

    return (
      <button
        type="button"
        className="btn btn-ghost user-action is-danger"
        onClick={() => {
          void handleEstadoChange(usuario, 'BLOQUEADO')
        }}
        disabled={bloquearDeshabilitado}
        title={
          esMiCuenta
            ? 'No puedes bloquear tu propia cuenta.'
            : esUltimoAdmin
              ? 'Debe quedar al menos un ADMIN activo.'
              : 'Bloquear esta cuenta'
        }
      >
        <Ban size={16} aria-hidden="true" />
        {busy ? 'Bloqueando…' : 'Bloquear'}
      </button>
    )
  }

  return (
    <section className="users-page" aria-labelledby="users-title">
      <div className="users-hero">
        <div>
          <button type="button" className="users-back" onClick={onBack}>
            <ArrowLeft size={16} aria-hidden="true" />
            Volver al menú
          </button>
          <span className="users-eyebrow">
            <ShieldCheck size={15} aria-hidden="true" />
            Administración de acceso
          </span>
          <h1 id="users-title">Usuarios y roles</h1>
          <p>
            Asigna roles, bloquea cuentas y revisa quién está esperando acceso.
            El rol ADMIN solo se crea con el bootstrap del sistema.
          </p>
        </div>
        <span className="users-hero-badge">
          <KeyRound size={16} aria-hidden="true" />
          Solo ADMIN
        </span>
      </div>

      <div className="users-summary" aria-label="Resumen de usuarios">
        <div className="users-summary-card">
          <span className="users-summary-icon">
            <Users size={17} aria-hidden="true" />
          </span>
          <strong>{resumen.total}</strong>
          <small>Cuentas visibles</small>
        </div>
        <div className="users-summary-card is-pending">
          <span className="users-summary-icon">
            <Clock3 size={17} aria-hidden="true" />
          </span>
          <strong>{resumen.pendientes}</strong>
          <small>Pendientes de rol</small>
        </div>
        <div className="users-summary-card is-active">
          <span className="users-summary-icon">
            <CheckCircle2 size={17} aria-hidden="true" />
          </span>
          <strong>{resumen.activos}</strong>
          <small>Cuentas activas</small>
        </div>
        <div className="users-summary-card is-blocked">
          <span className="users-summary-icon">
            <Ban size={17} aria-hidden="true" />
          </span>
          <strong>{resumen.bloqueados}</strong>
          <small>Cuentas bloqueadas</small>
        </div>
      </div>

      <div className="users-toolbar">
        <form className="users-search" role="search" onSubmit={handleSearch}>
          <Search size={18} aria-hidden="true" />
          <label className="sr-only" htmlFor="users-search-input">
            Buscar usuarios por nombre o email
          </label>
          <input
            id="users-search-input"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar por nombre o email…"
          />
          {query && (
            <button
              type="button"
              className="users-search-clear"
              onClick={() => setQuery('')}
              aria-label="Limpiar búsqueda"
            >
              <X size={16} aria-hidden="true" />
            </button>
          )}
          <button type="submit" className="btn btn-primary user-search-submit">
            Buscar
          </button>
        </form>

        <div className="users-toolbar-actions">
          {appliedQuery && (
            <button
              type="button"
              className="text-button"
              onClick={clearSearch}
            >
              Ver todos
            </button>
          )}
          <button
            type="button"
            className="icon-button"
            onClick={() => {
              void loadUsuarios(appliedQuery, true)
            }}
            disabled={loading}
            aria-label="Actualizar listado de usuarios"
            title="Actualizar listado"
          >
            <RefreshCw
              size={18}
              className={loading ? 'spin' : undefined}
              aria-hidden="true"
            />
          </button>
        </div>
      </div>

      {(error || feedback) && (
        <div
          className={`users-message ${error ? 'is-error' : 'is-success'}`}
          role={error ? 'alert' : 'status'}
        >
          {error ? (
            <AlertTriangle size={18} aria-hidden="true" />
          ) : (
            <CheckCircle2 size={18} aria-hidden="true" />
          )}
          <p>{error || feedback}</p>
          <button
            type="button"
            className="users-message-close"
            onClick={() => {
              setError('')
              setFeedback('')
            }}
            aria-label="Cerrar mensaje"
          >
            <X size={16} aria-hidden="true" />
          </button>
        </div>
      )}

      {loading ? (
        <div className="users-loading" role="status">
          <span className="auth-spinner" aria-hidden="true" />
          Cargando usuarios…
        </div>
      ) : usuariosOrdenados.length === 0 ? (
        <div className="users-empty">
          <span className="users-empty-icon" aria-hidden="true">
            <Users size={28} />
          </span>
          <h2>No encontramos usuarios</h2>
          <p>
            {appliedQuery
              ? `No hay coincidencias para “${appliedQuery}”. Prueba con otro nombre o correo.`
              : 'Todavía no hay cuentas registradas en el sistema.'}
          </p>
          {appliedQuery && (
            <button type="button" className="btn btn-ghost" onClick={clearSearch}>
              Limpiar búsqueda
            </button>
          )}
        </div>
      ) : (
        <ul className="users-list" aria-label="Listado de usuarios">
          {usuariosOrdenados.map((usuario) => {
            const esAdmin = usuario.rol === 'ADMIN'
            return (
              <li
                key={usuario.id}
                className={`user-row ${
                  usuario.estado === 'PENDIENTE' ? 'is-pending' : ''
                }`}
              >
                <div className="user-identity">
                  <span
                    className={`user-avatar ${
                      esAdmin ? 'is-admin' : ''
                    }`}
                    aria-hidden="true"
                  >
                    {initials(usuario.nombre)}
                  </span>
                  <div className="user-copy">
                    <div className="user-name-line">
                      <strong>{usuario.nombre}</strong>
                      {usuario.id === currentUser.id && (
                        <span className="user-you">Tú</span>
                      )}
                    </div>
                    <span className="user-email">{usuario.email}</span>
                    <div className="user-badges">
                      <span className="state-badge is-neutral">
                        #{usuario.id}
                      </span>
                      <span className="user-date">
                        Alta: {formatDate(usuario.fechaCreacion)}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="user-state-block">
                  <span
                    className={`state-badge ${estadoClass[usuario.estado]}`}
                  >
                    {estadoLabel[usuario.estado]}
                  </span>
                  <span className="user-role-read">
                    {usuario.rol ? rolLabel[usuario.rol] : 'Sin rol asignado'}
                  </span>
                </div>

                <div className="user-controls">
                  {renderRoleControl(usuario)}
                  <div className="user-actions">{renderStateControl(usuario)}</div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <p className="users-help">
        Al asignar un rol a una cuenta pendiente, esta se activa
        automáticamente. No es posible asignar ADMIN desde esta pantalla ni
        bloquear al último ADMIN activo.
      </p>
    </section>
  )
}
