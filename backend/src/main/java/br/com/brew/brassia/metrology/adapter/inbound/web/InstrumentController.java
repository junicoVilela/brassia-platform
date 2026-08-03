package br.com.brew.brassia.metrology.adapter.inbound.web;

import br.com.brew.brassia.metrology.adapter.inbound.web.dto.MetrologyDtos;
import br.com.brew.brassia.metrology.adapter.inbound.web.dto.MetrologyViews;
import br.com.brew.brassia.metrology.application.port.inbound.InstrumentCommands;
import br.com.brew.brassia.metrology.application.port.inbound.MetrologyQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Instrumentos de medição (MTR-001): cadastro, calibração, bloqueio e uso em ponto crítico. */
@RestController
@RequestMapping("/api/v1/metrology/instruments")
final class InstrumentController {

    private final InstrumentCommands.Register register;
    private final InstrumentCommands.Amend amend;
    private final InstrumentCommands.SetBlock setBlock;
    private final InstrumentCommands.Retire retire;
    private final InstrumentCommands.DesignateCriticalUse designate;
    private final InstrumentCommands.Calibrate calibrate;
    private final MetrologyQueries queries;

    InstrumentController(InstrumentCommands.Register register, InstrumentCommands.Amend amend,
            InstrumentCommands.SetBlock setBlock, InstrumentCommands.Retire retire,
            InstrumentCommands.DesignateCriticalUse designate, InstrumentCommands.Calibrate calibrate,
            MetrologyQueries queries) {
        this.register = register;
        this.amend = amend;
        this.setBlock = setBlock;
        this.retire = retire;
        this.designate = designate;
        this.calibrate = calibrate;
        this.queries = queries;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    @GetMapping
    List<MetrologyViews.InstrumentView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.read");
        var on = today();
        return queries.instruments(principal.requireBrewery()).stream()
                .map(i -> MetrologyViews.InstrumentView.from(i, on))
                .toList();
    }

    @GetMapping("/{id}")
    MetrologyViews.InstrumentView get(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.read");
        return queries.instrument(principal.requireBrewery(), id)
                .map(i -> MetrologyViews.InstrumentView.from(i, today()))
                .orElseThrow(() -> new IllegalArgumentException("instrumento inexistente"));
    }

    @PostMapping
    ResponseEntity<MetrologyViews.InstrumentView> register(@Valid @RequestBody MetrologyDtos.RegisterInstrument body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.manage");
        var brewery = principal.requireBrewery();
        var id = register.handle(new InstrumentCommands.Register.Command(principal.userId(), brewery,
                body.code(), body.name(), body.type(), body.rangeMin(), body.rangeMax(), body.resolution(),
                body.accuracy(), body.unit(), body.location()));
        return ResponseEntity.created(URI.create("/api/v1/metrology/instruments/" + id))
                .body(view(brewery, id));
    }

    @PutMapping("/{id}")
    MetrologyViews.InstrumentView amend(@PathVariable UUID id,
            @Valid @RequestBody MetrologyDtos.AmendInstrument body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.manage");
        var brewery = principal.requireBrewery();
        amend.handle(new InstrumentCommands.Amend.Command(principal.userId(), brewery, id, body.name(),
                body.rangeMin(), body.rangeMax(), body.resolution(), body.accuracy(), body.unit(),
                body.location()));
        return view(brewery, id);
    }

    @PostMapping("/{id}/block")
    MetrologyViews.InstrumentView block(@PathVariable UUID id,
            @Valid @RequestBody MetrologyDtos.BlockInstrument body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return setBlock(id, true, body.reason(), principal);
    }

    @PostMapping("/{id}/unblock")
    MetrologyViews.InstrumentView unblock(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return setBlock(id, false, null, principal);
    }

    private MetrologyViews.InstrumentView setBlock(UUID id, boolean blocked, String reason,
            SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.manage");
        var brewery = principal.requireBrewery();
        setBlock.handle(new InstrumentCommands.SetBlock.Command(principal.userId(), brewery, id, blocked,
                reason));
        return view(brewery, id);
    }

    @PostMapping("/{id}/retire")
    MetrologyViews.InstrumentView retire(@PathVariable UUID id,
            @Valid @RequestBody MetrologyDtos.RetireInstrument body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.manage");
        var brewery = principal.requireBrewery();
        retire.handle(new InstrumentCommands.Retire.Command(principal.userId(), brewery, id, body.reason()));
        return view(brewery, id);
    }

    /** Designar exige instrumento apto; remover a designação é sempre permitido. */
    @PutMapping("/{id}/critical-use")
    MetrologyViews.InstrumentView criticalUse(@PathVariable UUID id,
            @Valid @RequestBody MetrologyDtos.DesignateCriticalUse body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.manage");
        var brewery = principal.requireBrewery();
        designate.handle(new InstrumentCommands.DesignateCriticalUse.Command(principal.userId(), brewery, id,
                body.criticalUse()));
        return view(brewery, id);
    }

    @GetMapping("/{id}/calibrations")
    List<MetrologyViews.CalibrationView> calibrations(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.read");
        return queries.calibrations(principal.requireBrewery(), id).stream()
                .map(MetrologyViews.CalibrationView::from)
                .toList();
    }

    @PostMapping("/{id}/calibrations")
    ResponseEntity<MetrologyViews.InstrumentView> calibrate(@PathVariable UUID id,
            @Valid @RequestBody MetrologyDtos.RecordCalibration body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.manage");
        var brewery = principal.requireBrewery();
        var calibrationId = calibrate.handle(new InstrumentCommands.Calibrate.Command(principal.userId(),
                brewery, id, body.standardId(), body.performedOn(), body.dueOn(), body.performedBy(),
                body.certificateNumber(), body.result(), body.maxDeviation(), body.restriction(), body.note(),
                body.curve() == null ? java.util.List.of()
                        : body.curve().stream()
                                .map(p -> new InstrumentCommands.Calibrate.Point(p.reference(), p.measured()))
                                .toList()));
        return ResponseEntity
                .created(URI.create("/api/v1/metrology/instruments/" + id + "/calibrations/" + calibrationId))
                .body(view(brewery, id));
    }

    private MetrologyViews.InstrumentView view(UUID brewery, UUID id) {
        return queries.instrument(brewery, id)
                .map(i -> MetrologyViews.InstrumentView.from(i, today()))
                .orElseThrow(() -> new IllegalStateException("instrumento não encontrado após o comando"));
    }
}
