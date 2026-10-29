package com.kds.pedidos.messaging;

import com.kds.pedidos.config.RabbitMQConfig;
import com.kds.pedidos.dto.PedidoCreadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidosEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidosEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public PedidosEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarPedidoCreado(PedidoCreadoEvent evento) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.RK_PEDIDO_CREADO,
                    evento);
            log.info("Evento pedido.creado enviado de forma asíncrona para pedidoId={}", evento.pedidoId());
        } catch (AmqpException ex) {
            log.error("Error crítico: No se pudo publicar el evento pedido.creado en RabbitMQ", ex);
        }
    }
}
