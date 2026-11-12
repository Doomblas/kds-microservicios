package com.kds.menu.controller;

import com.kds.menu.auth.security.JwtService;
import com.kds.menu.dto.CategoriaRequest;
import com.kds.menu.exception.RecursoDuplicadoException;
import com.kds.menu.model.Categoria;
import com.kds.menu.service.CategoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CategoriaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoriaControllerTest {

    private static final String CATEGORIA_VALIDA_JSON = """
            {
              "nombre": "Hamburguesas",
              "activo": true
            }
            """;

    private static final String CATEGORIA_INVALIDA_JSON = """
            {
              "nombre": "   ",
              "activo": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoriaService categoriaService;

    /**
     * El filtro de JWT se registra en el slice de pruebas, pero estas pruebas
     * validan el contrato del controlador sin cadena de seguridad.
     */
    @MockitoBean
    private JwtService jwtService;

    @Test
    void listarCategorias_debeDevolver200ConElListado() throws Exception {

        when(categoriaService.listarTodas())
                .thenReturn(List.of(crearCategoria(1L, "Hamburguesas")));

        mockMvc.perform(get("/categorias"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Hamburguesas"));

        verify(categoriaService).listarTodas();
    }

    @Test
    void obtenerCategoria_debeDevolver200CuandoExiste() throws Exception {

        when(categoriaService.buscarPorId(1L))
                .thenReturn(Optional.of(crearCategoria(1L, "Hamburguesas")));

        mockMvc.perform(get("/categorias/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Hamburguesas"));

        verify(categoriaService).buscarPorId(1L);
    }

    @Test
    void obtenerCategoria_debeDevolver404CuandoNoExiste() throws Exception {

        when(categoriaService.buscarPorId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/categorias/99"))
                .andExpect(status().isNotFound());

        verify(categoriaService).buscarPorId(99L);
    }

    @Test
    void crearCategoria_debeDevolver200CuandoRequestEsValido() throws Exception {

        when(categoriaService.crear(any(CategoriaRequest.class)))
                .thenReturn(crearCategoria(1L, "Hamburguesas"));

        mockMvc.perform(post("/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CATEGORIA_VALIDA_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Hamburguesas"))
                .andExpect(jsonPath("$.activo").value(true));

        verify(categoriaService).crear(any(CategoriaRequest.class));
    }

    @Test
    void crearCategoria_debeDevolver400ConFieldErrorsCuandoRequestEsInvalido() throws Exception {

        mockMvc.perform(post("/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CATEGORIA_INVALIDA_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Error de validacion"))
                .andExpect(jsonPath("$.path").value("/categorias"))
                .andExpect(jsonPath("$.fieldErrors.nombre").exists())
                .andExpect(jsonPath("$.fieldErrors.activo").exists());

        verifyNoInteractions(categoriaService);
    }

    @Test
    void crearCategoria_debeDevolver409CuandoNombreDuplicado() throws Exception {

        when(categoriaService.crear(any(CategoriaRequest.class)))
                .thenThrow(new RecursoDuplicadoException(
                        "Ya existe una categoria con el nombre: hamburguesas"));

        mockMvc.perform(post("/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "hamburguesas",
                                  "activo": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Ya existe una categoria con el nombre: hamburguesas"))
                .andExpect(jsonPath("$.path").value("/categorias"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    private Categoria crearCategoria(Long id, String nombre) {

        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setActivo(true);
        return categoria;
    }
}
