package br.com.brew.brassia.equipment.adapter.inbound.web;

import br.com.brew.brassia.equipment.adapter.inbound.web.dto.AvailabilityResponse;
import br.com.brew.brassia.equipment.adapter.inbound.web.dto.MaintenanceResponse;
import br.com.brew.brassia.equipment.adapter.inbound.web.dto.ScheduleMaintenanceRequest;
import br.com.brew.brassia.equipment.application.port.inbound.CancelMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.CheckEquipmentAvailabilityUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ScheduleMaintenanceUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}")
final class EquipmentMaintenanceController {
    private final ScheduleMaintenanceUseCase schedule;
    private final CancelMaintenanceUseCase cancel;
    private final ListEquipmentMaintenanceUseCase list;
    private final CheckEquipmentAvailabilityUseCase availability;

    EquipmentMaintenanceController(ScheduleMaintenanceUseCase schedule, CancelMaintenanceUseCase cancel,
            ListEquipmentMaintenanceUseCase list, CheckEquipmentAvailabilityUseCase availability) {
        this.schedule = schedule;
        this.cancel = cancel;
        this.list = list;
        this.availability = availability;
    }

    @GetMapping("/maintenance")
    List<MaintenanceResponse> list(
            @PathVariable UUID equipmentId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.read");
        return list.handle(new ListEquipmentMaintenanceUseCase.Query(principal.requireBrewery(), equipmentId))
                .stream().map(MaintenanceResponse::from).toList();
    }

    @PostMapping("/maintenance")
    ResponseEntity<MaintenanceResponse> scheduleWindow(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody ScheduleMaintenanceRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.maintenance.manage");
        var result = schedule.handle(new ScheduleMaintenanceUseCase.Command(
                principal.userId(), principal.requireBrewery(), equipmentId, request.kind(), request.instrument(),
                request.startAt(), request.endAt(), request.notes()));
        return ResponseEntity
                .created(URI.create("/api/v1/equipment/" + equipmentId + "/maintenance/" + result.id()))
                .body(MaintenanceResponse.from(result));
    }

    @PostMapping("/maintenance/{maintenanceId}/cancel")
    ResponseEntity<Void> cancelWindow(
            @PathVariable UUID equipmentId,
            @PathVariable UUID maintenanceId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.maintenance.manage");
        cancel.handle(new CancelMaintenanceUseCase.Command(
                principal.userId(), principal.requireBrewery(), equipmentId, maintenanceId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability")
    AvailabilityResponse availability(
            @PathVariable UUID equipmentId,
            @RequestParam String from,
            @RequestParam String to,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.read");
        var result = availability.handle(new CheckEquipmentAvailabilityUseCase.Query(
                principal.requireBrewery(), equipmentId, parseInstant(from), parseInstant(to)));
        return AvailabilityResponse.from(result);
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("instante inválido (use ISO-8601, ex.: 2026-08-01T08:00:00Z)");
        }
    }
}
