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

    @Test
    void oCreditoRecusaAntesDeReservarEstoque() throws Exception {
        // A ORDEM É A REGRA. O pedido acima do teto é recusado pelo CRÉDITO mesmo quando também não há
        // lote livre — reservar antes de conferir prenderia a linha mais disputada do estoque para um
        // pedido que a casa não vai aceitar, e o operador veria a recusa errada: `insufficient_lot_stock`
        // manda procurar cerveja para uma venda que já estava recusada por decisão comercial.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "50.00");

        // 700 unidades passam do teto (8.400,00) E do que o lote tem livre.
        pedido(session, cena, 700, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("credit_limit_exceeded")));

        // A prova de que nada foi reservado: com o teto tirado do caminho, o mesmo pedido entra. Se a
        // tentativa anterior tivesse consumido disponibilidade, este aqui não caberia.
        teto(session, cena, "99999.00");
        pedido(session, cena, 700, null).andExpect(status().isCreated());
    }

    @Test
    void tetoEmOutraMoedaRecusaDizendoQueOProblemaEDeCadastro() throws Exception {
        // Sem taxa de câmbio não há conferência possível. Deixar passar apagaria o teto justamente para
        // o cliente cuja configuração está errada — o controle viraria decoração no caso que mais
        // precisa dele. E o erro precisa apontar o CADASTRO: antes disto a soma estourava dentro do
        // domínio de dinheiro e devolvia um `sales_currency_mismatch` que mandava procurar no pedido.
        var session = login();
        var cena = cenaVendavel(session);
        mockMvc.perform(put("/api/v1/sales/portal/credit/" + cena.customerId()).session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"ceiling\":200.00,\"currency\":\"USD\"}"))
                .andExpect(status().isNoContent());

        pedido(session, cena, 1, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("credit_limit_currency_mismatch")))
                .andExpect(jsonPath("$.ceilingCurrency", is("USD")))
                .andExpect(jsonPath("$.orderCurrency", is("BRL")));
    }

    @Test
    void aAuditoriaRegistraAExcecaoQueACONTECEU() throws Exception {
        // A trilha lê o desfecho, e não o pedido da requisição. Um motivo enviado num pedido que coube
        // no teto não vira exceção em lugar nenhum — nem no pedido, nem na auditoria. Ler a presença do
        // campo faria quem audita contar exceções que nunca houve.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");

        // Cabe no teto, mas manda motivo: a auditoria não pode registrar autorização.
        pedidoComMotivo(session, cena, 10, "mandei por precaução").andExpect(status().isCreated());
        assertQueAuditoriaNaoTem("mandei por precaução");

        // Agora fura de verdade: aí sim a auditoria carrega o motivo.
        pedidoComMotivo(session, cena, 10, "boleto compensa hoje").andExpect(status().isCreated());
        assertQueAuditoriaTem("boleto compensa hoje");
    }

    @Test
    void oReenvioDoPedidoAutorizadoNaoQuebraNemReescreveATrilha() throws Exception {
        // O reenvio devolve o pedido que já existe — e a auditoria dele lê o que ESTÁ GRAVADO. Quando a
        // decisão vinha do pedido e o texto da requisição, um reenvio sem motivo montava um `Map.of` com
        // `null` e derrubava com 500 o que deveria ser a resposta mais simples do sistema.
        var session = login();
        var cena = cenaVendavel(session);
        teto(session, cena, "200.00");
        pedido(session, cena, 10, null).andExpect(status().isCreated());

        var chave = UUID.randomUUID().toString();
        var corpo = scenario.orderBody(cena, cena.channelId(), 10, null, "boleto compensa hoje");
        var primeiro = idOf(mockMvc.perform(post(ORDERS).session(session).with(csrf())
                        .header("Idempotency-Key", chave)
                        .contentType("application/json").content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // O mesmo corpo, SEM motivo, com a mesma chave: é o retry de rede.
        var semMotivo = scenario.orderBody(cena, cena.channelId(), 10, null, null);
        var reenviado = idOf(mockMvc.perform(post(ORDERS).session(session).with(csrf())
                        .header("Idempotency-Key", chave)
                        .contentType("application/json").content(semMotivo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(reenviado)
                .as("o reenvio devolve o mesmo pedido").isEqualTo(primeiro);

        // A asserção é sobre o CONTEÚDO, e não sobre quantas vezes o reenvio audita: toda entrada deste
        // pedido carrega o motivo que foi de fato autorizado. Uma entrada sem ele significaria que a
        // trilha leu a requisição repetida em vez do pedido gravado.
        var doPedido = jdbc.sql("SELECT count(*) FROM audit_event WHERE action = 'sales.order.place'"
                        + " AND target_id = :id").param("id", primeiro).query(Integer.class).single();
        var comMotivo = jdbc.sql("SELECT count(*) FROM audit_event WHERE action = 'sales.order.place'"
                        + " AND target_id = :id AND change_summary::text LIKE '%boleto compensa hoje%'")
                .param("id", primeiro).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(comMotivo)
                .as("nenhuma entrada deste pedido pode perder a autorização registrada")
                .isEqualTo(doPedido).isPositive();
    }

    private void assertQueAuditoriaTem(String motivo) {
        var achou = jdbc.sql("SELECT count(*) FROM audit_event WHERE action = 'sales.order.place'"
                        + " AND change_summary::text LIKE '%' || :motivo || '%'")
                .param("motivo", motivo).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(achou)
                .as("a auditoria precisa registrar a exceção que aconteceu").isEqualTo(1);
    }

    private void assertQueAuditoriaNaoTem(String motivo) {
        var achou = jdbc.sql("SELECT count(*) FROM audit_event WHERE action = 'sales.order.place'"
                        + " AND change_summary::text LIKE '%' || :motivo || '%'")
                .param("motivo", motivo).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(achou)
                .as("motivo enviado num pedido que coube não é exceção, e não pode virar registro")
                .isZero();
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
