import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const pedidosTarget = env.VITE_PEDIDOS_API_TARGET || 'http://localhost:8082'
  const menuTarget = env.VITE_MENU_API_TARGET || 'http://localhost:8080'

  // ms-pedidos y ms-menu no exponen CORS; el proxy unifica el origen en desarrollo.
  const proxy = {
    '/pedidos': {
      target: pedidosTarget,
      changeOrigin: true,
    },
    '/ws': {
      target: pedidosTarget,
      ws: true,
      changeOrigin: true,
    },
    '/productos': {
      target: menuTarget,
      changeOrigin: true,
    },
    '/categorias': {
      target: menuTarget,
      changeOrigin: true,
    },
  }

  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      port: 5176,
      strictPort: true,
      proxy,
    },
    preview: {
      host: '0.0.0.0',
      port: 4176,
      strictPort: true,
      proxy,
    },
  }
})
