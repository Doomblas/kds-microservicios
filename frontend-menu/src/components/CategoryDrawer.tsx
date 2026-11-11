import { AlertCircle, LoaderCircle } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { MenuApiError } from '../services/menuApi'
import type { CategoriaPayload } from '../types/menu'
import { Drawer } from './Drawer'

interface CategoryDrawerProps {
  open: boolean
  onClose: () => void
  onSubmit: (payload: CategoriaPayload) => Promise<void>
}

export function CategoryDrawer({
  open,
  onClose,
  onSubmit,
}: CategoryDrawerProps) {
  const fieldPrefix = useId()
  const formId = useId()

  const [nombre, setNombre] = useState('')
  const [activo, setActivo] = useState(true)
  const [fieldError, setFieldError] = useState('')
  const [formError, setFormError] = useState('')
  const [errorStatus, setErrorStatus] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }

    setNombre('')
    setActivo(true)
    setFieldError('')
    setFormError('')
    setErrorStatus(null)
    setSubmitting(false)
  }, [open])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError('')
    setErrorStatus(null)

    const cleanName = nombre.trim()

    if (!cleanName) {
      setFieldError('El nombre de la categoría es obligatorio.')
      document.getElementById(`${fieldPrefix}-nombre`)?.focus()
      return
    }

    if (cleanName.length > 100) {
      setFieldError('El nombre no puede superar los 100 caracteres.')
      document.getElementById(`${fieldPrefix}-nombre`)?.focus()
      return
    }

    setFieldError('')
    setSubmitting(true)

    try {
      await onSubmit({ nombre: cleanName, activo })
    } catch (error) {
      if (error instanceof MenuApiError) {
        setFormError(error.message)
        setErrorStatus(error.status)
      } else {
        setFormError('No se pudo crear la categoría. Intenta nuevamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const isConflict = errorStatus === 409

  return (
    <Drawer
      open={open}
      title="Nueva categoría"
      subtitle="Organiza el catálogo agrupando productos por sección."
      busy={submitting}
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={onClose}
            disabled={submitting}
          >
            Cancelar
          </button>
          <button
            type="submit"
            form={formId}
            className="btn btn-primary drawer-submit"
            disabled={submitting}
          >
            {submitting && (
              <LoaderCircle size={18} className="spin" aria-hidden="true" />
            )}
            {submitting ? 'Creando…' : 'Crear categoría'}
          </button>
        </>
      }
    >
      <form
        id={formId}
        className="drawer-form"
        onSubmit={handleSubmit}
        noValidate
      >
        {formError && (
          <div
            className={`form-alert ${isConflict ? 'is-conflict' : 'is-error'}`}
            role="alert"
          >
            <AlertCircle size={18} aria-hidden="true" />
            <div>
              <strong>
                {isConflict
                  ? 'Ya existe una categoría con ese nombre'
                  : 'No se pudo crear la categoría'}
              </strong>
              <p>{formError}</p>
            </div>
          </div>
        )}

        <div className="form-field">
          <label htmlFor={`${fieldPrefix}-nombre`}>
            Nombre de la categoría
            <span className="required-mark" aria-hidden="true">
              *
            </span>
          </label>
          <input
            id={`${fieldPrefix}-nombre`}
            type="text"
            value={nombre}
            onChange={(event) => {
              setNombre(event.target.value)
              if (fieldError) {
                setFieldError('')
              }
            }}
            placeholder="Ej. Hamburguesas"
            maxLength={100}
            autoComplete="off"
            aria-invalid={Boolean(fieldError)}
            aria-describedby={
              fieldError ? `${fieldPrefix}-nombre-error` : undefined
            }
            disabled={submitting}
          />
          <div className="field-support">
            <span className="char-count">{nombre.length}/100</span>
          </div>
          {fieldError && (
            <span
              className="field-error"
              id={`${fieldPrefix}-nombre-error`}
              role="alert"
            >
              {fieldError}
            </span>
          )}
        </div>

        <div className="form-switch-row">
          <div>
            <strong>Estado de la categoría</strong>
            <span>Visible y activa en la carta del menú</span>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={activo}
            className={`availability-switch form-switch ${activo ? 'is-on' : ''}`}
            onClick={() => setActivo((current) => !current)}
            disabled={submitting}
            aria-label="Estado activo de la categoría"
          >
            <span className="switch-thumb" aria-hidden="true" />
          </button>
        </div>

        <p className="drawer-note">
          Las categorías se crean desde aquí. La edición de categorías no está
          disponible en el backend actual.
        </p>
      </form>
    </Drawer>
  )
}
