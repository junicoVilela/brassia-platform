package br.com.brew.brassia.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro imutável de um comando auditável.
 *
 * @param occurredAt   instante do evento (UTC)
 * @param breweryId    cervejaria do contexto (pode ser nulo em comandos globais)
 * @param actorId      usuário que executou (pode ser nulo em comandos do sistema)
 * @param action       ação em formato ponto, ex.: {@code recipe.create}
 * @param resourceType tipo do recurso afetado, ex.: {@code recipe}
 * @param resourceId   identificador do recurso afetado (pode ser nulo)
 * @param outcome      desfecho do comando
 * @param metadata     dados adicionais; valores sensíveis são mascarados no adapter
 */
public record AuditEvent(
        Instant occurredAt,
        UUID breweryId,
        UUID actorId,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        Map<String, String> metadata) {

    public AuditEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        action = requireText(action, "action");
        resourceType = requireText(resourceType, "resourceType");
        Objects.requireNonNull(outcome, "outcome");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Evento de sucesso ocorrido agora. */
    public static AuditEvent success(UUID breweryId, UUID actorId, String action,
            String resourceType, String resourceId, Map<String, String> metadata) {
        return new AuditEvent(Instant.now(), breweryId, actorId, action,
                resourceType, resourceId, AuditOutcome.SUCCESS, metadata);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value;
    }
}
