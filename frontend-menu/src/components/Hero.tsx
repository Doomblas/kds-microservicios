import { FolderPlus, Plus } from 'lucide-react'
import { MenuPulse } from './MenuPulse'

interface HeroProps {
  available: number
  total: number
  percentage: number
  onNewCategory: () => void
  onNewProduct: () => void
  actionsDisabled: boolean
  newProductDisabled: boolean
  newProductHint?: string
}

export function Hero({
  available,
  total,
  percentage,
  onNewCategory,
  onNewProduct,
  actionsDisabled,
  newProductDisabled,
  newProductHint,
}: HeroProps) {
  return (
    <section className="hero" aria-labelledby="hero-title">
      <div className="hero-inner">
        <div className="hero-copy">
          <div className="hero-eyebrow">
            <span>Servicio ms-menu</span>
            <span className="hero-badge">
              <i aria-hidden="true" />
              Catálogo activo
            </span>
          </div>

          <h1 id="hero-title">Catálogo de Menú</h1>
          <p>
            Controla productos y categorías en tiempo real. Ajusta la
            disponibilidad del menú para mantener las comandas siempre al día.
          </p>

          <div className="hero-actions">
            <button
              type="button"
              className="btn btn-primary"
              onClick={onNewProduct}
              disabled={newProductDisabled}
              title={newProductHint}
            >
              <Plus size={19} aria-hidden="true" />
              Nuevo Producto
            </button>
            <button
              type="button"
              className="btn btn-soft"
              onClick={onNewCategory}
              disabled={actionsDisabled}
            >
              <FolderPlus size={19} aria-hidden="true" />
              Nueva Categoría
            </button>
          </div>
        </div>

        <MenuPulse
          available={available}
          total={total}
          percentage={percentage}
        />
      </div>
    </section>
  )
}
