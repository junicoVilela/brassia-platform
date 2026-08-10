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

    record SimulateCommand(UUID breweryId, BlendKind kind, List<MovementInput> inputs,
            List<MovementInput> outputs, BigDecimal declaredLossLiters, String reason, UUID actor) {
    }

    record MovementInput(UUID batchId, BigDecimal liters) {
    }
}
