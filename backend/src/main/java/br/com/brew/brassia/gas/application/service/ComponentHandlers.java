package br.com.brew.brassia.gas.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.gas.application.port.inbound.ComponentCommands;
import br.com.brew.brassia.gas.application.port.outbound.GasNetworkComponentRepository;
import br.com.brew.brassia.gas.domain.ComponentKind;
import br.com.brew.brassia.gas.domain.GasNetworkComponent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Cadastro de reguladores e manifolds (GAS-001). */
public final class ComponentHandlers {

    private ComponentHandlers() {
    }

    public static final class Register implements ComponentCommands.Register {

        private final GasNetworkComponentRepository components;
        private final AuditTrail audit;

        public Register(GasNetworkComponentRepository components, AuditTrail audit) {
            this.components = Objects.requireNonNull(components);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            if (components.existsByCode(command.breweryId(), command.code())) {
                throw new IllegalStateException("já existe componente com o código " + command.code());
            }
            var component = GasNetworkComponent.register(command.breweryId(), ComponentKind.of(command.kind()),
                    command.code(), command.name(), command.maxPressureBar(), command.setPressureBar());
            components.insert(component);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.component.register",
                    "gas.component", component.id().toString(),
                    Map.of("code", component.code(), "kind", component.kind().name(),
                            "maxPressureBar", component.maxPressureBar().toPlainString())));
            return component.id();
        }
    }

    public static final class Update implements ComponentCommands.Update {

        private final GasNetworkComponentRepository components;
        private final AuditTrail audit;

        public Update(GasNetworkComponentRepository components, AuditTrail audit) {
            this.components = Objects.requireNonNull(components);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var component = load(components, command.breweryId(), command.componentId());
            var version = component.version();
            component.update(command.name(), command.maxPressureBar(), command.setPressureBar());
            save(components, component, version);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.component.update",
                    "gas.component", component.id().toString(),
                    Map.of("code", component.code(),
                            "maxPressureBar", component.maxPressureBar().toPlainString())));
        }
    }

    public static final class SetActive implements ComponentCommands.SetActive {

        private final GasNetworkComponentRepository components;
        private final AuditTrail audit;

        public SetActive(GasNetworkComponentRepository components, AuditTrail audit) {
            this.components = Objects.requireNonNull(components);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var component = load(components, command.breweryId(), command.componentId());
            var version = component.version();
            if (command.active()) {
                component.activate();
            } else {
                component.deactivate();
            }
            save(components, component, version);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.component.set-active",
                    "gas.component", component.id().toString(),
                    Map.of("code", component.code(), "active", String.valueOf(command.active()))));
        }
    }

    private static GasNetworkComponent load(GasNetworkComponentRepository components, UUID breweryId,
            UUID componentId) {
        return components.findById(breweryId, componentId)
                .orElseThrow(() -> new IllegalArgumentException("componente inexistente"));
    }

    private static void save(GasNetworkComponentRepository components, GasNetworkComponent component,
            long expectedVersion) {
        if (!components.update(component, expectedVersion)) {
            throw new IllegalStateException("componente alterado por outra operação; tente novamente");
        }
    }
}
