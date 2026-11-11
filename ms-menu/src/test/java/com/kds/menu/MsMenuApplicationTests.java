package com.kds.menu;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test de integración: levanta el contexto completo de Spring y requiere una
 * instancia real de PostgreSQL accesible con la configuración de la aplicación.
 *
 * <p>No forma parte de la suite normal ({@code ./mvnw test}) porque el tag
 * {@code integration} está excluido en la configuración de Surefire. Para
 * ejecutarlo explícitamente, con PostgreSQL disponible:</p>
 *
 * <pre>{@code
 * DB_PASSWORD=... ./mvnw test -Pintegration-tests
 * }</pre>
 */
@Tag("integration")
@SpringBootTest
class MsMenuApplicationTests {

	@Test
	void contextLoads() {
	}

}
