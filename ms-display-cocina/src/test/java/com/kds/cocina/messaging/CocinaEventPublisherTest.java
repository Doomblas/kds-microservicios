package com.kds.cocina.messaging;

import com.kds.cocina.config.RabbitMQConfig;
import com.kds.cocina.dto.ItemOrdenResponse;
import com.kds.cocina.dto.NotificacionCocina;
import com.kds.cocina.dto.OrdenCocinaResponse;
import com.kds.cocina.dto.PedidoEstadoActualizadoEvent;
import com.kds.cocina.model.EstadoOrden;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CocinaEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private CocinaEventPublisher publisher;

    @Test
    void publicaEventoDeEstadoConElContratoV1() {
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 27, 9, 14, 20);

        publisher.publicarEstadoActualizado(orden(EstadoOrden.PREPARANDO), fecha);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.RK_PEDIDO_ESTADO_ACTUALIZADO),
                captor.capture());

        assertThat(captor.getValue()).isInstanceOf(PedidoEstadoActualizadoEvent.class);
        PedidoEstadoActualizadoEvent evento = (PedidoEstadoActualizadoEvent) captor.getValue();
        assertThat(evento.eventoId()).isNotBlank();
        assertThat(evento.evento()).isEqualTo("pedido.estado.actualizado");
        assertThat(evento.pedidoId()).isEqualTo(25L);
        assertThat(evento.numeroPedido()).isEqualTo("ORD-0025");
        assertThat(evento.estado()).isEqualTo(EstadoOrden.PREPARANDO);
        assertThat(evento.fechaActualizacion()).isEqualTo(fecha);
    }

    @Test
    void notificaPedidoCreadoEnElTopicDeCocina() {
        publisher.notificarPedidoCreado(orden(EstadoOrden.PENDIENTE));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/cocina"), captor.capture());

        assertThat(captor.getValue()).isInstanceOf(NotificacionCocina.class);
        NotificacionCocina notificacion = (NotificacionCocina) captor.getValue();
        assertThat(notificacion.tipo()).isEqualTo("PEDIDO_CREADO");
        assertThat(notificacion.pedidoId()).isEqualTo(25L);
        assertThat(notificacion.numeroPedido()).isEqualTo("ORD-0025");
        assertThat(notificacion.estado()).isEqualTo(EstadoOrden.PENDIENTE);
        assertThat(notificacion.items()).hasSize(1);
        assertThat(notificacion.items().getFirst().nombreProducto())
                .isEqualTo("Hamburguesa Clasica");
        assertThat(notificacion.items().getFirst().cantidad()).isEqualTo(2);
    }

    @Test
    void noPropagaFalloDeRabbitAlFlujoRest() {
        doThrow(new AmqpException("broker no disponible"))
                .when(rabbitTemplate).convertAndSend(
                        eq(RabbitMQConfig.EXCHANGE),
                        eq(RabbitMQConfig.RK_PEDIDO_ESTADO_ACTUALIZADO),
                        any(Object.class));

        assertThatCode(() -> publisher.publicarEstadoActualizado(
                orden(EstadoOrden.PREPARANDO), LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    private OrdenCocinaResponse orden(EstadoOrden estado) {
        return new OrdenCocinaResponse(
                10L,
                25L,
                "ORD-0025",
                estado,
                LocalDateTime.of(2026, 10, 27, 9, 10),
                null,
                null,
                List.of(new ItemOrdenResponse(1L, "Hamburguesa Clasica", 2)));
    }
}
