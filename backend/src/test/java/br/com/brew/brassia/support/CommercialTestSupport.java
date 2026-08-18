package br.com.brew.brassia.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * O que as classes de teste comerciais compartilham (DEB-SAL-003).
 *
 * <p><strong>Por que uma base, e não cópias.</strong> Liberação, pedido, portal e webhook precisam do
 * mesmo cenário — lote vendável, produto, canal, preço, cliente — e antes moravam todos num único arquivo
 * de mil linhas porque duplicá-lo custava mais. Repartir o arquivo sem extrair isto teria trocado um
 * arquivo grande por quatro cópias do mesmo cenário, que divergem na primeira regra nova.
 *
 * <p>É deliberadamente fina: login, principal e o pedido daquela cena. O que constrói cerveja mora na
 * {@link BrewScenario}.
 */
public abstract class CommercialTestSupport {

    protected static final ObjectMapper JSON = new ObjectMapper();
    protected static final String LOTS = "/api/v1/packaging/finished-lots";
    protected static final String ORDERS = "/api/v1/sales/orders";

    protected MockMvc mockMvc;
    protected BrewScenario scenario;

    protected MockHttpSession login() throws Exception {
        return scenario.login();
    }

    /** Um principal de outra cervejaria, para os testes de isolamento. */
    protected Authentication principal(UUID breweryId, Set<String> permissions) {
        return principal(breweryId, permissions, UUID.randomUUID());
    }

    protected Authentication principal(UUID breweryId, Set<String> permissions, UUID userId) {
        var p = new SecurityPrincipal(userId, breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }

    protected BrewScenario.SalesScene cenaVendavel(MockHttpSession session) throws Exception {
        return scenario.sellableProduct(session);
    }

    protected ResultActions pedido(MockHttpSession session, BrewScenario.SalesScene cena,
            int quantidade, LocalDate promessa) throws Exception {
        return mockMvc.perform(post(ORDERS).session(session).with(csrf())
                .contentType("application/json").content(scenario.orderBody(cena, quantidade, promessa)));
    }

    /** O mesmo pedido, com a chave de idempotência que o cliente manda. */
    protected ResultActions pedidoComChave(MockHttpSession session, BrewScenario.SalesScene cena,
            int quantidade, String chave) throws Exception {
        return mockMvc.perform(post(ORDERS).session(session).with(csrf())
                .header("Idempotency-Key", chave)
                .contentType("application/json").content(scenario.orderBody(cena, quantidade, null)));
    }

    protected String criaCanal(MockHttpSession session) throws Exception {
        return scenario.channel(session);
    }

    protected String criaCliente(MockHttpSession session) throws Exception {
        return scenario.customer(session);
    }

    protected void libera(MockHttpSession session, String lotId) throws Exception {
        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    /** Um plano reservado, pronto para executar — a porta de entrada dos testes de envase. */
    protected String reservedPlan(MockHttpSession session, int stock) throws Exception {
        var batchId = scenario.fermentingBatch(session);
        var containerId = scenario.canContainer(session);
        scenario.receiveContainers(session, containerId, stock);
        return scenario.reservedPlan(session, batchId, containerId, 800);
    }

    protected ResultActions execute(MockHttpSession session, String planId, String input,
            int produced, int rejected) throws Exception {
        return mockMvc.perform(post("/api/v1/packaging/plans/" + planId + "/execution")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"inputVolumeLiters\":" + input + ",\"producedUnits\":" + produced
                        + ",\"rejectedUnits\":" + rejected + "}"));
    }

    protected String batchOfPlan(MockHttpSession session, String planId) throws Exception {
        var body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/packaging/plans/" + planId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("batchId").asText();
    }

    /** O lote acabado que aquele plano produziu, já executado. */
    protected String loteAcabado(MockHttpSession session, String planId) throws Exception {
        var body = execute(session, planId, "284", 780, 12).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var code = JSON.readTree(body).get("finishedLotCode").asText();
        return finishedLotOf(session, batchOfPlan(session, planId), code).get("id").asText();
    }

    protected com.fasterxml.jackson.databind.JsonNode finishedLotOf(MockHttpSession session,
            String batchId, String code) throws Exception {
        return scenario.finishedLotOf(session, batchId, code);
    }

    protected void registraFrescor(MockHttpSession session, String planId) throws Exception {
        scenario.recordFreshness(session, planId);
    }

    protected static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }
}
