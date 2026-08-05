package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.ChecklistItem;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackagingPlanRepository {

    void insert(PackagingPlan plan);

    Optional<PackagingPlan> findById(UUID breweryId, UUID planId);

    /** Carrega o plano travando a linha (FOR UPDATE), para comandos concorrentes na mesma linha. */
    Optional<PackagingPlan> findForUpdate(UUID breweryId, UUID planId);

    List<PackagingPlan> findAll(UUID breweryId, UUID batchId);

    boolean existsByCode(UUID breweryId, String code);

    /** Confirma um item do checklist; guardado pelo estado (só plano em PLANNED). */
    boolean confirmChecklistItem(UUID breweryId, UUID planId, ChecklistItem item, UUID actorId, Instant at);

    /** Persiste a transição de estado com lock otimista; falso quando a versão mudou. */
    boolean updateStatus(PackagingPlan plan, long expectedVersion);

    /** Existe outro plano ativo ocupando a mesma linha na janela [from, to)? */
    boolean hasLineConflict(UUID breweryId, UUID lineEquipmentId, Instant from, Instant to, UUID excludePlanId);

    /**
     * Último envase reservado que ocupou a linha antes de {@code before}.
     *
     * <p>Traz o lote junto com o instante porque a limpeza responde "quando" e a troca de produto
     * (FDS-001) responde "o quê": sem saber qual cerveja passou ali, não há como dizer que
     * alergênico ficou.
     */
    Optional<LineUse> lastLineUse(UUID breweryId, UUID lineEquipmentId, Instant before, UUID excludePlanId);

    record LineUse(UUID batchId, Instant startedAt) {}
}
