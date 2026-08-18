package br.com.brew.brassia.forecast;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Previsão de demanda de ponta a ponta (FCST-001).
 *
 * <p>O histórico é montado direto no banco, e não pelo caminho de pedido: um pedido de verdade exige
 * lote acabado liberado, e criar doze meses deles levaria minutos para provar uma conta que não depende
 * disso. O caminho completo do pedido já é exercitado em {@code PackagingRunIT}; aqui o que se prova é
 * que a previsão lê o histórico certo e diz a verdade sobre ele.
 */
@SpringBootTest
@Testcontainers
class DemandForecastIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    MockMvc mockMvc;

    static String receitaId;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void semHistoricoAPrevisaoDizQueNaoTemPrevisao() throws Exception {
        // A ausência é dita, e não disfarçada de zero: um zero pareceria "ninguém quer este produto".
        var session = login();
        var cena = produto(session);

        mockMvc.perform(get(url(cena.produtoId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence", is("INSUFFICIENT")))
                .andExpect(jsonPath("$.hasNumbers", is(false)))
                .andExpect(jsonPath("$.expectedUnits").doesNotExist())
                // A versão do método vem mesmo sem previsão: é ela que explica o que foi tentado.
                .andExpect(jsonPath("$.method", is("moving-average v1")));
    }

    @Test
    void comSeisMesesDeHistoricoAPrevisaoTrazOsQuatroDados() throws Exception {
        // O aceite pede dados, versão, erro e confiança — os quatro juntos, porque o número sozinho
        // mente.
        var session = login();
        var cena = produto(session);
        for (var i = 6; i >= 1; i--) {
            pedidoNoMes(cena, YearMonth.now().minusMonths(i), 100);
        }

        mockMvc.perform(get(url(cena.produtoId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNumbers", is(true)))
                .andExpect(jsonPath("$.expectedUnits", is(100.00)))
                .andExpect(jsonPath("$.sampleMonths", is(6)))
                .andExpect(jsonPath("$.method", is("moving-average v1")))
                .andExpect(jsonPath("$.meanAbsolutePercentageError", is(notNullValue())))
                .andExpect(jsonPath("$.confidence", is("MODERATE")));
    }

    @Test
    void oPedidoCanceladoNaoContaComoDemanda() throws Exception {
        // Ele foi intenção que não virou venda. Contá-lo faria a previsão enxergar uma demanda que a
        // cervejaria nunca atendeu.
        var session = login();
        var cena = produto(session);
        for (var i = 6; i >= 1; i--) {
            pedidoNoMes(cena, YearMonth.now().minusMonths(i), 100);
        }
        // Um cancelado enorme no meio: se contasse, a média dispararia.
        var cancelado = pedidoNoMes(cena, YearMonth.now().minusMonths(3), 5000);
        jdbc.sql("UPDATE sales_order SET status = 'CANCELLED' WHERE id = :id")
                .param("id", cancelado).update();

        mockMvc.perform(get(url(cena.produtoId)).session(session))
                .andExpect(jsonPath("$.expectedUnits", is(100.00)));
    }

    @Test
    void oMesCorrenteFicaDeForaDaJanela() throws Exception {
        // Ele está incompleto: incluí-lo faria a previsão baixar todo dia 1º e subir até o dia 31, sem
        // nada ter mudado na demanda.
        var session = login();
        var cena = produto(session);
        for (var i = 6; i >= 1; i--) {
            pedidoNoMes(cena, YearMonth.now().minusMonths(i), 100);
        }
        pedidoNoMes(cena, YearMonth.now(), 9000);

        mockMvc.perform(get(url(cena.produtoId)).session(session))
                .andExpect(jsonPath("$.expectedUnits", is(100.00)))
                .andExpect(jsonPath("$.sampleMonths", is(6)));
    }

    @Test
    void oMesSemVendaEntraComoZeroENaoESaltado() throws Exception {
        // Omitir encurtaria a série e faria a média subir: a previsão passaria a descrever só os meses
        // bons, que é o erro mais fácil de cometer aqui.
        var session = login();
        var cena = produto(session);
        pedidoNoMes(cena, YearMonth.now().minusMonths(4), 100);
        pedidoNoMes(cena, YearMonth.now().minusMonths(3), 100);
        // Nada nos meses 2 e 1.

        mockMvc.perform(get(url(cena.produtoId)).session(session))
                .andExpect(jsonPath("$.sampleMonths", is(4)))
                // Média de 100, 100, 0, 0 = 50 — e não 100, que é o que dariam só os meses bons.
                .andExpect(jsonPath("$.expectedUnits", is(50.00)));
    }

    @Test
    void negaSemPermissaoEIsolaPorCervejaria() throws Exception {
        var session = login();
        var cena = produto(session);

        mockMvc.perform(get(url(cena.produtoId))
                        .with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());

        // Outra cervejaria não enxerga o histórico: sem venda, sem previsão.
        mockMvc.perform(get(url(cena.produtoId)).with(authentication(
                        principal(UUID.randomUUID(), Set.of("forecast.demand.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence", is("INSUFFICIENT")));
    }

    // --- cenário ---

    @Test
    void semTanqueDeclaradoACapacidadeENaoSeiENaoZero() throws Exception {
        // DUV-FCST-001. Zero diria que a cervejaria não consegue produzir nada, e alguém planejaria em
        // cima disso — mesma escolha que a previsão faz com histórico curto.
        var session = login();
        var cena = produto(session);
        // Independente de ordem: a política é da cervejaria inteira, e outro teste desta classe declara
        // tanque. Um teste que só passa se rodar primeiro é um teste que vai falhar sozinho um dia.
        jdbc.sql("DELETE FROM forecast_tank_cycle WHERE brewery_id = :b")
                .param("b", cena.breweryId()).update();

        mockMvc.perform(get(capacidade(cena.produtoId())).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.known", is(false)))
                .andExpect(jsonPath("$.capacityLiters").doesNotExist())
                .andExpect(jsonPath("$.fits").doesNotExist());
    }

    @Test
    void aCasaDeclaraOCicloEACapacidadeAparece() throws Exception {
        // O sistema não infere o ciclo: quantos dias uma cerveja ocupa o tanque depende do estilo, da
        // temperatura e do que a casa aceita.
        var session = login();
        var cena = produto(session);
        var tanque = criaFermentador(session, 1000);

        mockMvc.perform(put("/api/v1/forecast/tank-cycles/" + tanque).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"cycleDays\":14,\"note\":\"IPA da casa\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(capacidade(cena.produtoId())).session(session))
                .andExpect(jsonPath("$.known", is(true)))
                // 30 ou 31 dias ÷ 14 = 2 ciclos inteiros × 1000 L. O lote que não termina no período não
                // conta.
                .andExpect(jsonPath("$.capacityLiters", is(2000.0)))
                .andExpect(jsonPath("$.tanks[0]").exists())
                .andExpect(jsonPath("$.fits").exists());
    }

    @Test
    void oTanqueSaiDaContaQuandoADeclaracaoERemovida() throws Exception {
        // Ele saiu de operação, virou maturador. Sem isto, a única forma de corrigir seria declarar um
        // ciclo absurdo, e a capacidade mentiria em silêncio.
        var session = login();
        var cena = produto(session);
        var tanque = criaFermentador(session, 800);
        var codigoDoTanque = jdbc.sql("SELECT code FROM equipment WHERE id = :i")
                .param("i", UUID.fromString(tanque)).query(String.class).single();
        mockMvc.perform(put("/api/v1/forecast/tank-cycles/" + tanque).session(session).with(csrf())
                        .contentType("application/json").content("{\"cycleDays\":10}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/forecast/tank-cycles/" + tanque).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // O tanque removido sai da conta. Os demais desta classe podem existir, então o que se verifica é
        // que ESTE não aparece mais.
        var corpo = mockMvc.perform(get(capacidade(cena.produtoId())).session(session))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(corpo).doesNotContain(codigoDoTanque);
    }

    @Test
    void naoSeDeclaraCicloParaEquipamentoInexistente() throws Exception {
        // Criaria uma linha que nunca entra na conta, e a casa acharia que declarou.
        var session = login();

        mockMvc.perform(put("/api/v1/forecast/tank-cycles/" + UUID.randomUUID()).session(session)
                        .with(csrf()).contentType("application/json").content("{\"cycleDays\":14}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void declararCicloTemAlcadaPropria() throws Exception {
        var session = login();
        var tanque = criaFermentador(session, 1000);

        mockMvc.perform(put("/api/v1/forecast/tank-cycles/" + tanque)
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("forecast.demand.read"))))
                        .with(csrf()).contentType("application/json").content("{\"cycleDays\":14}"))
                .andExpect(status().isForbidden());
    }

    private String capacidade(String produtoId) {
        return "/api/v1/forecast/products/" + produtoId + "/capacity";
    }

    private String criaFermentador(MockHttpSession session, int litros) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"FV-%s","name":"Fermentador","capacityLiters":%d,
                                 "deadSpaceLiters":10,"mashEfficiencyPercent":72,
                                 "boilOffLitersPerHour":8}
                                """.formatted(sfx, litros)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private record Cena(String produtoId, String clienteId, String canalId, UUID breweryId) {}

    private String url(String produtoId) {
        return "/api/v1/forecast/products/" + produtoId + "/demand";
    }

    private Cena produto(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var recipeId = receita(session);
        var body = mockMvc.perform(post("/api/v1/sales/products").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"sku":"FC-%s","name":"Produto de previsão","recipeId":"%s",
                                 "containerId":"%s"}
                                """.formatted(sfx, recipeId, UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var produtoId = JSON.readTree(body).get("id").asText();

        var canal = mockMvc.perform(post("/api/v1/sales/channels").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"FC-%s\",\"name\":\"Canal\"}".formatted(sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var cliente = mockMvc.perform(post("/api/v1/crm/customers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"legalName\":\"Cliente de previsão\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        var brewery = jdbc.sql("SELECT brewery_id FROM sales_product WHERE id = :id")
                .param("id", UUID.fromString(produtoId)).query(UUID.class).single();
        return new Cena(produtoId, JSON.readTree(cliente).get("id").asText(),
                JSON.readTree(canal).get("id").asText(), brewery);
    }

    /**
     * Uma receita por classe. O produto aponta para ela (há chave estrangeira), e recriá-la a cada
     * teste só acrescentaria tempo de banco sem exercitar nada de previsão.
     */
    private String receita(MockHttpSession session) throws Exception {
        if (receitaId != null) {
            return receitaId;
        }
        var equipamento = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"BH-FC","name":"BH","capacityLiters":900,"deadSpaceLiters":20,
                                 "mashEfficiencyPercent":72,"boilOffLitersPerHour":8}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        receitaId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Receita de previsão","equipmentId":"%s","batchVolumeLiters":400,
                                 "boilTimeMinutes":60,
                                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"}]}
                                """.formatted(equipamento, UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return receitaId;
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    /**
     * Um pedido gravado direto, com a data do mês desejado.
     *
     * <p>Sem reserva de lote: o que a previsão lê é a linha do pedido, e montar o caminho completo
     * exigiria doze lotes liberados para provar uma conta que não depende deles.
     */
    private UUID pedidoNoMes(Cena cena, YearMonth mes, int unidades) {
        var orderId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO sales_order (id, brewery_id, code, customer_id, channel_id, status, placed_on,
                                         created_by, created_at)
                VALUES (:id, :brewery, :code, :customer, :channel, 'PLACED', :placed, :by, now())
                """)
                .param("id", orderId).param("brewery", cena.breweryId)
                .param("code", "FC-" + UUID.randomUUID().toString().substring(0, 12))
                .param("customer", UUID.fromString(cena.clienteId))
                .param("channel", UUID.fromString(cena.canalId))
                .param("placed", java.sql.Date.valueOf(mes.atDay(1)))
                .param("by", UUID.randomUUID())
                .update();
        jdbc.sql("""
                INSERT INTO sales_order_line (id, brewery_id, order_id, product_id, sku, quantity,
                                              unit_amount, currency, tax_included)
                VALUES (:id, :brewery, :order, :product, 'FC', :qty, 10, 'BRL', false)
                """)
                .param("id", UUID.randomUUID()).param("brewery", cena.breweryId)
                .param("order", orderId).param("product", UUID.fromString(cena.produtoId))
                .param("qty", unidades)
                .update();
        return orderId;
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
