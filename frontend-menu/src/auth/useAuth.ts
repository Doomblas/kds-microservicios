import { useCallback, useEffect, useState } from 'react'
import { authApi } from './authApi'
import {
  UNAUTHORIZED_EVENT,
  clearSession,
  getStoredUser,
  getToken,
  saveSession,
} from './session'
import type { RegistroPayload, Usuario } from './types'

export type AuthStatus = 'loading' | 'anonymous' | 'authenticated'

export function useAuth() {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<Usuario | null>(null)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    const token = getToken()

    if (!token) {
      setStatus('anonymous')
      return
    }

    let cancelled = false

    authApi
      .me()
      .then((usuario) => {
        if (cancelled) return

        const stored = getStoredUser()
        const permisosCambiaron =
          stored !== null &&
          (stored.rol !== usuario.rol || stored.estado !== usuario.estado)

        if (permisosCambiaron) {
          clearSession()
          setUser(null)
          setStatus('anonymous')
          setNotice('Tus permisos cambiaron. Inicia sesión nuevamente.')
          return
        }

        saveSession(token, usuario)
        setUser(usuario)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        clearSession()
        setUser(null)
        setStatus('anonymous')
      })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const handleUnauthorized = () => {
      clearSession()
      setUser(null)
      setStatus('anonymous')
      setNotice('Tu sesión expiró. Inicia sesión nuevamente.')
    }

    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const respuesta = await authApi.login(email, password)
    saveSession(respuesta.token, respuesta.usuario)
    setUser(respuesta.usuario)
    setStatus('authenticated')
    setNotice('')
  }, [])

  const register = useCallback((payload: RegistroPayload) => authApi.register(payload), [])

  const logout = useCallback((message = '') => {
    clearSession()
    setUser(null)
    setStatus('anonymous')
    setNotice(message)
  }, [])

  const refreshUser = useCallback(async () => {
    const token = getToken()
    if (!token) return null

    const usuario = await authApi.me()
    const stored = getStoredUser()

    if (
      stored !== null &&
      (stored.rol !== usuario.rol || stored.estado !== usuario.estado)
    ) {
      clearSession()
      setUser(null)
      setStatus('anonymous')
      setNotice('Tus permisos cambiaron. Inicia sesión nuevamente.')
      return null
    }

    saveSession(token, usuario)
    setUser(usuario)
    return usuario
  }, [])

  return {
    status,
    user,
    notice,
    login,
    register,
    logout,
    refreshUser,
    clearNotice: () => setNotice(''),
  }
}
