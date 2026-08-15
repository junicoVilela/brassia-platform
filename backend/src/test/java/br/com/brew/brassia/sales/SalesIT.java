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

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Produtos, canais e preços de ponta a ponta (SAL-001).
 *
 * <p>O que estes testes fixam é a invariante que dá sentido ao módulo: em qualquer dia, no máximo um
 * preço por produto e canal — e que ela é garantida pelo banco, não só pelo domínio.
 */
@SpringBootTest
@Testcontainers
class SalesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SALES = "/api/v1/sales";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    MockMvc mockMvc;

    /** Uma receita por classe: o produto aponta para ela, e recriá-la a cada teste não prova nada. */
    static String receitaId;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void cadastraProdutoECanalEConsultaDeVolta() throws Exception {
        var session = login();
        var produto = criaProduto(session, "ipa-473", "IPA lata 473 ml");
        criaCanal(session, "taproom", "Taproom");

        mockMvc.perform(get(SALES + "/products").session(session))
                .andExpect(status().isOk())
                // O SKU é normalizado: "ipa-473" e "IPA-473" são o mesmo código no mundo real.
                .andExpect(jsonPath("$[?(@.id=='" + produto + "')].sku", is(java.util.List.of("IPA-473"))));

        mockMvc.perform(get(SALES + "/channels").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='TAPROOM')].name", is(java.util.List.of("Taproom"))));
    }

    @Test
    void oSkuRepetidoNaMesmaCervejariaERecusadoCom409() throws Exception {
        var session = login();
        criaProduto(session, "PILS-350", "Pilsen lata 350 ml");

        mockMvc.perform(post(SALES + "/products").session(session).with(csrf())
                        .contentType("application/json")
                        .content(corpoProduto(session, "pils-350", "Outra Pilsen")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("sales_duplicate_code")))
                .andExpect(jsonPath("$.conflictingCode", is("PILS-350")));
    }

    @Test
    void oPrecoNovoFechaOAnteriorNaVespera() throws Exception {
        var session = login();
        var produto = criaProduto(session, "IPA-A", "IPA A");
        var canal = criaCanal(session, "CANAL-A", "Canal A");

        preco(session, produto, canal, "12.00", "2026-01-01").andExpect(status().isNoContent());
        preco(session, produto, canal, "14.00", "2026-03-01").andExpect(status().isNoContent());

        mockMvc.perform(get(SALES + "/products/" + produto + "/prices?channelId=" + canal).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()", is(2)))
                // As duas pontas são inclusivas: quem compra em 28/02 paga o preço antigo.
                .andExpect(jsonPath("$.entries[0].validTo", is("2026-02-28")))
                .andExpect(jsonPath("$.entries[1].validTo").doesNotExist());
    }

    @Test
    void aSobreposicaoDePeriodoFechadoERecusadaComADataNaResposta() throws Exception {
        var session = login();
        var produto = criaProduto(session, "IPA-B", "IPA B");
        var canal = criaCanal(session, "CANAL-B", "Canal B");
        preco(session, produto, canal, "12.00", "2026-01-01").andExpect(status().isNoContent());
        preco(session, produto, canal, "14.00", "2026-03-01").andExpect(status().isNoContent());

        preco(session, produto, canal, "13.00", "2026-02-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("sales_price_overlap")))
                // A data resolve o problema: sem ela, sobra tentativa e erro.
                .andExpect(jsonPath("$.from", is("2026-02-01")));
    }

    @Test
    void aLinhaDoTempoNaoTrocaDeMoeda() throws Exception {
        // "Aumentou ou baixou?" deixaria de ter resposta se a moeda mudasse no meio.
        var session = login();
        var produto = criaProduto(session, "IPA-C", "IPA C");
        var canal = criaCanal(session, "CANAL-C", "Canal C");
        preco(session, produto, canal, "12.00", "2026-01-01").andExpect(status().isNoContent());

        mockMvc.perform(post(SALES + "/products/" + produto + "/prices").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"channelId":"%s","amount":3.00,"currency":"USD","taxIncluded":false,
                                 "validFrom":"2026-03-01"}
                                """.formatted(canal)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("sales_currency_mismatch")));
    }

    @Test
    void oBancoRecusaSobreposicaoMesmoContornandoODominio() throws Exception {
        // A garantia de verdade é a restrição de exclusão, e não a checagem do domínio: checagem prévia
        // não sobrevive a duas requisições simultâneas, e sobreposição de preço é o que duas telas
        // abertas produzem. Este teste insere direto no banco para provar que a barreira existe lá.
        var session = login();
        var produto = criaProduto(session, "IPA-D", "IPA D");
        var canal = criaCanal(session, "CANAL-D", "Canal D");
        preco(session, produto, canal, "12.00", "2026-01-01").andExpect(status().isNoContent());

        var brewery = jdbc.sql("SELECT brewery_id FROM sales_product WHERE id = :id")
                .param("id", UUID.fromString(produto)).query(UUID.class).single();

        try {
            jdbc.sql("""
                    INSERT INTO sales_price_entry (id, brewery_id, product_id, channel_id, amount,
                                                   currency, tax_included, valid_from, valid_to,
                                                   created_by, created_at)
                    VALUES (:id, :brewery, :product, :channel, 99, 'BRL', false, DATE '2026-06-01',
                            NULL, :by, now())
                    """)
                    .param("id", UUID.randomUUID()).param("brewery", brewery)
                    .param("product", UUID.fromString(produto)).param("channel", UUID.fromString(canal))
                    .param("by", UUID.randomUUID())
                    .update();
            throw new AssertionError("o banco deveria ter recusado a sobreposição");
        } catch (org.springframework.dao.DataIntegrityViolationException esperado) {
            // É o que se quer: ex_sales_price_no_overlap barrou.
        }
    }

    @Test
    void periodosAdjacentesNaoConflitam() throws Exception {
        // Fechado em 28/02 e começando em 01/03 são adjacentes, e daterange [] os aceita lado a lado.
        var session = login();
        var produto = criaProduto(session, "IPA-E", "IPA E");
        var canal = criaCanal(session, "CANAL-E", "Canal E");

        preco(session, produto, canal, "10.00", "2026-01-01").andExpect(status().isNoContent());
        preco(session, produto, canal, "11.00", "2026-03-01").andExpect(status().isNoContent());
        preco(session, produto, canal, "12.00", "2026-06-01").andExpect(status().isNoContent());

        mockMvc.perform(get(SALES + "/products/" + produto + "/prices?channelId=" + canal).session(session))
                .andExpect(jsonPath("$.entries.length()", is(3)));
    }

    @Test
    void oPrecoZeroERecusado() throws Exception {
        var session = login();
        var produto = criaProduto(session, "IPA-F", "IPA F");
        var canal = criaCanal(session, "CANAL-F", "Canal F");

        preco(session, produto, canal, "0", "2026-01-01").andExpect(status().isBadRequest());
    }

    @Test
    void produtoDescontinuadoSomeDaListaPadraoMasContinuaExistindo() throws Exception {
        var session = login();
        var produto = criaProduto(session, "IPA-G", "IPA G");

        mockMvc.perform(put(SALES + "/products/" + produto + "/active").session(session).with(csrf())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(SALES + "/products").session(session))
                .andExpect(jsonPath("$[?(@.id=='" + produto + "')]", is(java.util.List.of())));
        mockMvc.perform(get(SALES + "/products?onlyActive=false").session(session))
                .andExpect(jsonPath("$[?(@.id=='" + produto + "')].sku", is(java.util.List.of("IPA-G"))));
    }

    @Test
    void negaSemPermissaoEIsolaPorCervejaria() throws Exception {
        var session = login();
        var produto = criaProduto(session, "IPA-H", "IPA H");
        var canal = criaCanal(session, "CANAL-H", "Canal H");

        // Quem só lê não cadastra.
        mockMvc.perform(post(SALES + "/products").with(authentication(principal(UUID.randomUUID(),
                        Set.of("sales.catalog.read")))).with(csrf())
                        .contentType("application/json").content(corpoProduto(session, "X-1", "Tentativa")))
                .andExpect(status().isForbidden());

        // Cadastrar produto NÃO dá o direito de mexer em preço: a alçada é própria e crítica.
        mockMvc.perform(post(SALES + "/products/" + produto + "/prices")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sales.catalog.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"channelId":"%s","amount":9.99,"currency":"BRL","taxIncluded":false,
                                 "validFrom":"2026-01-01"}
                                """.formatted(canal)))
                .andExpect(status().isForbidden());

        // Outra cervejaria não enxerga o produto — 404, e não 403.
        mockMvc.perform(get(SALES + "/products/" + produto + "/prices?channelId=" + canal)
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sales.catalog.read")))))
                .andExpect(status().isNotFound());
    }

    private String criaProduto(MockHttpSession session, String sku, String name) throws Exception {
        var result = mockMvc.perform(post(SALES + "/products").session(session).with(csrf())
                        .contentType("application/json").content(corpoProduto(session, sku, name)))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /**
     * A receita precisa existir de verdade: há chave estrangeira para {@code recipe}.
     *
     * <p>Criada uma vez por classe e reaproveitada — o produto aponta para a receita, e criar uma nova a
     * cada teste só acrescentaria tempo de banco sem exercitar nada de vendas.
     */
    private String corpoProduto(MockHttpSession session, String sku, String name) throws Exception {
        return """
                {"sku":"%s","name":"%s","recipeId":"%s","containerId":"%s"}
                """.formatted(sku, name, receita(session), UUID.randomUUID());
    }

    private String receita(MockHttpSession session) throws Exception {
        if (receitaId != null) {
            return receitaId;
        }
        var equipamento = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"BH-SAL","name":"BH","capacityLiters":900,"deadSpaceLiters":20,
                                 "mashEfficiencyPercent":72,"boilOffLitersPerHour":8}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        receitaId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"IPA de vendas","equipmentId":"%s","batchVolumeLiters":400,
                                 "boilTimeMinutes":60,
                                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"}]}
                                """.formatted(equipamento, UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return receitaId;
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    private String criaCanal(MockHttpSession session, String code, String name) throws Exception {
        var result = mockMvc.perform(post(SALES + "/channels").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"%s\",\"name\":\"%s\"}".formatted(code, name)))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions preco(MockHttpSession session,
            String produto, String canal, String valor, String de) throws Exception {
        return mockMvc.perform(post(SALES + "/products/" + produto + "/prices").session(session).with(csrf())
                .contentType("application/json")
                .content("""
                        {"channelId":"%s","amount":%s,"currency":"BRL","taxIncluded":false,
                         "validFrom":"%s"}
                        """.formatted(canal, valor, de)));
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
