package br.com.brew.brassia.referencedata.application.port.outbound;

import br.com.brew.brassia.referencedata.domain.ReferenceDataset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferenceDatasetRepository {

    /** Dataset já importado com o mesmo conteúdo (idempotência por checksum). */
    Optional<ReferenceDataset> findByChecksum(UUID sourceId, String checksum);

    void insert(ReferenceDataset dataset);

    Optional<ReferenceDataset> findById(UUID id);

    List<ReferenceDataset> findBySource(UUID sourceId);

    /** Marca como publicado com trava otimista; falha se a versão não bater. */
    boolean markPublished(UUID id, Instant publishedAt, long expectedVersion);
}
