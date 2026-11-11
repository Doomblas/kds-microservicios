import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage, menuApi } from '../services/menuApi'
import type {
  Categoria,
  CategoriaPayload,
  Producto,
  ProductoPayload,
} from '../types/menu'

const byName = (a: Categoria, b: Categoria) =>
  a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' })

export function useMenuData() {
  const [categorias, setCategorias] = useState<Categoria[]>([])
  const [productos, setProductos] = useState<Producto[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pendingProductIds, setPendingProductIds] = useState<Set<number>>(
    () => new Set(),
  )

  const load = useCallback(async (quiet = false) => {
    if (quiet) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }

    try {
      const [categoriasData, productosData] = await Promise.all([
        menuApi.getCategorias(),
        menuApi.getProductos(),
      ])

      setCategorias([...categoriasData].sort(byName))
      setProductos(
        [...productosData].sort((a, b) =>
          a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
        ),
      )
      setError(null)
      return true
    } catch (loadError) {
      setError(
        getErrorMessage(loadError, 'No se pudo cargar el catálogo del menú.'),
      )
      return false
    } finally {
      if (quiet) {
        setRefreshing(false)
      } else {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const refresh = useCallback(() => load(true), [load])

  const crearCategoria = useCallback(async (payload: CategoriaPayload) => {
    const created = await menuApi.crearCategoria(payload)
    setCategorias((current) => [...current, created].sort(byName))
    return created
  }, [])

  const crearProducto = useCallback(async (payload: ProductoPayload) => {
    const created = await menuApi.crearProducto(payload)
    setProductos((current) =>
      [...current, created].sort((a, b) =>
        a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
      ),
    )
    return created
  }, [])

  const actualizarProducto = useCallback(
    async (id: number, payload: ProductoPayload) => {
      const updated = await menuApi.actualizarProducto(id, payload)
      setProductos((current) =>
        current
          .map((producto) => (producto.id === id ? updated : producto))
          .sort((a, b) =>
            a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
          ),
      )
      return updated
    },
    [],
  )

  const cambiarDisponibilidad = useCallback(
    async (producto: Producto, disponible: boolean) => {
      setPendingProductIds((current) => new Set(current).add(producto.id))
      setProductos((current) =>
        current.map((item) =>
          item.id === producto.id ? { ...item, disponible } : item,
        ),
      )

      try {
        const updated = await menuApi.actualizarDisponibilidad(
          producto.id,
          disponible,
        )

        setProductos((current) =>
          current.map((item) => (item.id === producto.id ? updated : item)),
        )

        return updated
      } catch (toggleError) {
        setProductos((current) =>
          current.map((item) =>
            item.id === producto.id
              ? { ...item, disponible: producto.disponible }
              : item,
          ),
        )
        throw toggleError
      } finally {
        setPendingProductIds((current) => {
          const next = new Set(current)
          next.delete(producto.id)
          return next
        })
      }
    },
    [],
  )

  return {
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
  }
}
