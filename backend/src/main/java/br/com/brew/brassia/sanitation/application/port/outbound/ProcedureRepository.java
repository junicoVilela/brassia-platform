package br.com.brew.brassia.sanitation.application.port.outbound;

import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureRepository {
    void insert(CleaningProcedure procedure);

    /** Substitui nome + etapas de um POP em rascunho. */
    void update(CleaningProcedure procedure);

    Optional<CleaningProcedure> findById(UUID breweryId, UUID procedureId);

    /** Versão mais recente de um código (para versionar). */
    Optional<CleaningProcedure> findLatestByCode(UUID breweryId, String code);

    /** Versão PUBLICADA mais recente de um código (para iniciar um ciclo — CLN-003). */
    Optional<CleaningProcedure> findLatestPublishedByCode(UUID breweryId, String code);

    List<CleaningProcedure> findAll(UUID breweryId);

    /** DRAFT → PUBLISHED, guardado pelo estado. {@code false} se já não estava em rascunho. */
    boolean markPublished(UUID breweryId, UUID procedureId);

    /** Há alguma versão PUBLICADA para este código na cervejaria? (CLN-002) */
    boolean existsPublishedByCode(UUID breweryId, String code);
}
