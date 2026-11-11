import type { AvailabilityFilter as AvailabilityFilterValue } from '../types/menu'

interface AvailabilityFilterProps {
  value: AvailabilityFilterValue
  counts: {
    all: number
    available: number
    unavailable: number
  }
  onChange: (value: AvailabilityFilterValue) => void
}

const options: {
  value: AvailabilityFilterValue
  label: string
  countKey: keyof AvailabilityFilterProps['counts']
}[] = [
  { value: 'all', label: 'Todos', countKey: 'all' },
  { value: 'available', label: 'Disponibles', countKey: 'available' },
  { value: 'unavailable', label: 'No disponibles', countKey: 'unavailable' },
]

export function AvailabilityFilter({
  value,
  counts,
  onChange,
}: AvailabilityFilterProps) {
  return (
    <div className="availability-filter-group">
      <span className="filter-caption">Disponibilidad</span>
      <div className="availability-filter" role="group" aria-label="Filtrar por disponibilidad">
        {options.map((option) => {
          const isActive = value === option.value

          return (
            <button
              key={option.value}
              type="button"
              className={`filter-option ${isActive ? 'is-active' : ''}`}
              aria-pressed={isActive}
              onClick={() => onChange(option.value)}
            >
              <span>{option.label}</span>
              <span className="filter-count">{counts[option.countKey]}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
