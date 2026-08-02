package br.com.brew.brassia.gas.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.gas.application.port.inbound.ConnectionCommands;
import br.com.brew.brassia.gas.application.port.outbound.GasConnectionRepository;
import br.com.brew.brassia.gas.application.port.outbound.GasCylinderRepository;
import br.com.brew.brassia.gas.application.port.outbound.GasNetworkComponentRepository;
import br.com.brew.brassia.gas.domain.ComponentKind;
import br.com.brew.brassia.gas.domain.GasConnection;
import br.com.brew.brassia.gas.domain.GasConnectionBlockedException;
import br.com.brew.brassia.gas.domain.GasCylinder;
import br.com.brew.brassia.gas.domain.GasNetworkComponent;
import br.com.brew.brassia.gas.domain.LeakTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Operação da linha de gás (GAS-001): cilindro → regulador → (manifold) → ponto de uso.
 *
 * <p>Conectar reúne <strong>todos</strong> os impedimentos antes de montar a linha — cilindro
 * vencido, bloqueado, vazio ou em uso, componente errado ou inativo, ponto de uso ocupado —
 * porque descobrir um problema por tentativa significa montar e desmontar a linha várias vezes.
 * A linha nasce pendente: só serve depois de um teste de vazamento aprovado.
 */
public final class ConnectionHandlers {

    private ConnectionHandlers() {
    }

    public static final class Connect implements ConnectionCommands.Connect {

        private final GasConnectionRepository connections;
        private final GasCylinderRepository cylinders;
        private final GasNetworkComponentRepository components;
        private final EquipmentProfileLookup equipment;
        private final AuditTrail audit;

        public Connect(GasConnectionRepository connections, GasCylinderRepository cylinders,
                GasNetworkComponentRepository components, EquipmentProfileLookup equipment, AuditTrail audit) {
            this.connections = Objects.requireNonNull(connections);
            this.cylinders = Objects.requireNonNull(cylinders);
            this.components = Objects.requireNonNull(components);
            this.equipment = Objects.requireNonNull(equipment);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var cylinder = cylinders.findForUpdate(command.breweryId(), command.cylinderId())
                    .orElseThrow(() -> new IllegalArgumentException("cilindro inexistente"));
            if (equipment.find(command.breweryId(), command.pointOfUseEquipmentId()).isEmpty()) {
                throw new IllegalArgumentException("ponto de uso inexistente");
            }

            var today = LocalDate.now(ZoneOffset.UTC);
            var blockers = new ArrayList<>(cylinder.blockers(today));
            var regulator = component(command.regulatorId(), ComponentKind.REGULATOR, command.breweryId(), blockers);
            var manifold = command.manifoldId() == null
                    ? null
                    : component(command.manifoldId(), ComponentKind.MANIFOLD, command.breweryId(), blockers);
            if (connections.hasOpenConnectionAtPoint(command.breweryId(), command.pointOfUseEquipmentId())) {
                blockers.add(new GasConnectionBlockedException.Blocker("point_of_use_occupied",
                        "O ponto de uso já tem um cilindro conectado."));
            }
            if (!blockers.isEmpty()) {
                throw new GasConnectionBlockedException(List.copyOf(blockers));
            }

            // Monta antes de ocupar o cilindro: pressão acima do teto da rede é recusa, não linha montada.
            var connection = GasConnection.connect(command.breweryId(), cylinder.id(), regulator, manifold,
                    command.pointOfUseEquipmentId(), command.workingPressureBar(), Instant.now(),
                    command.actorId());

            var version = cylinder.version();
            cylinder.connect(today);
            save(cylinders, cylinder, version);
            connections.insert(connection);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.connection.connect",
                    "gas.connection", connection.id().toString(),
                    Map.of("cylinderCode", cylinder.code(),
                            "workingPressureBar", connection.workingPressureBar().toPlainString(),
                            "networkMaxPressureBar", connection.networkMaxPressureBar().toPlainString())));
            return connection.id();
        }

        /** Carrega o componente exigindo o papel certo; acumula o impedimento em vez de interromper. */
        private GasNetworkComponent component(UUID componentId, ComponentKind expected, UUID breweryId,
                List<GasConnectionBlockedException.Blocker> blockers) {
            var role = expected == ComponentKind.REGULATOR ? "regulator" : "manifold";
            var label = expected == ComponentKind.REGULATOR ? "regulador" : "manifold";
            var found = components.findById(breweryId, componentId);
            if (found.isEmpty() || found.get().kind() != expected) {
                blockers.add(new GasConnectionBlockedException.Blocker(role + "_unknown",
                        "O " + label + " informado não existe neste cadastro."));
                return null;
            }
            if (!found.get().active()) {
                blockers.add(new GasConnectionBlockedException.Blocker(role + "_inactive",
                        "O " + label + " está desativado no cadastro."));
            }
            return found.get();
        }
    }

    public static final class RecordLeakTest implements ConnectionCommands.RecordLeakTest {

        private final GasConnectionRepository connections;
        private final AuditTrail audit;

        public RecordLeakTest(GasConnectionRepository connections, AuditTrail audit) {
            this.connections = Objects.requireNonNull(connections);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var connection = load(connections, command.breweryId(), command.connectionId());
            var version = connection.version();
            connection.recordLeakTest(new LeakTest(command.passed(), command.method(), command.pressureDropBar(),
                    command.note(), command.actorId(), Instant.now()));
            save(connections, connection, version);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.connection.leak-test",
                    "gas.connection", connection.id().toString(),
                    Map.of("passed", String.valueOf(command.passed()), "status", connection.status().name(),
                            "pressureDropBar", command.pressureDropBar().toPlainString())));
        }
    }

    public static final class RecordPressure implements ConnectionCommands.RecordPressure {

        private final GasConnectionRepository connections;
        private final AuditTrail audit;

        public RecordPressure(GasConnectionRepository connections, AuditTrail audit) {
            this.connections = Objects.requireNonNull(connections);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Result handle(Command command) {
            var connection = load(connections, command.breweryId(), command.connectionId());
            var version = connection.version();
            var overPressure = connection.evaluatePressure(command.bar());
            save(connections, connection, version);

            // A leitura é evidência: gravada inclusive quando denuncia sobrepressão e bloqueia a linha.
            var readingId = UUID.randomUUID();
            connections.insertPressureReading(readingId, command.breweryId(), connection.id(), command.bar(),
                    command.tempC(), overPressure, command.actorId(), Instant.now());

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.connection.pressure",
                    "gas.connection", connection.id().toString(),
                    Map.of("bar", command.bar().toPlainString(), "overPressure", String.valueOf(overPressure),
                            "status", connection.status().name())));
            return new Result(readingId, overPressure, connection.status().name());
        }
    }

    public static final class RecordConsumption implements ConnectionCommands.RecordConsumption {

        private final GasConnectionRepository connections;
        private final GasCylinderRepository cylinders;
        private final AuditTrail audit;

        public RecordConsumption(GasConnectionRepository connections, GasCylinderRepository cylinders,
                AuditTrail audit) {
            this.connections = Objects.requireNonNull(connections);
            this.cylinders = Objects.requireNonNull(cylinders);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var connection = load(connections, command.breweryId(), command.connectionId());
            connection.requireServing();

            var cylinder = cylinders.findForUpdate(command.breweryId(), connection.cylinderId())
                    .orElseThrow(() -> new IllegalStateException("cilindro da conexão não encontrado"));
            var version = cylinder.version();
            cylinder.consume(command.kg());
            save(cylinders, cylinder, version);
            connections.insertConsumption(UUID.randomUUID(), command.breweryId(), connection.id(), cylinder.id(),
                    command.kg(), command.reason(), command.actorId(), Instant.now());

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.connection.consumption",
                    "gas.connection", connection.id().toString(),
                    Map.of("cylinderCode", cylinder.code(), "kg", command.kg().toPlainString(),
                            "remainingKg", cylinder.contentKg().toPlainString())));
        }
    }

    public static final class Disconnect implements ConnectionCommands.Disconnect {

        private final GasConnectionRepository connections;
        private final GasCylinderRepository cylinders;
        private final AuditTrail audit;

        public Disconnect(GasConnectionRepository connections, GasCylinderRepository cylinders,
                AuditTrail audit) {
            this.connections = Objects.requireNonNull(connections);
            this.cylinders = Objects.requireNonNull(cylinders);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var connection = load(connections, command.breweryId(), command.connectionId());
            var connectionVersion = connection.version();
            connection.disconnect(command.reason(), Instant.now());
            save(connections, connection, connectionVersion);

            // Devolve o cilindro; vazio continua vazio até a recarga.
            var cylinder = cylinders.findForUpdate(command.breweryId(), connection.cylinderId())
                    .orElseThrow(() -> new IllegalStateException("cilindro da conexão não encontrado"));
            var cylinderVersion = cylinder.version();
            cylinder.release();
            save(cylinders, cylinder, cylinderVersion);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.connection.disconnect",
                    "gas.connection", connection.id().toString(),
                    Map.of("cylinderCode", cylinder.code(), "reason", connection.disconnectReason(),
                            "cylinderStatus", cylinder.status().name())));
        }
    }

    private static GasConnection load(GasConnectionRepository connections, UUID breweryId, UUID connectionId) {
        return connections.findForUpdate(breweryId, connectionId)
                .orElseThrow(() -> new IllegalArgumentException("conexão inexistente"));
    }

    private static void save(GasConnectionRepository connections, GasConnection connection, long expectedVersion) {
        if (!connections.update(connection, expectedVersion)) {
            throw new IllegalStateException("conexão alterada por outra operação; tente novamente");
        }
    }

    private static void save(GasCylinderRepository cylinders, GasCylinder cylinder, long expectedVersion) {
        if (!cylinders.update(cylinder, expectedVersion)) {
            throw new IllegalStateException("cilindro alterado por outra operação; tente novamente");
        }
    }
}
