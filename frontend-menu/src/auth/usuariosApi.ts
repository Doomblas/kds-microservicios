import { AuthApiError, AUTH_API_BASE_URL } from './authApi'
import {
  authHeaders,
  clearSession,
  notifyUnauthorized,
} from './session'
import type {
  ApiErrorPayload,
  EstadoUsuario,
  Rol,
  Usuario,
} from './types'

const REQUEST_TIMEOUT_MS = 15_000

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    let response: Response

    try {
      response = await fetch(`${AUTH_API_BASE_URL}${path}`, {
        ...init,
        signal: controller.signal,
        headers: {
          Accept: 'application/json',
          ...(init.body ? { 'Content-Type': 'application/json' } : {}),
          ...authHeaders(),
          ...init.headers,
        },
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new AuthApiError(
          'La solicitud tardó demasiado. Intenta nuevamente.',
          0,
        )
      }
      throw new AuthApiError(
        'No se pudo conectar con el servicio de usuarios.',
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

      if (response.status === 401) {
        clearSession()
        notifyUnauthorized()
      }

      throw new AuthApiError(
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

export const usuariosApi = {
  listar: (busqueda = '') => {
    const termino = busqueda.trim()
    const query = termino ? `?q=${encodeURIComponent(termino)}` : ''
    return request<Usuario[]>(`/usuarios${query}`)
  },

  obtener: (id: number) => request<Usuario>(`/usuarios/${id}`),

  asignarRol: (id: number, rol: Rol) =>
    request<Usuario>(`/usuarios/${id}/rol`, {
      method: 'PATCH',
      body: JSON.stringify({ rol }),
    }),

  cambiarEstado: (id: number, estado: EstadoUsuario) =>
    request<Usuario>(`/usuarios/${id}/estado`, {
      method: 'PATCH',
      body: JSON.stringify({ estado }),
    }),
}
