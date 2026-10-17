package com.project.MediFlow.config;
import jakarta.servlet.DispatcherType;
import com.project.MediFlow.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final CustomAuthenticationEntryPoint authenticationEntryPoint;
        private final CustomAccessDeniedHandler accessDeniedHandler;


        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                http
                        .csrf(csrf -> csrf.disable())

                        .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                        .exceptionHandling(exception -> exception
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler))

                        .authorizeHttpRequests(auth -> auth
                                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                                .requestMatchers(
                                        "/api/auth/register",
                                        "/api/auth/login",
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**"
                                ).permitAll()
                                .anyRequest().authenticated())

                        .addFilterBefore(
                                jwtAuthenticationFilter,
                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

}