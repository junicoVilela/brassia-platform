package br.com.brew.brassia.referencedata.application.port.outbound;

import br.com.brew.brassia.referencedata.domain.ImportJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository {

    /** Persiste o job e seus problemas de validação no mesmo commit. */
    void insert(ImportJob job);

    Optional<ImportJob> findById(UUID id);

    List<ImportJob> findBySource(UUID sourceId);

    /**
     * Aplica a publicação do job com trava otimista: status → PUBLISHED e vínculo
     * ao dataset materializado. Falha (false) se a versão não bater.
     */
    boolean markPublished(UUID id, UUID publishedDatasetId, long expectedVersion);
}
