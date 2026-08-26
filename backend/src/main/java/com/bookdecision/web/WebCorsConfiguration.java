package com.bookdecision.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebCorsProperties.class)
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final WebCorsProperties properties;

    public WebCorsConfiguration(WebCorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()
                )
                .allowedHeaders(
                        HttpHeaders.ACCEPT,
                        HttpHeaders.CONTENT_TYPE,
                        UserDatasetController.ACCESS_TOKEN_HEADER
                )
                .allowCredentials(false)
                .maxAge(properties.preflightCacheSeconds());
    }
}
