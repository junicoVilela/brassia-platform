package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import br.com.brew.brassia.packaging.application.port.inbound.CarbonationCommands;
import br.com.brew.brassia.packaging.domain.Carbonation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos da carbonatação do plano de envase (PKG-002). */
public final class CarbonationDtos {

    private CarbonationDtos() {
    }

    /**
     * {@code confirmed} nunca tem valor padrão verdadeiro: o backend recusa sem confirmação
     * explícita, para nenhum número calculado virar decisão sozinho.
     */
    public record RecordCarbonationRequest(
            @NotBlank String method,
            @NotNull @Positive BigDecimal targetVolumes,
            @NotNull BigDecimal referenceTempC,
            String primingSugar,
            boolean confirmed) {}

    /** Recomendação: entradas, método, resultado e alertas — sem nada gravado. */
    public record RecommendationView(String method, BigDecimal targetVolumes, BigDecimal referenceTempC,
            BigDecimal residualVolumes, BigDecimal missingVolumes, BigDecimal beerVolumeLiters,
            String primingSugar, BigDecimal primingSugarGrams, BigDecimal pressureBar, String calculationMethod,
            String calculatorVersion, List<String> assumptions, List<String> alerts) {

        public static RecommendationView from(CarbonationCommands.Recommendation r) {
            return new RecommendationView(r.method(), r.targetVolumes(), r.referenceTempC(), r.residualVolumes(),
                    r.missingVolumes(), r.beerVolumeLiters(), r.primingSugar(), r.primingSugarGrams(),
                    r.pressureBar(), r.calculationMethod(), r.calculatorVersion(), r.assumptions(), r.alerts());
        }
    }

    public record CarbonationView(String method, BigDecimal targetVolumes, BigDecimal referenceTempC,
            BigDecimal residualVolumes, BigDecimal missingVolumes, String primingSugar,
            BigDecimal primingSugarGrams, BigDecimal pressureBar, String calculationMethod,
            String calculatorVersion, List<String> alerts, UUID confirmedBy, Instant confirmedAt) {

        public static CarbonationView from(Carbonation c) {
            return new CarbonationView(c.method().name(), c.targetVolumes(), c.referenceTempC(),
                    c.residualVolumes(), c.missingVolumes(),
                    c.primingSugar() == null ? null : c.primingSugar().name(), c.primingSugarGrams(),
                    c.pressureBar(), c.calculationMethod(), c.calculatorVersion(), c.alerts(), c.confirmedBy(),
                    c.confirmedAt());
        }
    }
}
