package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SecurityAlertRepository {
    record AlertView(
            UUID id, UUID breweryId, UUID userId, String alertType, String severity,
            String status, Map<String, Object> evidence, Instant createdAt) {}

    UUID create(UUID breweryId, UUID userId, String alertType, String severity, Map<String, Object> evidence);
    List<AlertView> listByBrewery(UUID breweryId, String status, int limit);
    /**
     * O alerta desta cervejaria, ou vazio.
     *
     * <p>O {@code breweryId} entrou com a DEB-INT-003: o SQL já filtrava por ele e o método não o recebia,
     * então toda chamada estourava — e resolver um alerta de segurança nunca funcionou. Escopar na
     * consulta, e não conferir depois no handler, é o que a OBS-REL-001 pede: a garantia deixa de depender
     * de quem chama lembrar de comparar.
     *
     * <p>Alerta de outra cervejaria responde como alerta que não existe, e é deliberado: distinguir os
     * dois contaria a quem tem o identificador que ele existe em algum lugar.
     */
    Optional<AlertView> findById(UUID breweryId, UUID id);
    void updateStatus(UUID breweryId, UUID id, String status, UUID resolvedBy);
}
