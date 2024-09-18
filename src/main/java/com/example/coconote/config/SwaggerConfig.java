package com.example.coconote.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@RequiredArgsConstructor
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
            Server server = new Server();
            server.setUrl("https://coconote.jsangmin.co.kr");
            server.description("coconote-dev");

            Server localServer = new Server();
            localServer.setUrl("http://localhost:8080");
            localServer.description("local");
        return new OpenAPI()
                .info(new Info()
                        .title("Coconote 프로젝트 API")
                        .description("Coconote 백엔드 API 명세서")
                        .version("v1"))
                .addServersItem(localServer)
                .addServersItem(server)
                .addSecurityItem(new SecurityRequirement().addList("JWT"))
                .components(new Components()
                        .addSecuritySchemes("JWT", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name(HttpHeaders.AUTHORIZATION)));
    }
}
