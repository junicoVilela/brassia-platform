package br.com.brew.brassia.crm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma decisão da pessoa sobre uma finalidade, no instante em que ela foi tomada (CRM-001).
 *
 * <p><strong>É um fato, não um estado.</strong> A entrada nunca é alterada nem removida: revogar
 * acrescenta uma linha, e conceder de novo acrescenta outra. Guardar só o estado atual responderia
 * "ela aceita hoje?" e perderia "ela aceitava em março?" — que é exatamente a pergunta que se faz quando
 * alguém contesta uma mensagem recebida, e a única que prova quem estava certo.
 *
 * @param purpose a finalidade decidida
 * @param decision o que foi decidido
 * @param at quando — em UTC, como todo instante persistido no projeto
 * @param source de onde veio a decisão, para ser auditável ("formulário do site", "assinatura em
 *     contrato", "pedido por telefone"). Texto livre porque a origem é do mundo, não do sistema
 * @param recordedBy quem registrou. Pode ser diferente de quem decidiu: um vendedor registra o que o
 *     cliente disse no telefone, e a auditoria precisa saber que houve um intermediário
 */
public record ConsentEntry(ContactPurpose purpose, ConsentDecision decision, Instant at, String source,
        UUID recordedBy) {

    private static final int MAX_SOURCE = 200;

    public ConsentEntry {
        Objects.requireNonNull(purpose, "finalidade");
        Objects.requireNonNull(decision, "decisão");
        Objects.requireNonNull(at, "instante da decisão");
        Objects.requireNonNull(recordedBy, "quem registrou");
        if (source == null || source.isBlank()) {
            // Sem origem a entrada não é auditável: ela afirma que houve consentimento sem dizer como,
            // e um consentimento que não se consegue demonstrar vale o mesmo que nenhum.
            throw new IllegalArgumentException("a origem da decisão é obrigatória");
        }
        source = source.strip();
        if (source.length() > MAX_SOURCE) {
            throw new IllegalArgumentException("a origem da decisão passa de " + MAX_SOURCE + " caracteres");
        }
        if (!purpose.requiresConsent()) {
            // Registrar consentimento para finalidade contratual daria a entender que ela depende disso —
            // e o dia em que alguém "revogasse" o aviso de entrega, a cervejaria pararia de poder entregar.
            throw new IllegalArgumentException(
                    "a finalidade " + purpose + " não se apoia em consentimento, e sim em " + purpose.basis());
        }
    }
}
