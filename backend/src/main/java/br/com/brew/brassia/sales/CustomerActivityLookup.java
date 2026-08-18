package br.com.brew.brassia.sales;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Quando o cliente comprou pela última vez (DUV-CRM-001).
 *
 * <p><strong>Consulta publicada, na direção padrão do ADR-0016</strong>: quem tem o dado do pedido é
 * vendas, então quem precisa saber pergunta aqui. O CRM não lê tabela de vendas para calcular retenção.
 *
 * <p><strong>Cancelado conta.</strong> Aqui a pergunta é sobre <em>relacionamento</em>, e não sobre
 * receita: um pedido que o cliente fez e cancelou é contato dele com a casa, e apagar o dado pessoal de
 * quem falou com a cervejaria mês passado porque a venda não fechou seria confundir "não comprou" com
 * "sumiu". É o oposto do {@link OrderHistoryLookup}, que exclui cancelado de propósito — lá a pergunta é
 * quanto se vendeu.
 */
public interface CustomerActivityLookup {

    /**
     * A data do pedido mais recente do cliente, cancelado ou não.
     *
     * <p>Vazio quando ele nunca comprou — e vazio <strong>não</strong> significa "pode anonimizar": quem
     * pergunta compõe essa data com as outras que conhece.
     */
    Optional<LocalDate> lastOrderOn(UUID breweryId, UUID customerId);
}
