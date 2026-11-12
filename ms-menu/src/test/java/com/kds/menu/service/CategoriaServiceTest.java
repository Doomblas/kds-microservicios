package com.kds.menu.service;

import com.kds.menu.dto.CategoriaRequest;
import com.kds.menu.exception.RecursoDuplicadoException;
import com.kds.menu.model.Categoria;
import com.kds.menu.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void crearCategoria_debeGuardarCuandoNombreNoExiste() {

        CategoriaRequest request = new CategoriaRequest();
        request.setNombre("Hamburguesas");
        request.setActivo(true);

        when(categoriaRepository.existsByNombreIgnoreCase("Hamburguesas")).thenReturn(false);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> {
            Categoria categoria = invocation.getArgument(0);
            categoria.setId(1L);
            return categoria;
        });

        Categoria resultado = categoriaService.crear(request);

        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getNombre()).isEqualTo("Hamburguesas");
        assertThat(resultado.getActivo()).isTrue();

        verify(categoriaRepository).existsByNombreIgnoreCase("Hamburguesas");
        verify(categoriaRepository).save(any(Categoria.class));
    }

    @Test
    void crearCategoria_debeFallarCuandoNombreDuplicadoIgnorandoMayusculas() {

        CategoriaRequest request = new CategoriaRequest();
        request.setNombre("hamburguesas");
        request.setActivo(true);

        when(categoriaRepository.existsByNombreIgnoreCase("hamburguesas")).thenReturn(true);

        assertThatThrownBy(() -> categoriaService.crear(request))
                .isInstanceOf(RecursoDuplicadoException.class)
                .hasMessage("Ya existe una categoria con el nombre: hamburguesas");

        verify(categoriaRepository).existsByNombreIgnoreCase("hamburguesas");
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    void listarTodas_debeDevolverCategoriasDelRepository() {

        List<Categoria> categorias = List.of(
                crearCategoria(1L, "Hamburguesas"),
                crearCategoria(2L, "Bebidas"));

        when(categoriaRepository.findAll()).thenReturn(categorias);

        List<Categoria> resultado = categoriaService.listarTodas();

        assertThat(resultado).containsExactlyElementsOf(categorias);
        verify(categoriaRepository).findAll();
    }

    @Test
    void buscarPorId_debeDevolverCategoriaCuandoExiste() {

        Categoria categoria = crearCategoria(1L, "Hamburguesas");

        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));

        Optional<Categoria> resultado = categoriaService.buscarPorId(1L);

        assertThat(resultado).containsSame(categoria);
        verify(categoriaRepository).findById(1L);
    }

    private Categoria crearCategoria(Long id, String nombre) {

        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setActivo(true);
        return categoria;
    }
}
