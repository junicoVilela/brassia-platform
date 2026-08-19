package br.com.brew.brassia.sales.domain;

import br.com.brew.brassia.shared.money.CurrencyMismatchException;
import br.com.brew.brassia.shared.money.Money;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um pedido: quem comprou, o quê, de quais lotes e para quando (SAL-002).
 *
 * <p><strong>Nasce confirmado, com estoque reservado.</strong> Não há rascunho: um pedido que não reservou
 * nada não segura lote nenhum, e chamar isso de pedido faria a cervejaria contar como vendido o que
 * qualquer outro cliente ainda pode levar.
 *
 * <p><strong>A promessa de entrega é validada contra a validade do lote reservado</strong>, e é a regra
 * que dá nome à história. Prometer para depois de a cerveja vencer é aceito em silêncio por qualquer
 * sistema que não olhe para o lote — e o problema aparece no dia da carga, quando não há mais o que fazer
 * além de desapontar alguém.
 *
 * <p><strong>O que este agregado NÃO garante</strong>, e está escrito para não parecer que garante: que a
 * soma das reservas de todos os pedidos caiba no lote. Isso é invariante entre pedidos, e um agregado só
 * enxerga a si mesmo — duas requisições simultâneas passariam as duas. A garantia é do banco, e mora na
 * restrição que impede o total reservado de passar das unidades do lote.
 */
public final class SalesOrder {

    private static final int MONEY_SCALE = 2;

    private final UUID id;
    private final UUID breweryId;
    private final UUID customerId;
    private final UUID channelId;
    private final String code;
    private final List<OrderLine> lines;
    private final LocalDate placedOn;
    private LocalDate promisedFor;
    private OrderStatus status;
    private final Instant createdAt;
    /** Nulo é o normal: o pedido coube no teto, ou não havia teto (SAL-004). */
    private CreditOverride creditOverride;

    private SalesOrder(UUID id, UUID breweryId, UUID customerId, UUID channelId, String code,
            List<OrderLine> lines, LocalDate placedOn, LocalDate promisedFor, OrderStatus status,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria");
        this.customerId = Objects.requireNonNull(customerId, "cliente");
        this.channelId = Objects.requireNonNull(channelId, "canal");
        this.code = code;
        this.lines = List.copyOf(lines);
        this.placedOn = Objects.requireNonNull(placedOn, "data do pedido");
        this.promisedFor = promisedFor;
        this.status = Objects.requireNonNull(status, "situação");
        this.createdAt = Objects.requireNonNull(createdAt, "criado em");
    }

    public static SalesOrder place(UUID id, UUID breweryId, UUID customerId, UUID channelId, String code,
            List<OrderLine> lines, LocalDate placedOn, LocalDate promisedFor, Instant createdAt) {
        if (lines == null || lines.isEmpty()) {
            // Pedido sem item não é pedido: ele não reserva nada e não promete nada.
            throw new IllegalArgumentException("o pedido precisa de pelo menos um item");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("o código do pedido é obrigatório");
        }
        var order = new SalesOrder(id, breweryId, customerId, channelId, code.strip(), lines, placedOn,
                null, OrderStatus.PLACED, createdAt);
        order.requireSingleCurrency();
        order.promiseFor(promisedFor);
        return order;
    }

    public static SalesOrder reconstitute(UUID id, UUID breweryId, UUID customerId, UUID channelId,
            String code, List<OrderLine> lines, LocalDate placedOn, LocalDate promisedFor,
            OrderStatus status, Instant createdAt, CreditOverride creditOverride) {
        var order = new SalesOrder(id, breweryId, customerId, channelId, code, lines, placedOn,
                promisedFor, status, createdAt);
        order.creditOverride = creditOverride;
        return order;
    }

    /**
     * Registra que alguém autorizou este pedido acima do teto de crédito (SAL-004).
     *
     * <p><strong>Só se registra o que foi de fato usado.</strong> Guardar a justificativa num pedido que
     * cabia no limite criaria um registro dizendo "liberado acima do teto" para uma venda que nunca
     * passou de teto nenhum — e quem auditasse contaria exceções que não aconteceram.
     *
     * <p>Autorizar duas vezes o mesmo pedido reescreveria quem autorizou, e é esse nome que responde a
     * pergunta do dono seis meses depois.
     */
    public void authorizeAboveCredit(CreditOverride override) {
        if (this.creditOverride != null) {
            throw new IllegalStateException("este pedido já tem autorização acima do teto");
        }
        this.creditOverride = Objects.requireNonNull(override, "autorização");
    }

    public Optional<CreditOverride> creditOverride() {
        return Optional.ofNullable(creditOverride);
    }

    /**
     * Promete uma data de entrega, ou nenhuma.
     *
     * <p>Sem data é estado legítimo: "a combinar" acontece, e inventar uma data para o campo não ficar
     * vazio seria prometer no lugar de quem vende. O que não se aceita é uma data que a validade não
     * sustenta.
     */
    public void promiseFor(LocalDate date) {
        requireChangeable();
        if (date == null) {
            this.promisedFor = null;
            return;
        }
        if (date.isBefore(placedOn)) {
            throw new IllegalArgumentException("a entrega foi prometida para antes do pedido");
        }
        // O lote que vence primeiro é quem manda: quem entrega tudo junto entrega o mais velho junto.
        earliestExpiring().ifPresent(reserva -> {
            if (date.isAfter(reserva.bestBefore())) {
                throw new PromiseAfterShelfLifeException(date, reserva.bestBefore(), reserva.lotCode());
            }
        });
        this.promisedFor = date;
    }

    /** Cancela e devolve as reservas — quem as devolve de fato é o adaptador, na mesma transação. */
    public void cancel() {
        requireChangeable();
        this.status = OrderStatus.CANCELLED;
    }

    public void fulfill() {
        requireChangeable();
        this.status = OrderStatus.FULFILLED;
    }

    /**
     * O total, arredondado a duas casas.
     *
     * <p><strong>O arredondamento é do total, e não da linha.</strong> Arredondar cada item e somar
     * produz um centavo de diferença para cima ou para baixo em pedidos grandes, e é a diferença que o
     * cliente encontra ao conferir a nota. {@link Money} guarda quatro casas justamente para o
     * arredondamento acontecer uma vez só, aqui.
     */
    public Money total() {
        return totalOf(lines.stream().map(OrderLine::total).toList());
    }

    /**
     * A mesma soma do {@link #total()}, para quem ainda não tem um pedido montado.
     *
     * <p><strong>Existe para o teto de crédito ser conferido antes de reservar</strong> (SAL-004): a
     * conferência precisa do total, e o pedido só pode ser montado depois de reservar — reservar antes de
     * saber se ele pode existir prende estoque que a transação vai devolver.
     *
     * <p>É estático e público em vez de a regra ser recopiada no caso de uso porque o arredondamento é a
     * regra: <strong>duas casas no TOTAL, e não por linha</strong>. Arredondar linha a linha produz um
     * centavo de diferença — que é justamente o que o cliente encontra ao conferir a nota, e o que uma
     * segunda implementação passaria a produzir sozinha na primeira divergência.
     */
    public static Money totalOf(List<Money> valores) {
        var soma = valores.stream().reduce(Money::plus).orElseThrow();
        return new Money(soma.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP), soma.currency());
    }

    /** A reserva que vence primeiro, entre todos os itens. */
    public Optional<LotReservation> earliestExpiring() {
        return lines.stream()
                .flatMap(l -> l.reservations().stream())
                .min(Comparator.comparing(LotReservation::bestBefore));
    }

    /** Todos os lotes que este pedido segura — é o que um recall percorre. */
    public List<LotReservation> reservations() {
        return lines.stream().flatMap(l -> l.reservations().stream()).toList();
    }

    private void requireSingleCurrency() {
        var moedas = lines.stream().map(l -> l.unitPrice().currency()).distinct().toList();
        if (moedas.size() > 1) {
            // Um pedido com duas moedas não tem total, e um pedido sem total não pode ser faturado.
            throw new CurrencyMismatchException(moedas.get(0), moedas.get(1));
        }
    }

    private void requireChangeable() {
        if (status != OrderStatus.PLACED) {
            throw new OrderNotChangeableException(status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID channelId() {
        return channelId;
    }

    public String code() {
        return code;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public LocalDate placedOn() {
        return placedOn;
    }

    public Optional<LocalDate> promisedFor() {
        return Optional.ofNullable(promisedFor);
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
