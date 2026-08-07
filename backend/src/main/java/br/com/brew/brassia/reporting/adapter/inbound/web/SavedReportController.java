package br.com.brew.brassia.reporting.adapter.inbound.web;

import br.com.brew.brassia.reporting.adapter.inbound.web.dto.SavedReportDtos;
import br.com.brew.brassia.reporting.application.port.inbound.SavedReportUseCases;
import br.com.brew.brassia.reporting.application.port.outbound.SavedReportRepository;
import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Relatórios salvos e entrega programada (RPT-003).
 *
 * <p>O link de download é <strong>temporário e pessoal</strong>: um token com prazo, emitido para
 * um usuário, e não o id da execução. O id vive no banco para sempre; o link tem de morrer. Cada
 * abertura é auditada, e link vencido responde igual a link inexistente — dizer qual dos dois foi
 * ensinaria a diferença a quem está testando tokens.
 */
@RestController
@RequestMapping("/api/v1/reporting/saved-reports")
final class SavedReportController {

    /** Duas horas: prazo para abrir o link recém-emitido, não a retenção do artefato. */
    private static final Duration LINK_TTL = Duration.ofHours(2);

    private final SavedReportUseCases.Queries queries;
    private final SavedReportUseCases.Define define;
    private final SavedReportUseCases.Redefine redefine;
    private final SavedReportUseCases.Activate activate;
    private final SavedReportUseCases.Run run;
    private final SavedReportUseCases.Deliver deliver;
    private final SavedReportRepository repository;

    SavedReportController(SavedReportUseCases.Queries queries, SavedReportUseCases.Define define,
            SavedReportUseCases.Redefine redefine, SavedReportUseCases.Activate activate,
            SavedReportUseCases.Run run, SavedReportUseCases.Deliver deliver,
            SavedReportRepository repository) {
        this.queries = queries;
        this.define = define;
        this.redefine = redefine;
        this.activate = activate;
        this.run = run;
        this.deliver = deliver;
        this.repository = repository;
    }

    @GetMapping
    List<SavedReportDtos.SavedReportView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reporting.saved.read");
        return SavedReportDtos.SavedReportView.from(queries.findAll(principal.requireBrewery()));
    }

    @GetMapping("/{reportId}")
    SavedReportDtos.SavedReportView ofId(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID reportId) {
        principal.requirePermission("reporting.saved.read");
        return SavedReportDtos.SavedReportView.from(
                queries.ofId(principal.requireBrewery(), reportId));
    }

    @GetMapping("/{reportId}/runs")
    List<SavedReportDtos.ReportRunView> runs(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID reportId) {
        principal.requirePermission("reporting.saved.read");
        return SavedReportDtos.ReportRunView.from(
                queries.runsOf(principal.requireBrewery(), reportId), Instant.now());
    }

    @PostMapping
    ResponseEntity<SavedReportDtos.SavedReportView> define(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SavedReportDtos.DefineRequest request) {
        principal.requirePermission("reporting.saved.manage");
        var report = define.handle(principal.userId(), principal.requireBrewery(),
                new SavedReportUseCases.Define.Command(request.name(), request.kind(),
                        request.filters() == null ? java.util.Map.of() : request.filters(),
                        zoneOf(request.timezone()), request.format(), request.schedule(),
                        request.retentionDays(), request.ownerUserId(), orEmpty(request.recipients())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SavedReportDtos.SavedReportView.from(report));
    }

    @PutMapping("/{reportId}")
    SavedReportDtos.SavedReportView redefine(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody SavedReportDtos.RedefineRequest request) {
        principal.requirePermission("reporting.saved.manage");
        return SavedReportDtos.SavedReportView.from(redefine.handle(principal.userId(),
                principal.requireBrewery(), reportId,
                new SavedReportUseCases.Redefine.Command(
                        request.filters() == null ? java.util.Map.of() : request.filters(),
                        zoneOf(request.timezone()), request.schedule(), request.retentionDays(),
                        orEmpty(request.recipients()))));
    }

    @PostMapping("/{reportId}/active")
    SavedReportDtos.SavedReportView activate(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID reportId, @Valid @RequestBody SavedReportDtos.ActivateRequest request) {
        principal.requirePermission("reporting.saved.manage");
        return SavedReportDtos.SavedReportView.from(activate.handle(principal.userId(),
                principal.requireBrewery(), reportId, request.active()));
    }

    /**
     * Executa agora — com a alçada do proprietário técnico, não com a de quem pediu. Pedir a
     * execução do relatório de outra pessoa não é uma forma de ler o que não se pode ler.
     */
    @PostMapping("/{reportId}/runs")
    SavedReportDtos.ReportRunView execute(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID reportId) {
        principal.requirePermission("reporting.saved.manage");
        var executed = run.handle(principal.userId(), principal.requireBrewery(), reportId);
        return SavedReportDtos.ReportRunView.from(executed, Instant.now(),
                tokenFor(principal, reportId, executed));
    }

    @PostMapping("/runs/{runId}/deliveries")
    SavedReportDtos.ReportRunView deliver(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID runId, @Valid @RequestBody SavedReportDtos.DeliverRequest request) {
        principal.requirePermission("reporting.saved.manage");
        var updated = deliver.handle(principal.userId(), principal.requireBrewery(), runId,
                request.recipientId(), request.delivered(), request.detail());
        return SavedReportDtos.ReportRunView.from(updated, Instant.now(), null);
    }

    /**
     * Emite um link temporário para esta execução, para quem tem direito a ele.
     *
     * <p>Direito é ser destinatário ou dono. Ter alçada de gerir relatórios não dá acesso ao
     * conteúdo — gerir a programação e ler o que ela produziu são coisas diferentes.
     */
    @PostMapping("/runs/{runId}/link")
    ResponseEntity<java.util.Map<String, String>> link(
            @AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID runId) {
        principal.requirePermission("reporting.saved.read");
        var breweryId = principal.requireBrewery();
        var reportRun = repository.findRun(breweryId, runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var report = repository.findById(breweryId, reportRun.reportId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!report.recipients().contains(principal.userId())
                && !report.ownerUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!reportRun.succeeded() || reportRun.expired(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE);
        }
        var now = Instant.now();
        // O link nunca vive mais do que o artefato: retenção curta manda no prazo do link.
        var expiry = min(now.plus(LINK_TTL), reportRun.expiresAt());
        var token = repository.issueToken(breweryId, runId, principal.userId(), expiry, now);
        return ResponseEntity.ok(java.util.Map.of("token", token, "expiresAt", expiry.toString()));
    }

    private String tokenFor(SecurityPrincipal principal, UUID reportId, ReportRun executed) {
        if (!executed.succeeded()) {
            return null;
        }
        var report = repository.findById(principal.requireBrewery(), reportId).orElse(null);
        if (report == null || (!report.recipients().contains(principal.userId())
                && !report.ownerUserId().equals(principal.userId()))) {
            return null;
        }
        var now = Instant.now();
        var expiry = min(now.plus(LINK_TTL), executed.expiresAt());
        return repository.issueToken(principal.requireBrewery(), executed.id(), principal.userId(),
                expiry, now);
    }

    private static Instant min(Instant one, Instant other) {
        return other != null && other.isBefore(one) ? other : one;
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("fuso desconhecido: " + timezone);
        }
    }

    private static Set<UUID> orEmpty(Set<UUID> recipients) {
        return recipients == null ? Set.of() : recipients;
    }
}
