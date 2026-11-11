import { FolderPlus, LogOut, Plus, Users } from 'lucide-react'
import { rolLabel } from '../auth/types'
import type { Usuario } from '../auth/types'

export type ConnectionState = 'loading' | 'online' | 'offline'

interface HeaderProps {
  connection: ConnectionState
  actionsDisabled: boolean
  newProductDisabled: boolean
  newProductHint?: string
  user: Usuario
  usersActive: boolean
  onNewCategory: () => void
  onNewProduct: () => void
  onOpenUsers?: () => void
  onLogout: () => void
}

const connectionLabels: Record<ConnectionState, string> = {
  loading: 'Conectando…',
  online: 'ms-menu · Conectado',
  offline: 'Sin conexión',
}

function initials(nombre: string) {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

export function Header({
  connection,
  actionsDisabled,
  newProductDisabled,
  newProductHint,
  user,
  usersActive,
  onNewCategory,
  onNewProduct,
  onOpenUsers,
  onLogout,
}: HeaderProps) {
  return (
    <header className="app-header">
      <div className="header-inner">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">
            M
          </div>
          <div className="brand-copy">
            <div className="brand-line">
              <span className="brand-name">KDS // Menú</span>
              <span className="brand-service">ms-menu</span>
            </div>
            <span className="brand-subtitle">Administración de Menú</span>
          </div>
        </div>

        <div className="header-actions">
          <span
            className={`connection-pill connection-${connection}`}
            role="status"
            aria-live="polite"
            aria-label={`Estado de conexión: ${connectionLabels[connection]}`}
            title={connectionLabels[connection]}
          >
            <span className="connection-dot" aria-hidden="true" />
            <span className="connection-label">
              {connectionLabels[connection]}
            </span>
          </span>

          {onOpenUsers && (
            <button
              type="button"
              className={`btn btn-ghost header-users-button ${
                usersActive ? 'is-active' : ''
              }`}
              onClick={onOpenUsers}
              aria-label="Administración de usuarios"
              aria-pressed={usersActive}
              title="Usuarios y roles"
            >
              <Users size={18} aria-hidden="true" />
              <span>Usuarios</span>
            </button>
          )}

          <button
            type="button"
            className="btn btn-ghost"
            onClick={onNewCategory}
            disabled={actionsDisabled}
            aria-label="Nueva categoría"
            title="Nueva categoría"
          >
            <FolderPlus size={18} aria-hidden="true" />
            <span>Nueva Categoría</span>
          </button>

          <button
            type="button"
            className="btn btn-primary"
            onClick={onNewProduct}
            disabled={newProductDisabled}
            aria-label="Nuevo producto"
            title={newProductHint ?? 'Nuevo producto'}
          >
            <Plus size={18} aria-hidden="true" />
            <span>Nuevo Producto</span>
          </button>

          <div
            className="session-user"
            title={`${user.nombre} · ${
              user.rol ? rolLabel[user.rol] : 'Sin rol'
            }`}
          >
            <span className="session-avatar" aria-hidden="true">
              {initials(user.nombre)}
            </span>
            <span className="session-meta">
              <strong>{user.nombre}</strong>
              <span>{user.rol ? rolLabel[user.rol] : 'Sin rol'}</span>
            </span>
          </div>

          <button
            type="button"
            className="icon-button session-logout"
            onClick={onLogout}
            aria-label="Cerrar sesión"
            title="Cerrar sesión"
          >
            <LogOut size={18} aria-hidden="true" />
          </button>
        </div>
      </div>
    </header>
  )
}
