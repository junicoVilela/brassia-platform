package br.com.brew.brassia.ai.adapter.outbound.persistence;

import br.com.brew.brassia.ai.application.port.outbound.CommandProposalRepository;
import br.com.brew.brassia.ai.domain.CommandProposal;
import br.com.brew.brassia.ai.domain.ProposalStatus;
import br.com.brew.brassia.ai.domain.ProposedAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Propostas de comando em PostgreSQL (AIA-003). */
@Repository
class JdbcCommandProposalRepository implements CommandProposalRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;

    JdbcCommandProposalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CommandProposal proposal) {
        jdbc.sql("""
                INSERT INTO ai_command_proposal (id, brewery_id, action, parameters, rationale, proposed_by,
                        proposed_at, expires_at, status)
                VALUES (:id, :brewery, :action, :parameters::jsonb, :rationale, :by, :at, :expires, :status)
                """)
                .param("id", proposal.id())
                .param("brewery", proposal.breweryId())
                .param("action", proposal.action().name())
                .param("parameters", write(proposal.parameters()))
                .param("rationale", proposal.rationale())
                .param("by", proposal.proposedBy())
                .param("at", Timestamp.from(proposal.proposedAt()))
                .param("expires", Timestamp.from(proposal.expiresAt()))
                .param("status", proposal.status().name())
                .update();
    }

    /**
     * Grava a decisão só se a proposta ainda estiver pendente.
     *
     * <p>{@code AND status = 'PENDING'} é o que impede dois cliques simultâneos em "confirmar" de produzirem
     * dois aceites da mesma proposta. Sem essa condição, a segunda gravação sobrescreveria a primeira em
     * silêncio — e quem clicou por segundo acreditaria que a decisão foi dele.
     */
    @Override
    public boolean saveDecision(CommandProposal proposal) {
        return jdbc.sql("""
                UPDATE ai_command_proposal
                SET status = :status, decided_by = :by, decided_at = :at, decision_note = :note
                WHERE id = :id AND brewery_id = :brewery AND status = 'PENDING'
                """)
                .param("id", proposal.id())
                .param("brewery", proposal.breweryId())
                .param("status", proposal.status().name())
                .param("by", proposal.decidedBy())
                .param("at", Timestamp.from(proposal.decidedAt()))
                .param("note", proposal.decisionNote())
                .update() == 1;
    }

    @Override
    public Optional<CommandProposal> find(UUID breweryId, UUID proposalId) {
        return jdbc.sql("""
                SELECT id, brewery_id, action, parameters, rationale, proposed_by, proposed_at, expires_at,
                        status, decided_by, decided_at, decision_note
                FROM ai_command_proposal WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId).param("id", proposalId)
                .query(JdbcCommandProposalRepository::map).optional();
    }

    @Override
    public List<CommandProposal> findAll(UUID breweryId) {
        return jdbc.sql("""
                SELECT id, brewery_id, action, parameters, rationale, proposed_by, proposed_at, expires_at,
                        status, decided_by, decided_at, decision_note
                FROM ai_command_proposal WHERE brewery_id = :brewery ORDER BY proposed_at DESC
                """)
                .param("brewery", breweryId)
                .query(JdbcCommandProposalRepository::map).list();
    }

    private static CommandProposal map(ResultSet rs, int rowNum) throws SQLException {
        var decidedAt = rs.getTimestamp("decided_at");
        return CommandProposal.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                ProposedAction.valueOf(rs.getString("action")),
                read(rs.getString("parameters")),
                rs.getString("rationale"),
                rs.getObject("proposed_by", UUID.class),
                rs.getTimestamp("proposed_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                ProposalStatus.valueOf(rs.getString("status")),
                rs.getObject("decided_by", UUID.class),
                decidedAt == null ? null : decidedAt.toInstant(),
                rs.getString("decision_note"));
    }

    private static String write(Map<String, String> parameters) {
        try {
            return JSON.writeValueAsString(parameters);
        } catch (JsonProcessingException impossible) {
            // Mapa de String para String sempre serializa; se não serializar, é bug de programação.
            throw new IllegalStateException("parâmetros da proposta não serializam", impossible);
        }
    }

    private static Map<String, String> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException corrupt) {
            throw new IllegalStateException("parâmetros da proposta estão ilegíveis no banco", corrupt);
        }
    }
}
