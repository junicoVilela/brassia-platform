package br.com.brew.brassia.optimization.adapter.inbound.web;

import br.com.brew.brassia.optimization.adapter.inbound.web.OptimizationDtos.RunResponse;
import br.com.brew.brassia.optimization.application.port.inbound.OptimizationCommands;
import br.com.brew.brassia.optimization.application.port.inbound.OptimizationQueries;
import br.com.brew.brassia.optimization.domain.ConstraintKind;
import br.com.brew.brassia.optimization.domain.Objective;
import br.com.brew.brassia.optimization.domain.OptimizationConstraint;
import br.com.brew.brassia.optimization.domain.UnknownOptimizationRunException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
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
 * Otimização e substituição assistida (OPT-001).
 *
 * <p><strong>Otimizar não aplica nada.</strong> O resultado é uma proposta; virar receita exige criar uma
 * versão nova pelo módulo de receita, sob revisão humana, e depois registrar o ponteiro aqui. Se este
 * controller pudesse escrever na receita, "revisado" viraria um campo que alguém marca em vez de um ato
 * que alguém pratica.
 *
 * <p>A explicação da IA entra por rota própria, <em>depois</em> do resultado existir — ela não tem por
 * onde alterar o score.
 */
@RestController
@RequestMapping("/api/v1/optimizations")
final class OptimizationController {

    private final OptimizationCommands commands;
    private final OptimizationQueries queries;

    OptimizationController(OptimizationCommands commands, OptimizationQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    List<RunResponse> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) UUID recipeId) {
        principal.requirePermission("optimization.run.read");
        return queries.list(principal.requireBrewery(), recipeId).stream()
                .map(RunResponse::from).toList();
    }

    @GetMapping("/{runId}")
    RunResponse get(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID runId) {
        principal.requirePermission("optimization.run.read");
        return queries.find(principal.requireBrewery(), runId).map(RunResponse::from)
                .orElseThrow(() -> new UnknownOptimizationRunException(runId));
    }

    /** 201 mesmo quando inviável: a corrida aconteceu e ficou registrada — inviabilidade é resposta. */
    @PostMapping
    ResponseEntity<RunResponse> optimize(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody OptimizeRequest request) {
        principal.requirePermission("optimization.run.execute");
        var run = commands.optimize(new OptimizationCommands.OptimizeCommand(
                principal.requireBrewery(), request.recipeId(), request.objective(),
                request.constraints() == null ? List.of()
                        : request.constraints().stream().map(ConstraintRequest::toDomain).toList(),
                principal.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RunResponse.from(run));
    }

    @PostMapping("/{runId}/explanation")
    RunResponse explain(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID runId, @Valid @RequestBody ExplainRequest request) {
        principal.requirePermission("optimization.run.execute");
        return RunResponse.from(commands.explain(principal.requireBrewery(), runId,
                request.explanation(), principal.userId()));
    }

    /**
     * Registra que uma alternativa virou versão de receita.
     *
     * <p>Recebe o id da versão já criada — não cria nada. Permissão crítica porque, mesmo sendo só um
     * ponteiro, ela declara que aquela alternativa passou a valer.
     */
    @PostMapping("/{runId}/application")
    RunResponse apply(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID runId,
            @Valid @RequestBody ApplyRequest request) {
        principal.requirePermission("optimization.run.apply");
        return RunResponse.from(commands.markApplied(principal.requireBrewery(), runId,
                request.recipeVersionId(), principal.userId()));
    }

    record OptimizeRequest(
            @NotNull UUID recipeId,
            @NotNull Objective objective,
            List<@Valid ConstraintRequest> constraints) {
    }

    record ConstraintRequest(@NotNull ConstraintKind kind, BigDecimal minValue, BigDecimal maxValue,
            UUID ingredientId) {

        OptimizationConstraint toDomain() {
            return new OptimizationConstraint(kind, minValue, maxValue, ingredientId);
        }
    }

    record ExplainRequest(@NotBlank @Size(max = 4000) String explanation) {
    }

    record ApplyRequest(@NotNull UUID recipeVersionId) {
    }
}
