package com.domain.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(Customizer.withDefaults())
                .cors(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/auth/csrf", "/api/admin/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/admin/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) -> response.setStatus(401)))
                .logout(logout -> logout
                        .logoutUrl("/api/admin/auth/logout")
                        .deleteCookies("OTT_ADMIN_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> response.setStatus(401))
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write(exception instanceof CsrfException
                                    ? "{\"code\":\"CSRF_INVALID\"}" : "{\"code\":\"FORBIDDEN\"}");
                        }))
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.admin-web-origin:http://localhost:3001}") String adminWebOrigin) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(adminWebOrigin));
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Range", "X-CSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Accept-Ranges", "Content-Length", "Content-Range"));
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", configuration);
        return source;
    }

    @Bean
    UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder passwordEncoder) {
        var admin = User.withUsername(properties.getAdminUsername())
                .password(passwordEncoder.encode(properties.getAdminPassword()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
