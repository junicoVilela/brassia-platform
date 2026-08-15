package br.com.brew.brassia.sales.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Por onde a cervejaria vende (SAL-001).
 *
 * <p><strong>Entidade, e não enum — a mesma decisão da atividade de mão de obra na CST-001-A.</strong>
 * A segmentação comercial de uma cervejaria que vende no próprio taproom e para dois distribuidores não
 * é a de uma que exporta e atende rede de supermercado. Um enum imporia a divisão de quem escreveu o
 * código, e a primeira cervejaria com um canal a mais precisaria de uma migration para poder vender.
 *
 * <p>O canal é o que faz a mesma cerveja ter preços diferentes sem que isso seja inconsistência: o
 * mesmo produto custa uma coisa no balcão e outra para quem leva mil caixas.
 */
public final class SalesChannel {

    private static final int MAX_CODE = 30;
    private static final int MAX_NAME = 120;

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private String name;
    private boolean active;

    private SalesChannel(UUID id, UUID breweryId, String code, String name, boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria");
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public static SalesChannel create(UUID id, UUID breweryId, String code, String name) {
        return new SalesChannel(id, breweryId, required(code, "código", MAX_CODE).toUpperCase(),
                required(name, "nome", MAX_NAME), true);
    }

    public static SalesChannel reconstitute(UUID id, UUID breweryId, String code, String name,
            boolean active) {
        return new SalesChannel(id, breweryId, code, name, active);
    }

    public void rename(String name) {
        this.name = required(name, "nome", MAX_NAME);
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("o " + field + " do canal é obrigatório");
        }
        var clean = value.strip();
        if (clean.length() > max) {
            throw new IllegalArgumentException("o " + field + " do canal passa de " + max + " caracteres");
        }
        return clean;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
