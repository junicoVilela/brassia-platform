package br.com.brew.brassia.sales.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sales.application.port.inbound.PaymentCommands;
import br.com.brew.brassia.sales.application.port.outbound.PaymentRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderRepository;
import br.com.brew.brassia.sales.domain.Payment;
import br.com.brew.brassia.sales.domain.UnknownProductException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebimentos do pedido (DEB-SAL-002).
 *
 * <p><strong>Não há PUT nem DELETE.</strong> Recebimento lançado errado se estorna, e o estorno é outro
 * lançamento: os dois ficam. Corrigir por cima faria a linha parecer original dizendo outra coisa — e é
 * exatamente essa linha que alguém confere com o extrato seis meses depois.
 */
@RestController
@RequestMapping("/api/v1/sales")
final class PaymentController {

    private final PaymentCommands commands;
    private final PaymentRepository payments;
    private final SalesOrderRepository orders;
    private final AuditTrail audit;

    PaymentController(PaymentCommands commands, PaymentRepository payments,
            SalesOrderRepository orders, AuditTrail audit) {
        this.commands = Objects.requireNonNull(commands);
        this.payments = Objects.requireNonNull(payments);
        this.orders = Objects.requireNonNull(orders);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping("/orders/{orderId}/payments")
    OrderPaymentsView list(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID orderId) {
        principal.requirePermission("sales.order.read");
        var brewery = principal.requireBrewery();
        var order = orders.find(brewery, orderId)
                .orElseThrow(() -> new UnknownProductException("o pedido", orderId));
        var lancamentos = payments.ofOrder(brewery, orderId);
        var total = order.total();
        var recebido = lancamentos.stream().map(p -> p.signedAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // O saldo vai calculado na resposta porque é a pergunta que a tela faz: "quanto falta". Deixar
        // a soma para o cliente faria cada tela decidir sozinha o que fazer com os estornos.
        return new OrderPaymentsView(orderId, total.toMinorUnit(), recebido.setScale(2),
                total.toMinorUnit().subtract(recebido.setScale(2)), total.currency(),
                lancamentos.stream().map(PaymentController::view).toList());
    }

    @PostMapping("/orders/{orderId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> record(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID orderId, @Valid @RequestBody RecordPaymentRequest request) {
        principal.requirePermission("sales.payment.record");
        var brewery = principal.requireBrewery();
        var id = commands.record(brewery, principal.userId(), new PaymentCommands.RecordPayment(
                orderId, request.amount(), request.currency(), request.receivedOn(), request.method(),
                request.note()));
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.payment.record",
                "sales.payment", id.toString(),
                Map.of("orderId", orderId.toString(), "amount", request.amount().toPlainString(),
                        "currency", request.currency(), "method", request.method())));
        return Map.of("id", id);
    }

    /** Estornar é crítico: tira dinheiro da conta e devolve limite ao cliente. */
    @PostMapping("/payments/{id}/reversal")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> reverse(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody ReversalRequest request) {
        principal.requirePermission("sales.payment.reverse");
        var brewery = principal.requireBrewery();
        var reversalId = commands.reverse(brewery, principal.userId(), id, request.reason());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.payment.reverse",
                "sales.payment", reversalId.toString(),
                Map.of("reversesPaymentId", id.toString(), "reason", request.reason())));
        return Map.of("id", reversalId);
    }

    private static PaymentView view(Payment p) {
        return new PaymentView(p.id(), p.amount().toMinorUnit(), p.amount().currency(), p.receivedOn(),
                p.method(), p.note().orElse(null), p.recordedBy(), p.recordedAt(), p.isReversal(),
                p.reversesPaymentId().orElse(null));
    }

    record RecordPaymentRequest(@NotNull @Positive BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency, LocalDate receivedOn,
            @NotBlank @Size(max = 40) String method, @Size(max = 500) String note) {}

    record ReversalRequest(@NotBlank @Size(max = 500) String reason) {}

    record OrderPaymentsView(UUID orderId, BigDecimal total, BigDecimal received,
            BigDecimal outstanding, String currency, List<PaymentView> payments) {}

    record PaymentView(UUID id, BigDecimal amount, String currency, LocalDate receivedOn, String method,
            String note, UUID recordedBy, Instant recordedAt, boolean reversal,
            UUID reversesPaymentId) {}
}
