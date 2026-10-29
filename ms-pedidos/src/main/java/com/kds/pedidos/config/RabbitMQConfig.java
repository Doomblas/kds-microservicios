package com.kds.pedidos.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "kds.exchange";
    public static final String PEDIDOS_ESTADO_QUEUE = "pedidos.estado.queue";
    public static final String RK_PEDIDO_CREADO = "pedido.creado";
    public static final String RK_PEDIDO_ESTADO_ACTUALIZADO = "pedido.estado.actualizado";

    @Bean
    public TopicExchange kdsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue pedidosEstadoQueue() {
        return QueueBuilder.durable(PEDIDOS_ESTADO_QUEUE).build();
    }

    @Bean
    public Binding pedidosEstadoBinding(Queue pedidosEstadoQueue, TopicExchange kdsExchange) {
        return BindingBuilder.bind(pedidosEstadoQueue)
                .to(kdsExchange)
                .with(RK_PEDIDO_ESTADO_ACTUALIZADO);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.getJavaTypeMapper().addTrustedPackages("com.kds.pedidos.dto");
        return converter;
    }
}
