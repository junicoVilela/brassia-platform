package br.com.brew.brassia.fieldfeedback.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O destino de uma ação exigida: atendida ou dispensada (FLD-001).
 *
 * <p>Dispensar exige justificativa e fica com autor e data. Sem isso, a dispensa seria indistinguível de
 * esquecimento — e o histórico mostraria dezenas de reclamações graves "sem quarentena" sem dizer se
 * alguém decidiu isso ou se ninguém olhou.
 */
public record ActionOutcome(
        RequiredAction action,
        boolean fulfilled,
        UUID referenceId,
        String justification,
        UUID decidedBy,
        Instant decidedAt) {

    public ActionOutcome {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(decidedBy, "decidedBy");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (fulfilled) {
            // Atendida sem referência seria uma afirmação sem contra o que conferir: qual quarentena?
            Objects.requireNonNull(referenceId, "ação atendida precisa apontar para o registro criado");
        } else {
            var text = Objects.requireNonNull(justification, "dispensa precisa de justificativa").trim();
            if (text.length() < 15) {
                // O mínimo não é burocracia: "n/a" e "ok" são o que se escreve quando não se decidiu nada.
                throw new IllegalArgumentException(
                        "a justificativa da dispensa precisa dizer por quê, não só que foi dispensada");
            }
            justification = text;
        }
    }

    public static ActionOutcome fulfilled(RequiredAction action, UUID referenceId, UUID by, Instant at) {
        return new ActionOutcome(action, true, referenceId, null, by, at);
    }

    public static ActionOutcome waived(RequiredAction action, String justification, UUID by, Instant at) {
        return new ActionOutcome(action, false, null, justification, by, at);
    }

    public Optional<UUID> reference() {
        return Optional.ofNullable(referenceId);
    }
}
