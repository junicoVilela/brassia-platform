package br.com.brew.brassia.quality.application.port.outbound;

import br.com.brew.brassia.quality.domain.NonConformity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NonConformityRepository {

    void insert(NonConformity nonConformity);

    /** Regrava cabeçalho, fases e ações; o histórico de verificações só cresce. */
    void update(NonConformity nonConformity);

    Optional<NonConformity> findById(UUID breweryId, UUID nonConformityId);

    Optional<NonConformity> lockById(UUID breweryId, UUID nonConformityId);

    List<NonConformity> findAll(UUID breweryId);

    boolean existsByCode(UUID breweryId, String code);

    /**
     * Próximo número da NC no ano, atômico.
     *
     * <p>Mesmo desenho da numeração das ordens (V36): o `INSERT ... ON CONFLICT DO UPDATE ... RETURNING`
     * resolve incremento e leitura numa ida ao banco, e é ele que impede duas aberturas simultâneas de
     * receberem o mesmo número. Ler-e-somar na aplicação daria o mesmo número às duas.
     */
    long nextSequence(UUID breweryId, int year);
}