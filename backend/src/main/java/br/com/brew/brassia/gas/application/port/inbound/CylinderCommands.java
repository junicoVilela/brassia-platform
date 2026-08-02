package br.com.brew.brassia.gas.application.port.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Comandos do cilindro de gás (GAS-001). */
public final class CylinderCommands {

    private CylinderCommands() {
    }

    public interface Register {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String gasType, BigDecimal capacityKg,
                BigDecimal tareKg, BigDecimal contentKg, LocalDate requalificationDueOn, String location) {}
    }

    /** Bloqueio e desbloqueio são decisão humana; o bloqueio exige motivo. */
    public interface SetBlock {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID cylinderId, boolean blocked, String reason) {}
    }

    /** Nova requalificação; o vencimento precisa ser futuro. */
    public interface Requalify {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID cylinderId, LocalDate dueOn) {}
    }

    /** Recarga: a massa aferida volta ao cilindro. */
    public interface Refill {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID cylinderId, BigDecimal contentKg) {}
    }
}
