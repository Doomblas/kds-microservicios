package com.kds.pedidos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pedidosOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("KDS - Pedidos y Caja API")
                .version("v1")
                .description("API REST del microservicio de administración de pedidos. "
                        + "Registra órdenes de compra, publica pedido.creado y sincroniza estados.")
                .contact(new Contact().name("Equipo KDS")));
    }
}
