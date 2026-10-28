package com.kds.cocina.messaging;

import com.kds.cocina.config.RabbitMQConfig;
import com.kds.cocina.dto.PedidoCreadoEvent;
import com.kds.cocina.service.OrdenCocinaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoCreadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCreadoListener.class);

    private final OrdenCocinaService ordenCocinaService;

    public PedidoCreadoListener(OrdenCocinaService ordenCocinaService) {
        this.ordenCocinaService = ordenCocinaService;
    }

    @RabbitListener(queues = RabbitMQConfig.COCINA_QUEUE)
    public void consumirPedidoCreado(PedidoCreadoEvent evento) {
        log.info("Evento pedido.creado recibido: eventoId={}, pedidoId={}, numeroPedido={}",
                evento.eventoId(), evento.pedidoId(), evento.numeroPedido());
        ordenCocinaService.registrarPedido(evento);
    }
}
