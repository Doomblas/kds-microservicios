import { Ban, LogOut, ShieldAlert } from 'lucide-react'
import { estadoLabel, rolLabel } from './types'
import type { Usuario } from './types'

interface AccessDeniedProps {
  user: Usuario
  onLogout: (message?: string) => void
  reason?: 'role' | 'blocked'
  moduleName?: string
  allowedRoles?: string
}

export function AccessDenied({
  user,
  onLogout,
  reason = 'role',
  moduleName = 'este módulo',
  allowedRoles = 'Administrador o Gestor de menú',
}: AccessDeniedProps) {
  const blocked = reason === 'blocked'

  return (
    <main className="status-shell">
      <section
        className={`status-card is-denied ${blocked ? 'is-blocked' : ''}`}
        aria-labelledby="denied-title"
      >
        <span className="status-icon is-denied" aria-hidden="true">
          {blocked ? <Ban size={30} /> : <ShieldAlert size={30} />}
        </span>
        <span className="status-eyebrow">
          {blocked ? 'Acceso suspendido' : 'Permisos insuficientes'}
        </span>
        <h1 id="denied-title">
          {blocked ? 'Tu cuenta está bloqueada' : 'No tienes acceso a este módulo'}
        </h1>
        <p className="status-copy">
          {blocked ? (
            <>
              Un administrador bloqueó la cuenta <strong>{user.email}</strong>.
              Contacta al administrador para recuperar el acceso.
            </>
          ) : (
            <>
              Hola, <strong>{user.nombre}</strong>. Tu rol actual no permite abrir{' '}
              {moduleName}. Este espacio requiere {allowedRoles}.
            </>
          )}
        </p>

        <dl className="status-details">
          <div>
            <dt>Cuenta</dt>
            <dd>{user.email}</dd>
          </div>
          <div>
            <dt>Rol</dt>
            <dd>{user.rol ? rolLabel[user.rol] : 'Sin asignar'}</dd>
          </div>
          <div>
            <dt>Estado</dt>
            <dd>
              <span
                className={`state-badge ${
                  blocked ? 'is-blocked' : 'is-active'
                }`}
              >
                {estadoLabel[user.estado]}
              </span>
            </dd>
          </div>
        </dl>

        {!blocked && (
          <p className="status-note is-plain">
            Si crees que se trata de un error, solicita a un administrador que
            revise tu rol o ingresa con una cuenta autorizada.
          </p>
        )}

        <div className="status-actions">
          <button
            type="button"
            className="btn btn-primary"
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
