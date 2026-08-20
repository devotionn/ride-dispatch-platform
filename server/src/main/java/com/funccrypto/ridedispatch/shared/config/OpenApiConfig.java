package com.funccrypto.ridedispatch.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI rideDispatchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ride Dispatch Platform API")
                .version("v1")
                .description("Passenger H5, driver and admin APIs for the manual ride dispatch platform."));
    }
}
