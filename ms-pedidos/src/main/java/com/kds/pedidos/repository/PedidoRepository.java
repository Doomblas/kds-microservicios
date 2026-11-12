package com.kds.pedidos.repository;

import com.kds.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "detalles")
    List<Pedido> findAllByOrderByFechaCreacionDesc();

    @EntityGraph(attributePaths = "detalles")
    Optional<Pedido> findById(Long id);

    Optional<Pedido> findByNumeroPedido(String numeroPedido);
}
