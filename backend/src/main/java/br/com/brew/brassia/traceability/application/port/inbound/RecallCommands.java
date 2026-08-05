package br.com.brew.brassia.traceability.application.port.inbound;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.domain.Recall;
import java.util.UUID;

/** Comandos do recall (FDS-003): abrir, registrar comunicação e encerrar. */
public interface RecallCommands {

    interface Open {
        Recall handle(UUID actorId, UUID breweryId, NodeType type, UUID nodeId, String reason);
    }

    interface RecordNotification {
        void handle(UUID actorId, UUID breweryId, UUID recallId, UUID notificationId, String channel,
                String note);
    }

    interface Close {
        void handle(UUID actorId, UUID breweryId, UUID recallId, String summary);
    }
}
