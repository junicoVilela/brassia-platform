package br.com.brew.brassia.gas.adapter.inbound.web;

import br.com.brew.brassia.gas.adapter.inbound.web.dto.ServiceLineDtos;
import br.com.brew.brassia.gas.application.port.inbound.ServiceLineCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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

/** Linha de serviço e balanceamento (GAS-002): recomendação explicada e revisões preservadas. */
@RestController
@RequestMapping("/api/v1/gas")
final class ServiceLineController {

    private final ServiceLineCommands.RegisterLine registerLine;
    private final ServiceLineCommands.RegisterTubing registerTubing;
    private final ServiceLineCommands.Balance balance;
    private final ServiceLineCommands.ApplyRevision apply;
    private final ServiceLineCommands.Queries queries;

    ServiceLineController(ServiceLineCommands.RegisterLine registerLine,
            ServiceLineCommands.RegisterTubing registerTubing, ServiceLineCommands.Balance balance,
            ServiceLineCommands.ApplyRevision apply, ServiceLineCommands.Queries queries) {
        this.registerLine = registerLine;
        this.registerTubing = registerTubing;
        this.balance = balance;
        this.apply = apply;
        this.queries = queries;
    }

    @GetMapping("/service-lines")
    List<ServiceLineDtos.ServiceLineView> lines(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.lines(principal.requireBrewery()).stream()
                .map(ServiceLineDtos.ServiceLineView::from)
                .toList();
    }

    /** Linha com o histórico de montagens: o que foi montado ontem explica o copo de ontem. */
    @GetMapping("/service-lines/{id}")
    ServiceLineDtos.ServiceLineDetailView line(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.line(principal.requireBrewery(), id)
                .map(ServiceLineDtos.ServiceLineDetailView::from)
                .orElseThrow(() -> new IllegalArgumentException("linha de serviço inexistente"));
    }

    @PostMapping("/service-lines")
    ResponseEntity<Registered> registerLine(@Valid @RequestBody ServiceLineDtos.RegisterLineRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        var id = registerLine.handle(new ServiceLineCommands.RegisterLine.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.name(),
                request.pointOfUseEquipmentId()));
        return ResponseEntity.created(URI.create("/api/v1/gas/service-lines/" + id)).body(new Registered(id));
    }

    record Registered(UUID id) {}

    /** Calcula e explica; nada é aplicado nem ajustado na rede. */
    @GetMapping("/service-lines/{id}/balance")
    ServiceLineDtos.LineBalanceView balance(@PathVariable UUID id,
            @RequestParam BigDecimal targetCo2Volumes,
            @RequestParam BigDecimal servingTempC,
            @RequestParam BigDecimal elevationMeters,
            @RequestParam BigDecimal residualPressureBar,
            @RequestParam BigDecimal targetFlowLpm,
            @RequestParam UUID resistanceId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return ServiceLineDtos.LineBalanceView.from(balance.handle(new ServiceLineCommands.Balance.Query(
                principal.requireBrewery(), id, targetCo2Volumes, servingTempC, elevationMeters,
                residualPressureBar, targetFlowLpm, resistanceId)));
    }

    /** Aplica a montagem: gera revisão nova e preserva a anterior. */
    @PostMapping("/service-lines/{id}/revisions")
    ServiceLineCommands.ApplyRevision.Result apply(@PathVariable UUID id,
            @Valid @RequestBody ServiceLineDtos.ApplyRevisionRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        return apply.handle(new ServiceLineCommands.ApplyRevision.Command(
                principal.userId(), principal.requireBrewery(), id, request.targetCo2Volumes(),
                request.servingTempC(), request.elevationMeters(), request.residualPressureBar(),
                request.targetFlowLpm(), request.resistanceId(), request.appliedLengthMeters(),
                request.note()));
    }

    @GetMapping("/tubing")
    List<ServiceLineDtos.TubingView> tubing(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.tubing(principal.requireBrewery()).stream()
                .map(ServiceLineDtos.TubingView::from)
                .toList();
    }

    /** Catálogo de tubos; os números vêm da ficha do fabricante. */
    @PostMapping("/tubing")
    ResponseEntity<Registered> registerTubing(@Valid @RequestBody ServiceLineDtos.RegisterTubingRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        var id = registerTubing.handle(new ServiceLineCommands.RegisterTubing.Command(
                principal.userId(), principal.requireBrewery(), request.material(),
                request.internalDiameterMm(), request.resistanceBarPerMeter(), request.referenceFlowLpm()));
        return ResponseEntity.created(URI.create("/api/v1/gas/tubing/" + id)).body(new Registered(id));
    }
}
