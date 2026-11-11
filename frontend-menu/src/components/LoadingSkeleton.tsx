export function LoadingSkeleton() {
  return (
    <div className="page-skeleton" role="status" aria-live="polite">
      <span className="sr-only">Cargando catálogo de menú…</span>

      <div className="skeleton-hero" aria-hidden="true">
        <div className="skeleton skeleton-kicker" />
        <div className="skeleton skeleton-title" />
        <div className="skeleton skeleton-copy" />
        <div className="skeleton skeleton-actions" />
        <div className="skeleton skeleton-pulse" />
      </div>

      <div className="skeleton-metrics" aria-hidden="true">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="skeleton skeleton-metric" key={index} />
        ))}
      </div>

      <div className="skeleton skeleton-toolbar" aria-hidden="true" />

      <div className="skeleton-pills" aria-hidden="true">
        {Array.from({ length: 5 }, (_, index) => (
          <div className="skeleton skeleton-pill" key={index} />
        ))}
      </div>

      <div className="skeleton-grid" aria-hidden="true">
        {Array.from({ length: 6 }, (_, index) => (
          <div className="skeleton skeleton-card" key={index}>
            <div className="skeleton skeleton-card-tag" />
            <div className="skeleton skeleton-card-title" />
            <div className="skeleton skeleton-card-copy" />
            <div className="skeleton skeleton-card-footer" />
          </div>
        ))}
      </div>
    </div>
  )
}
