package br.com.brew.brassia.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LastRelationshipTest {

    private static final LocalDate PEDIDO = LocalDate.parse("2025-03-10");
    private static final LocalDate ENTREGA = LocalDate.parse("2025-06-20");
    private static final LocalDate CONSENTIMENTO = LocalDate.parse("2024-11-05");

    @Test
    void oRelogioUsaOMaisRecenteDosTres() {
        // Cada um sozinho erra: só pedido ignora o bar que recebe entrega de contrato antigo; só entrega
        // ignora quem comprou e ainda não recebeu.
        var r = LastRelationship.of(PEDIDO, ENTREGA, CONSENTIMENTO);

        assertThat(r.latest()).contains(ENTREGA);
        assertThat(r.source()).contains("última entrega");
    }

    @Test
    void semRelacionamentoNenhumNaoHaData() {
        // Um cadastro sem pedido, entrega ou consentimento não é um cliente vencido — é um cadastro que
        // nunca foi usado. Tratar a ausência de evidência como evidência de ausência anonimizaria quem
        // acabou de ser cadastrado.
        var r = LastRelationship.of(null, null, null);

        assertThat(r.latest()).isEmpty();
        assertThat(r.source()).isEmpty();
    }

    @Test
    void umSinalBastaParaOReligioAndar() {
        // Quem só tem consentimento registrado tem relacionamento: ele falou com a casa.
        assertThat(LastRelationship.of(null, null, CONSENTIMENTO).latest()).contains(CONSENTIMENTO);
        assertThat(LastRelationship.of(PEDIDO, null, null).source()).contains("último pedido");
    }

    @Test
    void aOrigemDaDataViajaJunto() {
        // "Vence em março" sem dizer que a conta partiu de uma entrega de 2024 é um número que ninguém
        // consegue conferir — e conferir é o ponto, porque a anonimização é irreversível.
        var r = LastRelationship.of(PEDIDO, null, CONSENTIMENTO);

        assertThat(r.latest()).contains(PEDIDO);
        assertThat(r.source()).contains("último pedido");
    }

    @Test
    void oEmpateFicaComOPedido() {
        // Empate é raro e precisa de resposta estável: sem ordem definida, a mesma consulta responderia
        // coisas diferentes entre execuções, e a fila de retenção mudaria sozinha.
        var r = LastRelationship.of(PEDIDO, PEDIDO, PEDIDO);

        assertThat(r.source()).contains("último pedido");
    }
}
