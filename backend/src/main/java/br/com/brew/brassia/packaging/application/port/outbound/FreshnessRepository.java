package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.FreshnessRecord;
import br.com.brew.brassia.packaging.domain.ShelfLifePolicy;
import java.util.Optional;
import java.util.UUID;

public interface FreshnessRepository {

    /** Grava a evidência e a recomendação derivada; remedir substitui o registro do plano. */
    void save(FreshnessRecord record);

    Optional<FreshnessRecord> findByPlan(UUID breweryId, UUID planId);

    /** Carrega o registro travando a linha (FOR UPDATE), para override concorrente. */
    Optional<FreshnessRecord> findForUpdate(UUID breweryId, UUID planId);

    /** Persiste o override com lock otimista; falso quando a versão mudou. */
    boolean updateOverride(FreshnessRecord record, long expectedVersion);

    /** Política de vida útil da cervejaria; vazio quando ela ainda não configurou a sua. */
    Optional<ShelfLifePolicy> findPolicy(UUID breweryId);

    void savePolicy(UUID breweryId, ShelfLifePolicy policy);
}
