package com.kds.pedidos.messaging;

import com.kds.pedidos.client.MenuClient;
import com.kds.pedidos.config.RabbitMQConfig;
import com.kds.pedidos.dto.CrearPedidoRequest;
import com.kds.pedidos.dto.PedidoEstadoActualizadoEvent;
import com.kds.pedidos.dto.ProductoMenuResponse;
import com.kds.pedidos.model.EstadoPedido;
import com.kds.pedidos.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class PruebaConexionCocinaTest {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.0-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("kds.menu.base-url", () -> "http://localhost:8080");
    }

    @MockitoBean
    private MenuClient menuClient;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @BeforeEach
    void stubMenu() {
        when(menuClient.obtenerProducto(anyLong())).thenAnswer(invocation -> {
            Long productoId = invocation.getArgument(0);
            if (productoId == 1L) {
                return new ProductoMenuResponse(1L, "Hamburguesa Clasica", new BigDecimal("4.50"), true);
            }
            if (productoId == 5L) {
                return new ProductoMenuResponse(5L, "Papas", new BigDecimal("2.25"), true);
            }
            return new ProductoMenuResponse(productoId, "Producto Genérico KDS", new BigDecimal("3.00"), true);
        });
    }

    @Test
    void verificarFlujoCompletoConCocina() throws InterruptedException {
        System.out.println("\n[CONFIG ] Declarando de forma dinámica la cola de ms-display-cocina...");
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());

        Queue cocinaQueue = new Queue("cocina.pedidos.queue", true);
        admin.declareQueue(cocinaQueue);

        TopicExchange exchange = new TopicExchange(RabbitMQConfig.EXCHANGE);
        admin.declareBinding(BindingBuilder.bind(cocinaQueue).to(exchange).with(RabbitMQConfig.RK_PEDIDO_CREADO));

        System.out.println("\n[PASO 1] Ejecutando POST /pedidos (Simulado)...");
        var item = new CrearPedidoRequest.ItemPedidoRequest(1L, 2);
        var request = new CrearPedidoRequest(List.of(item));

        var pedidoRegistrado = pedidoService.registrarPedido(request);

        System.out.println("[PASO 2] Extrayendo mensaje de la cola 'cocina.pedidos.queue' (Simulando ms-display-cocina)...");
        Thread.sleep(500);

        Object mensajeEnColaCocina = rabbitTemplate.receiveAndConvert("cocina.pedidos.queue");

        assertThat(mensajeEnColaCocina).isNotNull();
        System.out.println(">>> ¡ÉXITO! ms-display-cocina recibiría este payload correctamente.");

        System.out.println("\n[PASO 3] Simulando que ms-display-cocina publica 'pedido.estado.actualizado'...");
        PedidoEstadoActualizadoEvent eventoCocina = new PedidoEstadoActualizadoEvent(
                UUID.randomUUID().toString(),
                RabbitMQConfig.RK_PEDIDO_ESTADO_ACTUALIZADO,
                pedidoRegistrado.id(),
                pedidoRegistrado.numeroPedido(),
                EstadoPedido.PREPARANDO,
                LocalDateTime.now()
        );

        amqpTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PEDIDO_ESTADO_ACTUALIZADO, eventoCocina);

        Thread.sleep(500);

        System.out.println("[PASO 4] Verificando si ms-pedidos procesó el cambio y actualizó el estado...");
        var pedidoActualizado = pedidoService.obtenerPedido(pedidoRegistrado.id());

        assertThat(pedidoActualizado.estado()).isEqualTo(EstadoPedido.PREPARANDO);
        System.out.println(">>> ¡ÉXITO! Tu servicio reaccionó al mensaje de cocina y actualizó la base de datos.");
    }
}
