package br.com.brew.brassia.audit.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.audit.AuditEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingAuditTrailTest {

    @Test
    void logsAuditLineWithoutLeakingSecrets() {
        var auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            new LoggingAuditTrail().record(AuditEvent.success(
                    UUID.randomUUID(), null, "recipe.create", "recipe", "r-1",
                    Map.of("name", "Hoppy Lager", "token", "super-secret-value")));

            assertThat(appender.list).hasSize(1);
            String message = appender.list.get(0).getFormattedMessage();
            assertThat(message)
                    .contains("action=recipe.create")
                    .contains("outcome=SUCCESS")
                    .contains("resourceType=recipe")
                    .contains("token=***")
                    .doesNotContain("super-secret-value");
        } finally {
            auditLogger.detachAppender(appender);
        }
    }
}
