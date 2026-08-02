package br.com.brew.brassia.gas.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/** Comandos do cadastro de reguladores e manifolds (GAS-001). */
public final class ComponentCommands {

    private ComponentCommands() {
    }

    public interface Register {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String kind, String code, String name,
                BigDecimal maxPressureBar, BigDecimal setPressureBar) {}
    }

    public interface Update {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID componentId, String name, BigDecimal maxPressureBar,
                BigDecimal setPressureBar) {}
    }

    public interface SetActive {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID componentId, boolean active) {}
    }
}
