package br.com.brew.brassia.sales;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.support.BrewScenario;
import br.com.brew.brassia.support.CommercialTestSupport;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O teto de crédito na porta do vendedor (SAL-004).
 *
 * <p>Antes disto o teto só era conferido no portal: o mesmo cliente tinha dois tratamentos dependendo de
 * por onde o pedido entrou.
 */
@SpringBootTest
@Testcontainers
class OrderCreditIT extends CommercialTestSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        scenario = new BrewScenario(mockMvc);
    }

    @Test
    void oTetoAgoraValeTambemNaPortaDoVendedor() throws Exception {
        // O buraco que esta história fecha: pela porta interna o pedido entrava sem consultar limite.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");

        pedido(session, cena, 10, null).andExpect(status().isCreated());
        pedido(session, cena, 10, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("credit_limit_exceeded")))
                .andExpect(jsonPath("$.ceiling", is(200.00)))
                .andExpect(jsonPath("$.committed", is(120.0000)))
                .andExpect(jsonPath("$.requested", is(120.00)));
    }

    @Test
    void comMotivoEPermissaoOPedidoPassaEFicaORegistroDeQuemAutorizou() throws Exception {
        // Recusar duro faria o vendedor cadastrar um teto maior "só por hoje" e esquecer de voltar — e aí
        // o limite deixa de existir para sempre, em vez de por um pedido.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");
        pedido(session, cena, 10, null).andExpect(status().isCreated());

        var id = idOf(pedidoComMotivo(session, cena, 10, "pagamento do boleto cai hoje")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/sales/orders/" + id).session(session))
                .andExpect(jsonPath("$.creditOverrideReason", is("pagamento do boleto cai hoje")))
                .andExpect(jsonPath("$.creditOverrideBy", is(notNullValue())));
    }

    @Test
    void quemRegistraPedidoNaoAutorizaExcecaoPorTabela() throws Exception {
        // Permissão separada e crítica: ela deixa passar uma venda acima do que a casa decidiu carregar.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");
        pedido(session, cena, 10, null).andExpect(status().isCreated());

        var soRegistra = principal(UUID.randomUUID(), Set.of("sales.order.manage", "sales.order.read"));
        mockMvc.perform(post(ORDERS).with(authentication(soRegistra)).with(csrf())
                        .contentType("application/json")
                        .content(scenario.orderBody(cena, cena.channelId(), 10, null, "confio nele")))
                .andExpect(status().isForbidden());
    }

    @Test
    void oPedidoQueCabeNaoRegistraAutorizacaoNenhuma() throws Exception {
        // Guardar a justificativa num pedido que cabia criaria um registro dizendo "liberado acima do
        // teto" para uma venda que nunca passou de teto nenhum — e quem auditasse contaria exceções que
        // não aconteceram.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");

        var id = idOf(pedidoComMotivo(session, cena, 10, "mandei por precaução")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/sales/orders/" + id).session(session))
                .andExpect(jsonPath("$.creditOverrideReason", is(nullValue())));
    }

    @Test
    void semTetoTudoCabePelaPortaInterna() throws Exception {
        // Não recusar por falta de decisão é reversível; recusar um pedido bom porque alguém chutou um
        // teto não é.
        var session = login();
        var cena = cenaVendavel(session);

        pedido(session, cena, 700, null).andExpect(status().isCreated());
    }

    @Test
    void oRecebimentoLiberaOTetoTambemParaOVendedor() throws Exception {
        // As duas histórias se encontram: o teto mede recebível (DEB-SAL-002), e agora vale nas duas
        // portas. Quem pagou volta a poder comprar sem precisar de exceção.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");
        var pedidoId = idOf(pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        pedido(session, cena, 10, null).andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/sales/orders/" + pedidoId + "/payments").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"amount\":120.00,\"currency\":\"BRL\",\"method\":\"PIX\"}"))
                .andExpect(status().isCreated());

        pedido(session, cena, 10, null).andExpect(status().isCreated());
    }

    private void teto(MockHttpSession session, BrewScenario.SalesScene cena, String valor)
            throws Exception {
        mockMvc.perform(put("/api/v1/sales/portal/credit/" + cena.customerId()).session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"ceiling\":" + valor + ",\"currency\":\"BRL\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions pedidoComMotivo(MockHttpSession session, BrewScenario.SalesScene cena,
            int quantidade, String motivo) throws Exception {
        return mockMvc.perform(post(ORDERS).session(session).with(csrf())
                .contentType("application/json")
                .content(scenario.orderBody(cena, cena.channelId(), quantidade, null, motivo)));
    }
}
