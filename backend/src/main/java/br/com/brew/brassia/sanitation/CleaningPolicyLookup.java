package br.com.brew.brassia.sanitation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada da política de limpeza (PRM-001), para que o envase possa aplicar a validade
 * do CIP sem conhecer a tabela de sanitização.
 *
 * <p>Quem é dono do conceito é a sanitização: quanto tempo uma liberação cobre é propriedade do
 * procedimento de limpeza, não do envase que a consome.
 */
public interface CleaningPolicyLookup {

    /** Horas de validade da liberação; vazio quando a cervejaria não configurou prazo. */
    Optional<Integer> validityHours(UUID breweryId);

    /** Se a liberação feita em {@code releasedAt} ainda cobre {@code at}. Sem prazo, sempre cobre. */
    boolean covers(UUID breweryId, Instant releasedAt, Instant at);
}
