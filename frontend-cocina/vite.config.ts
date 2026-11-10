import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendUrl = env.KDS_API_URL || 'http://localhost:8083'

  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      proxy: {
        '/ordenes': {
          target: backendUrl,
          changeOrigin: true,
        },
        '/ws': {
          target: backendUrl,
          ws: true,
          changeOrigin: true,
        },
      },
    },
  }
})
