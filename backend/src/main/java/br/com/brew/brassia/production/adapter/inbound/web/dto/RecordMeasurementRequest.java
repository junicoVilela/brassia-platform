package br.com.brew.brassia.production.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registro de medição (PRD-003), com identidade opcional de apontamento (PWA-002).
 *
 * @param clientRequestId gerado NO APARELHO no instante em que a pessoa registrou, não no envio. É o que
 *                        permite a este lado reconhecer o reenvio da fila offline como o mesmo fato.
 *                        Ausente quando o registro vem da tela com rede — aí a requisição é síncrona e
 *                        quem a fez viu a resposta.
 */
public record RecordMeasurementRequest(
        UUID stepId,
        @NotBlank String kind,
        @NotNull BigDecimal value,
        @NotBlank String unit,
        BigDecimal temperatureC,
        String method,
        @NotBlank String source,
        @Size(max = 80) String clientRequestId) {}
