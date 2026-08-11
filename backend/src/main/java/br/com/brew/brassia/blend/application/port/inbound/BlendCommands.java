package br.com.brew.brassia.blend.application.port.inbound;

import br.com.brew.brassia.blend.domain.BlendKind;
import br.com.brew.brassia.blend.domain.BlendOperation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Simular, aprovar e executar união ou divisão (BLD-001). */
public interface BlendCommands {

    BlendOperation simulate(SimulateCommand command);

    BlendOperation approve(UUID breweryId, UUID operationId, UUID actor);

    BlendOperation execute(UUID breweryId, UUID operationId, UUID actor);

    BlendOperation discard(UUID breweryId, UUID operationId, UUID actor);

    /**
     * @param results saídas que ainda não são lote — cada uma vira um lote na execução (DEC-BLD-003)
     */
    record SimulateCommand(UUID breweryId, BlendKind kind, List<MovementInput> inputs,
            List<MovementInput> outputs, List<ResultInput> results, BigDecimal declaredLossLiters,
            String reason, UUID actor) {
    }

    record MovementInput(UUID batchId, BigDecimal liters) {
    }

    /**
     * Lote novo declarado como resultado.
     *
     * <p>A receita vem de quem planeja e não da origem predominante: uma união de 60% de IPA com 40% de
     * Stout não é "uma IPA", e herdar a receita da maior parte imprimiria o ABV e o estilo dela no rótulo
     * de uma cerveja que não é ela.
     */
    record ResultInput(UUID recipeId, UUID equipmentId, BigDecimal liters) {
    }
}
