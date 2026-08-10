package br.com.brew.brassia.ai.adapter.outbound.persistence;

import br.com.brew.brassia.ai.application.port.outbound.AiBudgetRepository;
import br.com.brew.brassia.ai.application.port.outbound.ModelInvocationLedger;
import br.com.brew.brassia.ai.config.AiProperties;
import br.com.brew.brassia.brewery.BreweryDirectory;
import br.com.brew.brassia.brewery.BreweryRef;
import br.com.brew.brassia.ai.domain.AiBudget;
import br.com.brew.brassia.ai.domain.StaleAiBudgetException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * O teto de gasto em PostgreSQL, com o gasto do mês somado na leitura (AIA-001).
 *
 * <p><strong>Lê do ledger, não de um contador.</strong> O gasto do mês é somado das chamadas que
 * aconteceram, todas as vezes. Guardar um total incrementado ao lado do limite seria um segundo número
 * sobre o mesmo fato: bastaria um incremento perdido para o orçamento passar a proteger contra um
 * consumo que ele não vê. Somar custa uma consulta indexada e vale a certeza.
 */
@Repository
class JdbcAiBudgetRepository implements AiBudgetRepository {

    private final JdbcClient jdbc;
    private final ModelInvocationLedger ledger;
    private final BigDecimal defaultLimit;
    private final String currency;
    private final BreweryDirectory breweries;

    /**
     * Fuso de recurso, usado só quando a cervejaria não é encontrada (DEB-AI-001).
     *
     * <p>Não é mais a regra, é o que sobra quando não há resposta melhor. Lançar aqui derrubaria a consulta
     * de orçamento por um cadastro incompleto, e o orçamento é justamente a proteção que não pode cair.
     */
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("America/Sao_Paulo");

    /**
     * O relógio é interno, e não injetado como nos casos de uso: aqui ele só serve para saber em que mês
     * estamos. Não há regra a testar sobre "que horas eram" — a regra sobre a virada do mês é a do fuso,
     * e essa é configuração.
     */
    private final Clock clock = Clock.systemUTC();

    JdbcAiBudgetRepository(JdbcClient jdbc, ModelInvocationLedger ledger, AiProperties properties,
            BreweryDirectory breweries) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.defaultLimit = properties.monthlyBudget();
        this.currency = properties.currency();
        this.breweries = breweries;
    }

    @Override
    public AiBudget currentOf(UUID breweryId) {
        var spent = ledger.spentSince(breweryId, startOfMonth(breweryId));
        return jdbc.sql("""
                SELECT brewery_id, monthly_limit, currency, version, updated_by, updated_at
                FROM ai_model_budget WHERE brewery_id = :brewery
                """)
                .param("brewery", breweryId)
                .query((rs, rowNum) -> map(rs, spent))
                .optional()
                // Sem linha cadastrada vale o teto padrão da instalação. Versão zero é o sinal de que
                // ainda não há linha — quem gravar depois faz INSERT, não UPDATE.
                .orElseGet(() -> AiBudget.defaultOf(breweryId, defaultLimit, currency, spent));
    }

    @Override
    public AiBudget save(AiBudget budget, long expectedVersion) {
        var updated = expectedVersion == 0 ? insert(budget) : update(budget, expectedVersion);
        if (!updated) {
            throw new StaleAiBudgetException();
        }
        return currentOf(budget.breweryId());
    }

    /**
     * Primeira definição do teto desta cervejaria.
     *
     * <p>{@code ON CONFLICT DO NOTHING} em vez de estourar: se duas pessoas definirem o primeiro teto ao
     * mesmo tempo, a segunda recebe a mesma recusa por concorrência que receberia numa alteração — em vez
     * de um erro de constraint que ela não sabe interpretar.
     */
    private boolean insert(AiBudget budget) {
        return jdbc.sql("""
                INSERT INTO ai_model_budget (brewery_id, monthly_limit, currency, version, updated_by,
                        updated_at)
                VALUES (:brewery, :limit, :currency, 1, :by, :at)
                ON CONFLICT (brewery_id) DO NOTHING
                """)
                .param("brewery", budget.breweryId())
                .param("limit", budget.monthlyLimit())
                .param("currency", budget.currency())
                .param("by", budget.updatedBy())
                .param("at", Timestamp.from(budget.updatedAt()))
                .update() == 1;
    }

    private boolean update(AiBudget budget, long expectedVersion) {
        return jdbc.sql("""
                UPDATE ai_model_budget
                SET monthly_limit = :limit, version = version + 1, updated_by = :by, updated_at = :at
                WHERE brewery_id = :brewery AND version = :expected
                """)
                .param("brewery", budget.breweryId())
                .param("limit", budget.monthlyLimit())
                .param("by", budget.updatedBy())
                .param("at", Timestamp.from(budget.updatedAt()))
                .param("expected", expectedVersion)
                .update() == 1;
    }

    /**
     * Início do mês corrente <strong>no fuso da cervejaria</strong> (DEB-AI-001 resolvido).
     *
     * <p>Era o fuso de uma propriedade por instalação, e numa plataforma com cervejarias em fusos
     * diferentes isso erra para todas menos uma: no dia 1º, uma cervejaria a oeste ainda está no mês
     * anterior enquanto o servidor já virou, e o gasto do mês novo é debitado do orçamento do mês que
     * acabou. O erro dura poucas horas por mês e acontece justamente quando o orçamento está no limite,
     * que é quando ele importa.
     *
     * <p>Fuso inválido no cadastro cai no de recurso em vez de derrubar a consulta: {@code ZoneId.of}
     * lança para identificador desconhecido, e um cadastro com fuso digitado errado não pode impedir a
     * verificação de orçamento — ela é a proteção contra gasto descontrolado.
     */
    private Instant startOfMonth(UUID breweryId) {
        return clock.instant().atZone(zoneOf(breweryId))
                .withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
                .toInstant();
    }

    private ZoneId zoneOf(UUID breweryId) {
        return breweries.findById(breweryId)
                .map(BreweryRef::timezone)
                .map(JdbcAiBudgetRepository::parseOrFallback)
                .orElse(FALLBACK_ZONE);
    }

    private static ZoneId parseOrFallback(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            return FALLBACK_ZONE;
        }
    }

    private AiBudget map(ResultSet rs, BigDecimal spent) throws SQLException {
        return AiBudget.reconstitute(
                rs.getObject("brewery_id", UUID.class),
                rs.getBigDecimal("monthly_limit"),
                rs.getString("currency"),
                spent,
                rs.getLong("version"),
                rs.getObject("updated_by", UUID.class),
                rs.getTimestamp("updated_at").toInstant());
    }
}
