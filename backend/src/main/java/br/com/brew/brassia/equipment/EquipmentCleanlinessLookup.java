package br.com.brew.brassia.equipment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada do estado de limpeza (CLN-004-A).
 *
 * <p>Existe para que quem vai colocar cerveja num tanque pergunte a <em>uma</em> fonte se ele está limpo,
 * em vez de cada módulo deduzir isso por conta própria a partir dos ciclos. O envase já fazia essa
 * dedução sozinho (`CleaningReleaseLookup`), e era só ele: o fermentador recebia cerveja sem ninguém
 * perguntar nada.
 */
public interface EquipmentCleanlinessLookup {

    Optional<Status> status(UUID breweryId, UUID equipmentId);

    default boolean isClean(UUID breweryId, UUID equipmentId) {
        return status(breweryId, equipmentId).map(Status::clean).orElse(false);
    }

    /**
     * @param soiledSince desde quando está sujo; vazio quando está limpo. É o que distingue o tanque que
     *                    esvaziou hoje de manhã do que está parado sujo há três semanas — e o segundo é
     *                    um problema pior
     */
    record Status(boolean clean, Instant soiledSince, Instant cleanedAt, UUID cleanedByCycleId) {}
}
