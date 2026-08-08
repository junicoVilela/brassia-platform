package br.com.brew.brassia.ai.application.port.outbound;

import br.com.brew.brassia.ai.domain.ModelInvocation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O livro das chamadas ao modelo (AIA-001): só acrescenta, nunca corrige.
 *
 * <p><strong>Grava fora da transação de quem chamou.</strong> Uma chamada ao modelo custou dinheiro
 * de verdade no instante em que aconteceu; se o registro dela vivesse na transação do comando que a
 * pediu, todo comando que falhasse depois apagaria a prova do gasto — e o orçamento passaria a
 * proteger contra um consumo que ele não vê. Dinheiro que saiu não volta com rollback.
 */
public interface ModelInvocationLedger {

    /** Registra a chamada em transação própria, independente do comando que a originou. */
    void record(ModelInvocation invocation);

    /**
     * Soma dos custos desta cervejaria desde o instante dado.
     *
     * <p>O gasto do mês é contado do ledger, não guardado num contador: dois números sobre o mesmo
     * fato divergem, e o que vale é o que aconteceu.
     */
    BigDecimal spentSince(UUID breweryId, Instant since);

    /** Últimas chamadas desta cervejaria, para quem opera entender o que está acontecendo. */
    List<ModelInvocation> recent(UUID breweryId, int limit);
}
