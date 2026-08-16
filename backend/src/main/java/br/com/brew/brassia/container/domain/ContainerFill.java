package br.com.brew.brassia.container.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O que foi posto dentro do vasilhame, e quando (CON-002).
 *
 * <p><strong>Isto é evento, e não campo.</strong> Um {@code lote_atual} sobrescrito responderia "o que
 * está dentro agora" e perderia "o que estava dentro em 12 de março" — que é exatamente a pergunta de um
 * recall. Um keg vive anos e passa por dezenas de lotes; a única forma de a genealogia sobreviver a isso
 * é o vínculo ser histórico desde o primeiro dia.
 *
 * <p><strong>Esvaziar não apaga.</strong> Marca o fim do período. O registro continua dizendo que aquele
 * lote esteve naquele vasilhame entre duas datas — e é esse intervalo que liga o keg entregue ao lote
 * recolhido.
 */
public final class ContainerFill {

    private final UUID id;
    private final UUID containerId;
    private final UUID finishedLotId;
    private final String lotCode;
    private final BigDecimal volumeLiters;
    private final Instant filledAt;
    private final UUID filledBy;
    private Instant emptiedAt;

    private ContainerFill(UUID id, UUID containerId, UUID finishedLotId, String lotCode,
            BigDecimal volumeLiters, Instant filledAt, UUID filledBy, Instant emptiedAt) {
        this.id = Objects.requireNonNull(id);
        this.containerId = Objects.requireNonNull(containerId);
        this.finishedLotId = Objects.requireNonNull(finishedLotId, "lote");
        this.lotCode = Objects.requireNonNull(lotCode, "código do lote");
        this.volumeLiters = requirePositive(volumeLiters);
        this.filledAt = Objects.requireNonNull(filledAt);
        this.filledBy = Objects.requireNonNull(filledBy, "quem encheu");
        this.emptiedAt = emptiedAt;
    }

    public static ContainerFill record(UUID id, UUID containerId, UUID finishedLotId, String lotCode,
            BigDecimal volumeLiters, Instant filledAt, UUID filledBy) {
        return new ContainerFill(id, containerId, finishedLotId, lotCode, volumeLiters, filledAt,
                filledBy, null);
    }

    public static ContainerFill reconstitute(UUID id, UUID containerId, UUID finishedLotId,
            String lotCode, BigDecimal volumeLiters, Instant filledAt, UUID filledBy,
            Instant emptiedAt) {
        return new ContainerFill(id, containerId, finishedLotId, lotCode, volumeLiters, filledAt,
                filledBy, emptiedAt);
    }

    /** Fecha o período. Repetir não move a data: ela é o registro de quando o conteúdo saiu. */
    public void empty(Instant at) {
        if (emptiedAt == null) {
            if (at.isBefore(filledAt)) {
                // Esvaziar antes de encher é engano de digitação, e viraria um intervalo negativo que
                // nenhuma consulta de recall consegue interpretar.
                throw new IllegalArgumentException("o esvaziamento não pode ser anterior ao enchimento");
            }
            this.emptiedAt = at;
        }
    }

    /** O que está dentro AGORA. */
    public boolean isCurrent() {
        return emptiedAt == null;
    }

    /**
     * Este lote estava neste vasilhame naquele instante?
     *
     * <p>A pergunta do recall, e a razão de o vínculo ser histórico.
     */
    public boolean containedAt(Instant moment) {
        return !moment.isBefore(filledAt) && (emptiedAt == null || moment.isBefore(emptiedAt));
    }

    public UUID id() {
        return id;
    }

    public UUID containerId() {
        return containerId;
    }

    public UUID finishedLotId() {
        return finishedLotId;
    }

    public String lotCode() {
        return lotCode;
    }

    public BigDecimal volumeLiters() {
        return volumeLiters;
    }

    public Instant filledAt() {
        return filledAt;
    }

    public UUID filledBy() {
        return filledBy;
    }

    public Optional<Instant> emptiedAt() {
        return Optional.ofNullable(emptiedAt);
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("o volume envasado deve ser positivo");
        }
        return value;
    }
}
