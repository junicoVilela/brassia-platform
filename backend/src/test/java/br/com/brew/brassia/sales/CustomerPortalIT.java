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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O portal do cliente: ele vê só o que é dele (SAL-003).
 */
@SpringBootTest
@Testcontainers
class CustomerPortalIT extends CommercialTestSupport {

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
    void oPortalSoMostraOsPedidosDoProprioCliente() throws Exception {
        // O isolamento é estrutural: o cliente vem do vínculo do usuário, e nunca do caminho ou do
        // corpo. Se viesse de fora, bastaria trocá-lo para ver o pedido de outro.
        var session = login();
        var cena = cenaVendavel(session);
        var outroCliente = criaCliente(session);

        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        var doDono = portalUser(session, cena.customerId(), cena.channelId());
        var doOutro = portalUser(session, outroCliente, cena.channelId());

        mockMvc.perform(get("/api/v1/portal/orders").with(authentication(doDono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + pedidoId + "')].code", is(notNullValue())));

        // O outro cliente não vê nada — e o pedido específico responde 404, e não 403: distinguir
        // contaria que o identificador existe em algum lugar.
        mockMvc.perform(get("/api/v1/portal/orders").with(authentication(doOutro)))
                .andExpect(jsonPath("$[?(@.id=='" + pedidoId + "')]", is(java.util.List.of())));
        mockMvc.perform(get("/api/v1/portal/orders/" + pedidoId).with(authentication(doOutro)))
                .andExpect(status().isNotFound());
    }

    @Test
    void oUsuarioDePortalNaoAlcancaOsEndpointsInternos() throws Exception {
        // portal.access é a única permissão que ele recebe, e ela não abre nada interno.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        mockMvc.perform(get("/api/v1/sales/orders").with(authentication(portal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/crm/customers").with(authentication(portal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissaoSemVinculoNaoAbreOPortal() throws Exception {
        // A permissão diz que ele pode entrar; o vínculo diz de quem ele é. Sem o segundo não há a
        // quem mostrar nada.
        var semVinculo = principal(UUID.randomUUID(), Set.of("portal.access"));

        mockMvc.perform(get("/api/v1/portal/catalog").with(authentication(semVinculo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void oCatalogoDoPortalUsaOPrecoDoCanalDoVinculo() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        mockMvc.perform(get("/api/v1/portal/catalog").with(authentication(portal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitAmount", is(12.0000)))
                .andExpect(jsonPath("$[0].currency", is("BRL")))
                .andExpect(jsonPath("$[0].availableUnits", is(780)));
    }

    @Test
    void oClienteFazOProprioPedidoPeloPortal() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json")
                        .content(corpoPortal(cena, 10)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portal/orders").with(authentication(portal)))
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].total", is(120.00)))
                // O cliente vê o que comprou, e não de qual brassa saiu: lote é rastro interno.
                .andExpect(jsonPath("$[0].lines[0].sku", is(notNullValue())));
    }

    @Test
    void oTetoDeCompromissoRecusaComOsTresNumeros() throws Exception {
        // Saber que "passou do limite" sem saber de quanto é o teto, quanto já está comprometido e
        // quanto este pedido pede deixa quem comprou sem ação — e no portal não há vendedor por perto.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        mockMvc.perform(put("/api/v1/sales/portal/credit/" + cena.customerId()).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ceiling\":200.00,\"currency\":\"BRL\"}"))
                .andExpect(status().isNoContent());

        // 10 unidades a 12,00 = 120,00: cabe.
        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 10)))
                .andExpect(status().isCreated());

        // Mais 10 seriam 240,00 de compromisso, acima do teto de 200,00.
        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("credit_limit_exceeded")))
                .andExpect(jsonPath("$.ceiling", is(200.00)))
                .andExpect(jsonPath("$.committed", is(120.0000)))
                .andExpect(jsonPath("$.requested", is(120.0000)));
    }

    /**
     * O cliente consulta o próprio limite antes de montar o pedido.
     *
     * <p>O endpoint não tinha teste, e ele existe para que a recusa por crédito não seja a primeira
     * notícia: no portal não há vendedor por perto, e descobrir o teto batendo nele é a pior forma de
     * descobri-lo. Os dois números que a tela precisa são os mesmos da recusa — o teto e o comprometido.
     */
    @Test
    void oClienteConsultaOProprioLimiteAntesDeEsbarrarNele() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        // Sem teto definido, a resposta é honesta: nulo, e não zero. "Sem limite" e "limite zero" são
        // opostos, e um zero aqui diria ao cliente que ele não pode comprar nada.
        mockMvc.perform(get("/api/v1/portal/credit").with(authentication(portal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ceiling").doesNotExist())
                .andExpect(jsonPath("$.committed", is(0)));

        mockMvc.perform(put("/api/v1/sales/portal/credit/" + cena.customerId()).session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"ceiling\":200.00,\"currency\":\"BRL\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/portal/credit").with(authentication(portal)))
                .andExpect(jsonPath("$.ceiling", is(200.00)))
                .andExpect(jsonPath("$.currency", is("BRL")))
                .andExpect(jsonPath("$.committed", is(0)));

        // E o comprometido acompanha o pedido: é o número que muda, e por isso o que prova a consulta
        // ser derivada e não um retrato do cadastro.
        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 10)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portal/credit").with(authentication(portal)))
                .andExpect(jsonPath("$.ceiling", is(200.00)))
                .andExpect(jsonPath("$.committed", is(120.0000)));
    }

    @Test
    void semTetoTudoCabe() throws Exception {
        // Não recusar por falta de decisão é reversível; recusar um pedido bom porque alguém chutou um
        // teto não é.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 700)))
                .andExpect(status().isCreated());
    }

    @Test
    void aRecompraRepeteOsItensComOPrecoDeHoje() throws Exception {
        // Repete a intenção, e não o valor: reaproveitar o preço antigo faria a cervejaria vender
        // abaixo da lista sem ninguém ter decidido isso.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.customerId(), cena.channelId());

        var body = mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 10)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var primeiro = JSON.readTree(body).get("id").asText();

        // O preço sobe para 15,00 antes da recompra.
        mockMvc.perform(post("/api/v1/sales/products/" + cena.productId() + "/prices").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"channelId\":\"" + cena.channelId() + "\",\"amount\":15.00,"
                                + "\"currency\":\"BRL\",\"taxIncluded\":false,"
                                + "\"validFrom\":\"" + java.time.LocalDate.now() + "\"}"))
                .andExpect(status().isNoContent());

        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var recompra = mockMvc.perform(post("/api/v1/portal/orders/" + primeiro + "/reorder")
                        .with(authentication(portal)).with(csrf()).contentType("application/json")
                        .content("{\"code\":\"REC-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var novoId = JSON.readTree(recompra).get("id").asText();

        mockMvc.perform(get("/api/v1/portal/orders/" + novoId).with(authentication(portal)))
                .andExpect(jsonPath("$.total", is(150.00)))
                .andExpect(jsonPath("$.lines[0].quantity", is(10)));
    }
}
