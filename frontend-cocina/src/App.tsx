import { Client, type IMessage } from '@stomp/stompjs'
import {
  Bell,
  Check,
  ChefHat,
  Clock3,
  Flame,
  LoaderCircle,
  Maximize2,
  RefreshCw,
  Search,
  Signal,
  SignalZero,
  Utensils,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

type Estado = 'PENDIENTE' | 'PREPARANDO' | 'LISTO'
type Orden = {
  id: number
  pedidoId: number
  numeroPedido: string
  estado: Estado
  fechaRecepcion: string
  fechaInicio: string | null
  fechaFinalizacion: string | null
  items: { productoId: number; nombreProducto: string; cantidad: number }[]
}
type NotificacionNueva = {
  tipo: string
  pedidoId: number
  numeroPedido: string
  estado: Estado
  items: { nombreProducto: string; cantidad: number }[]
}

const API = import.meta.env.VITE_API_URL?.replace(/\/$/, '') ?? ''
const columns: { status: Estado; label: string; helper: string }[] = [
  { status: 'PENDIENTE', label: 'Por preparar', helper: 'Nuevas comandas' },
  { status: 'PREPARANDO', label: 'En preparación', helper: 'En cocina ahora' },
  { status: 'LISTO', label: 'Listos', helper: 'Para entregar' },
]

function wsUrl() {
  const configured = import.meta.env.VITE_WS_URL as string | undefined
  if (configured) return configured
  return `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`
}

function displayTime(value: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return new Intl.DateTimeFormat('es-EC', { hour: '2-digit', minute: '2-digit' }).format(date)
}

function elapsed(value: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000))
  if (minutes < 1) return 'Ahora'
  return `${minutes} min`
}

function App() {
  const [orders, setOrders] = useState<Orden[]>([])
  const [connected, setConnected] = useState(false)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [busyIds, setBusyIds] = useState<number[]>([])
  const [toast, setToast] = useState('')
  const [soundEnabled, setSoundEnabled] = useState(false)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const soundRef = useRef<AudioContext | null>(null)
  const soundEnabledRef = useRef(soundEnabled)
  soundEnabledRef.current = soundEnabled

  const loadOrders = useCallback(async (quiet = false) => {
    if (quiet) setRefreshing(true)
    else setLoading(true)
    try {
      const response = await fetch(`${API}/ordenes`, { headers: { Accept: 'application/json' } })
      if (!response.ok) throw new Error(`No se pudieron cargar las órdenes (${response.status})`)
      const data = (await response.json()) as Orden[]
      setOrders(data.sort((a, b) => new Date(a.fechaRecepcion).getTime() - new Date(b.fechaRecepcion).getTime()))
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo conectar con el servicio de cocina')
    } finally {
      setLoading(false)
      setRefreshing(false)
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
      // El panel sigue siendo funcional aunque el navegador bloquee el audio.
    }
  }, [])

  useEffect(() => {
    void loadOrders()
    const poll = window.setInterval(() => void loadOrders(true), 20000)
    return () => window.clearInterval(poll)
  }, [loadOrders])

  useEffect(() => {
    function focusSearch(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        document.querySelector<HTMLInputElement>('.search-box input')?.focus()
      }
    }
    window.addEventListener('keydown', focusSearch)
    return () => window.removeEventListener('keydown', focusSearch)
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
            setToast(`Nueva comanda · ${event.numeroPedido}`)
            window.setTimeout(() => setToast(''), 4500)
            void loadOrders(true)
          } catch {
            setError('Se recibió una notificación de cocina con formato inválido')
          }
        })
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
    })
    client.activate()
    return () => {
      void client.deactivate()
      soundRef.current?.close()
      soundRef.current = null
    }
  }, [loadOrders, playChime])

  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('es')
    return orders.filter((order) =>
      !query || order.numeroPedido.toLocaleLowerCase('es').includes(query) || String(order.pedidoId).includes(query)
        || order.items.some((item) => item.nombreProducto.toLocaleLowerCase('es').includes(query)),
    )
  }, [orders, search])

  const counts = useMemo(() => ({
    PENDIENTE: orders.filter((order) => order.estado === 'PENDIENTE').length,
    PREPARANDO: orders.filter((order) => order.estado === 'PREPARANDO').length,
    LISTO: orders.filter((order) => order.estado === 'LISTO').length,
  }), [orders])

  async function changeStatus(order: Orden) {
    const next: Estado = order.estado === 'PENDIENTE' ? 'PREPARANDO' : 'LISTO'
    setBusyIds((current) => [...current, order.id])
    try {
      const response = await fetch(`${API}/ordenes/${order.id}/estado`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ estado: next }),
      })
      if (!response.ok) {
        const body = await response.json().catch(() => null) as { message?: string } | null
        if (response.status === 409 || response.status === 404) void loadOrders(true)
        throw new Error(body?.message ?? `No se pudo actualizar la orden (${response.status})`)
      }
      const updated = (await response.json()) as Orden
      setOrders((current) => current.map((item) => item.id === updated.id ? updated : item))
      setToast(next === 'LISTO' ? `${updated.numeroPedido} está listo` : `${updated.numeroPedido} pasó a preparación`)
      window.setTimeout(() => setToast(''), 3500)
    } catch (cause) {
      setToast(cause instanceof Error ? cause.message : 'Error al actualizar la orden')
      window.setTimeout(() => setToast(''), 5000)
    } finally {
      setBusyIds((current) => current.filter((id) => id !== order.id))
    }
  }

  async function toggleFullscreen() {
    try {
      if (!document.fullscreenElement) await document.documentElement.requestFullscreen()
      else await document.exitFullscreen()
      setIsFullscreen(Boolean(document.fullscreenElement))
    } catch {
      setToast('El navegador no permitió activar pantalla completa')
      window.setTimeout(() => setToast(''), 3500)
    }
  }

  return (
    <main className="app-shell">
      <div className="ambient ambient-one" aria-hidden="true" />
      <div className="ambient ambient-two" aria-hidden="true" />
      <header className="topbar glass-panel">
        <a className="brand" href="#inicio" aria-label="KDS Cocina, inicio">
          <span className="brand-mark"><ChefHat size={22} strokeWidth={2.2} /></span>
          <span><strong>mise.</strong><small>KITCHEN DISPLAY</small></span>
        </a>
        <div className="topbar-center">
          <span className="service-label"><span className="live-dot" /> SERVICIO EN CURSO</span>
          <span className="current-time"><Clock3 size={14} /> {new Intl.DateTimeFormat('es-EC', { weekday: 'short', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date())}</span>
        </div>
        <div className="top-actions">
          <span className={`connection-pill ${connected ? 'is-connected' : ''}`} title={connected ? 'WebSocket conectado' : 'Conectando WebSocket'}>
            {connected ? <Signal size={15} /> : <SignalZero size={15} />}
            <span>{connected ? 'En vivo' : 'Reconectando'}</span>
          </span>
          <button className={`icon-button ${soundEnabled ? 'active' : ''}`} onClick={() => setSoundEnabled((value) => !value)} aria-label={soundEnabled ? 'Desactivar sonido de nuevas comandas' : 'Activar sonido de nuevas comandas'} title="Aviso sonoro">
            <Bell size={17} />
          </button>
          <button className="icon-button desktop-only" onClick={() => void toggleFullscreen()} aria-label={isFullscreen ? 'Salir de pantalla completa' : 'Pantalla completa'} title="Pantalla completa">
            <Maximize2 size={17} />
          </button>
        </div>
      </header>

      <section className="welcome-row" id="inicio">
        <div>
          <div className="eyebrow"><span className="eyebrow-line" /> PANEL DE COCINA</div>
          <h1>Comandas <span>en tiempo real.</span></h1>
          <p className="subtitle">Todo en orden. Cada plato a su tiempo.</p>
        </div>
        <div className="summary-glass glass-panel">
          <div className="summary-icon"><Utensils size={18} /></div>
          <div><span className="summary-value">{orders.length}</span><span className="summary-label">órdenes de hoy</span></div>
          <div className="summary-divider" />
          <div><span className="summary-value highlight">{counts.PENDIENTE + counts.PREPARANDO}</span><span className="summary-label">en cocina</span></div>
        </div>
      </section>

      <section className="toolbar" aria-label="Controles de órdenes">
        <label className="search-box glass-panel">
          <Search size={17} aria-hidden="true" />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar orden o producto..." aria-label="Buscar por número de pedido o producto" />
          {search && <button className="clear-search" onClick={() => setSearch('')} aria-label="Limpiar búsqueda"><X size={15} /></button>}
          <kbd>⌘ K</kbd>
        </label>
        <button className="refresh-button glass-panel" onClick={() => void loadOrders(true)} disabled={refreshing}>
          <RefreshCw size={15} className={refreshing ? 'spin' : ''} />
          <span>{refreshing ? 'Actualizando' : 'Actualizar'}</span>
        </button>
        <span className="sync-caption">Actualización automática cada 20 s</span>
      </section>

      {error && (
        <div className="error-banner" role="alert">
          <span><SignalZero size={17} /> {error}</span>
          <button onClick={() => void loadOrders()}><RefreshCw size={14} /> Reintentar</button>
        </div>
      )}

      <section className="board" aria-label="Órdenes agrupadas por estado">
        {columns.map((column, index) => {
          const columnOrders = filtered.filter((order) => order.estado === column.status)
          return (
            <section className={`order-column column-${column.status.toLowerCase()}`} key={column.status} aria-labelledby={`heading-${column.status}`}>
              <header className="column-heading">
                <div className="column-title-wrap">
                  <span className="column-indicator" />
                  <div><h2 id={`heading-${column.status}`}>{column.label}</h2><p>{column.helper}</p></div>
                </div>
                <span className="count-badge" aria-label={`${counts[column.status]} órdenes`}>{counts[column.status].toString().padStart(2, '0')}</span>
              </header>
              <div className="column-content">
                {loading ? (
                  <div className="loading-state"><LoaderCircle className="spin" size={20} /> Cargando comandas...</div>
                ) : columnOrders.length ? columnOrders.map((order) => (
                  <OrderCard key={order.id} order={order} busy={busyIds.includes(order.id)} onAdvance={() => void changeStatus(order)} />
                )) : (
                  <div className="empty-column">
                    <span className="empty-icon">{index === 0 ? <Flame size={19} /> : index === 1 ? <ChefHat size={19} /> : <Check size={19} />}</span>
                    <span>{search ? 'Sin resultados' : index === 2 ? 'Aún no hay platos listos' : 'Todo al día por aquí'}</span>
                  </div>
                )}
              </div>
            </section>
          )
        })}
      </section>

      <footer className="footer-note"><span>mise. <i>·</i> KDS v1.0</span><span>Hecho para que cada plato llegue perfecto.</span></footer>
      {toast && <div className="toast" role="status" aria-live="polite"><span className="toast-mark"><Check size={15} /></span>{toast}</div>}
    </main>
  )
}

function OrderCard({ order, busy, onAdvance }: { order: Orden; busy: boolean; onAdvance: () => void }) {
  const preparing = order.estado === 'PREPARANDO'
  const itemsCount = order.items.reduce((total, item) => total + item.cantidad, 0)
  return (
    <article className={`order-card glass-panel ${preparing ? 'is-preparing' : ''}`}>
      <div className="card-topline">
        <span className="order-number">{order.numeroPedido}</span>
        <span className="order-elapsed"><Clock3 size={13} /> {elapsed(order.fechaRecepcion)}</span>
      </div>
      <div className="card-meta"><span>Pedido #{order.pedidoId}</span><span>Recibido {displayTime(order.fechaRecepcion)}</span></div>
      <div className="card-rule" />
      <ul className="items-list" aria-label="Productos del pedido">
        {order.items.map((item, index) => (
          <li key={`${item.productoId}-${index}`}><span className="item-quantity">{item.cantidad}<small>×</small></span><span className="item-name">{item.nombreProducto}</span></li>
        ))}
      </ul>
      <div className="card-bottomline">
        <span className="items-total"><Utensils size={13} /> {itemsCount} {itemsCount === 1 ? 'ítem' : 'ítems'}</span>
        {preparing && order.fechaInicio && <span className="started-at">Desde {displayTime(order.fechaInicio)}</span>}
      </div>
      {order.estado !== 'LISTO' && (
        <button className={`advance-button ${preparing ? 'ready-button' : ''}`} onClick={onAdvance} disabled={busy}>
          {busy ? <LoaderCircle size={16} className="spin" /> : preparing ? <Check size={16} /> : <Flame size={16} />}
          <span>{busy ? 'Guardando...' : preparing ? 'Marcar como listo' : 'Iniciar preparación'}</span>
          {!busy && <span className="button-arrow">→</span>}
        </button>
      )}
      {order.estado === 'LISTO' && <div className="ready-stamp"><Check size={14} /> Listo para entregar</div>}
    </article>
  )
}

export default App
