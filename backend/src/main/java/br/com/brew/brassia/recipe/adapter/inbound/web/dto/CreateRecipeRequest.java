package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateRecipeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID equipmentId,
        @NotNull BigDecimal batchVolumeLiters,
        BigDecimal targetOgPoints,
        BigDecimal targetIbu,
        BigDecimal targetColorEbc,
        BigDecimal targetAbv,
        Integer boilTimeMinutes,
        /*
         * Quanto esta cerveja admite perder, por etapa (CST-002-A). Percentual, porque perda de trub e
         * absorção de lúpulo escalam com o tamanho da brassa — um valor absoluto ficaria errado, em
         * silêncio, no dia em que a cervejaria dobrasse o lote.
         *
         * Nulo é legítimo: quem ainda não mediu a própria perda não tem esperado, e a variação volta a
         * mostrar a perda como fato. Assumir zero faria toda perda parecer desvio.
         */
        @jakarta.validation.constraints.DecimalMin("0.0")
        @jakarta.validation.constraints.DecimalMax(value = "100.0", inclusive = false)
        BigDecimal transferLossPercent,
        @jakarta.validation.constraints.DecimalMin("0.0")
        @jakarta.validation.constraints.DecimalMax(value = "100.0", inclusive = false)
        BigDecimal packagingLossPercent,
        @NotEmpty List<Item> items) {

    public record Item(
            @NotNull UUID ingredientId,
            @NotBlank String stage,
            @NotNull BigDecimal quantity,
            @NotBlank String unit,
            Integer timingMinutes,
            BigDecimal percentage) {}
}
