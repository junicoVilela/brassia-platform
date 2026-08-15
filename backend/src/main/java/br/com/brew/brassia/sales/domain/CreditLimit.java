package br.com.brew.brassia.sales.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * O teto de compromisso em aberto de um cliente (SAL-003).
 *
 * <p><strong>Ele mede compromisso, e não recebível — a diferença importa e está aqui por escrito.</strong>
 * Um limite de crédito de verdade compara o teto com o que o cliente <em>deve</em>, e para isso é preciso
 * saber o que foi pago. A plataforma não tem baixa de pagamento (está fora do escopo da sprint), então o
 * que dá para medir é o que foi <em>prometido e ainda não entregue</em>: a soma dos pedidos confirmados.
 *
 * <p>A consequência prática, que ninguém deve descobrir sozinho: <strong>um pedido entregue e não pago
 * sai da conta.</strong> O controle funciona para impedir que um cliente acumule promessas além do que a
 * cervejaria aceita carregar — que é o caso real do bar pequeno pedindo mil caixas —, e não substitui
 * uma análise de crédito. Ver DEB-SAL-002.
 *
 * <p><strong>Sem limite é o padrão, e é seguro.</strong> Não recusar por falta de decisão é reversível;
 * recusar um pedido bom porque alguém chutou um teto não é — o cliente vai comprar de outro.
 */
public record CreditLimit(Money ceiling) {

    public CreditLimit {
        if (ceiling != null && !ceiling.isPositive()) {
            // Teto zero bloquearia toda compra, o que não é limite de crédito e sim cliente suspenso —
            // e suspender é decisão diferente, que se toma desativando o cliente.
            throw new IllegalArgumentException("o limite de crédito deve ser positivo");
        }
    }

    public static CreditLimit none() {
        return new CreditLimit(null);
    }

    public static CreditLimit of(Money ceiling) {
        return new CreditLimit(Objects.requireNonNull(ceiling, "teto"));
    }

    public boolean isDefined() {
        return ceiling != null;
    }

    public Optional<Money> ceilingAmount() {
        return Optional.ofNullable(ceiling);
    }

    /**
     * Se um pedido de {@code novo} cabe, dado o que já está comprometido.
     *
     * <p>Sem limite, tudo cabe. Com limite, a comparação é entre moedas iguais — comparar real com dólar
     * sem taxa produziria uma decisão de crédito baseada num número que não existe.
     */
    public boolean fits(Money committed, Money novo) {
        Objects.requireNonNull(committed, "comprometido");
        Objects.requireNonNull(novo, "novo pedido");
        if (ceiling == null) {
            return true;
        }
        return committed.plus(novo).compareTo(ceiling) <= 0;
    }
}
