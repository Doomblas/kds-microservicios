package com.kds.pedidos.messaging;

import com.kds.pedidos.config.RabbitMQConfig;
import com.kds.pedidos.dto.PedidoEstadoActualizadoEvent;
import com.kds.pedidos.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoEstadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoEstadoListener.class);
    private final PedidoService pedidoService;

    public PedidoEstadoListener(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @RabbitListener(queues = RabbitMQConfig.PEDIDOS_ESTADO_QUEUE)
    public void consumirEstadoActualizado(PedidoEstadoActualizadoEvent evento) {
        log.info("Mensaje asíncrono 'pedido.estado.actualizado' recibido: eventoId={}, pedidoId={}, nuevoEstado={}",
                evento.eventoId(), evento.pedidoId(), evento.estado());
        pedidoService.sincronizarEstadoCocina(evento);
    }
}
