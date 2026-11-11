const priceFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const percentageFormatter = new Intl.NumberFormat('en-US', {
  maximumFractionDigits: 1,
})

export function formatPrice(value: number | string): string {
  const amount = typeof value === 'number' ? value : Number(value)

  if (!Number.isFinite(amount)) {
    return '—'
  }

  return `$${priceFormatter.format(amount)}`
}

export function formatPercentage(value: number): string {
  if (!Number.isFinite(value)) {
    return '0%'
  }

  return `${percentageFormatter.format(value)}%`
}

export function calculateAvailabilityPercentage(
  available: number,
  total: number,
): number {
  return total > 0 ? (available / total) * 100 : 0
}
