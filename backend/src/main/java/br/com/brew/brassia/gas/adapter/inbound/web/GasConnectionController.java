package br.com.brew.brassia.gas.adapter.inbound.web;

import br.com.brew.brassia.gas.adapter.inbound.web.dto.GasDtos;
import br.com.brew.brassia.gas.adapter.inbound.web.dto.GasViews;
import br.com.brew.brassia.gas.application.port.inbound.ConnectionCommands;
import br.com.brew.brassia.gas.application.port.inbound.GasQueries;
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

/** Linha de gás (GAS-001): conexão, teste de vazamento, pressão, consumo e desconexão. */
@RestController
@RequestMapping("/api/v1/gas/connections")
final class GasConnectionController {

    private final ConnectionCommands.Connect connect;
    private final ConnectionCommands.RecordLeakTest leakTest;
    private final ConnectionCommands.RecordPressure pressure;
    private final ConnectionCommands.RecordConsumption consumption;
    private final ConnectionCommands.Disconnect disconnect;
    private final GasQueries queries;

    GasConnectionController(ConnectionCommands.Connect connect, ConnectionCommands.RecordLeakTest leakTest,
            ConnectionCommands.RecordPressure pressure, ConnectionCommands.RecordConsumption consumption,
            ConnectionCommands.Disconnect disconnect, GasQueries queries) {
        this.connect = connect;
        this.leakTest = leakTest;
        this.pressure = pressure;
        this.consumption = consumption;
        this.disconnect = disconnect;
        this.queries = queries;
    }

    @GetMapping
    List<GasViews.ConnectionView> list(@RequestParam(defaultValue = "false") boolean onlyOpen,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.connections(principal.requireBrewery(), onlyOpen).stream()
                .map(GasViews.ConnectionView::from)
                .toList();
    }

    /** Conexão com leituras de pressão e consumo acumulado. */
    @GetMapping("/{id}")
    GasViews.ConnectionDetailView get(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.connection(principal.requireBrewery(), id)
                .map(GasViews.ConnectionDetailView::from)
                .orElseThrow(() -> new IllegalArgumentException("conexão inexistente"));
    }

    /** Monta a linha; a recusa lista todos os impedimentos de uma vez. */
    @PostMapping
    ResponseEntity<Connected> connect(@Valid @RequestBody GasDtos.ConnectRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        var id = connect.handle(new ConnectionCommands.Connect.Command(
                principal.userId(), principal.requireBrewery(), request.cylinderId(), request.regulatorId(),
                request.manifoldId(), request.pointOfUseEquipmentId(), request.workingPressureBar()));
        return ResponseEntity.created(URI.create("/api/v1/gas/connections/" + id)).body(new Connected(id));
    }

    record Connected(UUID id) {}

    /** É o teste aprovado que libera a linha para servir. */
    @PostMapping("/{id}/leak-test")
    void leakTest(@PathVariable UUID id, @Valid @RequestBody GasDtos.LeakTestRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        leakTest.handle(new ConnectionCommands.RecordLeakTest.Command(
                principal.userId(), principal.requireBrewery(), id, request.passed(), request.method(),
                request.pressureDropBar(), request.note()));
    }

    /** A medição é preservada mesmo quando denuncia sobrepressão e bloqueia a linha. */
    @PostMapping("/{id}/pressure")
    ConnectionCommands.RecordPressure.Result pressure(@PathVariable UUID id,
            @Valid @RequestBody GasDtos.PressureRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        return pressure.handle(new ConnectionCommands.RecordPressure.Command(
                principal.userId(), principal.requireBrewery(), id, request.bar(), request.tempC()));
    }

    @PostMapping("/{id}/consumption")
    void consumption(@PathVariable UUID id, @Valid @RequestBody GasDtos.ConsumptionRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        consumption.handle(new ConnectionCommands.RecordConsumption.Command(
                principal.userId(), principal.requireBrewery(), id, request.kg(), request.reason()));
    }

    @PostMapping("/{id}/disconnect")
    void disconnect(@PathVariable UUID id, @Valid @RequestBody GasDtos.DisconnectRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        disconnect.handle(new ConnectionCommands.Disconnect.Command(
                principal.userId(), principal.requireBrewery(), id, request.reason()));
    }
}
