package com.kds.cocina.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "kds.exchange";
    public static final String COCINA_QUEUE = "cocina.pedidos.queue";
    public static final String RK_PEDIDO_CREADO = "pedido.creado";
    public static final String RK_PEDIDO_ESTADO_ACTUALIZADO = "pedido.estado.actualizado";

    @Bean
    public TopicExchange kdsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue cocinaPedidosQueue() {
        return QueueBuilder.durable(COCINA_QUEUE).build();
    }

    @Bean
    public Binding cocinaPedidosBinding(Queue cocinaPedidosQueue, TopicExchange kdsExchange) {
        return BindingBuilder.bind(cocinaPedidosQueue)
                .to(kdsExchange)
                .with(RK_PEDIDO_CREADO);
    }

    /**
     * Jackson 3 es el motor JSON de Spring Boot 4. El conversor garantiza que
     * los eventos viajen como JSON y no como objetos Java serializados.
     */
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.getJavaTypeMapper().addTrustedPackages("com.kds.cocina.dto");
        return converter;
    }
}
