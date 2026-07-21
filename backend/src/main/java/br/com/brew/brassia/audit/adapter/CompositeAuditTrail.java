package br.com.brew.brassia.audit.adapter;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Trilha de auditoria efetiva: registra o evento tanto no log estruturado
 * (observabilidade) quanto na tabela append-only (consulta). Os módulos injetam
 * {@link AuditTrail} e recebem este composite.
 */
@Primary
@Component
public class CompositeAuditTrail implements AuditTrail {
    private final LoggingAuditTrail logging;
    private final JdbcAuditTrail jdbc;

    CompositeAuditTrail(LoggingAuditTrail logging, JdbcAuditTrail jdbc) {
        this.logging = logging;
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditEvent event) {
        logging.record(event);
        jdbc.record(event);
    }
}
