package com.ai.aiops.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive placeholder security configuration for Phases 1-3.
 *
 * <p>OAuth2/JWT authentication and RBAC are an explicit later phase of this project. Building a
 * throwaway token scheme now would only be replaced wholesale once real identity-provider
 * integration lands, so this stub exists purely to: (a) give Spring Security a filter chain bean
 * so it doesn't generate a random dev password and log warnings, and (b) keep every endpoint
 * open during local development of the log-monitoring and anomaly-detection phases.
 *
 * TODO(phase 7): replace with OAuth2/JWT resource server config + method-level RBAC.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
