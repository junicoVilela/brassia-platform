package br.com.brew.brassia.security.config;

import br.com.brew.brassia.security.adapter.inbound.web.ProblemDetailAccessDeniedHandler;
import br.com.brew.brassia.security.adapter.inbound.web.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import jakarta.servlet.Filter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Filter apiKeyAuthenticationFilter) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName("XSRF-TOKEN");
        csrf.setCookiePath("/");
        return http
                // Aceite de convite é autenticado pelo token do link (sem sessão/
                // cookie de autoridade ambiente), portanto isento de CSRF.
                .csrf(config -> config.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                "/api/v1/security/users/accept-invitation",
                                "/scim/v2/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/security/login",
                                "/api/v1/security/login/mfa",
                                "/api/v1/security/password/forgot",
                                "/api/v1/security/password/reset",
                                "/api/v1/security/email-verification/confirm",
                                "/api/v1/security/users/accept-invitation").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/security/csrf",
                                "/scim/v2/ServiceProviderConfig",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers("/scim/v2/**", "/api/v1/security/service-accounts/me").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint())
                        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler()))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }
}
