package com.kds.pedidos.messaging;

import com.kds.pedidos.config.RabbitMQConfig;
import com.kds.pedidos.dto.CrearPedidoRequest;
import com.kds.pedidos.dto.PedidoEstadoActualizadoEvent;
import com.kds.pedidos.model.EstadoPedido;
import com.kds.pedidos.service.PedidoService;
// ¡ASEGÚRATE DE AGREGAR ESTOS TRES IMPORTS NUEVOS!
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Test
    void verificarFlujoCompletoConCocina() throws InterruptedException {
        // --- NUEVO COMPORTAMIENTO: DECLARAR LA COLA DE COCINA MANUALMENTE PARA EL TEST ---
        System.out.println("\n[CONFIG ] Declarando de forma dinámica la cola de ms-display-cocina...");
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
        
        // Creamos la estructura exacta que tu compañero usa en ms-display-cocina
        Queue cocinaQueue = new Queue("cocina.pedidos.queue", true);
        admin.declareQueue(cocinaQueue);
        
        // Recuperamos el exchange del proyecto y enlazamos la cola bajo la llave acordada
        var exchange = new org.springframework.amqp.core.TopicExchange(RabbitMQConfig.EXCHANGE);
        admin.declareBinding(BindingBuilder.bind(cocinaQueue).to(exchange).with(RabbitMQConfig.RK_PEDIDO_CREADO));
        // ---------------------------------------------------------------------------------

        // ---- FLUJO 1: ms-pedidos PUBLICANDO HACIA COCINA ----
        System.out.println("\n[PASO 1] Ejecutando POST /pedidos (Simulado)...");
        var item = new CrearPedidoRequest.ItemPedidoRequest(1L, 2);
        var request = new CrearPedidoRequest(List.of(item));
        
        var pedidoRegistrado = pedidoService.registrarPedido(request);
        
        System.out.println("[PASO 2] Extrayendo mensaje de la cola 'cocina.pedidos.queue' (Simulando ms-display-cocina)...");
        Thread.sleep(500); 
        
        // Ahora que la cola existe de verdad en el contenedor, esto ya no lanzará IOException
        Object mensajeEnColaCocina = rabbitTemplate.receiveAndConvert("cocina.pedidos.queue");
        
        assertThat(mensajeEnColaCocina).isNotNull();
        System.out.println(">>> ¡ÉXITO! ms-display-cocina recibiría este payload correctamente.");

        // ---- FLUJO 2: ms-display-cocina RESPONDIENDO A PEDIDOS ----
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
