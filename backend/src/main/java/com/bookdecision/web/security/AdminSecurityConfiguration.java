package com.bookdecision.web.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminApiProperties.class)
public class AdminSecurityConfiguration {

    private static final String ADMIN_PATH = "/api/v1/admin/**";

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            AdminApiProperties properties,
            ObjectMapper objectMapper
    ) throws Exception {
        // The API uses stateless HTTP Basic authentication; browser CSRF cookies are not involved.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (properties.enabled()) {
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(ADMIN_PATH).hasRole("ADMIN")
                            .anyRequest().permitAll())
                    .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(
                            (request, response, exception) -> writeUnauthorized(response, objectMapper)
                    ));
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .httpBasic(httpBasic -> httpBasic.disable());
        }
        return http.build();
    }

    private static void writeUnauthorized(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Administrator credentials are missing or invalid"
        );
        problem.setTitle("Unauthorized");
        problem.setProperty("errorCode", "ADMIN_AUTHENTICATION_REQUIRED");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    @Bean
    UserDetailsService adminUserDetailsService(AdminApiProperties properties) {
        if (!properties.enabled()) {
            // Suppresses Spring Boot's generated default user while the admin feature is disabled.
            return new InMemoryUserDetailsManager();
        }
        String username = requireCredential(properties.username(), "ADMIN_USERNAME");
        String password = requireCredential(properties.password(), "ADMIN_PASSWORD");
        if (username.length() > 128) {
            throw new IllegalStateException("ADMIN_USERNAME must contain at most 128 characters");
        }
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    private static String requireCredential(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    environmentName + " must be provided when book-decision.admin.enabled=true"
            );
        }
        return value;
    }
}
