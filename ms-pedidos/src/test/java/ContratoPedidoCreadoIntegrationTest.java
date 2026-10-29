

import com.kds.pedidos.dto.PedidoCreadoEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContratoPedidoCreadoIntegrationTest {

    @Test
    void verificaQueElContratoJsonCoincidaConMsDisplayCocina() {
        // 1. Instanciar el conversor oficial de Jackson de la arquitectura
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(JsonMapper.builder().build());
        converter.getJavaTypeMapper().addTrustedPackages("com.kds.pedidos.dto", "com.kds.cocina.dto");

        // 2. Construir el evento con los datos que genera ms-pedidos
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 27, 9, 10, 0);
        PedidoCreadoEvent eventoOriginal = new PedidoCreadoEvent(
                "550e8400-e29b-41d4-a716-446655440000",
                "pedido.creado",
                25L,
                "ORD-0025",
                fecha,
                List.of(new PedidoCreadoEvent.ItemPedidoCreado(1L, "Hamburguesa Clasica", 2))
        );

        // 3. Simular el viaje por los cables de RabbitMQ (Conversión a bytes binarios JSON)
        Message message = converter.toMessage(eventoOriginal, new MessageProperties());
        
        // 4. Leer el JSON en crudo para asegurar la estructura exacta acordada
        String jsonPayload = new String(message.getBody());
        
        // REGLA DEL CONTRATO: Campos requeridos por ms-display-cocina
        assertThat(jsonPayload).contains("\"eventoId\":\"550e8400-e29b-41d4-a716-446655440000\"");
        assertThat(jsonPayload).contains("\"evento\":\"pedido.creado\"");
        assertThat(jsonPayload).contains("\"pedidoId\":25");
        assertThat(jsonPayload).contains("\"numeroPedido\":\"ORD-0025\"");
        assertThat(jsonPayload).contains("\"nombreProducto\":\"Hamburguesa Clasica\"");
        assertThat(jsonPayload).contains("\"cantidad\":2");
        
        System.out.println("====== CONTRATO VERIFICADO COMPATIBLE CON COCINA ======");
        System.out.println(jsonPayload);
    }
}
