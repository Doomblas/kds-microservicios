import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

function looksLikeGateway(url: string) {
  try {
    const parsed = new URL(url)
    return (
      parsed.port === '8080' ||
      parsed.hostname === 'api-gateway' ||
      parsed.pathname.replace(/\/+$/, '') === '/gateway'
    )
  } catch {
    return false
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // Preferir Gateway si está definido; si no, backends directos (3 PC / diagnóstico).
  const gatewayUrl = env.VITE_GATEWAY_URL?.trim()
  const pedidosTarget =
    env.VITE_PEDIDOS_API_TARGET?.trim() ||
    gatewayUrl ||
    'http://localhost:8082'
  const menuTarget =
    env.VITE_MENU_API_TARGET?.trim() ||
    gatewayUrl ||
    'http://localhost:8080'
  const wsViaGateway =
    env.VITE_WS_VIA_GATEWAY === 'true' ||
    Boolean(gatewayUrl) ||
    looksLikeGateway(pedidosTarget)

  // Sin CORS: Vite unifica origen. Si el destino es el Gateway, /ws público
  // es /ws/pedidos (igual que nginx.conf); ms-pedidos directo sigue en /ws.
  const proxy = {
    '/pedidos': {
      target: pedidosTarget,
      changeOrigin: true,
    },
    '/ws': {
      target: pedidosTarget,
      ws: true,
      changeOrigin: true,
      ...(wsViaGateway
        ? {
            rewrite: (path: string) =>
              path.replace(/^\/ws/, '/ws/pedidos'),
          }
        : {}),
    },
    '/productos': {
      target: menuTarget,
      changeOrigin: true,
    },
    '/categorias': {
      target: menuTarget,
      changeOrigin: true,
    },
    '/auth': {
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
