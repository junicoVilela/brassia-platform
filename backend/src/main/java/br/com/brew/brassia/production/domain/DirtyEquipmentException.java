package br.com.brew.brassia.production.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** O tanque de destino não tem limpeza liberada (CLN-004-A). */
public final class DirtyEquipmentException extends RuntimeException {

    private final UUID equipmentId;
    private final Instant soiledSince;

    public DirtyEquipmentException(UUID equipmentId, Instant soiledSince) {
        super("equipamento sem limpeza liberada: " + equipmentId);
        this.equipmentId = equipmentId;
        this.soiledSince = soiledSince;
    }

    public UUID equipmentId() {
        return equipmentId;
    }

    /** Desde quando está sujo — é o que distingue "esvaziou agora" de "está parado há três semanas". */
    public Optional<Instant> soiledSince() {
        return Optional.ofNullable(soiledSince);
    }
}
