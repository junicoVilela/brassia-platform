package br.com.brew.brassia.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * O teto de gasto mensal com IA de uma cervejaria, e quanto do mês já foi (AIA-001).
 *
 * <p><strong>Por que existe.</strong> O risco desta sprint que não aparece em nenhum teste funcional
 * é o custo imprevisível: uma chamada custa frações de centavo e um laço que erra chama mil vezes.
 * Um teto por cervejaria transforma um prejuízo em uma recusa.
 *
 * <p><strong>Teto é configuração, gasto é derivado.</strong> O limite fica guardado porque é uma
 * decisão de alguém; o gasto do mês é somado do ledger a cada consulta, porque um contador guardado
 * ao lado do ledger seria um segundo número sobre o mesmo fato, e dois números sobre o mesmo fato
 * divergem. O que se guarda é o limite; o que se conta é o que aconteceu.
 *
 * <p>Sem linha configurada vale o teto padrão da instalação — nenhuma cervejaria fica sem teto por
 * esquecimento de cadastro.
 */
public final class AiBudget {

    private final UUID breweryId;
    private final BigDecimal monthlyLimit;
    private final String currency;
    private final BigDecimal spentThisMonth;
    private final long version;
    private final UUID updatedBy;
    private final Instant updatedAt;

    private AiBudget(UUID breweryId, BigDecimal monthlyLimit, String currency, BigDecimal spentThisMonth,
            long version, UUID updatedBy, Instant updatedAt) {
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.monthlyLimit = requireLimit(monthlyLimit);
        this.currency = Objects.requireNonNull(currency, "moeda é obrigatória");
        this.spentThisMonth = Objects.requireNonNull(spentThisMonth, "gasto do mês é obrigatório");
        this.version = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /** Teto padrão da instalação, para cervejaria que ainda não definiu o seu. Versão zero: não há linha. */
    public static AiBudget defaultOf(UUID breweryId, BigDecimal monthlyLimit, String currency,
            BigDecimal spentThisMonth) {
        return new AiBudget(breweryId, monthlyLimit, currency, spentThisMonth, 0L, null, null);
    }

    public static AiBudget reconstitute(UUID breweryId, BigDecimal monthlyLimit, String currency,
            BigDecimal spentThisMonth, long version, UUID updatedBy, Instant updatedAt) {
        return new AiBudget(breweryId, monthlyLimit, currency, spentThisMonth, version, updatedBy, updatedAt);
    }

    /**
     * Redefine o teto.
     *
     * <p>A versão vem de quem leu: se outra pessoa mexeu no teto entre a leitura e a gravação, esta
     * escrita é recusada em vez de sobrescrever a decisão dela. Teto de gasto é exatamente o tipo de
     * número que duas pessoas ajustam no mesmo dia.
     */
    public AiBudget redefine(BigDecimal newLimit, UUID actorId, Instant at) {
        return new AiBudget(breweryId, requireLimit(newLimit), currency, spentThisMonth, version,
                Objects.requireNonNull(actorId, "autor da alteração é obrigatório"),
                Objects.requireNonNull(at, "instante da alteração é obrigatório"));
    }

    /**
     * Verifica se o pior caso desta chamada ainda cabe no mês.
     *
     * <p>A verificação é antes de gastar, com o custo do teto de saída: depois de gastar o dinheiro
     * já saiu, e conferir o saldo então só serviria para descobrir que estourou.
     */
    public void requireHeadroom(BigDecimal estimatedCost) {
        Objects.requireNonNull(estimatedCost, "custo estimado é obrigatório");
        if (spentThisMonth.add(estimatedCost).compareTo(monthlyLimit) > 0) {
            throw new AiBudgetExceededException(monthlyLimit, spentThisMonth);
        }
    }

    public BigDecimal remaining() {
        var left = monthlyLimit.subtract(spentThisMonth);
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }

    public boolean exhausted() {
        return remaining().signum() == 0;
    }

    private static BigDecimal requireLimit(BigDecimal limit) {
        Objects.requireNonNull(limit, "teto mensal é obrigatório");
        if (limit.signum() < 0) {
            throw new IllegalArgumentException("teto mensal não pode ser negativo");
        }
        return limit;
    }

    public UUID breweryId() { return breweryId; }
    public BigDecimal monthlyLimit() { return monthlyLimit; }
    public String currency() { return currency; }
    public BigDecimal spentThisMonth() { return spentThisMonth; }
    public long version() { return version; }
    public UUID updatedBy() { return updatedBy; }
    public Instant updatedAt() { return updatedAt; }
}
