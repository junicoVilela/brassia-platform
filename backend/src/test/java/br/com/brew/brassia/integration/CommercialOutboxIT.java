package br.com.brew.brassia.integration;

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
 * Os fatos comerciais saem pelo outbox que já existia (INT-008).
 */
@SpringBootTest
@Testcontainers
class CommercialOutboxIT extends CommercialTestSupport {

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
    void oPedidoConfirmadoEnfileiraEntregaSemDadoPessoal() throws Exception {
        // O critério da história é "integração externa falha sem corromper pedido", e ele já era o
        // motivo de o outbox existir: o pedido grava a INTENÇÃO de entregar no mesmo commit, e quem
        // entrega é outro processo, depois. Nenhum provedor fora do ar segura uma venda.
        var session = login();
        var cena = cenaVendavel(session);
        var assinatura = assina(session, "sales_order.placed");

        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        var payload = payloadDaEntrega(assinatura, pedidoId);
        assertThat(payload.get("orderId").asText()).isEqualTo(pedidoId);
        assertThat(payload.get("total").asText()).isEqualTo("120.00");
        assertThat(payload.get("currency").asText()).isEqualTo("BRL");
        assertThat(payload.get("customerId").asText()).isEqualTo(cena.customerId());

        // O corpo NÃO leva dado pessoal: consentimento é por finalidade, e "integrar com o POS" não é
        // finalidade que alguém consentiu. Quem precisar do contato pede pela API, com alçada.
        assertThat(payload.has("contactName")).isFalse();
        assertThat(payload.has("email")).isFalse();
        assertThat(payload.has("phone")).isFalse();
    }

    @Test
    void oCancelamentoTambemSai() throws Exception {
        // Sem ele, o e-commerce continuaria anunciando como vendido um item que voltou para a vitrine.
        var session = login();
        var cena = cenaVendavel(session);
        var assinatura = assina(session, "sales_order.cancelled");

        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();
        mockMvc.perform(post("/api/v1/sales/orders/" + pedidoId + "/cancel").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(payloadDaEntrega(assinatura, pedidoId).get("code").asText()).isNotBlank();
    }

    @Test
    void aLiberacaoDoLoteAvisaQuemVendeLaFora() throws Exception {
        // É o gatilho para o e-commerce publicar o produto: antes da liberação não há o que vender.
        var session = login();
        var planId = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, planId);
        var assinatura = assina(session, "finished_lot.released");

        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        var payload = payloadDaEntrega(assinatura, lotId);
        assertThat(payload.get("finishedLotId").asText()).isEqualTo(lotId);
        assertThat(payload.get("units").asInt()).isEqualTo(780);
        // Sem evidência de oxigênio ainda: validade nula, e o corpo diz isso em vez de omitir o campo —
        // campo ausente faria quem integra achar que a versão do payload mudou.
        assertThat(payload.hasNonNull("bestBefore")).isFalse();
        assertThat(payload.has("bestBefore")).isTrue();
    }

    @Test
    void oPedidoSobreviveAFalhaDaEntrega() throws Exception {
        // A prova do critério: a entrega fica pendente para o processo de retry, e o pedido está lá,
        // confirmado, com o estoque reservado. Nenhum provedor fora do ar desfaz uma venda.
        var session = login();
        var cena = cenaVendavel(session);
        var assinatura = assina(session, "sales_order.placed");

        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        // A entrega existe e ainda não foi entregue — o endereço é fictício e nunca vai responder.
        var status = jdbc.sql("""
                SELECT status FROM webhook_delivery
                WHERE subscription_id = :s AND event_id = :e
                """)
                .param("s", UUID.fromString(assinatura)).param("e", pedidoId)
                .query(String.class).single();
        assertThat(status).isEqualTo("PENDING");

        // E o pedido continua de pé, com a reserva.
        mockMvc.perform(get("/api/v1/sales/orders/" + pedidoId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PLACED")))
                .andExpect(jsonPath("$.lines[0].reservations[0].units", is(10)));
    }

    /** Uma assinatura de webhook para o evento, com endereço que nunca responde. */
    private String assina(MockHttpSession session, String evento) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var body = mockMvc.perform(post("/api/v1/integration/webhooks").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"ERP-%s","endpoint":"https://erp.example.com/hooks",
                                 "events":["%s"]}
                                """.formatted(sfx, evento)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // O id vem aninhado: a resposta de criação também traz o segredo, que só aparece uma vez.
        return JSON.readTree(body).get("subscription").get("id").asText();
    }

    private JsonNode payloadDaEntrega(String assinatura, String eventId) throws Exception {
        var payload = jdbc.sql("""
                SELECT payload FROM webhook_delivery
                WHERE subscription_id = :s AND event_id = :e
                """)
                .param("s", UUID.fromString(assinatura)).param("e", eventId)
                .query(String.class).single();
        return JSON.readTree(payload);
    }
}
