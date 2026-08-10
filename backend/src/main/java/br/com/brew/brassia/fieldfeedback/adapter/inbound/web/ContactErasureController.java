package br.com.brew.brassia.fieldfeedback.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fieldfeedback.application.port.outbound.ComplaintRepository;
import br.com.brew.brassia.fieldfeedback.domain.UnknownComplaintException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apagamento de dados pessoais a pedido (FLD-001).
 *
 * <p><strong>Endpoint próprio, permissão crítica própria, e a reclamação sobrevive.</strong> O apagamento
 * esvazia o contato e preserva o registro de qualidade — que é o desenho inteiro desta história: a
 * investigação de um corpo estranho precisa durar anos, o telefone de quem ligou não.
 *
 * <p>É {@code DELETE} sobre o contato, e não sobre a reclamação, exatamente para tornar essa distinção
 * impossível de confundir na chamada.
 */
@RestController
@RequestMapping("/api/v1/field-feedback/complaints/{complaintId}/contact")
final class ContactErasureController {

    private final ComplaintRepository complaints;
    private final AuditTrail audit;
    private final Clock clock;

    ContactErasureController(ComplaintRepository complaints, AuditTrail audit) {
        this.complaints = complaints;
        this.audit = audit;
        this.clock = Clock.systemUTC();
    }

    @DeleteMapping
    ResponseEntity<Void> erase(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID complaintId) {
        principal.requirePermission("feedback.contact.erase");
        var brewery = principal.requireBrewery();
        // Conferir a existência antes evita que um apagamento sobre reclamação de outra cervejaria
        // responda 204 — o que informaria, pelo silêncio, que o id existe em algum lugar.
        if (complaints.find(brewery, complaintId).isEmpty()) {
            throw new UnknownComplaintException(complaintId);
        }
        complaints.eraseContact(brewery, complaintId, clock.instant());
        // O apagamento é auditado sem nenhum dado do que foi apagado: uma trilha que guardasse o nome
        // apagado desfaria o apagamento.
        audit.record(AuditEvent.success(brewery, principal.userId(), "feedback.contact.erase",
                "field_complaint_contact", complaintId.toString(), Map.of()));
        return ResponseEntity.noContent().build();
    }
}
