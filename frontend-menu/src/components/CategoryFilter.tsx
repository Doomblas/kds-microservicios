import type { CategoryFilterOption } from '../types/menu'

interface CategoryFilterProps {
  options: CategoryFilterOption[]
  selectedId: number | 'all'
  onSelect: (id: number | 'all') => void
}

export function CategoryFilter({
  options,
  selectedId,
  onSelect,
}: CategoryFilterProps) {
  return (
    <nav className="category-filter" aria-label="Filtrar por categoría">
      {options.map((option) => {
        const isActive = selectedId === option.id

        return (
          <button
            key={option.id}
            type="button"
            className={`category-pill ${isActive ? 'is-active' : ''}`}
            aria-pressed={isActive}
            onClick={() => onSelect(option.id)}
          >
            {option.id === 'all' && (
              <span className="category-pill-dot" aria-hidden="true" />
            )}
            <span>{option.nombre}</span>
            <span className="category-pill-count">{option.count}</span>
          </button>
        )
      })}
    </nav>
  )
}
