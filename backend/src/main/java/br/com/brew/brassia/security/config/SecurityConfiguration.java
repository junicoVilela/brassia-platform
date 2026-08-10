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
                                // Callback SAML: o IdP faz form POST de outro domínio e não tem como
                                // carregar o nosso token CSRF. A proteção aqui não é o token — é o aperto
                                // de mão de uso único, cujo `state` viaja no RelayState e é conferido em
                                // tempo constante contra o que foi guardado na ida (SEC-B07).
                                "/api/v1/security/sso/*/callback",
                                "/scim/v2/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/security/login",
                                "/api/v1/security/login/mfa",
                                "/api/v1/security/password/forgot",
                                "/api/v1/security/password/reset",
                                "/api/v1/security/email-verification/confirm",
                                "/api/v1/security/users/accept-invitation").permitAll()
                        // SSO: quem chama ainda NÃO TEM SESSÃO — está tentando criar uma. A proteção não
                        // é autenticação, é o aperto de mão de uso único e curta validade (SEC-B07).
                        .requestMatchers("/api/v1/security/sso/*/start",
                                "/api/v1/security/sso/*/callback").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/security/csrf",
                                "/scim/v2/ServiceProviderConfig",
                                // Sondas de liveness/readiness precisam responder sem credencial, e não
                                // revelam nada: `show-details` está no padrão `never`.
                                "/actuator/health/**",
                                "/actuator/info").permitAll()
                        // MÉTRICAS EXIGEM AUTENTICAÇÃO (REL-003).
                        //
                        // `/actuator/prometheus` era público. O corpo não tem dado de negócio, mas tem o
                        // inventário completo de rotas (label `uri`), volume de tráfego, taxa de erro por
                        // endpoint e pressão de pool do banco — reconhecimento de graça para quem estiver
                        // procurando por onde entrar, e um oráculo de disponibilidade para quem já entrou.
                        //
                        // O coletor passa a precisar de credencial: uma conta de serviço com API key
                        // atende, e o runbook de operação registra isso como pré-requisito do deploy.
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus", "/actuator/metrics/**")
                                .authenticated()
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
