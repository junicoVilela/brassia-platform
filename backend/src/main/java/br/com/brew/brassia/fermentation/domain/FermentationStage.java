package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Estágio de um perfil de fermentação (FER-001): setpoint de temperatura, rampa até ele,
 * pressão e o critério de avanço (tempo/densidade/manual) com a marca de "exige
 * confirmação". A condição tipada valida seus campos: TIME exige dias; GRAVITY exige FG-alvo.
 */
public record FermentationStage(UUID id, int sequence, String name, BigDecimal targetTempC, Integer rampHours,
        BigDecimal pressurePsi, AdvanceCondition condition, Integer conditionDays, BigDecimal targetGravity,
        boolean requiresConfirmation) {

    public FermentationStage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequência deve ser positiva");
        }
        name = requireText(name, "nome do estágio");
        Objects.requireNonNull(targetTempC, "temperatura-alvo é obrigatória");
        if (rampHours != null && rampHours < 0) {
            throw new IllegalArgumentException("rampa (horas) não pode ser negativa");
        }
        if (pressurePsi != null && pressurePsi.signum() < 0) {
            throw new IllegalArgumentException("pressão não pode ser negativa");
        }
        switch (condition) {
            case TIME -> {
                if (conditionDays == null || conditionDays <= 0) {
                    throw new IllegalArgumentException("avanço por tempo exige dias positivos");
                }
                if (targetGravity != null) {
                    throw new IllegalArgumentException("avanço por tempo não usa densidade-alvo");
                }
            }
            case GRAVITY -> {
                if (targetGravity == null || targetGravity.signum() <= 0) {
                    throw new IllegalArgumentException("avanço por densidade exige FG-alvo positivo");
                }
                if (conditionDays != null) {
                    throw new IllegalArgumentException("avanço por densidade não usa dias");
                }
            }
            case MANUAL -> {
                if (conditionDays != null || targetGravity != null) {
                    throw new IllegalArgumentException("avanço manual não usa dias nem densidade-alvo");
                }
            }
        }
    }

    public static FermentationStage of(int sequence, String name, BigDecimal targetTempC, Integer rampHours,
            BigDecimal pressurePsi, AdvanceCondition condition, Integer conditionDays, BigDecimal targetGravity,
            boolean requiresConfirmation) {
        return new FermentationStage(UUID.randomUUID(), sequence, name, targetTempC, rampHours, pressurePsi,
                condition, conditionDays, targetGravity, requiresConfirmation);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }
}
