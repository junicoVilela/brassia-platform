package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCleanlinessLookup;
import br.com.brew.brassia.equipment.EquipmentUsageCommands;
import br.com.brew.brassia.equipment.application.port.outbound.CleanlinessRepository;
import br.com.brew.brassia.equipment.domain.Cleanliness;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado de limpeza do equipamento (CLN-004-A).
 *
 * <p>Duas entradas e nenhuma terceira: <strong>usar suja</strong> e <strong>ciclo liberado limpa</strong>.
 * A ausência de um "marcar limpo" manual é a regra inteira — com ele, o estado diria que alguém clicou,
 * não que há evidência de sanitização.
 */
public final class CleanlinessHandlers {

    private CleanlinessHandlers() {
    }

    /** Consulta publicada. Equipamento inexistente devolve vazio, não "sujo": são coisas diferentes. */
    public static EquipmentCleanlinessLookup lookup(CleanlinessRepository repository) {
        return (breweryId, equipmentId) -> repository.find(breweryId, equipmentId)
                .map(c -> new EquipmentCleanlinessLookup.Status(c.isClean(), c.soiledAt(), c.cleanedAt(),
                        c.cleanedByCycleId()));
    }

    /** Usar suja. */
    public static EquipmentUsageCommands usage(CleanlinessRepository repository) {
        return (breweryId, equipmentId, at) -> {
            var current = repository.find(breweryId, equipmentId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "equipamento inexistente nesta cervejaria: " + equipmentId));
            var soiled = current.soil(at);
            // Já sujo devolve o mesmo estado: gravar de novo escreveria a mesma linha e, pior, poderia
            // renovar a data se alguém mudasse `soil` sem lembrar deste caminho.
            if (soiled != current) {
                repository.save(breweryId, equipmentId, soiled);
            }
        };
    }

    /**
     * Um ciclo de limpeza foi liberado sobre o equipamento.
     *
     * <p>Auditado, e não em silêncio: quem investiga uma contaminação semanas depois precisa saber
     * quando cada tanque passou de sujo a limpo, e por qual ciclo.
     */
    public static void applyRelease(CleanlinessRepository repository, AuditTrail audit, UUID breweryId,
            UUID equipmentId, UUID cycleId, UUID actorId, Instant releasedAt) {
        Objects.requireNonNull(cycleId, "cycleId");
        var current = repository.find(breweryId, equipmentId);
        if (current.isEmpty()) {
            // Ciclo sobre equipamento que não existe mais: o ciclo continua sendo registro válido do que
            // foi feito, e não há estado a atualizar. Falhar aqui derrubaria a liberação por causa do
            // efeito colateral dela.
            return;
        }
        repository.save(breweryId, equipmentId, current.get().cleanedBy(cycleId, releasedAt));
        audit.record(new AuditEvent(releasedAt, breweryId, actorId, "equipment.cleanliness.cleaned",
                "equipment", equipmentId.toString(), br.com.brew.brassia.audit.AuditOutcome.SUCCESS,
                Map.of("cycleId", cycleId.toString())));
    }

    static Optional<Cleanliness> unused() {
        return Optional.empty();
    }
}
