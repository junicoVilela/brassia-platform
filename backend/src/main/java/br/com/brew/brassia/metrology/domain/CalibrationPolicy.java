package br.com.brew.brassia.metrology.domain;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Política de calibração da cervejaria (PRM-001): de quantos em quantos meses cada tipo de
 * instrumento é recalibrado.
 *
 * <p>É <strong>por tipo</strong> porque o prazo de um termômetro não é o de uma balança. Fecha o
 * débito registrado em MTR-001, onde a periodicidade ficou de fora justamente por depender da norma
 * e do tipo.
 *
 * <p><strong>Sem política para o tipo, nada muda:</strong> o vencimento continua vindo do
 * certificado, informado a cada calibração.
 */
public final class CalibrationPolicy {

    private final UUID breweryId;
    private final Map<InstrumentType, Integer> monthsByType;

    private CalibrationPolicy(UUID breweryId, Map<InstrumentType, Integer> monthsByType) {
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.monthsByType = new EnumMap<>(Objects.requireNonNull(monthsByType, "periodicidades"));
        this.monthsByType.values().forEach(CalibrationPolicy::requireMonths);
    }

    public static CalibrationPolicy none(UUID breweryId) {
        return new CalibrationPolicy(breweryId, new EnumMap<>(InstrumentType.class));
    }

    public static CalibrationPolicy reconstitute(UUID breweryId, Map<InstrumentType, Integer> months) {
        return new CalibrationPolicy(breweryId, months);
    }

    /** @param months {@code null} remove a periodicidade daquele tipo */
    public void set(InstrumentType type, Integer months) {
        Objects.requireNonNull(type, "tipo de instrumento");
        if (months == null) {
            monthsByType.remove(type);
            return;
        }
        monthsByType.put(type, requireMonths(months));
    }

    /**
     * Próximo vencimento a partir da execução, quando há periodicidade para o tipo.
     *
     * <p>Vazio sem política — e aí o vencimento vem do certificado, que é o comportamento anterior.
     * O derivado nunca sobrepõe um vencimento informado.
     */
    public Optional<LocalDate> nextDueOn(InstrumentType type, LocalDate performedOn) {
        Objects.requireNonNull(type, "tipo de instrumento");
        Objects.requireNonNull(performedOn, "data da calibração");
        return Optional.ofNullable(monthsByType.get(type)).map(performedOn::plusMonths);
    }

    public UUID breweryId() {
        return breweryId;
    }

    public Map<InstrumentType, Integer> monthsByType() {
        return Map.copyOf(monthsByType);
    }

    private static Integer requireMonths(Integer months) {
        if (months == null || months <= 0) {
            throw new IllegalArgumentException("a periodicidade deve ser positiva");
        }
        if (months > 120) {
            throw new IllegalArgumentException("periodicidade acima de 10 anos não é calibração");
        }
        return months;
    }
}
