package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import br.com.brew.brassia.packaging.domain.PackagingRun;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contratos da execução do envase (PKG-003). */
public final class PackagingRunDtos {

    private PackagingRunDtos() {
    }

    /** A perda não é enviada: ela é derivada do que saiu do tanque menos o que foi envasado. */
    public record ExecutePackagingRequest(
            @NotNull @Positive BigDecimal inputVolumeLiters,
            @PositiveOrZero int producedUnits,
            @PositiveOrZero int rejectedUnits,
            @Size(max = 200) String note) {}

    /**
     * @param finishedLotCode lote de produto acabado gerado por esta execução (TRC-001-B); nulo só
     *                        para execuções anteriores à história, que nasceram sem lote
     */
    public record PackagingRunView(UUID id, UUID batchId, BigDecimal inputVolumeLiters, int producedUnits,
            int rejectedUnits, BigDecimal packagedVolumeLiters, BigDecimal rejectedVolumeLiters,
            BigDecimal lossesLiters, BigDecimal lossPercent, int containersConsumed, String note,
            Instant executedAt, UUID executedBy, String finishedLotCode) {

        public static PackagingRunView from(PackagingRun run, String finishedLotCode) {
            return new PackagingRunView(run.id(), run.batchId(), run.inputVolumeLiters(), run.producedUnits(),
                    run.rejectedUnits(), run.packagedVolumeLiters(), run.rejectedVolumeLiters(),
                    run.lossesLiters(), run.lossPercent(), run.containersConsumed(), run.note(),
                    run.executedAt(), run.executedBy(), finishedLotCode);
        }
    }
}
