package br.com.brew.brassia.traceability.application.port.inbound;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.domain.RecallDrill;
import java.util.UUID;

/** Comandos do simulado de recall (FDS-004). Nenhum deles toca em estoque, envase ou comunicação. */
public interface DrillCommands {

    interface Start {
        RecallDrill handle(UUID actorId, UUID breweryId, NodeType type, UUID nodeId, String note);
    }

    interface Finish {
        /**
         * @param unitsLocated quantas unidades a equipe de fato localizou — declarado por gente,
         *                     porque contar sozinho daria 100% sempre e não mediria nada
         */
        void handle(UUID actorId, UUID breweryId, UUID drillId, int unitsLocated, String summary,
                String correctiveActions);
    }
}
