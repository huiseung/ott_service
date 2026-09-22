package com.domain.backend.user.config;

import com.domain.backend.user.application.AccessTokenAuthenticationFilter;
import com.domain.backend.user.application.AccessTokenService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class UserSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenService accessTokenService) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        .requestMatchers("/api/user/**").hasRole("USER")
                        .requestMatchers("/api/playback/**").hasRole("USER")
                        .anyRequest().authenticated())
                .addFilterBefore(new AccessTokenAuthenticationFilter(accessTokenService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.user-web-origin:http://localhost:3000}") String userWebOrigin) {
        var userConfiguration = new CorsConfiguration();
        userConfiguration.setAllowedOrigins(List.of(userWebOrigin));
        userConfiguration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        userConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Range", "X-XSRF-TOKEN"));
        userConfiguration.setExposedHeaders(List.of("Accept-Ranges", "Content-Length", "Content-Range"));
        userConfiguration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/csrf", userConfiguration);
        source.registerCorsConfiguration("/api/auth/**", userConfiguration);
        source.registerCorsConfiguration("/api/user/**", userConfiguration);
        source.registerCorsConfiguration("/api/playback/**", userConfiguration);
        source.registerCorsConfiguration("/api/public/**", userConfiguration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
