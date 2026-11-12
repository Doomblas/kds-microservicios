import type { Producto } from '../types/menu'
import { ProductCard } from './ProductCard'

interface ProductGridProps {
  productos: Producto[]
  pendingProductIds: Set<number>
  onToggle: (producto: Producto) => void
  onEdit: (producto: Producto) => void
}

export function ProductGrid({
  productos,
  pendingProductIds,
  onToggle,
  onEdit,
}: ProductGridProps) {
  return (
    <section className="product-grid" aria-label="Productos del menú">
      {productos.map((producto) => (
        <ProductCard
          key={producto.id}
          producto={producto}
          isToggling={pendingProductIds.has(producto.id)}
          onToggle={onToggle}
          onEdit={onEdit}
        />
      ))}
    </section>
  )
}
