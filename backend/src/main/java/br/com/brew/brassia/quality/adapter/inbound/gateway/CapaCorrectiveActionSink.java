package br.com.brew.brassia.quality.adapter.inbound.gateway;

import br.com.brew.brassia.quality.application.port.inbound.NonConformityCommands;
import br.com.brew.brassia.traceability.CorrectiveActionSink;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A ponta da qualidade para as ações corretivas de um simulado de recall (FDS-004-A).
 *
 * <p>Passa pelo mesmo caso de uso da tela: as validações de tipo, dono e prazo são as mesmas, e um
 * caminho paralelo seria um segundo lugar onde elas precisariam ser mantidas iguais — divergiriam na
 * primeira mudança.
 *
 * <p>Não abre transação própria: quem chama é o encerramento do simulado, que já roda dentro de uma. É o
 * que impede um simulado encerrado apontando para uma NC cujas ações falharam ao gravar.
 */
@org.springframework.stereotype.Component
class CapaCorrectiveActionSink implements CorrectiveActionSink {

    private final NonConformityCommands.PlanAction planAction;

    CapaCorrectiveActionSink(NonConformityCommands.PlanAction planAction) {
        this.planAction = Objects.requireNonNull(planAction, "planAction");
    }

    @Override
    public void plan(UUID breweryId, UUID actorId, UUID targetId, List<CorrectiveAction> actions) {
        for (var action : actions) {
            planAction.handle(new NonConformityCommands.PlanAction.Command(actorId, breweryId, targetId,
                    action.kind(), action.description(), action.owner(), action.dueOn()));
        }
    }
}
