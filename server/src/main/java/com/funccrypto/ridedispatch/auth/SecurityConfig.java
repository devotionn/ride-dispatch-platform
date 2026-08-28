package com.funccrypto.ridedispatch.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * This service intentionally contains no form-login users. Authentication is
     * handled by the opaque Bearer session filter and AuthService; defining the
     * bean prevents Spring Boot from creating and logging a random development
     * password at startup.
     */
    @Bean
    UserDetailsService disabledFormLoginUsers() {
        return username -> {
            throw new UsernameNotFoundException("Form login is disabled");
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthService authService,
            SecurityJsonHandlers handlers) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/public/**",
                                "/api/v1/local/mock-payments/**",
                                "/api/v1/auth/admin/login",
                                "/api/v1/auth/driver/login",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasAnyRole("ADMIN", "DISPATCHER", "FINANCE")
                        .requestMatchers("/api/v1/driver/**")
                        .hasRole("DRIVER")
                        .anyRequest().authenticated())
                .addFilterBefore(new BearerAuthenticationFilter(authService), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
