package br.com.brew.brassia.ai.application.port.inbound;

import br.com.brew.brassia.ai.domain.AiBudget;
import java.math.BigDecimal;
import java.util.UUID;

/** Redefinir o teto de gasto com IA (AIA-001). */
public interface BudgetCommands {

    /**
     * Redefine o teto mensal da cervejaria.
     *
     * <p>É comando com autor e versão: alguém decide quanto a cervejaria aceita gastar com IA por
     * mês, e essa decisão é auditada. Baixar o teto abaixo do que já foi gasto é permitido e para as
     * chamadas do resto do mês — que é exatamente o que se quer de um freio.
     */
    AiBudget redefine(UUID actorId, UUID breweryId, BigDecimal monthlyLimit, long expectedVersion);
}
