package br.com.brew.brassia.production;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Os lotes que ainda estão em produção (QLT-001-A).
 *
 * <p>Existe porque uma varredura por cadência precisa saber sobre o que varrer, e as consultas publicadas
 * até aqui respondiam por <em>um</em> lote de cada vez — o que serve a quem já tem o identificador, e não
 * a quem procura o que está atrasado.
 *
 * <p><strong>Só lotes abertos.</strong> Cadência não se cobra de lote encerrado: a medição que faltou já
 * não pode ser feita, e um alerta sobre ela seria ruído permanente numa lista que precisa ser lida todo
 * dia para valer alguma coisa.
 */
public interface OpenBatchLookup {

    List<OpenBatch> openBatches(UUID breweryId);

    /**
     * @param recipeId usado para saber quais planos de controle se aplicam — plano sem receita vale para
     *                 todos, e é assim que controles da casa (água, higiene) alcançam qualquer lote
     * @param startedAt início do lote: é dele que a primeira janela de cadência conta, quando ainda não
     *                  houve medição nenhuma
     */
    record OpenBatch(UUID batchId, String code, UUID recipeId, Instant startedAt) {}
}
