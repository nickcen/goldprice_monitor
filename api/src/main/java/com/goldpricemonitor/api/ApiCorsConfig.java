package com.goldpricemonitor.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class ApiCorsConfig implements WebMvcConfigurer {
    private final String[] origins;

    public ApiCorsConfig(@Value("${app.desktop-origins:tauri://localhost,http://tauri.localhost,http://127.0.0.1:1420}") String origins) {
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept");
    }
}
