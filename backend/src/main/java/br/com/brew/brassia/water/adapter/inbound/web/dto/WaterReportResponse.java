package br.com.brew.brassia.water.adapter.inbound.web.dto;

import br.com.brew.brassia.water.application.port.inbound.ListWaterReportsUseCase;
import br.com.brew.brassia.water.application.port.inbound.RecordWaterReportUseCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WaterReportResponse(
        UUID id,
        UUID sourceId,
        LocalDate collectedOn,
        String method,
        BigDecimal calcium,
        BigDecimal magnesium,
        BigDecimal sodium,
        BigDecimal sulfate,
        BigDecimal chloride,
        BigDecimal bicarbonate,
        String notes) {

    public static WaterReportResponse from(RecordWaterReportUseCase.Result r) {
        return new WaterReportResponse(r.id(), r.sourceId(), r.collectedOn(), r.method(), r.calcium(),
                r.magnesium(), r.sodium(), r.sulfate(), r.chloride(), r.bicarbonate(), r.notes());
    }

    public static WaterReportResponse from(ListWaterReportsUseCase.Report r) {
        return new WaterReportResponse(r.id(), r.sourceId(), r.collectedOn(), r.method(), r.calcium(),
                r.magnesium(), r.sodium(), r.sulfate(), r.chloride(), r.bicarbonate(), r.notes());
    }
}
