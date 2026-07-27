package br.com.brew.brassia.production.adapter.inbound.web.dto;

import br.com.brew.brassia.production.domain.BatchAlert;
import java.time.Instant;
import java.util.UUID;

public record AlertView(
        UUID id, String kind, String message, Instant plannedAt, Instant occurredAt, String status,
        Instant createdAt, Instant confirmedAt) {

    public static AlertView from(BatchAlert a) {
        return new AlertView(a.id(), a.kind().name(), a.message(), a.plannedAt(), a.occurredAt(),
                a.status().name(), a.createdAt(), a.confirmedAt());
    }

    /** Resposta enxuta de criação (o histórico completo vem no GET). */
    public record Created(UUID id) {}
}
