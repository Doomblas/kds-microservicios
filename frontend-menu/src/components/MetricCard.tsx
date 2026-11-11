import type { LucideIcon } from 'lucide-react'

interface MetricCardProps {
  label: string
  value: number
  helper: string
  icon: LucideIcon
  variant?: 'default' | 'accent' | 'danger'
}

export function MetricCard({
  label,
  value,
  helper,
  icon: Icon,
  variant = 'default',
}: MetricCardProps) {
  return (
    <article className={`metric-card metric-${variant}`}>
      <div className="metric-card-top">
        <span className="metric-label">{label}</span>
        <span className="metric-icon" aria-hidden="true">
          <Icon size={18} />
        </span>
      </div>

      <strong className="metric-value">{value}</strong>
      <span className="metric-helper">{helper}</span>
    </article>
  )
}
