package com.kds.menu.service;

import com.kds.menu.dto.CategoriaRequest;
import com.kds.menu.exception.RecursoDuplicadoException;
import com.kds.menu.model.Categoria;
import com.kds.menu.repository.CategoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }

    public Optional<Categoria> buscarPorId(Long id) {
        return categoriaRepository.findById(id);
    }

    public Categoria crear(CategoriaRequest request) {

        if (categoriaRepository.existsByNombreIgnoreCase(request.getNombre())) {
            throw new RecursoDuplicadoException(
                    "Ya existe una categoria con el nombre: " + request.getNombre());
        }

        Categoria categoria = new Categoria();

        categoria.setNombre(request.getNombre());
        categoria.setActivo(request.getActivo());

        return categoriaRepository.save(categoria);
    }

    public void eliminar(Long id) {
        categoriaRepository.deleteById(id);
    }
}