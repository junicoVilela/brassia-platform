package br.com.brew.brassia.gas.application.port.outbound;

import br.com.brew.brassia.gas.domain.GasCylinder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GasCylinderRepository {

    void insert(GasCylinder cylinder);

    Optional<GasCylinder> findById(UUID breweryId, UUID cylinderId);

    /** Carrega o cilindro travando a linha (FOR UPDATE) para comandos concorrentes. */
    Optional<GasCylinder> findForUpdate(UUID breweryId, UUID cylinderId);

    List<GasCylinder> findAll(UUID breweryId);

    boolean existsByCode(UUID breweryId, String code);

    /** Persiste situação, conteúdo e requalificação com lock otimista. */
    boolean update(GasCylinder cylinder, long expectedVersion);
}
