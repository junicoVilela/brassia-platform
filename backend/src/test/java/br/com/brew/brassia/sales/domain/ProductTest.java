package br.com.brew.brassia.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID RECEITA = UUID.randomUUID();
    private static final UUID EMBALAGEM = UUID.randomUUID();

    private static Product produto(String sku) {
        return Product.create(UUID.randomUUID(), CERVEJARIA, sku, "IPA lata 473 ml", RECEITA, EMBALAGEM);
    }

    @Test
    void oSkuFicaEmMaiusculas() {
        // "ipa-473" e "IPA-473" são o mesmo código no mundo real, e tratá-los como dois deixaria a
        // cervejaria com dois produtos que são um.
        assertThat(produto("ipa-473").sku()).isEqualTo("IPA-473");
    }

    @Test
    void oSkuEONomeSaoObrigatorios() {
        assertThatThrownBy(() -> produto(" ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU");
        assertThatThrownBy(() -> Product.create(UUID.randomUUID(), CERVEJARIA, "IPA-473", "  ", RECEITA,
                EMBALAGEM)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nome");
    }

    @Test
    void oProdutoApontaParaReceitaEEmbalagemENaoOsCopia() {
        // Copiar nome ou volume criaria uma segunda verdade, que diverge no dia em que a receita for
        // renomeada.
        var p = produto("IPA-473");

        assertThat(p.recipeId()).isEqualTo(RECEITA);
        assertThat(p.containerId()).isEqualTo(EMBALAGEM);
    }

    @Test
    void naoSeApagaProdutoDescontinuaSe() {
        // Pedido antigo aponta para ele, e um pedido cujo item não existe mais é histórico inexplicável.
        var p = produto("IPA-473");
        assertThat(p.isActive()).isTrue();

        p.discontinue();
        assertThat(p.isActive()).isFalse();
        assertThat(p.sku()).isEqualTo("IPA-473");

        p.restore();
        assertThat(p.isActive()).isTrue();
    }

    @Test
    void oNomeMudaEOSkuNao() {
        // O SKU é a identidade que o pedido antigo carrega; renomear a apresentação é rotina comercial.
        var p = produto("IPA-473");
        p.rename("IPA da Casa lata 473 ml");

        assertThat(p.name()).isEqualTo("IPA da Casa lata 473 ml");
        assertThat(p.sku()).isEqualTo("IPA-473");
    }
}
