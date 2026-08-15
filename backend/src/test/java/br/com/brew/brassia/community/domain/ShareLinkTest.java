package br.com.brew.brassia.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShareLinkTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID AUTOR = UUID.randomUUID();
    private static final Instant CRIACAO = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant DEPOIS = CRIACAO.plus(Duration.ofDays(1));

    private static PublishedRecipe publicacao(UUID id, Visibility v) {
        return PublishedRecipe.publish(id, CERVEJARIA, UUID.randomUUID(), 1, AUTOR, "Ana", "IPA", null,
                RecipeLicense.CC0, v,
                new PublicRecipeSnapshot("IPA", null, new BigDecimal("400"), 60, null, List.of()),
                CRIACAO);
    }

    private static ShareLink link(UUID publicacaoId, Instant expira) {
        return ShareLink.create(UUID.randomUUID(), CERVEJARIA, publicacaoId, "hash-do-token",
                SharePermission.READ, "pro Bruno avaliar", CRIACAO, AUTOR, expira);
    }

    @Test
    void oLinkValeAteExpirar() {
        var l = link(UUID.randomUUID(), CRIACAO.plus(Duration.ofDays(7)));

        assertThat(l.usableAt(DEPOIS)).isTrue();
        assertThat(l.usableAt(CRIACAO.plus(Duration.ofDays(8)))).isFalse();
    }

    @Test
    void linkSemPrazoEEstadoLegitimo() {
        // "Manda para o pessoal ver" acontece. O que não é opcional é poder cortar.
        var l = link(UUID.randomUUID(), null);

        assertThat(l.expiresAt()).isEmpty();
        assertThat(l.usableAt(CRIACAO.plus(Duration.ofDays(3650)))).isTrue();
    }

    @Test
    void oLinkNaoNasceVencido() {
        // Um engano que o operador só descobriria quando o outro lado reclamasse.
        assertThatThrownBy(() -> link(UUID.randomUUID(), CRIACAO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void revogarCortaNaHora() {
        var l = link(UUID.randomUUID(), null);
        l.revoke(DEPOIS);

        assertThat(l.usableAt(DEPOIS)).isFalse();
        assertThat(l.revokedAt()).contains(DEPOIS);
    }

    @Test
    void revogarDuasVezesNaoEErroENaoMudaAData() {
        // Quem clica de novo quer o mesmo resultado; recusar mostraria falha para algo que deu certo.
        // E a data não se mexe: ela é o registro de quando o acesso foi cortado.
        var l = link(UUID.randomUUID(), null);
        l.revoke(DEPOIS);
        l.revoke(DEPOIS.plus(Duration.ofDays(1)));

        assertThat(l.revokedAt()).contains(DEPOIS);
    }

    @Test
    void oLinkNaoElevaVisibilidade() {
        // A regra que o critério da história exige: "acesso nunca ignora autorização ou visibilidade".
        // Um link válido para uma publicação privada não abre nada.
        var id = UUID.randomUUID();
        var l = link(id, null);

        assertThat(l.usableAt(DEPOIS)).isTrue();
        assertThat(l.grantsAccessTo(publicacao(id, Visibility.PRIVATE), DEPOIS)).isFalse();
        assertThat(l.grantsAccessTo(publicacao(id, Visibility.BREWERY), DEPOIS)).isFalse();
        assertThat(l.grantsAccessTo(publicacao(id, Visibility.LINK), DEPOIS)).isTrue();
    }

    @Test
    void fecharAPublicacaoDerrubaTodosOsLinksDeUmaVez() {
        // Sem precisar revogar um por um: a decisão de quem vê é da publicação, e o link só carrega a
        // chave. É o botão de pânico do autor.
        var id = UUID.randomUUID();
        var l = link(id, null);

        assertThat(l.grantsAccessTo(publicacao(id, Visibility.LINK), DEPOIS)).isTrue();
        assertThat(l.grantsAccessTo(publicacao(id, Visibility.PRIVATE), DEPOIS)).isFalse();
    }

    @Test
    void despublicarTambemDerruba() {
        var id = UUID.randomUUID();
        var l = link(id, null);
        var p = publicacao(id, Visibility.LINK);
        p.unpublish(DEPOIS);

        assertThat(l.grantsAccessTo(p, DEPOIS)).isFalse();
    }

    @Test
    void linkDeOutraPublicacaoNaoAbreEsta() {
        // Parece óbvio, e é o tipo de coisa que um handler distraído deixa passar ao carregar as duas
        // coisas separadamente.
        var l = link(UUID.randomUUID(), null);

        assertThat(l.grantsAccessTo(publicacao(UUID.randomUUID(), Visibility.PUBLIC), DEPOIS)).isFalse();
    }

    @Test
    void oLinkExpiradoNaoAbreNemPublicacaoPublica() {
        var id = UUID.randomUUID();
        var l = link(id, CRIACAO.plus(Duration.ofHours(1)));

        assertThat(l.grantsAccessTo(publicacao(id, Visibility.PUBLIC), DEPOIS)).isFalse();
    }

    @Test
    void aPermissaoDeComentarEExplicita() {
        // Nenhum nível permite editar a receita: um link é convite de leitura ou de conversa, e nunca
        // chave para o conteúdo interno.
        var leitura = link(UUID.randomUUID(), null);
        var conversa = ShareLink.create(UUID.randomUUID(), CERVEJARIA, UUID.randomUUID(), "hash",
                SharePermission.COMMENT, null, CRIACAO, AUTOR, null);

        assertThat(leitura.allowsComment()).isFalse();
        assertThat(conversa.allowsComment()).isTrue();
    }

    @Test
    void oValorLegivelDoTokenNaoViveNoAgregado() {
        // Só o hash. Um link vazado do banco seria acesso concedido sem ninguém ter compartilhado nada.
        var l = link(UUID.randomUUID(), null);

        assertThat(l.tokenHash()).isEqualTo("hash-do-token");
        assertThat(java.util.Arrays.stream(ShareLink.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("token", "rawToken", "secret");
    }
}
