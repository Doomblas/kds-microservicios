import { Client, type IMessage } from '@stomp/stompjs'
import {
  AlertCircle,
  ArrowDownWideNarrow,
  Bell,
  BellOff,
  Check,
  CheckCheck,
  ChefHat,
  Clock3,
  Flame,
  Inbox,
  LogOut,
  Maximize2,
  Minimize2,
  RefreshCw,
  Search,
  Signal,
  SignalZero,
  Utensils,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { AccessDenied } from './auth/AccessDenied'
import { AuthLoading } from './auth/AuthLoading'
import { AuthScreen } from './auth/AuthScreen'
import { PendingAccount } from './auth/PendingAccount'
import {
  authHeaders,
  clearSession,
  notifyUnauthorized,
} from './auth/session'
import { rolLabel } from './auth/types'
import type { Usuario } from './auth/types'
import { useAuth } from './auth/useAuth'
import { KitchenPulse } from './components/KitchenPulse'
import { OrderCard } from './components/OrderCard'
import type { Estado, NotificacionNueva, Orden, Toast } from './types'
import { displayTime } from './utils/time'

const API = import.meta.env.VITE_API_URL?.replace(/\/$/, '') ?? ''
const columns = [
  { status: 'PENDIENTE', label: 'Por preparar', helper: 'El siguiente plato empieza aquí.', icon: Inbox },
  { status: 'PREPARANDO', label: 'En preparación', helper: 'Manos a la obra.', icon: Flame },
  { status: 'LISTO', label: 'Listos para entregar', helper: 'De cocina a la mesa.', icon: CheckCheck },
] as const

function wsUrl() {
  const configured = import.meta.env.VITE_WS_URL as string | undefined
  if (configured) return configured
  return `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`
}

function initials(nombre: string) {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

interface CocinaDashboardProps {
  user: Usuario
  onLogout: (message?: string) => void
}

function CocinaDashboard({ user, onLogout }: CocinaDashboardProps) {
  const [orders, setOrders] = useState<Orden[]>([])
  const [connected, setConnected] = useState(false)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<Estado | 'TODOS'>('TODOS')
  const [busyIds, setBusyIds] = useState<number[]>([])
  const [toast, setToast] = useState<Toast | null>(null)
  const [soundEnabled, setSoundEnabled] = useState(false)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [now, setNow] = useState(Date.now())
  const [lastSync, setLastSync] = useState<number | null>(null)
  const soundRef = useRef<AudioContext | null>(null)
  const soundEnabledRef = useRef(soundEnabled)
  const loadRequestRef = useRef<AbortController | null>(null)
  const busyRef = useRef(new Set<number>())
  const searchRef = useRef<HTMLInputElement>(null)
  const boardTitleRef = useRef<HTMLHeadingElement>(null)
  const shortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘ K' : 'Ctrl K'
  soundEnabledRef.current = soundEnabled

  const loadOrders = useCallback(async (quiet = false) => {
    loadRequestRef.current?.abort()
    const controller = new AbortController()
    loadRequestRef.current = controller
    if (quiet) setRefreshing(true)
    else setLoading(true)
    try {
      const response = await fetch(`${API}/ordenes`, { headers: { Accept: 'application/json', ...authHeaders() }, signal: controller.signal })
      if (!response.ok) {
        if (response.status === 401) {
          clearSession()
          notifyUnauthorized()
        }
        throw new Error(`El servicio de Cocina respondió con un error (${response.status}).`)
      }
      const data = (await response.json()) as Orden[]
      if (!Array.isArray(data)) throw new Error('El servicio devolvió una respuesta inesperada.')
      if (controller.signal.aborted) return
      setOrders(data.sort((a, b) => new Date(a.fechaRecepcion).getTime() - new Date(b.fechaRecepcion).getTime()))
      setLastSync(Date.now())
      setError('')
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : 'Revisa la conexión con el servicio de Cocina.')
      }
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false)
        setRefreshing(false)
      }
    }
  }, [])

  const playChime = useCallback(() => {
    if (!soundEnabledRef.current) return
    try {
      const context = soundRef.current ?? new AudioContext()
      soundRef.current = context
      const oscillator = context.createOscillator()
      const gain = context.createGain()
      oscillator.type = 'sine'
      oscillator.frequency.setValueAtTime(740, context.currentTime)
      oscillator.frequency.setValueAtTime(988, context.currentTime + 0.12)
      gain.gain.setValueAtTime(0.0001, context.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.12, context.currentTime + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.42)
      oscillator.connect(gain).connect(context.destination)
      oscillator.start()
      oscillator.stop(context.currentTime + 0.44)
    } catch {
      // El aviso visual permanece disponible si el navegador bloquea el audio.
    }
  }, [])

  useEffect(() => {
    void loadOrders()
    const poll = window.setInterval(() => void loadOrders(true), 20000)
    const clock = window.setInterval(() => setNow(Date.now()), 15000)
    return () => {
      window.clearInterval(poll)
      window.clearInterval(clock)
      loadRequestRef.current?.abort()
    }
  }, [loadOrders])

  useEffect(() => {
    if (!toast) return
    const timeout = window.setTimeout(() => setToast(null), toast.kind === 'error' ? 8000 : 5000)
    return () => window.clearTimeout(timeout)
  }, [toast])

  useEffect(() => {
    function focusSearch(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        searchRef.current?.focus()
      }
    }
    function syncFullscreen() {
      setIsFullscreen(Boolean(document.fullscreenElement))
    }
    window.addEventListener('keydown', focusSearch)
    document.addEventListener('fullscreenchange', syncFullscreen)
    return () => {
      window.removeEventListener('keydown', focusSearch)
      document.removeEventListener('fullscreenchange', syncFullscreen)
    }
  }, [])

  useEffect(() => {
    const client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/cocina', (message: IMessage) => {
          try {
            const event = JSON.parse(message.body) as NotificacionNueva
            if (event.tipo !== 'PEDIDO_CREADO') return
            playChime()
            setToast({ kind: 'info', message: `Nueva comanda · ${event.numeroPedido}` })
            void loadOrders(true)
          } catch {
            setToast({ kind: 'error', message: 'No se pudo leer una notificación. Actualiza el tablero para comprobar las comandas.' })
          }
        })
        // Recupera también las comandas creadas mientras el socket estaba desconectado.
        void loadOrders(true)
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
      onStompError: () => setConnected(false),
    })
    client.activate()
    return () => {
      void client.deactivate()
      void soundRef.current?.close()
      soundRef.current = null
    }
  }, [loadOrders, playChime])

  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('es')
    return orders.filter((order) =>
      (!query || order.numeroPedido.toLocaleLowerCase('es').includes(query) || String(order.pedidoId).includes(query)
        || order.items.some((item) => item.nombreProducto.toLocaleLowerCase('es').includes(query))),
    )
  }, [orders, search])

  const counts = useMemo(() => ({
    PENDIENTE: orders.filter((order) => order.estado === 'PENDIENTE').length,
    PREPARANDO: orders.filter((order) => order.estado === 'PREPARANDO').length,
    LISTO: orders.filter((order) => order.estado === 'LISTO').length,
  }), [orders])
  const visibleOrders = filtered.filter((order) => statusFilter === 'TODOS' || order.estado === statusFilter)
  const hasFilters = Boolean(search.trim()) || statusFilter !== 'TODOS'

  function clearFilters() {
    setSearch('')
    setStatusFilter('TODOS')
    searchRef.current?.focus()
  }

  async function changeStatus(order: Orden) {
    if (order.estado === 'LISTO' || busyRef.current.has(order.id)) return
    const next: Estado = order.estado === 'PENDIENTE' ? 'PREPARANDO' : 'LISTO'
    const focusedElement = document.activeElement
    const restoreFocus = focusedElement?.closest(`[data-order-id="${order.id}"]`)
    busyRef.current.add(order.id)
    setBusyIds([...busyRef.current])
    try {
      const response = await fetch(`${API}/ordenes/${order.id}/estado`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeaders() },
        body: JSON.stringify({ estado: next }),
      })
      if (!response.ok) {
        if (response.status === 401) {
          clearSession()
          notifyUnauthorized()
        }
        const body = await response.json().catch(() => null) as { message?: string } | null
        if (response.status === 409 || response.status === 404) void loadOrders(true)
        throw new Error(body?.message ?? `No se pudo actualizar la comanda (${response.status}).`)
      }
      const updated = (await response.json()) as Orden
      // Una lectura iniciada antes del PATCH no debe devolver la tarjeta al estado anterior.
      loadRequestRef.current?.abort()
      setRefreshing(false)
      setLoading(false)
      setOrders((current) => current.map((item) => item.id === updated.id ? updated : item))
      setToast({ kind: 'success', message: next === 'LISTO' ? `${updated.numeroPedido} está listo para entregar.` : `${updated.numeroPedido} pasó a preparación.` })
      if (restoreFocus) {
        window.requestAnimationFrame(() => {
          if (document.activeElement !== document.body && document.activeElement !== focusedElement) return
          const card = document.querySelector<HTMLElement>(`[data-order-id="${order.id}"]`)
          const target = card?.querySelector<HTMLElement>('.advance-button') ?? card?.querySelector<HTMLElement>('.order-number') ?? boardTitleRef.current
          target?.focus({ preventScroll: true })
        })
      }
    } catch (cause) {
      setToast({ kind: 'error', message: cause instanceof Error ? cause.message : 'No se guardó el cambio. Inténtalo de nuevo.' })
    } finally {
      busyRef.current.delete(order.id)
      setBusyIds([...busyRef.current])
    }
  }

  async function toggleSound() {
    if (soundEnabled) {
      setSoundEnabled(false)
      return
    }
    try {
      const context = soundRef.current ?? new AudioContext()
      soundRef.current = context
      await context.resume()
      setSoundEnabled(true)
      setToast({ kind: 'info', message: 'Aviso sonoro activado para nuevas comandas.' })
    } catch {
      setToast({ kind: 'error', message: 'El navegador no permitió activar el sonido. Los avisos visuales siguen disponibles.' })
    }
  }

  async function toggleFullscreen() {
    try {
      if (!document.fullscreenElement) await document.documentElement.requestFullscreen()
      else await document.exitFullscreen()
    } catch {
      setToast({ kind: 'error', message: 'El navegador no permitió cambiar a pantalla completa.' })
    }
  }

  const live = connected && !error

  return (
    <>
      <a className="skip-link" href="#tablero">Ir al tablero de comandas</a>
      <header className="topbar">
        <div className="topbar-inner">
          <a className="brand" href="#inicio" aria-label="EPICURE, panel de Cocina">
            <span className="brand-mark"><ChefHat size={23} aria-hidden="true" /></span>
            <span><strong>EPICURE <span>// Cocina</span></strong><small>Kitchen Display System</small></span>
          </a>
          <div className="top-actions">
            <span className={`connection-pill ${live ? 'is-connected' : ''}`} role="status">
              {live ? <Signal size={15} aria-hidden="true" /> : <SignalZero size={15} aria-hidden="true" />}
              {error ? 'Sin sincronizar' : connected ? 'En vivo' : 'Reconectando'}
            </span>
            <span className="header-divider" aria-hidden="true" />
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
            <button className="icon-button session-logout" type="button" onClick={() => onLogout()} aria-label="Cerrar sesión" title="Cerrar sesión">
              <LogOut size={19} aria-hidden="true" />
            </button>
            <button className={`icon-button ${soundEnabled ? 'active' : ''}`} type="button" onClick={() => void toggleSound()} aria-pressed={soundEnabled} aria-label="Aviso sonoro de nuevas comandas" title={soundEnabled ? 'Desactivar sonido' : 'Activar sonido'}>
              {soundEnabled ? <Bell size={19} aria-hidden="true" /> : <BellOff size={19} aria-hidden="true" />}
            </button>
            {document.fullscreenEnabled && <button className="icon-button fullscreen-button" type="button" onClick={() => void toggleFullscreen()} aria-label={isFullscreen ? 'Salir de pantalla completa' : 'Pantalla completa'} title={isFullscreen ? 'Salir de pantalla completa' : 'Pantalla completa'}>
              {isFullscreen ? <Minimize2 size={19} aria-hidden="true" /> : <Maximize2 size={19} aria-hidden="true" />}
            </button>}
          </div>
        </div>
      </header>

      <main className="app-shell" id="inicio">
        <section className="hero" aria-labelledby="page-title">
          <div className="hero-copy">
            <div className="eyebrow">SERVICIO MS-DISPLAY-COCINA<span className="service-tag">Estación de cocina</span></div>
            <h1 id="page-title">Cada comanda, <span>a su tiempo.</span></h1>
            <p>Tu cocina, en un solo lugar. Recibe las comandas y acompaña cada plato hasta que esté listo.</p>
            <span className="current-time"><Clock3 size={15} aria-hidden="true" /><time dateTime={new Date(now).toISOString()}>{new Intl.DateTimeFormat('es-EC', { weekday: 'long', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }).format(now)}</time></span>
          </div>
          <KitchenPulse pending={counts.PENDIENTE} preparing={counts.PREPARANDO} loading={loading || (Boolean(error) && lastSync === null)} />
        </section>

        <section className="metrics" aria-label="Resumen de comandas">
          {[
            { label: 'Por preparar', value: counts.PENDIENTE, helper: 'Esperando entrar a cocina', icon: Inbox, className: 'metric-pending' },
            { label: 'En preparación', value: counts.PREPARANDO, helper: 'En manos del equipo', icon: Flame, className: 'metric-featured' },
            { label: 'Listas para entregar', value: counts.LISTO, helper: 'El siguiente paso es la mesa', icon: CheckCheck, className: 'metric-ready' },
            { label: 'Total de comandas', value: orders.length, helper: 'Registradas en el servicio', icon: Utensils, className: '' },
          ].map((metric) => (
            <div className={`metric-card ${metric.className}`} key={metric.label}>
              <div className="metric-topline"><span>{metric.label}</span><span className="metric-icon"><metric.icon size={18} aria-hidden="true" /></span></div>
              <strong>{loading || (error && lastSync === null) ? '—' : metric.value.toString().padStart(2, '0')}</strong>
              <small>{metric.helper}</small>
            </div>
          ))}
        </section>

        <section className="controls" aria-label="Controles de comandas">
          <div className="toolbar">
            <div className="search-box">
              <Search size={19} aria-hidden="true" />
              <label className="sr-only" htmlFor="order-search">Buscar por pedido o producto</label>
              <input id="order-search" ref={searchRef} type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar pedido o producto…" aria-describedby="search-hint" />
              {search ? <button className="clear-search" type="button" onClick={() => { setSearch(''); searchRef.current?.focus() }} aria-label="Limpiar búsqueda"><X size={17} aria-hidden="true" /></button> : <kbd aria-hidden="true">{shortcut}</kbd>}
              <span className="sr-only" id="search-hint">Busca por número de pedido, identificador o nombre de producto. Atajo: Control o Comando más K.</span>
            </div>
            <div className="sync-controls">
              <span className="sync-caption">{error ? 'Datos sin sincronizar' : lastSync ? <>Última lectura <time dateTime={new Date(lastSync).toISOString()}>{displayTime(lastSync)}</time></> : 'Conectando con Cocina…'}</span>
              <button className="refresh-button" type="button" onClick={() => void loadOrders(true)} disabled={refreshing || loading}>
                <RefreshCw size={17} className={refreshing ? 'spin' : ''} aria-hidden="true" /><span>{refreshing ? 'Actualizando…' : 'Actualizar'}</span>
              </button>
            </div>
          </div>
          <div className="filter-row">
            <div className="status-filters" role="group" aria-label="Filtrar por estado">
              {[{ status: 'TODOS', label: 'Todas', count: orders.length }, ...columns.map((column) => ({ ...column, count: counts[column.status] }))].map((filter) => (
                <button type="button" className={`filter-pill ${statusFilter === filter.status ? 'is-active' : ''}`} key={filter.status} aria-pressed={statusFilter === filter.status} onClick={() => setStatusFilter(filter.status as Estado | 'TODOS')}>
                  {filter.label}<span>{loading || (error && lastSync === null) ? '—' : filter.count}</span>
                </button>
              ))}
            </div>
            <span className="auto-refresh-note">Respaldo automático cada 20 s</span>
          </div>
        </section>

        {error && <div className="error-banner" role="alert">
          <AlertCircle size={20} aria-hidden="true" />
          <div><strong>No pudimos sincronizar el tablero</strong><p>{error} {lastSync ? 'Se conservan los últimos datos recibidos.' : 'Aún no se han recibido comandas.'}</p></div>
          <button type="button" className="retry-button" onClick={() => void loadOrders(lastSync !== null)} disabled={refreshing || loading}><RefreshCw size={16} aria-hidden="true" />Reintentar</button>
        </div>}

        {!connected && !error && !loading && <div className="connection-note"><SignalZero size={16} aria-hidden="true" /><span>Reconectando las notificaciones en vivo. El tablero sigue consultando las comandas cada 20 segundos.</span></div>}

        <section className="board-section" id="tablero" tabIndex={-1} aria-labelledby="board-title">
          <div className="board-title-row">
            <div><h2 id="board-title" ref={boardTitleRef} tabIndex={-1}>Flujo de comandas</h2><p aria-live="polite">{loading ? 'Cargando comandas…' : hasFilters ? `${visibleOrders.length} de ${orders.length} comandas coinciden con los filtros` : 'De la primera comanda al último detalle.'}</p></div>
            <span className="sort-caption"><ArrowDownWideNarrow size={16} aria-hidden="true" />Más antiguas primero</span>
          </div>

          {hasFilters && !loading && <div className="active-filters"><span>{search.trim() ? <>Buscando: <strong>“{search.trim()}”</strong></> : 'Vista filtrada por estado'}</span><button type="button" onClick={clearFilters}><X size={15} aria-hidden="true" />Limpiar filtros</button></div>}

          <div className={`board ${statusFilter !== 'TODOS' ? 'board-filtered' : ''}`}>
            {columns.filter((column) => statusFilter === 'TODOS' || column.status === statusFilter).map((column) => {
              const columnOrders = filtered.filter((order) => order.estado === column.status)
              return <section className={`order-column column-${column.status.toLowerCase()}`} key={column.status} aria-labelledby={`heading-${column.status}`}>
                <header className="column-heading">
                  <div className="column-title-wrap"><span className="column-icon"><column.icon size={19} aria-hidden="true" /></span><div><h3 id={`heading-${column.status}`}>{column.label}</h3><p>{column.helper}</p></div></div>
                  <span className="count-badge" aria-label={`${columnOrders.length} comandas visibles`}>{loading ? '—' : columnOrders.length.toString().padStart(2, '0')}</span>
                </header>
                <div className="column-content" aria-busy={loading}>
                  {loading ? <div className="loading-state" role="status"><span className="sr-only">Cargando {column.label.toLowerCase()}</span>{[0, 1].map((item) => <div className="skeleton-card" aria-hidden="true" key={item}><div /><div /><div /><div /></div>)}</div> : columnOrders.length ? columnOrders.map((order) => (
                    <OrderCard key={order.id} order={order} now={now} busy={busyIds.includes(order.id)} onAdvance={() => void changeStatus(order)} />
                  )) : <div className="empty-column">
                    <span className="empty-icon">{search.trim() ? <Search size={24} aria-hidden="true" /> : <column.icon size={24} aria-hidden="true" />}</span>
                    <strong>{error && lastSync === null ? 'Esperando conexión' : search.trim() ? 'Sin coincidencias' : column.status === 'LISTO' ? 'El pase está despejado' : column.status === 'PREPARANDO' ? 'Todo listo para empezar' : 'Al día por aquí'}</strong>
                    <p>{error && lastSync === null ? 'Reintenta la conexión para consultar las comandas.' : search.trim() ? 'Prueba con otro pedido o producto, o limpia la búsqueda.' : column.status === 'LISTO' ? 'Las comandas terminadas aparecerán aquí.' : column.status === 'PREPARANDO' ? 'Inicia una comanda para verla en esta columna.' : 'Las nuevas comandas llegarán a esta columna.'}</p>
                  </div>}
                </div>
              </section>
            })}
          </div>
        </section>

        <footer className="footer-note"><span><ChefHat size={15} aria-hidden="true" />EPICURE <span>// Cocina</span></span><span>Recibir. Preparar. Entregar.</span></footer>
      </main>

      {toast && <div className={`toast toast-${toast.kind}`} role={toast.kind === 'error' ? 'alert' : 'status'}>
        <span className="toast-mark">{toast.kind === 'error' ? <AlertCircle size={19} aria-hidden="true" /> : toast.kind === 'info' ? <Bell size={19} aria-hidden="true" /> : <Check size={19} aria-hidden="true" />}</span>
        <span>{toast.message}</span><button type="button" onClick={() => setToast(null)} aria-label="Cerrar aviso"><X size={18} aria-hidden="true" /></button>
      </div>}
    </>
  )
}

function App() {
  const {
    status,
    user,
    notice,
    login,
    register,
    logout,
    refreshUser,
    clearNotice,
  } = useAuth()

  if (status === 'loading') {
    return <AuthLoading />
  }

  if (status === 'anonymous' || !user) {
    return (
      <AuthScreen
        notice={notice}
        onLogin={login}
        onRegister={register}
        onClearNotice={clearNotice}
      />
    )
  }

  if (user.estado === 'BLOQUEADO') {
    return <AccessDenied user={user} onLogout={logout} reason="blocked" />
  }

  if (user.estado === 'PENDIENTE' || !user.rol) {
    return (
      <PendingAccount
        user={user}
        onRefresh={refreshUser}
        onLogout={logout}
      />
    )
  }

  if (user.rol !== 'ADMIN' && user.rol !== 'COCINA') {
    return (
      <AccessDenied
        user={user}
        onLogout={logout}
        moduleName="la estación de cocina"
        allowedRoles="Administrador o Cocina"
      />
    )
  }

  return <CocinaDashboard user={user} onLogout={logout} />
}

export default App
