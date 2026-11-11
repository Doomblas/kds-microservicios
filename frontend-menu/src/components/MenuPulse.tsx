interface MenuPulseProps {
  available: number
  total: number
  percentage: number
}

const RADIUS = 42
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

export function MenuPulse({ available, total, percentage }: MenuPulseProps) {
  const safePercentage = Math.min(100, Math.max(0, percentage))
  const dashOffset = CIRCUMFERENCE * (1 - safePercentage / 100)
  const unavailable = total - available

  return (
    <aside
      className="menu-pulse"
      aria-label={`${available} de ${total} productos disponibles (${safePercentage.toFixed(1)} por ciento)`}
    >
      <div className="menu-pulse-header">
        <div className="menu-pulse-title">
          <span className="pulse-beacon" aria-hidden="true" />
          <span>Menu Pulse // Disponibilidad</span>
        </div>
        <span className="menu-pulse-service">ms-menu</span>
      </div>

      <div className="menu-pulse-main">
        <div className="pulse-ring-wrap" aria-hidden="true">
          <svg className="pulse-ring" viewBox="0 0 100 100">
            <circle className="pulse-ring-track" cx="50" cy="50" r={RADIUS} />
            <circle
              className="pulse-ring-progress"
              cx="50"
              cy="50"
              r={RADIUS}
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={dashOffset}
            />
          </svg>
          <div className="pulse-ring-center">
            <strong>{safePercentage.toFixed(1)}%</strong>
            <span>Disponible</span>
          </div>
        </div>

        <div className="menu-pulse-copy">
          <strong>
            {available} de {total} disponibles
          </strong>
          <p>Productos listos para las comandas del servicio.</p>
        </div>
      </div>

      <div className="menu-pulse-footer">
        <span>
          <i className="dot dot-available" aria-hidden="true" />
          {available} productos listos
        </span>
        <span>
          <i className="dot dot-unavailable" aria-hidden="true" />
          {unavailable} no disponibles
        </span>
      </div>
    </aside>
  )
}
