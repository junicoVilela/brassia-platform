package br.com.brew.brassia.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Habilita o Spring Session JDBC (o Boot 4 não traz o autoconfig): as sessões
 * passam a ser persistidas em {@code spring_session} e indexadas pelo nome do
 * principal, permitindo listar/revogar por usuário (SEC-006) e a revogação na
 * desativação (SEC-001). O cookie segue a config por perfil.
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcHttpSession
class SessionConfiguration {

    @Bean
    CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name:__Host-brew_session}") String cookieName,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
