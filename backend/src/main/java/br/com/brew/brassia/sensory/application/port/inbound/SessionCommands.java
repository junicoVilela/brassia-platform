package br.com.brew.brassia.sensory.application.port.inbound;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Comandos da sessão sensorial (SEN-001). */
public final class SessionCommands {

    private SessionCommands() {
    }

    public interface Create {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String purpose, LocalDate scheduledFor) {}
    }

    public interface Amend {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID sessionId, String purpose,
                LocalDate scheduledFor) {}
    }

    /** O código cego é sorteado pelo sistema; quem cadastra informa o lote. */
    public interface AddSample {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID sessionId, UUID batchId, String note) {}
    }

    public interface RemoveSample {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID sessionId, UUID sampleId) {}
    }

    public interface Open {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID sessionId) {}
    }

    public interface Close {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID sessionId) {}
    }

    /** Ficha do provador: imutável, uma por amostra. */
    public interface SubmitEvaluation {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID sessionId, UUID sampleId,
                Map<String, Integer> scores, List<String> descriptors, String note) {}
    }
}
