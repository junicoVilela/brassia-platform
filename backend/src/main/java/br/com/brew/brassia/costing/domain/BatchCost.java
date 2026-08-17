package br.com.brew.brassia.costing.domain;

import br.com.brew.brassia.costing.CostContributor.CostCategory;
import br.com.brew.brassia.costing.CostContributor.CostGap;
import br.com.brew.brassia.costing.CostContributor.CostLine;
import br.com.brew.brassia.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * O custo realizado de um lote (CST-001): as parcelas somadas, com origem, e o que ficou de fora.
 *
 * <p><strong>Derivado enquanto aberto, congelado quando fechado.</strong> É a mesma distinção que a
 * rastreabilidade firmou: o que é sobre o presente se deriva, o que é sobre o passado se guarda. Um
 * custo aberto acompanha o que ainda acontece — um envase a mais muda o custo por litro; um custo
 * fechado é a resposta daquele dia, e recalculá-la depois responderia sobre outro dia.
 *
 * <p><strong>O total tem moeda</strong> (DEB-SAL-001, resolvido em 2026-08-18). Antes eram
 * {@code BigDecimal} nus: enquanto a casa opera numa moeda só nada quebra, mas a primeira exportação soma
 * real com dólar sem que nada reclame, e o erro aparece no fechamento do mês, longe da causa. A moeda vem
 * das preferências da cervejaria — ela já existia desde a Sprint 01, e o que faltava era chegar aqui.
 *
 * <p><strong>A moeda é do custo, e não da linha.</strong> Quem produz uma linha é o contribuinte —
 * estoque, envase, utilidades, mão de obra — e nenhum deles conhece dinheiro: eles reportam "consumi 20 kg
 * a 4,50". Exigir moeda na linha faria a produção precisar saber de moeda para registrar que trabalhou, que
 * é o que a V117 recusou ao separar apontamento de hora da taxa da hora.
 *
 * <p><strong>As lacunas viajam com o total, e não como nota de rodapé.</strong> Um custo sem mão de
 * obra que declara "sem mão de obra" é utilizável; um que soma zero mente por omissão. É o mesmo
 * princípio do perfil de alergênicos: "não sei" nunca vale como "não tem".
 */
public final class BatchCost {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final String batchCode;
    private final BigDecimal volumeLiters;
    private final String currency;
    private final List<CostLine> lines;
    private final List<CostGap> gaps;
    private final boolean closed;
    private final UUID closedBy;
    private final Instant closedAt;
    private final String note;

    private BatchCost(UUID id, UUID breweryId, UUID batchId, String batchCode, BigDecimal volumeLiters,
            String currency, List<CostLine> lines, List<CostGap> gaps, boolean closed, UUID closedBy,
            Instant closedAt, String note) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "lote é obrigatório");
        this.batchCode = Objects.requireNonNull(batchCode, "código do lote é obrigatório");
        this.volumeLiters = Objects.requireNonNull(volumeLiters, "volume é obrigatório");
        this.currency = Objects.requireNonNull(currency, "moeda é obrigatória");
        this.lines = List.copyOf(lines);
        this.gaps = List.copyOf(gaps);
        this.closed = closed;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.note = note;
    }

    /** Custo aberto: a soma de agora, que ainda muda se a produção mudar. */
    public static BatchCost open(UUID breweryId, UUID batchId, String batchCode, BigDecimal volumeLiters,
            String currency, List<CostLine> lines, List<CostGap> gaps) {
        return new BatchCost(UUID.randomUUID(), breweryId, batchId, batchCode, volumeLiters, currency,
                lines, gaps, false, null, null, null);
    }

    public static BatchCost reconstitute(UUID id, UUID breweryId, UUID batchId, String batchCode,
            BigDecimal volumeLiters, String currency, List<CostLine> lines, List<CostGap> gaps,
            UUID closedBy, Instant closedAt, String note) {
        return new BatchCost(id, breweryId, batchId, batchCode, volumeLiters, currency, lines, gaps, true,
                closedBy, closedAt, note);
    }

    /**
     * Fecha o custo com o que está somado agora.
     *
     * <p>Fechar é um ato, não uma consequência: alguém olha o número, aceita as lacunas e assina.
     * Por isso o custo aberto não vira fechado sozinho quando o lote termina — terminar de produzir
     * e terminar de apurar são coisas diferentes, e a segunda tem dono.
     */
    public BatchCost close(UUID actorId, String note, Instant at) {
        if (closed) {
            throw new IllegalStateException("o custo deste lote já foi fechado");
        }
        if (volumeLiters.signum() <= 0) {
            throw new IllegalStateException("não há volume produzido para dividir o custo");
        }
        return new BatchCost(id, breweryId, batchId, batchCode, volumeLiters, currency, lines, gaps, true,
                Objects.requireNonNull(actorId, "autor do fechamento é obrigatório"),
                Objects.requireNonNull(at, "instante do fechamento é obrigatório"), trimToNull(note));
    }

    public Money total() {
        return new Money(lines.stream().map(CostLine::total).reduce(BigDecimal.ZERO, BigDecimal::add),
                currency);
    }

    /** Total por categoria — é como o custo é lido: quanto foi malte, quanto foi lata. */
    public Map<CostCategory, Money> totalByCategory() {
        var totals = new EnumMap<CostCategory, Money>(CostCategory.class);
        for (CostLine line : lines) {
            totals.merge(line.category(), new Money(line.total(), currency), Money::plus);
        }
        return totals;
    }

    /**
     * Custo por litro do volume que existiu de fato.
     *
     * <p>O divisor é o volume transferido ao fermentador, não o planejado: dividir pelo planejado
     * embelezaria o indicador de um lote que rendeu menos, que é exatamente o lote sobre o qual se
     * precisa saber.
     */
    public Money costPerLiter() {
        if (volumeLiters.signum() <= 0) {
            return new Money(BigDecimal.ZERO, currency);
        }
        return new Money(total().amount().divide(volumeLiters, 4, RoundingMode.HALF_UP), currency);
    }

    public String currency() {
        return currency;
    }

    /** Verdadeiro quando alguma parcela conhecida ficou de fora: o total é menor que a verdade. */
    public boolean incomplete() {
        return !gaps.isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("observação excede 500 caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public String batchCode() { return batchCode; }
    public BigDecimal volumeLiters() { return volumeLiters; }
    public List<CostLine> lines() { return lines; }
    public List<CostGap> gaps() { return gaps; }
    public boolean closed() { return closed; }
    public UUID closedBy() { return closedBy; }
    public Instant closedAt() { return closedAt; }
    public String note() { return note; }
}
