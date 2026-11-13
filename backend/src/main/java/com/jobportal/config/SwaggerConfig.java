package com.jobportal.config;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI jobPortalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Job Portal API")
                        .description("A comprehensive job portal REST API for managing job applications, candidates, companies, and job postings")
                        .version("1.0")
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}