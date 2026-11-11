import { FolderPlus, Plus } from 'lucide-react'

export type ConnectionState = 'loading' | 'online' | 'offline'

interface HeaderProps {
  connection: ConnectionState
  actionsDisabled: boolean
  newProductDisabled: boolean
  newProductHint?: string
  onNewCategory: () => void
  onNewProduct: () => void
}

const connectionLabels: Record<ConnectionState, string> = {
  loading: 'Conectando…',
  online: 'ms-menu · Conectado',
  offline: 'Sin conexión',
}

export function Header({
  connection,
  actionsDisabled,
  newProductDisabled,
  newProductHint,
  onNewCategory,
  onNewProduct,
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
        </div>
      </div>
    </header>
  )
}
