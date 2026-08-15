package br.com.brew.brassia.crm.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O histórico de decisões de consentimento de um contato (CRM-001).
 *
 * <p><strong>Só cresce.</strong> Não há remoção nem edição, e é isso que torna o consentimento
 * auditável: a pergunta que a cervejaria vai precisar responder não é "ela aceita?", é "ela aceitava
 * quando mandamos aquilo?". A segunda só tem resposta se as decisões antigas continuarem existindo.
 *
 * <p><strong>A ordem é a do mundo, não a da inserção.</strong> Uma decisão tomada por telefone na
 * segunda pode ser digitada na quarta, depois de outra já registrada. Por isso a consulta ordena por
 * {@code at} e não pela posição na lista — usar a ordem de digitação faria a base responder com a
 * decisão errada justamente nos casos em que alguém foi conferir.
 */
public final class ConsentLedger {

    private final List<ConsentEntry> entries;

    private ConsentLedger(List<ConsentEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public static ConsentLedger empty() {
        return new ConsentLedger(List.of());
    }

    public static ConsentLedger reconstitute(List<ConsentEntry> entries) {
        return new ConsentLedger(Objects.requireNonNull(entries, "decisões"));
    }

    public void record(ConsentEntry entry) {
        entries.add(Objects.requireNonNull(entry, "decisão"));
    }

    /**
     * A decisão que valia em {@code at} para a finalidade, se houver alguma.
     *
     * <p>Decisão posterior a {@code at} é ignorada de propósito: perguntar "podia em março?" e receber a
     * resposta de hoje é o erro que faz a auditoria concluir que houve infração onde não houve — ou o
     * contrário, que é pior.
     */
    public Optional<ConsentEntry> decisionAt(ContactPurpose purpose, Instant at) {
        Objects.requireNonNull(purpose, "finalidade");
        Objects.requireNonNull(at, "instante");
        return entries.stream()
                .filter(e -> e.purpose() == purpose)
                .filter(e -> !e.at().isAfter(at))
                .max(Comparator.comparing(ConsentEntry::at));
    }

    /**
     * Se a finalidade estava permitida em {@code at}.
     *
     * <p>Sem decisão nenhuma, <strong>não</strong>. Silêncio não é permissão, e a diferença entre
     * "nunca perguntamos" e "ela recusou" fica preservada no histórico, não no resultado.
     */
    public boolean allows(ContactPurpose purpose, Instant at) {
        if (!purpose.requiresConsent()) {
            // Finalidade contratual não depende do que está aqui: ela se apoia no que foi vendido.
            return true;
        }
        return decisionAt(purpose, at)
                .map(e -> e.decision() == ConsentDecision.GRANTED)
                .orElse(false);
    }

    /** As decisões, em ordem cronológica. Cópia: o histórico não se altera por fora. */
    public List<ConsentEntry> entries() {
        return entries.stream().sorted(Comparator.comparing(ConsentEntry::at)).toList();
    }
}
