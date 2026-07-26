package br.com.brew.brassia.purchasing.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Fornecedor de insumos (cadastro mínimo de STK-001). Identificado por um código
 * único na cervejaria; base para o recebimento de lotes e a consolidação de
 * compras por fornecedor (PUR-002).
 */
public final class Supplier {

    private final SupplierId id;
    private final UUID breweryId;
    private final String name;
    private final String code;
    private final long version;

    private Supplier(SupplierId id, UUID breweryId, String name, String code, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.name = requireText(name, "nome", 160);
        this.code = requireText(code, "código", 40);
        this.version = version;
    }

    public static Supplier register(UUID breweryId, String name, String code) {
        return new Supplier(SupplierId.newId(), breweryId, name, code, 1);
    }

    public static Supplier reconstitute(SupplierId id, UUID breweryId, String name, String code, long version) {
        return new Supplier(id, breweryId, name, code, version);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return value.trim();
    }

    public SupplierId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String name() {
        return name;
    }

    public String code() {
        return code;
    }

    public long version() {
        return version;
    }
}
