package br.com.brew.brassia.crm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A pessoa com quem se fala num cliente (CRM-001).
 *
 * <p><strong>Separada do cliente de propósito, e a separação é a decisão principal desta história.</strong>
 * O {@link Customer} é a organização compradora — dado de negócio, que o pedido, a nota e o custo
 * precisam manter para sempre. O contato é a pessoa — dado pessoal, que tem prazo e direito de
 * apagamento. Se os dois morassem na mesma entidade, atender um pedido de exclusão obrigaria a escolher
 * entre apagar a pessoa e destruir o histórico comercial, e não existe resposta boa para essa escolha.
 *
 * <p><strong>Anonimizar não é apagar a linha.</strong> A casca fica: o identificador continua existindo,
 * e com ele continuam existindo as expedições e os pedidos que apontam para cá. O que some é quem a
 * pessoa era. É a diferença entre "esta entrega foi para alguém que pediu para ser esquecido" e um
 * histórico com um buraco que ninguém sabe explicar.
 */
public final class Contact {

    private static final int MAX_NAME = 160;
    private static final int MAX_EMAIL = 254;
    private static final int MAX_PHONE = 40;
    private static final int MAX_ROLE = 80;

    private final UUID id;
    private final UUID breweryId;
    private final UUID customerId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private Instant anonymizedAt;
    private final ConsentLedger consents;

    private Contact(UUID id, UUID breweryId, UUID customerId, String name, String email, String phone,
            String role, Instant anonymizedAt, ConsentLedger consents) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria");
        this.customerId = Objects.requireNonNull(customerId, "cliente");
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.anonymizedAt = anonymizedAt;
        this.consents = Objects.requireNonNull(consents, "consentimentos");
    }

    public static Contact create(UUID id, UUID breweryId, UUID customerId, String name, String email,
            String phone, String role) {
        var cleanName = required(name, "nome", MAX_NAME);
        return new Contact(id, breweryId, customerId, cleanName, optional(email, "e-mail", MAX_EMAIL),
                optional(phone, "telefone", MAX_PHONE), optional(role, "cargo", MAX_ROLE), null,
                ConsentLedger.empty());
    }

    public static Contact reconstitute(UUID id, UUID breweryId, UUID customerId, String name, String email,
            String phone, String role, Instant anonymizedAt, ConsentLedger consents) {
        return new Contact(id, breweryId, customerId, name, email, phone, role, anonymizedAt, consents);
    }

    /** Registra que a pessoa disse sim para uma finalidade. */
    public void grant(ContactPurpose purpose, Instant at, String source, UUID recordedBy) {
        requireAlive("registrar consentimento");
        consents.record(new ConsentEntry(purpose, ConsentDecision.GRANTED, at, source, recordedBy));
    }

    /** Registra que a pessoa disse não, ou voltou atrás. */
    public void revoke(ContactPurpose purpose, Instant at, String source, UUID recordedBy) {
        requireAlive("revogar consentimento");
        consents.record(new ConsentEntry(purpose, ConsentDecision.REVOKED, at, source, recordedBy));
    }

    /**
     * Se dá para falar com esta pessoa, para esta finalidade, considerando o estado em {@code at}.
     *
     * <p>Contato anonimizado responde {@code false} para tudo, <strong>inclusive transacional</strong>.
     * A base contratual autoriza mandar aviso de entrega; ela não cria um endereço para onde mandar.
     */
    public boolean allows(ContactPurpose purpose, Instant at) {
        Objects.requireNonNull(purpose, "finalidade");
        Objects.requireNonNull(at, "instante");
        if (isAnonymized()) {
            return false;
        }
        return consents.allows(purpose, at);
    }

    /**
     * Apaga quem a pessoa era, preservando a linha e o histórico de decisões.
     *
     * <p><strong>O histórico de consentimento fica.</strong> Parece contraditório apagar a pessoa e
     * manter o que ela decidiu, mas é o contrário: é o registro de que ela pediu para sair, e sem ele a
     * cervejaria não consegue demonstrar que atendeu ao pedido. O que ele já não tem é a quem se refere.
     */
    public void anonymize(Instant at) {
        Objects.requireNonNull(at, "instante");
        if (isAnonymized()) {
            throw new ContactAnonymizedException("este contato já foi anonimizado");
        }
        this.name = null;
        this.email = null;
        this.phone = null;
        this.role = null;
        this.anonymizedAt = at;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    private void requireAlive(String action) {
        if (isAnonymized()) {
            throw new ContactAnonymizedException(
                    "não é possível " + action + ": este contato foi anonimizado");
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("o " + field + " do contato é obrigatório");
        }
        return checkLength(value.strip(), field, max);
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.strip(), field, max);
    }

    private static String checkLength(String value, String field, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException("o " + field + " do contato passa de " + max + " caracteres");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID customerId() {
        return customerId;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> email() {
        return Optional.ofNullable(email);
    }

    public Optional<String> phone() {
        return Optional.ofNullable(phone);
    }

    public Optional<String> role() {
        return Optional.ofNullable(role);
    }

    public Optional<Instant> anonymizedAt() {
        return Optional.ofNullable(anonymizedAt);
    }

    public ConsentLedger consents() {
        return consents;
    }
}
