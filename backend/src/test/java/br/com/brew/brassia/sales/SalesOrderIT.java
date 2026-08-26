package br.com.brew.brassia.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.sales.application.port.inbound.OrderCommands;
import br.com.brew.brassia.sales.domain.InsufficientLotStockException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.support.BrewScenario;
import br.com.brew.brassia.support.CommercialTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pedido, reserva de lote e promessa de entrega (SAL-002).
 */
@SpringBootTest
@Testcontainers
class SalesOrderIT extends CommercialTestSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    /** Para o teste de concorrência: o que precisa correr em paralelo são duas transações do serviço. */
    @Autowired
    OrderCommands orders;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        scenario = new BrewScenario(mockMvc);
    }

    @Test
    void oPedidoReservaOLoteEOEstoqueNaoEVendidoDuasVezes() throws Exception {
        // O critério transversal da sprint é literal: "concorrência não vende estoque duas vezes".
        // Aqui a prova é sequencial — o segundo pedido encontra o estoque já preso pelo primeiro.
        var session = login();
        var cena = cenaVendavel(session);

        pedido(session, cena, 700, null).andExpect(status().isCreated());

        // Sobram 80 das 780: o segundo pedido de 700 não cabe.
        pedido(session, cena, 700, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("insufficient_lot_stock")))
                .andExpect(jsonPath("$.available", is(80)));
    }

    @Test
    void duasVendasSimultaneasNaoVendemOMESMOEstoqueDuasVezes() throws Exception {
        // O critério de aceite da sprint diz "CONCORRÊNCIA não vende estoque duas vezes", e o teste
        // acima prova a versão sequencial — o segundo pedido encontra o estoque já preso. Sequencial não
        // é concorrente: ele passaria mesmo se a reserva fosse ler-depois-escrever, que é justamente o
        // padrão que quebra quando duas telas vendem o mesmo lote no mesmo segundo. Este aqui roda pelo
        // SERVIÇO, com duas transações de verdade, pelo mesmo motivo que o teste do teto de crédito
        // (DEB-SAL-006) precisou rodar assim.
        var session = login();
        var cena = cenaVendavel(session);
        var brewery = breweryDoProduto(cena);

        // Duas tentativas de 700 unidades contra um lote de 780: cabe uma, e só uma.
        var largada = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var tentativas = executor.invokeAll(List.of(
                    tentaVender(brewery, cena, 700, largada),
                    tentaVender(brewery, cena, 700, largada)), 30, TimeUnit.SECONDS);
            var desfechos = new ArrayList<String>();
            for (var t : tentativas) {
                desfechos.add(t.get());
            }
            assertThat(desfechos)
                    .as("uma venda leva o lote e a outra é recusada — nunca as duas")
                    .containsExactlyInAnyOrder("vendida", "sem estoque");
        } finally {
            executor.shutdownNow();
        }

        // A prova que não depende do que as threads relataram: o lote não ficou negativo nem entregou
        // mais do que tinha. Reservado é exatamente o de um pedido.
        var reservado = jdbc.sql("""
                SELECT COALESCE(SUM(r.units), 0) FROM sales_lot_reservation r
                JOIN sales_order_line l ON l.id = r.order_line_id
                JOIN sales_order o ON o.id = l.order_id
                WHERE o.brewery_id = :brewery AND o.customer_id = :customer
                  AND o.status = 'PLACED'
                """)
                .param("brewery", brewery)
                .param("customer", UUID.fromString(cena.customerId()))
                .query(Integer.class).single();
        assertThat(reservado).as("o estoque reservado não pode passar do que existe").isEqualTo(700);
    }

    /** Uma venda que espera a outra na barreira antes de começar. */
    private Callable<String> tentaVender(UUID brewery, BrewScenario.SalesScene cena, int quantidade,
            CyclicBarrier largada) {
        return () -> {
            largada.await(30, TimeUnit.SECONDS);
            try {
                orders.place(brewery, UUID.randomUUID(), new OrderCommands.PlaceOrder(
                        "PED-" + UUID.randomUUID().toString().substring(0, 8),
                        UUID.fromString(cena.customerId()), UUID.fromString(cena.channelId()),
                        List.of(new OrderCommands.OrderItem(UUID.fromString(cena.productId()),
                                quantidade)),
                        null, null, null, null));
                return "vendida";
            } catch (InsufficientLotStockException e) {
                return "sem estoque";
            }
        };
    }

    private UUID breweryDoProduto(BrewScenario.SalesScene cena) {
        return jdbc.sql("SELECT brewery_id FROM sales_product WHERE id = :id")
                .param("id", UUID.fromString(cena.productId()))
                .query(UUID.class).single();
    }

    @Test
    void oLoteReservadoSomeDaOfertaQuandoAcaba() throws Exception {
        // Sem isto a tela ofereceria 780 unidades de um lote com 780 já vendidas — e alguém prometeria
        // cerveja que já tem dono.
        var session = login();
        var cena = cenaVendavel(session);
        var url = "/api/v1/sales/products/" + cena.productId() + "/sellable-lots";

        mockMvc.perform(get(url).session(session))
                .andExpect(jsonPath("$[0].units", is(780)))
                .andExpect(jsonPath("$[0].freeUnits", is(780)));

        pedido(session, cena, 700, null).andExpect(status().isCreated());

        // O lote continua existindo e continua vendável; o que mudou é quanto sobrou.
        mockMvc.perform(get(url).session(session))
                .andExpect(jsonPath("$[0].units", is(780)))
                .andExpect(jsonPath("$[0].freeUnits", is(80)));

        pedido(session, cena, 80, null).andExpect(status().isCreated());

        // Zerado, ele sai da oferta: mostrá-lo faria alguém prometer o que tem dono.
        mockMvc.perform(get(url).session(session))
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void aChaveDeIdempotenciaDevolveOMesmoPedido() throws Exception {
        // Um duplo clique ou um retry de rede não pode reservar o mesmo estoque duas vezes — o segundo
        // tiraria do próximo comprador uma cerveja que ninguém vai levar.
        var session = login();
        var cena = cenaVendavel(session);
        var chave = UUID.randomUUID().toString();

        var primeiro = pedidoComChave(session, cena, 100, chave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var segundo = pedidoComChave(session, cena, 100, chave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(segundo).get("id").asText())
                .isEqualTo(JSON.readTree(primeiro).get("id").asText());

        // E o estoque foi tocado uma vez só: sobram 680, e não 580.
        pedido(session, cena, 680, null).andExpect(status().isCreated());
    }

    @Test
    void naoSePrometeEntregaDepoisDaValidadeDoLote() throws Exception {
        // A regra que dá nome à história, agora de ponta a ponta. A resposta traz as duas datas e o
        // lote, porque é o que resolve: sem isso, sobra tentativa e erro.
        var session = login();
        var cena = cenaVendavel(session);

        pedido(session, cena, 10, java.time.LocalDate.now().plusDays(5000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("promise_after_shelf_life")))
                .andExpect(jsonPath("$.earliestBestBefore", is(notNullValue())))
                .andExpect(jsonPath("$.lotCode", is(notNullValue())));
    }

    @Test
    void cancelarDevolveOEstoque() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);

        var body = pedido(session, cena, 780, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        // Com tudo reservado, não cabe mais nada.
        pedido(session, cena, 1, null).andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/sales/orders/" + pedidoId + "/cancel").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // Devolvido: cabe de novo.
        pedido(session, cena, 780, null).andExpect(status().isCreated());
    }

    @Test
    void oPedidoCongelaOPrecoEGuardaOLoteReservado() throws Exception {
        // O preço congelado é o que mantém um pedido de março explicável em dezembro; o lote reservado
        // é o que um recall percorre.
        var session = login();
        var cena = cenaVendavel(session);

        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        mockMvc.perform(get("/api/v1/sales/orders/" + pedidoId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(120.00)))
                .andExpect(jsonPath("$.currency", is("BRL")))
                .andExpect(jsonPath("$.lines[0].unitAmount", is(12.0000)))
                .andExpect(jsonPath("$.lines[0].reservations[0].lotCode", is(cena.lotCode())))
                .andExpect(jsonPath("$.lines[0].reservations[0].units", is(10)));
    }

    @Test
    void semPrecoNoCanalOPedidoERecusado() throws Exception {
        // "Ainda não precificado" e "de graça" são coisas opostas, e um total zero faria a venda sair
        // de graça.
        var session = login();
        var cena = cenaVendavel(session);
        var outroCanal = criaCanal(session);

        // O canal SEM preço: era o ponto do teste, e a repartição quase o perdeu.
        mockMvc.perform(post("/api/v1/sales/orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content(scenario.orderBody(cena, outroCanal, 10, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("no_price_for_product")));
    }

    /**
     * Remarcar a entrega depois do pedido feito.
     *
     * <p><strong>A regra da validade vale na remarcação, e não só na criação.</strong> É o furo clássico
     * de uma regra que mora no caminho de entrada: cria-se com data boa e move-se depois para além do
     * vencimento do lote, prometendo ao cliente cerveja que vai estar velha no dia. O endpoint não tinha
     * teste nenhum — a regra estava certa no agregado, e ninguém provava que a porta a chamava.
     */
    @Test
    void remarcarAEntregaRespeitaAMesmaValidadeDaCriacao() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);
        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        // O caminho feliz primeiro: sem ele, a recusa abaixo não distingue "a regra pegou" de "o
        // endpoint não funciona".
        var amanha = java.time.LocalDate.now().plusDays(1);
        promessa(session, pedidoId, amanha).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sales/orders/" + pedidoId).session(session))
                .andExpect(jsonPath("$.promisedFor", is(amanha.toString())));

        // E a regra que guarda a criação guarda a mudança, com os mesmos três dados na recusa.
        promessa(session, pedidoId, java.time.LocalDate.now().plusDays(5000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("promise_after_shelf_life")))
                .andExpect(jsonPath("$.lotCode", is(cena.lotCode())));

        // A promessa recusada não ficou gravada pela metade.
        mockMvc.perform(get("/api/v1/sales/orders/" + pedidoId).session(session))
                .andExpect(jsonPath("$.promisedFor", is(amanha.toString())));
    }

    @Test
    void remarcarExigeAAlcadaDeGerirPedido() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);
        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        mockMvc.perform(put("/api/v1/sales/orders/" + pedidoId + "/promise")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sales.catalog.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"promisedFor\":\"" + java.time.LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions promessa(
            MockHttpSession session, String pedidoId, java.time.LocalDate data) throws Exception {
        return mockMvc.perform(put("/api/v1/sales/orders/" + pedidoId + "/promise").session(session)
                .with(csrf()).contentType("application/json")
                .content("{\"promisedFor\":\"" + data + "\"}"));
    }

    @Test
    void pedidoExigeAlcadaPropria() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);

        mockMvc.perform(post("/api/v1/sales/orders")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sales.catalog.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content(scenario.orderBody(cena, 10, null)))
                .andExpect(status().isForbidden());
    }
}
