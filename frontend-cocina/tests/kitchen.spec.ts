import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type WebSocketRoute } from '@playwright/test'
import type { Usuario } from '../src/auth/types'
import type { Orden } from '../src/types'

const testUser: Usuario = {
  id: 10,
  nombre: 'Cocina KDS',
  email: 'cocina@kds.local',
  rol: 'COCINA',
  estado: 'ACTIVO',
  fechaCreacion: new Date().toISOString(),
}

async function mockSession(
  page: Page,
  user: Usuario = testUser,
  token = 'playwright-test-token',
) {
  await page.addInitScript(
    ({ sessionToken, sessionUser }) => {
      window.localStorage.setItem('kds.auth.token', sessionToken)
      window.localStorage.setItem(
        'kds.auth.user',
        JSON.stringify(sessionUser),
      )
    },
    { sessionToken: token, sessionUser: user },
  )
  await page.route('**/auth/me', (route) => route.fulfill({ json: user }))
  return user
}

function fixtures(now = Date.now()): Orden[] {
  const ago = (minutes: number) => new Date(now - minutes * 60000).toISOString()
  return [
    { id: 1, pedidoId: 101, numeroPedido: 'ORD-0101', estado: 'PENDIENTE', fechaRecepcion: ago(12), fechaInicio: null, fechaFinalizacion: null, items: [{ productoId: 1, nombreProducto: 'Ribeye con romero y mantequilla ahumada', cantidad: 2 }, { productoId: 2, nombreProducto: 'Ensalada de la casa', cantidad: 1 }] },
    { id: 2, pedidoId: 102, numeroPedido: 'ORD-0102', estado: 'PREPARANDO', fechaRecepcion: ago(8), fechaInicio: ago(5), fechaFinalizacion: null, items: [{ productoId: 3, nombreProducto: 'Risotto de hongos silvestres', cantidad: 1 }] },
    { id: 3, pedidoId: 103, numeroPedido: 'ORD-0103', estado: 'LISTO', fechaRecepcion: ago(20), fechaInicio: ago(18), fechaFinalizacion: ago(2), items: [{ productoId: 4, nombreProducto: 'Pulpo a la brasa con patata y pimentón', cantidad: 3 }] },
  ]
}

async function mockKitchen(
  page: Page,
  initialOrders = fixtures(),
  session = true,
) {
  if (session) await mockSession(page)
  const state = { orders: structuredClone(initialOrders), failRead: false, failPatch: false, patches: [] as string[], sockets: [] as WebSocketRoute[] }
  await page.routeWebSocket('**/ws', (socket) => {
    state.sockets.push(socket)
    socket.onMessage((message) => {
      if (String(message).startsWith('CONNECT')) socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0')
    })
  })
  await page.route(/\/ordenes(?:\/\d+\/estado)?$/, async (route) => {
    if (route.request().method() === 'PATCH') {
      const next = route.request().postDataJSON().estado as Orden['estado']
      state.patches.push(next)
      if (state.failPatch) return route.fulfill({ status: 409, json: { message: 'Otra estación actualizó la comanda. Revisa su estado.' } })
      const id = Number(route.request().url().match(/\/ordenes\/(\d+)/)?.[1])
      const order = state.orders.find((item) => item.id === id)!
      order.estado = next
      if (next === 'PREPARANDO') order.fechaInicio = new Date().toISOString()
      if (next === 'LISTO') order.fechaFinalizacion = new Date().toISOString()
      return route.fulfill({ json: order })
    }
    return route.fulfill(state.failRead ? { status: 503, json: { message: 'Servicio no disponible' } } : { json: state.orders })
  })
  return state
}

test('muestra estados reales, busca por producto y combina los filtros', async ({ page }) => {
  await mockKitchen(page)
  await page.goto('/')
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()
  await expect(page.locator('.order-card')).toHaveCount(3)
  await page.getByRole('searchbox').fill('risotto')
  await expect(page.locator('.order-card')).toHaveCount(1)
  await expect(page.locator('.order-card')).toContainText('ORD-0102')
  await page.getByRole('button', { name: 'Por preparar 1', exact: true }).click()
  await expect(page.locator('.order-card')).toHaveCount(0)
  await expect(page.getByText('Sin coincidencias', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Limpiar filtros' }).click()
  await expect(page.locator('.order-card')).toHaveCount(3)
  await expect(page.getByRole('searchbox')).toBeFocused()
})

test('avanza sólo por los estados permitidos y conserva el foco de teclado', async ({ page }) => {
  const state = await mockKitchen(page)
  await page.goto('/')
  const start = page.getByRole('button', { name: 'Iniciar preparación: ORD-0101' })
  await start.focus()
  await page.keyboard.press('Enter')
  const ready = page.getByRole('button', { name: 'Marcar como listo: ORD-0101' })
  await expect(ready).toBeVisible()
  await expect(ready).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('[data-order-id="1"] .ready-stamp')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'ORD-0101' })).toBeFocused()
  expect(state.patches).toEqual(['PREPARANDO', 'LISTO'])
})

test('un fallo de PATCH se muestra como error sin fingir un cambio de estado', async ({ page }) => {
  const state = await mockKitchen(page)
  state.failPatch = true
  await page.goto('/')
  await page.getByRole('button', { name: 'Iniciar preparación: ORD-0101' }).click()
  await expect(page.getByRole('alert')).toContainText('Otra estación actualizó')
  await expect(page.locator('[data-order-id="1"]')).toHaveClass(/status-pendiente/)
  await expect(page.getByRole('button', { name: 'Iniciar preparación: ORD-0101' })).toBeEnabled()
})

test('distingue falta de conexión, datos conservados y un tablero realmente vacío', async ({ page }) => {
  const state = await mockKitchen(page)
  await page.goto('/')
  await expect(page.locator('.order-card')).toHaveCount(3)
  state.failRead = true
  await page.getByRole('button', { name: 'Actualizar', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Se conservan los últimos datos')
  await expect(page.locator('.order-card')).toHaveCount(3)
  await expect(page.getByText('Sin sincronizar', { exact: true })).toBeVisible()
  state.failRead = false
  state.orders = []
  await page.getByRole('button', { name: 'Reintentar' }).click()
  await expect(page.locator('.error-banner')).toHaveCount(0)
  await expect(page.getByText('Al día por aquí', { exact: true })).toBeVisible()
})

test('el fallo inicial no se presenta como un tablero vacío exitoso', async ({ page }) => {
  const state = await mockKitchen(page)
  state.failRead = true
  await page.goto('/')
  await expect(page.getByRole('alert')).toContainText('Aún no se han recibido comandas')
  await expect(page.getByText('Esperando conexión', { exact: true })).toHaveCount(3)
  await expect(page.locator('.metric-card > strong').first()).toHaveText('—')
})

test('recibe una comanda por STOMP y muestra aviso visual', async ({ page }) => {
  const state = await mockKitchen(page, [])
  await page.goto('/')
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()
  state.orders = fixtures().slice(0, 1)
  const body = JSON.stringify({ tipo: 'PEDIDO_CREADO', pedidoId: 101, numeroPedido: 'ORD-0101' })
  for (const socket of state.sockets) socket.send(`MESSAGE\nsubscription:sub-0\nmessage-id:test-1\ndestination:/topic/cocina\ncontent-type:application/json\n\n${body}\0`)
  await expect(page.locator('.order-card')).toHaveCount(1)
  await expect(page.getByText('Nueva comanda · ORD-0101')).toBeVisible()
})

test('atajo de búsqueda y controles accesibles con teclado', async ({ page }) => {
  await mockKitchen(page)
  await page.goto('/')
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()
  await page.keyboard.press('Control+k')
  await expect(page.getByRole('searchbox')).toBeFocused()
  await page.getByRole('button', { name: 'Aviso sonoro de nuevas comandas' }).click()
  await expect(page.getByRole('button', { name: 'Aviso sonoro de nuevas comandas' })).toHaveAttribute('aria-pressed', 'true')
})

test('pantalla completa prioriza las comandas y restaura la vista al salir', async ({ page }) => {
  await mockKitchen(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Pantalla completa', exact: true }).click()
  await expect(page.locator('.hero')).toBeHidden()
  await expect(page.getByRole('button', { name: 'Salir de pantalla completa' })).toBeVisible()
  await page.evaluate(() => document.exitFullscreen())
  await expect(page.locator('.hero')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Pantalla completa', exact: true })).toBeVisible()
})

test('el reloj avanza en comandas activas pero no en las ya terminadas', async ({ page }) => {
  const now = Date.now()
  await page.clock.install({ time: now })
  await mockKitchen(page, fixtures(now))
  await page.goto('/')
  const active = page.locator('[data-order-id="1"] .order-elapsed')
  const ready = page.locator('[data-order-id="3"] .order-elapsed')
  await expect(active).toContainText('12 min')
  await expect(ready).toContainText('18 min')
  await page.clock.fastForward(125000)
  await expect(active).toContainText('14 min')
  await expect(ready).toContainText('18 min')
})

test('sin sesión muestra el acceso y permite abrir el registro', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
  await expect(page.locator('.order-card')).toHaveCount(0)
  await page.getByRole('tab', { name: 'Registrarme' }).click()
  await expect(page.getByRole('heading', { name: 'Crear cuenta' })).toBeVisible()
  await expect(page.getByLabel('Nombre completo')).toBeVisible()
})

test('el login de Cocina entra al tablero y guarda la sesión', async ({ page }) => {
  await mockKitchen(page, fixtures(), false)
  await page.route('**/auth/login', (route) =>
    route.fulfill({
      json: {
        token: 'new-session-token',
        tipo: 'Bearer',
        expiraEnSegundos: 1800,
        usuario: testUser,
      },
    }),
  )

  await page.goto('/')
  await page.getByLabel('Correo electrónico').fill(testUser.email)
  await page.locator('#auth-password').fill('CocinaSegura123')
  await page.getByRole('button', { name: 'Ingresar al sistema' }).click()

  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()
  await expect(page.locator('.order-card')).toHaveCount(3)
  const token = await page.evaluate(() => window.localStorage.getItem('kds.auth.token'))
  expect(token).toBe('new-session-token')
})

test('una cuenta pendiente no accede al tablero de cocina', async ({ page }) => {
  await mockSession(page, {
    ...testUser,
    rol: null,
    estado: 'PENDIENTE',
    email: 'pendiente@kds.local',
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Esperando asignación de rol' })).toBeVisible()
  await expect(page.locator('.order-card')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Actualizar estado' })).toBeVisible()
})

test('un rol sin permiso ve la pantalla de acceso denegado', async ({ page }) => {
  await mockSession(page, { ...testUser, rol: 'CAJA', email: 'caja@kds.local' })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'No tienes acceso a este módulo' })).toBeVisible()
  await expect(page.getByText(/la estación de cocina/)).toBeVisible()
  await expect(page.locator('.order-card')).toHaveCount(0)
})

for (const width of [1440, 1024, 768, 390, 320]) {
  test(`sin desbordamiento ni infracciones automáticas WCAG AA a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1100 })
    const state = await mockKitchen(page)
    // Los números largos que genera Pedidos y nombres extensos deben caber.
    state.orders[0].numeroPedido = 'ORD-1794349037164'
    await page.goto('/')
    await expect(page.locator('.order-card')).toHaveCount(3)
    await page.evaluate(() => document.fonts.ready)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
    expect(overflow).toBe(false)
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(results.violations).toEqual([])
    const targets = await page.locator('button:visible').evaluateAll((buttons) => buttons.map((button) => {
      const { width: buttonWidth, height } = button.getBoundingClientRect()
      return { name: button.getAttribute('aria-label') ?? button.textContent, width: buttonWidth, height }
    }))
    expect(targets.filter((target) => target.width < 44 || target.height < 44)).toEqual([])
  })
}
