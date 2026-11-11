export type Rol = 'ADMIN' | 'GESTOR_MENU' | 'CAJA' | 'COCINA'

export type EstadoUsuario = 'PENDIENTE' | 'ACTIVO' | 'BLOQUEADO'

export interface Usuario {
  id: number
  nombre: string
  email: string
  rol: Rol | null
  estado: EstadoUsuario
  fechaCreacion: string
}

export interface AuthResponse {
  token: string
  tipo: string
  expiraEnSegundos: number
  usuario: Usuario
}

export interface RegistroPayload {
  nombre: string
  email: string
  password: string
}

export interface ApiErrorPayload {
  status?: number
  error?: string
  message?: string
  fieldErrors?: Record<string, string>
}

export const rolLabel: Record<Rol, string> = {
  ADMIN: 'Administrador',
  GESTOR_MENU: 'Gestor de menú',
  CAJA: 'Caja',
  COCINA: 'Cocina',
}

export const estadoLabel: Record<EstadoUsuario, string> = {
  PENDIENTE: 'Pendiente',
  ACTIVO: 'Activo',
  BLOQUEADO: 'Bloqueado',
}

export const moduleByRole: Record<Rol, string> = {
  ADMIN: 'todos los módulos',
  GESTOR_MENU: 'Menú',
  CAJA: 'Pedidos / Caja',
  COCINA: 'Cocina',
}
