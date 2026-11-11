import type { Usuario } from './types'

const TOKEN_KEY = 'kds.auth.token'
const USER_KEY = 'kds.auth.user'

export const UNAUTHORIZED_EVENT = 'kds:unauthorized'

export function getToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function getStoredUser(): Usuario | null {
  try {
    const raw = window.localStorage.getItem(USER_KEY)
    if (!raw) return null
    const user = JSON.parse(raw) as Usuario
    if (
      typeof user?.id !== 'number' ||
      typeof user?.email !== 'string' ||
      typeof user?.nombre !== 'string' ||
      typeof user?.estado !== 'string'
    ) {
      return null
    }
    return user
  } catch {
    return null
  }
}

export function saveSession(token: string, usuario: Usuario) {
  window.localStorage.setItem(TOKEN_KEY, token)
  window.localStorage.setItem(USER_KEY, JSON.stringify(usuario))
}

export function clearSession() {
  window.localStorage.removeItem(TOKEN_KEY)
  window.localStorage.removeItem(USER_KEY)
}

export function authHeaders(extra: Record<string, string> = {}): Record<string, string> {
  const token = getToken()
  return {
    ...extra,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

export function notifyUnauthorized() {
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}
