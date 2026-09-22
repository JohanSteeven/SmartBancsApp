package com.financial.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("SmartBancs Transaction API")
                .version("1.0.0")
                .description("API REST para procesamiento de transferencias financieras de alta concurrencia y baja latencia.")
                .contact(new Contact()
                    .name("SmartBancs Engineering Team")
                    .email("engineering@smartbancs.com")));
    }
}
