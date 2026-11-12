package com.kds.menu.service;

import com.kds.menu.dto.ProductoRequest;
import com.kds.menu.exception.CategoriaNoEncontradaException;
import com.kds.menu.exception.ProductoNoEncontradoException;
import com.kds.menu.exception.SolicitudInvalidaException;
import com.kds.menu.model.Categoria;
import com.kds.menu.model.Producto;
import com.kds.menu.repository.CategoriaRepository;
import com.kds.menu.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProductoService productoService;

    @Test
    void crear_debeGuardarProductoCuandoCategoriaExiste() {

        ProductoRequest request = crearRequest(
                "Hamburguesa Clasica",
                "Carne, queso, lechuga y salsa",
                "12.50",
                true,
                1L);

        Categoria categoria = crearCategoria(1L, "Hamburguesas");

        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto producto = invocation.getArgument(0);
            producto.setId(10L);
            return producto;
        });

        Producto resultado = productoService.crear(request);

        assertThat(resultado.getId()).isEqualTo(10L);
        assertThat(resultado.getNombre()).isEqualTo("Hamburguesa Clasica");
        assertThat(resultado.getDescripcion()).isEqualTo("Carne, queso, lechuga y salsa");
        assertThat(resultado.getPrecio()).isEqualByComparingTo("12.50");
        assertThat(resultado.getDisponible()).isTrue();
        assertThat(resultado.getCategoria()).isSameAs(categoria);

        verify(categoriaRepository).findById(1L);
        verify(productoRepository).save(any(Producto.class));
    }

    @Test
    void crear_debeFallarCuandoCategoriaNoExiste() {

        ProductoRequest request = crearRequest(
                "Hamburguesa Clasica",
                "Carne, queso, lechuga y salsa",
                "12.50",
                true,
                99L);

        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.crear(request))
                .isInstanceOf(CategoriaNoEncontradaException.class)
                .hasMessage("Categoria no encontrada con id: 99");

        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void actualizar_debeGuardarProductoCuandoProductoYCategoriaExisten() {

        Categoria categoriaOriginal = crearCategoria(1L, "Hamburguesas");
        Categoria categoriaNueva = crearCategoria(2L, "Bebidas");
        Producto producto = crearProducto(10L, categoriaOriginal);

        ProductoRequest request = crearRequest(
                "Hamburguesa Especial",
                "Carne, queso y tocino",
                "15.75",
                false,
                2L);

        when(productoRepository.findById(10L)).thenReturn(Optional.of(producto));
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoriaNueva));
        when(productoRepository.save(producto)).thenReturn(producto);

        Producto resultado = productoService.actualizar(10L, request);

        assertThat(resultado.getId()).isEqualTo(10L);
        assertThat(resultado.getNombre()).isEqualTo("Hamburguesa Especial");
        assertThat(resultado.getDescripcion()).isEqualTo("Carne, queso y tocino");
        assertThat(resultado.getPrecio()).isEqualByComparingTo("15.75");
        assertThat(resultado.getDisponible()).isFalse();
        assertThat(resultado.getCategoria()).isSameAs(categoriaNueva);

        verify(productoRepository).findById(10L);
        verify(categoriaRepository).findById(2L);
        verify(productoRepository).save(producto);
    }

    @Test
    void actualizar_debeFallarCuandoProductoNoExiste() {

        ProductoRequest request = crearRequest(
                "Hamburguesa Especial",
                "Carne, queso y tocino",
                "15.75",
                false,
                2L);

        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizar(99L, request))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessage("Producto no encontrado con id: 99");

        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void actualizar_debeFallarCuandoCategoriaNoExiste() {

        Categoria categoriaOriginal = crearCategoria(1L, "Hamburguesas");
        Producto producto = crearProducto(10L, categoriaOriginal);

        ProductoRequest request = crearRequest(
                "Hamburguesa Especial",
                "Carne, queso y tocino",
                "15.75",
                false,
                99L);

        when(productoRepository.findById(10L)).thenReturn(Optional.of(producto));
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizar(10L, request))
                .isInstanceOf(CategoriaNoEncontradaException.class)
                .hasMessage("Categoria no encontrada con id: 99");

        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void actualizarDisponibilidad_debeModificarSoloDisponibilidad() {

        Categoria categoria = crearCategoria(1L, "Hamburguesas");
        Producto producto = crearProducto(10L, categoria);

        when(productoRepository.findById(10L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(producto)).thenReturn(producto);

        Producto resultado = productoService.actualizarDisponibilidad(10L, false);

        assertThat(resultado.getDisponible()).isFalse();
        assertThat(resultado.getNombre()).isEqualTo(producto.getNombre());
        assertThat(resultado.getDescripcion()).isEqualTo(producto.getDescripcion());
        assertThat(resultado.getPrecio()).isEqualByComparingTo(producto.getPrecio());
        assertThat(resultado.getCategoria()).isSameAs(categoria);

        verify(productoRepository).findById(10L);
        verify(productoRepository).save(producto);
    }

    @Test
    void actualizarDisponibilidad_debeFallarCuandoProductoNoExiste() {

        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizarDisponibilidad(99L, false))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessage("Producto no encontrado con id: 99");

        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void actualizarDisponibilidad_debeFallarCuandoDisponibilidadEsNula() {

        assertThatThrownBy(() -> productoService.actualizarDisponibilidad(10L, null))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("El campo disponible es obligatorio");

        verifyNoInteractions(productoRepository, categoriaRepository);
    }

    private ProductoRequest crearRequest(
            String nombre,
            String descripcion,
            String precio,
            Boolean disponible,
            Long categoriaId) {

        ProductoRequest request = new ProductoRequest();
        request.setNombre(nombre);
        request.setDescripcion(descripcion);
        request.setPrecio(new BigDecimal(precio));
        request.setDisponible(disponible);
        request.setCategoriaId(categoriaId);
        return request;
    }

    private Categoria crearCategoria(Long id, String nombre) {

        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setActivo(true);
        return categoria;
    }

    private Producto crearProducto(Long id, Categoria categoria) {

        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre("Producto Original");
        producto.setDescripcion("Descripcion original");
        producto.setPrecio(new BigDecimal("8.00"));
        producto.setDisponible(true);
        producto.setCategoria(categoria);
        return producto;
    }
}
