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

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.support.BrewScenario;
import br.com.brew.brassia.support.CommercialTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
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
