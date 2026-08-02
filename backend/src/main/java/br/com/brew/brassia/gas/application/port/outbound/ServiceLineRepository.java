package br.com.brew.brassia.gas.application.port.outbound;

import br.com.brew.brassia.gas.domain.LineResistance;
import br.com.brew.brassia.gas.domain.ServiceLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceLineRepository {

    void insert(ServiceLine line);

    Optional<ServiceLine> findById(UUID breweryId, UUID lineId);

    /** Carrega a linha travando a linha do banco (FOR UPDATE), para revisões concorrentes. */
    Optional<ServiceLine> findForUpdate(UUID breweryId, UUID lineId);

    List<ServiceLine> findAll(UUID breweryId);

    boolean existsByCode(UUID breweryId, String code);

    boolean update(ServiceLine line, long expectedVersion);

    /** Revisão aplicada: nunca sobrescreve a anterior, só acrescenta. */
    void insertRevision(ServiceLine.Revision revision);

    /** Histórico completo de montagens, da mais recente para a mais antiga. */
    List<ServiceLine.Revision> findRevisions(UUID breweryId, UUID lineId);

    // --- catálogo de tubos ---

    void insertResistance(LineResistance resistance);

    Optional<LineResistance> findResistance(UUID breweryId, UUID resistanceId);

    /** Tubo do catálogo por material e diâmetro interno, que é a identidade dele. */
    Optional<LineResistance> findResistanceBySpec(UUID breweryId, String material, BigDecimal internalDiameterMm);

    List<LineResistance> findAllResistances(UUID breweryId);

    boolean updateResistance(LineResistance resistance, long expectedVersion);
}
