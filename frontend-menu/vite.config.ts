import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_MENU_API_TARGET || 'http://localhost:8080'

  // ms-menu no expone CORS; el proxy permite consumirlo en desarrollo y preview.
  const proxy = {
    '/api': {
      target: proxyTarget,
      changeOrigin: true,
      rewrite: (path: string) => path.replace(/^\/api/, ''),
    },
  }

  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      port: 5174,
      strictPort: true,
      proxy,
    },
    preview: {
      host: '0.0.0.0',
      port: 4174,
      strictPort: true,
      proxy,
    },
  }
})
