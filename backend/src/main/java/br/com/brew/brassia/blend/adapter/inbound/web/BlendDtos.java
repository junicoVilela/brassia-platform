package br.com.brew.brassia.blend.adapter.inbound.web;

import br.com.brew.brassia.blend.domain.BlendOperation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos do blend (BLD-001). */
final class BlendDtos {

    private BlendDtos() {
    }

    /**
     * A operação como sai na API.
     *
     * <p>{@code inputLiters}, {@code outputLiters} e {@code declaredLossLiters} viajam juntos e sempre.
     * Devolver só o total de saída deixaria quem consome sem como conferir a conta — e um balanço que
     * ninguém pode conferir é uma afirmação, não um balanço.
     */
    record BlendResponse(
            UUID id,
            String kind,
            List<MovementResponse> inputs,
            List<MovementResponse> outputs,
            BigDecimal inputLiters,
            BigDecimal outputLiters,
            BigDecimal declaredLossLiters,
            String reason,
            String status,
            boolean contributesLineage,
            UUID simulatedBy,
            Instant simulatedAt,
            UUID approvedBy,
            Instant approvedAt,
            UUID executedBy,
            Instant executedAt) {

        static BlendResponse from(BlendOperation operation) {
            return new BlendResponse(
                    operation.id(),
                    operation.kind().name(),
                    operation.inputs().stream()
                            .map(m -> new MovementResponse(m.batchId(), m.liters())).toList(),
                    operation.outputs().stream()
                            .map(m -> new MovementResponse(m.batchId(), m.liters())).toList(),
                    operation.inputLiters(),
                    operation.outputLiters(),
                    operation.declaredLossLiters(),
                    operation.reason(),
                    operation.status().name(),
                    // Explícito no contrato: quem lê precisa saber se esta operação já pesa na
                    // genealogia, e "EXECUTED" exige conhecer a regra para deduzir isso.
                    operation.contributesLineage(),
                    operation.simulatedBy(),
                    operation.simulatedAt(),
                    operation.approvedBy().orElse(null),
                    operation.approvedAt().orElse(null),
                    operation.executedBy().orElse(null),
                    operation.executedAt().orElse(null));
        }
    }

    record MovementResponse(UUID batchId, BigDecimal liters) {
    }
}
