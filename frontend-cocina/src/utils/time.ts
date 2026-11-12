const timeFormatter = new Intl.DateTimeFormat('es-EC', { hour: '2-digit', minute: '2-digit', hour12: false })

export function displayTime(value: string | number | null) {
  if (value === null) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : timeFormatter.format(date)
}

export function elapsed(start: string, end: string | number) {
  const difference = new Date(end).getTime() - new Date(start).getTime()
  if (!Number.isFinite(difference)) return '—'
  const minutes = Math.max(0, Math.floor(difference / 60000))
  if (minutes < 1) return '< 1 min'
  if (minutes < 60) return `${minutes} min`
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')} min`
}
