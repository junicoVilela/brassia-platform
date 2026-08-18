package br.com.brew.brassia.sales;

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
 * A baixa de pagamento, e o limite de crédito que passa a medir recebível (DEB-SAL-002).
 *
 * <p>Antes disto, o comprometido somava pedidos: quem pagava continuava com o limite ocupado, e quem
 * recebia o pedido sem pagar saía da conta. Os dois erros apareciam no mesmo cliente.
 */
@SpringBootTest
@Testcontainers
class PaymentIT extends CommercialTestSupport {

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
    void oRecebimentoParcialLiberaOLimiteNaProporcaoDoQueEntrou() throws Exception {
        // A decisão central. Ignorar o parcial faria um cliente que pagou 90% ocupar o limite inteiro —
        // e o vendedor recusaria a venda de alguém que está em dia.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        var pedidoId = idOf(pedidoPortal(portal, cena, 10).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // 120,00 comprometidos de um teto de 200,00: outros 120,00 não cabem.
        pedidoPortal(portal, cena, 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("credit_limit_exceeded")));

        recebe(session, pedidoId, "60.00").andExpect(status().isCreated());

        // Agora o compromisso é de 60,00, e o mesmo pedido cabe.
        pedidoPortal(portal, cena, 10).andExpect(status().isCreated());
    }

    @Test
    void oEstornoDevolveOCompromisso() throws Exception {
        // Estorno é evento compensatório: o recebimento continua lá, e a soma volta ao que era. Se o
        // estorno apagasse a linha, o limite voltaria sem que ninguém conseguisse explicar por quê.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");
        var portal = portalUser(session, cena.customerId(), cena.channelId());
        var pedidoId = idOf(pedidoPortal(portal, cena, 10).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        var pagamentoId = idOf(recebe(session, pedidoId, "120.00").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        pedidoPortal(portal, cena, 10).andExpect(status().isCreated());

        estorna(session, pagamentoId, "cheque devolvido").andExpect(status().isCreated());
        // E o limite volta a estar ocupado: 240,00 de compromisso contra um teto de 200,00.
        pedidoPortal(portal, cena, 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("credit_limit_exceeded")));

        // Os dois lançamentos ficam, e o saldo do pedido volta a ser o total.
        mockMvc.perform(get("/api/v1/sales/orders/" + pedidoId + "/payments").session(session))
                .andExpect(jsonPath("$.payments.length()", is(2)))
                .andExpect(jsonPath("$.received", is(0.00)))
                .andExpect(jsonPath("$.outstanding", is(120.00)))
                .andExpect(jsonPath("$.payments[1].reversal", is(true)));
    }

    @Test
    void oMesmoRecebimentoNaoSeEstornaDuasVezes() throws Exception {
        // A garantia é o índice único parcial: estornar duas vezes tiraria da conta um dinheiro que só
        // entrou uma vez, e o cliente ganharia limite que não tem.
        var session = login();
        var cena = cenaVendavel(session);
        var pedidoId = idOf(pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var pagamentoId = idOf(recebe(session, pedidoId, "120.00").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        estorna(session, pagamentoId, "lançado no pedido errado").andExpect(status().isCreated());
        estorna(session, pagamentoId, "de novo").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("payment_already_reversed")));
    }

    @Test
    void oEstornoPrecisaDeMotivo() throws Exception {
        // "Estornado" sem motivo deixa quem confere seis meses depois sem saber se foi engano de
        // digitação, cheque devolvido ou pedido cancelado — e as três levam a conversas diferentes.
        var session = login();
        var cena = cenaVendavel(session);
        var pedidoId = idOf(pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var pagamentoId = idOf(recebe(session, pedidoId, "120.00").andReturn().getResponse()
                .getContentAsString());

        estorna(session, pagamentoId, "   ").andExpect(status().isBadRequest());
    }

    @Test
    void oRecebimentoMaiorQueOSaldoERecusadoComONumeroCerto() throws Exception {
        // É o que pega o zero a mais: 1.200,00 num pedido de 120,00 é digitação, e não pagamento.
        var session = login();
        var cena = cenaVendavel(session);
        var pedidoId = idOf(pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        recebe(session, pedidoId, "1200.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("payment_exceeds_balance")))
                .andExpect(jsonPath("$.outstanding", is(120.00)))
                .andExpect(jsonPath("$.requested", is(1200.00)));

        // E o mesmo vale para o segundo lançamento que passa do que restou.
        recebe(session, pedidoId, "100.00").andExpect(status().isCreated());
        recebe(session, pedidoId, "50.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.outstanding", is(20.00)));
    }

    @Test
    void oPedidoCanceladoNaoRecebePagamento() throws Exception {
        // Baixar pagamento de pedido cancelado esconderia o problema real: ou o cancelamento está
        // errado, ou o dinheiro entrou por outro motivo.
        var session = login();
        var cena = cenaVendavel(session);
        var pedidoId = idOf(pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sales/orders/" + pedidoId + "/cancel").session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        recebe(session, pedidoId, "120.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("order_not_changeable")));
    }

    @Test
    void quemNaoTemAPermissaoNaoLancaNemEstorna() throws Exception {
        // Estornar é crítico: tira dinheiro da conta e devolve limite ao cliente.
        var session = login();
        var cena = cenaVendavel(session);
        var pedidoId = idOf(pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var pagamentoId = idOf(recebe(session, pedidoId, "120.00").andReturn().getResponse()
                .getContentAsString());

        var semNada = principal(UUID.randomUUID(), Set.of("sales.order.read"));
        mockMvc.perform(post("/api/v1/sales/orders/" + pedidoId + "/payments")
                        .with(authentication(semNada)).with(csrf()).contentType("application/json")
                        .content(corpo("120.00")))
                .andExpect(status().isForbidden());

        // Quem lança não estorna: são permissões separadas, e a segunda é crítica.
        var soLanca = principal(UUID.randomUUID(), Set.of("sales.payment.record"));
        mockMvc.perform(post("/api/v1/sales/payments/" + pagamentoId + "/reversal")
                        .with(authentication(soLanca)).with(csrf()).contentType("application/json")
                        .content("{\"reason\":\"engano\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aOutraCervejariaNaoVeNemEstornaOPagamento() throws Exception {
        // O identificador é global; o alcance não é. Sem o filtro por cervejaria, quem descobrisse um
        // UUID mexeria no caixa da casa vizinha.
        var session = login();
        var cena = cenaVendavel(session);
        var pedidoId = idOf(pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var pagamentoId = idOf(recebe(session, pedidoId, "120.00").andReturn().getResponse()
                .getContentAsString());

        var vizinha = principal(UUID.randomUUID(),
                Set.of("sales.order.read", "sales.payment.record", "sales.payment.reverse"));
        // 404, e não 403: distinguir contaria que o identificador existe em algum lugar.
        mockMvc.perform(get("/api/v1/sales/orders/" + pedidoId + "/payments")
                        .with(authentication(vizinha)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/sales/payments/" + pagamentoId + "/reversal")
                        .with(authentication(vizinha)).with(csrf()).contentType("application/json")
                        .content("{\"reason\":\"não é meu\"}"))
                .andExpect(status().isNotFound());
    }

    private void teto(MockHttpSession session, BrewScenario.SalesScene cena, String valor)
            throws Exception {
        mockMvc.perform(put("/api/v1/sales/portal/credit/" + cena.customerId()).session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"ceiling\":" + valor + ",\"currency\":\"BRL\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions recebe(MockHttpSession session, String pedidoId, String valor)
            throws Exception {
        return mockMvc.perform(post("/api/v1/sales/orders/" + pedidoId + "/payments").session(session)
                .with(csrf()).contentType("application/json").content(corpo(valor)));
    }

    private ResultActions estorna(MockHttpSession session, String pagamentoId, String motivo)
            throws Exception {
        return mockMvc.perform(post("/api/v1/sales/payments/" + pagamentoId + "/reversal")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"" + motivo + "\"}"));
    }

    private static String corpo(String valor) {
        return "{\"amount\":" + valor + ",\"currency\":\"BRL\",\"method\":\"PIX\"}";
    }
}
