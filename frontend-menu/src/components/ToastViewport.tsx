import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react'
import type { ToastMessage } from '../hooks/useToasts'

interface ToastViewportProps {
  toasts: ToastMessage[]
  onDismiss: (id: number) => void
}

const icons = {
  success: CheckCircle2,
  error: AlertTriangle,
  info: Info,
}

export function ToastViewport({ toasts, onDismiss }: ToastViewportProps) {
  if (toasts.length === 0) {
    return null
  }

  return (
    <div className="toast-viewport" aria-live="polite" aria-atomic="false">
      {toasts.map((toast) => {
        const Icon = icons[toast.type]

        return (
          <div
            key={toast.id}
            className={`toast toast-${toast.type}`}
            role={toast.type === 'error' ? 'alert' : 'status'}
          >
            <span className="toast-icon" aria-hidden="true">
              <Icon size={19} />
            </span>
            <p>{toast.message}</p>
            <button
              type="button"
              className="toast-close"
              onClick={() => onDismiss(toast.id)}
              aria-label="Cerrar notificación"
            >
              <X size={15} aria-hidden="true" />
            </button>
          </div>
        )
      })}
    </div>
  )
}
