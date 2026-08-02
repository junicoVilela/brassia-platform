package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * O envase tiraria do tanque mais cerveja do que o lote ainda tem. Um lote pode ser dividido em
 * vários envases — latas e barris, por exemplo — mas a soma das execuções não inventa cerveja.
 */
public final class BatchVolumeExceededException extends RuntimeException {

    private final BigDecimal batchVolumeLiters;
    private final BigDecimal alreadyPackagedLiters;
    private final BigDecimal requestedLiters;

    public BatchVolumeExceededException(BigDecimal batchVolumeLiters, BigDecimal alreadyPackagedLiters,
            BigDecimal requestedLiters) {
        super("volume do lote excedido");
        this.batchVolumeLiters = Objects.requireNonNull(batchVolumeLiters);
        this.alreadyPackagedLiters = Objects.requireNonNull(alreadyPackagedLiters);
        this.requestedLiters = Objects.requireNonNull(requestedLiters);
    }

    public BigDecimal batchVolumeLiters() { return batchVolumeLiters; }

    public BigDecimal alreadyPackagedLiters() { return alreadyPackagedLiters; }

    public BigDecimal requestedLiters() { return requestedLiters; }

    /** O quanto do lote ainda estava disponível para envasar. */
    public BigDecimal remainingLiters() {
        return batchVolumeLiters.subtract(alreadyPackagedLiters);
    }
}
