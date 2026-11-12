const priceFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const dateTimeFormatter = new Intl.DateTimeFormat('es-EC', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatPrice(value: number | string): string {
  const amount = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(amount)) return '—'
  return `$${priceFormatter.format(amount)}`
}

export function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return dateTimeFormatter.format(date)
}

export function estadoLabel(estado: string): string {
  switch (estado) {
    case 'PENDIENTE':
      return 'Pendiente'
    case 'PREPARANDO':
      return 'En preparación'
    case 'LISTO':
      return 'Listo'
    default:
      return estado
  }
}
