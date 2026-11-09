package com.kds.cocina.messaging;

import com.kds.cocina.config.RabbitMQConfig;
import com.kds.cocina.dto.NotificacionCocina;
import com.kds.cocina.dto.OrdenCocinaResponse;
import com.kds.cocina.dto.PedidoEstadoActualizadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class CocinaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CocinaEventPublisher.class);

    public static final String TIPO_PEDIDO_CREADO = "PEDIDO_CREADO";
    public static final String TOPIC_COCINA = "/topic/cocina";

    private final RabbitTemplate rabbitTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public CocinaEventPublisher(
            RabbitTemplate rabbitTemplate,
            SimpMessagingTemplate messagingTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    public void publicarEstadoActualizado(OrdenCocinaResponse orden, LocalDateTime fechaActualizacion) {
        PedidoEstadoActualizadoEvent evento = new PedidoEstadoActualizadoEvent(
                UUID.randomUUID().toString(),
                RabbitMQConfig.RK_PEDIDO_ESTADO_ACTUALIZADO,
                orden.pedidoId(),
                orden.numeroPedido(),
                orden.estado(),
                fechaActualizacion);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.RK_PEDIDO_ESTADO_ACTUALIZADO,
                    evento);
        } catch (AmqpException ex) {
            // En v1 no hay outbox ni reintentos propios. La orden ya debe quedar
            // persistida en cocina_db aunque RabbitMQ no este disponible.
            log.error("No se pudo publicar pedido.estado.actualizado para pedidoId={}",
                    orden.pedidoId(), ex);
        }
    }

    public void notificarPedidoCreado(OrdenCocinaResponse orden) {
        NotificacionCocina notificacion = new NotificacionCocina(
                TIPO_PEDIDO_CREADO,
                orden.pedidoId(),
                orden.numeroPedido(),
                orden.estado(),
                orden.items().stream()
                        .map(item -> new NotificacionCocina.ItemNotificacion(
                                item.nombreProducto(), item.cantidad()))
                        .toList());

        try {
            messagingTemplate.convertAndSend(TOPIC_COCINA, notificacion);
        } catch (RuntimeException ex) {
            // La notificacion es solo un aviso; la orden ya fue persistida.
            log.error("No se pudo notificar PEDIDO_CREADO para pedidoId={}",
                    orden.pedidoId(), ex);
        }
    }
}
