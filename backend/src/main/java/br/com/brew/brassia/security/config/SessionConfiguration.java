package br.com.brew.brassia.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
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

    /**
     * Serialização dos atributos da sessão (Java). O Boot 4 não traz o autoconfig
     * de sessão, então o conversor padrão precisa ser registrado explicitamente —
     * sem ele, salvar o SecurityContext falha (Object → byte[]).
     */
    @Bean("springSessionConversionService")
    GenericConversionService springSessionConversionService() {
        var service = new GenericConversionService();
        service.addConverter(Object.class, byte[].class, new SerializingConverter());
        service.addConverter(byte[].class, Object.class, new DeserializingConverter());
        return service;
    }

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
