import { useDeferredValue, useMemo, useState } from 'react'
import type {
  AvailabilityFilter,
  Categoria,
  CategoryFilterOption,
  Producto,
} from '../types/menu'

const normalize = (value: string) =>
  value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es')
    .trim()

export function useMenuFilters(productos: Producto[], categorias: Categoria[]) {
  const [query, setQuery] = useState('')
  const [availability, setAvailability] = useState<AvailabilityFilter>('all')
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | 'all'>(
    'all',
  )
  const deferredQuery = useDeferredValue(query)

  const availabilityCounts = useMemo(() => {
    const available = productos.filter((producto) => producto.disponible).length

    return {
      all: productos.length,
      available,
      unavailable: productos.length - available,
    }
  }, [productos])

  const categoryOptions = useMemo<CategoryFilterOption[]>(() => {
    const counts = new Map<number, number>()

    for (const producto of productos) {
      const categoryId = producto.categoria?.id
      if (categoryId != null) {
        counts.set(categoryId, (counts.get(categoryId) ?? 0) + 1)
      }
    }

    return [
      { id: 'all', nombre: 'Todas', count: productos.length },
      ...categorias.map((categoria) => ({
        id: categoria.id,
        nombre: categoria.nombre,
        count: counts.get(categoria.id) ?? 0,
        activo: categoria.activo,
      })),
    ]
  }, [categorias, productos])

  const filteredProducts = useMemo(() => {
    const term = normalize(deferredQuery)

    return productos.filter((producto) => {
      if (availability === 'available' && !producto.disponible) {
        return false
      }

      if (availability === 'unavailable' && producto.disponible) {
        return false
      }

      if (
        selectedCategoryId !== 'all' &&
        producto.categoria?.id !== selectedCategoryId
      ) {
        return false
      }

      if (term && !normalize(producto.nombre).includes(term)) {
        return false
      }

      return true
    })
  }, [availability, deferredQuery, productos, selectedCategoryId])

  const hasActiveFilters =
    query.trim() !== '' ||
    availability !== 'all' ||
    selectedCategoryId !== 'all'

  const clearFilters = () => {
    setQuery('')
    setAvailability('all')
    setSelectedCategoryId('all')
  }

  return {
    query,
    setQuery,
    deferredQuery,
    availability,
    setAvailability,
    selectedCategoryId,
    setSelectedCategoryId,
    availabilityCounts,
    categoryOptions,
    filteredProducts,
    hasActiveFilters,
    clearFilters,
  }
}
