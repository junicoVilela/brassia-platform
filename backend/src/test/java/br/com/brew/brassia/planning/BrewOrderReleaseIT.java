package br.com.brew.brassia.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BrewOrderReleaseIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired WebApplicationContext context;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void releasesDraftWithResponsible() throws Exception {
        var session = login();
        var orderId = draftOrder(session);

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RELEASED")));

        mockMvc.perform(get("/api/v1/brew-orders/" + orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RELEASED")));
    }

    /**
     * A jornada inteira: liberar uma OP de verdade e ver a entrega aparecer na fila (DEB-INT-002).
     *
     * <p>O {@code WebhookIT} já provava o outbox, a restrição única, o isolamento e as alçadas — mas
     * chamando o enfileirador direto. O que nenhum deles cobria é o <strong>elo</strong>: que liberar uma
     * ordem publica o evento, que o ouvinte o traduz, e que a entrega nasce dentro da mesma transação.
     * Montar a OP de novo dentro do teste de integração testaria o planejamento; a asserção mora aqui,
     * onde a ordem já é montada por outro motivo, que era exatamente o critério de remoção do débito.
     */
    @Test
    void releasingAnOrderEnqueuesTheWebhookDelivery() throws Exception {
        var session = login();
        subscribeToReleases(session);
        var orderId = draftOrder(session);

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        // O tipo é gravado pelo NOME EXTERNO, não pelo da enum. É o nome que viaja no corpo para quem
        // integra, e o banco guarda o que foi entregue — não a representação interna. Escrevi
        // 'BREW_ORDER_RELEASED' na primeira versão deste teste e ele passou a procurar o que não existe.
        var entregas = jdbc.sql("""
                SELECT payload FROM webhook_delivery
                WHERE event_type = 'brew_order.released' AND event_id = :orderId
                """)
                .param("orderId", orderId.toString())
                .query(String.class).list();

        assertThat(entregas).hasSize(1);
        // O corpo é montado a partir do EVENTO, não relido do banco: o retry precisa descrever o fato como
        // ele foi, não a ordem como está hoje.
        assertThat(entregas.getFirst()).contains(orderId.toString());
    }

    /**
     * Uma ordem liberada SEM assinatura não gera entrega — e é o outro lado da mesma prova.
     *
     * <p>Sem este caso, o teste acima passaria por uma entrega que existisse por qualquer motivo. Aqui a
     * ausência é o resultado esperado: enfileirar para quem não assinou seria mandar dado da cervejaria
     * para um destino que ninguém autorizou.
     */
    @Test
    void releasingWithoutSubscriptionEnqueuesNothing() throws Exception {
        var session = login();

        // Assinatura é da CERVEJARIA, não do pedido — filtrar a consulta por ordem não isola este caso. O
        // banco é o mesmo entre os testes da classe, e a assinatura criada no teste anterior sobrevive:
        // sem revogar, este teste passaria ou falharia conforme a ordem de execução, que ninguém controla.
        jdbc.sql("UPDATE webhook_subscription SET status = 'REVOKED'").update();

        var orderId = draftOrder(session);

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        var entregas = jdbc.sql("""
                SELECT COUNT(*) FROM webhook_delivery WHERE event_id = :orderId
                """)
                .param("orderId", orderId.toString())
                .query(Integer.class).single();

        assertThat(entregas).isZero();
    }

    private void subscribeToReleases(MockHttpSession session) throws Exception {
        var body = JSON.createObjectNode();
        body.put("name", "Integração de teste");
        // HTTPS e endereço público: o InternalAddressGuard (DEC-REL-001) recusa destino interno, e o
        // cadastro não é o momento em que ele age — mas o domínio já exige HTTPS aqui.
        body.put("endpoint", "https://exemplo.invalido/webhooks");
        body.set("events", JSON.createArrayNode().add("brew_order.released"));

        mockMvc.perform(post("/api/v1/integration/webhooks").session(session).with(csrf())
                        .contentType("application/json").content(JSON.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void blocksReleaseWithoutResponsible() throws Exception {
        var session = login();
        var orderId = draftOrder(session);

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("missing_responsible")));
    }

    @Test
    void blocksReleaseWhenAlreadyReleased() throws Exception {
        var session = login();
        var orderId = draftOrder(session);
        var user = "{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json").content(user))
                .andExpect(status().isOk());

        // Segunda liberação → bloqueada (não está mais em rascunho).
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json").content(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("not_draft")));
    }

    @Test
    void deniesWithoutPermissionAndAcrossBrewery() throws Exception {
        var session = login();
        var orderId = draftOrder(session);
        var body = "{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.order.read")))).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.order.manage")))).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String draftOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Rel %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String code, String attributes)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"KG\",\"purchaseUnit\":\"KG\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
