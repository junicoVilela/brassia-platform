package br.com.brew.brassia.sales.domain;

import br.com.brew.brassia.shared.money.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * O teto de compromisso em aberto de um cliente (SAL-003).
 *
 * <p><strong>Ele mede recebível desde 2026-08-18</strong> (DEB-SAL-002). Até então media só compromisso:
 * a soma dos pedidos confirmados. Os dois erros apareciam no mesmo cliente — quem pagava continuava com o
 * limite ocupado, e um pedido entregue e não pago saía da conta. Com a baixa de pagamento, o que ocupa o
 * teto é <em>o que o cliente deve</em>: pedidos confirmados e atendidos, menos os recebimentos, já
 * descontados os estornos.
 *
 * <p>O recebimento <strong>parcial conta na proporção do que entrou</strong>. Ignorá-lo faria um cliente
 * que pagou 90% ocupar o limite inteiro, e o vendedor recusaria a venda de alguém que está em dia. O
 * controle continua não substituindo análise de crédito: ele impede que um cliente acumule dívida além do
 * que a cervejaria aceita carregar — o caso real do bar pequeno pedindo mil caixas.
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
