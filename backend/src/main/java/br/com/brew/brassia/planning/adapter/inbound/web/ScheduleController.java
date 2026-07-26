package br.com.brew.brassia.planning.adapter.inbound.web;

import br.com.brew.brassia.planning.adapter.inbound.web.dto.CreateScheduleEntryRequest;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.ScheduleEntryResponse;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.ScheduleEntryView;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.SimulateScheduleRequest;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.SimulateScheduleResponse;
import br.com.brew.brassia.planning.application.port.inbound.CreateScheduleEntryUseCase;
import br.com.brew.brassia.planning.application.port.inbound.ListScheduleEntriesUseCase;
import br.com.brew.brassia.planning.application.port.inbound.SimulateScheduleUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planning/schedule")
final class ScheduleController {

    private final CreateScheduleEntryUseCase createEntry;
    private final SimulateScheduleUseCase simulate;
    private final ListScheduleEntriesUseCase listEntries;

    ScheduleController(CreateScheduleEntryUseCase createEntry, SimulateScheduleUseCase simulate,
            ListScheduleEntriesUseCase listEntries) {
        this.createEntry = createEntry;
        this.simulate = simulate;
        this.listEntries = listEntries;
    }

    @PostMapping
    ResponseEntity<ScheduleEntryResponse> create(
            @Valid @RequestBody CreateScheduleEntryRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.schedule.manage");
        // brewery_id é autoridade do principal (cervejaria ativa), nunca do corpo.
        var result = createEntry.handle(new CreateScheduleEntryUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.recipeId(), request.equipmentId(),
                request.assignedUserId(), request.plannedVolumeLiters(), request.scheduledStart(),
                request.scheduledEnd()));
        return ResponseEntity.created(URI.create("/api/v1/planning/schedule/" + result.id()))
                .body(new ScheduleEntryResponse(result.id(), result.status()));
    }

    @PostMapping("/simulate")
    SimulateScheduleResponse simulate(
            @Valid @RequestBody SimulateScheduleRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.schedule.read");
        return SimulateScheduleResponse.from(simulate.handle(new SimulateScheduleUseCase.Query(
                principal.requireBrewery(), request.equipmentId(), request.scheduledStart(),
                request.scheduledEnd())));
    }

    @GetMapping
    List<ScheduleEntryView> list(
            @RequestParam String from,
            @RequestParam String to,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.schedule.read");
        var range = parseRange(from, to);
        return listEntries.handle(new ListScheduleEntriesUseCase.Query(principal.requireBrewery(),
                range.from(), range.to())).stream().map(ScheduleEntryView::from).toList();
    }

    private static Range parseRange(String from, String to) {
        try {
            var f = Instant.parse(from);
            var t = Instant.parse(to);
            if (!t.isAfter(f)) {
                throw new IllegalArgumentException("'to' deve ser posterior a 'from'");
            }
            return new Range(f, t);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("intervalo inválido: use datas ISO-8601 em 'from' e 'to'");
        }
    }

    private record Range(Instant from, Instant to) {}
}
