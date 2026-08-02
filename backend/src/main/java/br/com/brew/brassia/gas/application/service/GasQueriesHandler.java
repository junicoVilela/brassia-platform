package br.com.brew.brassia.gas.application.service;

import br.com.brew.brassia.gas.application.port.inbound.GasQueries;
import br.com.brew.brassia.gas.application.port.outbound.GasConnectionRepository;
import br.com.brew.brassia.gas.application.port.outbound.GasCylinderRepository;
import br.com.brew.brassia.gas.application.port.outbound.GasNetworkComponentRepository;
import br.com.brew.brassia.gas.domain.ComponentKind;
import br.com.brew.brassia.gas.domain.GasConnection;
import br.com.brew.brassia.gas.domain.GasCylinder;
import br.com.brew.brassia.gas.domain.GasNetworkComponent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Consultas de gás (GAS-001), sem efeito colateral. */
public final class GasQueriesHandler implements GasQueries {

    private final GasCylinderRepository cylinders;
    private final GasNetworkComponentRepository components;
    private final GasConnectionRepository connections;

    public GasQueriesHandler(GasCylinderRepository cylinders, GasNetworkComponentRepository components,
            GasConnectionRepository connections) {
        this.cylinders = Objects.requireNonNull(cylinders);
        this.components = Objects.requireNonNull(components);
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public List<GasCylinder> cylinders(UUID breweryId) {
        return cylinders.findAll(breweryId);
    }

    @Override
    public Optional<GasCylinder> cylinder(UUID breweryId, UUID cylinderId) {
        return cylinders.findById(breweryId, cylinderId);
    }

    @Override
    public List<GasNetworkComponent> components(UUID breweryId, String kind) {
        return components.findAll(breweryId, kind == null || kind.isBlank() ? null : ComponentKind.of(kind));
    }

    @Override
    public List<GasConnection> connections(UUID breweryId, boolean onlyOpen) {
        return connections.findAll(breweryId, onlyOpen);
    }

    @Override
    public Optional<ConnectionDetail> connection(UUID breweryId, UUID connectionId) {
        return connections.findById(breweryId, connectionId)
                .map(connection -> new ConnectionDetail(connection,
                        connections.findPressureReadings(breweryId, connectionId),
                        connections.findConsumption(breweryId, connectionId)));
    }
}
