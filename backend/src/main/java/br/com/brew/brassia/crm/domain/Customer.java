package br.com.brew.brassia.crm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A organização que compra (CRM-001).
 *
 * <p><strong>Dado de negócio, e não dado pessoal</strong> — a distinção é o que sustenta o resto do
 * módulo. Bar, restaurante e distribuidor não têm direito ao esquecimento; o pedido, a nota e o custo
 * precisam continuar apontando para eles. Quem tem prazo e apagamento é o {@link Contact}, que mora
 * separado justamente por isso.
 *
 * <p><strong>Não se apaga cliente: desativa-se.</strong> Remover a linha deixaria pedido e expedição
 * apontando para o nada, e é o histórico de expedição que um recall percorre para saber a quem avisar.
 * Desativar tira das listas de venda e mantém o passado legível.
 *
 * <p><strong>O documento não é validado aqui, e isso é decisão registrada.</strong> Ver DEC-CRM-002:
 * cliente estrangeiro não tem CNPJ, e recusar cadastro por formato é a plataforma decidindo com quem a
 * cervejaria pode vender.
 */
public final class Customer {

    private static final int MAX_LEGAL_NAME = 200;
    private static final int MAX_TRADE_NAME = 200;
    private static final int MAX_TAX_ID = 40;

    private final UUID id;
    private final UUID breweryId;
    private String legalName;
    private String tradeName;
    private String taxId;
    private boolean active;
    private final Instant createdAt;

    private Customer(UUID id, UUID breweryId, String legalName, String tradeName, String taxId,
            boolean active, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria");
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.taxId = taxId;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "criado em");
    }

    public static Customer create(UUID id, UUID breweryId, String legalName, String tradeName, String taxId,
            Instant createdAt) {
        return new Customer(id, breweryId, required(legalName, "razão social", MAX_LEGAL_NAME),
                optional(tradeName, "nome fantasia", MAX_TRADE_NAME), optional(taxId, "documento", MAX_TAX_ID),
                true, createdAt);
    }

    public static Customer reconstitute(UUID id, UUID breweryId, String legalName, String tradeName,
            String taxId, boolean active, Instant createdAt) {
        return new Customer(id, breweryId, legalName, tradeName, taxId, active, createdAt);
    }

    public void rename(String legalName, String tradeName) {
        this.legalName = required(legalName, "razão social", MAX_LEGAL_NAME);
        this.tradeName = optional(tradeName, "nome fantasia", MAX_TRADE_NAME);
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    /** O nome que se mostra na tela: fantasia quando existe, razão social quando não. */
    public String displayName() {
        return tradeName == null ? legalName : tradeName;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a " + field + " do cliente é obrigatória");
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
            throw new IllegalArgumentException("o campo " + field + " passa de " + max + " caracteres");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String legalName() {
        return legalName;
    }

    public Optional<String> tradeName() {
        return Optional.ofNullable(tradeName);
    }

    public Optional<String> taxId() {
        return Optional.ofNullable(taxId);
    }

    public boolean isActive() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
