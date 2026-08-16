package br.com.brew.brassia.distribution.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.distribution.application.service.LoadHandlers;
import br.com.brew.brassia.distribution.domain.Load;
import br.com.brew.brassia.distribution.domain.LoadStop;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Carga, roteiro e responsável (LOG-001). */
@RestController
@RequestMapping("/api/v1/distribution/loads")
final class LoadController {

    private final LoadHandlers handlers;
    private final AuditTrail audit;

    LoadController(LoadHandlers handlers, AuditTrail audit) {
        this.handlers = Objects.requireNonNull(handlers);
        this.audit = Objects.requireNonNull(audit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> plan(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody PlanRequest request) {
        principal.requirePermission("distribution.load.plan");
        var brewery = principal.requireBrewery();
        var id = handlers.plan(brewery, request.code(), request.scheduledFor(),
                request.capacityLiters(), principal.userId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "distribution.load.plan",
                "distribution_load", id.toString(), Map.of("code", request.code())));
        return Map.of("id", id);
    }

    @GetMapping
    List<LoadView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate day) {
        principal.requirePermission("distribution.load.read");
        return handlers.list(principal.requireBrewery(), day).stream().map(LoadView::of).toList();
    }

    @GetMapping("/{id}")
    LoadView read(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("distribution.load.read");
        return LoadView.of(handlers.read(principal.requireBrewery(), id));
    }

    @PostMapping("/{id}/stops")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> addStop(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody StopRequest request) {
        principal.requirePermission("distribution.load.plan");
        var stopId = handlers.addStop(principal.requireBrewery(), id, request.customerId(),
                request.customerName(), request.sequence(), request.windowFrom(), request.windowTo());
        return Map.of("id", stopId);
    }

    @DeleteMapping("/{id}/stops/{stopId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeStop(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID stopId) {
        principal.requirePermission("distribution.load.plan");
        handlers.removeStop(principal.requireBrewery(), id, stopId);
    }

    /**
     * Põe um vasilhame na parada.
     *
     * <p>É aqui que a saída cobra a qualidade — a promessa que a CON-002 deixou em aberto. A checagem
     * acontece na montagem <strong>e</strong> na liberação: na montagem, para não jogar fora o trabalho
     * de montar uma carga que não pode sair; na liberação, porque entre uma e outra um lote pode ter
     * entrado em quarentena.
     */
    @PostMapping("/{id}/stops/{stopId}/containers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void loadContainer(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID stopId, @Valid @RequestBody ContainerRequest request) {
        principal.requirePermission("distribution.load.plan");
        handlers.loadContainer(principal.requireBrewery(), id, stopId, request.containerId());
    }

    @DeleteMapping("/{id}/containers/{containerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unloadContainer(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID containerId) {
        principal.requirePermission("distribution.load.plan");
        handlers.unloadContainer(principal.requireBrewery(), id, containerId);
    }

    @PostMapping("/{id}/driver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void assign(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody DriverRequest request) {
        principal.requirePermission("distribution.load.plan");
        handlers.assign(principal.requireBrewery(), id, request.driverId(), request.vehicle());
    }

    /**
     * Confere e libera — <strong>alçada própria, e nunca a mesma pessoa que montou</strong>.
     *
     * <p>A conferência existe para encontrar o erro de quem montou, e quem montou relê o próprio trabalho
     * enxergando o que quis colocar. Uma conferência feita pela mesma pessoa custa o mesmo tempo e não
     * encontra nada.
     */
    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("distribution.load.release");
        var brewery = principal.requireBrewery();
        handlers.release(brewery, id, principal.userId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "distribution.load.release",
                "distribution_load", id.toString(), Map.of()));
    }

    /** Reabrir derruba a conferência: o papel não pode dizer que alguém olhou o que ninguém olhou. */
    @PostMapping("/{id}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reopen(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("distribution.load.plan");
        var brewery = principal.requireBrewery();
        handlers.reopen(brewery, id);
        audit.record(AuditEvent.success(brewery, principal.userId(), "distribution.load.reopen",
                "distribution_load", id.toString(), Map.of()));
    }

    @PostMapping("/{id}/depart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void depart(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("distribution.load.plan");
        handlers.depart(principal.requireBrewery(), id);
    }

    @PostMapping("/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("distribution.load.plan");
        handlers.close(principal.requireBrewery(), id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("distribution.load.plan");
        handlers.cancel(principal.requireBrewery(), id);
    }

    record PlanRequest(@NotBlank @Size(max = 40) String code, @NotNull LocalDate scheduledFor,
            @NotNull @DecimalMin(value = "0.001") BigDecimal capacityLiters) {}

    record StopRequest(@NotNull UUID customerId, @NotBlank @Size(max = 160) String customerName,
            @NotNull @Min(1) Integer sequence, Instant windowFrom, Instant windowTo) {}

    record ContainerRequest(@NotNull UUID containerId) {}

    record DriverRequest(@NotNull UUID driverId, @Size(max = 60) String vehicle) {}

    /**
     * @param frozen depois de liberada a carga não muda — a tela esconde os botões em vez de deixar o
     *               operador descobrir no 409
     */
    record LoadView(UUID id, String code, LocalDate scheduledFor, BigDecimal capacityLiters,
            BigDecimal loadedLiters, BigDecimal remainingLiters, String status, UUID plannedBy,
            UUID releasedBy, Instant releasedAt, UUID driverId, String vehicle, boolean frozen,
            int customerCount, List<StopView> route) {

        static LoadView of(Load l) {
            return new LoadView(l.id(), l.code(), l.scheduledFor(), l.capacityLiters(),
                    l.loadedLiters(), l.remainingLiters(), l.status().name(), l.plannedBy(),
                    l.releasedBy().orElse(null), l.releasedAt().orElse(null),
                    l.driverId().orElse(null), l.vehicle().orElse(null), l.isFrozen(),
                    l.customerCount(),
                    l.route().stream().map(s -> StopView.of(s, l)).toList());
        }
    }

    record StopView(UUID id, int sequence, UUID customerId, String customerName, Instant windowFrom,
            Instant windowTo, List<ItemView> items) {

        static StopView of(LoadStop s, Load l) {
            return new StopView(s.id(), s.sequence(), s.customerId(), s.customerName(),
                    s.window().map(w -> w.from()).orElse(null),
                    s.window().map(w -> w.to()).orElse(null),
                    s.containerIds().stream()
                            .map(c -> new ItemView(c, l.volumeOf(c))).toList());
        }
    }

    record ItemView(UUID containerId, BigDecimal volumeLiters) {}
}
