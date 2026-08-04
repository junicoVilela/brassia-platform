package br.com.brew.brassia.gas.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Política de gases da cervejaria (PRM-001): de quantos em quantos meses o cilindro é requalificado.
 *
 * <p>Fecha o débito GAS-001-B. O prazo depende da norma aplicável e do tipo de cilindro, então ele
 * é <strong>parâmetro da casa</strong> — o sistema não o deduz de regra fixa.
 *
 * <p><strong>Sem política, nada muda:</strong> o vencimento continua sendo informado a cada
 * requalificação, como antes.
 */
public final class GasPolicy {

    private final UUID breweryId;
    private Integer requalificationMonths;
    private final long version;

    private GasPolicy(UUID breweryId, Integer requalificationMonths, long version) {
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.requalificationMonths = requireMonths(requalificationMonths);
        this.version = version;
    }

    public static GasPolicy none(UUID breweryId) {
        return new GasPolicy(breweryId, null, 0);
    }

    public static GasPolicy reconstitute(UUID breweryId, Integer requalificationMonths, long version) {
        return new GasPolicy(breweryId, requalificationMonths, version);
    }

    public void setRequalificationMonths(Integer months) {
        this.requalificationMonths = requireMonths(months);
    }

    /**
     * Próximo vencimento a partir de {@code performedOn}, quando há política.
     *
     * <p>Vazio sem política — e aí quem requalifica informa a data, que é o comportamento que
     * existia antes. O derivado nunca sobrepõe um vencimento informado.
     */
    public Optional<LocalDate> nextDueOn(LocalDate performedOn) {
        Objects.requireNonNull(performedOn, "data da requalificação");
        return Optional.ofNullable(requalificationMonths).map(performedOn::plusMonths);
    }

    public UUID breweryId() {
        return breweryId;
    }

    public Optional<Integer> requalificationMonths() {
        return Optional.ofNullable(requalificationMonths);
    }

    public long version() {
        return version;
    }

    private static Integer requireMonths(Integer months) {
        if (months == null) {
            return null;
        }
        if (months <= 0) {
            throw new IllegalArgumentException("a periodicidade deve ser positiva");
        }
        if (months > 240) {
            throw new IllegalArgumentException("periodicidade acima de 20 anos não é requalificação");
        }
        return months;
    }
}
