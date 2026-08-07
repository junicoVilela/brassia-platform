package br.com.brew.brassia.reporting.application.port.outbound;

import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.reporting.domain.SavedReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência das definições, execuções e links (RPT-003). */
public interface SavedReportRepository {

    void save(SavedReport report);

    void update(SavedReport report);

    Optional<SavedReport> findById(UUID breweryId, UUID reportId);

    List<SavedReport> findAll(UUID breweryId);

    /** Definições ativas e programadas de todas as cervejarias — é o que o agendador percorre. */
    List<SavedReport> findScheduled();

    /**
     * Grava a execução, ou devolve a que já existe para a mesma chave.
     *
     * <p>É aqui que a idempotência mora, e a garantia é do banco: o índice único em (relatório,
     * chave) transforma uma corrida entre dois agendadores num conflito, e o conflito devolve a
     * execução existente em vez de criar a segunda.
     */
    ReportRun saveRun(ReportRun run);

    Optional<ReportRun> findRun(UUID breweryId, UUID runId);

    Optional<ReportRun> findRunByKey(UUID reportId, String idempotencyKey);

    List<ReportRun> findRuns(UUID breweryId, UUID reportId);

    void updateDeliveries(ReportRun run);

    /** Emite o link temporário para um usuário e devolve o token. */
    String issueToken(UUID breweryId, UUID runId, UUID userId, Instant expiresAt, Instant now);

    Optional<TokenGrant> findToken(String token);

    /** O que um token concede: qual execução, para quem e até quando. */
    record TokenGrant(String token, UUID runId, UUID breweryId, UUID userId, Instant expiresAt) {

        public boolean expired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
