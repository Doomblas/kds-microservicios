package com.kds.pedidos.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "kds.exchange";
    public static final String PEDIDOS_COCINA_QUEUE = "pedidos.cocina.queue";
    public static final String RK_PEDIDO_CREADO = "pedido.creado";
    public static final String RK_PEDIDO_ESTADO_ACTUALIZADO = "pedido.estado.actualizado";

    @Bean
    public TopicExchange kdsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue pedidosCocinaQueue() {
        return QueueBuilder.durable(PEDIDOS_COCINA_QUEUE).build();
    }

    @Bean
    public Binding pedidosCocinaBinding(Queue pedidosCocinaQueue, TopicExchange kdsExchange) {
        return BindingBuilder.bind(pedidosCocinaQueue)
                .to(kdsExchange)
                .with(RK_PEDIDO_ESTADO_ACTUALIZADO);
    }

    /**
     * Jackson 3 es el motor JSON de Spring Boot 4. El conversor garantiza que
     * los eventos viajen como JSON y no como objetos Java serializados.
     * TypePrecedence.INFERRED evita fallos cuando Cocina publica con
     * __TypeId__ de su propio paquete (com.kds.cocina.dto).
     */
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.getJavaTypeMapper().addTrustedPackages("com.kds.pedidos.dto");
        converter.setTypePrecedence(TypePrecedence.INFERRED);
        return converter;
    }
}
