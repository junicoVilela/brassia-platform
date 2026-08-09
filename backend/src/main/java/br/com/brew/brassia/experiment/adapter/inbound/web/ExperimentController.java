package br.com.brew.brassia.experiment.adapter.inbound.web;

import br.com.brew.brassia.experiment.adapter.inbound.web.ExperimentDtos.ExperimentResponse;
import br.com.brew.brassia.experiment.application.port.inbound.ExperimentCommands;
import br.com.brew.brassia.experiment.application.port.inbound.ExperimentQueries;
import br.com.brew.brassia.experiment.domain.UnknownExperimentException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Experimentos de lote dividido (EXP-001).
 *
 * <p><strong>Concluir tem permissão própria e crítica.</strong> Planejar é uma intenção; concluir define o
 * que a cervejaria vai passar a acreditar sobre a própria receita, e essa conclusão vai guiar decisões
 * depois que ninguém mais lembrar de como o experimento foi feito.
 */
@RestController
@RequestMapping("/api/v1/experiments")
final class ExperimentController {

    private final ExperimentCommands commands;
    private final ExperimentQueries queries;

    ExperimentController(ExperimentCommands commands, ExperimentQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    List<ExperimentResponse> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) UUID recipeId) {
        principal.requirePermission("experiment.plan.read");
        return queries.list(principal.requireBrewery(), recipeId).stream()
                .map(ExperimentResponse::from).toList();
    }

    @GetMapping("/{experimentId}")
    ExperimentResponse get(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID experimentId) {
        principal.requirePermission("experiment.plan.read");
        return queries.find(principal.requireBrewery(), experimentId)
                .map(ExperimentResponse::from)
                .orElseThrow(() -> new UnknownExperimentException(experimentId));
    }

    @PostMapping
    ResponseEntity<ExperimentResponse> plan(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody PlanRequest request) {
        principal.requirePermission("experiment.plan.write");
        var plan = commands.plan(new ExperimentCommands.PlanCommand(
                principal.requireBrewery(), request.recipeId(), request.hypothesis(),
                request.controlBatchId(), request.variantBatchId(),
                request.factors().stream()
                        .map(f -> new ExperimentCommands.FactorInput(f.name(), f.controlValue(),
                                f.variantValue()))
                        .toList(),
                request.plannedMeasurements() == null ? Set.of() : request.plannedMeasurements(),
                request.sensoryPlanned(), request.sensoryBlind(), principal.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ExperimentResponse.from(plan));
    }

    @PostMapping("/{experimentId}/start")
    ExperimentResponse start(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID experimentId) {
        principal.requirePermission("experiment.plan.write");
        return ExperimentResponse.from(
                commands.start(principal.requireBrewery(), experimentId, principal.userId()));
    }

    @PostMapping("/{experimentId}/conclusion")
    ExperimentResponse conclude(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID experimentId, @Valid @RequestBody ConcludeRequest request) {
        principal.requirePermission("experiment.plan.conclude");
        return ExperimentResponse.from(commands.conclude(new ExperimentCommands.ConcludeCommand(
                principal.requireBrewery(), experimentId, request.supported(), request.observation(),
                principal.userId())));
    }

    @PostMapping("/{experimentId}/abandon")
    ExperimentResponse abandon(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID experimentId) {
        principal.requirePermission("experiment.plan.write");
        return ExperimentResponse.from(
                commands.abandon(principal.requireBrewery(), experimentId, principal.userId()));
    }

    /**
     * @param factors todos os fatores, inclusive os iguais — é o que permite conferir depois que o resto
     *                ficou mesmo igual. Exatamente um pode diferir.
     */
    record PlanRequest(
            @NotNull UUID recipeId,
            @NotBlank @Size(max = 2000) String hypothesis,
            @NotNull UUID controlBatchId,
            @NotNull UUID variantBatchId,
            @NotEmpty List<@Valid FactorRequest> factors,
            Set<String> plannedMeasurements,
            boolean sensoryPlanned,
            boolean sensoryBlind) {
    }

    record FactorRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String controlValue,
            @NotBlank @Size(max = 200) String variantValue) {
    }

    /** Sem campo de limitações: elas vêm do plano. Ver Conclusion. */
    record ConcludeRequest(boolean supported, @NotBlank @Size(max = 4000) String observation) {
    }
}
