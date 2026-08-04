package br.com.brew.brassia.traceability.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.adapter.inbound.web.dto.TraceabilityViews;
import br.com.brew.brassia.traceability.application.port.inbound.TraceabilityQueries;
import br.com.brew.brassia.traceability.domain.Direction;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Genealogia de um nó (TRC-001).
 *
 * <p>Consulta pura: não há comando, porque o grafo não é uma coisa que se cria — é a leitura do que
 * insumo, ordem, lote, levedura e envase já registraram cada um por si.
 */
@RestController
@RequestMapping("/api/v1/traceability")
final class GenealogyController {

    /** Padrão generoso o bastante para a cadeia inteira caber sem que ninguém precise pedir. */
    private static final int DEFAULT_DEPTH = 6;

    private final TraceabilityQueries queries;

    GenealogyController(TraceabilityQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/genealogy")
    TraceabilityViews.GenealogyView genealogy(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam NodeType nodeType, @RequestParam UUID nodeId,
            @RequestParam(defaultValue = "BOTH") Direction direction,
            @RequestParam(defaultValue = "" + DEFAULT_DEPTH) int depth) {
        principal.requirePermission("traceability.genealogy.read");
        return TraceabilityViews.GenealogyView.of(
                queries.genealogy(principal.breweryId(), nodeType, nodeId, direction, depth));
    }
}
