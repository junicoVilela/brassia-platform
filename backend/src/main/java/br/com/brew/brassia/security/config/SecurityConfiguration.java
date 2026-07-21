package br.com.brew.brassia.security.config;

import br.com.brew.brassia.security.adapter.inbound.web.ProblemDetailAccessDeniedHandler;
import br.com.brew.brassia.security.adapter.inbound.web.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName("XSRF-TOKEN");
        csrf.setCookiePath("/");
        return http
                .csrf(config -> config.csrfTokenRepository(csrf))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/security/login",
                                "/api/v1/security/login/mfa",
                                "/api/v1/security/password/forgot",
                                "/api/v1/security/password/reset").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
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
