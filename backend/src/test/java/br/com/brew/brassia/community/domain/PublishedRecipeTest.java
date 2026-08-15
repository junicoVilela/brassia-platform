package br.com.brew.brassia.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishedRecipeTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID RECEITA = UUID.randomUUID();
    private static final UUID AUTOR = UUID.randomUUID();
    private static final Instant ONTEM = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant HOJE = Instant.parse("2026-08-15T10:00:00Z");

    private static PublicRecipeSnapshot retrato() {
        return new PublicRecipeSnapshot("IPA da Casa", "American IPA", new BigDecimal("400"), 60, null,
                List.of(new PublicRecipeSnapshot.Item("Malte Pilsen", "MASH", new BigDecimal("20"), "KG",
                        null, null)));
    }

    private static PublishedRecipe publicada(Visibility v, RecipeLicense l) {
        return PublishedRecipe.publish(UUID.randomUUID(), CERVEJARIA, RECEITA, 3, AUTOR, "Ana",
                "IPA da Casa", "Uma IPA de sessão", l, v, retrato(), ONTEM);
    }

    @Test
    void publicaSeUmaVersaoENaoUmaReceita() {
        // A receita continua evoluindo em casa; o que está lá fora é o retrato de uma versão, com o
        // número à vista. Sem isso, cada ajuste interno mudaria em silêncio o que o público lê.
        var p = publicada(Visibility.PUBLIC, RecipeLicense.CC_BY);

        assertThat(p.recipeVersion()).isEqualTo(3);
        assertThat(p.recipeId()).isEqualTo(RECEITA);
    }

    @Test
    void aVersaoEObrigatoria() {
        // Sem ela a fonte não é reproduzível: "a IPA da Ana" não diz qual.
        assertThatThrownBy(() -> PublishedRecipe.publish(UUID.randomUUID(), CERVEJARIA, RECEITA, 0, AUTOR,
                "Ana", "IPA", null, RecipeLicense.CC0, Visibility.PUBLIC, retrato(), ONTEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versão");
    }

    @Test
    void autorLicencaTituloEVersaoSaoObrigatorios() {
        // As quatro coisas que o aceite pede. Nenhuma é campo opcional que a tela preenche quando
        // lembra: sem autor não há a quem atribuir, sem licença ninguém sabe o que pode fazer.
        assertThatThrownBy(() -> PublishedRecipe.publish(UUID.randomUUID(), CERVEJARIA, RECEITA, 1, AUTOR,
                "Ana", "  ", null, RecipeLicense.CC0, Visibility.PUBLIC, retrato(), ONTEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("título");
        assertThatThrownBy(() -> PublishedRecipe.publish(UUID.randomUUID(), CERVEJARIA, RECEITA, 1, AUTOR,
                "Ana", "IPA", null, null, Visibility.PUBLIC, retrato(), ONTEM))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aMatrizDeVisibilidade() {
        // O plano de testes pede a matriz inteira: privado, cervejaria, link, não listado e público.
        assertThat(publicada(Visibility.PRIVATE, RecipeLicense.CC0).listed()).isFalse();
        assertThat(publicada(Visibility.BREWERY, RecipeLicense.CC0).listed()).isFalse();
        assertThat(publicada(Visibility.LINK, RecipeLicense.CC0).listed()).isFalse();
        assertThat(publicada(Visibility.UNLISTED, RecipeLicense.CC0).listed()).isFalse();
        assertThat(publicada(Visibility.PUBLIC, RecipeLicense.CC0).listed()).isTrue();

        // Só de LINK para cima alguém de fora alcança.
        assertThat(publicada(Visibility.PRIVATE, RecipeLicense.CC0).readableByOutsider()).isFalse();
        assertThat(publicada(Visibility.BREWERY, RecipeLicense.CC0).readableByOutsider()).isFalse();
        assertThat(publicada(Visibility.LINK, RecipeLicense.CC0).readableByOutsider()).isTrue();
        assertThat(publicada(Visibility.UNLISTED, RecipeLicense.CC0).readableByOutsider()).isTrue();
        assertThat(publicada(Visibility.PUBLIC, RecipeLicense.CC0).readableByOutsider()).isTrue();
    }

    @Test
    void despublicadaNaoApareceEmBuscaMesmoTendoSidoPublica() {
        // A ordem das duas checagens importa: o contrário deixaria uma receita despublicada com PUBLIC
        // salvo continuar listada.
        var p = publicada(Visibility.PUBLIC, RecipeLicense.CC0);
        p.unpublish(HOJE);

        assertThat(p.listed()).isFalse();
        assertThat(p.readableByOutsider()).isFalse();
        assertThat(p.isPublished()).isFalse();
    }

    @Test
    void despublicarNaoApaga() {
        // O que já foi lido não se desfaz, e um fork feito enquanto estava pública continua legítimo.
        var p = publicada(Visibility.PUBLIC, RecipeLicense.CC_BY);
        p.unpublish(HOJE);

        assertThat(p.snapshot().name()).isEqualTo("IPA da Casa");
        assertThat(p.authorDisplayName()).isEqualTo("Ana");
        assertThat(p.unpublishedAt()).contains(HOJE);
    }

    @Test
    void despublicadaNaoAceitaMaisMudanca() {
        // Mudar licença ou visibilidade daria a impressão de efeito onde não há nada publicado para
        // alcançar. Republicar é ato novo, com data nova.
        var p = publicada(Visibility.PUBLIC, RecipeLicense.CC0);
        p.unpublish(HOJE);

        assertThatThrownBy(() -> p.changeVisibility(Visibility.LINK))
                .isInstanceOf(RecipeUnpublishedException.class);
        assertThatThrownBy(() -> p.relicense(RecipeLicense.CC_BY))
                .isInstanceOf(RecipeUnpublishedException.class);
        assertThatThrownBy(() -> p.edit("Outro título", null))
                .isInstanceOf(RecipeUnpublishedException.class);
    }

    @Test
    void todosOsDireitosReservadosNaoAutorizaFork() {
        // Publicar sem autorizar cópia é legítimo: a cervejaria mostra e não libera.
        assertThat(publicada(Visibility.PUBLIC, RecipeLicense.ALL_RIGHTS_RESERVED).forkableByOthers())
                .isFalse();
        assertThat(publicada(Visibility.PUBLIC, RecipeLicense.CC_BY).forkableByOthers()).isTrue();
    }

    @Test
    void oQueNaoAlcancaDeForaTambemNaoSeForka() {
        // Fork exige alcançar: uma receita da cervejaria não é forkável por terceiro só porque a
        // licença permitiria.
        assertThat(publicada(Visibility.BREWERY, RecipeLicense.CC0).forkableByOthers()).isFalse();
    }

    @Test
    void aLicencaMudaDaquiParaAFrenteENaoRetroage() {
        // Quem já copiou sob a licença antiga copiou sob a licença antiga. Fingir que desfaz seria a
        // plataforma prometendo um controle que ela não tem sobre o que já saiu.
        var p = publicada(Visibility.PUBLIC, RecipeLicense.CC0);
        p.relicense(RecipeLicense.ALL_RIGHTS_RESERVED);

        assertThat(p.license()).isEqualTo(RecipeLicense.ALL_RIGHTS_RESERVED);
        // A data de publicação não se mexe: a linha do tempo continua dizendo desde quando está no ar.
        assertThat(p.publishedAt()).isEqualTo(ONTEM);
    }
}
