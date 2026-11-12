import { Receipt, Send, Trash2 } from 'lucide-react'
import type { CartItem } from '../types/pedidos'
import { formatPrice } from '../utils/format'

interface CartPanelProps {
  cart: CartItem[]
  submitting: boolean
  onClear: () => void
  onIncrease: (productoId: number) => void
  onDecrease: (productoId: number) => void
  onSubmit: () => void
}

export function CartPanel({
  cart,
  submitting,
  onClear,
  onIncrease,
  onDecrease,
  onSubmit,
}: CartPanelProps) {
  const total = cart.reduce(
    (sum, item) => sum + item.precio * item.cantidad,
    0,
  )
  const itemCount = cart.reduce((sum, item) => sum + item.cantidad, 0)

  return (
    <section className="panel cart-panel" aria-labelledby="cart-title">
      <div className="panel-heading">
        <div>
          <h2 id="cart-title">Comanda actual</h2>
          <p>
            {itemCount === 0
              ? 'Agrega productos del catálogo.'
              : `${itemCount} ítem${itemCount === 1 ? '' : 's'} listos para enviar.`}
          </p>
        </div>
        {cart.length > 0 && (
          <button type="button" className="text-button" onClick={onClear}>
            <Trash2 size={15} aria-hidden="true" />
            Vaciar
          </button>
        )}
      </div>

      {cart.length === 0 ? (
        <div className="empty-block compact">
          <Receipt size={26} aria-hidden="true" />
          <strong>La comanda está vacía</strong>
          <p>Selecciona productos disponibles para crear un pedido.</p>
        </div>
      ) : (
        <ul className="cart-list">
          {cart.map((item) => (
            <li key={item.productoId}>
              <div>
                <strong>{item.nombre}</strong>
                <small>
                  {formatPrice(item.precio)} · subtotal{' '}
                  {formatPrice(item.precio * item.cantidad)}
                </small>
              </div>
              <div className="qty-control">
                <button
                  type="button"
                  aria-label={`Quitar una unidad de ${item.nombre}`}
                  onClick={() => onDecrease(item.productoId)}
                >
                  −
                </button>
                <span>{item.cantidad}</span>
                <button
                  type="button"
                  aria-label={`Agregar una unidad de ${item.nombre}`}
                  onClick={() => onIncrease(item.productoId)}
                >
                  +
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <div className="cart-footer">
        <div>
          <span>Total</span>
          <strong>{formatPrice(total)}</strong>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          disabled={cart.length === 0 || submitting}
          onClick={onSubmit}
        >
          <Send size={17} aria-hidden="true" />
          {submitting ? 'Enviando…' : 'Enviar a cocina'}
        </button>
      </div>
    </section>
  )
}
