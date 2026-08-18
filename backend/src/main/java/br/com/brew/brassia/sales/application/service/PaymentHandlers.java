package br.com.brew.brassia.sales.application.service;

import br.com.brew.brassia.sales.application.port.inbound.PaymentCommands;
import br.com.brew.brassia.sales.application.port.outbound.PaymentRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderRepository;
import br.com.brew.brassia.sales.domain.OrderNotChangeableException;
import br.com.brew.brassia.sales.domain.OrderStatus;
import br.com.brew.brassia.sales.domain.Payment;
import br.com.brew.brassia.sales.domain.PaymentExceedsBalanceException;
import br.com.brew.brassia.sales.domain.SalesOrder;
import br.com.brew.brassia.sales.domain.UnknownProductException;
import br.com.brew.brassia.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do recebimento (DEB-SAL-002). */
public class PaymentHandlers implements PaymentCommands {

    private final PaymentRepository payments;
    private final SalesOrderRepository orders;

    public PaymentHandlers(PaymentRepository payments, SalesOrderRepository orders) {
        this.payments = Objects.requireNonNull(payments);
        this.orders = Objects.requireNonNull(orders);
    }

    @Override
    @Transactional
    public UUID record(UUID breweryId, UUID actorId, RecordPayment command) {
        var order = orders.find(breweryId, command.orderId())
                .orElseThrow(() -> new UnknownProductException("o pedido", command.orderId()));
        if (order.status() == OrderStatus.CANCELLED) {
            // Baixar pagamento de pedido cancelado esconderia o problema real: ou o cancelamento está
            // errado, ou o dinheiro entrou por outro motivo — e nenhum dos dois se resolve aqui.
            throw new OrderNotChangeableException(order.status());
        }

        var amount = new Money(command.amount(), command.currency());
        var outstanding = outstanding(order, payments.ofOrder(breweryId, order.id()));
        // O compareTo já recusa moeda diferente: um recebimento em dólar num pedido em real não é
        // recebimento parcial, é outra conversa.
        if (amount.compareTo(outstanding) > 0) {
            throw new PaymentExceedsBalanceException(outstanding.toMinorUnit(), amount.toMinorUnit(),
                    amount.currency());
        }

        var receivedOn = command.receivedOn() == null
                ? LocalDate.now(ZoneOffset.UTC) : command.receivedOn();
        var payment = Payment.received(UUID.randomUUID(), breweryId, order.id(), amount, receivedOn,
                command.method(), command.note(), actorId);
        payments.record(payment);
        return payment.id();
    }

    @Override
    @Transactional
    public UUID reverse(UUID breweryId, UUID actorId, UUID paymentId, String reason) {
        var original = payments.find(breweryId, paymentId)
                .orElseThrow(() -> new UnknownProductException("o recebimento", paymentId));
        // A garantia de "um estorno por recebimento" é o índice único; aqui só nasce o lançamento
        // compensatório. A data do estorno é hoje, e não a do original: o dinheiro voltou hoje.
        var reversal = Payment.reversalOf(UUID.randomUUID(), original, reason, actorId,
                LocalDate.now(ZoneOffset.UTC));
        payments.record(reversal);
        return reversal.id();
    }

    /** O que o pedido ainda deve: total menos o que entrou, já descontados os estornos. */
    private static Money outstanding(SalesOrder order, List<Payment> lancamentos) {
        var devido = order.total();
        var recebido = new Money(BigDecimal.ZERO, devido.currency());
        for (var p : lancamentos) {
            recebido = recebido.plus(p.signedAmount());
        }
        return new Money(devido.amount().subtract(recebido.amount()), devido.currency());
    }
}
