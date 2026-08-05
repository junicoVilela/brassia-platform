package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.packaging.adapter.inbound.web.dto.FinishedLotDtos;
import br.com.brew.brassia.packaging.application.port.inbound.FinishedLotQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lotes de produto acabado (TRC-001-B).
 *
 * <p>Só leitura: eles nascem do envase, nunca de um comando. Criar um lote à mão seria afirmar que
 * existe cerveja envasada que nenhuma execução registrou.
 */
@RestController
@RequestMapping("/api/v1/packaging/finished-lots")
final class FinishedLotController {

    private final FinishedLotQueries queries;

    FinishedLotController(FinishedLotQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    List<FinishedLotDtos.FinishedLotView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) UUID batchId) {
        principal.requirePermission("packaging.plan.read");
        var lots = batchId == null
                ? queries.all(principal.breweryId())
                : queries.byBatch(principal.breweryId(), batchId);
        return lots.stream().map(FinishedLotDtos.FinishedLotView::from).toList();
    }
}
