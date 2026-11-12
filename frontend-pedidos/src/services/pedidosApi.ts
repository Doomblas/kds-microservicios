import type { CrearPedidoRequest, Pedido, PedidoEstado } from '../types/pedidos'
import { requestJson } from './http'

const configuredBaseUrl = import.meta.env.VITE_PEDIDOS_API_URL?.trim()
export const PEDIDOS_API_BASE_URL = (configuredBaseUrl || '').replace(/\/+$/, '')

export const pedidosApi = {
  listar: () => requestJson<Pedido[]>(PEDIDOS_API_BASE_URL, '/pedidos'),

  obtener: (id: number) =>
    requestJson<Pedido>(PEDIDOS_API_BASE_URL, `/pedidos/${id}`),

  obtenerEstado: (id: number) =>
    requestJson<PedidoEstado>(PEDIDOS_API_BASE_URL, `/pedidos/${id}/estado`),

  crear: (payload: CrearPedidoRequest) =>
    requestJson<Pedido>(PEDIDOS_API_BASE_URL, '/pedidos', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
}
