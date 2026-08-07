package br.com.brew.brassia.reporting.application.port.inbound;

import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.reporting.domain.SavedReport;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Comandos e consultas dos relatórios salvos (RPT-003). */
public final class SavedReportUseCases {

    private SavedReportUseCases() {
    }

    public interface Queries {

        List<SavedReport> findAll(UUID breweryId);

        SavedReport ofId(UUID breweryId, UUID reportId);

        List<ReportRun> runsOf(UUID breweryId, UUID reportId);
    }

    public interface Define {

        SavedReport handle(UUID actorId, UUID breweryId, Command command);

        /**
         * @param ownerUserId quem responde pela execução. É a alçada dele, resolvida no momento de
         *                    cada execução, que decide se o relatório sai
         * @param recipients  usuários da plataforma, não endereços digitados: só de usuário se sabe
         *                    a alçada
         */
        record Command(String name, SavedReport.ReportKind kind, Map<String, String> filters,
                ZoneId timezone, SavedReport.ReportFormat format, SavedReport.Schedule schedule,
                int retentionDays, UUID ownerUserId, Set<UUID> recipients) {}
    }

    public interface Redefine {

        SavedReport handle(UUID actorId, UUID breweryId, UUID reportId, Command command);

        record Command(Map<String, String> filters, ZoneId timezone, SavedReport.Schedule schedule,
                int retentionDays, Set<UUID> recipients) {}
    }

    public interface Activate {

        SavedReport handle(UUID actorId, UUID breweryId, UUID reportId, boolean active);
    }

    /** Executa agora. A alçada usada continua sendo a do proprietário técnico, nunca a de quem pediu. */
    public interface Run {

        ReportRun handle(UUID actorId, UUID breweryId, UUID reportId);
    }

    /**
     * Registra a entrega a um destinatário.
     *
     * <p>Idempotente por (execução, destinatário): reentregar atualiza a linha e conta a tentativa,
     * e em nenhum caso refaz o relatório.
     */
    public interface Deliver {

        ReportRun handle(UUID actorId, UUID breweryId, UUID runId, UUID recipientId, boolean delivered,
                String detail);
    }

    /** Abre um link temporário, ou recusa quando ele expirou. */
    public interface Download {

        Optional<Granted> handle(String token, UUID actorId);

        record Granted(ReportRun run, String reportName) {}
    }
}
