package br.com.brew.brassia.water.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SimulateWaterBlendUseCase {
    Result handle(Command command);

    /** Uma fonte na mistura e o volume aportado. */
    record Input(UUID sourceId, BigDecimal volumeLiters) {}

    /** @param targetProfileId perfil-alvo opcional para comparação */
    record Command(UUID breweryId, List<Input> inputs, UUID targetProfileId) {}

    /** Entrada aplicada, ecoada no resultado ("informa entradas"). */
    record AppliedInput(UUID sourceId, String code, BigDecimal volumeLiters) {}

    /** Desvio (mistura − alvo) por íon; pode ser negativo. */
    record Deviation(BigDecimal calcium, BigDecimal magnesium, BigDecimal sodium, BigDecimal sulfate,
            BigDecimal chloride, BigDecimal bicarbonate) {}

    record Target(UUID profileId, String code, Deviation deviation) {}

    record Result(String method, BigDecimal totalVolumeLiters, BigDecimal calcium, BigDecimal magnesium,
            BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride, BigDecimal bicarbonate,
            List<AppliedInput> inputs, Target target) {}
}
