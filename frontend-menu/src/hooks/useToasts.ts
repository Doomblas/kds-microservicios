import { useCallback, useEffect, useRef, useState } from 'react'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastMessage {
  id: number
  type: ToastType
  message: string
}

const TOAST_DURATION_MS = 3_800

export function useToasts() {
  const [toasts, setToasts] = useState<ToastMessage[]>([])
  const nextId = useRef(0)
  const timers = useRef(new Map<number, number>())

  const dismissToast = useCallback((id: number) => {
    const timer = timers.current.get(id)
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timers.current.delete(id)
    }
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const pushToast = useCallback((type: ToastType, message: string) => {
    const id = ++nextId.current
    setToasts((current) => [...current, { id, type, message }])

    const timer = window.setTimeout(() => {
      timers.current.delete(id)
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, TOAST_DURATION_MS)

    timers.current.set(id, timer)
    return id
  }, [])

  useEffect(() => {
    const activeTimers = timers.current

    return () => {
      activeTimers.forEach((timer) => window.clearTimeout(timer))
      activeTimers.clear()
    }
  }, [])

  return { toasts, pushToast, dismissToast }
}
