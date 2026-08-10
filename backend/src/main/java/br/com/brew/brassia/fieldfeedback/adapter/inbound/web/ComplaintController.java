package br.com.brew.brassia.fieldfeedback.adapter.inbound.web;

import br.com.brew.brassia.fieldfeedback.adapter.inbound.web.ComplaintDtos.ComplaintResponse;
import br.com.brew.brassia.fieldfeedback.adapter.inbound.web.ComplaintDtos.ContactResponse;
import br.com.brew.brassia.fieldfeedback.application.port.inbound.ComplaintCommands;
import br.com.brew.brassia.fieldfeedback.application.port.inbound.ComplaintQueries;
import br.com.brew.brassia.fieldfeedback.domain.ComplaintCategory;
import br.com.brew.brassia.fieldfeedback.domain.RequiredAction;
import br.com.brew.brassia.fieldfeedback.domain.SampleRetention;
import br.com.brew.brassia.fieldfeedback.domain.Severity;
import br.com.brew.brassia.fieldfeedback.domain.StorageReport;
import br.com.brew.brassia.fieldfeedback.domain.UnknownComplaintException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reclamações de campo (FLD-001).
 *
 * <p><strong>O dado pessoal está atrás de um endpoint e de uma permissão próprios.</strong> Quem analisa
 * off-flavor precisa do lote, da armazenagem e da amostra; não do endereço do consumidor. Uma permissão
 * única faria todo analista ler dado pessoal de graça, todo dia, sem precisar — e a auditoria registraria
 * um volume em que o acesso que importa ficaria invisível.
 */
@RestController
@RequestMapping("/api/v1/field-feedback/complaints")
final class ComplaintController {

    private final ComplaintCommands commands;
    private final ComplaintQueries queries;

    ComplaintController(ComplaintCommands commands, ComplaintQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    List<ComplaintResponse> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) UUID batchId) {
        principal.requirePermission("feedback.complaint.read");
        return queries.list(principal.requireBrewery(), batchId).stream()
                .map(ComplaintResponse::from).toList();
    }

    @GetMapping("/{complaintId}")
    ComplaintResponse get(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID complaintId) {
        principal.requirePermission("feedback.complaint.read");
        return queries.find(principal.requireBrewery(), complaintId).map(ComplaintResponse::from)
                .orElseThrow(() -> new UnknownComplaintException(complaintId));
    }

    @PostMapping
    ResponseEntity<ComplaintResponse> register(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody RegisterRequest request) {
        principal.requirePermission("feedback.complaint.write");
        var complaint = commands.register(new ComplaintCommands.RegisterCommand(
                principal.requireBrewery(), request.batchId(), request.reference(), request.category(),
                request.severity(), request.description(),
                request.storage() == null ? StorageReport.unknown() : request.storage().toDomain(),
                request.sample() == null ? SampleRetention.unknown() : request.sample().toDomain(),
                request.contact() == null ? null : request.contact().toInput(),
                principal.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ComplaintResponse.from(complaint));
    }

    @PostMapping("/{complaintId}/analysis")
    ComplaintResponse startAnalysis(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID complaintId) {
        principal.requirePermission("feedback.complaint.write");
        return ComplaintResponse.from(
                commands.startAnalysis(principal.requireBrewery(), complaintId, principal.userId()));
    }

    /** Registra que a exigência foi atendida, apontando para o que foi criado. */
    @PostMapping("/{complaintId}/actions/{action}/fulfillment")
    ComplaintResponse fulfill(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID complaintId, @PathVariable RequiredAction action,
            @Valid @RequestBody FulfillRequest request) {
        principal.requirePermission("feedback.complaint.write");
        return ComplaintResponse.from(commands.fulfill(principal.requireBrewery(), complaintId, action,
                request.referenceId(), principal.userId()));
    }

    /** Dispensa a exigência. A justificativa vai inteira para a auditoria. */
    @PostMapping("/{complaintId}/actions/{action}/waiver")
    ComplaintResponse waive(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID complaintId, @PathVariable RequiredAction action,
            @Valid @RequestBody WaiveRequest request) {
        principal.requirePermission("feedback.complaint.write");
        return ComplaintResponse.from(commands.waive(principal.requireBrewery(), complaintId, action,
                request.justification(), principal.userId()));
    }

    @PostMapping("/{complaintId}/closure")
    ComplaintResponse close(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID complaintId, @RequestBody(required = false) CloseRequest request) {
        principal.requirePermission("feedback.complaint.write");
        return ComplaintResponse.from(commands.close(principal.requireBrewery(), complaintId,
                request == null ? null : request.note(), principal.userId()));
    }

    /**
     * O dado pessoal.
     *
     * <p>Permissão crítica própria, e <strong>cada chamada é auditada</strong> — inclusive quando não há
     * contato. Registrar só o acerto deixaria de fora quem varre reclamações procurando dados.
     */
    @GetMapping("/{complaintId}/contact")
    ContactResponse contact(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID complaintId) {
        principal.requirePermission("feedback.contact.read");
        return queries.contact(principal.requireBrewery(), complaintId, principal.userId())
                .map(ContactResponse::from)
                .orElseThrow(() -> new UnknownComplaintException(complaintId));
    }

    record RegisterRequest(
            @NotNull UUID batchId,
            @Size(max = 80) String reference,
            @NotNull ComplaintCategory category,
            @NotNull Severity severity,
            @NotBlank @Size(max = 4000) String description,
            StorageRequest storage,
            SampleRequest sample,
            /* Opcional: reclamação anônima é reclamação. */
            ContactRequest contact) {
    }

    record StorageRequest(BigDecimal temperatureCelsius, Integer daysSincePurchase,
            Boolean exposedToLight, @Size(max = 2000) String notes) {

        StorageReport toDomain() {
            return new StorageReport(temperatureCelsius, daysSincePurchase, exposedToLight, notes);
        }
    }

    record SampleRequest(@NotNull SampleRetention.Status status, @Size(max = 200) String location) {

        SampleRetention toDomain() {
            return new SampleRetention(status, location);
        }
    }

    record ContactRequest(@Size(max = 200) String name, @Size(max = 200) String email,
            @Size(max = 40) String phone, @Size(max = 400) String address) {

        ComplaintCommands.ContactInput toInput() {
            return new ComplaintCommands.ContactInput(name, email, phone, address);
        }
    }

    record FulfillRequest(@NotNull UUID referenceId) {
    }

    record WaiveRequest(@NotBlank @Size(max = 2000) String justification) {
    }

    record CloseRequest(@Size(max = 2000) String note) {
    }
}
