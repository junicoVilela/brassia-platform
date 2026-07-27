package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import br.com.brew.brassia.inventory.application.port.inbound.RecordLotPropertiesUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Valor medido a vincular ao lote (STK-005). */
public record LotPropertyInput(
        @NotBlank String property,
        @NotNull BigDecimal value,
        String unit,
        @NotBlank String source,
        @NotBlank String confidence) {

    public RecordLotPropertiesUseCase.PropertyInput toInput() {
        return new RecordLotPropertiesUseCase.PropertyInput(property, value, unit, source, confidence);
    }
}
