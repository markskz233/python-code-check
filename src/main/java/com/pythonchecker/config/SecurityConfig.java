package com.pythonchecker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))  // 只对API请求禁用CSRF保护
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/**").permitAll()
            )
            .headers(headers -> headers.frameOptions(frameOptionsConfig -> frameOptionsConfig.disable()));
        
        return http.build();
    }
}