package br.com.brew.brassia.sales.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * O que a cervejaria vende (SAL-001).
 *
 * <p><strong>Produto não é lote, e a distinção é o desenho.</strong> "IPA lata 473 ml" é a identidade
 * comercial — ela existe antes da primeira brassa, sobrevive a todos os lotes e é o que aparece numa
 * lista de preço. O {@code FinishedLot} é a coisa física, com data de envase e validade própria. Um
 * pedido se faz do produto; a entrega se cumpre com lotes, e é aí que a rastreabilidade entra.
 *
 * <p>Se os dois fossem a mesma coisa, cada nova brassa criaria um item de catálogo novo, e a lista de
 * preço precisaria ser refeita a cada envase.
 *
 * <p><strong>Aponta para receita e embalagem, e não os copia.</strong> O que define o produto é o par
 * (o que é, em que vasilhame) — copiar nome ou volume criaria uma segunda verdade que diverge no dia em
 * que a receita for renomeada.
 */
public final class Product {

    private static final int MAX_SKU = 40;
    private static final int MAX_NAME = 160;

    private final UUID id;
    private final UUID breweryId;
    private final String sku;
    private String name;
    private final UUID recipeId;
    private final UUID containerId;
    private boolean active;

    private Product(UUID id, UUID breweryId, String sku, String name, UUID recipeId, UUID containerId,
            boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria");
        this.sku = sku;
        this.name = name;
        this.recipeId = Objects.requireNonNull(recipeId, "receita");
        this.containerId = Objects.requireNonNull(containerId, "embalagem");
        this.active = active;
    }

    public static Product create(UUID id, UUID breweryId, String sku, String name, UUID recipeId,
            UUID containerId) {
        return new Product(id, breweryId, required(sku, "SKU", MAX_SKU).toUpperCase(),
                required(name, "nome", MAX_NAME), recipeId, containerId, true);
    }

    public static Product reconstitute(UUID id, UUID breweryId, String sku, String name, UUID recipeId,
            UUID containerId, boolean active) {
        return new Product(id, breweryId, sku, name, recipeId, containerId, active);
    }

    public void rename(String name) {
        this.name = required(name, "nome", MAX_NAME);
    }

    /**
     * Descontinuar tira das listas de venda e mantém o passado legível.
     *
     * <p>Não se apaga produto pelo mesmo motivo que não se apaga cliente: pedido antigo aponta para ele,
     * e um pedido cujo item não existe mais é um histórico que ninguém consegue explicar.
     */
    public void discontinue() {
        this.active = false;
    }

    public void restore() {
        this.active = true;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("o " + field + " do produto é obrigatório");
        }
        var clean = value.strip();
        if (clean.length() > max) {
            throw new IllegalArgumentException("o " + field + " do produto passa de " + max + " caracteres");
        }
        return clean;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    /** Sempre em maiúsculas: {@code ipa-473} e {@code IPA-473} são o mesmo código no mundo real. */
    public String sku() {
        return sku;
    }

    public String name() {
        return name;
    }

    public UUID recipeId() {
        return recipeId;
    }

    public UUID containerId() {
        return containerId;
    }

    public boolean isActive() {
        return active;
    }
}
