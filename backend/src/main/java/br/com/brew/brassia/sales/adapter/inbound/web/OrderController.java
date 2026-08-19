package br.com.brew.brassia.sales.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sales.application.port.inbound.OrderCommands;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderRepository;
import br.com.brew.brassia.sales.domain.CreditOverride;
import br.com.brew.brassia.sales.domain.SalesOrder;
import br.com.brew.brassia.sales.domain.UnknownProductException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pedidos, reservas e promessa de entrega (SAL-002).
 *
 * <p><strong>A chave de idempotência vem no cabeçalho {@code Idempotency-Key}</strong>, e não no corpo:
 * ela é sobre a requisição, não sobre o pedido. Repetir a chamada devolve o mesmo pedido — um duplo
 * clique ou um retry de rede não pode reservar o mesmo estoque duas vezes.
 *
 * <p><strong>Não há DELETE.</strong> Pedido cancela, e o cancelamento devolve as reservas na mesma
 * transação. Apagar a linha faria sumir o fato de que houve uma venda e que ela foi desfeita.
 */
@RestController
@RequestMapping("/api/v1/sales/orders")
final class OrderController {

    private final OrderCommands commands;
    private final SalesOrderRepository orders;
    private final AuditTrail audit;

    OrderController(OrderCommands commands, SalesOrderRepository orders, AuditTrail audit) {
        this.commands = Objects.requireNonNull(commands);
        this.orders = Objects.requireNonNull(orders);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping
    List<OrderView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sales.order.read");
        return orders.list(principal.requireBrewery()).stream().map(OrderController::view).toList();
    }

    @GetMapping("/{id}")
    OrderView get(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("sales.order.read");
        return orders.find(principal.requireBrewery(), id).map(OrderController::view)
                .orElseThrow(() -> new UnknownProductException("o pedido", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> place(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        principal.requirePermission("sales.order.manage");
        if (request.creditOverrideReason() != null && !request.creditOverrideReason().isBlank()) {
            // Permissão separada e crítica: ela deixa passar uma venda acima do que a casa decidiu
            // carregar daquele cliente. Quem registra pedido não autoriza exceção por tabela.
            //
            // A alçada é conferida pela PRESENÇA do motivo, antes de o total ser conhecido — a autorização
            // mora na camada de aplicação, e trazer o principal para lá furaria a fronteira. O efeito é
            // que um pedido que acabaria cabendo no teto também é recusado a quem não tem a alçada, e
            // isso é deliberado: mandar uma justificativa é reivindicar autoridade para furar o limite,
            // e é a reivindicação que a permissão governa. O que não acontece é o REGISTRO — pedido que
            // coube não guarda autorização nenhuma.
            principal.requirePermission("sales.order.credit_override");
        }
        var brewery = principal.requireBrewery();
        var placed = commands.place(brewery, principal.userId(), new OrderCommands.PlaceOrder(
                request.code(), request.customerId(), request.channelId(),
                request.items().stream()
                        .map(i -> new OrderCommands.OrderItem(i.productId(), i.quantity())).toList(),
                request.placedOn(), request.promisedFor(), idempotencyKey,
                request.creditOverrideReason()));
        // A exceção ao teto vai na auditoria com o motivo: é o registro que permite ao dono perguntar
        // depois por que aquele cliente passou do limite.
        //
        // TUDO VEM DO PEDIDO GRAVADO — a decisão E o texto —, e nada da requisição. Duas razões, e a
        // segunda só apareceu quando o dado foi lido pela metade:
        //
        // 1. Um motivo enviado num pedido que acabou cabendo no teto não vira exceção em lugar nenhum.
        //    Ler a presença do campo faria a trilha contar exceções que nunca houve — exatamente o
        //    registro que o agregado se recusa a criar.
        // 2. No reenvio idempotente as duas fontes divergem: o pedido carrega a autorização da primeira
        //    vez, e a repetição pode vir sem motivo, ou com outro. Decidir pelo pedido e escrever o texto
        //    da requisição gravaria `null` num `Map.of` — que o recusa — derrubando com 500 um reenvio
        //    que deveria só devolver o pedido que já existe; ou gravaria na trilha uma justificativa que
        //    ninguém autorizou.
        var detalhes = placed.creditOverrideReason()
                .map(motivo -> Map.of("code", request.code(), "creditOverrideReason", motivo))
                .orElseGet(() -> Map.of("code", request.code()));
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.order.place", "sales.order",
                placed.id().toString(), detalhes));
        return Map.of("id", placed.id());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("sales.order.manage");
        var brewery = principal.requireBrewery();
        commands.cancel(brewery, principal.userId(), id);
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.order.cancel", "sales.order",
                id.toString(), Map.of()));
    }

    @PutMapping("/{id}/promise")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void promise(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody PromiseRequest request) {
        principal.requirePermission("sales.order.manage");
        var brewery = principal.requireBrewery();
        commands.promiseFor(brewery, principal.userId(), id, request.promisedFor());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.order.promise", "sales.order",
                id.toString(),
                Map.of("promisedFor", String.valueOf(request.promisedFor()))));
    }

    private static OrderView view(SalesOrder o) {
        var total = o.total();
        return new OrderView(o.id(), o.code(), o.customerId(), o.channelId(), o.status().name(),
                o.placedOn(), o.promisedFor().orElse(null), total.amount(), total.currency(),
                o.creditOverride().map(CreditOverride::reason).orElse(null),
                o.creditOverride().map(CreditOverride::authorizedBy).orElse(null),
                o.lines().stream()
                        .map(l -> new OrderLineView(l.productId(), l.sku(), l.quantity(),
                                l.unitPrice().amount(), l.unitPrice().currency(), l.taxIncluded(),
                                l.reservations().stream()
                                        .map(r -> new ReservationView(r.finishedLotId(), r.lotCode(),
                                                r.units(), r.bestBefore()))
                                        .toList()))
                        .toList());
    }

    /**
     * @param creditOverrideReason só é usado quando o pedido passa do teto de crédito (SAL-004); nos
     *                             outros casos é ignorado, porque não houve teto a furar
     */
    record PlaceOrderRequest(@NotBlank @Size(max = 40) String code, @NotNull UUID customerId,
            @NotNull UUID channelId, @NotEmpty @Valid List<ItemRequest> items, LocalDate placedOn,
            LocalDate promisedFor, @Size(max = 500) String creditOverrideReason) {}

    record ItemRequest(@NotNull UUID productId, @Positive int quantity) {}

    record PromiseRequest(LocalDate promisedFor) {}

    /**
     * A autorização acima do teto viaja na resposta: um pedido que passou do limite precisa dizer isso
     * na tela onde alguém o lê, e não só na trilha de auditoria (SAL-004).
     */
    record OrderView(UUID id, String code, UUID customerId, UUID channelId, String status,
            LocalDate placedOn, LocalDate promisedFor, BigDecimal total, String currency,
            String creditOverrideReason, UUID creditOverrideBy, List<OrderLineView> lines) {}

    record OrderLineView(UUID productId, String sku, int quantity, BigDecimal unitAmount,
            String currency, boolean taxIncluded, List<ReservationView> reservations) {}

    /** O lote reservado viaja na resposta: é o que um recall percorre para saber a quem avisar. */
    record ReservationView(UUID finishedLotId, String lotCode, int units, LocalDate bestBefore) {}
}
