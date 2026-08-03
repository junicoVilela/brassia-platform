package br.com.brew.brassia.quality.application.port.inbound;

import java.time.LocalDate;
import java.util.UUID;

/** Comandos do tratamento de não conformidade (QLT-002). */
public final class NonConformityCommands {

    private NonConformityCommands() {
    }

    public interface Open {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String title, String description,
                String source, UUID deviationId, String severity, LocalDate containmentDueOn,
                LocalDate investigationDueOn, LocalDate verificationDueOn) {}
    }

    public interface Contain {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID nonConformityId, String description) {}
    }

    public interface Investigate {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID nonConformityId, String rootCause,
                String method) {}
    }

    public interface PlanAction {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID nonConformityId, String kind, String description,
                String owner, LocalDate dueOn) {}
    }

    public interface CompleteAction {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID nonConformityId, UUID actionId) {}
    }

    /** Ineficaz devolve à fase de ação; não encerra. */
    public interface Verify {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID nonConformityId, boolean effective,
                String evidence) {}
    }

    /** Encerrar exige verificação eficaz, e fecha o desvio de origem quando houver. */
    public interface Close {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID nonConformityId) {}
    }
}
