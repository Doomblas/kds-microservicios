import { ClipboardList, RefreshCw } from 'lucide-react'
import type { EstadoPedido, Pedido } from '../types/pedidos'
import { estadoLabel, formatDateTime, formatPrice } from '../utils/format'

interface PedidoListProps {
  pedidos: Pedido[]
  loading: boolean
  refreshing: boolean
  filter: EstadoPedido | 'TODOS'
  onFilterChange: (value: EstadoPedido | 'TODOS') => void
  onRefresh: () => void
}

const filters: Array<{ value: EstadoPedido | 'TODOS'; label: string }> = [
  { value: 'TODOS', label: 'Todos' },
  { value: 'PENDIENTE', label: 'Pendientes' },
  { value: 'PREPARANDO', label: 'Preparando' },
  { value: 'LISTO', label: 'Listos' },
]

export function PedidoList({
  pedidos,
  loading,
  refreshing,
  filter,
  onFilterChange,
  onRefresh,
}: PedidoListProps) {
  const visible =
    filter === 'TODOS'
      ? pedidos
      : pedidos.filter((pedido) => pedido.estado === filter)

  const counts = {
    TODOS: pedidos.length,
    PENDIENTE: pedidos.filter((pedido) => pedido.estado === 'PENDIENTE').length,
    PREPARANDO: pedidos.filter((pedido) => pedido.estado === 'PREPARANDO')
      .length,
    LISTO: pedidos.filter((pedido) => pedido.estado === 'LISTO').length,
  }

  return (
    <section className="panel" aria-labelledby="orders-title">
      <div className="panel-heading">
        <div>
          <h2 id="orders-title">Pedidos recientes</h2>
          <p>Estados sincronizados desde cocina por WebSocket.</p>
        </div>
        <button
          type="button"
          className="icon-button"
          onClick={onRefresh}
          disabled={refreshing || loading}
          aria-label="Actualizar pedidos"
          title="Actualizar pedidos"
        >
          <RefreshCw
            size={18}
            className={refreshing ? 'spin' : undefined}
            aria-hidden="true"
          />
        </button>
      </div>

      <div className="filter-row" role="group" aria-label="Filtrar por estado">
        {filters.map((item) => (
          <button
            key={item.value}
            type="button"
            className={`filter-pill ${filter === item.value ? 'is-active' : ''}`}
            aria-pressed={filter === item.value}
            onClick={() => onFilterChange(item.value)}
          >
            {item.label}
            <span>{counts[item.value]}</span>
          </button>
        ))}
      </div>

      {loading ? (
        <div className="skeleton-list" aria-busy="true">
          {[0, 1, 2].map((item) => (
            <div className="skeleton-row" key={item} />
          ))}
        </div>
      ) : visible.length === 0 ? (
        <div className="empty-block compact">
          <ClipboardList size={26} aria-hidden="true" />
          <strong>Sin pedidos en esta vista</strong>
          <p>
            {pedidos.length === 0
              ? 'Cuando envíes la primera comanda aparecerá aquí.'
              : 'Cambia el filtro para ver otros estados.'}
          </p>
        </div>
      ) : (
        <ul className="pedido-list">
          {visible.map((pedido) => (
            <li key={pedido.id} className={`pedido-card estado-${pedido.estado.toLowerCase()}`}>
              <div>
                <strong>{pedido.numeroPedido}</strong>
                <small>{formatDateTime(pedido.fechaCreacion)}</small>
              </div>
              <div className="pedido-meta">
                <span className="estado-badge">{estadoLabel(pedido.estado)}</span>
                <span className="pedido-total">{formatPrice(pedido.total)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
