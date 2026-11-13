package com.jobportal.config;

import com.jobportal.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> {
                    response.setContentType("application/json");
                    response.setStatus(401);
                    response.getWriter().write("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                })
            )
            .authorizeHttpRequests(authz -> authz
                // Health, docs, graphql
                .requestMatchers("/health", "/docs", "/docs/**").permitAll()
                .requestMatchers("/graphql", "/graphql/**", "/playground/**", "/vendor/**").permitAll()
                .requestMatchers("/", "/error").permitAll()

                // Auth endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()

                // Swagger UI
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // Public read access
                .requestMatchers(HttpMethod.GET, "/api/v1/jobs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/slots/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/companies/**").permitAll()

                // Job management - companies and admin
                .requestMatchers(HttpMethod.POST, "/api/v1/jobs/**").hasAnyRole("COMPANY", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/**").hasAnyRole("COMPANY", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/jobs/**").hasAnyRole("COMPANY", "ADMIN")

                // Company endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/companies/**").hasRole("COMPANY")
                .requestMatchers(HttpMethod.PUT, "/api/v1/companies/**").hasRole("COMPANY")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/companies/**").hasRole("COMPANY")

                // Candidate endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/candidates/**").hasRole("CANDIDATE")
                .requestMatchers(HttpMethod.PUT, "/api/v1/candidates/**").hasRole("CANDIDATE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/candidates/**").hasRole("CANDIDATE")
                .requestMatchers(HttpMethod.GET, "/api/v1/candidates/**").hasAnyRole("CANDIDATE", "ADMIN")

                // Applications
                .requestMatchers("/api/v1/applications/**").hasAnyRole("CANDIDATE", "COMPANY", "ADMIN")

                // User management - admin only
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")

                // Resumes
                .requestMatchers("/api/v1/resumes/**").authenticated()

                // Everything else requires auth
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
