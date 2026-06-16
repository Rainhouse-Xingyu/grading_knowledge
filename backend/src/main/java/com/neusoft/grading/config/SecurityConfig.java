package com.neusoft.grading.config;

import com.neusoft.grading.filter.TokenAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenAuthFilter tokenAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CAS 回调和公开接口
                        .requestMatchers("/api/auth/cas/callback").permitAll()
                        // Knife4j / Swagger
                        .requestMatchers("/swagger-ui.html", "/v3/api-docs/**", "/doc.html/**", "/webjars/**").permitAll()
                        // Actuator 健康检查
                        .requestMatchers("/actuator/**").permitAll()
                        // 其他接口需要认证
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
