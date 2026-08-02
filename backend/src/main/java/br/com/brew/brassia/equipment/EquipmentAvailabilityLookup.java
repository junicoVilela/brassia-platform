package br.com.brew.brassia.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Consulta publicada de disponibilidade de um equipamento numa janela (EQP-002), para outros
 * módulos agendarem uso sem acessar a tabela de equipamentos nem a de manutenções (ex.: linha
 * de envase, PKG-001). O motivo da indisponibilidade é exposto para o chamador explicá-lo.
 */
public interface EquipmentAvailabilityLookup {

    Availability check(UUID breweryId, UUID equipmentId, Instant from, Instant to);

    enum Availability {
        AVAILABLE,
        /** Não existe equipamento com esse id nesta cervejaria. */
        UNKNOWN,
        /** Equipamento desativado no cadastro. */
        INACTIVE,
        /** Há manutenção agendada sobrepondo a janela. */
        UNDER_MAINTENANCE
    }
}
