import {
  AlertCircle,
  CheckCircle2,
  ChefHat,
  Eye,
  EyeOff,
  LayoutList,
  ShieldCheck,
  ShoppingBag,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { AuthApiError } from './authApi'
import type { RegistroPayload, Usuario } from './types'

type AuthMode = 'login' | 'register'

interface AuthScreenProps {
  notice: string
  onLogin: (email: string, password: string) => Promise<void>
  onRegister: (payload: RegistroPayload) => Promise<Usuario>
  onClearNotice: () => void
}

interface FieldErrors {
  nombre?: string
  email?: string
  password?: string
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function AuthScreen({
  notice,
  onLogin,
  onRegister,
  onClearNotice,
}: AuthScreenProps) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [generalError, setGeneralError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const isLogin = mode === 'login'

  const switchMode = (next: AuthMode) => {
    setMode(next)
    setFieldErrors({})
    setGeneralError('')
    setSuccessMessage('')
    setPassword('')
    setShowPassword(false)
    onClearNotice()
  }

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {}

    if (!isLogin && !nombre.trim()) {
      errors.nombre = 'El nombre es obligatorio.'
    } else if (!isLogin && nombre.trim().length > 120) {
      errors.nombre = 'El nombre no puede superar los 120 caracteres.'
    }

    if (!email.trim()) {
      errors.email = 'El email es obligatorio.'
    } else if (!EMAIL_PATTERN.test(email.trim())) {
      errors.email = 'Ingresa un email válido.'
    }

    if (!password) {
      errors.password = 'La contraseña es obligatoria.'
    } else if (!isLogin && password.length < 8) {
      errors.password = 'La contraseña debe tener al menos 8 caracteres.'
    } else if (password.length > 72) {
      errors.password = 'La contraseña no puede superar los 72 caracteres.'
    }

    return errors
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const errors = validate()

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      setGeneralError('')
      return
    }

    setFieldErrors({})
    setGeneralError('')
    setSubmitting(true)

    try {
      if (isLogin) {
        await onLogin(email.trim(), password)
      } else {
        await onRegister({
          nombre: nombre.trim(),
          email: email.trim(),
          password,
        })
        setMode('login')
        setNombre('')
        setPassword('')
        setShowPassword(false)
        setSuccessMessage(
          'Cuenta creada. Un administrador debe asignarte un rol antes de usar los módulos operativos.',
        )
        onClearNotice()
      }
    } catch (error) {
      if (error instanceof AuthApiError) {
        setFieldErrors({
          nombre: error.fieldErrors?.nombre,
          email: error.fieldErrors?.email,
          password: error.fieldErrors?.password,
        })
        setGeneralError(error.message)
      } else {
        setGeneralError('Ocurrió un error inesperado. Intenta nuevamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-shell">
      <aside className="auth-brand-panel" aria-label="Información de acceso KDS">
        <div className="auth-brand-top">
          <div className="auth-brand-mark" aria-hidden="true">
            K
          </div>
          <div>
            <strong>KDS // Acceso</strong>
            <small>Un solo acceso, tres estaciones</small>
          </div>
        </div>

        <div className="auth-brand-copy">
          <span className="auth-eyebrow">Kitchen Display System</span>
          <h1>
            Tu rol define
            <br />
            tu estación.
          </h1>
          <p>
            Ingresa con tu cuenta KDS. El menú, la caja y la cocina comparten la
            misma identidad y permisos.
          </p>

          <ul className="auth-role-list">
            <li>
              <LayoutList size={18} aria-hidden="true" />
              <span>
                <strong>Gestión de menú</strong>
                Catálogo, categorías y disponibilidad.
              </span>
            </li>
            <li>
              <ShoppingBag size={18} aria-hidden="true" />
              <span>
                <strong>Caja</strong>
                Comandas, catálogo y seguimiento.
              </span>
            </li>
            <li>
              <ChefHat size={18} aria-hidden="true" />
              <span>
                <strong>Cocina</strong>
                Preparación y estados de cada pedido.
              </span>
            </li>
          </ul>
        </div>

        <p className="auth-brand-footnote">
          <ShieldCheck size={16} aria-hidden="true" />
          Sesiones protegidas con JWT y permisos verificados en el servidor.
        </p>
      </aside>

      <main className="auth-panel">
        <div className="auth-card">
          <div className="auth-card-heading">
            <span className="auth-card-eyebrow">
              {isLogin ? 'Bienvenido de nuevo' : 'Solicita tu acceso'}
            </span>
            <h2>{isLogin ? 'Iniciar sesión' : 'Crear cuenta'}</h2>
            <p>
              {isLogin
                ? 'Utiliza el correo y la contraseña de tu cuenta KDS.'
                : 'Regístrate con tus datos. El acceso se habilita cuando un administrador asigna tu rol.'}
            </p>
          </div>

          <div className="auth-tabs" role="tablist" aria-label="Tipo de acceso">
            <button
              type="button"
              role="tab"
              aria-selected={isLogin}
              className={`auth-tab ${isLogin ? 'is-active' : ''}`}
              onClick={() => switchMode('login')}
            >
              Iniciar sesión
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={!isLogin}
              className={`auth-tab ${!isLogin ? 'is-active' : ''}`}
              onClick={() => switchMode('register')}
            >
              Registrarme
            </button>
          </div>

          {notice && (
            <div className="auth-alert is-info" role="status">
              <AlertCircle size={18} aria-hidden="true" />
              <p>{notice}</p>
            </div>
          )}

          {successMessage && (
            <div className="auth-alert is-success" role="status">
              <CheckCircle2 size={18} aria-hidden="true" />
              <p>{successMessage}</p>
            </div>
          )}

          {generalError && (
            <div className="auth-alert is-error" role="alert">
              <AlertCircle size={18} aria-hidden="true" />
              <p>{generalError}</p>
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit} noValidate>
            {!isLogin && (
              <div className="form-field">
                <label htmlFor="auth-nombre">
                  Nombre completo
                  <span className="required-mark" aria-hidden="true">
                    *
                  </span>
                </label>
                <input
                  id="auth-nombre"
                  name="nombre"
                  type="text"
                  autoComplete="name"
                  value={nombre}
                  onChange={(event) => setNombre(event.target.value)}
                  placeholder="Tu nombre"
                  maxLength={120}
                  aria-invalid={Boolean(fieldErrors.nombre)}
                  aria-describedby={
                    fieldErrors.nombre ? 'auth-nombre-error' : undefined
                  }
                  disabled={submitting}
                />
                {fieldErrors.nombre && (
                  <span className="field-error" id="auth-nombre-error">
                    {fieldErrors.nombre}
                  </span>
                )}
              </div>
            )}

            <div className="form-field">
              <label htmlFor="auth-email">
                Correo electrónico
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="auth-email"
                name="email"
                type="email"
                inputMode="email"
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="nombre@empresa.com"
                maxLength={180}
                aria-invalid={Boolean(fieldErrors.email)}
                aria-describedby={
                  fieldErrors.email ? 'auth-email-error' : undefined
                }
                disabled={submitting}
              />
              {fieldErrors.email && (
                <span className="field-error" id="auth-email-error">
                  {fieldErrors.email}
                </span>
              )}
            </div>

            <div className="form-field">
              <label htmlFor="auth-password">
                Contraseña
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              </label>
              <div className="auth-password-wrap">
                <input
                  id="auth-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete={isLogin ? 'current-password' : 'new-password'}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder={
                    isLogin ? 'Tu contraseña' : 'Mínimo 8 caracteres'
                  }
                  maxLength={72}
                  aria-invalid={Boolean(fieldErrors.password)}
                  aria-describedby={
                    fieldErrors.password ? 'auth-password-error' : undefined
                  }
                  disabled={submitting}
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={
                    showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'
                  }
                  aria-pressed={showPassword}
                  disabled={submitting}
                >
                  {showPassword ? (
                    <EyeOff size={18} aria-hidden="true" />
                  ) : (
                    <Eye size={18} aria-hidden="true" />
                  )}
                </button>
              </div>
              {fieldErrors.password && (
                <span className="field-error" id="auth-password-error">
                  {fieldErrors.password}
                </span>
              )}
            </div>

            <button
              type="submit"
              className="btn btn-primary auth-submit"
              disabled={submitting}
            >
              {submitting
                ? isLogin
                  ? 'Ingresando…'
                  : 'Creando cuenta…'
                : isLogin
                  ? 'Ingresar al sistema'
                  : 'Crear cuenta'}
            </button>
          </form>

          <p className="auth-legal">
            Las cuentas nuevas quedan <strong>pendientes</strong> hasta que un
            administrador asigne un rol. No es posible elegir el rol desde el
            registro.
          </p>
        </div>
      </main>
    </div>
  )
}
