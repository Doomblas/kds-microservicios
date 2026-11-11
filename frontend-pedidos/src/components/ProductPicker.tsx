import { Minus, Plus, Search, UtensilsCrossed } from 'lucide-react'
import type { CartItem, ProductoMenu } from '../types/pedidos'
import { formatPrice } from '../utils/format'

interface ProductPickerProps {
  productos: ProductoMenu[]
  cart: CartItem[]
  query: string
  loading: boolean
  onQueryChange: (value: string) => void
  onAdd: (producto: ProductoMenu) => void
  onDecrease: (productoId: number) => void
}

export function ProductPicker({
  productos,
  cart,
  query,
  loading,
  onQueryChange,
  onAdd,
  onDecrease,
}: ProductPickerProps) {
  const available = productos.filter((producto) => producto.disponible)
  const filtered = available.filter((producto) => {
    const needle = query.trim().toLocaleLowerCase('es')
    if (!needle) return true
    return (
      producto.nombre.toLocaleLowerCase('es').includes(needle) ||
      (producto.categoria?.nombre ?? '').toLocaleLowerCase('es').includes(needle)
    )
  })

  function quantityOf(productoId: number) {
    return cart.find((item) => item.productoId === productoId)?.cantidad ?? 0
  }

  return (
    <section className="panel" aria-labelledby="catalog-title">
      <div className="panel-heading">
        <div>
          <h2 id="catalog-title">Catálogo</h2>
          <p>Productos disponibles desde ms-menu.</p>
        </div>
        <div className="search-box">
          <Search size={18} aria-hidden="true" />
          <label className="sr-only" htmlFor="product-search">
            Buscar producto
          </label>
          <input
            id="product-search"
            type="search"
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="Buscar producto o categoría…"
          />
        </div>
      </div>

      {loading ? (
        <div className="skeleton-grid" aria-busy="true">
          {[0, 1, 2, 3].map((item) => (
            <div className="skeleton-card" key={item} />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="empty-block">
          <UtensilsCrossed size={28} aria-hidden="true" />
          <strong>No hay productos para mostrar</strong>
          <p>
            {available.length === 0
              ? 'Carga productos disponibles en ms-menu para armar pedidos.'
              : 'Prueba con otro término de búsqueda.'}
          </p>
        </div>
      ) : (
        <div className="product-grid">
          {filtered.map((producto) => {
            const quantity = quantityOf(producto.id)
            return (
              <article className="product-card" key={producto.id}>
                <div className="product-topline">
                  <span className="product-category">
                    {producto.categoria?.nombre ?? 'Sin categoría'}
                  </span>
                  <strong>{formatPrice(producto.precio)}</strong>
                </div>
                <h3>{producto.nombre}</h3>
                <p>{producto.descripcion || 'Sin descripción.'}</p>
                <div className="product-actions">
                  {quantity > 0 ? (
                    <div className="qty-control">
                      <button
                        type="button"
                        aria-label={`Quitar una unidad de ${producto.nombre}`}
                        onClick={() => onDecrease(producto.id)}
                      >
                        <Minus size={16} aria-hidden="true" />
                      </button>
                      <span aria-live="polite">{quantity}</span>
                      <button
                        type="button"
                        aria-label={`Agregar una unidad de ${producto.nombre}`}
                        onClick={() => onAdd(producto)}
                      >
                        <Plus size={16} aria-hidden="true" />
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={() => onAdd(producto)}
                    >
                      <Plus size={16} aria-hidden="true" />
                      Agregar
                    </button>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
