package br.com.brew.brassia.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Escrita publicada para quem usa o equipamento (CLN-004-A).
 *
 * <p><strong>Só suja.</strong> Não há operação de limpar aqui, e é deliberado: limpar é consequência de
 * um ciclo verificado e liberado, que chega pelo evento `CleaningCycleReleased`. Expor "marcar limpo" numa
 * porta faria existir um caminho para declarar limpeza sem evidência — e seria o caminho usado no dia de
 * correria.
 */
public interface EquipmentUsageCommands {

    /**
     * O equipamento recebeu cerveja.
     *
     * <p>Idempotente por natureza: sujar o que já está sujo não muda nada, nem renova a data. Silencioso
     * para equipamento inexistente seria pior — quem chama acabou de usá-lo.
     */
    void markSoiled(UUID breweryId, UUID equipmentId, Instant at);
}
