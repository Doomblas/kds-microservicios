export type Estado = 'PENDIENTE' | 'PREPARANDO' | 'LISTO'

export type Orden = {
  id: number
  pedidoId: number
  numeroPedido: string
  estado: Estado
  fechaRecepcion: string
  fechaInicio: string | null
  fechaFinalizacion: string | null
  items: { productoId: number; nombreProducto: string; cantidad: number }[]
}

export type NotificacionNueva = {
  tipo: string
  pedidoId: number
  numeroPedido: string
}

export type Toast = {
  kind: 'success' | 'info' | 'error'
  message: string
}
