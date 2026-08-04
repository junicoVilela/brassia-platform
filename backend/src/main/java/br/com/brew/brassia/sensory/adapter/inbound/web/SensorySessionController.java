package br.com.brew.brassia.sensory.adapter.inbound.web;

import br.com.brew.brassia.sensory.adapter.inbound.web.dto.SensoryDtos;
import br.com.brew.brassia.sensory.adapter.inbound.web.dto.SensoryViews;
import br.com.brew.brassia.sensory.application.port.inbound.SensoryQueries;
import br.com.brew.brassia.sensory.application.port.inbound.SessionCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessões sensoriais (SEN-001).
 *
 * <p>A cegueira é defendida na resposta, não na tela: a amostra sai sem lote enquanto a sessão não
 * é encerrada, e o resultado é recusado com 409. Deixar isso para o frontend significaria que
 * qualquer cliente da API — inclusive o navegador com o devtools aberto — enxergaria o que o
 * provador não deveria.
 */
@RestController
@RequestMapping("/api/v1/sensory/sessions")
final class SensorySessionController {

    private final SessionCommands.Create create;
    private final SessionCommands.Amend amend;
    private final SessionCommands.AddSample addSample;
    private final SessionCommands.RemoveSample removeSample;
    private final SessionCommands.Open open;
    private final SessionCommands.Close close;
    private final SessionCommands.SubmitEvaluation submit;
    private final SensoryQueries queries;

    SensorySessionController(SessionCommands.Create create, SessionCommands.Amend amend,
            SessionCommands.AddSample addSample, SessionCommands.RemoveSample removeSample,
            SessionCommands.Open open, SessionCommands.Close close,
            SessionCommands.SubmitEvaluation submit, SensoryQueries queries) {
        this.create = create;
        this.amend = amend;
        this.addSample = addSample;
        this.removeSample = removeSample;
        this.open = open;
        this.close = close;
        this.submit = submit;
        this.queries = queries;
    }

    @GetMapping
    List<SensoryViews.SessionView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.read");
        var brewery = principal.requireBrewery();
        return queries.sessions(brewery).stream()
                .map(s -> SensoryViews.SessionView.from(s, queries.evaluationCount(brewery, s.id())))
                .toList();
    }

    @GetMapping("/attributes")
    List<SensoryViews.AttributeView> attributes(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.read");
        return SensoryViews.AttributeView.all();
    }

    @GetMapping("/{id}")
    SensoryViews.SessionView get(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.read");
        return view(principal.requireBrewery(), id);
    }

    @PostMapping
    ResponseEntity<SensoryViews.SessionView> create(@Valid @RequestBody SensoryDtos.CreateSession body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.manage");
        var brewery = principal.requireBrewery();
        var id = create.handle(new SessionCommands.Create.Command(principal.userId(), brewery, body.code(),
                body.purpose(), body.scheduledFor()));
        return ResponseEntity.created(URI.create("/api/v1/sensory/sessions/" + id)).body(view(brewery, id));
    }

    @PutMapping("/{id}")
    SensoryViews.SessionView amend(@PathVariable UUID id, @Valid @RequestBody SensoryDtos.AmendSession body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.manage");
        var brewery = principal.requireBrewery();
        amend.handle(new SessionCommands.Amend.Command(principal.userId(), brewery, id, body.purpose(),
                body.scheduledFor()));
        return view(brewery, id);
    }

    @PostMapping("/{id}/samples")
    ResponseEntity<SensoryViews.SessionView> addSample(@PathVariable UUID id,
            @Valid @RequestBody SensoryDtos.AddSample body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.manage");
        var brewery = principal.requireBrewery();
        addSample.handle(new SessionCommands.AddSample.Command(principal.userId(), brewery, id,
                body.batchId(), body.note()));
        return ResponseEntity.created(URI.create("/api/v1/sensory/sessions/" + id)).body(view(brewery, id));
    }

    @DeleteMapping("/{id}/samples/{sampleId}")
    SensoryViews.SessionView removeSample(@PathVariable UUID id, @PathVariable UUID sampleId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.manage");
        var brewery = principal.requireBrewery();
        removeSample.handle(new SessionCommands.RemoveSample.Command(principal.userId(), brewery, id,
                sampleId));
        return view(brewery, id);
    }

    @PostMapping("/{id}/open")
    SensoryViews.SessionView open(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.manage");
        var brewery = principal.requireBrewery();
        open.handle(new SessionCommands.Open.Command(principal.userId(), brewery, id));
        return view(brewery, id);
    }

    @PostMapping("/{id}/close")
    SensoryViews.SessionView close(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.manage");
        var brewery = principal.requireBrewery();
        close.handle(new SessionCommands.Close.Command(principal.userId(), brewery, id));
        return view(brewery, id);
    }

    @PostMapping("/{id}/evaluations")
    ResponseEntity<SensoryViews.SessionView> submit(@PathVariable UUID id,
            @Valid @RequestBody SensoryDtos.SubmitEvaluation body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.evaluation.submit");
        var brewery = principal.requireBrewery();
        submit.handle(new SessionCommands.SubmitEvaluation.Command(principal.userId(), brewery, id,
                body.sampleId(), body.scores(), body.descriptors(), body.note()));
        return ResponseEntity.created(URI.create("/api/v1/sensory/sessions/" + id)).body(view(brewery, id));
    }

    /** Recusado com 409 enquanto a sessão não é encerrada. */
    @GetMapping("/{id}/results")
    SensoryViews.ResultsView results(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.read");
        return SensoryViews.ResultsView.from(queries.results(principal.requireBrewery(), id));
    }

    private SensoryViews.SessionView view(UUID brewery, UUID id) {
        return queries.session(brewery, id)
                .map(s -> SensoryViews.SessionView.from(s, queries.evaluationCount(brewery, s.id())))
                .orElseThrow(() -> new IllegalArgumentException("sessão sensorial inexistente"));
    }
}
