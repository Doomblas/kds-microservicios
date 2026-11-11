import { Activity, ArrowUpRight } from 'lucide-react'

export function KitchenPulse({ pending, preparing, loading }: { pending: number; preparing: number; loading: boolean }) {
  const active = pending + preparing
  const circumference = 2 * Math.PI * 40
  const portion = active ? preparing / active : 0

  return (
    <aside className="kitchen-pulse" aria-label="Actividad de cocina">
      <div className="pulse-heading"><span><Activity size={15} aria-hidden="true" />PULSO DE COCINA</span><ArrowUpRight size={17} aria-hidden="true" /></div>
      <div className="pulse-content">
        <div className="pulse-dial" aria-hidden="true">
          <svg viewBox="0 0 100 100">
            <circle className="pulse-track" cx="50" cy="50" r="40" />
            <circle className="pulse-progress" cx="50" cy="50" r="40" strokeDasharray={`${circumference * portion} ${circumference}`} />
          </svg>
          <span><strong>{loading ? '—' : active}</strong><small>ACTIVAS</small></span>
        </div>
        <div className="pulse-copy">
          <strong>{loading ? 'Sincronizando…' : active ? `${active} ${active === 1 ? 'comanda activa' : 'comandas activas'}` : 'Todo bajo control'}</strong>
          <p>{loading ? 'Consultando el servicio de Cocina.' : active ? 'Cada paso cuenta. El siguiente plato está en tus manos.' : 'Las nuevas comandas aparecerán aquí automáticamente.'}</p>
        </div>
      </div>
      <div className="pulse-footer">
        <span><span className="pulse-dot pending-dot" />{loading ? '—' : pending} por preparar</span>
        <span><span className="pulse-dot" />{loading ? '—' : preparing} en preparación</span>
      </div>
    </aside>
  )
}
