import { LoaderCircle, Pencil } from 'lucide-react'
import type { Producto } from '../types/menu'
import { formatPrice } from '../utils/format'

interface ProductCardProps {
  producto: Producto
  isToggling: boolean
  onToggle: (producto: Producto) => void
  onEdit: (producto: Producto) => void
}

export function ProductCard({
  producto,
  isToggling,
  onToggle,
  onEdit,
}: ProductCardProps) {
  const categoryName = producto.categoria?.nombre ?? 'Sin categoría'
  const description = producto.descripcion?.trim() || 'Sin descripción.'
  const toggleLabel = producto.disponible
    ? `Marcar ${producto.nombre} como no disponible`
    : `Marcar ${producto.nombre} como disponible`

  return (
    <article
      className={`product-card ${producto.disponible ? '' : 'is-unavailable'}`}
    >
      <div className="product-card-top">
        <span className="category-tag">{categoryName}</span>
        <span
          className={`availability-badge ${producto.disponible ? 'is-available' : 'is-unavailable'}`}
        >
          <i aria-hidden="true" />
          {isToggling
            ? 'Actualizando…'
            : producto.disponible
              ? 'Disponible'
              : 'No disponible'}
        </span>
      </div>

      <div className="product-card-body">
        <h3>{producto.nombre}</h3>
        <p>{description}</p>
      </div>

      <div className="product-card-footer">
        <div className="product-price-block">
          <span>Precio</span>
          <strong className="product-price">
            {formatPrice(producto.precio)}
          </strong>
        </div>

        <div className="product-card-actions">
          <button
            type="button"
            role="switch"
            aria-checked={producto.disponible}
            aria-label={toggleLabel}
            title={toggleLabel}
            className={`availability-switch ${producto.disponible ? 'is-on' : ''} ${isToggling ? 'is-busy' : ''}`}
            disabled={isToggling}
            onClick={() => onToggle(producto)}
          >
            <span className="switch-thumb" aria-hidden="true">
              {isToggling && <LoaderCircle size={12} className="spin" />}
            </span>
          </button>

          <button
            type="button"
            className="icon-button"
            onClick={() => onEdit(producto)}
            aria-label={`Editar ${producto.nombre}`}
            title="Editar producto"
          >
            <Pencil size={18} aria-hidden="true" />
          </button>
        </div>
      </div>
    </article>
  )
}
