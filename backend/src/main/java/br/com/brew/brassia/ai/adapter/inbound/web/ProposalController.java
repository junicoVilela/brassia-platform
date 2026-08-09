package br.com.brew.brassia.ai.adapter.inbound.web;

import br.com.brew.brassia.ai.adapter.inbound.web.dto.ProposalDtos;
import br.com.brew.brassia.ai.application.port.inbound.ProposalCommands;
import br.com.brew.brassia.ai.application.port.inbound.ProposalQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Propostas de comando e a decisão humana sobre elas (AIA-003).
 *
 * <p><strong>Três alçadas diferentes, e a diferença é a história.</strong> {@code ai.command.read} deixa ver;
 * {@code ai.command.propose} deixa pedir uma proposta; confirmar exige a permissão <em>do comando proposto</em>
 * — {@code costing.cost.close}, {@code quality.nc.manage}, {@code sanitation.cycle.execute} — conferida no
 * caso de uso e não aqui. Se a verificação do aceite morasse neste controller, ela valeria só para quem chega
 * por HTTP, e a regra é sobre quem consente, não sobre por onde entrou.
 *
 * <p>Note que o corpo do aceite não traz ação nem parâmetros: eles vêm da proposta gravada. É o que impede
 * confirmar uma coisa na tela e executar outra no servidor.
 */
@RestController
@RequestMapping("/api/v1/ai/proposals")
final class ProposalController {

    private final ProposalCommands commands;
    private final ProposalQueries queries;
    private final Clock clock;

    ProposalController(ProposalCommands commands, ProposalQueries queries) {
        this.commands = commands;
        this.queries = queries;
        this.clock = Clock.systemUTC();
    }

    @GetMapping
    List<ProposalDtos.ProposalView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("ai.command.read");
        return ProposalDtos.ProposalView.from(queries.list(principal.requireBrewery()),
                principal.permissions(), clock.instant());
    }

    /**
     * Pede ao copiloto propostas para um lote.
     *
     * <p>POST porque gasta — cada pedido é uma chamada cobrada — e porque escreve: as propostas válidas ficam
     * gravadas esperando decisão. Lista vazia é resposta legítima.
     */
    @PostMapping("/batches/{batchId}")
    List<ProposalDtos.ProposalView> propose(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID batchId) {
        principal.requirePermission("ai.command.propose");
        var proposed = commands.propose(principal.userId(), principal.requireBrewery(), batchId,
                principal.permissions());
        return ProposalDtos.ProposalView.from(proposed, principal.permissions(), clock.instant());
    }

    /**
     * Confirma.
     *
     * <p>Sem {@code requirePermission} aqui: a alçada exigida depende da ação proposta, que só se conhece
     * depois de carregar a proposta. O caso de uso a confere, e devolve 403 pelo mesmo caminho.
     */
    @PostMapping("/{proposalId}/acceptance")
    ProposalDtos.ProposalView accept(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID proposalId, @Valid @RequestBody ProposalDtos.DecisionRequest request) {
        var decided = commands.accept(principal.userId(), principal.requireBrewery(), proposalId,
                principal.permissions(), request.note());
        return ProposalDtos.ProposalView.from(decided, principal.permissions(), clock.instant());
    }

    /** Descarta. Exige só poder ver: recusar uma sugestão não altera nada no sistema. */
    @PostMapping("/{proposalId}/rejection")
    ProposalDtos.ProposalView reject(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID proposalId, @Valid @RequestBody ProposalDtos.DecisionRequest request) {
        principal.requirePermission("ai.command.read");
        var decided = commands.reject(principal.userId(), principal.requireBrewery(), proposalId,
                request.note());
        return ProposalDtos.ProposalView.from(decided, principal.permissions(), clock.instant());
    }
}
