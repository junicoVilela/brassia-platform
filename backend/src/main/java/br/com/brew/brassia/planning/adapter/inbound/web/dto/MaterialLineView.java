package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import br.com.brew.brassia.planning.application.port.inbound.MaterialRequirementUseCase;
import java.math.BigDecimal;
import java.util.UUID;

/** Necessidade de um ingrediente, na unidade canônica (sem disponibilidade/falta — Sprint 06). */
public record MaterialLineView(UUID ingredientId, BigDecimal requiredQuantity, String unit) {

    public static MaterialLineView from(MaterialRequirementUseCase.Line line) {
        return new MaterialLineView(line.ingredientId(), line.requiredQuantity(), line.unit());
    }
}
