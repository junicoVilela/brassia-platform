package br.com.brew.brassia.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O documento indexado (RAG-001).
 *
 * <p>Dois invariantes concentram o valor da história: a permissão é atributo do documento — não uma
 * verificação que a borda faz e a busca confia — e um documento indexado não muda de texto.
 */
class KnowledgeDocumentTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final LocalDate ABRIL = LocalDate.of(2026, 4, 1);
    private static final String PERMISSION = "sanitation.procedure.read";

    @Test
    @DisplayName("indexar corta o texto em trechos e guarda o checksum do conteúdo")
    void indexaCortandoEmTrechos() {
        var document = document("O peracético é usado a 0,15%.\n\nO tempo de contato é de 20 minutos.");

        assertThat(document.chunks()).isNotEmpty();
        assertThat(document.checksum()).hasSize(64);
        assertThat(document.version()).isEqualTo(1);
        assertThat(document.effectivity().open()).isTrue();
    }

    @Test
    @DisplayName("os trechos são numerados na ordem do documento: é o que torna a citação localizável")
    void trechosSaoNumeradosEmOrdem() {
        var text = "A".repeat(1400) + "\n\n" + "B".repeat(1400) + "\n\n" + "C".repeat(1400);
        var document = document(text);

        assertThat(document.chunks()).hasSizeGreaterThan(1);
        for (var i = 0; i < document.chunks().size(); i++) {
            assertThat(document.chunks().get(i).ordinal()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("documento sem texto indexável é recusado: título que nunca poderá ser citado não é fonte")
    void semTextoNaoEhIndexavel() {
        assertThatThrownBy(() -> document("   \n\n  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não é indexável");
    }

    @Test
    @DisplayName("o mesmo texto produz o mesmo checksum; texto diferente, checksum diferente")
    void checksumIdentificaOConteudo() {
        var a = document("Texto igual.");
        var b = document("Texto igual.");
        var c = document("Texto diferente.");

        assertThat(a.checksum()).isEqualTo(b.checksum());
        assertThat(a.checksum()).isNotEqualTo(c.checksum());
    }

    @Test
    @DisplayName("sem a permissão exigida o documento não é visível — nem o título")
    void semPermissaoNaoEhVisivel() {
        var document = document("Laudo de análise.");

        assertThat(document.visibleTo(Set.of(PERMISSION))).isTrue();
        assertThat(document.visibleTo(Set.of("outra.permissao"))).isFalse();
        assertThat(document.visibleTo(Set.of())).isFalse();
        assertThat(document.visibleTo(null)).isFalse();
    }

    @Test
    @DisplayName("substituir muda a vigência e nada mais: o texto de um documento indexado não se corrige")
    void substituirNaoMudaOTexto() {
        // Corrigir o texto apagaria a base de uma resposta já dada — alguém leu "0,15%" citando este
        // documento. Por isso a única coisa que muda depois da indexação é até quando ele valeu.
        var document = document("O peracético é usado a 0,15%.");

        var superseded = document.supersededFrom(LocalDate.of(2026, 6, 1));

        assertThat(superseded.chunks()).isEqualTo(document.chunks());
        assertThat(superseded.checksum()).isEqualTo(document.checksum());
        assertThat(superseded.id()).isEqualTo(document.id());
        assertThat(superseded.version()).isEqualTo(document.version());
        assertThat(superseded.effectivity().to()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("substituído continua vigente no passado, e não no presente")
    void substituidoRespondeSobreOPassado() {
        var superseded = document("Versão antiga.").supersededFrom(LocalDate.of(2026, 6, 1));

        assertThat(superseded.effectiveOn(ABRIL)).isTrue();
        assertThat(superseded.effectiveOn(LocalDate.of(2026, 6, 1))).isFalse();
    }

    @Test
    @DisplayName("permissão exigida em branco não passa: documento sem dono de acesso não entra")
    void permissaoEhObrigatoria() {
        assertThatThrownBy(() -> KnowledgeDocument.index(BREWERY, DocumentType.LAB_REPORT, "L-1",
                "Laudo", 1, Effectivity.from(ABRIL), "  ", null, null, "texto",
                Chunker.standard(), ACTOR, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("indexação sem autor não existe: documento entra na base por decisão de alguém")
    void autorEhObrigatorio() {
        assertThatThrownBy(() -> KnowledgeDocument.index(BREWERY, DocumentType.LAB_REPORT, "L-1",
                "Laudo", 1, Effectivity.from(ABRIL), PERMISSION, null, null, "texto",
                Chunker.standard(), null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("versão zero ou negativa não existe")
    void versaoDeveSerPositiva() {
        assertThatThrownBy(() -> KnowledgeDocument.index(BREWERY, DocumentType.LAB_REPORT, "L-1",
                "Laudo", 0, Effectivity.from(ABRIL), PERMISSION, null, null, "texto",
                Chunker.standard(), ACTOR, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static KnowledgeDocument document(String text) {
        return KnowledgeDocument.index(BREWERY, DocumentType.SAFETY_DATA_SHEET, "FISPQ-PERAC",
                "FISPQ — Ácido peracético", 1, Effectivity.from(ABRIL), PERMISSION, null,
                "s3://docs/fispq-perac.pdf", text, Chunker.standard(), ACTOR, Instant.now());
    }
}
