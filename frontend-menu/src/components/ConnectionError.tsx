import { RefreshCw, WifiOff } from 'lucide-react'
import { MENU_API_BASE_URL } from '../services/menuApi'

interface ConnectionErrorProps {
  message: string
  refreshing: boolean
  onRetry: () => void
}

export function ConnectionError({
  message,
  refreshing,
  onRetry,
}: ConnectionErrorProps) {
  return (
    <section className="connection-error" role="alert">
      <span className="connection-error-icon" aria-hidden="true">
        <WifiOff size={32} />
      </span>
      <h2>No se pudo conectar con ms-menu</h2>
      <p>{message}</p>
      <p className="connection-hint">
        Endpoint configurado: <code>{MENU_API_BASE_URL}</code>
      </p>
      <button
        type="button"
        className="btn btn-primary"
        onClick={onRetry}
        disabled={refreshing}
      >
        <RefreshCw
          size={17}
          className={refreshing ? 'spin' : undefined}
          aria-hidden="true"
        />
        {refreshing ? 'Reintentando…' : 'Reintentar conexión'}
      </button>
    </section>
  )
}
