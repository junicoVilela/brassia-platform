package br.com.brew.brassia.packaging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Que lotes acabados podem ser vendidos (SAL-001-B).
 *
 * <p><strong>A definição, decidida pelo mantenedor em 2026-08-15:</strong> um lote é vendável quando está
 * <strong>liberado pela qualidade</strong>, <strong>dentro da validade</strong> e <strong>não bloqueado
 * por quarentena</strong>. As três condições, e não duas: vender cerveja que a própria casa já sabe estar
 * sob suspeita seria descobrir o problema na expedição, depois de a venda estar prometida.
 *
 * <p><strong>Quem compõe as três condições é este módulo, e não quem pergunta.</strong> O lote acabado, a
 * validade (FSL-001) e a consulta de quarentena já estão todos ao alcance de {@code packaging} — ele já
 * depende de {@code traceability}. Se {@code sales} compusesse, precisaria depender de três módulos para
 * responder uma pergunta só, e cada novo critério de venda viraria uma dependência nova lá.
 *
 * <p><strong>O resultado diz por que NÃO é vendável.</strong> Um booleano faria a tela dizer "não
 * disponível" e o operador ligar para a qualidade perguntar o motivo. O impedimento nomeado deixa claro
 * se falta assinatura, se a validade venceu ou se há quarentena — e as três levam a ações diferentes.
 */
public interface SellableLotLookup {

    /**
     * Lotes vendáveis de um produto, entendido como o par (receita, embalagem).
     *
     * <p>Só os vendáveis: quem monta um pedido precisa da lista do que pode prometer, e misturar o que
     * não pode com um aviso ao lado convida ao erro de clicar no primeiro.
     */
    List<SellableLot> sellableLots(UUID breweryId, UUID recipeId, UUID containerId, LocalDate on);

    /** O estado de um lote específico, vendável ou não, com o impedimento quando houver. */
    Optional<LotSaleStatus> statusOf(UUID breweryId, UUID finishedLotId, LocalDate on);

    /**
     * @param bestBefore validade que vale — o override humano vence a recomendação, como na FSL-001.
     *                   Nulo quando não há evidência de oxigênio nem decisão registrada, e nesse caso o
     *                   lote não é vendável: validade desconhecida não é validade em dia
     */
    record SellableLot(UUID finishedLotId, String code, String batchCode, int units,
            BigDecimal containerVolumeMl, LocalDate packagedOn, LocalDate bestBefore) {}

    /**
     * @param blocker vazio quando o lote é vendável
     */
    record LotSaleStatus(UUID finishedLotId, String code, boolean sellable, Optional<Blocker> blocker,
            LocalDate bestBefore) {}

    /**
     * @param code    código estável do impedimento: {@code not_released}, {@code expired},
     *                {@code shelf_life_unknown}, {@code quarantined} ou {@code quarantine_suspected}
     * @param message frase pronta para o operador, que diz o que fazer e não só o que houve
     */
    record Blocker(String code, String message) {}
}
