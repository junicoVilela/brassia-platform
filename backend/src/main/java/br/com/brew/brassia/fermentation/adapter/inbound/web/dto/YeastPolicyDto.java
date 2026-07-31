package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.domain.YeastPolicy;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Política de reutilização da cervejaria (YST-002); campo omitido herda o padrão do domínio. */
public record YeastPolicyDto(
        @Positive Integer maxGeneration,
        @Positive Integer maxAgeDays,
        BigDecimal minViabilityPercent) {

    public static YeastPolicyDto from(YeastPolicy p) {
        return new YeastPolicyDto(p.maxGeneration(), p.maxAgeDays(), p.minViabilityPercent());
    }
}
