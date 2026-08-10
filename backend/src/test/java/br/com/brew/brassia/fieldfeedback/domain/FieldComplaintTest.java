package br.com.brew.brassia.fieldfeedback.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reclamação de campo (FLD-001).
 *
 * <p>O que estes testes fixam: a severidade <em>determina</em> as ações exigidas, e uma reclamação com
 * ação pendente não encerra. A dispensa existe, mas custa uma justificativa assinada.
 */
class FieldComplaintTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID LOTE = UUID.randomUUID();
    private static final UUID ATOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T12:00:00Z");

    private static FieldComplaint reclamacao(Severity severidade, ComplaintCategory categoria) {
        return FieldComplaint.register(UUID.randomUUID(), CERVEJARIA, LOTE, "OS-123", categoria,
                severidade, "Gosto de papelão na terceira lata", StorageReport.unknown(),
                SampleRetention.unknown(), ATOR, AGORA);
    }

    @Nested
    @DisplayName("a severidade determina o que é exigido")
    class Exigencias {

        @Test
        @DisplayName("preferência não exige nada")
        void preferenciaNaoExige() {
            var c = reclamacao(Severity.PREFERENCE, ComplaintCategory.OFF_FLAVOR);

            assertThat(c.requiredActions()).isEmpty();
        }

        @Test
        @DisplayName("desvio sistêmico exige investigação de causa")
        void sistemicoExigeCapa() {
            // Problema sistêmico tratado exemplar a exemplar reaparece no lote seguinte.
            var c = reclamacao(Severity.SYSTEMIC, ComplaintCategory.OFF_FLAVOR);

            assertThat(c.requiredActions()).containsExactly(RequiredAction.ROOT_CAUSE_ANALYSIS);
        }

        @Test
        @DisplayName("risco à saúde exige quarentena e investigação")
        void seguracaExigeAmbas() {
            var c = reclamacao(Severity.SAFETY, ComplaintCategory.OFF_FLAVOR);

            assertThat(c.requiredActions()).containsExactlyInAnyOrder(
                    RequiredAction.QUARANTINE, RequiredAction.ROOT_CAUSE_ANALYSIS);
        }

        @Test
        @DisplayName("A CATEGORIA SOZINHA JÁ EXIGE, mesmo com severidade baixa")
        void categoriaDeRiscoPrevalece() {
            // Quem registra pode classificar um corpo estranho como QUALITY por não querer alarmar. Uma
            // exigência que dependesse só da severidade cairia junto com essa classificação.
            var c = reclamacao(Severity.QUALITY, ComplaintCategory.FOREIGN_BODY);

            assertThat(c.requiredActions()).contains(RequiredAction.QUARANTINE);
        }

        @Test
        @DisplayName("alegação de doença também")
        void doencaPrevalece() {
            var c = reclamacao(Severity.PREFERENCE, ComplaintCategory.ILLNESS);

            assertThat(c.requiredActions()).contains(RequiredAction.QUARANTINE);
        }

        @Test
        @DisplayName("toda ação exigida diz o que fazer")
        void acoesDescritas() {
            for (var action : RequiredAction.values()) {
                assertThat(action.description()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("encerramento")
    class Encerramento {

        @Test
        @DisplayName("NÃO SE ENCERRA COM AÇÃO PENDENTE — e o erro diz quais faltam")
        void naoEncerraComPendencia() {
            // É o que impede uma reclamação de corpo estranho de virar "cliente contatado, caso resolvido".
            var c = reclamacao(Severity.QUALITY, ComplaintCategory.FOREIGN_BODY);

            assertThatExceptionOfType(PendingActionsException.class)
                    .isThrownBy(() -> c.close("resolvido", ATOR, AGORA))
                    .satisfies(e -> assertThat(e.pending())
                            .contains(RequiredAction.QUARANTINE, RequiredAction.ROOT_CAUSE_ANALYSIS));
        }

        @Test
        @DisplayName("sem exigências, encerra direto")
        void encerraSemExigencias() {
            var c = reclamacao(Severity.PREFERENCE, ComplaintCategory.OFF_FLAVOR);

            c.close("Cliente orientado sobre o estilo", ATOR, AGORA);

            assertThat(c.status()).isEqualTo(ComplaintStatus.CLOSED);
        }

        @Test
        @DisplayName("ação atendida aponta para o registro criado")
        void atendidaApontaReferencia() {
            var c = reclamacao(Severity.SYSTEMIC, ComplaintCategory.OFF_FLAVOR);
            var capa = UUID.randomUUID();

            c.fulfill(RequiredAction.ROOT_CAUSE_ANALYSIS, capa, ATOR, AGORA);
            c.close("Causa encontrada", ATOR, AGORA);

            assertThat(c.outcomes()).singleElement()
                    .satisfies(o -> assertThat(o.reference()).contains(capa));
            assertThat(c.status()).isEqualTo(ComplaintStatus.CLOSED);
        }

        @Test
        @DisplayName("atendida sem referência é recusada")
        void atendidaSemReferencia() {
            // Seria uma afirmação sem contra o que conferir: qual quarentena?
            var c = reclamacao(Severity.SYSTEMIC, ComplaintCategory.OFF_FLAVOR);

            org.assertj.core.api.Assertions.assertThatNullPointerException().isThrownBy(
                    () -> c.fulfill(RequiredAction.ROOT_CAUSE_ANALYSIS, null, ATOR, AGORA));
        }

        @Test
        @DisplayName("DISPENSA EXIGE JUSTIFICATIVA DE VERDADE, não 'n/a'")
        void dispensaExigeJustificativa() {
            // "n/a" e "ok" são o que se escreve quando não se decidiu nada.
            var c = reclamacao(Severity.SYSTEMIC, ComplaintCategory.OFF_FLAVOR);

            assertThatIllegalArgumentException().isThrownBy(
                    () -> c.waive(RequiredAction.ROOT_CAUSE_ANALYSIS, "n/a", ATOR, AGORA));
        }

        @Test
        @DisplayName("dispensa justificada libera o encerramento e fica assinada")
        void dispensaJustificadaLibera() {
            var c = reclamacao(Severity.QUALITY, ComplaintCategory.FOREIGN_BODY);
            var revisor = UUID.randomUUID();

            c.waive(RequiredAction.QUARANTINE,
                    "Corpo estranho identificado como fragmento do copo do consumidor, em foto",
                    revisor, AGORA);
            c.waive(RequiredAction.ROOT_CAUSE_ANALYSIS,
                    "Sem indício de falha de processo; exemplar único e origem externa confirmada",
                    revisor, AGORA);
            c.close("Encerrada após análise da foto", ATOR, AGORA);

            assertThat(c.status()).isEqualTo(ComplaintStatus.CLOSED);
            assertThat(c.outcomes()).allSatisfy(o -> {
                assertThat(o.fulfilled()).isFalse();
                assertThat(o.decidedBy()).isEqualTo(revisor);
                assertThat(o.justification()).isNotBlank();
            });
        }

        @Test
        @DisplayName("não se registra destino para ação que o caso não exige")
        void acaoNaoExigida() {
            // Inventaria histórico: pareceria que a quarentena foi cogitada e dispensada.
            var c = reclamacao(Severity.PREFERENCE, ComplaintCategory.OFF_FLAVOR);

            assertThatIllegalArgumentException().isThrownBy(
                    () -> c.waive(RequiredAction.QUARANTINE, "não precisa porque não precisa", ATOR, AGORA));
        }

        @Test
        @DisplayName("encerrada não encerra de novo")
        void encerraDuasVezes() {
            var c = reclamacao(Severity.PREFERENCE, ComplaintCategory.OFF_FLAVOR);
            c.close("ok", ATOR, AGORA);

            assertThatExceptionOfType(FieldComplaint.IllegalComplaintTransitionException.class)
                    .isThrownBy(() -> c.close("de novo", ATOR, AGORA));
        }
    }

    @Nested
    @DisplayName("armazenagem e amostra")
    class Contexto {

        @Test
        @DisplayName("desconhecido é distinto de 'estava tudo bem'")
        void desconhecidoNaoEhOk() {
            // Sem isso, a investigação procura na produção um problema que aconteceu no depósito.
            assertThat(StorageReport.unknown().knownConditions()).isFalse();
            assertThat(new StorageReport(new java.math.BigDecimal("35"), 14, true, null)
                    .knownConditions()).isTrue();
        }

        @Test
        @DisplayName("amostra retida exige local declarado")
        void amostraRetidaExigeLocal() {
            // Amostra sem lugar declarado é amostra que ninguém acha quando precisa.
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new SampleRetention(SampleRetention.Status.RETAINED, "  "));
        }

        @Test
        @DisplayName("só a amostra retida é analisável")
        void apenasRetidaAnalisavel() {
            assertThat(new SampleRetention(SampleRetention.Status.RETAINED, "Geladeira 2").analyzable())
                    .isTrue();
            assertThat(SampleRetention.unknown().analyzable()).isFalse();
        }
    }

    @Nested
    @DisplayName("dado pessoal")
    class DadoPessoal {

        @Test
        @DisplayName("A RECLAMAÇÃO NÃO TEM CAMPO PARA DADO PESSOAL")
        void semCampoDePessoa() throws Exception {
            // Um DTO que não tem campo para nome não vaza nome por esquecimento: não há o que esquecer
            // de remover. O teste vigia a estrutura, que é o que sustenta a garantia.
            var proibidos = java.util.List.of("name", "email", "phone", "address", "contact",
                    "complainant");
            var campos = java.util.Arrays.stream(FieldComplaint.class.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName)
                    .map(String::toLowerCase)
                    .toList();

            assertThat(campos).noneSatisfy(campo ->
                    assertThat(proibidos).anySatisfy(p -> assertThat(campo).contains(p)));
        }

        @Test
        @DisplayName("contato vazio é recusado")
        void contatoVazio() {
            assertThatIllegalArgumentException().isThrownBy(() -> ComplainantContact.record(
                    UUID.randomUUID(), " ", null, null, "", ATOR, AGORA));
        }

        @Test
        @DisplayName("APAGAR ESVAZIA O CONTEÚDO E PRESERVA O FATO do apagamento")
        void apagarPreservaOFato() {
            // Apagar a linha inteira tornaria indistinguível "anônima desde o início" de "apagada a
            // pedido" — e a segunda precisa ser demonstrável, inclusive para quem pediu.
            var contato = ComplainantContact.record(UUID.randomUUID(), "Fulana", "f@example.com",
                    "11999999999", "Rua X", ATOR, AGORA);

            contato.erase(AGORA.plusSeconds(60));

            assertThat(contato.erased()).isTrue();
            assertThat(contato.name()).isEmpty();
            assertThat(contato.email()).isEmpty();
            assertThat(contato.phone()).isEmpty();
            assertThat(contato.address()).isEmpty();
            assertThat(contato.erasedAt()).contains(AGORA.plusSeconds(60));
            // O registro de que houve contato permanece.
            assertThat(contato.recordedBy()).isEqualTo(ATOR);
        }
    }
}
