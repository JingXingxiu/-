package com.bookdecision.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI bookDecisionOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "adminBasic",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                ))
                .info(new Info()
                        .title("Used-book recycling decision API")
                        .version("v1")
                        .description("Constraint-optimization demo. Every response discloses its dataset source."));
    }
}
