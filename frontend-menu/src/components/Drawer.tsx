import { X } from 'lucide-react'
import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react'

interface DrawerProps {
  open: boolean
  title: string
  subtitle?: string
  busy?: boolean
  onClose: () => void
  children: ReactNode
  footer: ReactNode
}

const EXIT_DURATION_MS = 280

function usePresence(open: boolean) {
  const [mounted, setMounted] = useState(open)

  useEffect(() => {
    if (open) {
      setMounted(true)
      return
    }

    const timer = window.setTimeout(() => setMounted(false), EXIT_DURATION_MS)
    return () => window.clearTimeout(timer)
  }, [open])

  return mounted
}

export function Drawer({
  open,
  title,
  subtitle,
  busy = false,
  onClose,
  children,
  footer,
}: DrawerProps) {
  const mounted = usePresence(open)
  const titleId = useId()
  const panelRef = useRef<HTMLElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)
  const onCloseRef = useRef(onClose)
  const busyRef = useRef(busy)
  onCloseRef.current = onClose
  busyRef.current = busy

  useEffect(() => {
    if (!open) {
      return
    }

    previousFocusRef.current = document.activeElement as HTMLElement | null
    const originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const focusTimer = window.setTimeout(() => {
      const panel = panelRef.current
      if (!panel) {
        return
      }

      const firstField = panel.querySelector<HTMLElement>(
        'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled])',
      )
      firstField?.focus()
    }, 40)

    return () => {
      window.clearTimeout(focusTimer)
      document.body.style.overflow = originalOverflow
      previousFocusRef.current?.focus()
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      return
    }

    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape' && !busyRef.current) {
        onCloseRef.current()
      }
    }

    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [open])

  if (!mounted) {
    return null
  }

  const handleTabTrap = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key !== 'Tab' || !panelRef.current) {
      return
    }

    const focusable = Array.from(
      panelRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
      ),
    ).filter((element) => element.offsetParent !== null)

    if (focusable.length === 0) {
      return
    }

    const first = focusable[0]
    const last = focusable[focusable.length - 1]

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div
      className={`drawer-layer ${open ? 'is-open' : ''}`}
      aria-hidden={!open}
    >
      <div
        className="drawer-backdrop"
        aria-hidden="true"
        onClick={() => {
          if (!busy) {
            onClose()
          }
        }}
      />

      <aside
        ref={panelRef}
        className="drawer-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={handleTabTrap}
      >
        <header className="drawer-header">
          <div>
            <span className="drawer-eyebrow">ms-menu</span>
            <h2 id={titleId}>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button
            type="button"
            className="drawer-close"
            onClick={onClose}
            disabled={busy}
            aria-label="Cerrar panel"
          >
            <X size={21} aria-hidden="true" />
          </button>
        </header>

        <div className="drawer-body">{children}</div>
        <footer className="drawer-footer">{footer}</footer>
      </aside>
    </div>
  )
}
