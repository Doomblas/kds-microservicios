import { AlertCircle, ChevronDown, LoaderCircle } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { MenuApiError } from '../services/menuApi'
import type { Categoria, Producto, ProductoPayload } from '../types/menu'
import { Drawer } from './Drawer'

interface ProductDrawerProps {
  open: boolean
  producto: Producto | null
  presetCategoryId?: number
  categorias: Categoria[]
  onClose: () => void
  onSubmit: (payload: ProductoPayload) => Promise<void>
}

export function ProductDrawer({
  open,
  producto,
  presetCategoryId,
  categorias,
  onClose,
  onSubmit,
}: ProductDrawerProps) {
  const fieldPrefix = useId()
  const formId = useId()

  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [precio, setPrecio] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [disponible, setDisponible] = useState(true)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }

    setNombre(producto?.nombre ?? '')
    setDescripcion(producto?.descripcion ?? '')
    setPrecio(producto ? Number(producto.precio).toFixed(2) : '')
    setCategoriaId(
      String(
        producto?.categoria?.id ??
          presetCategoryId ??
          categorias[0]?.id ??
          '',
      ),
    )
    setDisponible(producto?.disponible ?? true)
    setFieldErrors({})
    setFormError('')
    setSubmitting(false)
  }, [categorias, open, presetCategoryId, producto])

  const validate = () => {
    const errors: Record<string, string> = {}
    const cleanName = nombre.trim()
    const cleanDescription = descripcion.trim()
    const normalizedPrice = precio.trim().replace(',', '.')
    const numericPrice = Number(normalizedPrice)

    if (!cleanName) {
      errors.nombre = 'El nombre del producto es obligatorio.'
    } else if (cleanName.length > 120) {
      errors.nombre = 'El nombre no puede superar los 120 caracteres.'
    }

    if (cleanDescription.length > 255) {
      errors.descripcion =
        'La descripción no puede superar los 255 caracteres.'
    }

    if (!normalizedPrice) {
      errors.precio = 'El precio es obligatorio.'
    } else if (
      !/^\d+(\.\d{1,2})?$/.test(normalizedPrice) ||
      !Number.isFinite(numericPrice) ||
      numericPrice <= 0
    ) {
      errors.precio = 'Ingresa un precio mayor que 0 con máximo dos decimales.'
    } else if (numericPrice > 99_999_999.99) {
      errors.precio = 'El precio es demasiado alto.'
    }

    if (!categoriaId) {
      errors.categoriaId = 'Selecciona una categoría.'
    }

    setFieldErrors(errors)

    const firstError = Object.keys(errors)[0]
    if (firstError) {
      document.getElementById(`${fieldPrefix}-${firstError}`)?.focus()
      return null
    }

    return {
      nombre: cleanName,
      descripcion: cleanDescription,
      precio: numericPrice,
      disponible,
      categoriaId: Number(categoriaId),
    } satisfies ProductoPayload
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError('')

    const payload = validate()
    if (!payload) {
      return
    }

    setSubmitting(true)

    try {
      await onSubmit(payload)
    } catch (error) {
      if (error instanceof MenuApiError) {
        if (error.fieldErrors) {
          setFieldErrors(error.fieldErrors)
        }
        setFormError(error.message)
      } else {
        setFormError('No se pudo guardar el producto. Intenta nuevamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const descriptionLength = descripcion.length

  return (
    <Drawer
      open={open}
      title={producto ? 'Editar producto' : 'Nuevo producto'}
      subtitle={
        producto
          ? 'Actualiza la información del producto en el menú.'
          : 'Completa la información para agregarlo al catálogo.'
      }
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
            disabled={submitting || categorias.length === 0}
          >
            {submitting && (
              <LoaderCircle size={18} className="spin" aria-hidden="true" />
            )}
            {submitting
              ? 'Guardando…'
              : producto
                ? 'Guardar cambios'
                : 'Crear producto'}
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
        {categorias.length === 0 && (
          <div className="form-alert is-warning" role="alert">
            <AlertCircle size={18} aria-hidden="true" />
            <div>
              <strong>No hay categorías disponibles</strong>
              <p>Crea una categoría antes de agregar productos.</p>
            </div>
          </div>
        )}

        {formError && (
          <div className="form-alert is-error" role="alert">
            <AlertCircle size={18} aria-hidden="true" />
            <div>
              <strong>No se pudo guardar el producto</strong>
              <p>{formError}</p>
            </div>
          </div>
        )}

        <div className="form-field">
          <label htmlFor={`${fieldPrefix}-nombre`}>
            Nombre del producto
            <span className="required-mark" aria-hidden="true">
              *
            </span>
          </label>
          <input
            id={`${fieldPrefix}-nombre`}
            type="text"
            value={nombre}
            onChange={(event) => setNombre(event.target.value)}
            placeholder="Ej. Hamburguesa Clásica"
            maxLength={120}
            autoComplete="off"
            aria-invalid={Boolean(fieldErrors.nombre)}
            aria-describedby={
              fieldErrors.nombre ? `${fieldPrefix}-nombre-error` : undefined
            }
            disabled={submitting}
          />
          {fieldErrors.nombre && (
            <span
              className="field-error"
              id={`${fieldPrefix}-nombre-error`}
              role="alert"
            >
              {fieldErrors.nombre}
            </span>
          )}
        </div>

        <div className="form-field">
          <label htmlFor={`${fieldPrefix}-descripcion`}>Descripción</label>
          <textarea
            id={`${fieldPrefix}-descripcion`}
            value={descripcion}
            onChange={(event) => setDescripcion(event.target.value)}
            placeholder="Describe brevemente el producto…"
            rows={3}
            maxLength={255}
            aria-invalid={Boolean(fieldErrors.descripcion)}
            aria-describedby={
              fieldErrors.descripcion
                ? `${fieldPrefix}-descripcion-error`
                : undefined
            }
            disabled={submitting}
          />
          <div className="field-support">
            <span
              className={`char-count ${descriptionLength > 255 ? 'is-over' : ''}`}
            >
              {descriptionLength}/255
            </span>
          </div>
          {fieldErrors.descripcion && (
            <span
              className="field-error"
              id={`${fieldPrefix}-descripcion-error`}
              role="alert"
            >
              {fieldErrors.descripcion}
            </span>
          )}
        </div>

        <div className="form-grid">
          <div className="form-field">
            <label htmlFor={`${fieldPrefix}-precio`}>
              Precio
              <span className="required-mark" aria-hidden="true">
                *
              </span>
            </label>
            <div className="input-with-prefix">
              <span aria-hidden="true">$</span>
              <input
                id={`${fieldPrefix}-precio`}
                type="text"
                inputMode="decimal"
                value={precio}
                onChange={(event) => setPrecio(event.target.value)}
                placeholder="0.00"
                autoComplete="off"
                aria-invalid={Boolean(fieldErrors.precio)}
                aria-describedby={
                  fieldErrors.precio ? `${fieldPrefix}-precio-error` : undefined
                }
                disabled={submitting}
              />
            </div>
            {fieldErrors.precio && (
              <span
                className="field-error"
                id={`${fieldPrefix}-precio-error`}
                role="alert"
              >
                {fieldErrors.precio}
              </span>
            )}
          </div>

          <div className="form-field">
            <label htmlFor={`${fieldPrefix}-categoriaId`}>
              Categoría
              <span className="required-mark" aria-hidden="true">
                *
              </span>
            </label>
            <div className="select-wrap">
              <select
                id={`${fieldPrefix}-categoriaId`}
                value={categoriaId}
                onChange={(event) => setCategoriaId(event.target.value)}
                aria-invalid={Boolean(fieldErrors.categoriaId)}
                aria-describedby={
                  fieldErrors.categoriaId
                    ? `${fieldPrefix}-categoriaId-error`
                    : undefined
                }
                disabled={submitting || categorias.length === 0}
              >
                <option value="">Selecciona una categoría</option>
                {categorias.map((categoria) => (
                  <option key={categoria.id} value={categoria.id}>
                    {categoria.nombre}
                    {categoria.activo ? '' : ' (inactiva)'}
                  </option>
                ))}
              </select>
              <ChevronDown size={18} aria-hidden="true" />
            </div>
            {fieldErrors.categoriaId && (
              <span
                className="field-error"
                id={`${fieldPrefix}-categoriaId-error`}
                role="alert"
              >
                {fieldErrors.categoriaId}
              </span>
            )}
          </div>
        </div>

        <div className="form-switch-row">
          <div>
            <strong>Disponibilidad</strong>
            <span>Habilitar para orden inmediata</span>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={disponible}
            className={`availability-switch form-switch ${disponible ? 'is-on' : ''}`}
            onClick={() => setDisponible((current) => !current)}
            disabled={submitting}
            aria-label="Disponibilidad del producto"
          >
            <span className="switch-thumb" aria-hidden="true" />
          </button>
        </div>
      </form>
    </Drawer>
  )
}
