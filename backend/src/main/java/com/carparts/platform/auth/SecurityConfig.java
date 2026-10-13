package com.carparts.platform.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthSessionFilter sessionFilter) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        http.cors(Customizer.withDefaults())
            .csrf(c -> c.csrfTokenRepository(csrf)
                    .ignoringRequestMatchers("/api/auth/login","/api/auth/register","/api/public/**"))
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health","/api/auth/login","/api/auth/register","/api/auth/csrf","/api/public/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/lookups","/api/products","/api/products/*","/api/products/code-preview").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/products/**").hasAnyRole("SUPER_ADMIN","MANAGER","ADMIN","PRODUCT_ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/products/**").hasAnyRole("SUPER_ADMIN","MANAGER","ADMIN","PRODUCT_ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/products/**").hasAnyRole("SUPER_ADMIN","MANAGER","ADMIN","PRODUCT_ADMIN")
                .requestMatchers("/api/admin/lookups/**","/api/admin/product-cases/**").hasAnyRole("SUPER_ADMIN","MANAGER","ADMIN","PRODUCT_ADMIN")
                .requestMatchers("/api/admin/operations/**").hasAnyRole("SUPER_ADMIN","MANAGER","ADMIN")
                .requestMatchers("/api/admin/product-operations/**").hasAnyRole("SUPER_ADMIN","MANAGER","ADMIN","PRODUCT_ADMIN","FINANCE")
                .requestMatchers("/api/admin/users/**").hasAnyRole("SUPER_ADMIN","MANAGER")
                .requestMatchers("/api/admin/news/**").hasAnyRole("SUPER_ADMIN","MANAGER","ADMIN","SUPPORT")
                .requestMatchers("/api/manager/**").hasAnyRole("SUPER_ADMIN","MANAGER","PROCUREMENT","FINANCE","SHIPPING","SUPPORT")
                .requestMatchers("/api/panel/**","/api/auth/me","/api/auth/logout").authenticated()
                .anyRequest().authenticated())
            .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
