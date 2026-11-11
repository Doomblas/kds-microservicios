import type { ApiErrorPayload } from '../types/pedidos'

const REQUEST_TIMEOUT_MS = 15_000

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function requestJson<T>(
  baseUrl: string,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    let response: Response

    try {
      response = await fetch(`${baseUrl}${path}`, {
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
        throw new ApiError('La solicitud tardó demasiado. Intenta nuevamente.', 0)
      }
      throw new ApiError('No se pudo conectar con el servicio. Verifica que esté en ejecución.', 0)
    }

    if (!response.ok) {
      let payload: ApiErrorPayload | null = null
      try {
        payload = (await response.json()) as ApiErrorPayload
      } catch {
        payload = null
      }
      throw new ApiError(
        payload?.message || `La solicitud falló con estado ${response.status}.`,
        response.status,
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
  if (error instanceof ApiError || error instanceof Error) {
    return error.message || fallback
  }
  return fallback
}
