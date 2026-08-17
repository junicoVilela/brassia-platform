package br.com.brew.brassia.sales.domain;

import br.com.brew.brassia.shared.money.CurrencyMismatchException;
import br.com.brew.brassia.shared.money.Money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalesOrderTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID CANAL = UUID.randomUUID();
    private static final UUID PRODUTO = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.parse("2026-08-15");
    private static final Instant AGORA = Instant.parse("2026-08-15T10:00:00Z");

    private static LotReservation reserva(String codigo, int unidades, String validade) {
        return new LotReservation(UUID.randomUUID(), codigo, unidades, LocalDate.parse(validade));
    }

    private static OrderLine linha(int quantidade, String preco, LotReservation... reservas) {
        return new OrderLine(PRODUTO, "IPA-473", quantidade, Money.of(preco, "BRL"), false,
                List.of(reservas));
    }

    private static SalesOrder pedido(LocalDate promessa, OrderLine... linhas) {
        return SalesOrder.place(UUID.randomUUID(), CERVEJARIA, CLIENTE, CANAL, "PED-1",
                List.of(linhas), HOJE, promessa, AGORA);
    }

    @Test
    void oPedidoNasceConfirmadoENaoRascunho() {
        // Um pedido que não reservou nada não segura lote, e contá-lo como venda faria a cervejaria
        // prometer o que outro cliente ainda pode levar.
        var p = pedido(null, linha(10, "12.00", reserva("L-1", 10, "2027-01-01")));

        assertThat(p.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(p.promisedFor()).isEmpty();
    }

    @Test
    void oPedidoPrecisaDePeloMenosUmItem() {
        assertThatThrownBy(() -> SalesOrder.place(UUID.randomUUID(), CERVEJARIA, CLIENTE, CANAL, "PED-1",
                List.of(), HOJE, null, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos um item");
    }

    @Test
    void oItemNaoPrometeMaisDoQueReservou() {
        // O pior momento para descobrir isso é na expedição, com o caminhão parado.
        assertThatThrownBy(() -> linha(100, "12.00", reserva("L-1", 80, "2027-01-01")))
                .isInstanceOf(UnreservedQuantityException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("80");
    }

    @Test
    void aReservaPodeVirDeMaisDeUmLote() {
        // Cem unidades de dois lotes diferentes é o caso comum: raramente uma brassa cobre o pedido.
        var p = pedido(null, linha(100, "12.00",
                reserva("L-1", 60, "2027-01-01"), reserva("L-2", 40, "2027-03-01")));

        assertThat(p.reservations()).hasSize(2);
    }

    @Test
    void naoSePrometeEntregaDepoisDaValidade() {
        // A regra que dá nome à história. Sem ela o pedido é aceito, o cliente organiza a operação em
        // cima da data, e o problema aparece no dia da carga.
        assertThatThrownBy(() -> pedido(LocalDate.parse("2026-12-01"),
                linha(10, "12.00", reserva("L-1", 10, "2026-10-01"))))
                .isInstanceOf(PromiseAfterShelfLifeException.class)
                .hasMessageContaining("L-1")
                .hasMessageContaining("2026-10-01");
    }

    @Test
    void mandaOLoteQueVencePrimeiro() {
        // Quem entrega tudo junto entrega o mais velho junto: a data que limita é a menor, e não a
        // média nem a maior.
        assertThatThrownBy(() -> pedido(LocalDate.parse("2026-11-15"), linha(100, "12.00",
                reserva("VELHO", 40, "2026-11-01"), reserva("NOVO", 60, "2027-06-01"))))
                .isInstanceOf(PromiseAfterShelfLifeException.class)
                .hasMessageContaining("VELHO");
    }

    @Test
    void prometerNoDiaExatoDaValidadeVale() {
        // A validade é o último dia bom, e não o primeiro dia ruim.
        var p = pedido(LocalDate.parse("2026-10-01"), linha(10, "12.00",
                reserva("L-1", 10, "2026-10-01")));

        assertThat(p.promisedFor()).contains(LocalDate.parse("2026-10-01"));
    }

    @Test
    void semDataDeEntregaEEstadoLegitimo() {
        // "A combinar" acontece. Inventar uma data para o campo não ficar vazio seria prometer no lugar
        // de quem vende.
        var p = pedido(null, linha(10, "12.00", reserva("L-1", 10, "2027-01-01")));

        assertThat(p.promisedFor()).isEmpty();
    }

    @Test
    void naoSePrometeParaAntesDoPedido() {
        assertThatThrownBy(() -> pedido(LocalDate.parse("2026-08-01"),
                linha(10, "12.00", reserva("L-1", 10, "2027-01-01"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("antes do pedido");
    }

    @Test
    void oArredondamentoEDoTotalENaoDaLinha() {
        // O caso em que os dois caminhos DIVERGEM, que é o único que prova a regra.
        //
        // Duas linhas de 1 unidade a R$ 0,0050. Arredondando linha a linha: 0,01 + 0,01 = R$ 0,02.
        // Somando antes: 0,0050 + 0,0050 = 0,0100, que arredonda para R$ 0,01. Um centavo de
        // diferença — e é o centavo que o cliente encontra ao conferir a nota.
        //
        // Money guarda quatro casas justamente para o arredondamento acontecer uma vez só, no total.
        var p = pedido(null,
                linha(1, "0.0050", reserva("L-1", 1, "2027-01-01")),
                linha(1, "0.0050", reserva("L-2", 1, "2027-01-01")));

        assertThat(p.total().amount()).isEqualByComparingTo("0.01");
        assertThat(p.total().currency()).isEqualTo("BRL");
    }

    @Test
    void oTotalSomaOsItens() {
        var p = pedido(null,
                linha(10, "12.50", reserva("L-1", 10, "2027-01-01")),
                linha(4, "30.00", reserva("L-2", 4, "2027-01-01")));

        assertThat(p.total().amount()).isEqualByComparingTo("245.00");
    }

    @Test
    void oPedidoNaoMisturaMoedas() {
        // Um pedido com duas moedas não tem total, e sem total não há como faturar.
        var emReal = linha(1, "10.00", reserva("L-1", 1, "2027-01-01"));
        var emDolar = new OrderLine(PRODUTO, "IPA-473", 1, Money.of("3.00", "USD"), false,
                List.of(reserva("L-2", 1, "2027-01-01")));

        assertThatThrownBy(() -> pedido(null, emReal, emDolar))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void cancelarUmaVezValeECancelarDeNovoNao() {
        // Cancelar o já cancelado é operação sem efeito que, em silêncio, faria quem chamou acreditar
        // que fez algo.
        var p = pedido(null, linha(10, "12.00", reserva("L-1", 10, "2027-01-01")));

        p.cancel();
        assertThat(p.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(p::cancel).isInstanceOf(OrderNotChangeableException.class);
    }

    @Test
    void pedidoAtendidoNaoSeCancelaNemMudaDePromessa() {
        // Devolveria ao estoque unidades que já saíram pela porta, e o estoque passaria a contar
        // cerveja que não existe.
        var p = pedido(null, linha(10, "12.00", reserva("L-1", 10, "2027-01-01")));
        p.fulfill();

        assertThatThrownBy(p::cancel).isInstanceOf(OrderNotChangeableException.class);
        assertThatThrownBy(() -> p.promiseFor(LocalDate.parse("2026-09-01")))
                .isInstanceOf(OrderNotChangeableException.class);
    }

    @Test
    void aQuantidadeEOPrecoSaoPositivos() {
        assertThatThrownBy(() -> linha(0, "12.00", reserva("L-1", 1, "2027-01-01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LotReservation(UUID.randomUUID(), "L-1", 0,
                LocalDate.parse("2027-01-01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unidades");
    }
}
