package br.com.brew.brassia.integration.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Os eventos que podem ser publicados para fora (INT-002).
 *
 * <p><strong>É uma allowlist fechada, e é ela que define a fronteira.</strong> Publicar "todo evento de
 * domínio" pareceria mais flexível e seria o contrário: cada evento novo passaria a vazar para fora sem
 * ninguém decidir que ele deveria, e um evento interno criado para acoplar dois módulos viraria contrato
 * público por acidente. O que sai daqui é escolhido uma vez, com nome estável.
 *
 * <p>O nome externo é <strong>separado do nome da classe</strong> de propósito: renomear
 * {@code BrewOrderReleased} é refatoração nossa e não pode quebrar o endpoint de um cliente.
 */
public enum WebhookEventType {

    BREW_ORDER_RELEASED("brew_order.released"),
    BREW_ORDER_STARTED("brew_order.started"),
    BREW_ORDER_CANCELLED("brew_order.cancelled"),
    RECIPE_PUBLISHED("recipe.published"),
    CLEANING_CYCLE_RELEASED("cleaning_cycle.released"),
    SENSOR_READING_FLAGGED("sensor_reading.flagged");

    private final String externalName;

    WebhookEventType(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public static WebhookEventType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipo de evento é obrigatório");
        }
        var normalized = raw.trim();
        return Arrays.stream(values())
                .filter(t -> t.externalName.equalsIgnoreCase(normalized)
                        || t.name().equalsIgnoreCase(normalized.replace('.', '_')
                                .toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tipo de evento desconhecido: " + raw));
    }

    public static Set<String> externalNames() {
        return Arrays.stream(values()).map(WebhookEventType::externalName)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
