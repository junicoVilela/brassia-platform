package br.com.brew.brassia.equipment.application.port.outbound;

import br.com.brew.brassia.equipment.domain.Cleanliness;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado de limpeza do equipamento (CLN-004-A).
 *
 * <p>Mora na tabela de equipamento e tem repositório próprio porque **não é perfil**: capacidade e
 * eficiência descrevem o que o equipamento é e viram revisão a cada mudança; limpo ou sujo descreve como
 * ele está agora. Passar por `EquipmentRepository.update` geraria uma revisão de perfil a cada tanque
 * esvaziado, enchendo o histórico de mudanças que não mudaram medida nenhuma.
 */
public interface CleanlinessRepository {

    Optional<Cleanliness> find(UUID breweryId, UUID equipmentId);

    /**
     * O estado de vários equipamentos numa consulta só.
     *
     * <p>Existe pela lição da REL-002: a listagem carregaria o estado item a item e o N+1 ficaria
     * invisível em quem lê o código — a consulta extra não apareceria na chamada, apareceria no mapeador.
     */
    java.util.Map<UUID, Cleanliness> findAll(UUID breweryId, java.util.Collection<UUID> equipmentIds);

    /** Grava o estado. Retorna {@code false} quando o equipamento não existe nesta cervejaria. */
    boolean save(UUID breweryId, UUID equipmentId, Cleanliness cleanliness);
}
