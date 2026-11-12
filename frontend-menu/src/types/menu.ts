export interface Categoria {
  id: number
  nombre: string
  activo: boolean
}

export interface Producto {
  id: number
  nombre: string
  descripcion: string | null
  precio: number
  disponible: boolean
  categoria: Categoria
}

export interface CategoriaPayload {
  nombre: string
  activo: boolean
}

export interface ProductoPayload {
  nombre: string
  descripcion: string
  precio: number
  disponible: boolean
  categoriaId: number
}

export interface ApiErrorPayload {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
  fieldErrors?: Record<string, string>
}

export type AvailabilityFilter = 'all' | 'available' | 'unavailable'

export interface CategoryFilterOption {
  id: number | 'all'
  nombre: string
  count: number
  activo?: boolean
}
