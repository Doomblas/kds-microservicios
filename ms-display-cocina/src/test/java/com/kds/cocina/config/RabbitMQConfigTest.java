package com.kds.cocina.config;

import com.kds.cocina.dto.PedidoCreadoEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    @Test
    void convierteElEventoPedidoCreadoComoJson() {
        JacksonJsonMessageConverter converter =
                (JacksonJsonMessageConverter) new RabbitMQConfig()
                        .jsonMessageConverter(JsonMapper.builder().build());

        PedidoCreadoEvent original = new PedidoCreadoEvent(
                "evt-1",
                "pedido.creado",
                25L,
                "ORD-0025",
                LocalDateTime.of(2026, 10, 27, 9, 10),
                List.of(new PedidoCreadoEvent.ItemPedidoCreado(
                        1L, "Hamburguesa Clasica", 2)));

        Message message = converter.toMessage(original, new MessageProperties());
        Object recuperado = converter.fromMessage(message);

        assertThat(recuperado).isInstanceOf(PedidoCreadoEvent.class);
        PedidoCreadoEvent evento = (PedidoCreadoEvent) recuperado;
        assertThat(evento.eventoId()).isEqualTo("evt-1");
        assertThat(evento.evento()).isEqualTo("pedido.creado");
        assertThat(evento.pedidoId()).isEqualTo(25L);
        assertThat(evento.fechaCreacion()).isEqualTo(original.fechaCreacion());
        assertThat(evento.items()).hasSize(1);
        assertThat(evento.items().getFirst().nombreProducto()).isEqualTo("Hamburguesa Clasica");
    }
}
