package br.com.brew.brassia.blend.adapter.inbound.web;

import br.com.brew.brassia.blend.adapter.inbound.web.BlendDtos.BlendResponse;
import br.com.brew.brassia.blend.application.port.inbound.BlendCommands;
import br.com.brew.brassia.blend.application.port.inbound.BlendQueries;
import br.com.brew.brassia.blend.domain.BlendKind;
import br.com.brew.brassia.blend.domain.UnknownBlendOperationException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * União e divisão de volume (BLD-001).
 *
 * <p><strong>Três permissões, e não uma.</strong> Simular é gratuito e não muda nada; aprovar autoriza
 * misturar; executar abre a válvula. As duas últimas são críticas porque, depois de misturadas, duas
 * cervejas não se separam — a operação é irreversível de um jeito que quase nenhuma outra é.
 */
@RestController
@RequestMapping("/api/v1/blends")
final class BlendController {

    private final BlendCommands commands;
    private final BlendQueries queries;

    BlendController(BlendCommands commands, BlendQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    List<BlendResponse> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("blend.operation.read");
        return queries.list(principal.requireBrewery()).stream().map(BlendResponse::from).toList();
    }

    @GetMapping("/{operationId}")
    BlendResponse get(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID operationId) {
        principal.requirePermission("blend.operation.read");
        return queries.find(principal.requireBrewery(), operationId).map(BlendResponse::from)
                .orElseThrow(() -> new UnknownBlendOperationException(operationId));
    }

    /** Simular grava: uma simulação é uma proposta, e proposta que some não se aprova depois. */
    @PostMapping
    ResponseEntity<BlendResponse> simulate(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SimulateRequest request) {
        principal.requirePermission("blend.operation.simulate");
        var operation = commands.simulate(new BlendCommands.SimulateCommand(
                principal.requireBrewery(), request.kind(),
                request.inputs().stream()
                        .map(m -> new BlendCommands.MovementInput(m.batchId(), m.liters())).toList(),
                request.outputs() == null ? java.util.List.<BlendCommands.MovementInput>of()
                        : request.outputs().stream()
                                .map(m -> new BlendCommands.MovementInput(m.batchId(), m.liters())).toList(),
                request.results() == null ? java.util.List.<BlendCommands.ResultInput>of()
                        : request.results().stream()
                                .map(r -> new BlendCommands.ResultInput(r.recipeId(), r.equipmentId(),
                                        r.liters())).toList(),
                request.declaredLossLiters() == null ? BigDecimal.ZERO : request.declaredLossLiters(),
                request.reason(), principal.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(BlendResponse.from(operation));
    }

    @PostMapping("/{operationId}/approval")
    BlendResponse approve(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID operationId) {
        principal.requirePermission("blend.operation.approve");
        return BlendResponse.from(
                commands.approve(principal.requireBrewery(), operationId, principal.userId()));
    }

    @PostMapping("/{operationId}/execution")
    BlendResponse execute(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID operationId) {
        principal.requirePermission("blend.operation.execute");
        return BlendResponse.from(
                commands.execute(principal.requireBrewery(), operationId, principal.userId()));
    }

    @PostMapping("/{operationId}/discard")
    BlendResponse discard(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID operationId) {
        principal.requirePermission("blend.operation.simulate");
        return BlendResponse.from(
                commands.discard(principal.requireBrewery(), operationId, principal.userId()));
    }

    record SimulateRequest(
            @NotNull BlendKind kind,
            @NotEmpty List<@Valid MovementRequest> inputs,
            // Sem @NotEmpty desde a DEC-BLD-003: uma união cujo destino é um lote novo não tem nenhuma
            // saída pré-existente. Quem exige ao menos uma saída — de qualquer tipo — é o domínio, que é
            // onde a regra pode contar as duas listas juntas.
            List<@Valid MovementRequest> outputs,
            List<@Valid ResultRequest> results,
            @DecimalMin("0.0") BigDecimal declaredLossLiters,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** Saída que ainda não é lote: a receita declara o que o resultado é. */
    record ResultRequest(
            @NotNull UUID recipeId,
            @NotNull UUID equipmentId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal liters) {
    }

    record MovementRequest(
            @NotNull UUID batchId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal liters) {
    }
}
