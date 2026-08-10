package br.com.brew.brassia.fieldfeedback.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Como o produto foi guardado até chegar à reclamação (FLD-001).
 *
 * <p><strong>Existe para separar defeito de maltrato.</strong> Uma cerveja exposta a 35 °C por duas semanas
 * desenvolve off-flavor sem que nada tenha saído errado na fábrica — e sem esse dado, a investigação
 * procura na produção um problema que aconteceu no depósito do distribuidor.
 *
 * <p>Cada campo é opcional porque quase nunca se sabe tudo. O que não se aceita é o desconhecido virar
 * ausência silenciosa: {@link #knownConditions()} distingue "guardado corretamente" de "ninguém perguntou".
 */
public record StorageReport(
        BigDecimal approximateTemperatureCelsius,
        Integer daysSincePurchase,
        Boolean exposedToLight,
        String notes) {

    public static StorageReport unknown() {
        return new StorageReport(null, null, null, null);
    }

    /** Se alguém chegou a levantar as condições. Vazio não é "estava tudo bem". */
    public boolean knownConditions() {
        return approximateTemperatureCelsius != null || daysSincePurchase != null
                || exposedToLight != null || (notes != null && !notes.isBlank());
    }

    public Optional<BigDecimal> temperature() {
        return Optional.ofNullable(approximateTemperatureCelsius);
    }
}
