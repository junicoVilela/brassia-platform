package br.com.brew.brassia.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap do primeiro administrador. Resolve o ciclo inicial (convidar exige
 * permissão que ninguém tem ainda). Desligado por padrão; habilitado apenas em
 * ambientes de bootstrap (ex.: local/primeiro provisionamento).
 */
@ConfigurationProperties("brassia.security.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String email, String password, String name) {
    public BootstrapAdminProperties {
        name = (name == null || name.isBlank()) ? "Administrador" : name;
    }
}
