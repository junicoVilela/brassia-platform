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
    SENSOR_READING_FLAGGED("sensor_reading.flagged"),

    // INT-008 — os eventos comerciais. Eles não trazem integração nova: entram na mesma allowlist e
    // saem pelo mesmo outbox com retry, porque o critério "integração externa falha sem corromper
    // pedido" já era o motivo de o outbox existir (ver WebhookDelivery).
    //
    // Quem consome: fiscal emite a nota a partir do pedido confirmado, POS e e-commerce acertam o
    // estoque deles, contábil reconhece a receita no atendimento. A plataforma não calcula imposto —
    // motor fiscal está fora do escopo da sprint —, ela avisa que houve o fato.
    SALES_ORDER_PLACED("sales_order.placed"),
    SALES_ORDER_CANCELLED("sales_order.cancelled"),
    SALES_ORDER_FULFILLED("sales_order.fulfilled"),

    /** O lote virou vendável: é o gatilho para o e-commerce publicar o produto. */
    FINISHED_LOT_RELEASED("finished_lot.released");

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
