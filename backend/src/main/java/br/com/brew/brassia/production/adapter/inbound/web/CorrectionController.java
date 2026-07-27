package br.com.brew.brassia.production.adapter.inbound.web;

import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.production.adapter.inbound.web.dto.PreviewCorrectionRequest;
import br.com.brew.brassia.production.application.port.inbound.ListBrewCorrectionsUseCase;
import br.com.brew.brassia.production.application.port.inbound.PreviewCorrectionUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Correções determinísticas do dia de brassa (PRD-004): só pré-visualiza impactos. */
@RestController
@RequestMapping("/api/v1/production")
final class CorrectionController {

    private final ListBrewCorrectionsUseCase listCorrections;
    private final PreviewCorrectionUseCase previewCorrection;

    CorrectionController(ListBrewCorrectionsUseCase listCorrections, PreviewCorrectionUseCase previewCorrection) {
        this.listCorrections = listCorrections;
        this.previewCorrection = previewCorrection;
    }

    @GetMapping("/corrections")
    List<CalculatorEngine.CalculatorInfo> corrections(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return listCorrections.handle();
    }

    @PostMapping("/batches/{id}/corrections/preview")
    CalculatorEngine.Computation preview(
            @PathVariable UUID id, @Valid @RequestBody PreviewCorrectionRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        Map<String, java.math.BigDecimal> inputs = request.inputs() == null ? Map.of() : request.inputs();
        return previewCorrection.handle(new PreviewCorrectionUseCase.Command(
                principal.requireBrewery(), id, request.calculator(), inputs));
    }
}
