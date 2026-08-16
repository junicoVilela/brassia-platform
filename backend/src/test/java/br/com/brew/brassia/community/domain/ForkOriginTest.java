package br.com.brew.brassia.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ForkOriginTest {

    private static final UUID PUBLICACAO = UUID.randomUUID();
    private static final Instant FORK = Instant.parse("2026-08-15T10:00:00Z");

    private static ForkOrigin origem(RecipeLicense licenca) {
        return new ForkOrigin(PUBLICACAO, "Ana", "IPA da Casa", licenca, 3, FORK);
    }

    @Test
    void aAtribuicaoECongeladaENaoUmPonteiro() {
        // O critério da história: "sem acesso futuro ao conteúdo privado do autor". Nome, título e
        // licença ficam como estavam — se o autor renomear ou fechar, a atribuição segue correta e o
        // forkador não ganha nada novo.
        var o = origem(RecipeLicense.CC_BY);

        assertThat(o.sourceAuthorName()).isEqualTo("Ana");
        assertThat(o.sourceTitle()).isEqualTo("IPA da Casa");
        assertThat(o.sourceRecipeVersion()).isEqualTo(3);
        assertThat(o.attribution()).isEqualTo("IPA da Casa, de Ana (CC BY 4.0)");
    }

    @Test
    void semAutorOuTituloNaoHaAtribuicao() {
        // Uma atribuição sem nome é uma cópia sem crédito, que é o que a licença existe para impedir.
        assertThatThrownBy(() -> new ForkOrigin(PUBLICACAO, "  ", "IPA", RecipeLicense.CC0, 1, FORK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autor");
        assertThatThrownBy(() -> new ForkOrigin(PUBLICACAO, "Ana", " ", RecipeLicense.CC0, 1, FORK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("título");
    }

    @Test
    void compartilharIgualSePropaga() {
        // CC BY-SA existe justamente para que derivados continuem abertos. Sem registrar a licença de
        // origem, ninguém saberia disso seis meses depois.
        assertThat(origem(RecipeLicense.CC_BY_SA).requiredLicenseForDerivative())
                .contains(RecipeLicense.CC_BY_SA);
    }

    @Test
    void asDemaisLicencasDeixamOForkadorEscolher() {
        // O que ele fez é dele, desde que a atribuição fique.
        assertThat(origem(RecipeLicense.CC0).requiredLicenseForDerivative()).isEmpty();
        assertThat(origem(RecipeLicense.CC_BY).requiredLicenseForDerivative()).isEmpty();
        assertThat(origem(RecipeLicense.CC_BY_NC).requiredLicenseForDerivative()).isEmpty();
    }

    @Test
    void aLicencaDeOrigemViajaJunto() {
        // Ela é a obrigação que sobrevive à cópia.
        assertThat(origem(RecipeLicense.CC_BY_NC).sourceLicense()).isEqualTo(RecipeLicense.CC_BY_NC);
    }

    @Test
    void oForkERecusadoPorLicencaOuPorAlcance() {
        // Duas causas diferentes, e a segunda não é sobre licença: não se forka o que não se pode ler.
        assertThat(ForkNotAllowedException.license(RecipeLicense.ALL_RIGHTS_RESERVED).getMessage())
                .contains("Todos os direitos reservados");
        assertThat(ForkNotAllowedException.unreachable().getMessage()).contains("acessível");
    }

    @Test
    void aRecusaPorIngredienteFaltanteDizQuaisSao() {
        // Recusar inteiro, e não montar meia receita: uma receita a que faltam três de oito
        // ingredientes não é incompleta, é errada — e alguém vai brassá-la achando que é a do outro.
        var e = new UnmappedIngredientsException(java.util.List.of("Lúpulo Citra", "Levedura US-05"));

        assertThat(e.missing()).containsExactly("Lúpulo Citra", "Levedura US-05");
        assertThat(e.getMessage()).contains("Lúpulo Citra").contains("Levedura US-05");
    }
}
