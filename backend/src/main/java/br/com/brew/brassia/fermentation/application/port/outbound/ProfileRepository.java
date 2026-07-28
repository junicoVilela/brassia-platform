package br.com.brew.brassia.fermentation.application.port.outbound;

import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    void insert(FermentationProfile profile);

    /** Substitui nome + estágios de um perfil em rascunho. */
    void update(FermentationProfile profile);

    Optional<FermentationProfile> findById(UUID breweryId, UUID profileId);

    /** Versão mais recente de um código (para versionar). */
    Optional<FermentationProfile> findLatestByCode(UUID breweryId, String code);

    List<FermentationProfile> findAll(UUID breweryId);

    /** DRAFT → PUBLISHED, guardado pelo estado. {@code false} se já não estava em rascunho. */
    boolean markPublished(UUID breweryId, UUID profileId);
}
