package br.com.brew.brassia.shared.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WebObservabilityConfiguration {

    /**
     * Ordem menor que a do Spring Security (padrão -100) para o traceId já existir
     * no MDC quando as respostas de erro 401/403 forem escritas.
     */
    private static final int TRACE_FILTER_ORDER = -200;

    @Bean
    FilterRegistrationBean<RequestTraceIdFilter> traceIdFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new RequestTraceIdFilter());
        registration.setOrder(TRACE_FILTER_ORDER);
        return registration;
    }
}
