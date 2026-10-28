package com.kds.cocina.service;

import com.kds.cocina.dto.OrdenCocinaResponse;
import com.kds.cocina.dto.PedidoCreadoEvent;
import com.kds.cocina.exception.RecursoNoEncontradoException;
import com.kds.cocina.exception.TransicionEstadoInvalidaException;
import com.kds.cocina.messaging.CocinaEventPublisher;
import com.kds.cocina.model.EstadoOrden;
import com.kds.cocina.model.ItemCocina;
import com.kds.cocina.model.OrdenCocina;
import com.kds.cocina.repository.OrdenCocinaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenCocinaServiceTest {

    @Mock
    private OrdenCocinaRepository ordenCocinaRepository;

    @Mock
    private CocinaEventPublisher eventPublisher;

    @InjectMocks
    private OrdenCocinaService service;

    @Test
    void registraPedidoCreadoYPersisteLaCopiaLocal() {
        PedidoCreadoEvent evento = pedidoCreado(25L, "ORD-0025");
        when(ordenCocinaRepository.findByPedidoId(25L)).thenReturn(Optional.empty());
        when(ordenCocinaRepository.save(any(OrdenCocina.class)))
                .thenAnswer(invocation -> {
                    OrdenCocina orden = invocation.getArgument(0);
                    orden.setId(10L);
                    return orden;
                });

        OrdenCocinaResponse respuesta = service.registrarPedido(evento);

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.pedidoId()).isEqualTo(25L);
        assertThat(respuesta.numeroPedido()).isEqualTo("ORD-0025");
        assertThat(respuesta.estado()).isEqualTo(EstadoOrden.PENDIENTE);
        assertThat(respuesta.items()).hasSize(2);
        assertThat(respuesta.items().getFirst().nombreProducto()).isEqualTo("Hamburguesa Clasica");
        assertThat(respuesta.items().getFirst().cantidad()).isEqualTo(2);

        ArgumentCaptor<OrdenCocina> captor = ArgumentCaptor.forClass(OrdenCocina.class);
        verify(ordenCocinaRepository).save(captor.capture());
        assertThat(captor.getValue().getFechaRecepcion()).isEqualTo(evento.fechaCreacion());
        assertThat(captor.getValue().getItems()).hasSize(2);

        verify(eventPublisher).notificarPedidoCreado(respuesta);
        verify(eventPublisher, never()).publicarEstadoActualizado(any(), any());
    }

    @Test
    void ignoraEventoDuplicadoSinCrearOtraOrdenNiNotificar() {
        OrdenCocina existente = orden(10L, 25L, "ORD-0025", EstadoOrden.PENDIENTE);
        when(ordenCocinaRepository.findByPedidoId(25L)).thenReturn(Optional.of(existente));

        OrdenCocinaResponse respuesta = service.registrarPedido(pedidoCreado(25L, "ORD-0025"));

        assertThat(respuesta.id()).isEqualTo(10L);
        verify(ordenCocinaRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rechazaEventoSinItems() {
        PedidoCreadoEvent evento = new PedidoCreadoEvent(
                "evt-1", "pedido.creado", 25L, "ORD-0025", LocalDateTime.now(), List.of());

        assertThatThrownBy(() -> service.registrarPedido(evento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos un item");

        verify(ordenCocinaRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cambiaDePendienteAPreparandoYPublicaEvento() {
        OrdenCocina orden = orden(10L, 25L, "ORD-0025", EstadoOrden.PENDIENTE);
        when(ordenCocinaRepository.findById(10L)).thenReturn(Optional.of(orden));
        when(ordenCocinaRepository.save(orden)).thenReturn(orden);

        OrdenCocinaResponse respuesta = service.actualizarEstado(10L, EstadoOrden.PREPARANDO);

        assertThat(respuesta.estado()).isEqualTo(EstadoOrden.PREPARANDO);
        assertThat(respuesta.fechaInicio()).isNotNull();
        assertThat(respuesta.fechaFinalizacion()).isNull();
        verify(eventPublisher).publicarEstadoActualizado(eq(respuesta), any(LocalDateTime.class));
    }

    @Test
    void cambiaDePreparandoAListoYRegistraFechaFinalizacion() {
        OrdenCocina orden = orden(10L, 25L, "ORD-0025", EstadoOrden.PREPARANDO);
        LocalDateTime inicio = LocalDateTime.now().minusMinutes(5);
        orden.setFechaInicio(inicio);
        when(ordenCocinaRepository.findById(10L)).thenReturn(Optional.of(orden));
        when(ordenCocinaRepository.save(orden)).thenReturn(orden);

        OrdenCocinaResponse respuesta = service.actualizarEstado(10L, EstadoOrden.LISTO);

        assertThat(respuesta.estado()).isEqualTo(EstadoOrden.LISTO);
        assertThat(respuesta.fechaInicio()).isEqualTo(inicio);
        assertThat(respuesta.fechaFinalizacion()).isNotNull();
        verify(eventPublisher).publicarEstadoActualizado(eq(respuesta), any(LocalDateTime.class));
    }

    @Test
    void rechazaSaltoDePendienteAListo() {
        OrdenCocina orden = orden(10L, 25L, "ORD-0025", EstadoOrden.PENDIENTE);
        when(ordenCocinaRepository.findById(10L)).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> service.actualizarEstado(10L, EstadoOrden.LISTO))
                .isInstanceOf(TransicionEstadoInvalidaException.class)
                .hasMessageContaining("PENDIENTE -> LISTO");

        verify(ordenCocinaRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rechazaAsignarPendienteManualmente() {
        OrdenCocina orden = orden(10L, 25L, "ORD-0025", EstadoOrden.PREPARANDO);
        when(ordenCocinaRepository.findById(10L)).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> service.actualizarEstado(10L, EstadoOrden.PENDIENTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDIENTE");

        verify(ordenCocinaRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void repetirElMismoEstadoEsIdempotenteYNoRepublica() {
        OrdenCocina orden = orden(10L, 25L, "ORD-0025", EstadoOrden.PREPARANDO);
        when(ordenCocinaRepository.findById(10L)).thenReturn(Optional.of(orden));

        OrdenCocinaResponse respuesta = service.actualizarEstado(10L, EstadoOrden.PREPARANDO);

        assertThat(respuesta.estado()).isEqualTo(EstadoOrden.PREPARANDO);
        verify(ordenCocinaRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void ordenInexistenteLanzaRecursoNoEncontrado() {
        when(ordenCocinaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerOrden(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }

    private PedidoCreadoEvent pedidoCreado(Long pedidoId, String numeroPedido) {
        return new PedidoCreadoEvent(
                "evt-" + pedidoId,
                "pedido.creado",
                pedidoId,
                numeroPedido,
                LocalDateTime.of(2026, 10, 27, 9, 10),
                List.of(
                        new PedidoCreadoEvent.ItemPedidoCreado(
                                1L, "Hamburguesa Clasica", 2),
                        new PedidoCreadoEvent.ItemPedidoCreado(
                                5L, "Papas", 1)));
    }

    private OrdenCocina orden(
            Long id, Long pedidoId, String numeroPedido, EstadoOrden estado) {
        OrdenCocina orden = new OrdenCocina();
        orden.setId(id);
        orden.setPedidoId(pedidoId);
        orden.setNumeroPedido(numeroPedido);
        orden.setEstado(estado);
        orden.setFechaRecepcion(LocalDateTime.of(2026, 10, 27, 9, 10));

        ItemCocina item = new ItemCocina();
        item.setId(1L);
        item.setProductoId(1L);
        item.setNombreProducto("Hamburguesa Clasica");
        item.setCantidad(2);
        orden.agregarItem(item);

        return orden;
    }
}
