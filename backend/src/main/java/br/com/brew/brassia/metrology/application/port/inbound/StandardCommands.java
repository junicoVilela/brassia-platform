package br.com.brew.brassia.metrology.application.port.inbound;

import java.time.LocalDate;
import java.util.UUID;

/** Comandos do padrão de calibração (MTR-001). */
public final class StandardCommands {

    private StandardCommands() {
    }

    public interface Register {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String description,
                String certificateNumber, String issuer, String traceability, LocalDate validUntil) {}
    }

    /** Renova o certificado do padrão, preservando identidade e rastreabilidade. */
    public interface Renew {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID standardId, String certificateNumber, String issuer,
                LocalDate validUntil, LocalDate issuedOn) {}
    }
}
