import { Client, type IMessage } from '@stomp/stompjs'
import {
  AlertCircle,
  CheckCircle2,
  Clock3,
  LogOut,
  Receipt,
  ShoppingBag,
  Signal,
  SignalZero,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { AccessDenied } from './auth/AccessDenied'
import { AuthLoading } from './auth/AuthLoading'
import { AuthScreen } from './auth/AuthScreen'
import { PendingAccount } from './auth/PendingAccount'
import { rolLabel } from './auth/types'
import type { Usuario } from './auth/types'
import { useAuth } from './auth/useAuth'
import { CartPanel } from './components/CartPanel'
import { PedidoList } from './components/PedidoList'
import { ProductPicker } from './components/ProductPicker'
import { getErrorMessage } from './services/http'
import { menuCatalogApi } from './services/menuCatalogApi'
import { pedidosApi } from './services/pedidosApi'
import type {
  CartItem,
  EstadoPedido,
  NotificacionCaja,
  Pedido,
  ProductoMenu,
  Toast,
} from './types/pedidos'

function wsUrl() {
  const configured = import.meta.env.VITE_WS_URL
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

interface PedidosDashboardProps {
  user: Usuario
  onLogout: (message?: string) => void
}

function PedidosDashboard({ user, onLogout }: PedidosDashboardProps) {
  const [productos, setProductos] = useState<ProductoMenu[]>([])
  const [pedidos, setPedidos] = useState<Pedido[]>([])
  const [cart, setCart] = useState<CartItem[]>([])
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<EstadoPedido | 'TODOS'>('TODOS')
  const [loadingCatalog, setLoadingCatalog] = useState(true)
  const [loadingOrders, setLoadingOrders] = useState(true)
  const [refreshingOrders, setRefreshingOrders] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [connected, setConnected] = useState(false)
  const [catalogError, setCatalogError] = useState('')
  const [ordersError, setOrdersError] = useState('')
  const [toast, setToast] = useState<Toast | null>(null)
  const [now, setNow] = useState(Date.now())

  const loadCatalog = useCallback(async () => {
    setLoadingCatalog(true)
    try {
      const data = await menuCatalogApi.getProductos()
      setProductos(Array.isArray(data) ? data : [])
      setCatalogError('')
    } catch (error) {
      setCatalogError(
        getErrorMessage(error, 'No se pudo cargar el catálogo de ms-menu.'),
      )
    } finally {
      setLoadingCatalog(false)
    }
  }, [])

  const loadOrders = useCallback(async (quiet = false) => {
    if (quiet) setRefreshingOrders(true)
    else setLoadingOrders(true)
    try {
      const data = await pedidosApi.listar()
      const sorted = (Array.isArray(data) ? data : []).sort(
        (a, b) =>
          new Date(b.fechaCreacion).getTime() -
          new Date(a.fechaCreacion).getTime(),
      )
      setPedidos(sorted)
      setOrdersError('')
    } catch (error) {
      setOrdersError(
        getErrorMessage(error, 'No se pudo cargar los pedidos de ms-pedidos.'),
      )
    } finally {
      setLoadingOrders(false)
      setRefreshingOrders(false)
    }
  }, [])

  useEffect(() => {
    void loadCatalog()
    void loadOrders()
    const clock = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(clock)
  }, [loadCatalog, loadOrders])

  useEffect(() => {
    if (!toast) return
    const timeout = window.setTimeout(
      () => setToast(null),
      toast.kind === 'error' ? 8000 : 4500,
    )
    return () => window.clearTimeout(timeout)
  }, [toast])

  useEffect(() => {
    const client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/pedidos', (message: IMessage) => {
          try {
            const event = JSON.parse(message.body) as NotificacionCaja
            if (event.tipo !== 'ESTADO_ACTUALIZADO') return
            setPedidos((current) =>
              current.map((pedido) =>
                pedido.id === event.pedidoId
                  ? { ...pedido, estado: event.estado }
                  : pedido,
              ),
            )
            setToast({
              kind: 'info',
              message: `${event.numeroPedido} pasó a ${event.estado}.`,
            })
          } catch {
            setToast({
              kind: 'error',
              message: 'No se pudo leer una notificación de caja.',
            })
          }
        })
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
    }
  }, [loadOrders])

  const counts = useMemo(
    () => ({
      PENDIENTE: pedidos.filter((pedido) => pedido.estado === 'PENDIENTE').length,
      PREPARANDO: pedidos.filter((pedido) => pedido.estado === 'PREPARANDO')
        .length,
      LISTO: pedidos.filter((pedido) => pedido.estado === 'LISTO').length,
    }),
    [pedidos],
  )

  function upsertCart(producto: ProductoMenu, delta: number) {
    setCart((current) => {
      const existing = current.find((item) => item.productoId === producto.id)
      if (!existing) {
        if (delta <= 0) return current
        return [
          ...current,
          {
            productoId: producto.id,
            nombre: producto.nombre,
            precio: Number(producto.precio),
            cantidad: delta,
          },
        ]
      }

      const nextQuantity = existing.cantidad + delta
      if (nextQuantity <= 0) {
        return current.filter((item) => item.productoId !== producto.id)
      }

      return current.map((item) =>
        item.productoId === producto.id
          ? { ...item, cantidad: nextQuantity }
          : item,
      )
    })
  }

  function changeQuantity(productoId: number, delta: number) {
    const producto = productos.find((item) => item.id === productoId)
    if (!producto) {
      setCart((current) => {
        const existing = current.find((item) => item.productoId === productoId)
        if (!existing) return current
        const nextQuantity = existing.cantidad + delta
        if (nextQuantity <= 0) {
          return current.filter((item) => item.productoId !== productoId)
        }
        return current.map((item) =>
          item.productoId === productoId
            ? { ...item, cantidad: nextQuantity }
            : item,
        )
      })
      return
    }
    upsertCart(producto, delta)
  }

  async function handleSubmit() {
    if (cart.length === 0 || submitting) return
    setSubmitting(true)
    try {
      const created = await pedidosApi.crear({
        items: cart.map((item) => ({
          productoId: item.productoId,
          cantidad: item.cantidad,
        })),
      })
      setCart([])
      setPedidos((current) => [created, ...current])
      setToast({
        kind: 'success',
        message: `${created.numeroPedido} enviado a cocina.`,
      })
    } catch (error) {
      setToast({
        kind: 'error',
        message: getErrorMessage(error, 'No se pudo crear el pedido.'),
      })
    } finally {
      setSubmitting(false)
    }
  }

  const live = connected && !ordersError

  return (
    <>
      <a className="skip-link" href="#caja">
        Ir a la caja de pedidos
      </a>

      <header className="topbar">
        <div className="topbar-inner">
          <a className="brand" href="#inicio" aria-label="EPICURE, panel de Pedidos">
            <span className="brand-mark">
              <ShoppingBag size={22} aria-hidden="true" />
            </span>
            <span>
              <strong>
                EPICURE <span>// Pedidos</span>
              </strong>
              <small>Caja · Kitchen Display System</small>
            </span>
          </a>
          <div className="topbar-actions">
            <span
              className={`connection-pill ${live ? 'is-connected' : ''}`}
              role="status"
            >
              {live ? (
                <Signal size={15} aria-hidden="true" />
              ) : (
                <SignalZero size={15} aria-hidden="true" />
              )}
              {ordersError
                ? 'Sin sincronizar'
                : connected
                  ? 'En vivo'
                  : 'Reconectando'}
            </span>

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
              onClick={() => onLogout()}
              aria-label="Cerrar sesión"
              title="Cerrar sesión"
            >
              <LogOut size={18} aria-hidden="true" />
            </button>
          </div>
        </div>
      </header>

      <main className="app-shell" id="inicio">
        <section className="hero" aria-labelledby="page-title">
          <div className="hero-copy">
            <div className="eyebrow">
              SERVICIO MS-PEDIDOS
              <span className="service-tag">Estación de caja</span>
            </div>
            <h1 id="page-title">
              Toma el pedido. <span>Cocina lo prepara.</span>
            </h1>
            <p>
              Arma la comanda con el catálogo de menú, envíala a cocina y sigue
              el estado en tiempo real.
            </p>
            <span className="current-time">
              <Clock3 size={15} aria-hidden="true" />
              <time dateTime={new Date(now).toISOString()}>
                {new Intl.DateTimeFormat('es-EC', {
                  weekday: 'long',
                  day: 'numeric',
                  month: 'short',
                  hour: '2-digit',
                  minute: '2-digit',
                  hour12: false,
                }).format(now)}
              </time>
            </span>
          </div>
          <div className="hero-card">
            <div className="hero-card-topline">
              <Receipt size={18} aria-hidden="true" />
              Flujo de caja
            </div>
            <strong>{pedidos.length.toString().padStart(2, '0')}</strong>
            <p>Pedidos registrados en ms-pedidos.</p>
            <div className="hero-card-footer">
              <span>{counts.PENDIENTE} pendientes</span>
              <span>{counts.PREPARANDO} en cocina</span>
              <span>{counts.LISTO} listos</span>
            </div>
          </div>
        </section>

        <section className="metrics" aria-label="Resumen de pedidos">
          {[
            {
              label: 'Pendientes',
              value: counts.PENDIENTE,
              helper: 'Esperando cocina',
              className: 'metric-pending',
            },
            {
              label: 'En preparación',
              value: counts.PREPARANDO,
              helper: 'Avance desde cocina',
              className: 'metric-featured',
            },
            {
              label: 'Listos',
              value: counts.LISTO,
              helper: 'Listos para entregar',
              className: 'metric-ready',
            },
            {
              label: 'Total',
              value: pedidos.length,
              helper: 'En ms-pedidos',
              className: '',
            },
          ].map((metric) => (
            <div className={`metric-card ${metric.className}`} key={metric.label}>
              <span>{metric.label}</span>
              <strong>
                {loadingOrders ? '—' : metric.value.toString().padStart(2, '0')}
              </strong>
              <small>{metric.helper}</small>
            </div>
          ))}
        </section>

        {(catalogError || ordersError) && (
          <div className="error-banner" role="alert">
            <AlertCircle size={20} aria-hidden="true" />
            <div>
              <strong>Hay un problema de sincronización</strong>
              <p>{catalogError || ordersError}</p>
            </div>
            <button
              type="button"
              className="retry-button"
              onClick={() => {
                if (catalogError) void loadCatalog()
                if (ordersError) void loadOrders()
              }}
            >
              Reintentar
            </button>
          </div>
        )}

        <div className="workspace" id="caja">
          <ProductPicker
            productos={productos}
            cart={cart}
            query={query}
            loading={loadingCatalog}
            onQueryChange={setQuery}
            onAdd={(producto) => upsertCart(producto, 1)}
            onDecrease={(productoId) => changeQuantity(productoId, -1)}
          />

          <div className="side-column">
            <CartPanel
              cart={cart}
              submitting={submitting}
              onClear={() => setCart([])}
              onIncrease={(productoId) => changeQuantity(productoId, 1)}
              onDecrease={(productoId) => changeQuantity(productoId, -1)}
              onSubmit={() => {
                void handleSubmit()
              }}
            />
            <PedidoList
              pedidos={pedidos}
              loading={loadingOrders}
              refreshing={refreshingOrders}
              filter={filter}
              onFilterChange={setFilter}
              onRefresh={() => {
                void loadOrders(true)
              }}
            />
          </div>
        </div>

        <footer className="footer-note">
          <span>
            <ShoppingBag size={15} aria-hidden="true" />
            EPICURE <span>// Pedidos</span>
          </span>
          <span>Catálogo · Comanda · Estado</span>
        </footer>
      </main>

      {toast && (
        <div
          className={`toast toast-${toast.kind}`}
          role={toast.kind === 'error' ? 'alert' : 'status'}
        >
          <span className="toast-mark">
            {toast.kind === 'error' ? (
              <AlertCircle size={19} aria-hidden="true" />
            ) : toast.kind === 'success' ? (
              <CheckCircle2 size={19} aria-hidden="true" />
            ) : (
              <Signal size={19} aria-hidden="true" />
            )}
          </span>
          <span>{toast.message}</span>
          <button
            type="button"
            onClick={() => setToast(null)}
            aria-label="Cerrar aviso"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>
      )}
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

  if (user.rol !== 'ADMIN' && user.rol !== 'CAJA') {
    return (
      <AccessDenied
        user={user}
        onLogout={logout}
        moduleName="la caja de pedidos"
        allowedRoles="Administrador o Caja"
      />
    )
  }

  return <PedidosDashboard user={user} onLogout={logout} />
}

export default App
