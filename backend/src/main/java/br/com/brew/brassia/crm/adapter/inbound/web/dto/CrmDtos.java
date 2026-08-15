package br.com.brew.brassia.crm.adapter.inbound.web.dto;

import br.com.brew.brassia.crm.domain.ContactPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos de entrada e saída da CRM-001. */
public final class CrmDtos {

    private CrmDtos() {
    }

    public record CreateCustomerRequest(@NotBlank @Size(max = 200) String legalName,
            @Size(max = 200) String tradeName, @Size(max = 40) String taxId) {}

    public record RenameCustomerRequest(@NotBlank @Size(max = 200) String legalName,
            @Size(max = 200) String tradeName) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record CustomerView(UUID id, String legalName, String tradeName, String displayName,
            String taxId, boolean active) {}

    public record CreateContactRequest(@NotBlank @Size(max = 160) String name,
            @Email @Size(max = 254) String email, @Size(max = 40) String phone,
            @Size(max = 80) String role) {}

    /**
     * A visão do contato não devolve "pode receber?" como um booleano só.
     *
     * <p>Devolve a permissão <strong>por finalidade</strong>, porque um único indicador obrigaria a tela
     * a escolher qual finalidade representar — e escolheria errado metade das vezes. Também traz o
     * histórico, que é o que permite responder a pergunta da auditoria sem uma segunda chamada.
     */
    public record ContactView(UUID id, UUID customerId, String name, String email, String phone,
            String role, boolean anonymized, Instant anonymizedAt, List<PurposeView> purposes,
            List<ConsentEntryView> consentHistory) {}

    public record PurposeView(ContactPurpose purpose, String basis, boolean allowedNow) {}

    public record ConsentEntryView(ContactPurpose purpose, String decision, Instant decidedAt,
            String source) {}

    /**
     * O instante da decisão vem de fora, e é obrigatório.
     *
     * <p>Deixar o servidor carimbar {@code now()} transformaria "ela aceitou na segunda, registramos na
     * quarta" em "ela aceitou na quarta" — e o livro de consentimento passaria a contar a história da
     * digitação em vez da do mundo.
     */
    public record RecordConsentRequest(@NotNull ContactPurpose purpose, @NotNull Boolean granted,
            @NotNull Instant decidedAt, @NotBlank @Size(max = 200) String source) {}

    public record RetentionPolicyView(Integer daysAfterLastInteraction) {}

    public record SetRetentionRequest(@NotNull @Positive Integer daysAfterLastInteraction) {}
}
