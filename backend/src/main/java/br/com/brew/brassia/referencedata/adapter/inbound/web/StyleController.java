package br.com.brew.brassia.referencedata.adapter.inbound.web;

import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.CompareStyleRequest;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.CompareStyleResponse;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.CreateStyleSetRequest;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.ReferenceIdResponse;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.StyleSetDetailResponse;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.StyleSetResponse;
import br.com.brew.brassia.referencedata.application.port.inbound.CompareToStyleUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.CreateStyleSetUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.ListStyleSetsUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishStyleSetUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.StyleSetDetailUseCase;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.ReferenceDatasetResponse;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
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

@RestController
@RequestMapping("/api/v1/reference/style-sets")
final class StyleController {

    private final CreateStyleSetUseCase create;
    private final ListStyleSetsUseCase list;
    private final StyleSetDetailUseCase detail;
    private final PublishStyleSetUseCase publish;
    private final CompareToStyleUseCase compare;

    StyleController(CreateStyleSetUseCase create, ListStyleSetsUseCase list, StyleSetDetailUseCase detail,
            PublishStyleSetUseCase publish, CompareToStyleUseCase compare) {
        this.create = create;
        this.list = list;
        this.detail = detail;
        this.publish = publish;
        this.compare = compare;
    }

    @GetMapping
    PageResponse<StyleSetResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.read");
        var result = list.handle(new ListStyleSetsUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(StyleSetResponse::from).toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) result.total() / size);
        return new PageResponse<>(content, page, size, result.total(), totalPages);
    }

    @PostMapping
    ResponseEntity<ReferenceIdResponse> create(
            @Valid @RequestBody CreateStyleSetRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.manage");
        var result = create.handle(new CreateStyleSetUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.sourceId(), request.authority(),
                request.edition(), request.language(), request.effectiveFrom(), request.effectiveTo(),
                request.attribution(), request.styles().stream().map(s -> s.toSpec()).toList()));
        return ResponseEntity.created(URI.create("/api/v1/reference/style-sets/" + result.id()))
                .body(new ReferenceIdResponse(result.id()));
    }

    @GetMapping("/{id}")
    StyleSetDetailResponse detail(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.read");
        return StyleSetDetailResponse.from(
                detail.handle(new StyleSetDetailUseCase.Query(principal.requireBrewery(), id)));
    }

    @PostMapping("/{id}/publish")
    ReferenceDatasetResponse publish(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.publish");
        var result = publish.handle(new PublishStyleSetUseCase.Command(
                principal.userId(), principal.requireBrewery(), id));
        return new ReferenceDatasetResponse(result.id(), null, null, null, result.status(), null, null, null,
                result.publishedAt(), null);
    }

    @PostMapping("/{id}/styles/{code}/compare")
    CompareStyleResponse compare(
            @PathVariable UUID id,
            @PathVariable String code,
            @RequestBody CompareStyleRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.read");
        var result = compare.handle(new CompareToStyleUseCase.Query(
                principal.requireBrewery(), id, code, request.og(), request.fg(), request.abv(), request.ibu(),
                request.colorEbc()));
        return CompareStyleResponse.from(result);
    }
}
