package br.com.brew.brassia.quality.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Ponto de controle: um parâmetro medido, sua faixa, com que frequência e o que fazer quando sai
 * dela.
 *
 * <p><strong>Ação é obrigatória.</strong> Ponto sem ação prescrita não é controle — é observação:
 * registra-se que algo saiu da faixa e ninguém sabe o que fazer a respeito. É justamente a ação
 * que transforma a medição em decisão.
 *
 * <p>{@code critical} marca ponto crítico de controle. É aqui que a designação de instrumento
 * criada em MTR-001 encontra o uso: medir num ponto crítico exige instrumento apto.
 */
public final class ControlPoint {

    private final UUID id;
    private final String parameter;
    private final SpecLimits limits;
    private final Frequency frequency;
    private final String action;
    private final Severity severity;
    private final boolean critical;

    private ControlPoint(UUID id, String parameter, SpecLimits limits, Frequency frequency, String action,
            Severity severity, boolean critical) {
        this.id = Objects.requireNonNull(id, "id");
        this.parameter = requireText(parameter, "parâmetro", 120);
        this.limits = Objects.requireNonNull(limits, "limites são obrigatórios");
        this.frequency = Objects.requireNonNull(frequency, "frequência é obrigatória");
        this.action = requireText(action, "ação", 500);
        this.severity = Objects.requireNonNull(severity, "severidade é obrigatória");
        this.critical = critical;
    }

    public static ControlPoint of(String parameter, SpecLimits limits, Frequency frequency, String action,
            Severity severity, boolean critical) {
        return new ControlPoint(UUID.randomUUID(), parameter, limits, frequency, action, severity, critical);
    }

    public static ControlPoint reconstitute(UUID id, String parameter, SpecLimits limits, Frequency frequency,
            String action, Severity severity, boolean critical) {
        return new ControlPoint(id, parameter, limits, frequency, action, severity, critical);
    }

    public UUID id() {
        return id;
    }

    public String parameter() {
        return parameter;
    }

    public SpecLimits limits() {
        return limits;
    }

    public Frequency frequency() {
        return frequency;
    }

    public String action() {
        return action;
    }

    public Severity severity() {
        return severity;
    }

    public boolean critical() {
        return critical;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }
}
