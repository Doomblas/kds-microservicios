package com.kds.cocina.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI displayCocinaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("KDS - Display Cocina API")
                .version("v1")
                .description("API REST del microservicio de display de cocina. "
                        + "Consume pedido.creado y publica pedido.estado.actualizado.")
                .contact(new Contact().name("Equipo KDS")));
    }
}
