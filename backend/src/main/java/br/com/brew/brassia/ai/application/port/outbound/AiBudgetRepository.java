package br.com.brew.brassia.ai.application.port.outbound;

import br.com.brew.brassia.ai.domain.AiBudget;
import java.util.UUID;

/** O teto de gasto por cervejaria (AIA-001). */
public interface AiBudgetRepository {

    /**
     * Teto da cervejaria com o gasto do mês corrente já somado.
     *
     * <p>Nunca vazio: sem linha cadastrada devolve o teto padrão da instalação, porque uma cervejaria
     * sem teto por esquecimento de cadastro é uma cervejaria sem proteção nenhuma.
     */
    AiBudget currentOf(UUID breweryId);

    /**
     * Grava o teto redefinido.
     *
     * @param expectedVersion versão lida por quem está gravando; {@code 0} quando ainda não havia linha
     * @throws br.com.brew.brassia.ai.domain.StaleAiBudgetException se alguém alterou nesse meio-tempo
     */
    AiBudget save(AiBudget budget, long expectedVersion);
}
