package br.com.brew.brassia.fieldfeedback.adapter.inbound.web;

import br.com.brew.brassia.fieldfeedback.domain.ComplainantContact;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos do feedback de campo (FLD-001). */
final class ComplaintDtos {

    private ComplaintDtos() {
    }

    /**
     * A reclamação como sai na API.
     *
     * <p><strong>Não existe campo para nome, telefone, e-mail ou endereço — e essa ausência é a
     * garantia.</strong> Um DTO sem o campo não vaza o dado por esquecimento: não há o que esquecer de
     * remover quando alguém adicionar um endpoint novo. O contato tem resposta própria, permissão própria
     * e leitura auditada.
     *
     * <p>{@code pendingActions} viaja junto com {@code requiredActions}: quem consome precisa saber não só
     * o que o caso exige, mas o que ainda falta — a diferença entre as duas é o que impede o encerramento.
     */
    record ComplaintResponse(
            UUID id,
            UUID batchId,
            String reference,
            String category,
            String severity,
            String description,
            StorageResponse storage,
            SampleResponse sample,
            List<ActionResponse> requiredActions,
            List<String> pendingActions,
            List<OutcomeResponse> outcomes,
            String status,
            String closingNote,
            UUID closedBy,
            Instant closedAt,
            UUID registeredBy,
            Instant registeredAt) {

        static ComplaintResponse from(FieldComplaint complaint) {
            return new ComplaintResponse(
                    complaint.id(),
                    complaint.batchId(),
                    complaint.reference().orElse(null),
                    complaint.category().name(),
                    complaint.severity().name(),
                    complaint.description(),
                    StorageResponse.from(complaint),
                    new SampleResponse(complaint.sample().status().name(),
                            complaint.sample().location(), complaint.sample().analyzable()),
                    complaint.requiredActions().stream()
                            .map(a -> new ActionResponse(a.name(), a.description())).toList(),
                    complaint.pendingActions().stream().map(Enum::name).toList(),
                    complaint.outcomes().stream().map(OutcomeResponse::from).toList(),
                    complaint.status().name(),
                    complaint.closingNote().orElse(null),
                    complaint.closedBy().orElse(null),
                    complaint.closedAt().orElse(null),
                    complaint.registeredBy(),
                    complaint.registeredAt());
        }
    }

    /**
     * @param conditionsKnown se alguém chegou a levantar as condições. Explícito porque, sem ele, campos
     *                        nulos seriam lidos como "guardado corretamente" — e a investigação iria
     *                        procurar na produção um problema que aconteceu no depósito
     */
    record StorageResponse(BigDecimal temperatureCelsius, Integer daysSincePurchase,
            Boolean exposedToLight, String notes, boolean conditionsKnown) {

        static StorageResponse from(FieldComplaint complaint) {
            var s = complaint.storage();
            return new StorageResponse(s.approximateTemperatureCelsius(), s.daysSincePurchase(),
                    s.exposedToLight(), s.notes(), s.knownConditions());
        }
    }

    record SampleResponse(String status, String location, boolean analyzable) {
    }

    record ActionResponse(String code, String description) {
    }

    record OutcomeResponse(String action, boolean fulfilled, UUID referenceId, String justification,
            UUID decidedBy, Instant decidedAt) {

        static OutcomeResponse from(br.com.brew.brassia.fieldfeedback.domain.ActionOutcome outcome) {
            return new OutcomeResponse(outcome.action().name(), outcome.fulfilled(),
                    outcome.referenceId(), outcome.justification(), outcome.decidedBy(),
                    outcome.decidedAt());
        }
    }

    /**
     * O contato, em resposta separada.
     *
     * <p>Apagado devolve os campos vazios e {@code erased = true}: some o conteúdo, fica o fato. Devolver
     * 404 depois do apagamento tornaria indistinguível "nunca houve contato" de "os dados foram apagados",
     * e a segunda precisa ser demonstrável.
     */
    record ContactResponse(String name, String email, String phone, String address, boolean erased,
            Instant erasedAt, Instant recordedAt) {

        static ContactResponse from(ComplainantContact contact) {
            return new ContactResponse(contact.name().orElse(null), contact.email().orElse(null),
                    contact.phone().orElse(null), contact.address().orElse(null), contact.erased(),
                    contact.erasedAt().orElse(null), contact.recordedAt());
        }
    }
}
