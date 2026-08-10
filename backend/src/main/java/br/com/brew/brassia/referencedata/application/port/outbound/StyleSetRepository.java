package br.com.brew.brassia.referencedata.application.port.outbound;

import br.com.brew.brassia.referencedata.domain.StyleAuthority;
import br.com.brew.brassia.referencedata.domain.StyleSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StyleSetRepository {

    boolean existsByCoordinates(UUID breweryId, StyleAuthority authority, String edition, String language);

    /** Persiste o conjunto e seus estilos no mesmo commit. */
    void insert(StyleSet styleSet);

    /** Conjunto visível à cervejaria (próprio ou global), com seus estilos. */
    Optional<StyleSet> findVisible(UUID breweryId, UUID id);

    /** Metadados dos conjuntos visíveis (sem carregar os estilos), paginados. */
    List<StyleSet> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);

    boolean markPublished(UUID breweryId, UUID id, Instant publishedAt, long expectedVersion);
}
