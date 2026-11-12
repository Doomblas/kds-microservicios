package com.kds.menu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI menuOpenAPI() {

        Info info = new Info()
                .title("KDS Menu API")
                .description("""
                        API del microservicio de menú del Kitchen Display System.
                        Permite administrar categorías y productos disponibles para la creación de pedidos.
                        """)
                .version("v1.0");

        return new OpenAPI().info(info);
    }
}
