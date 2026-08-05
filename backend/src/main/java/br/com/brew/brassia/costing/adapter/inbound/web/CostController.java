package br.com.brew.brassia.costing.adapter.inbound.web;

import br.com.brew.brassia.costing.adapter.inbound.web.dto.CostDtos;
import br.com.brew.brassia.costing.application.port.inbound.CostCommands;
import br.com.brew.brassia.costing.application.port.inbound.CostQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
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
 * Custo realizado do lote (CST-001).
 *
 * <p>A consulta responde o custo fechado quando existe e o de agora quando não existe — e diz em
 * qual dos dois casos está, porque a diferença muda como o número deve ser lido. Fechar é comando
 * de alçada própria: o custo deixa de mudar, e passa a valer como evidência.
 */
@RestController
@RequestMapping("/api/v1/costing")
final class CostController {

    private final CostQueries queries;
    private final CostCommands.Close close;

    CostController(CostQueries queries, CostCommands.Close close) {
        this.queries = queries;
        this.close = close;
    }

    @GetMapping("/batch-costs")
    List<CostDtos.BatchCostView> closed(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("costing.cost.read");
        return CostDtos.BatchCostView.from(queries.closed(principal.requireBrewery()));
    }

    @GetMapping("/batches/{batchId}")
    CostDtos.BatchCostView ofBatch(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID batchId) {
        principal.requirePermission("costing.cost.read");
        return CostDtos.BatchCostView.from(queries.ofBatch(principal.requireBrewery(), batchId));
    }

    @PostMapping("/batches/{batchId}/close")
    CostDtos.BatchCostView close(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID batchId, @Valid @RequestBody CostDtos.CloseRequest request) {
        principal.requirePermission("costing.cost.close");
        return CostDtos.BatchCostView.from(close.handle(principal.userId(), principal.requireBrewery(),
                batchId, request.note()));
    }
}
