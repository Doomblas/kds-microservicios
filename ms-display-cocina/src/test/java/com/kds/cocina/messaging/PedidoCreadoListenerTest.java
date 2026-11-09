package com.kds.cocina.messaging;

import com.kds.cocina.dto.PedidoCreadoEvent;
import com.kds.cocina.service.OrdenCocinaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PedidoCreadoListenerTest {

    @Mock
    private OrdenCocinaService ordenCocinaService;

    @InjectMocks
    private PedidoCreadoListener listener;

    @Test
    void delegaElEventoAlServicioDeOrdenes() {
        PedidoCreadoEvent evento = new PedidoCreadoEvent(
                "evt-1",
                "pedido.creado",
                25L,
                "ORD-0025",
                LocalDateTime.of(2026, 10, 27, 9, 10),
                List.of(new PedidoCreadoEvent.ItemPedidoCreado(
                        1L, "Hamburguesa Clasica", 2)));

        listener.consumirPedidoCreado(evento);

        verify(ordenCocinaService).registrarPedido(evento);
    }
}
