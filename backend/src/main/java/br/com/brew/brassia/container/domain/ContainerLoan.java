package br.com.brew.brassia.container.domain;

import br.com.brew.brassia.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O vasilhame que está fora de casa, com prazo e caução (CON-003).
 *
 * <p><strong>Um empréstimo é diferente de uma posição.</strong> A CON-002 já sabe que o keg está no
 * cliente; o que falta é o compromisso: até quando ele deveria voltar, quanto ficou retido, e o que fazer
 * quando o prazo passa. Sem isso, "no cliente há dois dias" e "no cliente há sete meses" são a mesma
 * linha na tela.
 *
 * <p><strong>A caução é {@code Money}, o mesmo tipo de vendas</strong> (DEB-CON-002, resolvido em
 * 2026-08-18). Ele foi promovido a {@code shared} quando esta história precisou da mesma regra —
 * duplicá-la criava duas definições de "dinheiro com moeda" que podem divergir sem que a segunda avise a
 * primeira. A regra que ficou <em>aqui</em> é a de negócio: caução zero não existe.
 *
 * <p><strong>Perda não é baixa.</strong> A CON-001 recusa dar baixa no que está com o cliente, e essa
 * recusa continua de pé: o vasilhame que não voltou tem outro fato, outro responsável e outro efeito
 * sobre a caução. Tratá-los como o mesmo botão faria "sumiu" e "descartei" virarem a mesma linha no
 * inventário.
 */
public final class ContainerLoan {

    private final UUID id;
    private final UUID breweryId;
    private final UUID containerId;
    private final UUID customerId;
    /** Congelado: renomear o cliente não reescreve o comprovante de caução que ele tem na mão. */
    private final String customerName;
    private final Instant lentAt;
    private final LocalDate dueOn;
    private final Money deposit;
    private Instant returnedAt;
    private Instant lostAt;
    private String lossReason;

    private ContainerLoan(UUID id, UUID breweryId, UUID containerId, UUID customerId,
            String customerName, Instant lentAt, LocalDate dueOn, Money deposit,
            Instant returnedAt, Instant lostAt, String lossReason) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.containerId = Objects.requireNonNull(containerId, "vasilhame");
        this.customerId = Objects.requireNonNull(customerId, "cliente");
        this.customerName = requireName(customerName);
        this.lentAt = Objects.requireNonNull(lentAt, "quando saiu");
        this.dueOn = Objects.requireNonNull(dueOn, "prazo de retorno");
        if (deposit != null && !deposit.isPositive()) {
            // Caução zero não é caução: é a ausência dela, e a ausência se representa com nulo. Zero
            // somaria no relatório de valores retidos como se houvesse dinheiro parado.
            throw new IllegalArgumentException("a caução deve ser positiva");
        }
        this.deposit = deposit;
        this.returnedAt = returnedAt;
        this.lostAt = lostAt;
        this.lossReason = lossReason;
    }

    public static ContainerLoan open(UUID id, UUID breweryId, UUID containerId, UUID customerId,
            String customerName, Instant lentAt, LocalDate dueOn, Money deposit) {
        if (dueOn.isBefore(lentAt.atZone(java.time.ZoneOffset.UTC).toLocalDate())) {
            // Um prazo que vence antes de o keg sair nasce em atraso, e o operador só descobriria quando
            // a cobrança saísse errada.
            throw new IllegalArgumentException("o prazo não pode ser anterior à saída");
        }
        return new ContainerLoan(id, breweryId, containerId, customerId, customerName, lentAt, dueOn,
                deposit, null, null, null);
    }

    public static ContainerLoan reconstitute(UUID id, UUID breweryId, UUID containerId,
            UUID customerId, String customerName, Instant lentAt, LocalDate dueOn,
            Money deposit, Instant returnedAt, Instant lostAt, String lossReason) {
        return new ContainerLoan(id, breweryId, containerId, customerId, customerName, lentAt, dueOn,
                deposit, returnedAt, lostAt, lossReason);
    }

    /** O vasilhame voltou. A caução passa a ser devida ao cliente — devolvê-la é ato do financeiro. */
    public void returned(Instant at) {
        requireOpen();
        if (at.isBefore(lentAt)) {
            throw new IllegalArgumentException("a devolução não pode ser anterior à saída");
        }
        this.returnedAt = at;
    }

    /**
     * O vasilhame não volta mais.
     *
     * <p>Exige motivo: "perdido" sozinho não distingue o bar que fechou do keg que foi roubado do
     * caminhão — e as duas coisas terminam em conversas diferentes com o cliente.
     */
    public void lost(Instant at, String reason) {
        requireOpen();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a perda precisa de motivo");
        }
        this.lostAt = at;
        this.lossReason = reason.trim();
    }

    // --- prazo ---

    public boolean isOpen() {
        return returnedAt == null && lostAt == null;
    }

    /** Atrasado é o que ainda não voltou depois do prazo — e não o que voltou tarde. */
    public boolean overdueOn(LocalDate today) {
        return isOpen() && today.isAfter(dueOn);
    }

    /**
     * Quantos dias de atraso, para a cobrança e para a fila do dia.
     *
     * <p>Zero quando está no prazo. Não é negativo: "faltam três dias" é outra pergunta, e devolvê-la
     * aqui faria alguém somar atrasos com folgas e chegar a zero sem nenhum keg no lugar.
     */
    public long daysLate(LocalDate today) {
        if (!overdueOn(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueOn, today);
    }

    /** Devolvido depois do prazo: não é atraso em aberto, mas é histórico de quem devolve tarde. */
    public boolean returnedLate() {
        return returnedAt != null
                && returnedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(dueOn);
    }

    // --- caução ---

    public DepositOutcome depositOutcome() {
        if (lostAt != null) {
            return DepositOutcome.RETAINED;
        }
        if (returnedAt != null) {
            return DepositOutcome.TO_REFUND;
        }
        return DepositOutcome.HELD;
    }

    public boolean hasDeposit() {
        return deposit != null;
    }

    private void requireOpen() {
        if (!isOpen()) {
            // Encerrar duas vezes reescreveria a data que a cobrança usou.
            throw new IllegalStateException("este empréstimo já foi encerrado");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID containerId() {
        return containerId;
    }

    public UUID customerId() {
        return customerId;
    }

    public String customerName() {
        return customerName;
    }

    public Instant lentAt() {
        return lentAt;
    }

    public LocalDate dueOn() {
        return dueOn;
    }

    public Optional<Money> deposit() {
        return Optional.ofNullable(deposit);
    }

    public Optional<Instant> returnedAt() {
        return Optional.ofNullable(returnedAt);
    }

    public Optional<Instant> lostAt() {
        return Optional.ofNullable(lostAt);
    }

    public Optional<String> lossReason() {
        return Optional.ofNullable(lossReason);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("o empréstimo precisa do nome do cliente");
        }
        return name.trim();
    }
}
