package br.com.brew.brassia.equipment;

import java.time.Instant;
import java.util.UUID;

/**
 * Escrita publicada do estado de limpeza (CLN-004-A).
 *
 * <p><strong>Não existe "marcar limpo".</strong> Existe "foi limpo por este ciclo", e o ciclo é
 * obrigatório no contrato. A diferença não é de nome: um método sem ciclo seria o caminho usado no dia de
 * correria, e "limpo" passaria a significar "alguém clicou" em vez de "há evidência de sanitização, com
 * concentração, temperatura e ATP medidos".
 *
 * <p><strong>Por que porta e não evento.</strong> A primeira tentativa foi um listener em `equipment`
 * consumindo `CleaningCycleReleased` — e ela criou ciclo entre os módulos, porque `sanitation` já depende
 * de `equipment` desde a CLN-003 (o ciclo valida o equipamento ao iniciar). Invertendo, a dependência
 * continua numa direção só. O ganho não é apenas arquitetural: chamada dentro da transação da liberação
 * elimina a janela em que um ciclo aparece liberado com o tanque ainda sujo.
 */
public interface EquipmentCleanlinessCommands {

    /**
     * Um ciclo verificado e liberado deixou o equipamento limpo.
     *
     * <p>Equipamento inexistente é ignorado em silêncio: o ciclo continua sendo registro válido do que foi
     * feito, e derrubar a liberação por causa do efeito colateral dela seria perder o registro por causa
     * da consequência.
     */
    void markCleanedByCycle(UUID breweryId, UUID equipmentId, UUID cycleId, UUID actorId, Instant releasedAt);
}
