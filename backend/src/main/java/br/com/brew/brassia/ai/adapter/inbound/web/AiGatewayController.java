package br.com.brew.brassia.ai.adapter.inbound.web;

import br.com.brew.brassia.ai.adapter.inbound.web.dto.GatewayDtos;
import br.com.brew.brassia.ai.application.port.inbound.BudgetCommands;
import br.com.brew.brassia.ai.application.port.inbound.GatewayCommands;
import br.com.brew.brassia.ai.application.port.inbound.GatewayQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O gateway de IA visto de fora (AIA-001).
 *
 * <p>Três operações com alçadas diferentes de propósito: consultar o estado é leitura barata; verificar
 * a conectividade gasta dinheiro a cada clique; redefinir o teto decide quanto dinheiro pode ser gasto.
 * Uma permissão só para as três transformaria "quero ver se a IA está no ar" em poder de alterar o freio.
 */
@RestController
@RequestMapping("/api/v1/ai/gateway")
final class AiGatewayController {

    private final GatewayQueries queries;
    private final GatewayCommands commands;
    private final BudgetCommands budgets;

    AiGatewayController(GatewayQueries queries, GatewayCommands commands, BudgetCommands budgets) {
        this.queries = queries;
        this.commands = commands;
        this.budgets = budgets;
    }

    @GetMapping
    GatewayDtos.GatewayStatusView status(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("ai.gateway.read");
        return GatewayDtos.GatewayStatusView.from(queries.of(principal.requireBrewery()));
    }

    /** Verificação de conectividade. É POST porque gasta: uma verificação não é consulta. */
    @PostMapping("/probe")
    GatewayDtos.ProbeView probe(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("ai.gateway.probe");
        return GatewayDtos.ProbeView.from(
                commands.probe(principal.userId(), principal.requireBrewery()));
    }

    @PutMapping("/budget")
    GatewayDtos.BudgetView budget(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody GatewayDtos.BudgetRequest request) {
        principal.requirePermission("ai.budget.manage");
        return GatewayDtos.BudgetView.from(budgets.redefine(principal.userId(), principal.requireBrewery(),
                request.monthlyLimit(), request.version()));
    }
}
