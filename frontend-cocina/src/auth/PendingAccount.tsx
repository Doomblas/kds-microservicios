import { Clock3, LogOut, RefreshCw, ShieldQuestion } from 'lucide-react'
import { useState } from 'react'
import { estadoLabel } from './types'
import type { Usuario } from './types'

interface PendingAccountProps {
  user: Usuario
  onRefresh: () => Promise<Usuario | null>
  onLogout: (message?: string) => void
}

export function PendingAccount({
  user,
  onRefresh,
  onLogout,
}: PendingAccountProps) {
  const [refreshing, setRefreshing] = useState(false)
  const [message, setMessage] = useState('')

  const handleRefresh = async () => {
    setRefreshing(true)
    setMessage('')
    try {
      const updated = await onRefresh()
      if (updated && (updated.rol || updated.estado !== 'PENDIENTE')) {
        setMessage('Tus permisos cambiaron. Vuelve a iniciar sesión.')
      } else {
        setMessage(
          'Tu cuenta sigue pendiente. Un administrador debe asignarte un rol.',
        )
      }
    } catch {
      setMessage('No se pudo consultar el estado de tu cuenta. Intenta nuevamente.')
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <main className="status-shell">
      <section className="status-card" aria-labelledby="pending-title">
        <span className="status-icon is-pending" aria-hidden="true">
          <Clock3 size={30} />
        </span>
        <span className="status-eyebrow">Cuenta creada correctamente</span>
        <h1 id="pending-title">Esperando asignación de rol</h1>
        <p className="status-copy">
          Hola, <strong>{user.nombre}</strong>. Tu cuenta existe, pero todavía no
          tiene permisos operativos. Un administrador debe asignarte un rol
          desde la administración de usuarios.
        </p>

        <dl className="status-details">
          <div>
            <dt>Cuenta</dt>
            <dd>{user.email}</dd>
          </div>
          <div>
            <dt>Rol</dt>
            <dd>Sin asignar</dd>
          </div>
          <div>
            <dt>Estado</dt>
            <dd>
              <span className="state-badge is-pending">
                {estadoLabel[user.estado]}
              </span>
            </dd>
          </div>
        </dl>

        <div className="status-note">
          <ShieldQuestion size={19} aria-hidden="true" />
          <p>
            Mientras la cuenta esté pendiente no es posible abrir el menú, la caja
            ni la cocina. Si ya te asignaron un rol, actualiza el estado o vuelve
            a iniciar sesión.
          </p>
        </div>

        {message && (
          <p className="status-message" role="status">
            {message}
          </p>
        )}

        <div className="status-actions">
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              void handleRefresh()
            }}
            disabled={refreshing}
          >
            <RefreshCw
              size={18}
              className={refreshing ? 'spin' : undefined}
              aria-hidden="true"
            />
            {refreshing ? 'Consultando…' : 'Actualizar estado'}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => onLogout()}
          >
            <LogOut size={18} aria-hidden="true" />
            Cerrar sesión
          </button>
        </div>
      </section>
    </main>
  )
}
