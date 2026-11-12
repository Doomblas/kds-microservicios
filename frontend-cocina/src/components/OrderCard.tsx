import { ArrowRight, Check, CheckCheck, Clock3, Flame, LoaderCircle, Utensils } from 'lucide-react'
import type { Orden } from '../types'
import { displayTime, elapsed } from '../utils/time'

const statusLabels = {
  PENDIENTE: 'Pendiente',
  PREPARANDO: 'En preparación',
  LISTO: 'Listo para entregar',
}

export function OrderCard({ order, now, busy, onAdvance }: {
  order: Orden
  now: number
  busy: boolean
  onAdvance: () => void
}) {
  const preparing = order.estado === 'PREPARANDO'
  const ready = order.estado === 'LISTO'
  const itemsCount = order.items.reduce((total, item) => total + item.cantidad, 0)
  const action = preparing ? 'Marcar como listo' : 'Iniciar preparación'

  return (
    <article
      className={`order-card status-${order.estado.toLowerCase()}`}
      data-order-id={order.id}
      aria-labelledby={`order-${order.id}`}
      aria-busy={busy}
    >
      <div className="card-topline">
        <span className="status-tag">{statusLabels[order.estado]}</span>
        <span className="order-elapsed" title={ready ? 'Tiempo total hasta estar listo' : 'Tiempo desde la recepción'}>
          <Clock3 size={14} aria-hidden="true" />
          <span className="sr-only">{ready ? 'Tiempo total: ' : 'Tiempo transcurrido: '}</span>
          {elapsed(order.fechaRecepcion, ready && order.fechaFinalizacion ? order.fechaFinalizacion : now)}
        </span>
      </div>

      <h3 className="order-number" id={`order-${order.id}`} tabIndex={-1}>{order.numeroPedido}</h3>
      <div className="card-meta">
        <span>Pedido #{order.pedidoId}</span>
        <span>Recibido <time dateTime={order.fechaRecepcion}>{displayTime(order.fechaRecepcion)}</time></span>
      </div>

      <ul className="items-list" aria-label="Productos del pedido">
        {order.items.map((item, index) => (
          <li key={`${item.productoId}-${index}`}>
            <span className="item-quantity"><span className="sr-only">Cantidad: </span>{item.cantidad}<span aria-hidden="true">×</span></span>
            <span className="item-name">{item.nombreProducto}</span>
          </li>
        ))}
      </ul>

      <div className="card-bottomline">
        <span className="items-total"><Utensils size={13} aria-hidden="true" />{itemsCount} {itemsCount === 1 ? 'unidad' : 'unidades'}</span>
        {preparing && order.fechaInicio && <span>Inicio {displayTime(order.fechaInicio)}</span>}
        {ready && order.fechaFinalizacion && <span>Listo {displayTime(order.fechaFinalizacion)}</span>}
      </div>

      {ready ? (
        <div className="ready-stamp"><CheckCheck size={18} aria-hidden="true" />Listo para entregar</div>
      ) : (
        <button
          type="button"
          className={`advance-button ${preparing ? 'ready-button' : ''}`}
          onClick={onAdvance}
          disabled={busy}
          aria-label={`${action}: ${order.numeroPedido}`}
        >
          {busy ? <LoaderCircle size={18} className="spin" aria-hidden="true" /> : preparing ? <Check size={18} aria-hidden="true" /> : <Flame size={18} aria-hidden="true" />}
          <span>{busy ? 'Guardando…' : action}</span>
          {!busy && <ArrowRight size={17} className="button-arrow" aria-hidden="true" />}
        </button>
      )}
    </article>
  )
}
