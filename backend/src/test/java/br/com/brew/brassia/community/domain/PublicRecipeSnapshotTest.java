package br.com.brew.brassia.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicRecipeSnapshotTest {

    private static PublicRecipeSnapshot snapshot() {
        return new PublicRecipeSnapshot("IPA da Casa", "American IPA", new BigDecimal("400"), 60,
                new PublicRecipeSnapshot.Targets(new BigDecimal("1.052"), new BigDecimal("1.010"),
                        new BigDecimal("55"), new BigDecimal("12"), new BigDecimal("5.5")),
                List.of(new PublicRecipeSnapshot.Item("Malte Pilsen", "MASH", new BigDecimal("20"),
                        "KG", null, new BigDecimal("80"))));
    }

    @Test
    void oRetratoPublicoNaoCarregaIdentificadorInterno() {
        // A prova da allowlist, e ela é feita por reflexão de propósito: um teste que olhasse só os
        // valores passaria no dia em que alguém acrescentasse um campo novo com id dentro. Este falha.
        var proibidos = List.of("breweryid", "ingredientid", "equipmentid", "previousrecipeid",
                "recipeid", "supplierid", "customerid", "cost", "custo", "price", "preco",
                "stock", "estoque", "supplier", "fornecedor");

        assertThat(camposDe(PublicRecipeSnapshot.class)).noneSatisfy(campo ->
                assertThat(proibidos).contains(campo.toLowerCase()));
        assertThat(camposDe(PublicRecipeSnapshot.Item.class)).noneSatisfy(campo ->
                assertThat(proibidos).contains(campo.toLowerCase()));
        assertThat(camposDe(PublicRecipeSnapshot.Targets.class)).noneSatisfy(campo ->
                assertThat(proibidos).contains(campo.toLowerCase()));
    }

    @Test
    void oItemSaiPeloNomeENaoPeloIdentificador() {
        // O id é a chave do catálogo da cervejaria, onde moram preço de compra e fornecedor. O nome é
        // o que outro cervejeiro precisa, e não abre porta nenhuma.
        var item = snapshot().items().getFirst();

        assertThat(item.ingredientName()).isEqualTo("Malte Pilsen");
        assertThat(camposDe(PublicRecipeSnapshot.Item.class)).contains("ingredientName");
    }

    @Test
    void oNomeDaReceitaEObrigatorio() {
        assertThatThrownBy(() -> new PublicRecipeSnapshot("  ", null, BigDecimal.ONE, 60, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nome");
    }

    @Test
    void oItemPrecisaDoNomeDoIngrediente() {
        // Um item sem nome publicaria "20 kg de alguma coisa", que não é receita.
        assertThatThrownBy(() -> new PublicRecipeSnapshot.Item(" ", "MASH", BigDecimal.ONE, "KG", null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ingrediente");
    }

    @Test
    void aListaDeItensNaoSeAlteraPorFora() {
        assertThatThrownBy(() -> snapshot().items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static List<String> camposDe(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
