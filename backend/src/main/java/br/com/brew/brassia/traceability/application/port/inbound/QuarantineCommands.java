package br.com.brew.brassia.traceability.application.port.inbound;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.domain.Quarantine;
import java.util.UUID;

/** Comandos da quarentena (FDS-002): abrir a investigação e encerrá-la com alçada. */
public interface QuarantineCommands {

    interface Open {
        Quarantine handle(UUID actorId, UUID breweryId, NodeType type, UUID nodeId, String reason);
    }

    interface Release {
        void handle(UUID actorId, UUID breweryId, UUID quarantineId, String justification);
    }
}
