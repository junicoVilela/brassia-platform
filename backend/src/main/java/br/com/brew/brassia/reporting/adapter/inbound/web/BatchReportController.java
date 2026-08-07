package br.com.brew.brassia.reporting.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.reporting.adapter.inbound.web.dto.BatchReportDtos;
import br.com.brew.brassia.reporting.application.port.inbound.BatchReportQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relatório do lote (RPT-001).
 *
 * <p>Ler e exportar são coisas diferentes, e por isso são dois verbos e duas alçadas. Ler é
 * consulta; <strong>exportar tira o documento de dentro do sistema</strong> — a partir dali ele
 * vive num e-mail, num pen drive, na mão de um auditor, e nada mais o traz de volta. Por isso a
 * exportação é POST e é auditada: não pelo que ela calcula, mas pelo que ela permite.
 */
@RestController
@RequestMapping("/api/v1/reporting")
final class BatchReportController {

    private final BatchReportQueries reports;
    private final AuditTrail audit;

    BatchReportController(BatchReportQueries reports, AuditTrail audit) {
        this.reports = reports;
        this.audit = audit;
    }

    @GetMapping("/batches/{batchId}")
    BatchReportDtos.BatchReportView report(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID batchId) {
        principal.requirePermission("reporting.batch.read");
        return BatchReportDtos.BatchReportView.from(reports.ofBatch(principal.requireBrewery(), batchId));
    }

    @PostMapping("/batches/{batchId}/export")
    ResponseEntity<BatchReportDtos.BatchReportView> export(
            @AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID batchId) {
        principal.requirePermission("reporting.batch.export");
        var breweryId = principal.requireBrewery();
        var report = reports.ofBatch(breweryId, batchId);

        // O registro sai depois de o relatório existir: auditar uma exportação que falhou seria
        // afirmar que o documento saiu quando ele nem chegou a ser montado.
        audit.record(AuditEvent.success(breweryId, principal.userId(), "reporting.batch.export",
                "production.batch", batchId.toString(),
                Map.of("batchCode", report.batchCode(), "incomplete",
                        String.valueOf(report.incomplete()))));

        var view = BatchReportDtos.BatchReportView.from(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"relatorio-" + report.batchCode() + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(view);
    }
}
