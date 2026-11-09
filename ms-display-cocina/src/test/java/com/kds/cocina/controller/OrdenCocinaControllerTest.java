package com.kds.cocina.controller;

import com.kds.cocina.dto.ItemOrdenResponse;
import com.kds.cocina.dto.OrdenCocinaResponse;
import com.kds.cocina.exception.RecursoNoEncontradoException;
import com.kds.cocina.exception.TransicionEstadoInvalidaException;
import com.kds.cocina.model.EstadoOrden;
import com.kds.cocina.service.OrdenCocinaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrdenCocinaControllerTest {

    @Mock
    private OrdenCocinaService ordenCocinaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrdenCocinaController(ordenCocinaService))
                .setControllerAdvice(new com.kds.cocina.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void listaOrdenesDeCocina() throws Exception {
        when(ordenCocinaService.listarOrdenes()).thenReturn(List.of(respuesta(10L, EstadoOrden.PENDIENTE)));

        mockMvc.perform(get("/ordenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pedidoId").value(25))
                .andExpect(jsonPath("$[0].numeroPedido").value("ORD-0025"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].items[0].nombreProducto").value("Hamburguesa Clasica"));
    }

    @Test
    void consultaUnaOrdenExistente() throws Exception {
        when(ordenCocinaService.obtenerOrden(10L))
                .thenReturn(respuesta(10L, EstadoOrden.PENDIENTE));

        mockMvc.perform(get("/ordenes/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void devuelve404CuandoLaOrdenNoExiste() throws Exception {
        when(ordenCocinaService.obtenerOrden(99L))
                .thenThrow(new RecursoNoEncontradoException("Orden de cocina no encontrada con id 99"));

        mockMvc.perform(get("/ordenes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Orden de cocina no encontrada con id 99"));
    }

    @Test
    void cambiaEstadoDeLaOrden() throws Exception {
        when(ordenCocinaService.actualizarEstado(10L, EstadoOrden.PREPARANDO))
                .thenReturn(respuesta(10L, EstadoOrden.PREPARANDO));

        mockMvc.perform(patch("/ordenes/10/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PREPARANDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PREPARANDO"));
    }

    @Test
    void devuelve409CuandoLaTransicionNoEsPermitida() throws Exception {
        when(ordenCocinaService.actualizarEstado(10L, EstadoOrden.LISTO))
                .thenThrow(new TransicionEstadoInvalidaException(
                        "Transicion de estado no permitida: PENDIENTE -> LISTO"));

        mockMvc.perform(patch("/ordenes/10/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void devuelve400CuandoElEstadoNoExiste() throws Exception {
        mockMvc.perform(patch("/ordenes/10/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"VOLANDO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void devuelve400CuandoFaltaElEstado() throws Exception {
        mockMvc.perform(patch("/ordenes/10/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private OrdenCocinaResponse respuesta(Long id, EstadoOrden estado) {
        return new OrdenCocinaResponse(
                id,
                25L,
                "ORD-0025",
                estado,
                LocalDateTime.of(2026, 10, 27, 9, 10),
                estado == EstadoOrden.PENDIENTE ? null : LocalDateTime.of(2026, 10, 27, 9, 14),
                estado == EstadoOrden.LISTO ? LocalDateTime.of(2026, 10, 27, 9, 20) : null,
                List.of(new ItemOrdenResponse(1L, "Hamburguesa Clasica", 2)));
    }
}
