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
        /**
         * @param nonConformityId a NC onde as ações corretivas viram itens de CAPA (FDS-004-A). Nula
         *                        quando o simulado não gerou ação; não pode vir junto com o texto livre
         * @param actions ações a planejar na NC — com tipo, dono e prazo, que é o que distingue uma ação
         *                de uma intenção
         */
        void handle(UUID actorId, UUID breweryId, UUID drillId, int unitsLocated, String summary,
                String correctiveActions, UUID nonConformityId, java.util.List<Action> actions);

        record Action(String kind, String description, String owner, java.time.LocalDate dueOn) {}
    }
}
