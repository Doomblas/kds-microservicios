import {
  AlertTriangle,
  CheckCircle2,
  FolderOpen,
  Layers3,
  PackageOpen,
  RefreshCw,
  SearchX,
  SlidersHorizontal,
  UtensilsCrossed,
  XCircle,
} from 'lucide-react'
import { useState } from 'react'
import { AccessDenied } from './auth/AccessDenied'
import { AuthLoading } from './auth/AuthLoading'
import { AuthScreen } from './auth/AuthScreen'
import { PendingAccount } from './auth/PendingAccount'
import { useAuth } from './auth/useAuth'
import { UserManagement } from './auth/UserManagement'
import type { Usuario } from './auth/types'
import { AvailabilityFilter } from './components/AvailabilityFilter'
import { CategoryDrawer } from './components/CategoryDrawer'
import { CategoryFilter } from './components/CategoryFilter'
import { ConnectionError } from './components/ConnectionError'
import { EmptyState } from './components/EmptyState'
import { Header } from './components/Header'
import { Hero } from './components/Hero'
import { LoadingSkeleton } from './components/LoadingSkeleton'
import { MetricCard } from './components/MetricCard'
import { ProductDrawer } from './components/ProductDrawer'
import { ProductGrid } from './components/ProductGrid'
import { SearchBar } from './components/SearchBar'
import { ToastViewport } from './components/ToastViewport'
import { useMenuData } from './hooks/useMenuData'
import { useMenuFilters } from './hooks/useMenuFilters'
import { useToasts } from './hooks/useToasts'
import { getErrorMessage } from './services/menuApi'
import type { CategoriaPayload, Producto, ProductoPayload } from './types/menu'
import { calculateAvailabilityPercentage } from './utils/format'

interface MenuDashboardProps {
  user: Usuario
  onLogout: (message?: string) => void
}

function MenuDashboard({ user, onLogout }: MenuDashboardProps) {
  const [view, setView] = useState<'menu' | 'usuarios'>('menu')
  const {
    categorias,
    productos,
    loading,
    refreshing,
    error,
    pendingProductIds,
    refresh,
    crearCategoria,
    crearProducto,
    actualizarProducto,
    cambiarDisponibilidad,
  } = useMenuData()

  const { toasts, pushToast, dismissToast } = useToasts()

  const {
    query,
    setQuery,
    availability,
    setAvailability,
    selectedCategoryId,
    setSelectedCategoryId,
    availabilityCounts,
    categoryOptions,
    filteredProducts,
    hasActiveFilters,
    clearFilters,
  } = useMenuFilters(productos, categorias)

  const [productDrawerOpen, setProductDrawerOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<Producto | null>(null)
  const [presetCategoryId, setPresetCategoryId] = useState<
    number | undefined
  >()
  const [categoryDrawerOpen, setCategoryDrawerOpen] = useState(false)

  const totalProductos = productos.length
  const productosDisponibles = availabilityCounts.available
  const productosNoDisponibles = availabilityCounts.unavailable
  const totalCategorias = categorias.length
  const availabilityPercentage = calculateAvailabilityPercentage(
    productosDisponibles,
    totalProductos,
  )
  const hasData = totalProductos > 0 || totalCategorias > 0

  const connection = loading ? 'loading' : error ? 'offline' : 'online'
  const actionsBlocked = loading || (!hasData && Boolean(error))
  const newProductDisabled = actionsBlocked || totalCategorias === 0
  const newProductHint =
    totalCategorias === 0
      ? 'Crea una categoría antes de agregar productos.'
      : undefined

  const selectedCategory =
    selectedCategoryId === 'all'
      ? null
      : (categorias.find(
          (categoria) => categoria.id === selectedCategoryId,
        ) ?? null)

  const handleNewCategory = () => {
    setView('menu')
    setProductDrawerOpen(false)
    setCategoryDrawerOpen(true)
  }

  const handleNewProduct = (categoryId?: number) => {
    setView('menu')
    setCategoryDrawerOpen(false)
    setEditingProduct(null)
    setPresetCategoryId(categoryId)
    setProductDrawerOpen(true)
  }

  const handleEditProduct = (producto: Producto) => {
    setView('menu')
    setCategoryDrawerOpen(false)
    setEditingProduct(producto)
    setPresetCategoryId(undefined)
    setProductDrawerOpen(true)
  }

  const handleOpenUsers = () => {
    setProductDrawerOpen(false)
    setCategoryDrawerOpen(false)
    setView('usuarios')
  }

  const handleRefresh = async () => {
    const refreshed = await refresh()

    if (refreshed) {
      pushToast('info', 'Catálogo actualizado correctamente.')
    } else {
      pushToast(
        'error',
        'No se pudo actualizar el catálogo. Revisa la conexión con ms-menu.',
      )
    }
  }

  const handleProductSubmit = async (payload: ProductoPayload) => {
    if (editingProduct) {
      await actualizarProducto(editingProduct.id, payload)
      setProductDrawerOpen(false)
      pushToast('success', 'Producto actualizado correctamente.')
      return
    }

    await crearProducto(payload)
    setProductDrawerOpen(false)
    pushToast('success', 'Producto creado correctamente.')
  }

  const handleCategorySubmit = async (payload: CategoriaPayload) => {
    await crearCategoria(payload)
    setCategoryDrawerOpen(false)
    pushToast('success', 'Categoría creada correctamente.')
  }

  const handleToggle = async (producto: Producto) => {
    const nextAvailability = !producto.disponible

    try {
      await cambiarDisponibilidad(producto, nextAvailability)
      pushToast(
        'success',
        `${producto.nombre} ${
          nextAvailability
            ? 'ya está disponible'
            : 'se marcó como no disponible'
        }.`,
      )
    } catch (toggleError) {
      pushToast(
        'error',
        getErrorMessage(
          toggleError,
          'No se pudo actualizar la disponibilidad del producto.',
        ),
      )
    }
  }

  const renderCatalog = () => {
    if (loading) {
      return <LoadingSkeleton />
    }

    if (error && !hasData) {
      return (
        <ConnectionError
          message={error}
          refreshing={refreshing}
          onRetry={() => {
            void handleRefresh()
          }}
        />
      )
    }

    if (totalProductos === 0) {
      return (
        <EmptyState
          icon={UtensilsCrossed}
          title="Tu menú aún está vacío"
          description={
            totalCategorias === 0
              ? 'Crea una categoría para organizar el catálogo y después agrega tu primer producto.'
              : 'Ya tienes categorías listas. Agrega el primer producto para comenzar a construir el menú.'
          }
          actionLabel={
            totalCategorias === 0 ? 'Crear primera categoría' : 'Crear producto'
          }
          onAction={() => {
            if (totalCategorias === 0) {
              handleNewCategory()
            } else {
              handleNewProduct()
            }
          }}
        />
      )
    }

    if (filteredProducts.length === 0) {
      const onlyCategoryFilter =
        selectedCategory !== null &&
        availability === 'all' &&
        query.trim() === ''

      if (onlyCategoryFilter) {
        return (
          <EmptyState
            icon={PackageOpen}
            title={`${selectedCategory.nombre} no tiene productos`}
            description="Esta categoría todavía no tiene productos. Crea uno para empezar a mostrarla en el menú."
            actionLabel="Crear producto en esta categoría"
            onAction={() => handleNewProduct(selectedCategory.id)}
            secondaryActionLabel="Ver todo el menú"
            onSecondaryAction={clearFilters}
          />
        )
      }

      return (
        <EmptyState
          icon={query.trim() ? SearchX : SlidersHorizontal}
          title="No encontramos productos"
          description="No hay coincidencias con la búsqueda o los filtros seleccionados. Prueba con otros criterios."
          actionLabel="Limpiar filtros"
          onAction={clearFilters}
        />
      )
    }

    return (
      <ProductGrid
        productos={filteredProducts}
        pendingProductIds={pendingProductIds}
        onToggle={(producto) => {
          void handleToggle(producto)
        }}
        onEdit={handleEditProduct}
      />
    )
  }

  const showingUsers = view === 'usuarios' && user.rol === 'ADMIN'

  return (
    <div className="app-shell">
      <Header
        connection={connection}
        actionsDisabled={actionsBlocked}
        newProductDisabled={newProductDisabled}
        newProductHint={newProductHint}
        user={user}
        usersActive={showingUsers}
        onNewCategory={handleNewCategory}
        onNewProduct={() => handleNewProduct()}
        onOpenUsers={user.rol === 'ADMIN' ? handleOpenUsers : undefined}
        onLogout={() => onLogout()}
      />

      <main className="page" aria-busy={showingUsers ? false : loading}>
        {showingUsers ? (
          <UserManagement
            currentUser={user}
            onBack={() => setView('menu')}
          />
        ) : (
          <>
          {!loading && !(error && !hasData) && (
            <>
              {error && hasData && (
                <div className="inline-error" role="alert">
                  <AlertTriangle size={19} aria-hidden="true" />
                  <p>
                    <strong>No se pudo actualizar el catálogo.</strong> {error}
                  </p>
                  <button
                    type="button"
                    className="btn btn-ghost btn-small"
                    onClick={() => {
                      void handleRefresh()
                    }}
                    disabled={refreshing}
                  >
                    <RefreshCw
                      size={15}
                      className={refreshing ? 'spin' : undefined}
                      aria-hidden="true"
                    />
                    Reintentar
                  </button>
                </div>
              )}
  
              <Hero
                available={productosDisponibles}
                total={totalProductos}
                percentage={availabilityPercentage}
                onNewCategory={handleNewCategory}
                onNewProduct={() => handleNewProduct()}
                actionsDisabled={actionsBlocked}
                newProductDisabled={newProductDisabled}
                newProductHint={newProductHint}
              />
  
              <section className="metric-grid" aria-label="Métricas del menú">
                <MetricCard
                  label="Total de Productos"
                  value={totalProductos}
                  helper="Registrados en el catálogo"
                  icon={Layers3}
                />
                <MetricCard
                  label="Productos Disponibles"
                  value={productosDisponibles}
                  helper="Listos para la venta"
                  icon={CheckCircle2}
                  variant="accent"
                />
                <MetricCard
                  label="No Disponibles"
                  value={productosNoDisponibles}
                  helper="Pausados temporalmente"
                  icon={XCircle}
                  variant="danger"
                />
                <MetricCard
                  label="Total de Categorías"
                  value={totalCategorias}
                  helper="Secciones activas del menú"
                  icon={FolderOpen}
                />
              </section>
  
              <section className="control-bar" aria-label="Buscar y filtrar">
                <SearchBar value={query} onChange={setQuery} />
                <AvailabilityFilter
                  value={availability}
                  counts={availabilityCounts}
                  onChange={setAvailability}
                />
                <button
                  type="button"
                  className="icon-button refresh-button"
                  onClick={() => {
                    void handleRefresh()
                  }}
                  disabled={refreshing}
                  aria-label="Actualizar catálogo"
                  title="Actualizar catálogo"
                >
                  <RefreshCw
                    size={18}
                    className={refreshing ? 'spin' : undefined}
                    aria-hidden="true"
                  />
                </button>
              </section>
  
              <CategoryFilter
                options={categoryOptions}
                selectedId={selectedCategoryId}
                onSelect={setSelectedCategoryId}
              />
  
              {filteredProducts.length > 0 && (
                <div className="results-row">
                  <span aria-live="polite">
                    Mostrando <strong>{filteredProducts.length}</strong> de{' '}
                    <strong>{totalProductos}</strong> productos
                  </span>
                  {hasActiveFilters && (
                    <button
                      type="button"
                      className="text-button"
                      onClick={clearFilters}
                    >
                      Limpiar filtros
                    </button>
                  )}
                </div>
              )}
            </>
          )}
  
          {renderCatalog()}
  
          {!loading && !(error && !hasData) && (
            <footer className="app-footer">
              <span>KDS v1.0 · Administración de menú</span>
              <span>
                {totalProductos} productos · {totalCategorias} categorías
              </span>
            </footer>
          )}
          </>
        )}
      </main>

      <ProductDrawer
        open={productDrawerOpen}
        producto={editingProduct}
        presetCategoryId={presetCategoryId}
        categorias={categorias}
        onClose={() => setProductDrawerOpen(false)}
        onSubmit={handleProductSubmit}
      />

      <CategoryDrawer
        open={categoryDrawerOpen}
        onClose={() => setCategoryDrawerOpen(false)}
        onSubmit={handleCategorySubmit}
      />

      <ToastViewport toasts={toasts} onDismiss={dismissToast} />
    </div>
  )
}

function App() {
  const {
    status,
    user,
    notice,
    login,
    register,
    logout,
    refreshUser,
    clearNotice,
  } = useAuth()

  if (status === 'loading') {
    return <AuthLoading />
  }

  if (status === 'anonymous' || !user) {
    return (
      <AuthScreen
        notice={notice}
        onLogin={login}
        onRegister={register}
        onClearNotice={clearNotice}
      />
    )
  }

  if (user.estado === 'BLOQUEADO') {
    return (
      <AccessDenied
        user={user}
        onLogout={logout}
        reason="blocked"
      />
    )
  }

  if (user.estado === 'PENDIENTE' || !user.rol) {
    return (
      <PendingAccount
        user={user}
        onRefresh={refreshUser}
        onLogout={logout}
      />
    )
  }

  if (user.rol !== 'ADMIN' && user.rol !== 'GESTOR_MENU') {
    return (
      <AccessDenied
        user={user}
        onLogout={logout}
        moduleName="la administración del menú"
        allowedRoles="Administrador o Gestor de menú"
      />
    )
  }

  return <MenuDashboard user={user} onLogout={logout} />
}

export default App
