package br.com.brew.brassia.fieldfeedback.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Quem reclamou (FLD-001) — o dado pessoal, deliberadamente fora da reclamação.
 *
 * <p><strong>Por que é um agregado separado e não campos em {@link FieldComplaint}.</strong> Quatro razões,
 * e nenhuma é organização de código:
 *
 * <ul>
 *   <li><strong>Tempo de vida diferente.</strong> A investigação de um corpo estranho precisa sobreviver
 *       anos; o telefone de quem ligou, não. Separado, um apaga sem levar o outro.
 *   <li><strong>Alcance diferente.</strong> Quem analisa off-flavor precisa do lote, da temperatura de
 *       armazenagem e da amostra — não do endereço do consumidor. Junto, todo mundo que abre a
 *       reclamação lê o dado pessoal de graça.
 *   <li><strong>Acesso auditável.</strong> Numa tabela própria com endpoint próprio, cada leitura é um
 *       evento. Como coluna, a leitura acontece dentro de um SELECT que ninguém consegue distinguir.
 *   <li><strong>Erro por omissão vira erro visível.</strong> Um DTO de reclamação que não tem campo para
 *       nome não vaza nome por esquecimento — não há o que esquecer de remover.
 * </ul>
 *
 * <p>O contato é opcional: reclamação anônima é reclamação. Um sistema que exige identificar o consumidor
 * para registrar um corpo estranho coleta dado que não precisa e perde o relato de quem não quis se
 * identificar.
 */
public final class ComplainantContact {

    private final UUID complaintId;
    private final String name;
    private final String email;
    private final String phone;
    private final String address;
    private final UUID recordedBy;
    private final Instant recordedAt;

    private boolean erased;
    private Instant erasedAt;

    private ComplainantContact(UUID complaintId, String name, String email, String phone,
            String address, boolean erased, Instant erasedAt, UUID recordedBy, Instant recordedAt) {
        this.complaintId = complaintId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.erased = erased;
        this.erasedAt = erasedAt;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public static ComplainantContact record(UUID complaintId, String name, String email, String phone,
            String address, UUID recordedBy, Instant recordedAt) {
        Objects.requireNonNull(complaintId, "complaintId");
        Objects.requireNonNull(recordedBy, "recordedBy");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (blank(name) && blank(email) && blank(phone) && blank(address)) {
            // Contato vazio não é contato: seria uma linha de dado pessoal sem dado, aumentando a
            // superfície sem informar nada.
            throw new IllegalArgumentException("informe ao menos um meio de contato");
        }
        return new ComplainantContact(complaintId, trimmed(name), trimmed(email), trimmed(phone),
                trimmed(address), false, null, recordedBy, recordedAt);
    }

    public static ComplainantContact reconstitute(UUID complaintId, String name, String email,
            String phone, String address, boolean erased, Instant erasedAt, UUID recordedBy,
            Instant recordedAt) {
        return new ComplainantContact(complaintId, name, email, phone, address, erased, erasedAt,
                recordedBy, recordedAt);
    }

    /**
     * Apaga os dados pessoais preservando a reclamação.
     *
     * <p>A linha não some, e é de propósito: some o <em>conteúdo</em>, fica o registro de que houve
     * contato e de quando ele foi apagado. Apagar a linha inteira tornaria indistinguível "reclamação
     * anônima desde o início" de "dados apagados a pedido" — e a segunda é um fato que precisa ser
     * demonstrável, inclusive para quem pediu o apagamento.
     */
    public void erase(Instant at) {
        this.erased = true;
        this.erasedAt = Objects.requireNonNull(at, "at");
    }

    public boolean erased() {
        return erased;
    }

    public UUID complaintId() {
        return complaintId;
    }

    public Optional<String> name() {
        return erased ? Optional.empty() : Optional.ofNullable(name);
    }

    public Optional<String> email() {
        return erased ? Optional.empty() : Optional.ofNullable(email);
    }

    public Optional<String> phone() {
        return erased ? Optional.empty() : Optional.ofNullable(phone);
    }

    public Optional<String> address() {
        return erased ? Optional.empty() : Optional.ofNullable(address);
    }

    public Optional<Instant> erasedAt() {
        return Optional.ofNullable(erasedAt);
    }

    public UUID recordedBy() {
        return recordedBy;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmed(String value) {
        return blank(value) ? null : value.trim();
    }
}
