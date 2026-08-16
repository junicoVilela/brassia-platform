package br.com.brew.brassia.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContributionTest {

    private static final UUID PUBLICACAO = UUID.randomUUID();
    private static final UUID QUEM_ESCREVEU = UUID.randomUUID();
    private static final UUID AUTOR_DA_RECEITA = UUID.randomUUID();
    private static final Instant ONTEM = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant HOJE = Instant.parse("2026-08-15T10:00:00Z");

    private static Contribution comentario() {
        return Contribution.write(UUID.randomUUID(), PUBLICACAO, QUEM_ESCREVEU, "Bruno",
                ContributionKind.COMMENT, "Ficou ótima!", null, ONTEM);
    }

    private static Contribution sugestao() {
        return Contribution.write(UUID.randomUUID(), PUBLICACAO, QUEM_ESCREVEU, "Bruno",
                ContributionKind.SUGGESTION, "Eu subiria o Citra para 30 g", "Lúpulo Citra", ONTEM);
    }

    @Test
    void oComentarioNasceAbertoEENaoDecidivel() {
        // Ele não propôs nada: não há o que aceitar. Deixar passar faria a tela oferecer dois botões
        // sem sentido e a contagem de pendentes incluir elogios.
        var c = comentario();

        assertThat(c.status()).isEqualTo(ContributionStatus.OPEN);
        assertThat(c.isPending()).isFalse();
        assertThatThrownBy(() -> c.accept(AUTOR_DA_RECEITA, HOJE, null))
                .isInstanceOf(NotDecidableException.class);
        assertThatThrownBy(() -> c.decline(AUTOR_DA_RECEITA, HOJE, null))
                .isInstanceOf(NotDecidableException.class);
    }

    @Test
    void aSugestaoAbertaEPendente() {
        // É o que permite a tela dizer "3 sugestões pendentes" sem contar comentários.
        assertThat(sugestao().isPending()).isTrue();
    }

    @Test
    void aceitarRegistraConcordanciaENaoAlteraNada() {
        // A decisão central da história. Aplicar é ato do autor, na receita dele, e vira versão nova —
        // o retrato publicado é congelado, e a receita de verdade é privada.
        var s = sugestao();
        s.accept(AUTOR_DA_RECEITA, HOJE, "boa ideia, vou testar na próxima");

        assertThat(s.status()).isEqualTo(ContributionStatus.ACCEPTED);
        assertThat(s.decidedBy()).contains(AUTOR_DA_RECEITA);
        assertThat(s.decidedAt()).contains(HOJE);
        assertThat(s.decisionNote()).contains("boa ideia, vou testar na próxima");
        // O texto da sugestão continua o mesmo: aceitar não reescreve o que foi proposto.
        assertThat(s.body()).isEqualTo("Eu subiria o Citra para 30 g");
        assertThat(s.isPending()).isFalse();
    }

    @Test
    void recusarNaoApaga() {
        // Uma sugestão recusada continua visível, com a decisão ao lado: é o que evita ela voltar três
        // vezes, e o que torna a conversa um histórico em vez de uma caixa de entrada.
        var s = sugestao();
        s.decline(AUTOR_DA_RECEITA, HOJE, "prefiro manter o perfil mais leve");

        assertThat(s.status()).isEqualTo(ContributionStatus.DECLINED);
        assertThat(s.isVisible()).isTrue();
        assertThat(s.body()).isEqualTo("Eu subiria o Citra para 30 g");
    }

    @Test
    void naoSeDecideDuasVezes() {
        // Reescreveria quem decidiu e quando — e é esse registro que torna a conversa auditável.
        var s = sugestao();
        s.accept(AUTOR_DA_RECEITA, HOJE, null);

        assertThatThrownBy(() -> s.decline(AUTOR_DA_RECEITA, HOJE, "mudei de ideia"))
                .isInstanceOf(AlreadyDecidedException.class);
        assertThat(s.status()).isEqualTo(ContributionStatus.ACCEPTED);
    }

    @Test
    void oContextoEOQueTornaOComentarioContextual() {
        // Sem ele, a publicação vira um mural: "o lúpulo está alto" sem dizer qual lúpulo.
        assertThat(sugestao().context()).contains("Lúpulo Citra");
        assertThat(comentario().context()).isEmpty();
    }

    @Test
    void oTextoEObrigatorio() {
        assertThatThrownBy(() -> Contribution.write(UUID.randomUUID(), PUBLICACAO, QUEM_ESCREVEU,
                "Bruno", ContributionKind.COMMENT, "   ", null, ONTEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("texto");
    }

    @Test
    void esconderNaoApaga() {
        // A moderação precisa poder ser revista, e um texto apagado não se revisa (COM-005).
        var c = comentario();
        c.hide(HOJE);

        assertThat(c.isVisible()).isFalse();
        assertThat(c.hiddenAt()).contains(HOJE);
        assertThat(c.body()).isEqualTo("Ficou ótima!");
    }

    @Test
    void aAutoriaDoComentarioECongelada() {
        // Como a atribuição do fork: ela não muda quando a pessoa troca o nome de exibição.
        assertThat(comentario().authorDisplayName()).isEqualTo("Bruno");
    }

    @Test
    void aNotaDaDecisaoEOpcional() {
        // Aceitar sem explicar é legítimo; obrigar texto faria o autor escrever "ok" para poder seguir.
        var s = sugestao();
        s.accept(AUTOR_DA_RECEITA, HOJE, null);

        assertThat(s.decisionNote()).isEmpty();
        assertThat(s.status()).isEqualTo(ContributionStatus.ACCEPTED);
    }
}
