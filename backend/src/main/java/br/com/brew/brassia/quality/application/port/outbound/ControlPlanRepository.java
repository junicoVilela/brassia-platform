package br.com.brew.brassia.quality.application.port.outbound;

import br.com.brew.brassia.quality.domain.ControlPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ControlPlanRepository {

    void insert(ControlPlan plan);

    /** Regrava cabeçalho e pontos do rascunho; publicado nunca é reescrito. */
    void update(ControlPlan plan);

    Optional<ControlPlan> findById(UUID breweryId, UUID planId);

    Optional<ControlPlan> lockById(UUID breweryId, UUID planId);

    List<ControlPlan> findAll(UUID breweryId);

    boolean existsByCodeAndVersion(UUID breweryId, String code, int version);
}
