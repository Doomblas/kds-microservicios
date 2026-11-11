package com.kds.menu.controller;

import com.kds.menu.dto.ProductoRequest;
import com.kds.menu.exception.CategoriaNoEncontradaException;
import com.kds.menu.exception.ProductoNoEncontradoException;
import com.kds.menu.model.Categoria;
import com.kds.menu.model.Producto;
import com.kds.menu.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductoController.class)
class ProductoControllerTest {

    private static final String PRODUCTO_VALIDO_JSON = """
            {
              "nombre": "Hamburguesa Clasica",
              "descripcion": "Carne, queso, lechuga y salsa",
              "precio": 4.50,
              "disponible": true,
              "categoriaId": 1
            }
            """;

    private static final String PRODUCTO_INVALIDO_JSON = """
            {
              "nombre": "",
              "descripcion": "x",
              "precio": -1,
              "disponible": null,
              "categoriaId": 0
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    @Test
    void listarProductos_debeDevolver200ConElListado() throws Exception {

        when(productoService.listarTodos()).thenReturn(List.of(crearProducto(true)));

        mockMvc.perform(get("/productos"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Hamburguesa Clasica"))
                .andExpect(jsonPath("$[0].disponible").value(true));

        verify(productoService).listarTodos();
    }

    @Test
    void obtenerProducto_debeDevolver200CuandoExiste() throws Exception {

        when(productoService.buscarPorId(1L)).thenReturn(Optional.of(crearProducto(true)));

        mockMvc.perform(get("/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Hamburguesa Clasica"));

        verify(productoService).buscarPorId(1L);
    }

    @Test
    void obtenerProducto_debeDevolver404CuandoNoExiste() throws Exception {

        when(productoService.buscarPorId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/productos/99"))
                .andExpect(status().isNotFound());

        verify(productoService).buscarPorId(99L);
    }

    @Test
    void crearProducto_debeDevolver200CuandoRequestEsValido() throws Exception {

        when(productoService.crear(any(ProductoRequest.class))).thenReturn(crearProducto(true));

        mockMvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Hamburguesa Clasica"))
                .andExpect(jsonPath("$.precio").value(4.50))
                .andExpect(jsonPath("$.disponible").value(true));

        verify(productoService).crear(any(ProductoRequest.class));
    }

    @Test
    void crearProducto_debeDevolver400ConFieldErrorsCuandoRequestEsInvalido() throws Exception {

        mockMvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_INVALIDO_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Error de validacion"))
                .andExpect(jsonPath("$.path").value("/productos"))
                .andExpect(jsonPath("$.fieldErrors.nombre").exists())
                .andExpect(jsonPath("$.fieldErrors.precio").exists())
                .andExpect(jsonPath("$.fieldErrors.disponible").exists())
                .andExpect(jsonPath("$.fieldErrors.categoriaId").exists());

        verifyNoInteractions(productoService);
    }

    @Test
    void crearProducto_debeDevolver404CuandoCategoriaNoExiste() throws Exception {

        when(productoService.crear(any(ProductoRequest.class)))
                .thenThrow(new CategoriaNoEncontradaException(99L));

        mockMvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Categoria no encontrada con id: 99"))
                .andExpect(jsonPath("$.path").value("/productos"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void actualizarProducto_debeDevolver200CuandoRequestEsValido() throws Exception {

        when(productoService.actualizar(eq(1L), any(ProductoRequest.class)))
                .thenReturn(crearProducto(false));

        mockMvc.perform(put("/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.disponible").value(false));

        verify(productoService).actualizar(eq(1L), any(ProductoRequest.class));
    }

    @Test
    void actualizarProducto_debeDevolver404CuandoNoExiste() throws Exception {

        when(productoService.actualizar(eq(99L), any(ProductoRequest.class)))
                .thenThrow(new ProductoNoEncontradoException(99L));

        mockMvc.perform(put("/productos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Producto no encontrado con id: 99"))
                .andExpect(jsonPath("$.path").value("/productos/99"));
    }

    @Test
    void actualizarDisponibilidad_debeDevolver200CuandoRequestEsValido() throws Exception {

        when(productoService.actualizarDisponibilidad(1L, false))
                .thenReturn(crearProducto(false));

        mockMvc.perform(patch("/productos/1/disponibilidad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disponible": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.disponible").value(false));

        verify(productoService).actualizarDisponibilidad(1L, false);
    }

    @Test
    void actualizarDisponibilidad_debeDevolver400CuandoDisponibleEsNulo() throws Exception {

        mockMvc.perform(patch("/productos/1/disponibilidad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disponible": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Error de validacion"))
                .andExpect(jsonPath("$.fieldErrors.disponible").exists());

        verifyNoInteractions(productoService);
    }

    @Test
    void actualizarDisponibilidad_debeDevolver404CuandoProductoNoExiste() throws Exception {

        when(productoService.actualizarDisponibilidad(99L, false))
                .thenThrow(new ProductoNoEncontradoException(99L));

        mockMvc.perform(patch("/productos/99/disponibilidad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disponible": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Producto no encontrado con id: 99"))
                .andExpect(jsonPath("$.path").value("/productos/99/disponibilidad"));
    }

    @Test
    void requestMalformado_debeDevolver400ConApiError() throws Exception {

        mockMvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud no es valido"))
                .andExpect(jsonPath("$.path").value("/productos"));

        verifyNoInteractions(productoService);
    }

    @Test
    void errorInesperado_debeDevolver500SinExponerDetalleInterno() throws Exception {

        when(productoService.listarTodos())
                .thenThrow(new RuntimeException("fallo interno de prueba"));

        mockMvc.perform(get("/productos"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"))
                .andExpect(jsonPath("$.path").value("/productos"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(content().string(not(containsString("fallo interno de prueba"))));
    }

    private Producto crearProducto(boolean disponible) {

        Categoria categoria = new Categoria();
        categoria.setId(1L);
        categoria.setNombre("Hamburguesas");
        categoria.setActivo(true);

        Producto producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Hamburguesa Clasica");
        producto.setDescripcion("Carne, queso, lechuga y salsa");
        producto.setPrecio(new BigDecimal("4.50"));
        producto.setDisponible(disponible);
        producto.setCategoria(categoria);
        return producto;
    }
}
