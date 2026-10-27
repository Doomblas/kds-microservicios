package com.kds.menu.service;

import com.kds.menu.dto.ProductoRequest;
import com.kds.menu.model.Categoria;
import com.kds.menu.model.Producto;
import com.kds.menu.repository.CategoriaRepository;
import com.kds.menu.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    public ProductoService(
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository) {

        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    public Optional<Producto> buscarPorId(Long id) {
        return productoRepository.findById(id);
    }

    public Producto crear(ProductoRequest request) {

        Categoria categoria = categoriaRepository
                .findById(request.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));

        Producto producto = new Producto();

        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecio(request.getPrecio());
        producto.setDisponible(request.getDisponible());
        producto.setCategoria(categoria);

        return productoRepository.save(producto);
    }

    public void eliminar(Long id) {
        productoRepository.deleteById(id);
    }
}