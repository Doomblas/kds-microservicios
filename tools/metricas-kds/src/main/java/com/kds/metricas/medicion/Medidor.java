package com.kds.metricas.medicion;

import com.kds.metricas.config.Configuracion;
import com.kds.metricas.http.ClienteKds;
import com.kds.metricas.http.Dtos.Orden;
import com.kds.metricas.http.Dtos.Pedido;
import com.kds.metricas.http.Dtos.PedidoEstado;
import com.kds.metricas.http.Dtos.Producto;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.Muestra;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Motor de medicion de la propagacion de eventos.
 *
 * <p>Definiciones de latencia aplicadas:</p>
 * <ul>
 *   <li><b>Pedido nuevo</b>: t0 se toma inmediatamente antes de enviar el POST
 *       que crea el pedido; t1 es el instante en que el API Gateway devuelve la
 *       orden equivalente en ms-display-cocina. Mide Pedidos -&gt; RabbitMQ -&gt;
 *       Cocina -&gt; persistencia.</li>
 *   <li><b>Cambio a PREPARANDO / LISTO</b>: t0 se toma inmediatamente antes de
 *       enviar el PATCH contra la orden de cocina; t1 es el instante en que
 *       ms-pedidos devuelve el nuevo estado. Mide Cocina -&gt; RabbitMQ -&gt;
 *       Pedidos.</li>
 * </ul>
 *
 * <p>El tiempo se mide con {@link System#nanoTime()} (reloj monotono) y se
 * convierte a milisegundos con tres decimales. La propagacion se detecta
 * mediante sondeo corto y controlado que se detiene en cuanto se observa el
 * estado esperado.</p>
 */
public class Medidor {

    private static final long NS_POR_MS = 1_000_000L;

    private final ClienteKds cliente;
    private final Configuracion cfg;
    private final List<Muestra> muestras = new ArrayList<>();
    private final long productoId;

    public Medidor(ClienteKds cliente, Configuracion cfg, long productoId) {
        this.cliente = cliente;
        this.cfg = cfg;
        this.productoId = productoId;
    }

    /** Ejecuta todas las iteraciones y devuelve las muestras registradas. */
    public List<Muestra> ejecutar() {
        if (cfg.calentamiento() > 0) {
            ejecutarCalentamiento();
        }
        for (int i = 1; i <= cfg.numPruebas(); i++) {
            iteracion(i);
        }
        return List.copyOf(muestras);
    }

    /**
     * Iteraciones previas de calentamiento (no contabilizadas). Sirven para
     * que la JVM, el cliente HTTP y las conexiones esten calientes antes de
     * tomar las muestras oficiales.
     */
    public void ejecutarCalentamiento() {
        for (int i = 0; i < cfg.calentamiento(); i++) {
            iteracionSinRegistrar();
        }
    }

    /** Ejecuta una iteracion completa: pedido nuevo -> PREPARANDO -> LISTO. */
    private void iteracion(int numero) {
        Muestra nuevo = medirPedidoNuevo(numero);
        muestras.add(nuevo);

        if (!nuevo.exito()) {
            String motivo = "OMITIDO: no se pudo crear el pedido de la iteracion " + numero
                    + " (" + (nuevo.detalle() == null ? "motivo desconocido" : nuevo.detalle()) + ")";
            muestras.add(Muestra.fallida(Escenario.CAMBIO_PREPARANDO, numero, motivo, Instant.now()));
            muestras.add(Muestra.fallida(Escenario.CAMBIO_LISTO, numero, motivo, Instant.now()));
            return;
        }

        Muestra preparando = medirCambioEstado(numero, nuevo, "PREPARANDO", Escenario.CAMBIO_PREPARANDO);
        muestras.add(preparando);

        Muestra listo = medirCambioEstado(numero, nuevo, "LISTO", Escenario.CAMBIO_LISTO);
        muestras.add(listo);
    }

    /** Igual que {@link #iteracion(int)} pero sin registrar muestras (calentamiento). */
    private void iteracionSinRegistrar() {
        Muestra nuevo = medirPedidoNuevo(0);
        if (!nuevo.exito()) {
            return;
        }
        medirCambioEstado(0, nuevo, "PREPARANDO", Escenario.CAMBIO_PREPARANDO);
        medirCambioEstado(0, nuevo, "LISTO", Escenario.CAMBIO_LISTO);
    }

    // ------------------------------------------------------------------
    // Escenario 1: pedido nuevo
    // ------------------------------------------------------------------

    private Muestra medirPedidoNuevo(int numero) {
        Pedido pedido;
        try {
            // t0: instante inmediatamente anterior al envio del POST.
            long t0 = System.nanoTime();
            pedido = cliente.crearPedido(productoId, cfg.cantidad());
            long t0Post = System.nanoTime();

            long limite = t0 + TimeUnit.MILLISECONDS.toNanos(cfg.timeoutMs());
            int intentos = 0;

            while (true) {
                intentos++;
                ClienteKds.ConTiempo<List<Orden>> respuesta = cliente.listarOrdenesConTiempo();
                for (Orden orden : respuesta.valor()) {
                    if (orden.pedidoId() != null && orden.pedidoId().equals(pedido.id())) {
                        long t1 = respuesta.nanoRecepcion();
                        double latencia = (t1 - t0) / (double) NS_POR_MS;
                        return Muestra.exitosa(Escenario.PEDIDO_NUEVO, numero, pedido.id(),
                                pedido.numeroPedido(), orden.id(), latencia, Instant.now());
                    }
                }

                if (System.nanoTime() > limite) {
                    double limiteMs = cfg.timeoutMs();
                    return Muestra.fallida(Escenario.PEDIDO_NUEVO, numero, pedido.id(), pedido.numeroPedido(), null,
                            "TIMEOUT: la orden no aparecio en ms-display-cocina tras " + limiteMs
                                    + " ms (" + intentos + " sondeos). POST /pedidos completo en "
                                    + String.format("%.2f", (t0Post - t0) / (double) NS_POR_MS) + " ms.",
                            Instant.now());
                }
                dormir(cfg.pollIntervalMs());
            }
        } catch (Exception e) {
            return Muestra.fallida(Escenario.PEDIDO_NUEVO, numero,
                    "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Instant.now());
        }
    }

    // ------------------------------------------------------------------
    // Escenarios 2 y 3: cambios de estado
    // ------------------------------------------------------------------

    private Muestra medirCambioEstado(int numero, Muestra pedido, String estado,
                                      Escenario escenario) {
        Long pedidoId = pedido.pedidoId();
        Long ordenId = pedido.ordenId();
        try {
            // t0: instante inmediatamente anterior al envio del PATCH.
            long t0 = System.nanoTime();
            cliente.cambiarEstadoOrden(ordenId, estado);
            long t0Respuesta = System.nanoTime();

            long limite = t0 + TimeUnit.MILLISECONDS.toNanos(cfg.timeoutMs());
            int intentos = 0;

            while (true) {
                intentos++;
                ClienteKds.ConTiempo<PedidoEstado> respuesta = cliente.obtenerEstadoPedidoConTiempo(pedidoId);
                PedidoEstado actual = respuesta.valor();
                if (actual != null && estado.equals(actual.estado())) {
                    long t1 = respuesta.nanoRecepcion();
                    double latencia = (t1 - t0) / (double) NS_POR_MS;
                    return Muestra.exitosa(escenario, numero, pedidoId, actual.numeroPedido(), ordenId,
                            latencia, Instant.now());
                }

                if (System.nanoTime() > limite) {
                    return Muestra.fallida(escenario, numero, pedidoId, pedido.numeroPedido(), ordenId,
                            "TIMEOUT: ms-pedidos no reflejo el estado " + estado + " tras " + cfg.timeoutMs()
                                    + " ms (" + intentos + " sondeos). PATCH /ordenes/" + ordenId
                                    + "/estado completo en "
                                    + String.format("%.2f", (t0Respuesta - t0) / (double) NS_POR_MS) + " ms.",
                            Instant.now());
                }
                dormir(cfg.pollIntervalMs());
            }
        } catch (Exception e) {
            return Muestra.fallida(escenario, numero, pedidoId, pedido.numeroPedido(), ordenId,
                    "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Instant.now());
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Selecciona el producto de prueba: el indicado por configuracion o el primero disponible. */
    public static long seleccionarProducto(List<Producto> productos, Long productoConfigurado) {
        if (productoConfigurado != null) {
            for (Producto p : productos) {
                if (productoConfigurado.equals(p.id())) {
                    if (!Boolean.TRUE.equals(p.disponible())) {
                        throw new IllegalStateException("El producto configurado (id=" + productoConfigurado
                                + ") no esta disponible en ms-menu.");
                    }
                    return p.id();
                }
            }
            throw new IllegalStateException("El producto configurado (id=" + productoConfigurado
                    + ") no existe en el catalogo de ms-menu.");
        }
        for (Producto p : productos) {
            if (Boolean.TRUE.equals(p.disponible())) {
                return p.id();
            }
        }
        throw new IllegalStateException("No hay ningun producto disponible en el catalogo de ms-menu.");
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Medicion interrumpida", e);
        }
    }
}
