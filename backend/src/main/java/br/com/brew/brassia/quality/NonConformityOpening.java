package br.com.brew.brassia.quality;

import java.util.UUID;

/**
 * Abertura de não conformidade publicada para outros módulos (DEB-AIA-003).
 *
 * <p>Existe para o copiloto: a proposta `OPEN_NON_CONFORMITY` afirmava "abrir NC para o lote" e era a
 * única das três que não executava ao ser aceita. As duas barreiras registradas na Sprint 14 caíram —
 * a NC passou a referenciar lote, e os três prazos já vinham da política da casa (PRM-001) desde então.
 *
 * <p><strong>O que não está nesta porta é tão importante quanto o que está.</strong> Não há prazo, nem
 * código, nem status: prazo é regra de negócio que sai da severidade pela política, código é numerado
 * pelo sistema, e uma NC nasce aberta. Deixar qualquer um dos três entrar por aqui abriria caminho para
 * um chamador — inclusive a IA — decidir o que a cervejaria decidiu uma vez, na tela de parâmetros.
 */
public interface NonConformityOpening {

    /**
     * Abre a NC e devolve o identificador.
     *
     * @param batchId o lote de que ela fala — obrigatório aqui, ainda que opcional no modelo: quem chama
     *                esta porta está afirmando "para o lote", e sem o vínculo o registro sairia solto
     * @param origin  texto que vai para a descrição, dizendo de onde a NC veio. A rastreabilidade da
     *                decisão importa mais aqui do que numa abertura manual: meses depois, "quem abriu
     *                isto?" tem como resposta um copiloto, e a descrição é onde isso fica dito
     */
    UUID openForBatch(UUID breweryId, UUID actorId, UUID batchId, String title, String severity,
            String origin);
}
