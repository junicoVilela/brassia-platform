package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.packaging.SellableLotLookup;
import br.com.brew.brassia.packaging.adapter.inbound.web.dto.FinishedLotDtos;
import br.com.brew.brassia.packaging.application.port.inbound.FinishedLotQueries;
import br.com.brew.brassia.packaging.application.service.LotReleaseHandler;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lotes de produto acabado (TRC-001-B) e a liberação para venda (SAL-001-B).
 *
 * <p>O lote em si é só leitura: ele nasce do envase, nunca de um comando. Criar um lote à mão seria
 * afirmar que existe cerveja envasada que nenhuma execução registrou.
 *
 * <p><strong>A liberação é o único comando aqui, e ela não é do envase: é da qualidade.</strong> Mora
 * neste módulo porque é estado do lote — se morasse em {@code quality}, a expedição precisaria consultar
 * o outro módulo e fecharia ciclo (ADR-0016). A alçada, essa sim, é {@code quality.lot.release}.
 */
@RestController
@RequestMapping("/api/v1/packaging/finished-lots")
final class FinishedLotController {

    private final FinishedLotQueries queries;
    private final LotReleaseHandler releases;
    private final SellableLotLookup sellable;
    private final AuditTrail audit;

    FinishedLotController(FinishedLotQueries queries, LotReleaseHandler releases,
            SellableLotLookup sellable, AuditTrail audit) {
        this.queries = queries;
        this.releases = releases;
        this.sellable = sellable;
        this.audit = audit;
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

    /**
     * Libera o lote para venda.
     *
     * <p>Alçada própria e crítica: liberar é afirmar que a cerveja pode ir ao cliente, e não é o mesmo
     * ato de planejar ou executar um envase. Não há revogação — lote liberado que depois se mostra
     * problemático é caso de quarentena ou recall, que deixam rastro do porquê.
     */
    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReleaseRequest request) {
        principal.requirePermission("quality.lot.release");
        var brewery = principal.requireBrewery();
        var note = request == null ? null : request.note();
        releases.release(brewery, id, principal.userId(), note);
        audit.record(AuditEvent.success(brewery, principal.userId(), "packaging.lot.release",
                "packaging.finished_lot", id.toString(), Map.of()));
    }

    /**
     * O estado de venda do lote, com o impedimento nomeado quando houver.
     *
     * <p>Um booleano faria a tela dizer "não disponível" e o operador ligar para a qualidade perguntar o
     * motivo. Falta assinatura, validade vencida e quarentena levam a três ações diferentes.
     */
    @GetMapping("/{id}/sale-status")
    SellableLotLookup.LotSaleStatus saleStatus(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id) {
        principal.requirePermission("packaging.plan.read");
        return sellable.statusOf(principal.requireBrewery(), id, LocalDate.now())
                .orElseThrow(() -> new br.com.brew.brassia.packaging.domain.UnknownFinishedLotException(id));
    }

    record ReleaseRequest(@Size(max = 500) String note) {}
}
