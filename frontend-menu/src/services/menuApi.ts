import type {
  ApiErrorPayload,
  Categoria,
  CategoriaPayload,
  Producto,
  ProductoPayload,
} from '../types/menu'

const configuredBaseUrl = import.meta.env.VITE_MENU_API_URL?.trim()
export const MENU_API_BASE_URL = (configuredBaseUrl || '/api').replace(/\/+$/, '')

const REQUEST_TIMEOUT_MS = 15_000

export class MenuApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string> | null

  constructor(
    message: string,
    status: number,
    fieldErrors: Record<string, string> | null = null,
  ) {
    super(message)
    this.name = 'MenuApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    let response: Response

    try {
      response = await fetch(`${MENU_API_BASE_URL}${path}`, {
        ...init,
        signal: controller.signal,
        headers: {
          Accept: 'application/json',
          ...(init.body ? { 'Content-Type': 'application/json' } : {}),
          ...init.headers,
        },
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new MenuApiError(
          'La solicitud tardó demasiado. Intenta nuevamente.',
          0,
        )
      }

      throw new MenuApiError(
        'No se pudo conectar con ms-menu. Verifica que el servicio esté en ejecución.',
        0,
      )
    }

    if (!response.ok) {
      let payload: ApiErrorPayload | null = null

      try {
        payload = (await response.json()) as ApiErrorPayload
      } catch {
        payload = null
      }

      throw new MenuApiError(
        payload?.message || `La solicitud falló con estado ${response.status}.`,
        response.status,
        payload?.fieldErrors ?? null,
      )
    }

    if (response.status === 204) {
      return undefined as T
    }

    return (await response.json()) as T
  } finally {
    window.clearTimeout(timeout)
  }
}

export function getErrorMessage(
  error: unknown,
  fallback = 'Ocurrió un error inesperado.',
): string {
  if (error instanceof MenuApiError || error instanceof Error) {
    return error.message || fallback
  }

  return fallback
}

export const menuApi = {
  getCategorias: () => request<Categoria[]>('/categorias'),

  crearCategoria: (payload: CategoriaPayload) =>
    request<Categoria>('/categorias', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getProductos: () => request<Producto[]>('/productos'),

  crearProducto: (payload: ProductoPayload) =>
    request<Producto>('/productos', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  actualizarProducto: (id: number, payload: ProductoPayload) =>
    request<Producto>(`/productos/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  actualizarDisponibilidad: (id: number, disponible: boolean) =>
    request<Producto>(`/productos/${id}/disponibilidad`, {
      method: 'PATCH',
      body: JSON.stringify({ disponible }),
    }),
}
