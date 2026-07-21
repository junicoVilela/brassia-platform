package br.com.brew.brassia.brewery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cervejaria default de bootstrap (ex.: primeiro provisionamento local). */
@ConfigurationProperties("brassia.brewery.bootstrap")
public record BreweryBootstrapProperties(boolean enabled, String code, String name, String timezone) {
    public BreweryBootstrapProperties {
        code = (code == null || code.isBlank()) ? "MATRIZ" : code;
        name = (name == null || name.isBlank()) ? "Cervejaria Matriz" : name;
        timezone = (timezone == null || timezone.isBlank()) ? "America/Sao_Paulo" : timezone;
    }
}
