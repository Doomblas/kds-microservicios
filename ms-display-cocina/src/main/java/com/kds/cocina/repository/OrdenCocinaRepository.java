package com.kds.cocina.repository;

import com.kds.cocina.model.OrdenCocina;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdenCocinaRepository extends JpaRepository<OrdenCocina, Long> {

    @EntityGraph(attributePaths = "items")
    List<OrdenCocina> findAllByOrderByFechaRecepcionAsc();

    @EntityGraph(attributePaths = "items")
    Optional<OrdenCocina> findById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<OrdenCocina> findByPedidoId(Long pedidoId);

    boolean existsByPedidoId(Long pedidoId);
}
