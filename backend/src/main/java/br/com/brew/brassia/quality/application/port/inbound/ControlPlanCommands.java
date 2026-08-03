package br.com.brew.brassia.quality.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/** Comandos do plano de controle (QLT-001). */
public final class ControlPlanCommands {

    private ControlPlanCommands() {
    }

    public interface Create {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String name, UUID recipeId,
                String stage) {}
    }

    public interface Amend {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, String name, UUID recipeId, String stage) {}
    }

    public interface AddPoint {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, String parameter, BigDecimal min,
                BigDecimal max, BigDecimal target, String unit, String frequencyKind, Integer everyHours,
                String action, String severity, boolean critical) {}
    }

    public interface RemovePoint {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, UUID pointId) {}
    }

    /** Publicar congela a versão: a partir daí o plano julga e não muda mais. */
    public interface Publish {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId) {}
    }

    /** Nova versão a partir de uma publicada; a anterior segue intacta como histórico. */
    public interface NewVersion {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId) {}
    }
}
