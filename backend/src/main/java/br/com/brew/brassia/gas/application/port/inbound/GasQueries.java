package br.com.brew.brassia.gas.application.port.inbound;

import br.com.brew.brassia.gas.application.port.outbound.GasConnectionRepository;
import br.com.brew.brassia.gas.domain.GasConnection;
import br.com.brew.brassia.gas.domain.GasCylinder;
import br.com.brew.brassia.gas.domain.GasNetworkComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consultas de gás (GAS-001), sem efeito colateral. */
public interface GasQueries {

    List<GasCylinder> cylinders(UUID breweryId);

    Optional<GasCylinder> cylinder(UUID breweryId, UUID cylinderId);

    /** {@code kind} nulo lista reguladores e manifolds. */
    List<GasNetworkComponent> components(UUID breweryId, String kind);

    List<GasConnection> connections(UUID breweryId, boolean onlyOpen);

    Optional<ConnectionDetail> connection(UUID breweryId, UUID connectionId);

    /** Conexão com o histórico que a operação consulta junto. */
    record ConnectionDetail(GasConnection connection,
            List<GasConnectionRepository.PressureReadingRow> pressureReadings,
            List<GasConnectionRepository.ConsumptionRow> consumption) {}
}
