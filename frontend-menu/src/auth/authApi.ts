import {
  authHeaders,
  clearSession,
  getToken,
  notifyUnauthorized,
} from './session'
import type {
  ApiErrorPayload,
  AuthResponse,
  RegistroPayload,
  Usuario,
} from './types'

const configuredBaseUrl = import.meta.env.VITE_MENU_API_URL?.trim()
export const AUTH_API_BASE_URL = (configuredBaseUrl || '/api').replace(/\/+$/, '')

const REQUEST_TIMEOUT_MS = 15_000

export class AuthApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string> | null

  constructor(
    message: string,
    status: number,
    fieldErrors: Record<string, string> | null = null,
  ) {
    super(message)
    this.name = 'AuthApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  autenticado = true,
): Promise<T> {
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
          ...(autenticado ? authHeaders() : {}),
          ...init.headers,
        },
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new AuthApiError('La solicitud tardó demasiado. Intenta nuevamente.', 0)
      }
      throw new AuthApiError('No se pudo conectar con el servicio de autenticación.', 0)
    }

    if (!response.ok) {
      let payload: ApiErrorPayload | null = null
      try {
        payload = (await response.json()) as ApiErrorPayload
      } catch {
        payload = null
      }

      if (response.status === 401 && autenticado) {
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

export const authApi = {
  login: (email: string, password: string) =>
    request<AuthResponse>(
      '/auth/login',
      {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      },
      false,
    ),

  register: (payload: RegistroPayload) =>
    request<Usuario>(
      '/auth/register',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      false,
    ),

  me: () => request<Usuario>('/auth/me'),
}

export function puedeRenovarSesion() {
  return Boolean(getToken())
}
