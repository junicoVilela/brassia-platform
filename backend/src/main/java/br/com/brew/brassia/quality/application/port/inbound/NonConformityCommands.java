package br.com.brew.brassia.quality.application.port.inbound;

import java.time.LocalDate;
import java.util.UUID;

/** Comandos do tratamento de não conformidade (QLT-002). */
public final class NonConformityCommands {

    private NonConformityCommands() {
    }

    public interface Open {
        UUID handle(Command command);

        /**
         * @param code nulo ou em branco faz o sistema numerar (NC-AAAA-NNNN). Sempre foi digitado por
         *             quem abria, o que funciona com uma pessoa na frente da tela — um comando executado
         *             a partir de uma proposta não tem quem digite
         * @param batchId o lote de que a NC fala; nulo em NC de auditoria, fornecedor ou processo
         */
        record Command(UUID actorId, UUID breweryId, String code, String title, String description,
                String source, UUID deviationId, UUID batchId, String severity, LocalDate containmentDueOn,
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
