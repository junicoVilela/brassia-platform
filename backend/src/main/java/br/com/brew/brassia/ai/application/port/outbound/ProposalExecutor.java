package br.com.brew.brassia.ai.application.port.outbound;

import br.com.brew.brassia.ai.domain.CommandProposal;
import java.util.UUID;

/**
 * Executa o comando de uma proposta confirmada (AIA-003 / DEB-AIA-002).
 *
 * <p>É porta de saída para que o caso de uso não conheça `costing` nem `sanitation`: ele sabe que uma
 * proposta confirmada vira comando, não quem executa cada um. A tradução de ação em chamada mora no
 * adapter, que é o único lugar que legitimamente conhece as duas pontas.
 *
 * <p><strong>Nem toda ação executa.</strong> {@code OPEN_NON_CONFORMITY} continua manual, e o motivo está
 * em {@link br.com.brew.brassia.ai.domain.ProposedAction}: executá-la exigiria inventar prazos de contenção,
 * investigação e verificação — regra de negócio que não é de quem escreve código. Para essas, executar é
 * não fazer nada, e quem confirma é levado à rota do comando como antes.
 */
public interface ProposalExecutor {

    /**
     * @param actorId quem confirmou — é ele que responde pelo comando, não quem pediu a análise
     * @throws RuntimeException qualquer falha do comando; propaga de propósito, para que a transação
     *         desfaça também a decisão. Consentimento gravado sem o efeito que ele autorizou é pior que
     *         nenhum dos dois: alguém acreditaria que o custo foi fechado.
     */
    void execute(CommandProposal proposal, UUID actorId);
}
