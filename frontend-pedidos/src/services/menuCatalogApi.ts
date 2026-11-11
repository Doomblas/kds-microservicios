import type { ProductoMenu } from '../types/pedidos'
import { requestJson } from './http'

const configuredBaseUrl = import.meta.env.VITE_MENU_API_URL?.trim()
export const MENU_API_BASE_URL = (configuredBaseUrl || '').replace(/\/+$/, '')

export const menuCatalogApi = {
  getProductos: () =>
    requestJson<ProductoMenu[]>(MENU_API_BASE_URL, '/productos'),
}
