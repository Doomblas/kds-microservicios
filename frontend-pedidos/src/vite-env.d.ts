/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_PEDIDOS_API_URL?: string
  readonly VITE_PEDIDOS_API_TARGET?: string
  readonly VITE_MENU_API_URL?: string
  readonly VITE_MENU_API_TARGET?: string
  readonly VITE_AUTH_API_URL?: string
  readonly VITE_WS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
