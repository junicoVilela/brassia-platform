package br.com.brew.brassia.fermentation.adapter.inbound.web;

import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.CollectYeastRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ReviewYeastRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.UseYeastRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.YeastHarvestView;
import br.com.brew.brassia.fermentation.application.port.inbound.CollectYeastUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.GetYeastGenealogyUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.ListYeastHarvestsUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.ReviewYeastHarvestUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.UseYeastHarvestUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Coletas de levedura (YST-001): registro, revisão humana e genealogia. */
@RestController
@RequestMapping("/api/v1/fermentation/yeast/harvests")
final class YeastHarvestController {

    private final CollectYeastUseCase collect;
    private final ReviewYeastHarvestUseCase review;
    private final ListYeastHarvestsUseCase list;
    private final GetYeastGenealogyUseCase genealogy;
    private final UseYeastHarvestUseCase use;

    YeastHarvestController(CollectYeastUseCase collect, ReviewYeastHarvestUseCase review,
            ListYeastHarvestsUseCase list, GetYeastGenealogyUseCase genealogy, UseYeastHarvestUseCase use) {
        this.collect = collect;
        this.review = review;
        this.list = list;
        this.genealogy = genealogy;
        this.use = use;
    }

    @GetMapping
    List<YeastHarvestView> list(@RequestParam(defaultValue = "false") boolean onlyAvailable,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.read");
        return list.handle(principal.requireBrewery(), onlyAvailable).stream().map(YeastHarvestView::from).toList();
    }

    /** Genealogia completa: da coleta até a levedura comprada. */
    @GetMapping("/{id}/genealogy")
    List<YeastHarvestView> genealogy(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.read");
        return genealogy.handle(principal.requireBrewery(), id).stream().map(YeastHarvestView::from).toList();
    }

    @PostMapping
    ResponseEntity<Collected> collect(@Valid @RequestBody CollectYeastRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.manage");
        var result = collect.handle(new CollectYeastUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.strainId(),
                request.sourceBatchId(), request.parentHarvestId(), request.harvestedAt(),
                request.viabilityPercent(), request.condition(), request.storageLocation(),
                request.storageTempC()));
        return ResponseEntity.created(URI.create("/api/v1/fermentation/yeast/harvests/" + result.id()))
                .body(new Collected(result.id(), result.generation()));
    }

    record Collected(UUID id, int generation) {}

    @PostMapping("/{id}/review")
    void review(@PathVariable UUID id, @Valid @RequestBody ReviewYeastRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.manage");
        review.handle(new ReviewYeastHarvestUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.approve(), request.note()));
    }

    /** Confirma o uso da coleta num lote; consome a coleta (YST-002). */
    @PostMapping("/{id}/use")
    void use(@PathVariable UUID id, @Valid @RequestBody UseYeastRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.manage");
        use.handle(new UseYeastHarvestUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.targetBatchId(), request.confirmed()));
    }
}
