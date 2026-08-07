package br.com.brew.brassia.reporting.adapter.inbound.web;

import br.com.brew.brassia.reporting.application.port.inbound.SavedReportUseCases;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abre o link temporário de uma execução (RPT-003).
 *
 * <p>O token não substitui a sessão: ele diz <em>qual</em> artefato, e a sessão diz <em>quem</em>.
 * Um link que autenticasse sozinho viraria uma senha em texto claro no corpo de um e-mail, repassável
 * a qualquer um.
 *
 * <p>Token vencido, token de outra pessoa e token inexistente respondem a mesma coisa. A diferença
 * entre eles só interessa a quem está testando tokens.
 */
@RestController
@RequestMapping("/api/v1/reporting/downloads")
final class ReportDownloadController {

    private final SavedReportUseCases.Download download;

    ReportDownloadController(SavedReportUseCases.Download download) {
        this.download = download;
    }

    @GetMapping("/{token}")
    ResponseEntity<String> download(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable String token) {
        principal.requirePermission("reporting.saved.read");
        var granted = download.handle(token, principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "este link não vale mais"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + granted.reportName().replaceAll("[^\\w.-]", "-")
                                + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(granted.run().content());
    }
}
