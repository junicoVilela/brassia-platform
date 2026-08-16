package br.com.brew.brassia.container.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.container.application.service.ContainerHandlers;
import br.com.brew.brassia.container.domain.Container;
import br.com.brew.brassia.container.domain.ContainerKind;
import br.com.brew.brassia.container.domain.ContainerState;
import br.com.brew.brassia.container.domain.IdentifierTechnology;
import br.com.brew.brassia.container.domain.Ownership;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

/** Identidade e ciclo do contêiner retornável (CON-001). */
@RestController
@RequestMapping("/api/v1/containers")
final class ContainerController {

    private final ContainerHandlers handlers;
    private final AuditTrail audit;

    ContainerController(ContainerHandlers handlers, AuditTrail audit) {
        this.handlers = Objects.requireNonNull(handlers);
        this.audit = Objects.requireNonNull(audit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> register(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody RegisterRequest request) {
        principal.requirePermission("container.manage");
        var brewery = principal.requireBrewery();
        var id = handlers.register(brewery, request.code(), request.kind(),
                request.nominalCapacityLiters(), request.ownership());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.register", "container",
                id.toString(), Map.of("code", request.code(), "kind", request.kind().name())));
        return Map.of("id", id);
    }

    @GetMapping
    List<ContainerView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) ContainerState state) {
        principal.requirePermission("container.read");
        return handlers.list(principal.requireBrewery(), state).stream().map(ContainerView::of).toList();
    }

    @GetMapping("/{id}")
    ContainerView read(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("container.read");
        return ContainerView.of(handlers.read(principal.requireBrewery(), id));
    }

    /**
     * Uma leitura de código vira um contêiner — <strong>e nada além disso</strong>.
     *
     * <p>O endereço exige `container.read` como qualquer outra consulta: o critério transversal da sprint
     * é que ler um QR não concede autorização, e a forma de honrá-lo é o código não ser credencial em
     * lugar nenhum. Quem escaneou continua precisando de alçada para mover, encher ou dar baixa.
     */
    @GetMapping("/by-identifier")
    ContainerView resolve(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam String value) {
        principal.requirePermission("container.read");
        return ContainerView.of(handlers.resolve(principal.requireBrewery(), value));
    }

    @GetMapping("/{id}/identifiers")
    List<IdentifierView> identifiers(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id) {
        principal.requirePermission("container.read");
        return handlers.identifiers(principal.requireBrewery(), id).stream()
                .map(i -> new IdentifierView(i.id(), i.value(), i.technology().name(), i.assignedAt(),
                        i.retiredAt().orElse(null)))
                .toList();
    }

    @PostMapping("/{id}/identifiers")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> assign(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody IdentifierRequest request) {
        principal.requirePermission("container.manage");
        var brewery = principal.requireBrewery();
        var identifierId = handlers.assignIdentifier(brewery, id, request.value(),
                request.technology());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.identifier.assign",
                "container", id.toString(), Map.of("technology", request.technology().name())));
        return Map.of("id", identifierId);
    }

    /** Aposentar a etiqueta não apaga a etiqueta: ela continua explicando as leituras antigas. */
    @PostMapping("/identifiers/{identifierId}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retireIdentifier(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID identifierId) {
        principal.requirePermission("container.manage");
        var brewery = principal.requireBrewery();
        handlers.retireIdentifier(brewery, identifierId);
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.identifier.retire",
                "container_identifier", identifierId.toString(), Map.of()));
    }

    /** Alçada crítica: liberar um vaso de pressão para uso é atestado técnico. */
    @PostMapping("/{id}/inspections")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void inspect(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody InspectionRequest request) {
        principal.requirePermission("container.inspect");
        var brewery = principal.requireBrewery();
        handlers.inspect(brewery, id, request.performedAt(), request.validUntil(), principal.userId(),
                request.note());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.inspect", "container",
                id.toString(), Map.of("validUntil", request.validUntil().toString())));
    }

    @PostMapping("/{id}/moves")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void move(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody MoveRequest request) {
        principal.requirePermission("container.manage");
        var brewery = principal.requireBrewery();
        handlers.move(brewery, id, request.to());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.move", "container",
                id.toString(), Map.of("to", request.to().name())));
    }

    @PostMapping("/{id}/condition")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void condition(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody ConditionRequest request) {
        principal.requirePermission("container.manage");
        var brewery = principal.requireBrewery();
        if (request.condemned()) {
            handlers.condemn(brewery, id);
        } else {
            handlers.markDamaged(brewery, id);
        }
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.condition", "container",
                id.toString(), Map.of("condemned", String.valueOf(request.condemned()))));
    }

    /** Baixa: alçada crítica, motivo obrigatório, e nada é apagado. */
    @PostMapping("/{id}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retire(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody RetireRequest request) {
        principal.requirePermission("container.inspect");
        var brewery = principal.requireBrewery();
        handlers.retire(brewery, id, request.reason());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.retire", "container",
                id.toString(), Map.of("reason", request.reason())));
    }

    record RegisterRequest(@NotBlank @Size(max = 40) String code, @NotNull ContainerKind kind,
            @NotNull @DecimalMin(value = "0.001") BigDecimal nominalCapacityLiters,
            @NotNull Ownership ownership) {}

    record IdentifierRequest(@NotBlank @Size(max = 120) String value,
            @NotNull IdentifierTechnology technology) {}

    record InspectionRequest(@NotNull Instant performedAt, @NotNull Instant validUntil,
            @Size(max = 500) String note) {}

    /**
     * Só o destino.
     *
     * <p>O caminho não precisa ser dito: "vazio" vindo da oficina e "vazio" vindo do cliente são
     * transições diferentes, e <strong>o estado atual já diz qual é</strong>. Pedir a origem ao cliente
     * da API permitiria que ela viesse errada.
     */
    record MoveRequest(@NotNull ContainerState to) {}

    record ConditionRequest(boolean condemned) {}

    record RetireRequest(@NotBlank @Size(max = 500) String reason) {}

    /**
     * O contêiner como a tela o vê.
     *
     * @param fillable já composto: avaria, estado e inspeção juntos. A tela não recalcula a regra, e por
     *                 isso não pode divergir dela.
     */
    record ContainerView(UUID id, String code, String kind, BigDecimal nominalCapacityLiters,
            String ownership, String condition, String state, Instant inspectionValidUntil,
            boolean fillable, Instant retiredAt, String retirementReason) {

        static ContainerView of(Container c) {
            return new ContainerView(c.id(), c.code(), c.kind().name(), c.nominalCapacityLiters(),
                    c.ownership().name(), c.condition().name(), c.state().name(),
                    c.inspection().map(i -> i.validUntil()).orElse(null), c.fillableAt(Instant.now()),
                    c.retiredAt().orElse(null), c.retirementReason().orElse(null));
        }
    }

    record IdentifierView(UUID id, String value, String technology, Instant assignedAt,
            Instant retiredAt) {}
}
