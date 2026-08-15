package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.LotRelease;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistência da liberação de lote acabado (SAL-001-B).
 *
 * <p>Só insere e lê. Não há {@code update} nem {@code delete} porque não há revogação: lote liberado que
 * depois se mostra problemático é caso de quarentena ou recall, e apagar a liberação faria sumir o fato
 * de que alguém a assinou.
 */
public interface LotReleaseRepository {

    void insert(LotRelease release);

    Optional<LotRelease> find(UUID breweryId, UUID finishedLotId);

    /**
     * As liberações de vários lotes de uma vez.
     *
     * <p>Existe para a listagem de vendáveis não fazer uma consulta por lote — com trinta lotes na tela,
     * a versão ingênua faz trinta idas ao banco, e foi assim que o N+1 da REL-002 apareceu.
     */
    Map<UUID, LotRelease> findAll(UUID breweryId, Set<UUID> finishedLotIds);
}
