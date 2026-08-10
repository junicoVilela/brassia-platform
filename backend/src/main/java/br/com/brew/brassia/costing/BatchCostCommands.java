package br.com.brew.brassia.costing;

import java.util.UUID;

/**
 * Fechar o custo de um lote, publicado para outros módulos (CST-001 / DEB-AIA-002).
 *
 * <p><strong>Carrega o ator, e é a diferença que importa em relação à primeira porta de comando do
 * projeto.</strong> {@code fermentation.FermentationCommands} não tem ator porque telemetria é máquina
 * relatando o que mediu. Fechar custo é ato humano: alguém olha o número, aceita as lacunas declaradas e
 * assina. O ator viaja no comando e vai para a trilha de auditoria do custeio — sem ele, uma assinatura
 * ficaria sem dono, que é o oposto do que fechar um custo significa.
 *
 * <p><strong>Estreita de propósito.</strong> O custeio faz muito mais que fechar; publica-se um comando,
 * não o módulo. Cada porta publicada é superfície que outro módulo pode acionar, e superfície que ninguém
 * pediu é caminho que ninguém revisou.
 *
 * <p>Nomeada {@code BatchCostCommands} e não {@code CostCommands} porque já existe uma
 * {@code costing.application.port.inbound.CostCommands} interna: dois tipos de mesmo nome simples no mesmo
 * módulo obrigariam a qualificar as referências, e o leitor teria de conferir o import para saber qual
 * porta está olhando.
 */
public interface BatchCostCommands {

    /**
     * @param note justificativa de quem assina; pode ser nula
     * @throws IllegalStateException quando o custo já está fechado ou o lote não permite fechar
     */
    void close(UUID actorId, UUID breweryId, UUID batchId, String note);
}
