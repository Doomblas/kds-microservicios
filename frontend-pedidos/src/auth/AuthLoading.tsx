export function AuthLoading() {
  return (
    <main className="auth-loading" aria-live="polite" aria-busy="true">
      <div className="auth-loading-card">
        <span className="auth-spinner" aria-hidden="true" />
        <strong>Verificando tu sesión</strong>
        <p>Estamos validando tus permisos con ms-menu.</p>
      </div>
    </main>
  )
}
