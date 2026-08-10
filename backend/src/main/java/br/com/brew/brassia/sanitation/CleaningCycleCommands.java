package br.com.brew.brassia.sanitation;

import java.util.UUID;

/**
 * Iniciar um ciclo de limpeza, publicado para outros módulos (SAN-001 / DEB-AIA-002).
 *
 * <p><strong>Inicia, não agenda</strong> — e a distinção custou uma correção de rótulo. A proposta do
 * copiloto dizia "Programar ciclo de limpeza", mas o módulo de sanitização não tem agendamento: um ciclo
 * existe a partir do momento em que começa. Manter "programar" num botão que <em>inicia</em> faria a pessoa
 * consentir com uma coisa e outra acontecer, que é exatamente o que a confirmação humana existe para
 * impedir.
 *
 * <p><strong>Carrega o ator porque há um.</strong> Quem confirma é quem passa a responder pelo ciclo — é o
 * nome que aparece no registro de execução e na liberação. Ver a nota em
 * {@link br.com.brew.brassia.costing.BatchCostCommands} sobre por que isto difere da porta de fermentação.
 *
 * <p>O procedimento vem por código, não por id: é assim que ele é conhecido por quem opera, e é o que a
 * proposta consegue nomear sem inventar identificador.
 */
public interface CleaningCycleCommands {

    /**
     * @return o id do ciclo iniciado
     * @throws IllegalArgumentException equipamento inexistente ou procedimento não publicado
     */
    UUID start(UUID actorId, UUID breweryId, UUID equipmentId, String procedureCode);
}
