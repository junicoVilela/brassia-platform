package br.com.brew.brassia.sales.domain;

import br.com.brew.brassia.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um recebimento contra um pedido (DEB-SAL-002).
 *
 * <p><strong>É evento, e não saldo.</strong> Um campo "valor pago" no pedido responderia "quanto falta"
 * e perderia "quem pagou o quê, e quando" — que é a pergunta de qualquer conferência com o financeiro. E
 * um saldo que se sobrescreve não se audita.
 *
 * <p><strong>Estorno é evento compensatório, e não edição.</strong> Um recebimento lançado errado —
 * cliente trocado, valor digitado a mais — não se apaga: registra-se o estorno, e os dois ficam. É o
 * mesmo princípio da prova de entrega da LOG-002, e pela mesma razão: o registro que se reescreve parece
 * original e diz outra coisa.
 *
 * <p><strong>Recebimento parcial é normal, e não exceção.</strong> Metade na entrega e metade em trinta
 * dias é como boa parte do comércio funciona; exigir o valor cheio faria o operador lançar o que não
 * recebeu para o sistema parar de reclamar.
 */
public final class Payment {

    private final UUID id;
    private final UUID breweryId;
    private final UUID orderId;
    private final Money amount;
    private final LocalDate receivedOn;
    private final String method;
    private final String note;
    private final UUID recordedBy;
    private final Instant recordedAt;
    /** Quando este lançamento estorna outro: o original continua de pé, e este explica o que mudou. */
    private final UUID reversesPaymentId;

    private Payment(UUID id, UUID breweryId, UUID orderId, Money amount, LocalDate receivedOn,
            String method, String note, UUID recordedBy, Instant recordedAt,
            UUID reversesPaymentId) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.orderId = Objects.requireNonNull(orderId, "pedido");
        this.amount = Objects.requireNonNull(amount, "valor");
        this.receivedOn = Objects.requireNonNull(receivedOn, "data do recebimento");
        this.method = requireMethod(method);
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.recordedBy = Objects.requireNonNull(recordedBy, "quem lançou");
        this.recordedAt = Objects.requireNonNull(recordedAt);
        this.reversesPaymentId = reversesPaymentId;
        if (!amount.isPositive()) {
            // Valor negativo seria estorno disfarçado de recebimento, e a soma bateria sem que ninguém
            // conseguisse explicar de onde veio.
            throw new IllegalArgumentException("o recebimento deve ser positivo");
        }
    }

    public static Payment received(UUID id, UUID breweryId, UUID orderId, Money amount,
            LocalDate receivedOn, String method, String note, UUID recordedBy) {
        return new Payment(id, breweryId, orderId, amount, receivedOn, method, note, recordedBy,
                Instant.now(), null);
    }

    /**
     * O estorno de um recebimento lançado errado.
     *
     * <p>Ele nasce com o <strong>mesmo valor</strong> do original: estornar parte de um lançamento seria
     * corrigir o valor, e correção de valor se faz estornando inteiro e lançando de novo — senão a soma
     * passa a depender da ordem em que alguém leu as linhas.
     */
    public static Payment reversalOf(UUID id, Payment original, String reason, UUID recordedBy,
            LocalDate receivedOn) {
        if (original.reversesPaymentId != null) {
            throw new IllegalArgumentException("estorne o recebimento original, e não o estorno");
        }
        if (reason == null || reason.isBlank()) {
            // "Estornado" sem motivo deixa quem confere seis meses depois sem saber se foi engano de
            // digitação, cheque devolvido ou pedido cancelado — e as três levam a conversas diferentes.
            throw new IllegalArgumentException("o estorno precisa de motivo");
        }
        return new Payment(id, original.breweryId, original.orderId, original.amount, receivedOn,
                original.method, reason, recordedBy, Instant.now(), original.id);
    }

    public static Payment reconstitute(UUID id, UUID breweryId, UUID orderId, Money amount,
            LocalDate receivedOn, String method, String note, UUID recordedBy, Instant recordedAt,
            UUID reversesPaymentId) {
        return new Payment(id, breweryId, orderId, amount, receivedOn, method, note, recordedBy,
                recordedAt, reversesPaymentId);
    }

    public boolean isReversal() {
        return reversesPaymentId != null;
    }

    /** O quanto este lançamento move o saldo: negativo quando estorna. */
    public Money signedAmount() {
        return isReversal() ? new Money(amount.amount().negate(), amount.currency()) : amount;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID orderId() {
        return orderId;
    }

    public Money amount() {
        return amount;
    }

    public LocalDate receivedOn() {
        return receivedOn;
    }

    public String method() {
        return method;
    }

    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    public UUID recordedBy() {
        return recordedBy;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public Optional<UUID> reversesPaymentId() {
        return Optional.ofNullable(reversesPaymentId);
    }

    private static String requireMethod(String method) {
        if (method == null || method.isBlank()) {
            // Sem o meio, a conciliação com o extrato vira adivinhação: "R$ 1.200 no dia 12" existe três
            // vezes num extrato movimentado.
            throw new IllegalArgumentException("o recebimento precisa do meio de pagamento");
        }
        return method.trim();
    }
}
