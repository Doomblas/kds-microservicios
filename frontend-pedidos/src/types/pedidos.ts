export type EstadoPedido = 'PENDIENTE' | 'PREPARANDO' | 'LISTO'

export interface Pedido {
  id: number
  numeroPedido: string
  estado: EstadoPedido
  total: number
  fechaCreacion: string
}

export interface PedidoEstado {
  id: number
  numeroPedido: string
  estado: EstadoPedido
}

export interface CrearPedidoItem {
  productoId: number
  cantidad: number
}

export interface CrearPedidoRequest {
  items: CrearPedidoItem[]
}

export interface NotificacionCaja {
  tipo: string
  pedidoId: number
  numeroPedido: string
  estado: EstadoPedido
}

export interface ProductoMenu {
  id: number
  nombre: string
  descripcion: string | null
  precio: number
  disponible: boolean
  categoria?: {
    id: number
    nombre: string
    activo: boolean
  }
}

export interface CartItem {
  productoId: number
  nombre: string
  precio: number
  cantidad: number
}

export interface ApiErrorPayload {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
}

export type ToastKind = 'success' | 'info' | 'error'

export interface Toast {
  kind: ToastKind
  message: string
}
