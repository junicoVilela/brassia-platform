package br.com.brew.brassia.ai.adapter.outbound.persistence;

import br.com.brew.brassia.ai.ModelPurpose;
import br.com.brew.brassia.ai.application.port.outbound.ModelInvocationLedger;
import br.com.brew.brassia.ai.domain.InvocationStatus;
import br.com.brew.brassia.ai.domain.ModelInvocation;
import br.com.brew.brassia.ai.domain.TokenUsage;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * O ledger de chamadas em PostgreSQL (AIA-001).
 *
 * <p><strong>{@code REQUIRES_NEW} é a decisão central deste adapter.</strong> A chamada ao modelo já
 * custou dinheiro quando esta gravação acontece. Se ela participasse da transação do comando que a
 * pediu, todo comando que falhasse depois levaria a prova do gasto junto no rollback — e o orçamento
 * passaria a proteger contra um consumo que não vê. Dinheiro que saiu não volta com rollback, e o
 * registro dele não pode.
 */
@Repository
class JdbcModelInvocationLedger implements ModelInvocationLedger {

    private final JdbcClient jdbc;

    JdbcModelInvocationLedger(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ModelInvocation invocation) {
        jdbc.sql("""
                INSERT INTO ai_model_invocation (id, brewery_id, actor_id, purpose, provider, model, status,
                        input_tokens, output_tokens, cost, currency, latency_millis, failure_reason,
                        occurred_at)
                VALUES (:id, :brewery, :actor, :purpose, :provider, :model, :status, :inputTokens,
                        :outputTokens, :cost, :currency, :latency, :reason, :at)
                """)
                .param("id", invocation.id())
                .param("brewery", invocation.breweryId())
                .param("actor", invocation.actorId())
                .param("purpose", invocation.purpose().name())
                .param("provider", invocation.provider())
                .param("model", invocation.model())
                .param("status", invocation.status().name())
                .param("inputTokens", invocation.usage().inputTokens())
                .param("outputTokens", invocation.usage().outputTokens())
                .param("cost", invocation.cost())
                .param("currency", invocation.currency())
                .param("latency", invocation.latencyMillis())
                .param("reason", invocation.failureReason())
                .param("at", Timestamp.from(invocation.occurredAt()))
                .update();
    }

    @Override
    public BigDecimal spentSince(UUID breweryId, Instant since) {
        // COALESCE porque cervejaria sem chamada nenhuma gastou zero, não "desconhecido": um nulo aqui
        // viraria NPE na soma do orçamento e derrubaria a consulta de status de quem nunca usou IA.
        return jdbc.sql("""
                SELECT COALESCE(SUM(cost), 0) FROM ai_model_invocation
                WHERE brewery_id = :brewery AND occurred_at >= :since
                """)
                .param("brewery", breweryId)
                .param("since", Timestamp.from(since))
                .query(BigDecimal.class).single();
    }

    @Override
    public List<ModelInvocation> recent(UUID breweryId, int limit) {
        return jdbc.sql("""
                SELECT id, brewery_id, actor_id, purpose, provider, model, status, input_tokens,
                        output_tokens, cost, currency, latency_millis, failure_reason, occurred_at
                FROM ai_model_invocation WHERE brewery_id = :brewery
                ORDER BY occurred_at DESC LIMIT :limit
                """)
                .param("brewery", breweryId)
                .param("limit", limit)
                .query(JdbcModelInvocationLedger::map).list();
    }

    private static ModelInvocation map(ResultSet rs, int rowNum) throws SQLException {
        return ModelInvocation.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                ModelPurpose.valueOf(rs.getString("purpose")),
                rs.getString("provider"),
                rs.getString("model"),
                InvocationStatus.valueOf(rs.getString("status")),
                new TokenUsage(rs.getLong("input_tokens"), rs.getLong("output_tokens")),
                rs.getBigDecimal("cost"),
                rs.getString("currency"),
                rs.getLong("latency_millis"),
                rs.getString("failure_reason"),
                rs.getTimestamp("occurred_at").toInstant());
    }
}
