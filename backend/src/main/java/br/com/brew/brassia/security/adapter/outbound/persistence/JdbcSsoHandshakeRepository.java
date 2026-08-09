package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.SsoHandshakeRepository;
import br.com.brew.brassia.security.domain.SsoHandshake;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** O aperto de mão SSO em PostgreSQL (SEC-B07). */
@Repository
class JdbcSsoHandshakeRepository implements SsoHandshakeRepository {

    private static final String COLUMNS = """
            id, provider_id, state, nonce, code_verifier, redirect_after_login, created_at, consumed_at
            """;

    private final JdbcClient jdbc;

    JdbcSsoHandshakeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SsoHandshake handshake) {
        jdbc.sql("""
                INSERT INTO sso_handshake (id, provider_id, state, nonce, code_verifier,
                        redirect_after_login, created_at, consumed_at)
                VALUES (:id, :provider, :state, :nonce, :verifier, :redirect, :created, NULL)
                """)
                .param("id", handshake.id())
                .param("provider", handshake.providerId())
                .param("state", handshake.state())
                .param("nonce", handshake.nonce())
                .param("verifier", handshake.codeVerifier())
                .param("redirect", handshake.redirectAfterLogin())
                .param("created", Timestamp.from(handshake.createdAt()))
                .update();
    }

    @Override
    public Optional<SsoHandshake> byState(String state) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sso_handshake WHERE state = :state")
                .param("state", state)
                .query(this::map).optional();
    }

    /**
     * O uso único, decidido pelo banco.
     *
     * <p>O {@code WHERE consumed_at IS NULL} é o ponto inteiro deste método. Duas voltas simultâneas com a
     * mesma resposta — um duplo clique, um retry do navegador, uma aba duplicada — passariam as duas por
     * uma checagem feita em memória antes de gravar. Aqui exatamente uma vence, e a outra recebe zero
     * linhas afetadas.
     */
    @Override
    public boolean markConsumed(SsoHandshake handshake) {
        return jdbc.sql("""
                UPDATE sso_handshake SET consumed_at = :consumed
                WHERE id = :id AND consumed_at IS NULL
                """)
                .param("consumed", Timestamp.from(handshake.consumedAt()))
                .param("id", handshake.id())
                .update() == 1;
    }

    private SsoHandshake map(ResultSet rs, int rowNum) throws SQLException {
        var consumed = rs.getTimestamp("consumed_at");
        return SsoHandshake.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("provider_id", UUID.class),
                rs.getString("state"),
                rs.getString("nonce"),
                rs.getString("code_verifier"),
                rs.getString("redirect_after_login"),
                rs.getTimestamp("created_at").toInstant(),
                consumed == null ? null : consumed.toInstant());
    }
}
