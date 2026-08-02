package br.com.brew.brassia.gas.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/** Comandos da linha de gás (GAS-001). */
public final class ConnectionCommands {

    private ConnectionCommands() {
    }

    /** Monta a linha; a recusa lista todos os impedimentos de cilindro e rede de uma vez. */
    public interface Connect {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID cylinderId, UUID regulatorId, UUID manifoldId,
                UUID pointOfUseEquipmentId, BigDecimal workingPressureBar) {}
    }

    /** Teste de vazamento — é ele que libera a linha para servir. */
    public interface RecordLeakTest {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID connectionId, boolean passed, String method,
                BigDecimal pressureDropBar, String note) {}
    }

    /** Registra a pressão medida; sobrepressão preserva a medição e bloqueia a linha. */
    public interface RecordPressure {
        Result handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID connectionId, BigDecimal bar, BigDecimal tempC) {}

        record Result(UUID readingId, boolean overPressure, String status) {}
    }

    public interface RecordConsumption {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID connectionId, BigDecimal kg, String reason) {}
    }

    public interface Disconnect {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID connectionId, String reason) {}
    }
}
